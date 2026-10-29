package top.hibermod.ctdscript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import top.hibermod.ctdapi.ApiCatalog;
import top.hibermod.ctdscript.content.ContentRegistry;
import top.hibermod.ctdapi.interop.Interop;
import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdapi.traction.Traction;

/**
 * CTD 脚本驱动：把 Python 脚本的注册行为翻译成 CTD API 的 Java 注册调用。
 *
 * <h2>生命周期</h2>
 * <pre>
 * 构造        —— 什么都不做（Context 很重，别在构造期抢资源）
 * commonSetup —— 内置注册表 bootstrap → 创建 GraalPy 运行时 → 执行脚本
 * loadComplete—— ApiCatalog.freezeAll()  ← 脚本必须在此之前跑完
 * 关服        —— 关闭 Context
 * </pre>
 */
@Mod(CtdScript.MOD_ID)
public final class CtdScript {

    private static final Logger LOG = LoggerFactory.getLogger("ctdscript");

    public static final String MOD_ID = "ctdscript";

    private static CtdScript instance;

    private final Path scriptRoot;
    private ScriptRuntime runtime;
    private ScriptReloader reloader;
    private ScriptLoader.Report lastReport;

    public CtdScript(IEventBus modBus) {
        instance = this;
        this.scriptRoot = FMLPaths.CONFIGDIR.get().resolve("ctd").resolve("scripts");

        // ① 服务先就位：脚本在**加载期**就可能调用广播与拓展，
        //    所以这些必须在 loadStartupScripts() 之前完成。
        top.hibermod.ctdscript.broadcast.LocalSounds.scan(
            FMLPaths.CONFIGDIR.get().resolve("ctd").resolve("assets"));
        top.hibermod.ctdscript.broadcast.LocalSoundRegistry.attach(modBus);
        BROADCAST.chain().register(new top.hibermod.ctdscript.broadcast.LocalVoiceSource());

        top.hibermod.ctdscript.broadcast.BroadcastLog.configure(
            FMLPaths.CONFIGDIR.get().resolve("ctd"));

        // 兜底语音源：真声源都不可用时至少把文案记下来，不让广播静默消失
        BROADCAST.chain().register(new top.hibermod.ctdscript.broadcast.LogVoiceSource());

        // 宿主报「车接近/停稳/离站」，这里接上广播：装好即响，无需写胶水
        top.hibermod.ctdscript.broadcast.StationEvents.attach();

        // 拓展口：别的模组往这里登记，脚本通过 ctd.ext 使用
        top.hibermod.ctdscript.bridge.Extensions.register(
            top.hibermod.ctdscript.bridge.EngineExtension.NAMESPACE,
            top.hibermod.ctdscript.bridge.EngineExtension.INSTANCE);

        // ② 跑脚本：方块定义在这里收集（注册事件之前必须收集完）
        loadStartupScripts();

        // ③ 注册监听：此时定义已经齐了
        top.hibermod.ctdscript.content.ScriptBlocks.attach(modBus);
        top.hibermod.ctdscript.content.EngineBlocks.attach(modBus);

        // 美术资源：把脚本的 assets/ 复制成资源包，并为每个方块合成模型 JSON。
        // 必须在资源加载之前——也就是现在（构造期）。
        try {
            java.nio.file.Path pack = top.hibermod.ctdscript.content.ScriptPack.materialize(
                FMLPaths.CONFIGDIR.get().resolve("ctd"));
            if (pack != null) {
                boolean ok = top.hibermod.ctdscript.content.ScriptPack.installIntoResourcepacks(
                    FMLPaths.GAMEDIR.get(), pack);
                System.out.println("[ctdscript] script resource pack: "
                    + (ok ? "installed" : "NOT installed") + " -> " + pack);
            }
        } catch (Throwable t) {
            System.err.println("[ctdscript] cannot build script resource pack: " + t);
        }

        modBus.addListener(this::onRegisterRenderers);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onLoadComplete);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    /** 取当前实例（命令与宿主联动用）。 */
    public static CtdScript get() {
        return instance;
    }

    public Path scriptRoot() {
        return scriptRoot;
    }

    /** 静态入口：拓展对象要读加载报告（实例方法同名会冲突，故用 report）。 */
    public static ScriptLoader.Report report() {
        CtdScript local = instance;
        return local == null ? null : local.lastReport;
    }

    public ScriptLoader.Report lastReport() {
        return lastReport;
    }

    /**
     * 热重载脚本。
     *
     * <p><b>只重载 {@code kernel/} 与 {@code ato/}</b>——只有这两类注册在 API 侧有
     * {@code replace(...)} 语义。信号制度 / 设备 / 交路一旦 {@code freeze()} 就不能增量修改，
     * 重载它们必须重启服务器。
     *
     * <p>窗口期：旧运行时先关、新脚本后跑，中间没有内核在发指令。
     * 按 API 的「零指令 = 不干预」原则，列车会退回 Create 原生行为一个 tick 左右。
     * 这是可接受的降级，不是 bug。
     *
     * @return 加载报告；运行时不可用时返回 {@code null}
     */
    /**
     * 全量热重载：五类脚本全部重跑，失败则整批回滚。
     *
     * <h2>为什么可以重载「定义类」了</h2>
     * 之前只有 kernel/ato 能重载，因为注册表冻结后无法增量修改。
     * 现在 API 提供了 {@code unfreeze()} 与 {@code remove(id)}，
     * 于是流程变成：解冻 → 撤掉上一批脚本注册项 → 重跑 → 重新冻结。
     *
     * <h2>为什么整批回滚而不是逐文件</h2>
     * 逐文件回滚会留下「一半新一半旧」的注册表——那种状态比完全不重载更难排查。
     * 全有或全无是唯一能让人放心的语义。
     *
     * <h2>回滚靠什么</h2>
     * {@link ScriptRegistry} 记着每个注册项**对象本身**，所以回滚不需要
     * 旧脚本文件还在，也不需要它还能跑通。改了文件也能退回去。
     */
    /**
     * 全量热重载。逻辑在 {@link ScriptReloader} 里——那是可离线测试的版本，
     * 这个方法只负责把结果转成命令输出。
     */
    public synchronized ScriptReloader.Result reloadScripts() {
        if (reloader == null) reloader = new ScriptReloader(scriptRoot);
        ScriptReloader.Result r = reloader.reload();
        lastReport = r.report();
        return r;
    }


    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 方块此时已经进了注册表，才能把「车上右键」行为挂到真正的方块上。
        // 在构造期调用会拿到 air —— 注册表那时还是空的。
        top.hibermod.ctdscript.content.EngineBlocks.registerInteractions();

        // 脚本会引用内置的语义/灯色/速度等级，所以先把内置内容注册好
        SignalRegistries.bootstrap();
        Traction.Builtin.bootstrap();
        Interop.Known.bootstrap();

        // 兜底：正常情况下构造函数已经加载过（幂等）
        event.enqueueWork(() -> {
            try {
                loadStartupScripts();
            } catch (Throwable t) {
                System.err.println("[ctdscript] scripting unavailable: " + t);
            }
        });
    }

    /**
     * 启动期加载脚本（幂等）。
     *
     * <h2>为什么必须最早跑</h2>
     * 脚本可以注册**方块**，而方块只能在 {@code RegisterEvent} 期间进入注册表。
     * 注册事件发生在所有模组构造之后、{@code FMLCommonSetupEvent} 之前，
     * 所以加载必须早于它——否则 {@code block.register(...)} 收集到的定义
     * 永远不会变成真正的方块，而且**不会报错**。
     */
    public synchronized void loadStartupScripts() {
        if (runtime != null) return;

        try {
            Files.createDirectories(scriptRoot);
        } catch (Exception e) {
            System.err.println("[ctdscript] cannot create script dir " + scriptRoot + ": " + e);
        }

        try {
            this.runtime = new ScriptRuntime();
            this.lastReport = new ScriptLoader(runtime, scriptRoot).loadAll();
            System.out.println("[ctdscript] " + lastReport.summary());
        } catch (Throwable t) {
            // 脚本层整体失败不能拖垮服务器：记录并继续，API 注册表保持内置状态
            System.err.println("[ctdscript] scripting unavailable: " + t);
            this.runtime = null;
        } finally {
            // 无论成功与否都封存：之后的注册一律拒绝，避免「半套定义」进注册表
            ContentRegistry.seal();
            System.out.println("[ctdscript] content sealed: " + ContentRegistry.summary());
        }
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            try {
                ApiCatalog.freezeAll();
                System.out.println("[ctdscript] registries frozen, "
                    + ApiCatalog.entries().size() + " entries");
            } catch (IllegalStateException e) {
                System.err.println("[ctdscript] registry validation failed: " + e.getMessage());
            }
        });
    }

    /** 客户端渲染器注册：车载设备的按人外观。 */
    @net.neoforged.bus.api.SubscribeEvent
    public void onRegisterRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            top.hibermod.ctdscript.content.EngineBlocks.onboardDeviceType(),
            top.hibermod.ctdscript.client.OnboardDeviceRenderer::new);
        event.registerBlockEntityRenderer(
            top.hibermod.ctdscript.content.EngineBlocks.displayBoardType(),
            top.hibermod.ctdscript.client.DisplayBoardRenderer::new);
        System.out.println("[ctdscript] registered 2 block-entity renderers");
    }

    /** 广播服务：事件模型 + 语音源链。全局一份，重载只清监听不清音源。 */
    private static final top.hibermod.ctdscript.broadcast.BroadcastService BROADCAST =
        new top.hibermod.ctdscript.broadcast.BroadcastService();

    /** 当前服务端实例（本地音频播放需要它）。客户端/未启动时为 null。 */
    public static net.minecraft.server.MinecraftServer server() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    public static top.hibermod.ctdscript.broadcast.BroadcastService broadcastService() {
        return BROADCAST;
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root =
            Commands.literal("ctd")
                .requires(src -> src.hasPermission(2))
                .then(top.hibermod.ctdscript.command.DisplayCommand.build())
                .then(top.hibermod.ctdscript.command.SignalCommand.build())
                .then(top.hibermod.ctdscript.command.DeviceCommand.build())
                .then(top.hibermod.ctdscript.command.FixInteractorsCommand.build())
                .then(Commands.literal("broadcast")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal(BROADCAST.status()), false);
                        return 1;
                    })
                    .then(Commands.literal("emit")
                        .then(Commands.argument("event", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(ctx -> {
                                String key = com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "event");
                                var ev = top.hibermod.ctdscript.broadcast.BroadcastEvent.byKey(key);
                                if (ev == null) {
                                    ctx.getSource().sendFailure(Component.literal(
                                        "unknown event: " + key));
                                    return 0;
                                }
                                String voice = BROADCAST.emit(
                                    new top.hibermod.ctdscript.broadcast.BroadcastMessage(
                                        ev, "train", "T600", "测试站", "2", 0, null));
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    voice == null ? "no voice source could speak it"
                                                  : "spoken by: " + voice), false);
                                return 1;
                            })))
                    .then(Commands.literal("say")
                        .then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String text = com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "text");
                                String voice = BROADCAST.emit(
                                    new top.hibermod.ctdscript.broadcast.BroadcastMessage(
                                        top.hibermod.ctdscript.broadcast.BroadcastEvent.APPROACHING,
                                        "train", "", "", "", 0, text));
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    voice == null ? "no voice source" : "spoken by: " + voice), false);
                                return 1;
                            }))))                .then(Commands.literal("reload").executes(ctx -> {
                    var r = reloadScripts();
                    ctx.getSource().sendSuccess(() -> Component.literal(r.message()), false);
                    if (!r.success()) {
                        for (String f : r.report().failures()) {
                            ctx.getSource().sendFailure(Component.literal("  " + f));
                        }
                    }
                    return r.success() ? r.report().ok() : 0;
                }))
                .then(Commands.literal("content").executes(ctx -> {
                    var src = ctx.getSource();
                    src.sendSuccess(() -> Component.literal("CTD script content: "
                        + top.hibermod.ctdscript.content.ContentRegistry.summary()
                        + (top.hibermod.ctdscript.content.ContentRegistry.isSealed()
                            ? " (sealed)" : " (open)")), false);
                    for (var b : top.hibermod.ctdscript.content.ContentRegistry.blocks()) {
                        boolean ok = top.hibermod.ctdscript.content.ScriptBlocks.isRegistered(b);
                        src.sendSuccess(() -> Component.literal("  block " + b.id()
                            + " texture=" + b.texture() + (ok ? " [registered]" : " [MISSING]")), false);
                    }
                    for (var h : top.hibermod.ctdscript.content.ContentRegistry.huds()) {
                        src.sendSuccess(() -> Component.literal("  hud   " + h.id()
                            + " every " + h.intervalTicks() + "t"), false);
                    }
                    return top.hibermod.ctdscript.content.ContentRegistry.blocks().size();
                }))                .then(Commands.literal("list").executes(ctx -> {
                    var entries = ApiCatalog.entries();
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("CTD registry: " + entries.size() + " entries"), false);
                    for (var e : entries) {
                        ctx.getSource().sendSuccess(() -> Component.literal("  " + e), false);
                    }
                    return entries.size();
                }))
                .then(Commands.literal("scripts").executes(ctx -> {
                    var r = lastReport;
                    ctx.getSource().sendSuccess(
                        () -> Component.literal(r == null ? "no load report" : r.summary()), false);
                    return r == null ? 0 : r.ok();                }))
                .then(Commands.literal("stats").executes(ctx -> {
                    int n = 0;
                    for (var id : top.hibermod.ctdapi.DispatchKernels.registeredIds()) {
                        var k = top.hibermod.ctdapi.DispatchKernels.create(id).orElse(null);
                        if (k instanceof top.hibermod.ctdscript.adapters.PythonKernel pk) {
                            String line = id + "  " + pk.stats().summary()
                                + (pk.disabled() ? "  [DISABLED]" : "");
                            ctx.getSource().sendSuccess(() -> Component.literal("  " + line), false);
                            n++;
                        }
                    }
                    if (n == 0) {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("no script kernels registered"), false);
                    }
                    return n;
                }));

        event.getDispatcher().register(root);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }
    /** 每 10 刻把信号机语义推一次；有变化才发。 */
    private void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        if (now - lastAspectPush < 10) return;
        lastAspectPush = now;

        var snapshot = top.hibermod.ctdapi.status.SignalStates.snapshot();
        var entries = new java.util.ArrayList<
            top.hibermod.ctdscript.net.SignalAspectPayload.Entry>(snapshot.size());
        var ids = new StringBuilder();
        for (var e : snapshot.entrySet()) {
            entries.add(new top.hibermod.ctdscript.net.SignalAspectPayload.Entry(
                e.getKey(), e.getValue()));
            ids.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }

        String signature = ids.toString();
        if (signature.equals(lastAspectSignature)) return;      // 没变化就不发
        lastAspectSignature = signature;

        var payload = new top.hibermod.ctdscript.net.SignalAspectPayload(entries);
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            try {
                player.connection.send(payload);
            } catch (Throwable ignored) {
                // 客户端没装：没必要每 10 刻刷一次栈
            }
        }
    }

    private long lastAspectPush;
    private String lastAspectSignature = "";
}