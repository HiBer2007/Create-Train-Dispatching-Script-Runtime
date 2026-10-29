package top.hibermod.ctdscript.bridge;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import top.hibermod.ctdscript.content.DisplayBoardBlockEntity;

/**
 * 脚本侧的显示板接口：{@code ctd.display}。
 *
 * <h2>为什么用坐标而不是对象</h2>
 * 沙箱里的脚本不该持有方块实体的强引用——那是把服务端的生命周期
 * 塞进一个随时会被重载的上下文里。坐标是纯数据，
 * 每一次写都重新查一遍，方块没了就什么都不做。
 *
 * <h2>为什么按维度分开</h2>
 * 坐标本身没有维度信息。默认写主世界，需要别的维度就显式给
 * （{@code "minecraft:the_nether"}）。
 */
public final class DisplayApi {

    public static final DisplayApi INSTANCE = new DisplayApi();

    private DisplayApi() {}

    private static ServerLevel level(String dimension) {
        MinecraftServer server = top.hibermod.ctdscript.CtdScript.server();
        if (server == null) return null;
        if (dimension == null || dimension.isBlank()) return server.overworld();
        var key = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(dimension));
        return server.getLevel(key);
    }

    /**
     * 设置显示板内容。
     *
     * @param dimension 维度 id，传空字符串表示主世界
     * @param lines     每行一条；最多 4 行，超长会截断（不抛异常）
     * @return 是否写成功（false = 那个坐标不是显示板 / 方块不存在）
     */
    @HostAccess.Export
    public boolean set(String dimension, int x, int y, int z, List<String> lines) {
        ServerLevel level = level(dimension);
        if (level == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) return false;
        if (!(level.getBlockEntity(pos) instanceof DisplayBoardBlockEntity board)) return false;

        board.setLines(new ArrayList<>(lines == null ? List.of() : lines));
        return true;
    }

    /** 便捷写法：主世界。 */
    @HostAccess.Export
    public boolean set(int x, int y, int z, List<String> lines) {
        return set("", x, y, z, lines);
    }

    /** 清空。 */
    @HostAccess.Export
    public boolean clear(String dimension, int x, int y, int z) {
        return set(dimension, x, y, z, List.of());
    }

    /** 读回内容；不是显示板返回空列表。 */
    @HostAccess.Export
    public List<String> get(String dimension, int x, int y, int z) {
        ServerLevel level = level(dimension);
        if (level == null) return List.of();

        BlockPos pos = new BlockPos(x, y, z);
        if (!(level.getBlockEntity(pos) instanceof DisplayBoardBlockEntity board)) {
            return List.of();
        }
        return List.copyOf(board.lines());
    }
}
