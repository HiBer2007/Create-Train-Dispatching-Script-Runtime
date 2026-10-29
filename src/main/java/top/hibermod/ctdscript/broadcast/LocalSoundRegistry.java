package top.hibermod.ctdscript.broadcast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 把脚本提供的音频登记成 {@code SoundEvent}。
 *
 * <p>注册之后客户端才能按 id 播放；{@code sounds.json} 由 {@code ScriptPack} 合成。
 * 这两步缺一不可——少了注册，id 不存在；少了 {@code sounds.json}，
 * 客户端不知道 id 对应哪个文件，**而且不会报错**，只是没声。
 */
public final class LocalSoundRegistry {

    private static final Map<net.minecraft.resources.ResourceLocation, SoundEvent> EVENTS =
        new ConcurrentHashMap<>();

    private LocalSoundRegistry() {}

    public static void attach(IEventBus modBus) {
        modBus.addListener(LocalSoundRegistry::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.SOUND_EVENT)) return;

        for (var id : LocalSounds.ids()) {
            event.register(Registries.SOUND_EVENT, id, () -> {
                SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
                EVENTS.put(id, sound);
                return sound;
            });
        }
    }

    public static SoundEvent get(net.minecraft.resources.ResourceLocation id) {
        return id == null ? null : EVENTS.get(id);
    }

    /** 所有人都会听到的那一档音量；广播不是环境音，别太小。 */
    public static SoundSource source() {
        return SoundSource.NEUTRAL;
    }
}
