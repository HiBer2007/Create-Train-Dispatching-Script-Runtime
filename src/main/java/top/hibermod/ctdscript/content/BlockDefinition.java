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
 * 引擎把它挂成虚拟资源包，并**合成** blockstate / 模型 JSON——
 * 脚本只需要提供图片，不需要写任何 JSON。
 *
 * @param id        方块 id，命名空间必须属于脚本自己
 * @param texture   纹理资源 id（指向 {@code textures/} 下的 png）
 * @param hardness  硬度，决定挖掘时间
 * @param light     自发光等级 0–15
 * @param solid     是否有碰撞体积
 */
public record BlockDefinition(
    ResourceLocation id,
    ResourceLocation texture,
    float hardness,
    int light,
    boolean solid
) {
    public BlockDefinition {
        if (id == null) throw new IllegalArgumentException("block id is required");
        if (texture == null) throw new IllegalArgumentException("texture is required for " + id);
        if (hardness < 0) throw new IllegalArgumentException("hardness must be >= 0");
        light = Math.max(0, Math.min(15, light));
    }

    /** 方块状态与模型都在命名空间下的这个路径。 */
    public ResourceLocation modelId() {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath());
    }

    /** 校验一份声明列表：id 重复、纹理缺失都要在启动期报出来。 */
    public static void validate(List<BlockDefinition> all) {
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (BlockDefinition b : all) {
            if (!seen.add(b.id())) {
                throw new IllegalStateException("duplicate block definition: " + b.id());
            }
        }
    }
}
