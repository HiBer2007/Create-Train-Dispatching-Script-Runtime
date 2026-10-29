package top.hibermod.ctdscript.content;

import java.util.LinkedHashSet;
import java.util.Set;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * 一列车装了什么车载设备 —— 由**构造物里实际存在的方块**决定。
 *
 * <h2>为什么从方块读，而不是从别处记</h2>
 * 设备是车的一部分：拆掉设备，能力就该消失；换到另一列车，就带不过去。
 * 任何「额外的登记表」都会和世界里的真实情况脱节，而脱节之后
 * 玩家会看到「我明明拆了设备，仪表还在亮」。
 *
 * <p>构造物里的方块数据（{@code StructureBlockInfo.nbt()}）随车保存与同步，
 * 所以客户端也能读到同一份事实——HUD 的闸门因此不需要额外网络通道。
 */
public final class TrainDevices {

    private TrainDevices() {}

    /** 这辆车装了哪些设备。 */
    public static Set<DeviceKind> of(AbstractContraptionEntity entity) {
        Set<DeviceKind> out = new LinkedHashSet<>();
        if (entity == null) return out;

        Contraption contraption = entity.getContraption();
        if (contraption == null) return out;

        for (StructureBlockInfo info : contraption.getBlocks().values()) {
            // 型号由**方块本身**决定：注册表里的事实，不需要读状态或 NBT
            if (info.state().getBlock() instanceof OnboardDeviceBlock device) {
                out.add(device.kind());
            }
        }
        return out;
    }

    /** 这列车装了 CBTC 车载设备吗——决定它能不能用 CBTC。 */
    public static boolean hasCbtcAtp(AbstractContraptionEntity entity) {
        return of(entity).contains(DeviceKind.CBTC_ATP);
    }

    /** 这列车该用哪套信号系统；没设备返回 {@code null}（退回固定闭塞）。 */
    public static String signalSystemOf(AbstractContraptionEntity entity) {
        for (DeviceKind kind : of(entity)) {
            if (kind.signalSystem() != null) return kind.signalSystem();
        }
        return null;
    }

    public static String summary(AbstractContraptionEntity entity) {
        Set<DeviceKind> kinds = of(entity);
        if (kinds.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (DeviceKind k : kinds) {
            if (sb.length() > 0) sb.append(',');
            sb.append(k.id());
        }
        return sb.toString();
    }
}
