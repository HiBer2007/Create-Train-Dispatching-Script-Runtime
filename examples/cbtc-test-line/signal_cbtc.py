# CBTC 使用的信号机 —— 复用 Create 已有的信号方块，只是给它一套「CBTC 制度」。
#
# 三显示：红（停车）/ 黄（注意）/ 绿（允许）。
# 没有新方块：玩家放的就是 Create 的信号机，我们提供语义与机型。
#
# 这个文件属于**启动期定义**，热重载不会生效（注册表已经关了）。

from ctd import signal

# 机型：三灯，红灯在第 0 槽、黄第 1、绿第 2
wayside = signal.type("ctd", "cbtc_wayside", "ctd:cbtc")
wayside.lamp(signal.RED).lamp(signal.YELLOW).lamp(signal.GREEN)
wayside.expresses(signal.STOP, 0)
wayside.expresses(signal.CAUTION, 1)
wayside.expresses(signal.PROCEED, 2)
wayside_id = wayside.register()

# 制度：包含三种语义，默认落到上面那个机型
cbtc = signal.system("ctd", "cbtc", "CBTC 移动闭塞")
cbtc.palette(signal.RED, signal.YELLOW, signal.GREEN)
cbtc.aspect(signal.STOP).aspect(signal.CAUTION).aspect(signal.PROCEED)
cbtc.type(wayside)
cbtc.fallback(wayside)

print("[signal] registered %s type=%s" % (cbtc.register(), wayside_id), flush=True)
