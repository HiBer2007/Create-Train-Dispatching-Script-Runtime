package top.hibermod.ctdscript.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import top.hibermod.ctdscript.broadcast.LocalSounds;

/**
 * 合成 {@code sounds.json}。
 *
 * <h2>为什么必须合成</h2>
 * 注册了 {@code SoundEvent} 只是给了它一个 id；
 * 客户端还要知道「这个 id 对应哪个文件」，那份映射在 {@code sounds.json} 里。
 * 少了它**不会报错**，只会没声——最难查的一类问题。
 *
 * <h2>合并规则</h2>
 * 脚本自己写了 {@code sounds.json} 就原样保留，引擎只补缺。
 * 目前靠「文件存在即不动」实现：脚本若想完全接管，放一份文件即可。
 */
final class ScriptSounds {

    private ScriptSounds() {}

    static void write(Path assets) throws IOException {
        Map<String, Map<String, String>> perNs = new LinkedHashMap<>();
        for (var id : LocalSounds.ids()) {
            perNs.computeIfAbsent(id.getNamespace(), k -> new LinkedHashMap<>())
                 .put(id.getPath(), id.toString());
        }

        for (var e : perNs.entrySet()) {
            Path nsDir = assets.resolve(e.getKey());
            Files.createDirectories(nsDir);
            Path file = nsDir.resolve("sounds.json");

            if (Files.exists(file)) {
                // 脚本自带的完整定义优先，引擎不掺和
                continue;
            }

            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (var entry : e.getValue().entrySet()) {
                sb.append("  \"").append(entry.getKey()).append("\": {\n")
                  .append("    \"category\": \"neutral\",\n")
                  .append("    \"sounds\": [\"").append(entry.getValue()).append("\"]\n")
                  .append("  }");
                if (++i < e.getValue().size()) sb.append(',');
                sb.append('\n');
            }
            sb.append("}\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        }
    }
}
