package top.hibermod.ctdscript.broadcast;

/**
 * 一个语音来源。
 *
 * <h2>为什么是接口</h2>
 * 用户要的是**多种语音源**：本地音频文件、TTS、模型推理。
 * 三者实现方式完全不同（读文件 / 起外部进程 / 走网络），
 * 但引擎对它们的要求只有一个：**给我一段文字，想办法出声**。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@link #available()} 必须便宜——每帧都可能被问，不要在里面起进程；</li>
 *   <li>{@link #speak} 失败要**返回 false 而不是抛异常**，让链子能降级；</li>
 *   <li>实现方自己负责线程；引擎不保证在哪个线程调用。</li>
 * </ul>
 */
public interface VoiceSource {

    /** 稳定 id，脚本里用它指定音源（{@code local} / {@code tts} / {@code model}）。 */
    String id();

    /** 现在能不能用。不可用时链子会跳过它。 */
    boolean available();

    /**
     * 播一条。
     *
     * @return 是否成功。失败由链子降级到下一个源，**不要在这里抛异常**。
     */
    boolean speak(BroadcastMessage message);

    /** 人读的说明，用于 {@code /ctd broadcast} 自检。 */
    default String describe() {
        return id() + (available() ? " [ready]" : " [unavailable]");
    }
}
