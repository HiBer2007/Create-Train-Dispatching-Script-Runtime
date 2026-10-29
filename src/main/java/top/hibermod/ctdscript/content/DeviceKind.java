package top.hibermod.ctdscript.content;

/**
 * 车载设备型号：车上装了哪种，就只激活对应的能力。
 *
 * <h2>为什么能力由设备决定，而不是全局开关</h2>
 * 一套信号系统能不能用，取决于**这列车装没装对应的车载设备**。
 * 没装 CBTC 车载设备的车，不该出现 CBTC 的仪表，也不该指望地面信号为它算移动授权——
 * 现实中它会退回固定闭塞，靠轨旁信号行车。
 *
 * <p>同一列车可以装多种设备：速度表负责速度，ATP 负责信号预告与交路。
 * 它们的 HUD 各自独立、可以同时显示，互不覆盖。
 */
public enum DeviceKind {

    /** 车辆仪表：只提供速度。任何车都能装，与信号制度无关。 */
    SPEEDOMETER("speedometer", null),

    /** CBTC 车载 ATP：提供移动授权、允许速度、信号预告、交路指示。 */
    CBTC_ATP("cbtc_atp", "ctd:cbtc");

    private final String id;
    private final String signalSystem;

    DeviceKind(String id, String signalSystem) {
        this.id = id;
        this.signalSystem = signalSystem;
    }

    /** 稳定 id，写进方块实体 NBT 与网络包。**不要改**，改了存档里的设备会失效。 */
    public String id() {
        return id;
    }

    /** 这个设备让列车具备哪套信号系统；{@code null} 表示不参与信号判定。 */
    public String signalSystem() {
        return signalSystem;
    }

    public static DeviceKind byId(String id) {
        if (id == null) return null;
        for (DeviceKind k : values()) {
            if (k.id.equalsIgnoreCase(id)) return k;
        }
        return null;
    }

    /** 不认识的 id 落到速度表：至少不会让车「什么设备都没有」。 */
    public static DeviceKind byIdOrSpeedometer(String id) {
        DeviceKind k = byId(id);
        return k == null ? SPEEDOMETER : k;
    }
}
