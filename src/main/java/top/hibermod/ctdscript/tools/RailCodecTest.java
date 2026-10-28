package top.hibermod.ctdscript.tools;

import java.util.Random;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.Rail;

/**
 * A1 验收：{@link Rail.NodeId} / {@link Rail.EdgeId} 的字符串编解码。
 *
 * <p>这条格式一旦发布就不能改——改了存档里的脚本配置会全部失联，
 * 所以在加方法的同时就把往返测试写死。
 */
public final class RailCodecTest {

    private RailCodecTest() {}

    public static void main(String[] args) {
        int failures = 0;

        // ① 固定样例：形状必须稳定
        ResourceKey<Level> overworld = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));

        Rail.NodeId a = new Rail.NodeId(overworld, 12, -64, 34, 0);
        String encoded = a.encode();
        failures += check("NodeId 编码形状", "minecraft:overworld@12,-64,34,0".equals(encoded), encoded);
        failures += check("NodeId 往返", a.equals(Rail.NodeId.decode(encoded)), encoded);

        Rail.NodeId b = new Rail.NodeId(overworld, 13, -64, 34, 16);
        Rail.EdgeId edge = Rail.EdgeId.of(a, b);
        String ee = edge.encode();
        failures += check("EdgeId 往返", edge.equals(Rail.EdgeId.decode(ee)), ee);
        failures += check("EdgeId 规范化（反向等价）",
            Rail.EdgeId.of(b, a).equals(Rail.EdgeId.decode(ee)), ee);

        // ② 随机往返
        Random rnd = new Random(42);
        ResourceLocation[] dims = {
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
            ResourceLocation.fromNamespaceAndPath("mymod", "custom_dim")
        };
        int bad = 0;
        for (int i = 0; i < 1000; i++) {
            ResourceKey<Level> dim = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dims[rnd.nextInt(dims.length)]);
            Rail.NodeId n = new Rail.NodeId(dim,
                rnd.nextInt(1 << 20) - (1 << 19),
                rnd.nextInt(512) - 128,
                rnd.nextInt(1 << 20) - (1 << 19),
                rnd.nextInt(17) - 8);
            if (!n.equals(Rail.NodeId.decode(n.encode()))) bad++;
        }
        failures += check("1000 次 NodeId 随机往返", bad == 0, bad + " failed");

        // ③ 拒绝坏输入（不能静悄悄返回错的东西）
        failures += check("拒绝缺少 @", Rail.NodeId.tryDecode("minecraft:overworld") == null, "accepted");
        failures += check("拒绝坐标数量不对", Rail.NodeId.tryDecode("minecraft:overworld@1,2,3") == null, "accepted");
        failures += check("拒绝非数字", Rail.NodeId.tryDecode("minecraft:overworld@a,b,c,d") == null, "accepted");
        failures += check("拒绝坏维度", Rail.NodeId.tryDecode("@@1,2,3,4") == null, "accepted");
        failures += check("拒绝缺竖线", Rail.EdgeId.tryDecode("minecraft:overworld@1,2,3,4") == null, "accepted");

        System.out.println();
        if (failures > 0) {
            System.out.println("A1 验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("A1 验收通过：Rail id 编解码稳定且能拒绝坏输入");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
