package top.hibermod.ctdscript.bridge;

import java.util.UUID;

/**
 * 内核下发的移动授权的脚本侧入口。
 *
 * <p>真正的存储是 {@link top.hibermod.ctdapi.status.Authority}——
 * 宿主也要读它（推给客户端做仪表），所以事实放在契约层，这里只做转发。
 * 保留这个类是为了让脚本侧的名字保持稳定（脚本里叫「内核授权」）。
 */
public final class KernelAuthority {

    private KernelAuthority() {}

    public static void put(UUID trainId, double authority) {
        top.hibermod.ctdapi.status.Authority.put(trainId, authority, -1);
    }

    public static void put(UUID trainId, double authority, double speedCap) {
        top.hibermod.ctdapi.status.Authority.put(trainId, authority, speedCap);
    }

    public static double peek(UUID trainId) {
        return top.hibermod.ctdapi.status.Authority.distance(trainId);
    }

    public static double peekCap(UUID trainId) {
        return top.hibermod.ctdapi.status.Authority.speedCap(trainId);
    }

    public static void clear(UUID trainId) {
        top.hibermod.ctdapi.status.Authority.clear(trainId);
    }

    public static void clearAll() {
        top.hibermod.ctdapi.status.Authority.clearAll();
    }

    public static int size() {
        return top.hibermod.ctdapi.status.Authority.size();
    }
}
