package top.hibermod.ctdscript.client;

/**
 * 客户端保存「我要不要看车载仪表」。
 *
 * <p>这份状态由服务端下发（{@code HudPrefPayload}），客户端**不自己改**：
 * 谁有权限开、开了之后别的玩家看到什么，都由服务端统一决定。
 */
public final class ClientHudPrefs {

    private static volatile boolean enabled;

    private ClientHudPrefs() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void set(boolean value) {
        if (enabled == value) return;
        enabled = value;
        System.out.println("[ctdscript] onboard indicator " + (value ? "ON" : "OFF"));
    }
}
