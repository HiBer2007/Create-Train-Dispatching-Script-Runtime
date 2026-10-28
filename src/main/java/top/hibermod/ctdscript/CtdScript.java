package top.hibermod.ctdscript;

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

    public static final String MOD_ID = "ctdscript";

    private static CtdScript instance;

    private final Path scriptRoot;
    private ScriptRuntime runtime;
    private ScriptLoader.Report lastReport;

    public CtdScript(IEventBus modBus) {
        instance = this;
        this.scriptRoot = FMLPaths.CONFIGDIR.get().resolve("ctd").resolve("scripts");

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onLoadComplete);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    /** 取当前实例（命令与宿主联动用）。 */
    public static CtdScript get() {
        return instance;
    }

    public Path scriptRoot() {
        return scriptRoot;
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
    public synchronized ScriptLoader.Report reloadScripts() {
        try {
            if (runtime != null) {
                runtime.close();
            }
            runtime = new ScriptRuntime();
            lastReport = new ScriptLoader(runtime, scriptRoot)
                .loadAll(java.util.List.of("kernel", "ato"));
            System.out.println("[ctdscript] reloaded: " + lastReport.summary());
            return lastReport;
        } catch (Throwable t) {
            System.err.println("[ctdscript] reload failed: " + t);
            return null;
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 脚本会引用内置的语义/灯色/速度等级，所以先把内置内容注册好
        SignalRegistries.bootstrap();
        Traction.Builtin.bootstrap();
        Interop.Known.bootstrap();

        try {
            Files.createDirectories(scriptRoot);
        } catch (Exception e) {
            System.err.println("[ctdscript] cannot create script dir " + scriptRoot + ": " + e);
        }

        event.enqueueWork(() -> {
            try {
                this.runtime = new ScriptRuntime();
                this.lastReport = new ScriptLoader(runtime, scriptRoot).loadAll();
                System.out.println("[ctdscript] " + lastReport.summary());
            } catch (Throwable t) {
                // 脚本层整体失败不能拖垮服务器：记录并继续，API 注册表保持内置状态
                System.err.println("[ctdscript] scripting unavailable: " + t);
                this.runtime = null;
            }
        });
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

    private void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root =
            Commands.literal("ctd")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> {
                    var r = reloadScripts();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        r == null ? "reload failed" : r.summary("config/ctd/scripts")), false);
                    return r == null ? 0 : r.ok();
                }))
                .then(Commands.literal("list").executes(ctx -> {
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
                    return r == null ? 0 : r.ok();
                }));

        event.getDispatcher().register(root);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }
}
