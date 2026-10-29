package top.hibermod.ctdscript.content;

import java.util.List;

import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * CBTC 显示源：把**列车此刻的移动授权**喂给 Create 的 DisplayLink。
 *
 * <h2>为什么做成 DisplaySource</h2>
 * Create 的 DisplayLink 已经能把「某个数据源」的文字推到辉光管、告示牌、翻页屏上，
 * 而且这些方块玩家已经在用。再自己造一个显示方块，等于让玩家多学一套东西，
 * 还要我们维护渲染与同步。
 *
 * <p>所以这里只提供**数据**，显示交给已有方块。这也正是 CRN 做显示器的方式。
 *
 * <h2>显示什么</h2>
 * 取距离 DisplayLink 最近的那列车（车没动也在范围内），显示三行：
 * <pre>
 * CBTC
 * 允许 45 km/h
 * 授权 320 格
 * </pre>
 * 距离之外没有车就显示 {@code --}，而不是空白——空白会让人以为显示器坏了。
 */
public class CbtcDisplaySource extends DisplaySource {

    /** 在多远之内认「这是本显示器的车」。 */
    private static final double RANGE = 32.0;

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        Train train = nearestTrain(context.getSourcePos());

        if (train == null) {
            return List.of(
                Component.literal("CBTC"),
                Component.literal("--"),
                Component.literal("--"));
        }

        double speed = Math.abs(train.speed);
        double kmh = speed * 72.0;                 // 格/刻 → km/h
        double authority = authorityOf(train);

        MutableComponent permitted = Component.literal(String.format("%.0f km/h", kmh));
        MutableComponent ma = authority <= 0
            ? Component.literal("--")
            : Component.literal(String.format("%.0f 格", authority));

        return List.of(Component.literal("CBTC"), permitted, ma);
    }

    /** 最近的列车；找不到返回 {@code null}。 */
    private static Train nearestTrain(BlockPos pos) {
        Train best = null;
        double bestDist = RANGE * RANGE;

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty()) continue;
            var entity = train.carriages.get(0).anyAvailableEntity();
            if (entity == null) continue;

            double d = entity.blockPosition().distSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = train;
            }
        }
        return best;
    }

    /**
     * 该车当前的移动授权。
     *
     * <p>数据来自我们自己的 CBTC 内核写下的值（{@code out.proceed} 的 authority）。
     * 取不到就返回 0，上层显示成 {@code --}——**不要**在这里临时编一个数字，
     * 显示器上出现一个看起来合理但没人算过的授权，比显示未知危险得多。
     */
    private static double authorityOf(Train train) {
        return top.hibermod.ctdscript.CtdScript.broadcastService() == null
            ? 0.0
            : top.hibermod.ctdscript.bridge.KernelAuthority.peek(train.id);
    }
}
