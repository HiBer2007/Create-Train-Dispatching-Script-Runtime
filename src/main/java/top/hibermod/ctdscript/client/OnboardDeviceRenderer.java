package top.hibermod.ctdscript.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdscript.content.OnboardDeviceBlockEntity;

/**
 * 车载设备的**按人**外观。
 *
 * <h2>为什么不能靠方块状态</h2>
 * 方块的外观是全局的：同一个 {@code BlockState}，所有人看到的一样。
 * 而「我看不看仪表」是每个人的事，所以外观差异不能写进 blockstate，
 * 只能放在**渲染器**里——渲染本来就是每帧、每个客户端各画各的。
 *
 * <p>于是：方块模型所有人都一样（一块金属面板），
 * 只有打开开关的人，才会在面板上看到那块发光的绿屏。
 *
 * <h2>不改变交互</h2>
 * 这台渲染器只影响「看起来」，不影响「能不能点」——点击走的是
 * {@code OnboardDeviceInteraction}，与服务端偏好。
 */
public class OnboardDeviceRenderer implements BlockEntityRenderer<OnboardDeviceBlockEntity> {

    /** 亮屏贴图，随 ctdscript 自己的资源包发布。 */
    public static final ResourceLocation SCREEN =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "textures/block/onboard_screen.png");

    public OnboardDeviceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(OnboardDeviceBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // 别人看不到：只有自己打开了指示器才画
        if (!ClientHudPrefs.enabled()) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(SCREEN));

        pose.pushPose();
        // 贴在方块顶面上方一点点，避免 z-fighting
        pose.translate(0, 1.002, 0);
        drawTopQuad(pose, vc, packedLight, packedOverlay, 0.2f, 0.8f);
        pose.popPose();
    }

    /** 在顶面上画一块 0.2–0.8 的小方片。 */
    private static void drawTopQuad(PoseStack pose, VertexConsumer vc, int light, int overlay,
                                    float from, float to) {
        var m = pose.last().pose();
        float y = 0f;
        // 顶点顺序：左下、右下、右上、左上（逆时针，面朝上）
        vc.addVertex(m, from, y, to).setColor(255, 255, 255, 255)
          .setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vc.addVertex(m, to, y, to).setColor(255, 255, 255, 255)
          .setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vc.addVertex(m, to, y, from).setColor(255, 255, 255, 255)
          .setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vc.addVertex(m, from, y, from).setColor(255, 255, 255, 255)
          .setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(OnboardDeviceBlockEntity be) {
        return false;
    }
}
