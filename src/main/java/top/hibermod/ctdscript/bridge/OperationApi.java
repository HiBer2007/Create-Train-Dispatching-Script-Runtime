package top.hibermod.ctdscript.bridge;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.operation.Assignment;
import top.hibermod.ctdapi.operation.Assignments;
import top.hibermod.ctdapi.operation.Run;
import top.hibermod.ctdapi.operation.UnitNumber;
import top.hibermod.ctdapi.operation.UnitSet;

/**
 * {@code ctd.operation}：车辆编号 / 车组 / 车次。
 *
 * <pre>
 * from ctd import operation
 *
 * unit  = operation.unit("CRH2A-2013")
 * group = operation.set("mymod", "relay_01", "重联-01", [unit])
 * run   = operation.run("ctdapi:jinghu_up", "G1234", depart_at)
 * out.assign(operation.assignment("mymod", "job_1", run, [group]))
 * </pre>
 *
 * <p><b>这是能力，不是策略。</b> 桥接只负责「把这些车跑这个车次」这一件事；
 * 哪组车该跑哪个车次，是脚本自己的调度算法决定的。
 */
public final class OperationApi {

    public static final OperationApi INSTANCE = new OperationApi();

    @HostAccess.Export public final Assignment.Source KERNEL = Assignment.Source.KERNEL;
    @HostAccess.Export public final Assignment.Source EXTERNAL = Assignment.Source.EXTERNAL;
    @HostAccess.Export public final Assignment.Source OPERATOR = Assignment.Source.OPERATOR;

    private OperationApi() {}

    /** 车辆编号。 */
    @HostAccess.Export
    public UnitNumber unit(String value) {
        return new UnitNumber(value);
    }

    /** 车组：{@code units} 是有序的车辆编号列表。 */
    @HostAccess.Export
    public UnitSet set(String namespace, String path, String displayName, List<?> units) {
        List<UnitNumber> list = new ArrayList<>();
        for (Object o : units) {
            if (o instanceof UnitNumber u) {
                list.add(u);
            } else if (o instanceof String s) {
                list.add(new UnitNumber(s));       // 允许直接传字符串，少一层样板
            } else {
                throw new IllegalArgumentException(
                    "unit set member must be a unit number or string, got " + o);
            }
        }
        return new UnitSet(
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            Component.literal(displayName),
            list);
    }

    /** 单车车组。 */
    @HostAccess.Export
    public UnitSet single(String namespace, String path, String displayName, String unit) {
        return UnitSet.of(
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            Component.literal(displayName),
            new UnitNumber(unit));
    }

    /** 车次。 */
    @HostAccess.Export
    public Run run(String serviceId, String runNumber, long plannedDeparture) {
        return new Run(ResourceLocation.parse(serviceId), runNumber, plannedDeparture);
    }

    /** 组装一个指派。来源默认是 KERNEL。 */
    @HostAccess.Export
    public Assignment assignment(String namespace, String path, Run run, List<?> sets, long now) {
        List<UnitSet> list = new ArrayList<>();
        for (Object o : sets) {
            if (!(o instanceof UnitSet s)) {
                throw new IllegalArgumentException("assignment member must be a unit set, got " + o);
            }
            list.add(s);
        }
        return new Assignment(
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            run, list, Assignment.Source.KERNEL, now);
    }

    /** 来源可选的版本。 */
    @HostAccess.Export
    public Assignment assignmentFrom(String namespace, String path, Run run, List<?> sets,
                                     Assignment.Source source, long now) {
        Assignment a = assignment(namespace, path, run, sets, now);
        return new Assignment(a.id(), a.run(), a.sets(), source, now);
    }

    // ── 查询与撤销（脚本自己也要读）────────────────────────────────────────

    @HostAccess.Export
    public void publish(Assignment assignment) {
        Assignments.publish(assignment);
    }

    @HostAccess.Export
    public boolean revoke(String namespace, String path) {
        return Assignments.revoke(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** 某辆车当前跑的车次号；没有则空串。 */
    @HostAccess.Export
    public String run_of(String unit) {
        return Assignments.ofUnit(new UnitNumber(unit))
            .map(a -> a.run().runNumber())
            .orElse("");
    }

    /** 某车次由哪些车辆担当。 */
    @HostAccess.Export
    public List<String> units_of_run(String runNumber) {
        List<String> out = new ArrayList<>();
        for (Assignment a : Assignments.ofRun(runNumber)) {
            for (UnitNumber u : a.allUnits()) out.add(u.value());
        }
        return out;
    }

    /** 当前全部车次号。 */
    @HostAccess.Export
    public List<String> run_numbers() {
        return Assignments.runNumbers();
    }

    @HostAccess.Export
    public int count() {
        return Assignments.size();
    }
}
