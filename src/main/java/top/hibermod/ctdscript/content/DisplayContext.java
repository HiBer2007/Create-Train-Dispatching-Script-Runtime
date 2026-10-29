package top.hibermod.ctdscript.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 数据源被调用时的上下文：这块板子在哪、绑了什么参数。
 *
 * @param level 板子所在的世界
 * @param pos   板子位置（世界坐标；车载板子是它此刻的世界位置）
 * @param arg   源 id 冒号后面的部分，例如 {@code station:中央车站} 里的 {@code 中央车站}
 * @param gameTime 当前游戏刻
 */
public record DisplayContext(Level level, BlockPos pos, String arg, long gameTime) {

    public String argOr(String fallback) {
        return arg == null || arg.isBlank() ? fallback : arg;
    }
}
