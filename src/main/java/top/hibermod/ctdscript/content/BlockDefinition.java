package top.hibermod.ctdscript.content;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * 脚本声明的一个方块。
 *
 * <h2>为什么是「声明」而不是「对象」</h2>
 * 方块必须在 {@code RegisterEvent} 期间进入注册表，而脚本在模组构造期加载。
 * 两个时刻之间只能传**纯数据**：这里就是那份数据。
 *
 * <h2>纹理从哪来</h2>
 * {@code texture} 是一个资源 id（如 {@code mysys:block/bridge_panel}），
 * 对应的 PNG 由脚本放在 {@code config/ctd/assets/<ns>/textures/block/<name>.png}。
 * 引擎把它挂成资源包，并**合成** blockstate / 模型 / 语言文件——
 * 脚本只需要提供图片，不需要写任何 JSON。
 *
 * @param id          方块 id
 * @param texture     纹理资源 id（指向 {@code textures/} 下的 png）
 * @param hardness    硬度
 * @param light       自发光 0–15
 * @param solid       是否有碰撞体积
 * @param displayName 显示名；为空时按 id 生成
 */
public record BlockDefinition(
    ResourceLocation id,
    ResourceLocation texture,
    float hardness,
    int light,
    boolean solid,
    String displayName
) {
    public BlockDefinition {
        if (id == null) throw new IllegalArgumentException("block id is required");
        if (texture == null) throw new IllegalArgumentException("texture is required for " + id);
        if (hardness < 0) throw new IllegalArgumentException("hardness must be >= 0");
        light = Math.max(0, Math.min(15, light));
    }

    /** 方块模型在命名空间下的路径。 */
    public ResourceLocation modelId() {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath());
    }

    /** 语言文件里的键：{@code block.<ns>.<path>}。 */
    public String langKey() {
        return "block." + id.getNamespace() + "." + id.getPath();
    }

    /** 没有指定显示名时，把 id 变成「Bridge Panel」这种可读形式。 */
    public String effectiveName() {
        if (displayName != null && !displayName.isBlank()) return displayName;

        StringBuilder sb = new StringBuilder();
        for (String word : id.getPath().split("_")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1))
              .append(' ');
        }
        return sb.toString().trim();
    }

    /** 校验一份声明列表：id 重复要在启动期报出来。 */
    public static void validate(List<BlockDefinition> all) {
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (BlockDefinition b : all) {
            if (!seen.add(b.id())) {
                throw new IllegalStateException("duplicate block definition: " + b.id());
            }
        }
    }
}
