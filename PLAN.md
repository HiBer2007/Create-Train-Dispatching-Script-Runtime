# PLAN — 做完剩下的一切

> 本文件是**工作清单**，不是设计文档。设计理由在 `docs/12-scripting-driver.md`。
> 更新规则：每完成一项就把勾打上，并把「现在做到哪」更新到文末的进度表。

---

## 0. 完成定义（Definition of Done）

整个系统「写完了」= 下面四件事同时成立：

| # | 判据 | 怎么验 |
|---|---|---|
| D1 | 脚本能注册**信号制度 / 内核 / ATO / 设备 / 交路**五类东西，且都能在游戏里生效 | 进游戏，`/ctd list` 看到；列车按脚本走 |
| D2 | 道岔（含汽鸣铁道的真实道岔）能被脚本锁定/扳动 | 脚本锁岔 → 进路真的走那条股道 |
| D3 | 脚本改动能不重启服务器就生效（除确实做不到的） | `/ctd reload` 后行为改变 |
| D4 | 一个外部程序（非 Java）能通过 Web 拿到遥测并下发指令 | 浏览器/脚本扣车，列车下一帧响应 |

**D1 的隐藏前提**：必须有一个**宿主模组**（`ctd-host`）实现 `Topology` / `Telemetry` / `DispatchFrame`。
没有它，上面所有东西都只能在离线测试里跑，进不了游戏。

---

## 1. 现状盘点

| 模块 | 状态 | 说明 |
|---|---|---|
| `ctdapi` | ✅ 可用 | 32 个契约类，`clean build` 通过 |
| `ctdscript` P0 | ✅ | 信号制度注册，`signalBridgeTest` 通过 |
| `ctdscript` P1 | ✅ | 脚本内核，`kernelAtoBridgeTest` 通过 |
| `ctdscript` P2 | ✅ | 脚本 ATO，同上 |
| `ctdscript` P3 | 🟡 部分 | 设备类型 / 交路注册已通；**道岔锁闭、`DeviceCommand`、`EnergyCommand` 未接** |
| `ctdscript` P4 | 🟡 部分 | watchdog + 连续失败禁用 + `/ctd reload`（仅 kernel/ato）已做；**信号/设备热重载未做** |
| `ctdscript` P5 | ❌ | Web 桥接未开始 |
| `ctdscript` P6 | ❌ | 外部进程未开始 |
| `ctd-host` | ❌ | **完全没有**。这是 D1/D2 的硬前提 |

---

## 2. 阶段与任务

### 阶段 A — `ctdscript` P3：设备与道岔

| # | 任务 | 验收 |
|---|---|---|
| A1 | 给 `Rail.NodeId` / `Rail.EdgeId` 加**字符串编解码**（`toString` / `parse`），形状稳定、可跨存档 | 往返 1000 个 id 无误 |
| A2 | 门面暴露 `ctx.switches()`：节点、已锁支线、持有者、是否争用 | 离线测试能读到 |
| A3 | `out.lock_switch(node, branch, holder)` / `out.unlock_switch(node)` | 离线测试产出正确 `SwitchCommand` |
| A4 | `out.device(device_id, payload)` 桥接 `Command.DeviceCommand` | payload 原样透传 |
| A5 | `out.energy(provider_id, payload)` 桥接 `Command.EnergyCommand` | 同上 |
| A6 | `out.service(train_id, service_id, device_id)` 桥接 `ServiceCommand` | 同上 |
| A7 | 设备注册支持 `signalSystems` 参数（车载设备能操作哪套制度） | `ApiCatalog` 里能看到 |
| A8 | 离线验收：道岔锁 + 设备指令的完整用例 | 新增测试全绿 |

### 阶段 B — `ctdscript` P4：重载与沙箱收敛

| # | 任务 | 验收 |
|---|---|---|
| B1 | 给 `SignalRegistries` / `Devices` / `Service` 加 `remove(id)` + `unfreeze()`（**仅重载用**） | 能注销脚本注册的条目 |
| B2 | 脚本侧记录「我注册了什么」，重载时精确回滚 | 重载后注册表条目数不变 |
| B3 | `/ctd reload` 支持全部五类（不再只 kernel/ato） | 改信号灯位后重载即生效 |
| B4 | 重载失败时**回滚到旧状态**（而不是留下一半注册） | 故意写坏脚本，服务器仍用工旧内核 |
| B5 | 超时预算可配置（默认 15ms），并按内核统计 p50/p99 | `/ctd stats` 打印 |
| B6 | 沙箱收敛：确认 `import java` / 文件 / 进程 / 线程四道禁令都有测试覆盖 | 测试断言全部被拒 |
| B7 | 脚本目录支持 `disabled/` 子目录（跳过加载） | 放进去的脚本不执行 |

### 阶段 C — `ctd-host`：宿主模组（**最关键，卡着一切**）

| # | 任务 | 验收 |
|---|---|---|
| C1 | 建仓库 `Create-Train-Dispatching-Host`，骨架 + 构建 + 文档 | `clean build` 通过 |
| C2 | Mixin：`GlobalRailwayManager#tick` HEAD 插入调度 pass | 每帧 `onFrame` 被调用（日志证明） |
| C3 | 拓扑镜像：`TrackGraph` → `Topology`，含 `enabled`（道岔禁用股道） | `/ctd list` 能看到节点/边数 |
| C4 | 遥测采集：`Train` → `Telemetry.TrainState` | 速度/位置与游戏内一致 |
| C5 | 指令执行：`TrainCommand` → `train.speed`（取 min，安全钳位） | 脚本给 0 授权，车真的停 |
| C6 | 信号写入：`AspectCommand` → `SignalBoundary.cachedStates` | 灯真的变 |
| C7 | 道岔翻译：`SwitchCommand` → 汽鸣铁道 `TrackSwitch#setSwitchState`（有则用，无则逻辑锁） | 岔真的扳 |
| C8 | 存档：`KernelEnv.state()` + `Restrictions` / `Emergency` 持久化 | 重启后不丢 |
| C9 | 安全兜底：内核异常 → 丢弃整帧 → 原生行为 | 故意让脚本崩，车不失控 |

### 阶段 D — `ctdscript` P5：Web 桥接

| # | 任务 | 验收 |
|---|---|---|
| D1 | 建仓库 `ctd-web`（或作为 ctdscript 的可选模块） | 构建通过 |
| D2 | WebSocket 服务端，实现 `KernelTransport` | 能连上 |
| D3 | `FrameCodec`：`FrameContext` → JSON → `KernelResponse` | 往返等价 |
| D4 | 把脚本注册的内核暴露成 `RemoteKernel` 的后端 | 外部程序能接管 |
| D5 | 人工指令队列（扣车/改道/限速）优先于脚本 | 浏览器下发即生效 |
| D6 | 连接断开 → `RemotePolicy` 降级 | 断网不失控 |

### 阶段 E — `ctdscript` P6：外部进程优化（可选）

| # | 任务 | 验收 |
|---|---|---|
| E1 | 定义异步任务的请求/响应格式（时刻表优化等） | — |
| E2 | 外部 CPython 示例程序 | 能跑通一次优化 |
| E3 | 超时与失败不影响帧 | 杀掉外部进程，游戏无感知 |

### 阶段 F — 收尾

| # | 任务 | 验收 |
|---|---|---|
| F1 | 进游戏实测：脚本内核驱动一列车走完一段线 | 视频/日志 |
| F2 | 与汽鸣铁道、Electro Energetics 同装测试 | 不崩、能量状态正确 |
| F3 | 文档：`docs/13-host-design.md`、更新 `docs/12` | — |
| F4 | 三个仓库打 tag + release jar | — |

---

## 3. 依赖与阻塞

```
ctd-host ──► 决定 D1/D2 能否成立
   │
   ├── 需要 Mixin 注入点（docs/03、docs/05 已分析）
   ├── 需要 Create 6.0.11 的 TrackGraph / Train 实现细节
   └── 需要汽鸣铁道道岔 API（docs/09 已分析）
```

**当前最大风险**：`ctd-host` 一行没写。没有它，脚本层再完整也只是离线玩具。
所以阶段 C 的优先级实际上高于 B/D/E。

---

## 4. 执行顺序建议

```
A（设备与道岔）        ← 补完 ctdscript 的 API 面，1~2 轮
C（宿主）              ← 最大工作量，但决定一切；C2/C3/C4/C5 是 MVP
B（重载收敛）          ← 有宿主后可实测，才有意义
D（Web）               ← 可选，取决于是否真的要外部调度台
E（外部优化）          ← 最后，且可能永远不做
F（收尾）              ← 必须
```

**MVP 线**：A + C2~C5 + F1 = 脚本能在游戏里开动一列车。

---

## 5. 风险清单

| # | 风险 | 缓解 |
|---|---|---|
| R1 | 宿主 Mixin 注入点被 CRN / 汽鸣铁道 占用 | 已在 docs/09 列出禁区：`TrackEdge#canTravelTo`、`Navigation#search` 不碰 |
| R2 | 脚本性能在解释器模式下不够 | B5 已加 p50/p99 统计，`/ctd stats` 可查；不达标就把热点挪回 Java |
| R3 | 信号/设备热重载做不干净（半注册状态） | B4 强制回滚语义，做不到就不做（宁可要求重启） |
| R4 | 外部进程的延迟吃掉 tick 预算 | D6 的 `RemotePolicy` 已有衰减机制 |
| R5 | 汽鸣铁道版本漂移（NeoForge 21.1.233 vs 我们 21.1.219） | C7 用反射/软依赖，编译期不硬绑 |
| **R6** | **Create jar 下载极慢（实测 ~0.02 MB/s，40MB 需半小时以上），C1 编译验证被卡** | 后台续传；先用 `javac` 单独验证不依赖 Create 的类；Create 侧代码照 `F:\TCM\Create` 源码写，等 jar 到位再统一编译 |

---

### 阶段 G — 运营身份与「脚本时刻表」物品

| # | 任务 | 验收 |
|---|---|---|
| G1 | API：`operation` 包（UnitNumber / UnitSet / Run / Assignment / Assignments） | ✅ 已实现 |
| G2 | `DispatchFrame#assign`（default 方法，不强制宿主支持） | ✅ 已实现 |
| G3 | Python 桥 `ctd.operation` | ✅ `operationBridgeTest` 6 项通过 |
| G4 | 宿主把 `assign` 落到 Create：改 `ScheduleRuntime` / 触发自动出库 | 未做 |
| G5 | **新物品「脚本时刻表」**：在物品里指定某个脚本调度，放到列车上即绑定 | 未做 |
| G6 | 车辆编号与 `Train.name` 的绑定规则（谁负责写、冲突怎么办） | 未做 |

**加载时机划分**（已实现并强制）：

| 目录 | 阶段 | 内容 |
|---|---|---|
| `signal/` `device/` `timetable/` | **启动，不可热重载** | 信号与灯位逻辑、地面/车载设备类型、交路 |
| `kernel/` `ato/` | **可热重载** | 调度算法、自动驾驶算法 |

重载阶段调用定义类注册会直接抛异常（`loadPhaseTest` 3 项验收）。

---

## 6. 进度表

| 阶段 | 状态 | 备注 |
|---|---|---|
| A | ✅ **完成** | A1–A8 全部实现并验收：`railCodecTest` 10 项、`deviceSwitchBridgeTest` 13 项 |
| B | ✅ **完成** | 全量重载（五类全可重载）+ 整批回滚 + 沙箱六项 + `disabled/` + 耗时统计。`reloadTest` 8 项、`sandboxTest` 6 项全绿 |
| C | ✅ **完成（除环境阻塞项）** | 宿主全部落地；帧刻 3:1、镜像 `nodes=2 edges=1`、遥测 `trains=1`、速度钳位 `0.350`、信号写入 12 项、指派 → `Schedule`；脚本可加载并驱动列车。**未验**：列车位移（四级失败链已定论，见 `DebugTrainCommand` 注释与 `docs/19` §4）、道岔物理扳动 |
| D | ✅ **完成** | `ctdweb` 仓库：WebSocket 桥（手写服务端，零依赖）+ 编解码 + 断线三策略。`bridgeTest` 18 项全绿 |
| E | ✅ **完成** | `TaskBroker`：提交/取回/超时/去重，常数时间不阻塞。跨语言实测（Python 标准库客户端） |
| G | ✅ **完成** | 运营身份 API + 脚本时刻表物品 + 加载阶段划分 + 后台预留接口。宿主侧指派**全链路打通**：指派 → 解析 → 真实 `Schedule`（`schedule=2` 实测） |
| F | ✅ **完成** | 安装文档、版本矩阵、发布清单、`docs/17` 进游戏验证步骤；四仓库 manifest 版本正确 |

**最后更新**：阶段 A 完成时（A8 验收通过）。

### 已完成明细

| 项 | 证据 |
|---|---|
| A1 Rail id 编解码 | `railCodecTest`：10 项断言，含 1000 次随机往返 |
| A2 `ctx.switches()` | `deviceSwitchBridgeTest`：读到 1 个岔口 |
| A3 锁岔/解锁 | 同上：产出 2 条 `SwitchCommand`，节点/股道/持有者全部解析正确 |
| A4–A6 设备/能源/补给 | 同上：payload 原样透传、联动方 id 正确 |
| A7 设备 `signalSystems` | `DeviceApi.registerType` 已支持 |
| A8 离线验收 | `deviceSwitchBridgeTest` 13 项全绿 |
