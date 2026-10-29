package top.hibermod.ctdscript.broadcast;

/**
 * 一条待播的广播。
 *
 * @param event    触发事件
 * @param scope    {@code train} 或 {@code platform}
 * @param train    车次/车辆编号，可为空
 * @param station  车站名，可为空
 * @param platform 站台号，可为空
 * @param delaySeconds 晚点秒数（负值表示早点）
 * @param text     已经渲染好的文案（占位符已替换）
 */
public record BroadcastMessage(
    BroadcastEvent event,
    String scope,
    String train,
    String station,
    String platform,
    int delaySeconds,
    String text
) {
    public BroadcastMessage {
        if (event == null) throw new IllegalArgumentException("event is required");
        if (text == null) text = "";
        if (scope == null || scope.isBlank()) scope = "train";
    }

    /** 给日志与自检用的单行摘要。 */
    public String summary() {
        return event.key() + "(" + scope + ")"
            + (train.isEmpty() ? "" : " " + train)
            + (station.isEmpty() ? "" : " @" + station)
            + (platform.isEmpty() ? "" : " p" + platform)
            + (delaySeconds == 0 ? "" : " " + delaySeconds + "s")
            + ": " + text;
    }
}
