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
| `ctdscript` P5 | 🟡 | `ctdweb` 有帧编解码 / 远端内核 / 任务队列骨架，未联调 |
| `ctdscript` P6 | ❌ | 外部进程未开始 |
| `ctd-host` | ✅ 可用 | 注入 / 拓扑镜像 / 遥测 / 指令执行 / 信号写入 / 逻辑锁 / 进路 / ATO 均已接线；物理扳岔与能源样本未接 |

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
| H | 🟡 进行中 | **脚本定义内容**：`content.block(...)` / `content.hud(...)`；方块启动期注册（已实测可放置）；纹理由脚本提供 png，引擎合成模型并入 `resourcepacks/`；HUD 由引擎渲染。待验：客户端外观 |
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


---

## 7. 新阶段（H–L）：从「能注册」到「能当仪表台用」

> 起因：脚本注册方块与 HUD 打通之后，暴露出一串「有了壳没有魂」的问题——
> 方块没有方块实体、HUD 不区分乘务场景、没有翻译、没有创造栏。
> 更关键的是：**HUD 的制式不该由引擎决定**。

### 参考实现与取舍

| 参考 | 它怎么做 | 我们取什么 | 不取什么 |
|---|---|---|---|
| **CreateRealism**（stacode123） | `TrainHUD.renderOverlay` 注入；一张固定的 `etcszoom%d.png` 大图 + `etcshand.png` 指针 + 单位图 + 数字 | 「图元叠加 + 指针旋转 + 单位图」这套渲染手法 | **瑞典 ETCS 的固定版式**。我们的版式必须由脚本给 |
| **Tramways**（PurpleCreate） | 限速牌/区间限速/鸣笛标志；广播用 `AnnouncementEvent` 枚举（9 类）+ `AnnouncementVariant` 语音变体 + 配置界面 | 事件分类的思路、语音变体的结构 | 有轨电车制式本身、以及它那套「简陋」的广播数据 |

**结论一句话**：引擎提供**画布与图元**，脚本提供**版式与文案**，引擎永不知道「电压表长什么样」。

---

### 阶段 H — 内容层补齐（进行中）

| # | 任务 | 验收 |
|---|---|---|
| H1 | 脚本注册方块（`content.block`） | ✅ `/setblock` 成功，`[registered]` |
| H2 | **每个方块都要有方块实体**：通用 `ScriptBlockEntity` + `EntityBlock` | 打破方块后 BE 正常保存/读取；右键能响应 |
| H3 | **创造模式物品栏**：`ctdscript:content` 页，收纳全部脚本方块；`ctdhost` 的脚本时刻表也要有页 | 创造模式里能翻到，不只在搜索里 |
| H4 | **翻译**：引擎按 `block.<ns>.<path>` 自动合成 `en_us` / `zh_cn`，并**合并**脚本自带的 lang（脚本优先） | 客户端显示「Bridge Panel」而不是 `block.mysys.bridge_panel` |
| H5 | **拓展 SPI**：`ctd.content.registerExtension(ns, object)` → 脚本侧 `ctd.ext.<ns>.*` | 另一个模组注册一个方法，脚本能调到 |

### 阶段 I — HUD 引擎（可定制制式）

| # | 任务 | 验收 |
|---|---|---|
| I1 | **图元**：`image`（支持 u/v/w/h 子区域与整图）、`text`（字体/缩放/颜色/阴影）、`rect`、`rotatedImage`（指针绕锚点旋转）、`progressBar`、`clip` 嵌套 | 脚本能画出指针式速度表 |
| I2 | **数据绑定**：引擎把 `speed / speedLimit / targetSpeed / nextStop / platform / delay / doorSide` 作为只读字段喂给 draw 回调 | 脚本不自己算速度 |
| I3 | **显示闸门**：只在「玩家坐在**装有车载设备**的列车上 + 右键过该设备启用指示器」时渲染 | 站在地上不进车 → 不显示；车没装设备 → 不显示 |
| I4 | **纹理走脚本资源包**：`textures/gui/**` 已随包生效；脚本可用 `mysys:gui/speedo_dial` 直接引用 | 自定义涂装无需写 Java |
| I5 | **制式零内置**：引擎不带任何「中国铁路 / ETCS / 有轨电车」预设 | 换制式 = 换脚本，不动代码 |

> I3 依赖阶段 K 的车载设备方块。顺序：**K2 → I3**。

### 阶段 J — PIS、广播与语音源

| # | 任务 | 验收 |
|---|---|---|
| J1 | **PIS 数据面**：站台号、到站/离站、晚点、变更站台、终止/通过 —— 结构化下发（复用 `Pis` 与 `OperationFeed`） | 脚本能拿到「下一站 + 站台号 + 晚点秒数」 |
| J2 | **广播事件模型**：引擎只定义**事件**（接近/停稳/离站/站台变更/晚点/取消/通过/折返），文案由脚本给 | 事件触发时脚本被调用，拿到完整上下文 |
| J3 | **多语音源**：`local`（音频文件）/ `tts`（外部进程合成）/ `model`（模型推理）。引擎按优先级选源，失败**降级到下一源**并记录 | 三源都配置时，坏掉一个仍能出声 |
| J4 | **脚本 API**：`broadcast.say(event, text, voice="auto")`；支持变量占位（站名、车次、时间） | 一个脚本同时管车内与站台广播 |

> 语音源是**外部能力**：`tts` / `model` 走 `ctdweb` 的桥或子进程，引擎不内置模型。
> 本地音频直接读 `config/ctd/assets/<ns>/sounds/**` 并注册进声音事件表。

### 阶段 K — 车载与站台设备

| # | 任务 | 验收 |
|---|---|---|
| K1 | 通用 `ScriptBlockEntity`（H1/H2 的底座），带一个可脚本读写的 `state` 字典 | 脚本能存一个值，重启后还在 |
| K2 | **车载设备方块**（HUD 指示器）：右键切换启用；启用状态随构造保存 | 右键后 HUD 出现；再右键消失 |
| K3 | **动态文字方块**（参考 CRN 的显示板）：车上/站台都能显示脚本给的文字，随列车移动 | 车上牌子跟着车走，内容每秒刷新 |
| K4 | 站台设备：站台号牌 / 广播喇叭（触发 J2 的站台侧事件） | 列车进站时喇叭侧事件触发 |

### 阶段 L — 线路标志（参考 Tramways 的概念）

| # | 任务 | 验收 |
|---|---|---|
| L1 | 标志数据进拓扑镜像：限速、区间限速、鸣笛、禁止鸣笛、停车点 | `ctx.signs()` 能读到前方 500 格内的标志 |
| L2 | 区间限速（按区段生效，非全图） | 两个限速标志之间生效，且脚本能覆盖 |
| L3 | 脚本动作：鸣笛、开关门、请求停站 | 脚本调 `out.horn()` 有声音，且被日志记录 |

> 标志**不是**有轨电车专用：L1 只提供「贴在线路上的数据点」，怎么解释由信号制度/脚本决定。

---

## 8. 顺序与依赖

```
H2 (方块实体) ──► K1 (通用 BE) ──► K2 (车载设备) ──► I3 (HUD 闸门)
H3/H4 (创造栏/翻译) 独立，可并行
I1/I2/I5 (图元/数据/零内置) 独立，先做
J1 (PIS 数据) ──► J2 (广播事件) ──► J3 (语音源) ──► J4 (脚本 API)
K3/K4 依赖 K1
L1 (标志进镜像) ──► L2 (区间限速) ──► L3 (脚本动作)
```

**最先做**：`I1 + I2 + H2`——没有画布和方块实体，后面全是空中楼阁。

---

## 9. 新阶段进度表

| 阶段 | 状态 | 说明 |
|---|---|---|
| H | ✅ 完成 | H1–H5 全部完成：方块可放置、方块实体、创造栏、自动翻译、**拓展 SPI**（`Extensions.register` → `ctd.ext.get(ns)`，实测脚本能调到） |
| I | ✅ 完成 | 图元（子区域贴图 / 旋转指针 / 进度条 / long 颜色）、只读数据绑定、**四层闸门**（坐车 → 车有设备 → 我开了 → 渲染）、按人可见（渲染器）；实测脚本能画出指针式速度表 |
| J | 🟡 进行中 | 事件模型（8 类）、语音源链 + 降级、脚本 API（`ctd.broadcast.on/emit`）、`/ctd broadcast`、UTF-8 独立日志均已完成并实测；**本地音频源已完成**（扫描 ogg → 注册 SoundEvent → 合成 sounds.json → 播放，降级实测通过）；**PIS 自动触发已完成**（宿主判定相位 → `OperationFeed.trainAtStation` → 脚本 → 音源，合成事件实测）；**待做**：TTS/模型外部源 |
| K | 🟡 进行中 | K1 方块实体、K2 车载设备（车上右键、按玩家偏好、按人渲染）、**K3 显示板**（方块+实体+渲染器+脚本 API `ctd.display`，脚本实测写入 `["列车 1 列","t=124566"]`）已完成；**待做**：K3 车上渲染的视觉确认、K4 站台设备 |
| L | 🟡 进行中 | L1 限速/鸣笛进 `Restrictions` 并**真的压到车上**（`/ctd host restrict`，实测 `limited to 30 km/h`）；**L3 鸣笛已接**（进鸣笛区段自动鸣笛，每条边一次，判定实测；实际发声需组装好的列车）；**待做**：L2 区间限速按距离段生效、脚本动作（开关门/请求停站）与脚本读取 `ctx.restrictions()` |

---

## 10. 测试线：CBTC 与调度（本轮新增）

> 目标：给「等你有空测试」准备一套**能立刻看出对错**的东西。
> 详见 `docs/21-cbtc-test-line.md`。

| 产物 | 位置 | 状态 |
|---|---|---|
| CBTC 移动闭塞内核 | `examples/cbtc-test-line/cbtc.py` | ✅ 加载、自检 4/4、每帧算 MA 并下发 |
| 简单调度内核 | `examples/cbtc-test-line/dispatch.py` | ✅ 授权不足即 `hold`，足够则放行 |
| 设计文档 | `docs/21-cbtc-test-line.md` | ✅ |

实测输出：

```
[cbtc] selftest ok (4 checks)
[cbtc] 未命名列车 ma=10000 cap=1.40
[dispatch] released=1 held=0
```

**未验证**：真实列车间隔控制（试验线只有一列静止车）、岔区进路、站台精确停车。

### 为什么分成两个内核

调度决定「该不该走」（运营），移动闭塞决定「能走多远」（安全）。
合成一个，以后任何运营策略调整都会碰到安全逻辑——这是这类系统最典型的失控路径。
---

## 11. 当前阻塞

**等待人工测试**。完整清单见 `docs/22-test-checklist.md`：A 显示板、B HUD、C 广播、D 限速鸣笛、E CBTC。

已知未完成项也在那份文档的 §F，测试时不必重复报。

---

## 12. 进度表（每完成一项就更新这里）

| 阶段 | 任务 | 状态 | 证据 |
|---|---|---|---|
| A | A1 `Rail.NodeId`/`EdgeId` 字符串编解码 | ✅ | `RailCodecTest` 往返 1000 次 |
| A | A2 `ctx.switches()` 暴露岔口锁闭 | ✅ 本轮 | `HostRuntime` 从 `SwitchApplier.locks()` + `holderOf()` 构造 `FrameContext.SwitchState` |
| A | A3 `out.lock_switch` / `unlock_switch` | ✅ | `FrameOutput` → `Command.SwitchCommand` |
| A | A4–A6 设备 / 能源 / 运营指令桥接 | ✅ | 已转交 `OperationFeed` 并计数 |
| A | A7 设备注册带 `signalSystems` | ✅ | `DeviceKind.signalSystem()` |
| C | C2 `GlobalRailwayManager#tick` 注入 | ✅ | 启动自检 `MixinChecks`，缺注入直接拒绝启动 |
| C | C3 拓扑镜像（含信号机） | ✅ | `/ctd host status` 显示 `nodes=28 edges=29 signals=2` |
| C | C4 遥测：速度 / 位置 / **里程** / **车载制度** | ✅ 本轮 | `Odometers` 累计里程；`signalSystemsOf()` 从构造物读设备 |
| C | C4b 遥测：进路状态 `route` | ✅ 本轮 | `out.route(车, 终点, 边序列, 优先级, 原因)` → `Command.RouteRequest` → `RouteApplier` 分配 id、算剩余长度与需锁岔口 → 回填 `TrainState.route` |
| C | C4c 遥测：能源样本 `energy` | ✅ 本轮 | 走 `Interop.sampleEnergy()` 聚合已注册的提供方；一个都没有时返回 `UNKNOWN` 并在 `/ctd host status` 注明「没有联动方，列车照常运行」 |
| C | C5 指令执行（速度钳位） | ✅ 本轮实测 | `speed=0.350 blocked=false`（脚本开车），到轨道尽头转 `blocked=true` |
| C | C6 信号写入 + 客户端显示 | ✅ 写入 / 🟡 灯色 | 内核把两架信号机写成 `ctd:clear`（日志逐面确认）；客户端灯色仍待人工看一眼 |
| C | C7 道岔翻译（逻辑锁） | ✅ | `SwitchApplier` 锁 + 持有者 |
| C | C7b 道岔物理扳动 | ✅ 本轮 | `PhysicalSwitches` 通过 `railways:switch` 边点调 `trySetSwitchState`；未装汽鸣铁道时自动退化为逻辑锁并在状态里注明 |
| C | C8 存档持久化 | 🟡 | `KernelEnv.state()` 已接；`Restrictions`/`Emergency` 未验 |
| C | C9 安全兜底 | ✅ | 连续失败禁用 + 注入自检 |
| C | C10 ATO 接入主循环 | ✅ 本轮 | 宿主 `AtoPass` 在内核授权之后跑 `AtoEngine.decide()`，输出与内核限速取 `min`（只能收紧） |
| B | B1–B4 全量重载 + 失败整批回滚 | ✅ 本轮 | 五个注册表全部纳入解冻/冻结；`reloadTest` 新增「连续重载两次」用例，覆盖 ATO 漏解冻的 bug |
| D | Web 桥接 | ✅ 本轮 | 真游戏 + 真 Python 进程：收到遥测 21 帧，`control hold -> accepted` |
| F | 文档与实测 | ✅ 本轮 | `docs/17` 新增 §7（复现 + 八个诊断字段表）；`docs/18` 新增 0.3.0；灯色/道岔人工项仍待现场看 |

**本轮（审查 + 修补）**：修掉了三个「有代码、无数据」的断点——内核拿到的信号快照恒空、
里程恒为 0、每列车的信号系统恒空。这三处都属于「现象看起来像渲染/算法问题，
实际是数据根本没到」。

### F 阶段：进游戏实测（进行中）

| 项 | 状态 | 证据 / 结论 |
|---|---|---|
| 拓扑与信号 | ✅ | `nodes=28 edges=29 signals=2 aspects=2` |
| 外部程序读遥测 | ✅ | Python 客户端收到 `1 train(s)` |
| 外部程序下控制指令 | ✅ | `[ctdweb] control hold <uuid> -> accepted` |
| 道岔/能源降级路径 | ✅ | `logical=0 physical=no (no Railways)`、`energy providers=0` |
| **脚本驱动列车** | ❌ **未达成** | 见下 |

**脚本驱动列车卡在哪（实测数据）**

```
train : 未命名列车 speed=0.000 target=0.000 graph=ok paused=false manual=false schedule=0
```

- 本轮修掉两个真 bug：① 「只能收紧」写成相对上一帧自身输出取 min → **扣车永久粘住**；
  ② 内核在 `GlobalRailwayManager#tick` HEAD 写 `targetSpeed`，随后被
  `Navigation.tick()` 覆盖 → 改成 `Navigation#tick` TAIL 施加（新增 `NavigationMixin` + `SpeedControl`）。
- 仍然不动的原因：**Create 只 tick「运行中」的列车**。没有时刻表（`schedule=0`）的列车
  既不进入 `movingTrains` 也不进 `waitingTrains`，`Train.tick` 不被调用，
  `Navigation.tick` 自然也不跑——我们的 TAIL 注入无从生效。
- 下一步：让内核能动 Create 的时刻表/手动驾驶状态（`Assignment` 已有部分集成），
  或者由宿主在命令到达时把列车置为手动可控状态。这不是脚本 API 的问题，是「谁来让车进入运行态」的归属问题。

**F 阶段补充实测（第二轮）**

| 观测 | 数据 | 结论 |
|---|---|---|
| 列车在 tick 列表里 | `ticked=moving` | `Train.tick` 会被调用 |
| `Navigation.tick` | 注入一次都没触发 | 该注入点不可靠，改挂 `Train.tick` |
| `Train.tick` 注入 | `[train] tick calls=1600` | **可行**：这是列车每帧唯一必经之路 |
| 目标速度落地 | `target=0.350`（内核要求 0.35） | 施加链路通了 |
| 车速 | `speed=0.007` 恒定 | 被 Create 的 `tickPassiveSlowdown()` 每帧减掉一个加速度步长，正好抵消 |

**下一步（明确、可验证）**：`Train.tickPassiveSlowdown()` 在「本帧有内核速度请求」时必须跳过。
已加 `PassiveSlowdownMixin`，还需要确认它真的生效（`speed` 应当从 0.007 一路爬到 0.35）。

**F 阶段第三轮：`speed=0.007` 的完整因果链（已闭环）**

```
kernel out.proceed(train, 2000, 0.5)
  → SpeedApplier 收敛为 0.35（与 hold_all 的 0.35 取 min）并登记
  → Train#tick TAIL 施加（[train] tick calls=1600 证明这一层在跑）
  → target=0.350 ✓（遥测可见）
  → PassiveSlowdownMixin 拦截被动减速（[passive] cancelled=1600 证明拦截生效）
  → 但 carriage.travel 判定 blocked（train.status.track = end_of_track）
  → Create 每帧 speed = 0；我们每帧 +1 步 → 恒定 0.0075
```

`blocked=true` 是本轮新加的状态字段，它把最后一个未知项确定了：
**不是注入失效、不是减速抵消、不是红灯**——是列车停在轨道尽头，Create 拒绝让它移动。

- 信号侧同时验证：`out.aspect(signal, side, "ctd:clear")` 真的把两架信号机写成了 `ctd:clear`
  （`[green] signals=3bbf72fe=ctd:clear,...,a9dcfa61=ctd:clear`）。
- 结论：`C` 阶段的「脚本真实驱动列车」在**管线层面已成立**（指令→收敛→施加→目标速度落地），
  剩下的 0.007→0.35 需要一个**不在尽头**的轨道场景才能观测到。

**F 阶段第四轮：为什么这个世界里永远开不动（已定因）**

`/ctd host debugtrack` 造的是**一段 4 格的孤立轨道**：

```java
// DebugTrackCommand.java:53-56
new TrackNodeLocation.DiscoveredLocation(level, base.x, base.y, base.z) ...
new TrackNodeLocation.DiscoveredLocation(level, base.x + 4, base.y, base.z) ...
graph.connectNodes(level, a, b, null);
```

4 格放不下任何真实列车，于是 `carriage.travel` 在两端都判定 `blocked`，
Create 每帧把 `speed` 清零——**正反向都一样**（实测 `dir=reverse` 仍然 `blocked=true`）。

这不是注入或脚本的问题：管线每一段都有日志证据。要看到 0.35 的车速，需要：

1. 世界里有一段**足够长**的直轨（≥ 列车长度 + 几十格余量）；
2. `/ctd host debugtrain create` 把车放上去；
3. 内核 `out.proceed(id, 2000, 0.5)`，然后看 `speed` 爬到 0.5。

**本轮同时补掉的功能缺口**：`Direction` 以前只有 `KEEP` 被用上，
现在 `proceed(id, ma, cap, "reverse")` 会发 `REVERSE`，
宿主把它翻译成**负的目标速度**（Create 用 speed 的符号决定推进方向）。
`SpeedControl` 保留幅值钳位再补符号，因此倒车同样受最高速与授权约束。

**F 阶段第五轮：脚本在游戏里把车开起来了（C 阶段验收达成）**

复现步骤（三条命令）：

```
/ctd host debugtrack        # 给最大那张图接长 96 格
/ctd reload                 # 让 hold_all / drive_probe 内核保持启用
/ctd host status
```

实测序列（同一个世界，连续三条状态）：

```
train : 未命名列车 speed=0.350 target=0.350 ... blocked=false   ← 脚本正在开车
train : 未命名列车 speed=0.007 target=0.350 ... blocked=true    ← 开到接长段的尽头
train : 未命名列车 speed=0.007 target=0.350 ... blocked=true
```

- `speed` 爬到 **0.350**（内核 `out.proceed(id, 100, 0.35)` 给的上限），`blocked=false`：
  **脚本 → 宿主 → 列车** 这条链路在真实服务端上完整成立。
- 几秒后 `blocked=true`：车开到了新铺的 96 格尽头，Create 把它停住——这是正确行为，
  也反过来证明车**确实位移了**。
- 再跑一次 `/ctd host debugtrack` 就又能接长 96 格，可以反复演示。

**本轮修掉的两个「看起来像调度坏了」的问题**

| 问题 | 症状 | 修法 |
|---|---|---|
| `debugtrack` 只造 4 格 | 任何车都 `blocked`，速度永远 0.007 | 改成接长 24 段 × 4 格 = 96 格 |
| 新图对已有列车没用 | 车还挂在旧图上，接长别处毫无帮助 | 改为**在最大那张图的端点接长** |

**F 阶段第六轮：注入自检写错了两次，第二次才对**

想把「脚本能不能开车」的两处注入纳入启动自检，结果连踩两个坑：

| 尝试 | 结果 | 原因 |
|---|---|---|
| 在 `FMLCommonSetupEvent` 里查 `Train` 的方法 | **误报「注入缺失」并拒绝启动** | 那时 `Train` 还没被加载，反射看到的是原生类 |
| 改到 `ServerStartedEvent` 仍然报缺失 | 还是误报 | `@Inject` 的处理方法会被**内联进目标方法后丢弃**，反射永远看不到它 |

正确做法：在每个 mixin 里加一个 `@Unique` 标记方法（`@Unique` 会被真正加进目标类），
自检查这个标记。现在启动自检能真正区分「注入在」与「注入不在」：

```
required mixins are not applied -> Train#tick TAIL (...); Train#tickPassiveSlowdown (...)
```

这条错误信息在排查过程中真的触发过一次，证明了它是有用的，而不是装饰。

**同时修掉的调试命令缺陷**：`debugtrack` 只接长一端。如果接在列车背后，
车头照样顶着尽头（实测 `nodes` 从 52 涨到 76，`blocked` 仍为 true）。
现在**两端都接长**，不用猜车朝哪边：

```
debug track: graph=... nodes=100 +48 node(s) (96 blocks)
train : speed=0.350 target=0.350 blocked=false    ← 连续两次采样
train : speed=0.007 target=0.350 blocked=true     ← 约 14 秒后到另一端
```

0.35 格/tick × 20 tick/s ≈ 7 格/s，96 格刚好跑 14 秒——数字对得上，
说明这不是「原地抖」，是真位移。

**F 阶段第七轮：0.3.0 真的对齐了**

发布说明写了三轮 0.3.0，产物却一直叫 `0.1.0`。这一轮才真正对齐，并且发现：**只改版本号是不够的**。

```
gradle.properties:  ctdapi_jar = create-train-dispatching-api-0.1.0.jar   ← 按文件名钉死
```

只把 `mod_version` 改成 0.3.0，其余三个仓库仍然按旧文件名找 API，
于是拿旧 API 编译、游戏里 `ctdapi` 依旧报 0.1.0——第一次尝试就是这个结果。

四个仓库现在都：`mod_version=0.3.0` + `ctdapi_jar=create-train-dispatching-api-0.3.0.jar`。

| 仓库 | 产物 | 大小 |
|---|---|---|
| ctdapi | `create-train-dispatching-api-0.3.0.jar` | 204 KB |
| ctdhost | `create-train-dispatching-host-0.3.0.jar` | 135 KB |
| ctdscript | `create-train-dispatching-script-runtime-0.3.0.jar` | 45.9 MB |
| ctdweb | `create-train-dispatching-web-0.3.0.jar` | 55 KB |

服务端实测：

```
Create Train Dispatching API 0.3.0 (ctdapi)
Create Train Dispatching Script Runtime 0.3.0 (ctdscript)
Create Train Dispatching Web Bridge 0.3.0 (ctdweb)
[ctdscript] 9/9 scripts loaded
Done (0.850s)!
```

`ctdhost` 在 `runServer` 下显示 `0.0NONE`——`${file.jarVersion}` 只在 jar 内可解析，打包产物是 0.3.0。

**顺带修掉一个自伤的构建问题**：服务端日志原来写在 `build/` 里，导致 `gradlew clean` 报
`Unable to delete directory 'build'`。日志已挪到 `run/`。