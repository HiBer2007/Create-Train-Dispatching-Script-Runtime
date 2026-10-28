# Create Train Dispatching Script Runtime

把「注册信号制度 / 写调度内核 / 写 ATO」从 Java 变成 Python。

> **本模组只做一件事**：把 Python 的注册行为翻译成 CTD API 的 Java 注册调用。
> 它不实现任何调度逻辑，也不碰 Create。

- 依赖：`ctdapi`（契约层）+ GraalPy 24.1.2
- 许可证：LGPL-3.0-or-later

---

## 为什么需要它

CTD API 从设计之初就把信号、ATO、设备、HUD、PIS 全做成了**开放注册表**。
这意味着「加一套信号制度」不需要改核心代码——只需要**调用注册方法**。

既然如此，为什么必须是 Java？服务器管理员改一条限速规则、试一种新的闭塞逻辑，
不该要求他建一个 Gradle 工程。

于是有了这个驱动：**脚本层只需要做「注册的注册」**。

---

## 状态

| 能力 | 状态 |
|---|---|
| GraalPy 嵌入 + 沙箱 | ✅ 已实测 |
| `ctd.signal` 注册制度 / 机型 / 语义 | ✅ P0 通过 |
| `ctd.kernel` 注册调度内核 | ✅ P1 通过 |
| `ctd.ato` 注册 ATO | ✅ P2 通过 |
| `ctd.device` 注册设备类型 | ✅ |
| `ctd.timetable` 注册交路 | ✅ |
| `/ctd reload` 热重载 | ✅ 仅 kernel / ato |
| 连续失败自动禁用 | ✅ |

详见 `docs/12-scripting-driver.md`。

---

## 脚本放在哪

```
config/ctd/scripts/
├── signal/
├── device/
├── timetable/
├── kernel/
└── ato/
```

按目录顺序执行（signal → device → timetable → kernel → ato），
因为后面的脚本可能引用前面注册的 id。

全部脚本在 `FMLCommonSetupEvent` 执行，**必须在 `ApiCatalog.freezeAll()` 之前完成**。

### 最小示例

```python
# config/ctd/scripts/signal/mini.py
from ctd import signal

sys = signal.system("scripttest", "mini", "脚本测试制度")

t = signal.type("scripttest", "mini_2", sys.id())
t.lamp(signal.RED)
t.lamp(signal.GREEN)
t.expresses(signal.STOP, 0)
t.expresses(signal.PROCEED, 1)
t.semantics(signal.BLOCK)
t.register()

sys.palette(signal.RED, signal.GREEN)
sys.aspect(signal.STOP)
sys.aspect(signal.PROCEED)
sys.type(t)
sys.fallback(t)
sys.register()
```

---

## 命令

```
/ctd list      列出注册表内容（权限 2）
/ctd scripts   脚本加载报告（成功/失败）
/ctd reload    重载 kernel/ 与 ato/（其它注册表需重启服务器）
```

---

## 脚本示例

```python
# config/ctd/scripts/kernel/fixed_block.py
from ctd import kernel

class FixedBlock:
    def on_frame(self, ctx, out):
        for t in ctx.trains():
            if t.manually_driven() or t.derailed():
                continue
            if not t.has_route():
                out.hold(t.id(), "no route")
                continue
            ma = min(t.route_remaining(), t.speed_for(max(t.authority(), 0.0)))
            out.proceed(t.id(), ma, t.speed_for(max(ma, 0.0)))

kernel.register("mymod", "fixed_block", FixedBlock())
```

```python
# config/ctd/scripts/ato/simple_ato.py
from ctd import ato

class SimpleAto:
    def decide(self, ctx):
        if not ctx.can_traction(): return ato.stop("no traction power")
        if ctx.blocked_ahead():    return ato.stop("track closed")
        if ctx.authority() <= 0:   return ato.stop("no movement authority")

        limit = min(ctx.permitted_speed(), ctx.speed_class_limit(), ctx.strictest_limit())
        target = min(limit, ctx.speed_for_authority())
        dv = target - ctx.train().speed()
        if dv >= 0:
            return ato.decision(min(1.0, dv / 0.05), 0.0, target, ato.NONE, False, "accelerate")
        return ato.decision(0.0, min(1.0, -dv / 0.05), target, ato.NONE, False, "brake")

ato.register("mymod", "simple_ato", "ctdapi:mainline_4", SimpleAto())
```

---

## 沙箱

| 限制 | 实现 |
|---|---|
| 不能 `import java` | `allowHostClassLookup(name -> false)` |
| 不能反射进 JDK | `HostAccess.EXPLICIT`，只有 `@Export` 成员可见 |
| 不能碰文件系统 | 未开启 `allowIO` |
| 不能起进程/线程 | `allowCreateProcess(false)` / `allowCreateThread(false)` |
| 不能卡死主线程 | watchdog：超时调 `Context.interrupt` |

**信任模型**：脚本由服主放置，可信其意图，不可信其质量。
防护重点是死循环、异常、超时，不是恶意代码。

---

## 验证

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.5.11-hotspot'

.\gradlew.bat graalpySmokeTest   # 运行时能力（不启动游戏）
.\gradlew.bat signalBridgeTest   # P0 验收：脚本注册 → API 注册表 → freeze
.\gradlew.bat build              # 含打包
```

---

## 构建注记

- GraalPy 必须用 **24.1.x**（GraalVM for JDK 21）。25.x 面向 JDK 25。
- 依赖 `libs/create-train-dispatching-api-0.1.0.jar`。API 发布到 maven 后换成坐标即可。
- `maven.neoforged.net` 在部分网络下 TCP 不可达，`build.gradle` 已重写到 ForgeCDN。
