package top.hibermod.ctdscript.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为脚本方块合成语言文件。
 *
 * <h2>为什么必须有</h2>
 * 不写 lang 的后果是客户端直接显示 {@code block.mysys.bridge_panel} 这种键。
 * 玩家看到的就是一串标识符——这不是「没翻译好」，是根本没人给过翻译。
 *
 * <h2>合并规则</h2>
 * 脚本自己在 {@code assets/<ns>/lang/*.json} 里写的键**优先**，
 * 引擎只补齐缺失的。这样想改名的脚本不会被引擎覆盖，没写的也不会裸键。
 */
final class ScriptLang {

    private ScriptLang() {}

    static void write(Path assets) throws IOException {
        Map<String, Map<String, String>> perNs = new LinkedHashMap<>();
        for (BlockDefinition def : ContentRegistry.blocks()) {
            perNs.computeIfAbsent(def.id().getNamespace(), k -> new LinkedHashMap<>())
                 .put(def.langKey(), def.effectiveName());
        }

        for (Map.Entry<String, Map<String, String>> e : perNs.entrySet()) {
            Path langDir = assets.resolve(e.getKey()).resolve("lang");
            Files.createDirectories(langDir);
            for (String code : new String[] { "en_us", "zh_cn" }) {
                Path file = langDir.resolve(code + ".json");
                Map<String, String> merged = new LinkedHashMap<>();
                if (Files.exists(file)) merged.putAll(read(file));
                for (Map.Entry<String, String> kv : e.getValue().entrySet()) {
                    merged.putIfAbsent(kv.getKey(), kv.getValue());
                }
                Files.writeString(file, toJson(merged), StandardCharsets.UTF_8);
            }
        }
    }

    private static Map<String, String> read(Path file) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
            while (m.find()) out.put(m.group(1), m.group(2));
        } catch (Exception ignored) {
            // 读不动就当没有；写的时候会按合成结果重建
        }
        return out;
    }

    private static String toJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append("  ").append(quote(e.getKey())).append(": ").append(quote(e.getValue()));
            if (++i < map.size()) sb.append(',');
            sb.append(System.lineSeparator());
        }
        return sb.append("}\n").toString();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
