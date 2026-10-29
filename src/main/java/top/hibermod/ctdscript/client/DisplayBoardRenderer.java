package top.hibermod.ctdscript.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4f;

import top.hibermod.ctdscript.content.DisplayBoardBlockEntity;

/**
 * 把显示板的文字画在方块正面上。
 *
 * <h2>为什么用方块实体渲染器而不是模型</h2>
 * 文字是动态的，模型是静态资源。要改字就得重发模型，那是几十 KB 的资源重载；
 * 这里只在渲染时多画几行字。
 *
 * <h2>朝向</h2>
 * 目前固定朝北面（z 负方向）。等要做多朝向时，读方块状态的 {@code FACING} 即可，
 * 绘制逻辑不用变——变的只是绕 y 轴转多少度。
 */
public class DisplayBoardRenderer implements BlockEntityRenderer<DisplayBoardBlockEntity> {

    /**
     * 文字距方块表面的偏移。
     *
     * <p>贴太近会被方块自身的面裁掉（侧面看时靠近的那半边消失，这是实测现象），
     * 所以离面远一点，配合 {@code SEE_THROUGH} 一起用。
     */
    private static final float SURFACE_OFFSET = 0.02f;

    private final Font font;

    public DisplayBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(DisplayBoardBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        List<String> lines = be.lines();
        if (lines.isEmpty()) return;

        pose.pushPose();
        // 画在朝北那一面上，稍微外移
        pose.translate(0.5f, 0, -SURFACE_OFFSET);
        // 让文字面向北侧观察者
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));

        // 按最长一行自动缩放：48 个字符按固定比例会撑到三格宽
        int widestPx = 0;
        for (String line : lines) {
            widestPx = Math.max(widestPx, font.width(line == null ? "" : line));
        }
        float scale = Math.min(0.0105f, widestPx <= 0 ? 0.0105f : 0.92f / widestPx);
        float y = 1.0f - 0.18f;                // 从顶部往下排
        for (String line : lines) {
            String text = line == null ? "" : line;
            float width = font.width(text) * scale;
            pose.pushPose();
            pose.translate(-width / 2f, y, 0f);
            pose.scale(scale, -scale, scale);   // y 取负：方块坐标是向下为正的屏幕系
            font.drawInBatch(Component.literal(text), 0, 0, 0xFFFFFFFF, false,
                pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH,
                0x55000000, packedLight);
            pose.popPose();
            y -= 0.24f;
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DisplayBoardBlockEntity be) {
        // 构造物上的方块实体离摄像机可能很远，不能因为「看起来在屏幕外」就被裁掉
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(DisplayBoardBlockEntity be) {
        // 文字可能超出方块一点，给渲染剔除留余量
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.5);
    }
}
