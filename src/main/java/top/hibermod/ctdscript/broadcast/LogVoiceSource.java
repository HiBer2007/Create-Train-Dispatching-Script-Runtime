package top.hibermod.ctdscript.broadcast;

/**
 * 兜底语音源：把广播写进日志。
 *
 * <h2>为什么需要一个「不出声」的源</h2>
 * 三种真正的语音源都可能同时不可用（TTS 没配、模型没加载、本地音频没装）。
 * 那时候有两个选择：安静，或者记录下来。
 *
 * <p>选后者。广播没响是**最容易漏报**的故障——玩家不会因为「没听到」去填 bug。
 * 日志里留下完整文案，至少事后能查「当时该说什么」。
 *
 * <p>它排在链尾，永远可用，但也在 {@link #status()} 里被标成 {@code log}，
 * 运维一眼能看出「现在只是记录，没真的出声」。
 */
public final class LogVoiceSource implements VoiceSource {

    @Override
    public String id() {
        return "log";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean speak(BroadcastMessage message) {
        System.out.println("[ctdscript] (voice:log) " + message.summary());
        return true;
    }

    @Override
    public String describe() {
        return "log [fallback]";
    }
}
