package top.hibermod.ctdscript.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;

/**
 * 脚本 HUD 贴图的路径解析。
 *
 * <h2>为什么要这一层</h2>
 * 脚本写的是 {@code mysys:gui/badge}，而 Minecraft 的贴图资源路径是
 * {@code mysys:textures/gui/badge.png}。差一个字就是紫黑格，而且不报错。
 *
 * <p>这里只做**一次**转换并缓存，避免每帧拼字符串。
 * 同时容忍脚本已经把前缀写全的情况——两种写法都认。
 */
final class GuiTextures {

    private static final Map<ResourceLocation, ResourceLocation> CACHE = new ConcurrentHashMap<>();

    private GuiTextures() {}

    static ResourceLocation resolve(ResourceLocation logical) {
        if (logical == null) return null;
        return CACHE.computeIfAbsent(logical, GuiTextures::build);
    }

    private static ResourceLocation build(ResourceLocation logical) {
        String path = logical.getPath();
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            return logical;                       // 脚本写的是完整路径
        }
        if (path.endsWith(".png")) {
            return ResourceLocation.fromNamespaceAndPath(logical.getNamespace(),
                "textures/" + path);
        }
        return ResourceLocation.fromNamespaceAndPath(logical.getNamespace(),
            "textures/" + path + ".png");
    }
}
