# CTD test line: CBTC moving-block kernel
from ctd import kernel

SAFETY_MARGIN = 8.0
MIN_MA = 2.0
LOG_EVERY = 100
BIG_MA = 10000.0

STOP_ASPECT = "ctdapi:stop"
PROCEED_ASPECT = "ctdapi:proceed"


def movement_authority(me, ahead):
    if ahead is None:
        return BIG_MA
    gap = ahead.odometer() - me.odometer()
    gap -= (ahead.length() + me.length()) / 2.0
    return max(0.0, gap - SAFETY_MARGIN)


class Cbtc:
    def __init__(self):
        self._frame = 0

    def _drive_signals(self, ctx, out, trains):
        """Wayside lamps show section occupancy only.

        The authority in CBTC lives on board; the lamp exists so a person on the
        ground can read the section at a glance.
        """
        try:
            occupied = set()
            for t in trains:
                edge = t.leading_edge()
                if edge:
                    occupied.add(edge)

            for sig in ctx.signals():
                if len(sig) < 4:
                    continue
                signal_id, side, current, edge = sig[0], sig[1], sig[2], sig[3]
                if not edge:
                    continue
                want = STOP_ASPECT if edge in occupied else PROCEED_ASPECT
                if current != want:
                    out.aspect(signal_id, side, want)
        except Exception as e:
            # A broken lamp must not take speed control with it.
            print("[cbtc] signal update failed: %s" % e, flush=True)

    def on_frame(self, ctx, out):
        trains = ctx.trains()
        self._frame += 1
        if not trains:
            return
        ordered = sorted(trains, key=lambda t: t.odometer())
        summary = []
        for i, train in enumerate(ordered):
            if train.manually_driven():
                continue
            ahead = None
            for other in ordered[i + 1:]:
                if other.odometer() > train.odometer():
                    ahead = other
                    break
            ma = movement_authority(train, ahead)
            cap = min(train.max_speed(), train.speed_for(ma)) if ma > MIN_MA else 0.0
            out.proceed(train.id(), ma, cap)
            summary.append("%s ma=%.0f cap=%.2f" % (train.name() or train.id()[:6], ma, cap))
        self._drive_signals(ctx, out, ordered)

        if self._frame % LOG_EVERY == 0 and summary:
            print("[cbtc] " + " | ".join(summary), flush=True)


class _Fake:
    def __init__(self, name, odo, length, accel=0.5, maxs=1.4):
        self._name, self._odo, self._len = name, odo, length
        self._accel, self._max = accel, maxs

    def name(self):
        return self._name

    def odometer(self):
        return self._odo

    def length(self):
        return self._len

    def max_speed(self):
        return self._max

    def speed_for(self, distance):
        import math
        return min(self._max, math.sqrt(2.0 * self._accel * max(0.0, distance)))


def selftest():
    a = _Fake("A", 0, 20)
    b = _Fake("B", 100, 20)
    checks = [
        ("gap100 -> ma72", abs(movement_authority(a, b) - 72.0) < 1e-6),
        ("too close -> 0", movement_authority(a, _Fake("C", 12, 20)) == 0.0),
        ("no leader -> big", movement_authority(a, None) >= 1000.0),
        # 取两个都还没被封顶的短距离，才能看出「授权越大、允许速度越高」
        ("curve monotonic", a.speed_for(0.5) < a.speed_for(1.0) <= a.max_speed()),
    ]
    bad = [name for name, ok in checks if not ok]
    if bad:
        print("[cbtc] SELFTEST FAILED: " + ", ".join(bad), flush=True)
    else:
        print("[cbtc] selftest ok (%d checks)" % len(checks), flush=True)


selftest()
kernel.register("ctd", "cbtc", Cbtc())