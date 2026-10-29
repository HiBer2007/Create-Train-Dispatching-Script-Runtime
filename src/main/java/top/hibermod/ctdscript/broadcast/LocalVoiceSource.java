package top.hibermod.ctdscript.broadcast;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;

/**
 * 本地音频语音源：放脚本自己带的录音。
 *
 * <h2>为什么这是三种源里最先做的</h2>
 * TTS 与模型都依赖外部进程，本地音频不依赖任何东西——
 * 它是唯一能**立刻自证「真的出声了」**的一档，也是另外两档全部失败时的底。
 *
 * <h2>找不到对应录音怎么办</h2>
 * 返回 {@code false}，让链子降级到下一个源。
 * **绝不**拿一条不相干的音频顶替：那是「放错广播」，比没声音更糟。
 */
public final class LocalVoiceSource implements VoiceSource {

    @Override
    public String id() {
        return "local";
    }

    @Override
    public boolean available() {
        return LocalSounds.size() > 0;
    }

    @Override
    public boolean speak(BroadcastMessage message) {
        var id = LocalSounds.findFor(message.event());
        if (id == null) return false;

        SoundEvent sound = LocalSoundRegistry.get(id);
        if (sound == null) return false;

        MinecraftServer server = top.hibermod.ctdscript.CtdScript.server();
        if (server == null) return false;

        var level = server.overworld();
        if (level == null) return false;

        // 广播站台式播放：对所有在线玩家各放一次，位置取玩家自己
        // （这样无论人在哪都听得到；不是 3D 声源，不需要精确坐标）
        for (var player : server.getPlayerList().getPlayers()) {
            level.playSound(null, player.blockPosition(), sound,
                LocalSoundRegistry.source(), 1.0f, 1.0f);
        }

        int listeners = server.getPlayerList().getPlayerCount();
        // 「放了」和「有人听到」是两件事：没人在线时也要说清楚，
        // 否则运维会以为音频坏了
        BroadcastLog.write("(voice:local) " + id + " listeners=" + listeners
            + " -> " + message.summary());
        return true;
    }

    @Override
    public String describe() {
        return "local [" + LocalSounds.size() + " clips]";
    }

    static boolean soundExists(net.minecraft.resources.ResourceLocation id) {
        return BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }
}
