# CBTC signal head -- two-lamp combination coding
#
# Why combinations: only red/yellow/green exist, but a moving-block line needs to
# convey four or five grades of "how good is the line ahead". The trick is two
# lamps lit at once, so the pair encodes the gradient.
#
# Wayside lamps are a fallback for degraded or non-CBTC trains. In normal CBTC
# operation the driver's authority is on the cab display and the lineside head may
# legitimately stay dark.

from ctd import signal

CLEAR = signal.registerAspect("ctd", "clear", "无限制通行（绿-绿）", 1.00, "PROCEED")
APPROACH_LIMITED = signal.registerAspect("ctd", "approach_limited", "注意限速（绿-黄）", 0.85, "CAUTION")
APPROACH = signal.registerAspect("ctd", "approach", "准备停车（绿-红）", 0.55, "CAUTION")
CAUTION_DIVERGING = signal.registerAspect("ctd", "caution_diverging", "侧向谨慎（黄-黄）", 0.35, "CAUTION")
STOP = signal.registerAspect("ctd", "stop", "停车（红-红）", 0.00, "STOP")
CALL_ON = signal.registerAspect("ctd", "call_on", "引导信号（红-黄）", 0.20, "CALL_ON")
CBTC_MODE = signal.registerAspect("ctd", "cbtc_mode", "CBTC 模式（蓝）", 0.00, "DARK")
SHUNT = signal.registerAspect("ctd", "shunt", "调车（白）", 0.30, "SHUNT")
DARK = signal.registerAspect("ctd", "dark", "灭灯", 0.00, "DARK")

# Two physical lamp positions. Each position can show any colour, which is what
# makes "green over green" distinguishable from "green over yellow".
wayside = signal.type("ctd", "cbtc_two_lamp", "ctd:cbtc")
wayside.lamp(signal.GREEN).lamp(signal.RED)

wayside.expressesColored(CLEAR, "green,green")
wayside.expressesColored(APPROACH_LIMITED, "green,yellow")
wayside.expressesColored(APPROACH, "green,red")
wayside.expressesColored(CAUTION_DIVERGING, "yellow,yellow")
wayside.expressesColored(STOP, "red,red")
wayside.expressesColored(CALL_ON, "red,yellow")
wayside.expressesColored(CBTC_MODE, "blue")
wayside.expresses(DARK)

wayside_id = wayside.register()

cbtc = signal.system("ctd", "cbtc", "CBTC 移动闭塞（双灯组合）")
cbtc.palette(signal.RED, signal.YELLOW, signal.GREEN, signal.BLUE, signal.WHITE)
for a in (CLEAR, APPROACH_LIMITED, APPROACH, CAUTION_DIVERGING,
          STOP, CALL_ON, CBTC_MODE, SHUNT, DARK):
    cbtc.aspect(a)
cbtc.type(wayside)
cbtc.fallback(wayside)

print("[signal] registered %s type=%s" % (cbtc.register(), wayside_id), flush=True)
