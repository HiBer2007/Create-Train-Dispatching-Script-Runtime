package top.hibermod.ctdscript.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import net.minecraft.server.packs.PackType;


/**
 * 把脚本提供的美术资源变成 Minecraft 能加载的资源包。
 *
 * <h2>脚本只需要给图，不需要写 JSON</h2>
 * 目录约定（自定义，但固定）：
 *
 * <pre>
 * config/ctd/
 *   assets/                       ← 脚本放美术资源
 *     &lt;namespace&gt;/
 *       textures/block/*.png      ← 方块纹理
 *       textures/item/*.png
 *       textures/gui/*.png        ← HUD 贴图
 *       lang/en_us.json           ← 可选，引擎会合并
 *   scripts/block/*.py            ← content.block(...) 声明
 * </pre>
 *
 * 引擎在启动时把 {@code assets/} 复制到 {@code config/ctd/generated/resourcepack/}，
 * 并为每个声明的方块**合成** blockstate / block model / item model 三份 JSON，
 * 然后用原版的 {@link PathPackResources} 把这个目录挂成内置资源包。
 *
 * <h2>为什么生成真实目录而不是实现 PackResources</h2>
 * 自己实现 {@code PackResources} 要跟着版本改一堆方法签名；
 * 生成目录 + 用原版实现，版本升级时只需要改 pack_format。
 * 代价是多一份磁盘副本，纹理都是几十 KB，无所谓。
 */
public final class ScriptPack {

    /** 1.21 / 1.21.1 的资源包格式号。 */
    public static final int PACK_FORMAT = 34;

    private ScriptPack() {}

    /**
     * 生成资源包目录。**必须在资源加载之前调用**（模组构造期即可）。
     *
     * @return 生成的包根目录；没有方块也没有 assets 时返回 {@code null}
     */
    public static Path materialize(Path configDir) throws IOException {
        Path assetsRoot = configDir.resolve("assets");
        Path outRoot = configDir.resolve("generated").resolve("resourcepack");
        Path outAssets = outRoot.resolve("assets");

        boolean hasAssets = Files.isDirectory(assetsRoot) && hasAnyFile(assetsRoot);
        boolean hasBlocks = !ContentRegistry.blocks().isEmpty();
        if (!hasAssets && !hasBlocks) {
            return null;
        }

        deleteRecursively(outRoot);
        Files.createDirectories(outAssets);

        // ① 脚本自带的资源原样复制
        if (hasAssets) {
            copyTree(assetsRoot, outAssets);
        }

        // ② 为每个方块合成模型三件套
        for (BlockDefinition def : ContentRegistry.blocks()) {
            writeBlockAssets(outAssets, def);
        }

        // ③ pack.mcmeta
        String meta = """
            {
              "pack": {
                "description": "CTD script content",
                "pack_format": %d
              }
            }
            """.formatted(PACK_FORMAT);
        Files.writeString(outRoot.resolve("pack.mcmeta"), meta, StandardCharsets.UTF_8);

        return outRoot;
    }

    /**
     * 把生成的包放进游戏的 {@code resourcepacks/} 并写进 {@code options.txt}。
     *
     * <h2>为什么不用 {@code AddPackFindersEvent}</h2>
     * 那个事件只接受**模组 jar 内部**的包：它会按命名空间去
     * {@code ModList.get().getModContainerById(ns)} 找模组文件，
     * 外部目录（脚本生成的包）根本进不去。
     *
     * <p>NeoForge 也没有公开「加一个外部资源包并强制启用」的 API。
     * 所以这里走文件系统：把包复制到 {@code resourcepacks/ctd_scripts}，
     * 再确保 {@code options.txt} 的 {@code resourcePacks} 列表里有它。
     *
     * <p>这一步只影响**客户端外观**：服务端不需要模型，
     * 方块注册本身与资源包无关。所以即使这里失败，方块依然可用，
     * 只是会显示成紫黑格——会记一条 warn，不静默。
     *
     * @param gameDir 游戏目录（{@code run/}）
     * @return 是否成功安装
     */
    public static boolean installIntoResourcepacks(Path gameDir, Path generatedPack) {
        if (generatedPack == null) return false;
        try {
            Path target = gameDir.resolve("resourcepacks").resolve("ctd_scripts");
            deleteRecursively(target);
            Files.createDirectories(target.getParent());
            copyTree(generatedPack, target);
            enableInOptions(gameDir);
            return true;
        } catch (Exception e) {
            System.err.println("[ctdscript] cannot install script resource pack: " + e);
            return false;
        }
    }

    /**
     * 确保 {@code options.txt} 的 {@code resourcePacks} 列表包含本包。
     *
     * <p>格式是 {@code resourcePacks:["vanilla","file/ctd_scripts"]}。
     * 文件不存在或格式不认识时**不动它**——宁可让用户手动勾选，
     * 也不要把别人的设置改坏。
     */
    private static void enableInOptions(Path gameDir) throws IOException {
        Path options = gameDir.resolve("options.txt");
        if (!Files.exists(options)) return;

        java.util.List<String> lines = Files.readAllLines(options, StandardCharsets.UTF_8);
        String key = "resourcePacks:";
        boolean found = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(key)) continue;
            found = true;
            if (line.contains("file/ctd_scripts")) return;      // 已经启用

            String value = line.substring(key.length()).trim();
            if (!value.endsWith("]")) return;                    // 格式不认识，不碰
            String updated = value.substring(0, value.length() - 1).trim();
            if (updated.equals("[")) {
                updated = "[\"file/ctd_scripts\"]";
            } else {
                updated = updated + ",\"file/ctd_scripts\"]";
            }
            lines.set(i, key + updated);
            break;
        }

        if (found) {
            Files.write(options, lines, StandardCharsets.UTF_8);
        }
    }

    // ── 合成的三份 JSON ────────────────────────────────────────────────────

    private static void writeBlockAssets(Path assets, BlockDefinition def) throws IOException {
        String ns = def.id().getNamespace();
        String path = def.id().getPath();
        Path base = assets.resolve(ns);
        Files.createDirectories(base.resolve("blockstates"));
        Files.createDirectories(base.resolve("models").resolve("block"));
        Files.createDirectories(base.resolve("models").resolve("item"));

        // blockstate：唯一变体指向 block model
        writeJson(base.resolve("blockstates").resolve(path + ".json"), """
            {
              "variants": {
                "": { "model": "%s:block/%s" }
              }
            }
            """.formatted(ns, path));

        // block model：六面同纹理（cube_all）
        writeJson(base.resolve("models").resolve("block").resolve(path + ".json"), """
            {
              "parent": "minecraft:block/cube_all",
              "textures": { "all": "%s" }
            }
            """.formatted(def.texture()));

        // item model：继承 block model
        writeJson(base.resolve("models").resolve("item").resolve(path + ".json"), """
            { "parent": "%s:block/%s" }
            """.formatted(ns, path));
    }

    private static void writeJson(Path file, String json) throws IOException {
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    // ── 文件工具 ──────────────────────────────────────────────────────────

    private static boolean hasAnyFile(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.anyMatch(Files::isRegularFile);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream in = Files.newInputStream(p)) {
                        Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 删不掉就留着，下一次复制会覆盖
                }
            });
        }
    }
}
