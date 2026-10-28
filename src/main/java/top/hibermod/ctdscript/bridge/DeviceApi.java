package top.hibermod.ctdscript.bridge;

import java.util.LinkedHashSet;
import java.util.Set;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.device.Devices;

/**
 * {@code ctd.device}：注册地面 / 车载设备类型。
 *
 * <p>已可用（不依赖任何门面，纯注册）。
 */
public final class DeviceApi {

    public static final DeviceApi INSTANCE = new DeviceApi();

    private DeviceApi() {}

    /**
     * 注册一个设备类型。
     *
     * @param namespace    id 命名空间
     * @param path         id 路径
     * @param displayName  显示名
     * @param kind         {@code "ground"} 或 {@code "onboard"}
     * @param capabilities 能力标签数组（如 {@code ["fuel-port"]}）
     * @param commandable  是否接受指令
     * @param signalSystems 该车载设备能操作的信号制度 id 数组（地面设备传空）
     */
    @HostAccess.Export
    public String registerType(String namespace, String path, String displayName, String kind,
                               String[] capabilities, boolean commandable, String[] signalSystems) {
        top.hibermod.ctdscript.ScriptMode.requireStartup("DeviceApi");
        Devices.Kind k = "onboard".equalsIgnoreCase(kind) ? Devices.Kind.ONBOARD : Devices.Kind.GROUND;

        Set<String> caps = new LinkedHashSet<>();
        if (capabilities != null) {
            for (String c : capabilities) caps.add(c);
        }

        Set<ResourceLocation> systems = new LinkedHashSet<>();
        if (signalSystems != null) {
            for (String s : signalSystems) {
                if (s != null && !s.isBlank()) systems.add(ResourceLocation.parse(s));
            }
        }

        Devices.register(new Devices.DeviceType(
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            Component.literal(displayName),
            k,
            caps,
            commandable,
            systems));

        return namespace + ":" + path;
    }
}
