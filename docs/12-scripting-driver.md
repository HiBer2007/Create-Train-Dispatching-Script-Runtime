# 12 — 脚本驱动（ctd-script）实施方案

> 状态：**方案记录 + 前提验证**。代码骨架在 `mods/ctdscript`。
> 目标：用 **GraalPy** 把「注册信号制度 / 写调度内核 / 写 ATO」从 Java 变成 Python。
> 记录动机：这套方案横跨三层（GraalPy 嵌入、桥接、API 注册），细节多，必须落文档。

---

## 0. 一句话

> 用 GraalPy 做嵌入运行时，用「描述性 Python 对象 → 适配器 → Java 注册」做桥接，
> 把脚本限制在「注册 + 策略决策」这一层，每帧执行压在 15ms 以内，
> 重的优化任务交给外部进程或 Web 服务。

**关键前提**：现有 API 已经把信号、ATO、设备、HUD、PIS 全部做成**开放注册表**，
所以脚本层只需要做「注册的注册」，**不新增任何核心契约**。

---

## 1. 前提验证（已完成的部分）

### 1.1 GraalPy 的坐标与版本线

在 Maven Central 上核实过（`repo1.maven.org`，可达）：

| 坐标 | 用途 | 最新 |
|---|---|---|
| `org.graalvm.polyglot:polyglot` | 嵌入 API | 25.4.4.1.1 |
| `org.graalvm.polyglot:python-community` | Python 语言实现 | 25.4.4.1.1 |

**版本选型：`24.1.2`。**

理由：GraalVM 的版本线与 JDK 绑定——

| GraalVM | 对应 JDK |
|---|---|
| 24.1.x | **JDK 21**（本项目所用） |
| 24.2.x | JDK 23 |
| 25.x | JDK 25 |

本项目 `java.toolchain` 是 21，所以必须走 **24.1.x**。引用 25.x 会在运行期遇到
Truffle 与 JDK 版本不匹配。

> ⚠ 这条不是猜的：GraalVM 官方版本策略就是「每个 JDK LTS 一条 GraalVM 线」。
> 选错线的表现通常是 `Unsupported class file major version` 或
> `No language and polyglot implementation was found on the classpath`。

### 1.2 运行模式的现实

GraalPy 在 **stock OpenJDK** 上可以运行，但只有**解释器模式**——没有 Graal JIT。
真正有 JIT 加速需要跑在 GraalVM 上（或用 `libgraal`）。

这意味着：**「比 CPython 快 4 倍」这类数字在 stock JDK 上不成立**。
在本项目的部署形态里，应该按「解释执行、比 Java 慢一个数量级」来做容量规划。
如果性能不够，出路是：
1. 把热点逻辑留在 Java（这是默认做法）；
2. 改用 GraalVM 启动服务器（运维成本高，且 NeoForge 支持情况待验证）；
3. 把重计算移到外部进程。

### 1.3 已实测（2026-10）

`./gradlew graalpySmokeTest` —— 不启动游戏，验证运行时能力：

```
  ok   python 执行（1+2*3 = 7）
  ok   字符串格式化
  ok   import java 被拦截
  ok   调用 @Export 方法
  ok   非 @Export 方法不可见
  ok   ctd 包可 import（from ctd import greeter）
```

`./gradlew signalBridgeTest` —— P0 验收，验证「Python → 桥接 → API 注册表」全链路：

```
  ok   脚本执行完成
  ok   制度已注册: scripttest:mini (1 机型, 2 语义)
  ok   机型已注册: scripttest:mini_2 (2 灯)
  ok   语义映射正确
  ok   freezeAll() 通过
  registry entries: 25
```

**结论：技术前提成立。**

- GraalPy `24.1.2` 在普通 OpenJDK 21 上能跑；
- `HostAccess.EXPLICIT` + `allowHostClassLookup(false)` 的沙箱按预期工作；
- `sys.modules['ctd']` 注入可行，脚本能写 `from ctd import signal`；
- 脚本注册的信号制度能通过 API 的 `freeze()` 校验。

### 1.4 尚未验证的宣称

方案原文引用了两个工程先例和若干性能数字，**我尚未核实**：

| 宣称 | 状态 |
|---|---|
| 「XLLogic 在 NeoForge 1.21.1 上跑通了 GraalPy」 | ⚠ 未核实。未给仓库地址，无法确认 |
| 「GraalPy 比 CPython 快 4 倍」 | ⚠ 仅在 GraalVM JIT 下成立，stock JDK 不适用 |
| 「Jython 某些场景比 CPython 慢 10 倍」 | ⚠ 未核实；但 Jython 停滞于 Python 2.7 是公认事实，足以否决 |
| 「Pyjinn 解析器在 JVM 内」 | ⚠ 未核实 |

**本项目不依赖这些宣称**：实测已经证明 GraalPy 可用，剩下的只是性能是否够用，
而性能要靠 §9 的实测数据说话。

---

## 2. 选型：为什么是 GraalPy

| 方案 | 结论 | 理由 |
|---|---|---|
| **GraalPy（嵌入）** | ✅ 主路径 | Python 3 语义完整；可与 Java 对象互操作；进程内无 IPC 延迟 |
| Jython | ❌ | 停滞在 Python 2.7；生态断代；性能不可预期 |
| 外部 CPython 进程 | ⚠ 补充 | 生态最全、隔离最好，但 **IPC 往返吃掉 tick 预算**，不适合每帧 `onFrame` |

**结论**：每帧决策走 GraalPy；**异步、非帧关键**的任务（时刻表优化、大规模分析）
走外部进程或 Web 服务。

这条分工与 API 的 `remote` 包天然吻合——`RemoteKernel` 本来就是为进程外引擎准备的。

---

## 3. 三个模组的分工

```
ctd-api     契约（已存在，无游戏行为）
    ↑
ctd-host    宿主：拓扑镜像 / 遥测 / 指令执行 / 存档        （未开工）
    ↑
ctd-script  ★ 本方案：GraalPy + 注册桥接                  （骨架已建）
    ↑
ctd-web     可选：WebSocket 外壳（复用 API 的 RemoteKernel）（未开工）
```

**`ctd-script` 只依赖 `ctd-api`**，不需要 Create，也不需要宿主。
它做的事只有一件：把 Python 的注册行为翻译成 Java 的注册调用。

> 这是刻意的：脚本驱动不该关心 Create 怎么工作。
> 它只是一个「往注册表里塞东西」的工具。

---

## 4. `ctd-script` 的内部结构

```
top.hibermod.ctdscript
├── CtdScript                 @Mod 主类；生命周期与 freeze 顺序
├── ScriptRuntime             GraalPy Context 管理 + 沙箱 + watchdog
├── ScriptLoader              扫描 config/ctd/scripts，分类执行
├── ScriptRegistry            记录脚本注册了什么（reload 时按此回滚）
├── bridge/
│   ├── SignalApi             ctd.signal：制度 / 机型 / 语义 / 灯色
│   ├── KernelApi             ctd.kernel：注册 DispatchKernel
│   ├── AtoApi                ctd.ato：注册 AtoEngine
│   ├── DeviceApi             ctd.device：注册设备类型
│   └── TimingApi             ctd.timetable：注册交路
├── adapters/
│   ├── PythonKernel          Python 对象 → DispatchKernel
│   ├── PythonAto             Python 对象 → AtoEngine
│   └── BridgeUtil            Value ↔ 值对象转换
├── command/CtdCommand        /ctd reload · /ctd list · /ctd disable
└── tools/GraalPySmokeTest    不启动游戏的烟测
```

### 4.1 沙箱配置

```java
HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
    .allowListAccess(true)      // ★ 见下面那条坑
    .allowMapAccess(true)
    .allowArrayAccess(true)
    .build();

Context.newBuilder("python")
    .allowHostAccess(access)                   // 只有 @HostAccess.Export 成员可见
    .allowHostClassLookup(name -> false)       // 禁止 import java / 反射找类
    .allowCreateProcess(false)
    .allowCreateThread(false)
    .option("python.ForceImportSite", "false")
    .build();
```

**四条禁令**：不暴露 `java` 包、不允许文件系统、不允许起进程、不允许起线程。

> ### ⚠ 踩过的坑：`EXPLICIT` 不等于「能遍历集合」
>
> `HostAccess.EXPLICIT` 管的是**成员可见性**，集合访问是另外三个开关。
> 只开 EXPLICIT 时，门面返回的 `java.util.List` 在 Python 里**不是 iterable**：
>
> ```
> TypeError: foreign object is not iterable
> ```
>
> 表现是脚本一写 `for t in ctx.trains():` 就整帧失败，而报错还被
> 「本帧丢弃 → 退回原生」吞掉，很难定位。必须显式 `allowListAccess(true)`。
>
> 顺带：开了之后 `len(ctx.trains())` 也能用了。

### 4.2 `ctd` 包的注入

GraalPy 的 bindings 是全局变量，不是模块。为了支持 `from ctd import signal`，
在 Context 创建后执行一段 bootstrap：

```python
import sys, types
_ctd = types.ModuleType("ctd")
_ctd.signal    = _ctd_signal
_ctd.kernel    = _ctd_kernel
_ctd.ato       = _ctd_ato
_ctd.device    = _ctd_device
_ctd.timetable = _ctd_timetable
sys.modules["ctd"] = _ctd
```

之后用户脚本写 `from ctd import signal` 即可。

> 这段 bootstrap 已在烟测里验证可行（见 `GraalPySmokeTest` ⑤）。

---

## 5. Python 侧 API 的形状

### 5.1 信号：描述性对象 + 显式注册

```python
from ctd import signal

system = signal.System("ctd", "mainline_4", "干线四显示")

home4 = signal.Type("ctd", "home_4", system.id)
home4.lamp(signal.RED)
home4.lamp(signal.YELLOW)
home4.lamp(signal.YELLOW)
home4.lamp(signal.GREEN)
home4.expresses(signal.STOP, 0)
home4.expresses(signal.CAUTION, 1)
home4.expresses(signal.ADVANCE_CAUTION, 1, 2)
home4.expresses(signal.PROCEED, 3)
home4.semantics(signal.HOME)

signal.register_type(home4)
signal.register_system(system)
```

**设计要点**：

- `System` / `Type` 是桥接层暴露的**宿主对象**（`@HostAccess.Export`），
  Python 拿到的是 Java 实例的包装。链式调用就是普通方法调用，零解析成本。
- `register_*` 是**显式**的。不注册就等于没定义——避免「对象构造了但忘了注册」
  这种最难查的错误。
- 注册顺序无关：桥接层先收集，在 `ApiCatalog.freezeAll()` 之前统一落库。

### 5.2 内核：一个对象，一个方法

```python
from ctd import kernel

class FixedBlockKernel:
    def on_frame(self, ctx, out):
        for t in ctx.trains():
            if t.manually_driven() or t.derailed():
                continue
            if not t.has_route():
                out.hold(t.id(), "no route")
                continue
            ma = min(t.route_remaining(), t.speed_for(max(t.authority(), 0.0)))
            out.proceed(t.id(), ma, t.speed_for(max(ma, 0.0)))
        out.log("frame %d: %d trains" % (ctx.game_time(), len(ctx.trains())))

kernel.register("mymod", "fixed_block", FixedBlockKernel())
```

**注意 id 是显式传的**：原方案让脚本实现 `id()` 返回元组，但桥接层得去猜返回值类型
（列表？元组？字符串？），猜错一次就是一个难查的启动失败。显式参数只有一种写法。

**关键性能设计**：`on_frame` **一次调用处理整批列车**，
而不是「每列车一次跨语言调用」。跨语言调用是主要开销来源。

### 5.3 ATO：必须保留「一个 ATO ⟷ 一套信号制度」

```python
from ctd import ato

class SimpleAto:
    def decide(self, ctx):
        if ctx.under_emergency():        return ato.stop("emergency")
        if not ctx.can_traction():       return ato.stop("no traction power")
        if ctx.blocked_ahead():          return ato.stop("track closed")
        if ctx.authority() <= 0:         return ato.stop("no movement authority")

        limit = min(ctx.permitted_speed(), ctx.speed_class_limit(), ctx.strictest_limit())
        target = min(limit, ctx.speed_for_authority())

        dv = target - ctx.train().speed()
        if dv >= 0:
            return ato.decision(min(1.0, dv / 0.05), 0.0, target, ato.NONE, False, "accelerate")
        return ato.decision(0.0, min(1.0, -dv / 0.05), target, ato.NONE, False, "brake")

ato.register("mymod", "simple_ato", "ctdapi:mainline_4", SimpleAto())
```

`decide` 返回 `ato.decision(...)` / `ato.stop(...)` / `ato.coast(...)`，
返回 `None` 等价于「本帧不干预」。

**桥接层不做任何放宽**：`ato.register` 只是把 Python 对象包成 `AtoEngine`，
交给 `AtoEngines.register`。**「制度存在且唯一」的校验仍在 API 的 `freeze()` 里**。
脚本绕不过去，这正是「脚本层不入侵核心逻辑」的含义。

---

## 6. 注册桥接：三种转换

| Python 给的东西 | Java 要的东西 | 转换方式 |
|---|---|---|
| 宿主描述对象（`signal.Type`） | `SignalType` 等 record | 桥接层内部就是 Java 对象，直接建 |
| 带方法的 Python 对象（内核/ATO） | 接口实现 | 动态代理或手写适配器 |
| 纯数据（dict/list/str） | 值对象 | `BridgeUtil` 逐字段映射 |

**内核与 ATO 用适配器而不是动态代理**：代理每次调用都要走反射，
而 `on_frame` / `decide` 是热路径。手写一个 `PythonKernel implements DispatchKernel`
把调用直接转发到 `Value.invokeMember("on_frame", ...)`，省一层。

---

## 7. 加载顺序

必须严格对齐 API 的生命周期：

```
mod 构造期          → 创建 Context，预热解析器（不执行用户脚本）
FMLCommonSetupEvent → 扫描 config/ctd/scripts，执行全部脚本，收集注册
                     → 落库到 SignalRegistries / AtoEngines / DispatchKernels
FMLLoadCompleteEvent→ ApiCatalog.freezeAll()
```

**脚本执行必须在 freeze 之前完成。** freeze 之后 `register*` 会抛
`IllegalStateException`，这是 API 的保护，不是 bug。

目录约定：

```
config/ctd/scripts/
├── signal/      信号制度、机型、语义
├── ato/         ATO 引擎
├── kernel/      调度内核
├── device/      设备类型
└── timetable/   交路
```

**按目录决定执行顺序**（signal → device → timetable → kernel → ato），
因为后注册的东西可能引用前面注册的 id。

---

## 8. 安全模型

| 威胁 | 对策 |
|---|---|
| 脚本读宿主机文件 | `allowIO(NONE)` |
| 脚本起进程/线程 | `allowCreateProcess(false)` / `allowCreateThread(false)` |
| 脚本反射进 JDK | `allowHostClassLookup(-> false)`，`import java` 失败 |
| 脚本卡死主线程 | watchdog（见下） |
| 脚本改已冻结的注册表 | freeze 后桥接层拒绝注册并记录警告 |
| 脚本抛异常 | 宿主捕获 → **丢弃该帧全部指令** → 降级到 Create 原生 |

**信任模型**：脚本是服务器管理员放的，可以信任其**意图**，但不能信任其**质量**。
所以防护重点不是恶意代码，而是**死循环、异常、超时**这三种「会让服务器卡住」的情况。

### 8.1 watchdog 的现实

真正的抢占式中断在 JVM 上很难做干净。GraalPy 提供了 `Context.interrupt(Duration)`，
可以由另一个线程触发。实现方式：

```
主线程:            调用 on_frame
watchdog 线程:     sleep(limit) → 若主线程还在本帧内 → context.interrupt()
宿主:             捕获 PolyglotException(interrupted) → 本帧降级
```

**它能停住脚本，但不能保证停在安全点**。所以还有第二道防线：
计时统计——连续 N 帧超限就自动禁用该脚本（`/ctd disable <id>` 的等价行为）。

---

## 9. 性能红线

| 指标 | 目标 | 依据 |
|---|---|---|
| `on_frame` 单次执行 | **< 15ms** | tick 预算 50ms，留 35ms 给宿主与 Create 物理 |
| 启动期脚本解析 | **不占 tick** | 放在构造期或异步完成 |
| 跨语言调用 / 帧 | **O(1) 次 on_frame**，不是 O(列车数) | 批量传值对象 |

**绝对红线**：不要把逐 tick 的密集数值优化放进 Python。
Python 负责**策略编排**（条件判断、状态机、规则），
数值优化留在 Java 或外部进程。

### 9.1 必须实测

- 空 `on_frame` 的跨语言调用开销；
- 遍历 50 / 200 / 1000 列车的开销；
- `ctd.signal` 注册 100 个机型的启动耗时。

在拿到这些数字之前，方案只能算**待验证**。

---

## 10. 热重载语义

`/ctd reload` 的行为：

```
① 关闭旧运行时（Context.close）
② 新建运行时
③ 只重跑 kernel/ 与 ato/ 目录
④ 注册走 API 的 replace(...) 而不是 register(...)
```

**只有 kernel 与 ato 支持热重载。** 信号制度 / 设备 / 交路一旦 `freeze()` 就不能增量修改，
重载它们必须重启服务器——这是 API 的约束，不是偷懒。

为此 API 增加了两个方法（`DispatchKernels` / `AtoEngines`）：

```java
replace(id, factory)   // 同 id 换实现，不抛异常
remove(id)             // 注销
```

**它们刻意与 `register` 分开**：正常注册仍然重复即抛，
否则「重复注册」这个最有价值的早期错误就被吞掉了。只有热重载路径才用 `replace`。

**窗口期**：`①→③` 之间没有内核在发指令。按 API 的「零指令 = 不干预」原则，
列车会**退回 Create 原生行为**一个 tick 左右。这是可接受的降级，不是 bug——
但必须在文档里讲清楚，否则会被当成「reload 时列车抽风」。

### 10.1 连续失败自动禁用

`PythonKernel` 统计连续失败次数，达到 10 次就**把自己变成空操作**：

```
连续失败 → 日志（第 1 次与每 5 次）→ 第 10 次后禁用 + 一条 error 日志
```

禁用不是注销——宿主仍认为内核存在，只是不再写指令，效果就是退回原生。
之所以不注销：**脚本自己炸了的时候，没人能保证宿主还愿意配合注销**。

---

## 11. 路线图

| 阶段 | 目标 | 验收 |
|---|---|---|
| **P0** | GraalPy 嵌入跑通，加载一个注册最小信号制度的脚本 | 启动无报错，`ApiCatalog.entries()` 能看到脚本注册的制度 |
| **P1** | 脚本实现 `FixedBlockKernel`，驱动列车 | 列车按脚本逻辑走/停；脚本异常时降级到原生 |
| **P2** | 脚本实现 `SimpleAto` 并绑定已注册制度 | `AtoEngines.freeze()` 通过，自动驾驶可启动 |
| **P3** | 脚本注册设备类型并桥接 `DeviceCommand` | 道岔可扳，计轴可读 |
| **P4** | 热重载 + watchdog + 沙箱完备 | `/ctd reload` 生效；超时脚本被丢弃 |
| **P5** | 对接 `ctd-web`（复用 `RemoteKernel`） | 浏览器扣车，脚本内核下一帧响应 |
| **P6** | 可选：外部进程处理异步优化 | 重优化不占游戏 tick |

**当前进度**：

| 阶段 | 状态 |
|---|---|
| P0 | ✅ **通过**（`signalBridgeTest`）：GraalPy 嵌入、沙箱、`ctd` 包注入、信号制度注册、freeze 校验 |
| P1 | ✅ **通过**（`kernelAtoBridgeTest`）：脚本内核驱动列车、异常时退回原生 |
| P2 | ✅ **通过**：脚本 ATO 绑定制度、授权 0 时停车、freeze 收下注册 |
| P3 | 部分可用：`ctd.device.registerType` / `ctd.timetable.registerService` 已实现；道岔锁闭未接 |
| P4 | 部分可用：watchdog + 连续失败自动禁用 + `/ctd reload`（仅 kernel/ato）已做；信号/设备热重载需重启 |
| P5/P6 | 未开始 |

**P1/P2 实测记录**（`./gradlew kernelAtoBridgeTest`，不启动游戏）：

```
  ok   内核产出了 2 条列车指令
  ok   有进路的车收到授权 > 0
  ok   无进路的车被扣停
  ok   脚本日志已转发
  ok   ATO 绑定到 ctdapi:mainline_4
  ok   ATO 返回了决策
  ok   目标速度落在授权允许范围内
  ok   原因非空
  ok   授权为 0 时停车
  ok   热重载：同 id 重新注册未抛异常
  ok   freezeAll() 通过（含脚本注册的内核与 ATO）
```

---

## 12. 风险清单

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | GraalPy 在 stock JDK 上只有解释器模式，性能可能不达标 | 高 | P0 后立刻实测；不达标则把逻辑留在 Java |
| R2 | GraalPy 与 NeoForge 的模块系统/类加载器冲突 | 中 | 尽早跑一次真实服务端启动 |
| R3 | 打包体积（GraalPy 语言包很大） | 中 | 评估 jar-in-jar 与发行渠道 |
| R4 | watchdog 无法真正抢占 | 中 | 计时统计 + 连续超限自动禁用 |
| R5 | 脚本 API 面一旦扩大就难收回 | 中 | 只暴露 `ctd.*` 五个模块，不暴露 `java` |
| R6 | 热重载窗口期列车退化 | 低 | 文档写明；可做成「先注册新再停旧」 |

---

## 13. 与现有 API 的映射

| Python 调用 | Java 落点 |
|---|---|
| `signal.register_system(...)` | `SignalRegistries.registerSystem(...)` |
| `signal.register_type(...)` | `SignalRegistries.registerType(...)` |
| `signal.register_aspect(...)` | `SignalRegistries.registerAspect(...)` |
| `kernel.register(obj)` | `DispatchKernels.register(id, () -> new PythonKernel(obj))` |
| `ato.register(obj)` | `AtoEngines.register(new PythonAto(obj))` |
| `device.register_type(...)` | `Devices.register(...)` |
| `timetable.register_service(...)` | `Service.register(...)` |
| 全部注册完成后 | `ApiCatalog.freezeAll()` |

**没有新增任何契约。** 这是整个方案成立的根本原因：
API 从设计之初就是开放注册表，脚本层只是换了个注册的写法。

---

## 14. 加载时机：定义 vs 算法（强制）

判据不是「改起来方不方便」，而是**这东西有没有被别处引用**。

| 目录 | 阶段 | 内容 | 为什么 |
|---|---|---|---|
| `signal/` | **启动** | 信号制度、机型、灯位、语义映射 | 设备、内核、HUD 全按 id 引用它；换掉等于换规则，运行中的车会按旧规则决策 |
| `device/` | **启动** | 地面设备、车载设备类型 | 设备实例已绑在轨道边点上，类型换了实例变孤儿 |
| `timetable/` | **启动** | 交路、停站表、速度等级 | 车次与运行图按 id 引用交路 |
| `kernel/` | **可热重载** | 调度算法：往哪走、怎么让行 | 无长期引用，换实现只是换策略 |
| `ato/` | **可热重载** | 自动驾驶算法：加减速曲线 | 每帧重算 |

**强制手段**：重载阶段调用定义类注册会**直接抛异常**，不静默忽略。
静默忽略会让作者以为改了、实际没生效——这种问题最难查。

```
cannot register signal system during hot reload: definitions
(signal systems, device types, services) are startup-only.
Move this script into kernel/ or ato/ if it is an algorithm,
or restart the server if it really is a definition.
```

验收：`./gradlew loadPhaseTest`
```
ok   启动阶段允许注册信号制度
ok   重载阶段被拒且给出可操作提示
ok   重载阶段允许注册内核
```

### 12.1 边界情况

- 同一份脚本里既注册制度又注册内核 → 整份按启动阶段处理，重载时**整份跳过**并报错。
- 想只改算法又不想重启 → 把算法单独放 `kernel/`，别和定义混在一个文件里。

---

## 21. 第十一轮：两个模组一起跑，暴露三个问题

### 环境

把 ctdscript 的 jar 放进 ctdhost 的 `run/mods`，让宿主和脚本运行时同时加载。

```
Create Train Dispatching API 0.1.0 (ctdapi)
Create Train Dispatching Script Runtime 0.1.0 (ctdscript)
[ctdhost]: ctdhost: constructing
```

**共存本身是通的**：三个模组都在模组列表里，`/ctd` 没有命令冲突。

### 问题一：诊断交路引用不存在的信号制度（已修）

```
[ctdscript] registry validation failed:
  - service ctdapi:diagnostic uses unknown signal system ctdapi:diagnostic
```

上一轮我在 `DiagnosticKernel` 里注册了一条交路，但**没注册它引用的信号制度**。
单跑 ctdhost 时没有 `freeze` 流程，所以一直没暴露；ctdscript 启动会 `freezeAll`，立刻抓到。

修法：注册一个真实的诊断制度 + 机型。

### 问题二：校验器拦下「停不了车的制度」（已修）

注册了制度之后：

```
[ctdscript] registry validation failed:
  - ctdapi:diagnostic has no STOP-category aspect (a signal system that cannot stop trains)
```

**这是 API 自己的安全规则在起作用**，拦得完全正确。
补上 `SignalRegistries.bootstrap()` 与 `.aspect(Builtin.STOP)` 后通过（`registries frozen, 40 entries`）。

### 问题三：GraalPy 在 NeoForge 里加载不了（**未解决**）

```
[ctdscript] scripting unavailable: java.lang.NoClassDefFoundError: org/graalvm/polyglot/PolyglotException
```

排查过程：

| 尝试 | 结果 |
|---|---|
| 把 GraalPy 放进 `run/mods` | 无效——它没有 `mods.toml`，不是模组，也不会进 classpath |
| 改成 `runtimeOnly fileTree(libs/graalpy)` | 无效 |
| 在 ctdscript 自己的工程里起服务端 | 无效（同样报错，而单元测试里是好的） |
| 剥掉 10 个 jar 的 `module-info.class` | 无效 |

**根因**：NeoForge 用模块层加载模组，而 GraalPy 的 jar 只挂在普通 classpath 上，
模组模块读不到它。`docs/12` 把这条记为风险 R2（「GraalPy 与 NeoForge 的模块系统冲突」），
当时的对策是「尽早跑一次真实服务端启动」——**现在跑了，它发生了**。

**下一步**：用 **JarJar**（jar-in-jar）打包。FML 会给嵌套 jar 建立正确的模块条目，
这也是 NeoForge 生态里捆绑大型库的标准做法。同时它顺带解决 R3（体积）的发行方式问题。

### 这一轮的价值

单独跑任何一个模组都发现不了这三个问题。尤其是第三个：
它不崩服，只是**静默降级**为「脚本不可用」，不跑真实服务端永远看不到。
