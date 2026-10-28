package top.hibermod.ctdscript.tools;

import java.util.ArrayList;
import java.util.List;

import top.hibermod.ctdapi.operation.Assignment;
import top.hibermod.ctdapi.operation.Assignments;
import top.hibermod.ctdapi.operation.UnitNumber;
import top.hibermod.ctdscript.ScriptRuntime;

/**
 * 运营身份验收：一组车组可以开行某个具体车次，且改派覆盖旧指派。
 */
public final class OperationBridgeTest {

    private OperationBridgeTest() {}

    private static final String SCRIPT = String.join("\n",
        "from ctd import operation",
        "",
        "u1 = operation.unit('CRH2A-2013')",
        "u2 = operation.unit('CRH2A-2014')",
        "group = operation.set('mymod', 'relay_01', '重联-01', [u1, u2])",
        "",
        "run = operation.run('ctdapi:jinghu_up', 'G1234', 100)",
        "a = operation.assignment('mymod', 'job_1', run, [group], 100)",
        "operation.publish(a)",
        "",
        "print('count', operation.count())",
        "print('run_of', operation.run_of('CRH2A-2013'))",
        "print('units', ','.join(operation.units_of_run('G1234')))");

    private static final String REASSIGN = String.join("\n",
        "from ctd import operation",
        "",
        "u1 = operation.unit('CRH2A-2013')",
        "single = operation.single('mymod', 'lone', '单机', 'CRH2A-2013')",
        "run2 = operation.run('ctdapi:jinghu_up', 'G5678', 200)",
        "operation.publish(operation.assignment('mymod', 'job_2', run2, [single], 200))",
        "print('after', operation.run_of('CRH2A-2013'))",
        "print('count', operation.count())");

    public static void main(String[] args) {
        int failures = 0;
        Assignments.clear();

        try (ScriptRuntime rt = new ScriptRuntime()) {
            rt.eval(SCRIPT, "optest/assign.py");

            failures += check("指派已登记", Assignments.size() == 1, "size=" + Assignments.size());

            var ofUnit = Assignments.ofUnit(new UnitNumber("CRH2A-2013"));
            failures += check("按车辆编号反查到车次",
                ofUnit.isPresent() && ofUnit.get().run().runNumber().equals("G1234"),
                String.valueOf(ofUnit.map(a -> a.run().runNumber()).orElse("none")));

            var ofRun = Assignments.ofRun("G1234");
            failures += check("一个车次由两组车辆担当",
                ofRun.size() == 1 && ofRun.get(0).allUnits().size() == 2,
                "assignments=" + ofRun.size());

            // 改派：同一辆车换到另一个车次，旧指派里的这辆车要被摘掉
            rt.eval(REASSIGN, "optest/reassign.py");

            var moved = Assignments.ofUnit(new UnitNumber("CRH2A-2013"));
            failures += check("改派后车辆已换到 G5678",
                moved.isPresent() && moved.get().run().runNumber().equals("G5678"),
                String.valueOf(moved.map(a -> a.run().runNumber()).orElse("none")));

            var old = Assignments.ofRun("G1234");
            boolean oldTrimmed = old.isEmpty()
                || old.get(0).allUnits().stream().noneMatch(u -> u.value().equals("CRH2A-2013"));
            failures += check("旧指派里这辆车已被摘掉", oldTrimmed, "still present");

            // 撤销
            bothRevoke(failures);
        } catch (Throwable t) {
            System.out.println("  FAIL 未捕获异常: " + t);
            failures++;
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("运营身份验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("运营身份验收通过：车组可开行车次，改派覆盖旧指派");
    }

    private static void bothRevoke(int failures) {
        List<String> ids = new ArrayList<>();
        for (Assignment a : Assignments.all()) ids.add(a.id().toString());
        for (String id : ids) {
            net.minecraft.resources.ResourceLocation rl =
                net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl != null) Assignments.revoke(rl);
        }
        boolean ok = Assignments.size() == 0;
        System.out.println((ok ? "  ok   " : "  FAIL ") + "撤销后指派表清空");
        if (!ok) System.exit(1);
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
