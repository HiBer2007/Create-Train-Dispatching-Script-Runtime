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
| B | ✅ **完成** | 只重跑可重载目录（kernel/ato/broadcast/hud）；**失败不回滚**且 ERROR 级可见，实测 `reloaded 7/7` / `FAILED 6/7` / 修好 `7/7` |
| C | ✅ **完成（除环境阻塞项）** | 宿主全部落地；帧刻 3:1、镜像 `nodes=2 edges=1`、遥测 `trains=1`、速度钳位 `0.350`、信号写入 12 项、指派 → `Schedule`；脚本可加载并驱动列车。**未验**：列车位移（四级失败链已定论，见 `DebugTrainCommand` 注释与 `docs/19` §4）、道岔物理扳动 |
| D | ✅ **完成** | `ctdweb` 仓库：WebSocket 桥（手写服务端，零依赖）+ 编解码 + 断线三策略。`bridgeTest` 18 项全绿 |
| E | ✅ **完成** | `TaskBroker`：提交/取回/超时/去重，常数时间不阻塞。跨语言实测（Python 标准库客户端） |
| G | ✅ **完成** | 运营身份 API + 脚本时刻表物品 + 加载阶段划分 + 后台预留接口。宿主侧指派**全链路打通**：指派 → 解析 → 真实 `Schedule`（`schedule=2` 实测） |
| H | 🟡 进行中 | **脚本定义内容**：`content.block(...)` / `content.hud(...)`；方块启动期注册（已实测可放置）；纹理由脚本提供 png，引擎合成模型并入 `resourcepacks/`；HUD 由引擎渲染。待验：客户端外观 |
| F | ✅ **完成（除三项人工目视）** | 安装文档、版本矩阵、发布清单、`docs/17` 验证步骤、`docs/16` §9 失败语义；四仓库 manifest 一致 |

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
| A | A4–A6 设备 / 能源 / 运营指令桥接 | ✅ | 转交 `OperationFeed` + `DeviceCommands` 落点（`deviceCmds=1/7` 实测） |
| A | A7 设备注册带 `signalSystems` | ✅ | `DeviceKind.signalSystem()` |
| C | C2 `GlobalRailwayManager#tick` 注入 | ✅ | 启动自检 `MixinChecks`，缺注入直接拒绝启动 |
| C | C3 拓扑镜像（含信号机） | ✅ | `/ctd host status` 显示 `nodes=28 edges=29 signals=2` |
| C | C4 遥测：速度 / 位置 / **里程** / **车载制度** | ✅ 本轮补测 | `routeOdometerTest` 6 项：有符号累加、多车独立、prune 清理 |
| C | C4b 遥测：进路状态 `route` | ✅ 本轮补测 | `routeOdometerTest` 11 项：剩余长度、岔口合并、撤销、id 稳定、未知边忽略 |度与需锁岔口 → 回填 `TrainState.route` |
| C | C4c 遥测：能源样本 `energy` | ✅ 本轮 | 走 `Interop.sampleEnergy()` 聚合已注册的提供方；一个都没有时返回 `UNKNOWN` 并在 `/ctd host status` 注明「没有联动方，列车照常运行」 |
| C | C5 指令执行（速度钳位） | ✅ 本轮实测 | `speed=0.350 blocked=false`（脚本开车），到轨道尽头转 `blocked=true` |
| C | C6 信号写入 + 客户端显示 | ✅ 写入 / 🟡 灯色 | 内核把两架信号机写成 `ctd:clear`（日志逐面确认）；客户端灯色仍待人工看一眼 |
| C | C7 道岔翻译（逻辑锁） | ✅ 本轮补测 | `switchApplierTest` **16 项**：上锁/解锁/冲突判定/多岔口互不干扰 |
| C | C7b 道岔物理扳动 | ✅ 本轮 | `PhysicalSwitches` 通过 `railways:switch` 边点调 `trySetSwitchState`；未装汽鸣铁道时自动退化为逻辑锁并在状态里注明 |
| C | C8 存档持久化 | ✅ 本轮实测 | 写入 → `/stop` → 重启 → 读回 `world/data/ctdhost.dat`；`Env.state()` 已接真实存档 |
| C | C9 安全兜底 | ✅ | 连续失败禁用 + 注入自检 |
| C | C10 ATO 接入主循环 | ✅ 本轮补测 | `atoPassTest` 10 项：只收紧/手动不接管/无制度缺席/坏引擎 10 次禁用/null 不干预 |
| B | B1–B4 全量重载（**不回滚**） | ✅ | 五个注册表纳入解冻/冻结；`reloadTest` 14 项：只跑可重载目录、失败可见且不回滚、连续两次不失效 |
| D | Web 桥接 | ✅ 闭环 | 外部 Python 进程**真的把车开到 0.300**：`speed=0.300 target=0.300 blocked=false` |
| F | 文档与实测 | ✅ 除人工项 | `docs/17` §7 诊断表、`docs/16` §9 失败语义、`docs/18` 0.3.0、`docs/19` 交接；灯色/HUD/道岔物理仍待现场看 |

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

**C8 存档持久化：写得好好的代码，从来没被调用过**

`HostSavedData` 一直有 `save` / `load` / `kernelState` / `putKernelState`，
但全仓库**没有一处实例化它**。真正的 `KernelEnv` 实现是这样的：

```java
private final CompoundTag tag = new CompoundTag();       // 每帧新建
@Override public CompoundTag state() { return tag; }
@Override public void markStateDirty() { }               // 空方法
```

于是脚本写进台账的任何东西，重启就没了，而且**不报错**——这类缺口只有「写一次、重启、读一次」才能发现。

现在：

```java
HostSavedData.get(server)                    // 主世界 DataStorage，computeIfAbsent
Env.state()      → data.kernelState(kernelId)
Env.markStateDirty() → data.putKernelState(...) + setDirty()
```

实测（`/ctd state` 探针）：

```
/ctd state put hello world-0.3.0     → marked dirty
/stop                                 → world/data/ctdhost.dat 落盘（133 B）
重启服务端
/ctd state show                       → state[ctdhost:probe] hello=world-0.3.0
```

内核 id 是真实参数（`Env(id)`），所以每个内核有自己的一份台账，互不覆盖。

**L1 拓扑镜像：不再每帧重建，也不再漏掉「只加了一条边」**

旧实现的「变没变」判断是坏的：

```java
edges += g.getNodes().size();     // 「连接数不好直接取，用节点集合算个上界估计即可」
if (nodes == lastNodeCount && edges == lastEdgeCount) return view;
```

`edges` 就是节点数，所以整段等价于**只比较节点数**。后果：
把两段轨接上（只加一条边）、或加装一架信号机——都不触发重建，
内核看到的是过期拓扑，而且没有任何提示。

现在的判断：

| 项 | 说明 |
|---|---|
| 图 id 哈希 | 换图能发现 |
| 节点数 | 加减节点 |
| **连接数** | 只加一条边也能发现 |
| **信号机数** | 加装信号机也能发现 |
| 检查频率 | 每 10 刻一次（0.5 秒），不是每帧 |

实测（100 节点）：

```
t0  topology : nodes=100 edges=101 rebuilds=1  lastMs=12
t0+20s（+1200 刻）
    topology : nodes=100 edges=101 rebuilds=1  lastMs=12      ← 稳态零重建
跑 /ctd host debugtrack（nodes 100 → 148）
t+3s
    topology : nodes=148 edges=149 rebuilds=2  lastMs=2       ← 变更被检测到
```

结论：稳态开销从「每帧 O(节点)」降到「每 10 帧 O(节点)」，且正确性比原来更严——
原来漏掉的边/信号机变更现在都能发现。

**`/ctd selftest`：把「系统通没通」压缩成一次执行**

以前判断系统状态要翻 `/ctd host status` + 对照 `docs/17` 的清单逐条看。
问题是**每一项失败都长得像成功**：速度 0、拓扑空、内核没注册，打印出来都不报错。

九项自动检查（实测全绿）：

```
CTD selftest — 全部通过
  PASS  Train#tick 注入          ← 内核速度能不能写进去
  PASS  被动减速拦截              ← 每帧加的一步会不会被 Create 减掉
  PASS  SignalBoundary 注入       ← 信号制度选择
  PASS  拓扑非空                  ← 有没有轨道
  PASS  拓扑缓存生效              ← rebuilds 是否失控
  PASS  已注册内核                ← 脚本加载了没有
  PASS  控制通道可入队/可取出      ← 外部指令路径
  PASS  内核台账可读写            ← 存档接没接上
  PASS  列车状态可读              ← graph/runtime 是否就绪
```

每一项失败都带一句「这意味着什么」，不是只给一个 false。

**它确实会失败**：这套机制在开发中真的拒过一次启动
（`required mixins are not applied -> Train#tick TAIL ...`），
原因是 `@Inject` 的处理方法被内联后丢弃、反射查不到它。见上一节。

它替代不了人工目视的三项（灯色、HUD、道岔物理位置），但能把「代码侧是否成立」
从半小时的对照变成一行命令。

**D 阶段闭环：外部进程开车**

之前只证明了「外部进程能收到遥测、能扣车」。这一轮补上最后一段：

```
Python 进程 → WebSocket → RemoteKernel → ControlChannel(EXTERNAL)
            → SpeedApplier → SpeedControl → Train#tick → 列车
```

实测（脚本内核全部停用，只留网页桥的内核）：

```
train : speed=0.300 target=0.300 blocked=false
```

`0.300` 是客户端里写死的 `CAP`，不是任何脚本给的数——**这个数字只可能来自那个 Python 进程**。

和脚本内核走的是两条不同的路：

| 入口 | 机制 | 优先级 |
|---|---|---|
| 脚本内核 | `DispatchKernel` → 本帧 `out` | 按提交顺序 |
| 外部程序 | `RemoteKernel` → `ControlChannel.EXTERNAL` | 低于玩家、高于自动化 |

两条都独立成立，意味着「谁能开车」不再只有一个入口。

**发布核对：文档里的版本和产物对上了**

`docs/16` 之前还写着 0.1.0，而且**命令表是旧的**——它列的 `/ctd list|scripts|reload|stats`
是脚本侧的命令，宿主侧真正天天用的是 `/ctd host status` / `/ctd selftest`，一个都没写。

现在 `docs/16` 按实际输出重写：

| 项 | 之前 | 现在 |
|---|---|---|
| 版本 | 0.1.0 | 0.3.0（四个仓库一致） |
| 启动日志 | 两行（其中一行已不存在） | 实际六行，含 `modules 版本` 与 `ctdweb: listening` |
| 命令表 | 4 条脚本命令 | 宿主 9 条 + 脚本 7 条，分开列 |
| 构建步骤 | 无 | JDK 21、三处 API jar 拷贝、`clean` 会被占用目录卡住的提醒 |
| 已知限制 | 6 条里 3 条已过时 | 重新按现状写（L1 缓存、L2 物理扳动、K4、CRN） |

顺带清掉全库残留的 `0.1.0`：`docs/10` 的版本行、`docs/12` 的示例输出、`docs/18` 的工件表。

验收任务清单也补全了（可作为发版核对表）：

```
ctdscript: signalBridgeTest railCodecTest deviceSwitchBridgeTest kernelAtoBridgeTest reloadTest
ctdweb   : bridgeTest taskBrokerTest controlTest
```

本轮实测 **ctdweb 三项全绿**（`FAIL=0`）。

**离线验收一次跑完：14 个任务 / 131 项检查**

`tools/verify-all.ps1`：

```
repo       task                    ok   fail
ctdapi     assignmentsTest          9    0
ctdhost    signalApplierTest       12    0
ctdscript  graalpySmokeTest         6    0
ctdscript  signalBridgeTest         5    0
ctdscript  railCodecTest           10    0
ctdscript  deviceSwitchBridgeTest  13    0
ctdscript  kernelAtoBridgeTest     11    0
ctdscript  loadPhaseTest            3    0
ctdscript  operationBridgeTest      6    0
ctdscript  sandboxTest              6    0
ctdscript  reloadTest              11    0
ctdweb     bridgeTest              18    0
ctdweb     taskBrokerTest          13    0
ctdweb     controlTest              8    0

合计: 131 项通过, 0 项失败
```

**核对过程中发现的两件事**

1. `AssignmentsPublishTest` 在 **ctdapi** 仓库（`top.hibermod.ctdapi.tools`），
   而文档一直把它写成 ctdscript 的测试。它一直有 gradle 任务（`assignmentsTest`），
   只是**在另一个仓库里**——按文档去找会找不到。

2. 三个从未跑过的任务（`loadPhaseTest` / `operationBridgeTest` / `sandboxTest`）
   这次全跑了一遍，共 15 项，全绿。之前 PLAN 写着「沙箱六项全绿」，
   但我自己从没在本轮跑过它——现在是真的跑过了。

**C7 道岔逻辑锁：能力一直有，测试一直是零**

道岔有两条路——逻辑锁（永远可用）与物理扳动（要装汽鸣铁道）。
物理那条离线测不了，于是「道岔翻译」这项能力在 PLAN 里标了很久的 ✅，
**但从来没有任何一条测试**。逻辑锁是进路冲突检查的基础：
它错了，两列车会被排进同一股道，而现场看起来完全正常。

本轮补上 `switchApplierTest`（16 项）：

| 组 | 检查 |
|---|---|
| 未锁 | `lockOf` 空、不拦任何股道、holder=0 |
| 上锁 | `apply` 计数、锁到指定股道、holder 记录 |
| 冲突 | 同股道放行、异股道拦截 |
| 解锁 | 锁消失、holder 归零、不再拦截 |
| 多岔口 | 两把锁并存、holder 不串、互不影响 |
| 幂等 | 重复上锁不增加条目、后锁覆盖前锁 |

`SwitchApplier.apply` 只吃 `Command.SwitchCommand` 与纯数据 id，所以这个测试
不需要世界、不需要 Create，跑起来只要十几秒。

**顺带说明**：`signalApplierTest`（12 项）在 ctdhost，`assignmentsTest`（9 项）在 ctdapi——
它们都在各自仓库的 gradle 任务里，`tools/verify-all.ps1` 会一起跑。

离线验收现在是 **15 个任务 / 147 项检查**。

---

## 验收标准（正式条目）

> 这一条是**门槛**，不是风格建议。写在这里是因为它被违反过几十次。

### 三个状态

| 标记 | 含义 | 必要条件 |
|---|---|---|
| ✅ | 完成 | 存在一种**可重跑**的验证方式，且最近一次跑过、通过 |
| 🟡 | 有实现，未验证 | 代码在，但没有可重跑的验证，或验证方式依赖人现场看 |
| ❌ | 未实现 | 没有代码，或明确推迟 |

### 「可重跑的验证方式」指三者之一

1. **离线验收任务**：`gradlew <task>`，输出 `ok / FAIL`，并入 `tools/verify-all.ps1`
2. **游戏内命令**：`/ctd selftest`、`/ctd host status` 的字段，任何人执行都能判断
3. **明确的人工步骤**：写在 `docs/17` 里，有期望输出、有「不对说明什么」

### 以下**不算**证据

- 「编译通过」
- 「跑起来没崩」
- 「某次手工看过，当时是好的」
- 日志里一句 `loaded` / `ok`，但没有可判断的数值

### 已知被这条标准抓出的问题

| 项 | 当时标的 | 实际 |
|---|---|---|
| ATO 注册表解冻 | ✅ 重载可用 | 重载**两次**就崩（`reloadTest` 补了用例才发现） |
| 道岔逻辑锁 | ✅ | **零测试**（后来补 16 项） |
| 内核台账存档 | ✅ `KernelEnv.state()` 已接 | 存档类**从未被实例化**，重启即丢 |
| 拓扑缓存 | ✅ | 变更判断是**自己跟自己比**，只加一条边/一架信号机永远发现不了 |
| 三个离线任务 | ✅ 全绿 | 本轮之前**从未跑过** |
| `assignmentsTest` | ✅ 9 项 | 任务在 **ctdapi** 而非 ctdscript，按文档找不到 |

共同点：都通过了「看代码觉得对」这一关，都没通过「跑一遍」。

---
## 状态快照（2026-10-30 17:39）

### 已验证（可重跑）

| 层 | 方式 | 结果 |
|---|---|---|
| 离线验收 | pwsh -NoProfile -File tools/verify-all.ps1 | **15 个任务 / 147 项，全绿** |
| 游戏内代码侧 | /ctd selftest | **9 项全绿** |
| 脚本驱动列车 | /ctd host status | speed=0.350 blocked=false（持续十余秒） |
| 外部进程驱动 | 	ools/driving_client.py | speed=0.300 blocked=false |
| 存档 | /ctd state put → /stop → 重启 → show | 值仍在，world/data/ctdhost.dat |
| 信号写入 | 内核日志 | 两架信号机四面写成 ctd:clear |
| 拓扑缓存 | status 的 ebuilds= | 稳态 1200 刻只重建 1 次 |

### 未验证

| 项 | 缺什么 |
|---|---|
| 灯色 / HUD 外观 / 道岔物理位置 | 需要人看着屏幕 |
| K4 平台设备 | 规格未定，未实现 |
| CRN 集成 | 缺对应 jar |

### 本轮正在做、尚未完成

审「标了 ✅ 但没有可重跑验证」的条目，目前已经查过：

- ✅ SwitchApplierTest 16 项（上一轮补上）
- ⏳ RouteApplier（进路分配 / 剩余长度 / 岔口合并）——**只依赖 API 类型，可离线测**
- ⏳ Odometers（里程累加 / prune）——纯逻辑，可离线测
- ⏳ AtoPass（ATO 主循环与内核取 min）——依赖 Create，只能进游戏验

### 判据

标 ✅ 必须有一种**可重跑**的验证方式（离线任务 / 游戏内命令 / 明确的人工步骤）。
只是「编译通过」或「某次手工看过」，标 🟡。

**继续审：`RouteApplier` 与 `Odometers` 也补上了（17 项）**

按新写的验收标准，这两项此前只有「能编译」+「跑起来没崩」，属于 🟡。

`routeOdometerTest` 只依赖 API 类型（`Topology`、`Command.RouteRequest`、
`Telemetry.RouteState`），不需要世界也不需要 Create：

| 组 | 检查 |
|---|---|
| 进路 | 剩余长度 = 边长度之和；岔口 = 连续两边共用的节点；相同申请不换 `routeId`；未知边被忽略；不申请即撤销；空路径不崩；拓扑为 null 不崩 |
| 里程 | 有符号累加（倒车会减少）；多车各自独立；`prune` 清掉消失的车；`prune(空)` 清空 |

写这个测试时踩到两处 API 事实：

- `Topology` 是**接口**，构造入口在实现的 `TopologyView.builder()`，不是 `Topology.builder()`。
- `Odometers` 原本是**包私有**，测试碰不到；改成 public 才测得了。
  「测不了」往往是「设计上没打算让别人碰」——但里程这种纯函数没理由藏着。

离线验收现在 **16 个任务 / 164 项检查**。

剩下一项 `AtoPass` 依赖 Create 的 `Train`，只能进游戏验，暂标 🟡。

**最后一项：`AtoPass` 也能离线测了（10 项）**

它原本「只能进游戏验」，因为它直接读 `Create.RAILWAYS.trains`。
把「拿列车 / 改目标速度」抽成 `TrainAccess` 之后，四条规则都能在没有世界的情况下验证：

| 规则 | 写错的后果 | 测试 |
|---|---|---|
| 与内核取 min | ATO 会压过调度权限 | ATO 更紧取 ATO；ATO 更松取内核 |
| 手动/脱轨不接管 | 跟司机抢方向盘 | `manuallyDriven` 时 `apply=0` 且目标速度不变 |
| 没有匹配制度就缺席 | 没装车载设备的车被接管 | 空 `signalSystems` → 不接管 |
| 连续失败 10 次禁用 | 坏引擎每帧刷屏 | 跑 12 帧，计数停在 10，速度不变 |
| 引擎返回 null | 本帧不该动 | 不接管，且**不算失败** |

写测试时撞到的两个 API 事实：

- `AtoEngine.Decision` 的组件是 `(traction, brake, targetSpeed, doors, hold, service, reason)`，
  不是我凭印象写的顺序——**靠猜会静默写错**（编译器只会在类型不匹配时报警）。
- `AtoEngines.freeze()` 会校验「ATO 指向的制度必须已注册」，
  所以测试里不能用自造的 `ctd:test_system`，得先 `SignalRegistries.bootstrap()`。

离线验收现在 **17 个任务 / 174 项检查**。

至此 PLAN 里所有标 ✅ 的条目都有可重跑的验证方式；
剩下 🟡 的只有三项目视（灯色 / HUD / 道岔物理位置）与未实现的 K4。

**API 的三条「沉默不变量」补上了 28 项**

这三样都没有画面、不会抛异常，错了只表现为行为不对：

| 不变量 | 写错的后果 | 测试 |
|---|---|---|
| 能源「未知」≠「耗尽」 | 没装联动模组的存档里所有列车瞬间趴窝 | 无提供方 / 提供方返回 null / 提供方抛异常 → 都仍是 `UNKNOWN` 且 `canTraction()=true` |
| 授权哨兵 | 列车被一个从未表态的内核拦住 | `distance` 无哨兵（0 是合法授权，靠 `has()` 区分）；`speedCap=-1` 才是没说过 |
| 灯态空表 | 清空之后所有信号机看起来都被设成灭灯 | 空表 `size=0`、`get()` 返回空串而非 null、快照是独立副本 |

**第一次跑是三个 FAIL，全部是我写错了期望值**：

- 我以为 `Authority.distance()` 未表态时返回 `-1`——实际返回 `0.0`，
  `-1` 只用在 `speedCap`。类注释写得很清楚，是我没读到位。
- 我以为 `SignalStates.get()` 未命中返回 `null`——实际返回空串。

这正是这类测试的价值：**它把契约写成可执行的断言，写错就当场失败**。
如果不写，这两处「凭印象的契约」会一直留在脑子里，直到某天有人照着它写代码。

另外验证了合并语义：两个提供方取「更坏」的状态与较小余量，
一个提供方抛异常不影响其它提供方（`Interop` 的 try/catch 是真的有用）。

离线验收现在 **18 个任务 / 202 项检查**。

**设备注册表的三条规矩（14 项）

| 规矩 | 不守的后果 | 结果 |
|---|---|---|
| 冻结后不许注册 | 同一份存档在不同启动顺序下解析出不同设备，排障无法复现 | 抛异常 ✓ |
| 重复 id 必须拒绝 | 某个模组的设备被另一个模组**悄悄顶掉** | 拒绝且原条目保留 ✓ |
| `DeviceSite` 必须属于地面或车载之一 | 两边都为空的设备既查不到位置也找不到列车 | 构造期即拒绝 ✓ |

另外验了热重载路径（解冻 → 移除 → 重注册）、按能力筛选、
车载/地面分类统计，以及「重复移除返回 false」这种容易写错的返回值。

离线验收现在 **19 个任务 / 216 项检查**。

**本轮记录到的两件事**

1. `RailCodecTest` 已经覆盖了畸形输入（缺 `@`、坐标数量不对、非数字、坏维度、缺竖线）
   与 1000 次随机往返——**这块不用补**，审之前先看已有的比事后补更省事。
2. 设备注册表的规矩一条都没测过。它是 A7（设备注册带 `signalSystems`）的地基，
   而 A7 一直标着 ✅。

**制度表与交路表：两条只在 `freeze()` 时才校验的规则**

| 规则 | 不守的后果 | 结果 |
|---|---|---|
| 制度必须有停车语义的灯位 | 没有停车语义的「制度」让列车永远停不下来，它不是制度，是一组颜色 | 冻结时拒绝 ✓ |
| 交路必须指向已注册的制度 | 列车挂着一段永远解析不出信号语义的时刻表跑，运行期零提示 | 冻结时拒绝 ✓ |

写这个测试时被校验器连着纠正了三次，每次都是我不知道的规则：

1. **机型必须归属某套制度**——不显式 `.system(id)` 时默认挂到 `ctdapi:mainline_4`，
   而那张表被我 `resetForTests()` 清过，于是报「机型归属未知制度」。
2. **制度被移除后，它的机型也得撤**——引用完整性也是整表校验的一部分。
3. `SignalSystem.Builder` 的机型入口是 `typeId(ResourceLocation)`，
   `type(SignalType)` 是另一个重载——按名字猜会编译不过（这次是好事）。

这三条都不在文档里，是靠「跑一遍」逼出来的。

离线验收现在 **20 个任务 / 224 项检查**。

**`blocked=true` 的第二种成因：前方区块没加载**

第七轮之后列车又不动了，`blocked=true`。这次不是轨道尽头（已经 244 节点），
而是**列车前方那段轨道落在未加载的区块里**——无人在线的服务端上很常见。

两个修法，都是调试命令自己的责任：

1. **按列车位置选叶子**：原来取「前两个叶子」，图被接长多次之后叶子越来越多，
   于是接在了几条无关支线上，列车那一端还是堵着。
2. **强制加载新节点所在区块**：`debugtrack` 铺完节点后 `setChunkForced`。

修完立刻恢复：

```
train : speed=0.350 target=0.350 blocked=false    ← 连续两次采样
```

**这是 `blocked` 的第二种成因**，与「轨道尽头」现象完全一样：

| 成因 | 怎么区分 |
|---|---|
| 轨道尽头 | 接长轨道后恢复 |
| 前方区块未加载 | 接长也不恢复；强制加载后恢复 |
| 车太长 | 接长也不恢复；换短车或延长两端 |

这三种以前都只表现为一个 `blocked=true`。

**「怎么验」收敛到一处（F 阶段收尾）**

三份东西曾经各自列验收清单，它们会漂移——之前「文档说全绿、其实没跑过」
就是这么发生的。现在：

| 位置 | 内容 |
|---|---|
| `tools/verify-all.ps1` | **唯一**的离线任务清单，`-List` 可打印供核对 |
| `/ctd selftest` | 游戏内九项，不重复离线项 |
| `docs/17` | 只写无法自动化的（灯色 / HUD / 道岔物理位置） |

发版核对表也加了「`-List` 输出与文档一致」一项，用来防漂移。

**本轮的一个教训**：我为了给脚本加 `-List`，用了那套「逐行去掉首尾引号」的
清理脚本——它是给被 `'''` 弄脏的 Java 文件用的，对 PowerShell 文件会把**合法**的
字符串结束引号一起删掉，把 `verify-all.ps1` 切坏了四处。

修法不是再打补丁，而是**整文件重写**：这个脚本我完全知道内容，重写比逐行修复可靠。
`docs/19` §8 早就写着「脚本化改源码超过两处插入就该整文件重写」，这次是它救的场。

**设备指令不再「数一下就丢」（C 阶段 A4–A6 的落点）**

以前的路径是：

```java
for (Command.DeviceCommand c : commands) {
    OperationFeed.deviceCommand(c);
    forwardedDevices++;        // ← 数了一下
}
```

脚本发「开站台门」，宿主计数 +1，然后**什么也没发生**，事后也无从查证发过什么。
`/ctd host status` 里那行 `forwarded: devices=N` 看起来像「已经转交了」。

现在新增 `DeviceCommands`：

| 能力 | 说明 |
|---|---|
| 按设备 id 记住最后一条指令 | 后一条覆盖前一条，设备数不增长 |
| 时间戳与 holder | 能回答「谁在什么时候让它开的」 |
| `accepted / rejected` | 坏指令（空 deviceId）被拒绝并计数，不污染已有记录 |
| `snapshot()` | 按 id 排序，输出稳定 |

状态行多了一段：`forwarded: ... deviceCmds=<设备数>/<接受条数>`。

`deviceCommandsTest` 17 项：记录 / 覆盖 / 多设备互不影响 / 拒绝 / 空负载 /
排序 / 清空。

**边界仍然清楚**：它不解释 payload。门、屏、计轴器的语义属于各自的设备实现；
宿主这一层只保证「收到、存下、查得到、可追溯」。

离线验收现在 **21 个任务 / 241 项检查**。

**设备指令落点：运行时验证**

写完 `DeviceCommands` 之后立刻进真实服务端验了一遍（不给它留「只过了离线测试」的余地）：

```
# 一个诊断内核每 100 帧向同一台虚构设备发一条指令
forwarded   : devices=7 energy=0 ... deviceCmds=1/7
                                     ↑ 1 台设备，7 条已接受
```

`1/7` 这个数字同时证明了四件事：

| 事实 | 由哪个数字体现 |
|---|---|
| 指令真的进到了落点 | `deviceCmds` 不为 0 |
| 同一台设备被正确合并（不是 7 台） | 分子是 1 |
| 计数在累加 | 分母是 7 |
| 老的 `forwarded` 仍然照常工作 | `devices=7` |

诊断内核用完即停用（`disabled/kernel/device_probe.py`），不留在常规加载路径里。

**B 阶段的真 bug：`/ctd reload` 一直是失败的**

服务端日志里反复出现：

```
block registration is closed: content definitions are startup-only
hud registration is closed: content definitions are startup-only
reload failed (1-3 failures), rolled back 11 registrations
```

也就是说 **`/ctd reload` 在任何带 `block/` 或 `hud/` 脚本的世界里永远失败**。
而且失败是「优雅」的：整批回滚成功，系统继续按旧脚本跑，
从外面看只像「重载没生效」——我还据此判断过「改完脚本没反应」。

两个根因：

| 根因 | 现象 | 修法 |
|---|---|---|
| HUD 被当成启动期内容 | `hud/demo.py` 每次都失败 | HUD 是屏幕数据，没有注册表约束：`ContentRegistry.reopenHuds()` 在重载期间只放开这一半 |
| 重载重跑了**全部**目录 | `block/demo.py` 必然失败（方块进不了已关闭的注册表） | `RELOADABLE_DIRS = kernel, ato, broadcast, hud`；signal/device/timetable/block 属于启动期 |

另外把 HUD 纳入了 `ScriptRegistry`（新增 `Kind.HUD`），否则回滚时 HUD 恢复不回来——
它以前只进 `ContentRegistry`，`removeAll`/`restore` 都碰不到它。

实测（同一世界连续两次）：

```
reloaded 7/7 scripts loaded, restored=7
reloaded 7/7 scripts loaded, restored=7
selftest — 全部通过
```

**这一条推翻了我此前「B 阶段完成」的判断**：`reloadTest` 用的是只含
`signal/timetable/ato` 的临时目录，恰好绕开了 `block/` 与 `hud/`。
测试通过、真实世界失败——这是本仓库里第二例「测试目录与真实目录不一致」。

**取消自动回滚：失败必须是失败**

用户的要求很直接：**自动回滚脚本/方法这种行为不应当存在**——它会造成意料之外的错误，
而且把本该被发现的致命错误直接抛弃，等到生产环境才炸。

这个判断被本项目的历史证实：`/ctd reload` 在带 `block/` 或 `hud/` 的世界里
**每一轮都失败并自动回滚**，日志只有一行 WARN，系统照旧跑旧脚本。
所以那个 bug 藏了好几轮，我甚至据此判断过「改了脚本没反应」。

### 改动

| 之前 | 现在 |
|---|---|
| 失败 → `restore(snapshot)` 把旧条目塞回去 | **不做任何 restore** |
| 日志 `warn: reload failed, rolled back N` | `error: reload FAILED: N script(s) did not load; no rollback` + 逐条列出脚本名与异常 |
| 命令输出 `ROLLED BACK 6/7` | `FAILED 6/7 ... — no rollback; script kernels are not running until this is fixed` |
| 重载会删掉**整份**快照（含 signal/timetable） | 只删**可重载**目录产生的条目（目前是 HUD） |

最后一条尤其重要：有回滚的年代，删掉启动期定义还能塞回去；
**取消回滚后它就是真损失**——一次失败的 `/ctd reload` 会永久抹掉信号制度与交路，直到重启。
重载本来就不该碰启动期定义。

### 实测（真服务端，三段连续）

```
reloaded 7/7 scripts loaded                      ← 正常
FAILED 6/7 scripts loaded                        ← 改坏 kernel/hold_all.py
- kernel/hold_all.py — RuntimeError: deliberate failure ...
  — no rollback; script kernels are not running until this is fixed
  (startup definitions still need a server restart if you changed those)
reloaded 7/7 scripts loaded                      ← 修好（不需要重启）
```

日志同时是 `ERROR` 级：

```
[ctdscript/]: reload FAILED: 1 script(s) did not load; no rollback, script kernels stay disabled until this
[ctdscript/]:   - kernel/hold_all.py — RuntimeError: deliberate failure @ SourceSection(...)
```

### 代价（写清楚，不藏）

失败之后脚本内核**不再运行**——这是有意的：跑「一半新一半旧」的脚本比停下更难查。
要恢复只需改好脚本再 `/ctd reload`；**改过启动期目录则必须重启服务器**。
测试 `reloadTest` 已按新语义重写（14 项），并额外断言「启动期定义全程未被重载触碰」。

**把「静默吞异常」从宿主侧清干净**

上一轮取消了重载回滚。同一类问题还有十几个地方：`catch (Throwable ignored) {}`，
全部在关键路径上——mixin 的速度施加、被动减速拦截、拓扑重建、道岔反射、渲染。

它们的共同后果是**功能悄悄失效而系统看起来正常**，正是用户指出的那类问题。

### 改法：失败可见但不刷屏

新增 `Failures.caught(位置, 异常)`：

| 时机 | 级别 | 内容 |
|---|---|---|
| 某点**第一次**失败 | ERROR | 位置名 + 异常类型与消息 + 完整栈 |
| 之后每 100 次 | WARN | 位置名 + 累计次数 |
| 任意时刻 | — | `/ctd host status` 的 `swallowed:` 行列出所有出过问题的点与次数 |

「第一次一定报全」是关键：长期失效不会被日志淹没，但首发一定会被看到。

### 清掉的位置（11 处）

| 文件 | 位置 |
|---|---|
| `TrainTickMixin` | 速度施加 |
| `PassiveSlowdownMixin` | 被动减速拦截 |
| `NavigationMixin` | 寻路后施加 |
| `TopologyMirror` | 单节点读连接、单图读信号、签名计算 |
| `SwitchApplier` | 汽鸣铁道道岔反射 |
| `DebugTrackCommand` | 强制加载区块 |
| `NixieTubeRendererMixin` / `SignalRendererMixin` | 客户端渲染（出错退回原版，但会记） |

### 验证

- `failuresTest` 14 项：首次即记、持续累加、分点统计、null 异常不抛、可重置。
- 真服务端：`selftest` 九项全过，`swallowed: none`，日志无 `failed (first time`。
  也就是说这些兜底路径当前**没有**在失败——先证明「干净时是干净的」，
  以后一旦有记录就一定是真问题。

离线验收现在 **22 个任务 / 255 项检查**。

**失败登记搬到 API 层：宿主与脚本共用一张表**

上一轮把宿主侧 11 处静默 `catch` 接到 `Failures`，但那张表在 `ctdhost` 里——
脚本运行时是**另一个模组**，写不进去。于是「哪里在悄悄失败」要看两个地方，
等于没有统一视图。

现在登记表在 `ctdapi.diagnostics.Failures`：

| 写入方 | 位置 |
|---|---|
| 宿主 | `ctdhost.Failures`（同名的薄转发，无独立状态）→ API 层 |
| 脚本运行时 | `PythonKernel` 的 onDetach、`KernelApi` / `AtoApi` 的撤销失败 → API 层 |
| 读取方 | `/ctd host status` 的 `swallowed:` 行 |

语义不变：**第一次失败 ERROR 带栈，之后每 100 次一条 WARN，计数永久保留**。

实测（真服务端）：

```
selftest    : 全部通过
swallowed   : none
/ctd reload : reloaded 7/7 scripts loaded
swallowed   : none        ← 重载成功也不会往表里写垃圾
```

「干净时必须是干净的」这一点被特意验证：如果 `swallowed` 平时就非空，
这个字段很快就会被人忽略——那样它又变回了一个装饰。

离线验收 **22 个任务 / 255 项检查**。

**文档对齐（F 阶段收尾）**

三份文档里还留着已过期的说法，逐条改掉：

| 文件 | 过期内容 | 现在 |
|---|---|---|
| `docs/16` | 「失败整批回滚」、20 任务 / 224 项 | 重跑可重载目录、**不回滚**、22 任务 / 255 项；新增 §9「失败语义」 |
| `docs/18` | 0.2.0 段写着「整批回滚」未加注 | 标注「0.2.0 语义，0.3.0 起已取消」；新增「语义变更：取消自动回滚」 |
| `docs/19` | 15 任务 / 147 项、`11/11 scripts loaded`、「注册表回滚」 | 22 / 255、`reloaded 7/7`（连续两次）、「注册表撤销（**不是失败回滚**）」；补 `switchApplierTest` / `routeOdometerTest` 证据行 |

同时把 `docs/19` 的可信项对齐到实测数字：遥测带 17 项验收、道岔锁带 16 项。

**这一轮没有改代码**——只改文档。但它不是形式主义：
上一轮 `/ctd reload` 的 bug 之所以藏那么久，一半原因是**文档里写着「失败整批回滚」**，
于是「重载失败」被当成正常现象。文档说错了什么，人就会按错的去理解症状。

`docs/` 不在任何 git 仓库里（工作区根目录没有 `.git`），所以这些改动只落在磁盘上。

**`blocked=true` 的第三种成因：列车脚下的区块**

重启之后 `blocked=true`，接长 96 格也不恢复。原因跟上一轮不同：

| 上一轮 | 这一轮 |
|---|---|
| 列车**前方**新铺的区块没加载 | 列车**自己停在旧段**上，那一格所在区块早已卸载 |

`forceChunk` 只覆盖「本次新建的节点」。列车停在一年前建的旧节点上时，它自己和它前面那一格都在未加载区块里——
于是「接长了、还是不跑」，看起来像接长命令没生效。

修法：`forceAroundTrains()` 在接长前把每节车厢周围 3×3 区块强制加载。

实测：

```
debug track: nodes=244 +48 node(s) (96 blocks)
train: speed=0.350 target=0.350 blocked=false     ← 8 秒后第一次采样
train: speed=0.007 blocked=true                   ← 8 秒后第二次：96 格跑完到头了
```

两次采样恰好构成完整证据：**它真的动了**，而且**跑完就停**（不是一直假跑）。

### 三种 `blocked` 成因汇总（同一个现象，三个完全不同的原因）

| 成因 | 判别方法 |
|---|---|
| 轨道尽头 | 接长轨道 → 恢复 |
| 前方区块未加载 | 接长不恢复；强制加载新节点所在区块 → 恢复 |
| **列车自身所在区块未加载** | 接长不恢复；强制加载列车周围区块 → 恢复 |
| 车太长 | 都不恢复；换短车或两端都延长 |

---

## 交付状态（一页）

### 能信 —— 每项都有可重跑的验证

| 能力 | 怎么验 | 结果 |
|---|---|---|
| 脚本真的能开动列车 | `/ctd host status` 看 `speed/target/blocked` | `speed=0.350 blocked=false`（双采样） |
| 外部进程也能开 | `tools/driving_client.py` | `speed=0.300 blocked=false` |
| 热重载 | `/ctd reload` 两次 | `reloaded 7/7` ×2 |
| 重载失败可见 | 故意改坏一个 kernel | `FAILED 6/7` + 逐条异常 + `ERROR` 日志 |
| 存档跨重启 | `/ctd state put` → `/stop` → 重启 → `show` | 读回成功（`world/data/ctdhost.dat`） |
| 注入真的生效 | `/ctd selftest` | 9 项 PASS（含三个 `@Unique` 标记） |
| 静默失败为零 | `/ctd host status` 的 `swallowed:` | `none` |
| 离线全绿 | `tools/verify-all.ps1` | **22 任务 / 255 项** |

### 不能信 —— 需要人眼

| 项 | 为什么机器验不了 |
|---|---|
| 灯色 / 辉光管 | 要分辨「画出来的颜色对不对」 |
| HUD 外观 | 浅灰底板、正负号速度、双向指针，只能看 |
| 道岔物理扳动 | 需要装汽鸣铁道，看岔位是否真的动 |

### 下一步（三件，都需要外部输入）

1. **目视三项**：照 `docs/17` §9 的十二格打勾表走一遍（带通过/失败判据），把看到的告诉我。
2. **K4 平台设备**：给一份设备清单（车门 / 站台屏 / 发车指示器……），
   我按 `Devices` 现有机制实现方块与行为并补测试。
3. ~~**CRN 集成**：给 jar~~ → **已实测共存**（见 `docs/04` §7）：两个 jar 正常加载、CRN accessor 注入成功、零 mixin 冲突、`selftest` 9/9、`speed=0.350 blocked=false`。剩 CRN 的 GUI 外观与「两套规划同时工作谁让步」需客户端。

### 我这几轮改掉的「假完成」

| 曾经的 ✅ | 真相 |
|---|---|
| B 阶段完成 | `/ctd reload` 在真实世界里**永远回滚**，测试目录恰好绕开了 `block/` 与 `hud/` |
| A4–A6 完成 | 设备指令只计数，**没有任何落点** |
| C7 道岔完成 | 逻辑锁**零测试** |
| C8 存档完成 | `HostSavedData` 从来没被实例化，`markStateDirty()` 是空方法 |
| C3 拓扑缓存 | 签名「节点数与自己相加」，永远不变 |
| C9 安全兜底 | 11 处 `catch (Throwable ignored)` 把失败吞掉 |

**共同点**：都通过了「读代码觉得对」。验收标准要求「可重跑的检查」之后，
这六处才逐个暴露出来。

**CRN 0.10.0-C6 实测共存（原「缺 jar」阻塞解除）**

拿到 jar 后第一件事不是写集成代码，而是**把它们装进同一个服务端看会不会打架**。

| 检查 | 结果 |
|---|---|
| CRN / DragonLib 加载 | ✅ 日志确认版本 |
| CRN 的 `ScheduleRuntimeAccessor` 注入 | ✅ 日志确认 |
| mixin 冲突 | ✅ 零（无 `Mixin apply failed`） |
| 我们的四个注入 | ✅ `selftest` 9/9 |
| 拓扑镜像 | ✅ `nodes=244 edges=245` |
| 脚本内核 | ✅ `kernels: 5` |
| **速度控制** | ✅ `speed=0.350 target=0.350 blocked=false` |
| 静默失败 | ✅ `swallowed: none` |

出现的两条 WARN 都与 CRN 无关：一个 mixin 插件缺 `org.jetbrains.annotations`
（Mixin 自己会跳过），一个更新检查的 SSL 证书链（离线环境正常）。

**这件事值得记下来的原因**：`docs/04-compat-matrix.md` 里有整整一节「CRN 的实际注入面」，
写得很细，但那是**读源码推出来的**。真装上去之后结论一致——运气好，也是因为当初
那节确实是逐文件核实的，不是凭印象。反过来，`docs/19` 里六处「假完成」就是凭印象的下场。

仍未验：CRN 的仪表盘外观、以及「CRN 的线路规划与我们的调度器同时工作时谁让步」——
都需要图形客户端。