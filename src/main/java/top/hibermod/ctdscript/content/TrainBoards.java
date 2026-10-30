package top.hibermod.ctdscript.content;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

/**
 * 「按列车写车上显示板」的实现，单独成一个类。
 *
 * <h2>为什么不能直接写在 DisplayApi 里</h2>
 * 这里会碰 Create 的列车与构造物。而 {@code DisplayApi} 是脚本绑定时就会被加载的类
 * （`ScriptRuntime` 构造时把 `_ctd_display` 塞进绑定表）。
 * 一旦它自己引用了 Create 类型，**离线测试**（classpath 里没有 Create）
 * 就会在构造运行时的第一步崩掉——整套验收脚本全部跑不起来。
 *
 * <p>放到独立类里，只有真的调用 {@code display.train(...)} 时才加载，
 * 离线测试与不装 Create 的场景都不受影响。
 */
public final class TrainBoards {

    private TrainBoards() {}

    /** 按列车 id 写它车上的显示板。 */
    public static boolean write(String trainId, List<String> lines) {
        if (trainId == null || trainId.isBlank()) return false;

        UUID id;
        try {
            id = UUID.fromString(trainId);
        } catch (IllegalArgumentException e) {
            return false;
        }

        var train = com.simibubi.create.Create.RAILWAYS.trains.get(id);
        if (train == null) return false;

        List<String> copy = new ArrayList<>(lines == null ? List.of() : lines);

        for (var carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity == null) continue;
            if (ContraptionBoards.set((ServerLevel) entity.level(), entity, copy)) {
                return true;
            }
        }
        return false;
    }
}
