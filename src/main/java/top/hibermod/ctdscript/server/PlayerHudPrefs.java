package top.hibermod.ctdscript.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 「谁打开了车载仪表」——**按玩家**存，跟着存档走。
 *
 * <h2>为什么不放在方块里</h2>
 * 方块属于车，玩家属于自己。把人的偏好写进方块，会变成
 * 「最后点的那个人决定所有人看到什么」，这既不对，也解释不了。
 *
 * <h2>为什么是 SavedData 而不是玩家的 persistentData</h2>
 * 玩家的 persistentData 是随玩家走的（换服务器/换存档会带走），
 * 而「我要不要看仪表」是**这个存档里的偏好**，
 * 跟这个世界的运营规则是一回事。放存档里更合适。
 */
public final class PlayerHudPrefs extends SavedData {

    private static final String DATA_NAME = "ctdscript_hud_prefs";

    private final Set<UUID> enabled = new HashSet<>();

    public static PlayerHudPrefs get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PlayerHudPrefs::new, PlayerHudPrefs::load),
            DATA_NAME);
    }

    public boolean isEnabled(UUID player) {
        return player != null && enabled.contains(player);
    }

    /** 切换并返回新状态。 */
    public boolean toggle(UUID player) {
        if (player == null) return false;
        boolean now;
        if (enabled.contains(player)) {
            enabled.remove(player);
            now = false;
        } else {
            enabled.add(player);
            now = true;
        }
        setDirty();
        return now;
    }

    public void set(UUID player, boolean value) {
        if (player == null) return;
        boolean changed = value ? enabled.add(player) : enabled.remove(player);
        if (changed) setDirty();
    }

    public int size() {
        return enabled.size();
    }

    private static PlayerHudPrefs load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerHudPrefs data = new PlayerHudPrefs();
        ListTag list = tag.getList("enabled", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.enabled.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // 坏掉的条目跳过，不要让整份数据读不出来
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UUID id : enabled) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("enabled", list);
        return tag;
    }
}
