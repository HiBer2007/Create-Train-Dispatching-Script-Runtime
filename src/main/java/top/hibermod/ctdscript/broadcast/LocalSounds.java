package top.hibermod.ctdscript.broadcast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 脚本随包提供的音频：扫描 → 注册 → 播放。
 *
 * <h2>目录约定</h2>
 * <pre>
 * config/ctd/assets/&lt;ns&gt;/sounds/announce/stopped.ogg   →   &lt;ns&gt;:announce/stopped
 * </pre>
 * 引擎为每个 {@code .ogg} 注册一个 {@code SoundEvent}，并**合成** {@code sounds.json}
 * 写进资源包——脚本只丢文件，不写 JSON。
 *
 * <h2>为什么扫描必须在注册之前</h2>
 * {@code SoundEvent} 进的是启动期注册表，注册之后再加就不存在了。
 * 所以构造期扫一遍存成数据，{@code RegisterEvent} 里再登记。
 */
public final class LocalSounds {

    private static final Map<net.minecraft.resources.ResourceLocation, Path> FOUND = new LinkedHashMap<>();

    private LocalSounds() {}

    /**
     * 扫描所有命名空间下的 {@code sounds/**}。
     *
     * @param assetsRoot {@code config/ctd/assets}
     */
    public static void scan(Path assetsRoot) {
        FOUND.clear();
        if (!Files.isDirectory(assetsRoot)) return;

        try (Stream<Path> namespaces = Files.list(assetsRoot)) {
            for (Path nsDir : (Iterable<Path>) namespaces::iterator) {
                if (!Files.isDirectory(nsDir)) continue;
                String ns = nsDir.getFileName().toString();
                Path soundsRoot = nsDir.resolve("sounds");
                if (!Files.isDirectory(soundsRoot)) continue;
                scanNamespace(ns, soundsRoot);
            }
        } catch (IOException e) {
            System.err.println("[ctdscript] cannot scan local sounds: " + e);
        }
    }

    private static void scanNamespace(String ns, Path soundsRoot) throws IOException {
        try (Stream<Path> files = Files.walk(soundsRoot)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString().toLowerCase();
                if (!name.endsWith(".ogg") && !name.endsWith(".wav")) continue;

                String rel = soundsRoot.relativize(file).toString().replace('\\', '/');
                int dot = rel.lastIndexOf('.');
                if (dot > 0) rel = rel.substring(0, dot);

                var id = net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath(ns, rel.toLowerCase());
                FOUND.put(id, file);
            }
        }
    }

    /** 已发现的音频 id（{@code ns:path}）。 */
    public static List<net.minecraft.resources.ResourceLocation> ids() {
        return new ArrayList<>(FOUND.keySet());
    }

    /** 原始文件路径（{@code sounds.json} 合成时不需要，排查时有用）。 */
    public static Path fileOf(net.minecraft.resources.ResourceLocation id) {
        return FOUND.get(id);
    }

    /**
     * 找一条「这个事件该放的声音」。
     *
     * <p>约定：{@code <ns>:announce/<event>}。找不到就返回 {@code null}，
     * 由语音源链降级——**不要**随便拿一条别的音频顶替，那是错的广播。
     */
    public static net.minecraft.resources.ResourceLocation findFor(BroadcastEvent event) {
        if (event == null) return null;
        for (var id : FOUND.keySet()) {
            if (id.getPath().endsWith("announce/" + event.key())) return id;
        }
        return null;
    }

    public static int size() {
        return FOUND.size();
    }
}
