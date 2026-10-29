# CTD test line: CBTC moving block, dispatched per train by its onboard equipment
#
# Three behaviours, chosen per train from what is actually bolted to it:
#
#   CBTC ATP present   -> moving block. Movement authority plus a speed cap from
#                         the braking curve. Wayside heads for its section stay
#                         dark: the cab carries the authority.
#   No ATP (degraded)  -> fixed block. We do not command that train; the wayside
#                         heads light so its driver has something to obey.
#   Loses the device   -> next frame it is simply in the second group.
#                         Degradation is the absence of a device, not a special path.

from ctd import kernel

SAFETY_MARGIN = 8.0
MIN_MA = 2.0
LOG_EVERY = 100
BIG_MA = 10000.0

ATP = "cbtc_atp"

ASPECT_CLEAR = "ctd:clear"
ASPECT_STOP = "ctd:stop"
ASPECT_DARK = "ctd:dark"


def movement_authority(me, ahead):
    if ahead is None:
        return BIG_MA
    gap = ahead.odometer() - me.odometer()
    gap -= (ahead.length() + me.length()) / 2.0
    return max(0.0, gap - SAFETY_MARGIN)


class Cbtc:
    def __init__(self):
        self._frame = 0

    def _drive_signals(self, ctx, out, trains, is_cbtc):
        """Wayside heads exist for the trains that cannot see the cab.

        Dark is the normal state on a moving-block line. A section held by a
        degraded train lights red; the rest of the line lights clear so that train
        has a continuous indication to follow.
        """
        try:
            occupied_by_cbtc = set()
            occupied_by_other = set()
            for t in trains:
                edge = t.leading_edge()
                if not edge:
                    continue
                if is_cbtc.get(t.id(), False):
                    occupied_by_cbtc.add(edge)
                else:
                    occupied_by_other.add(edge)

            degraded_running = bool(occupied_by_other)

            for sig in ctx.signals():
                if len(sig) < 4:
                    continue
                signal_id, side, current, edge = sig[0], sig[1], sig[2], sig[3]
                if not edge:
                    continue

                if edge in occupied_by_other:
                    want = ASPECT_STOP
                elif edge in occupied_by_cbtc:
                    want = ASPECT_DARK
                elif degraded_running:
                    want = ASPECT_CLEAR
                else:
                    want = ASPECT_DARK

                if current != want:
                    out.aspect(signal_id, side, want)
        except Exception as e:
            print("[cbtc] signal update failed: %s" % e, flush=True)

    def on_frame(self, ctx, out):
        trains = ctx.trains()
        self._frame += 1
        if not trains:
            return

        is_cbtc = {}
        for t in trains:
            try:
                devices = ctx.devices_of(t.id())
            except Exception:
                devices = []
            is_cbtc[t.id()] = ATP in devices

        ordered = sorted(trains, key=lambda t: t.odometer())
        summary = []
        driven = 0

        for i, train in enumerate(ordered):
            if train.manually_driven():
                continue
            if not is_cbtc.get(train.id(), False):
                summary.append("%s DEGRADED" % (train.name() or train.id()[:6]))
                continue

            ahead = None
            for other in ordered[i + 1:]:
                if other.odometer() > train.odometer():
                    ahead = other
                    break

            ma = movement_authority(train, ahead)
            cap = min(train.max_speed(), train.speed_for(ma)) if ma > MIN_MA else 0.0

            out.proceed(train.id(), ma, cap)
            driven += 1
            summary.append("%s ma=%.0f cap=%.2f" % (train.name() or train.id()[:6], ma, cap))

        self._drive_signals(ctx, out, ordered, is_cbtc)

        if self._frame % LOG_EVERY == 0 and summary:
            print("[cbtc] driven=%d " % driven + " | ".join(summary), flush=True)


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
        ("curve monotonic", a.speed_for(0.5) < a.speed_for(1.0) <= a.max_speed()),
    ]
    bad = [name for name, ok in checks if not ok]
    if bad:
        print("[cbtc] SELFTEST FAILED: " + ", ".join(bad), flush=True)
    else:
        print("[cbtc] selftest ok (%d checks)" % len(checks), flush=True)


selftest()
kernel.register("ctd", "cbtc", Cbtc())
