# CTD 测试线 · 简单调度
#
# 只做一件事件：**放行**。
#   - 前方授权足够 → 放行到该授权
#   - 授权不足 → 保持（hold），由 CBTC 内核负责把速度压下来
#
# 为什么调度和 CBTC 分成两个内核：
#   调度决定「该不该走」（业务/运营），移动闭塞决定「能走多远」（安全）。
#   合在一起以后，任何一次运营策略调整都会碰到安全逻辑——
#   那是这类系统最典型的失控路径。

from ctd import kernel

# 放行门槛：授权小于这个值就原地保持（格）
MIN_AUTHORITY_TO_RELEASE = 30.0

# 停站时速度低于这个值算「停稳」（格/刻）
STOPPED_SPEED = 0.02

LOG_EVERY = 200


class SimpleDispatch:
    def __init__(self):
        self._frame = 0

    def on_frame(self, ctx, out):
        self._frame += 1
        held = 0
        released = 0

        for train in ctx.trains():
            if train.manually_driven():
                continue

            authority = train.authority()

            # 授权不足：站住不动。给 (0, 0) 而不是不给指令——
            # 不给指令意味着「交回原生」，那会让车按自己的时刻表继续冲。
            if authority < MIN_AUTHORITY_TO_RELEASE:
                out.hold(train.id(), "authority %.0f < %.0f" % (authority, MIN_AUTHORITY_TO_RELEASE))
                held += 1
            else:
                released += 1

        if self._frame % LOG_EVERY == 0 and (held or released):
            print("[dispatch] released=%d held=%d" % (released, held), flush=True)


kernel.register("ctd", "dispatch", SimpleDispatch())
