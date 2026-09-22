package dev.xyat.entitycontrol.dummy;

import com.mojang.logging.LogUtils;
import dev.xyat.entitycontrol.dummy.DummyInit;
import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.command.DummyCommandExtension;
import dev.xyat.entitycontrol.dummy.config.DummyConfig;
import dev.xyat.entitycontrol.dummy.event.GlobalDamageHandler;
import dev.xyat.entitycontrol.dummy.client.DummyModuleClientBootstrap;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.config.server.KTServerConfigSpec;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import org.slf4j.Logger;

import java.util.ArrayList;

public final class DummyModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DummyModule() {
        DummyConfig.register();
        KTServerConfigApi.register(KTServerConfigSpec.builder("entitycontrol:server")
                .stringList("equipment_blacklist",
                        () -> new ArrayList<>(DummyConfig.equipmentBlacklist.get()),
                        value -> DummyConfig.equipmentBlacklist.set(new ArrayList<>(value)))
                .intValue("standby_range", DummyConfig.dummyStandbyRange::get, DummyConfig.dummyStandbyRange::set, 0, 64)
                .intValue("broadcast_range", DummyConfig.dummyBroadcastRange::get, DummyConfig.dummyBroadcastRange::set, 0, 256)
                .doubleValue("standby_check_interval",
                        () -> DummyConfig.dummyStandbyCheckIntervalTicks.get() / 20.0D,
                        value -> DummyConfig.dummyStandbyCheckIntervalTicks.set(Math.max(1, Math.min(200, (int) Math.round(value * 20.0D)))),
                        1.0D / 20.0D, 10.0D)
                .doubleValue("sync_interval",
                        () -> DummyConfig.dummySyncIntervalTicks.get() / 20.0D,
                        value -> DummyConfig.dummySyncIntervalTicks.set(Math.max(1, Math.min(20, (int) Math.round(value * 20.0D)))),
                        1.0D / 20.0D, 1.0D)
                .intValue("curio_extra_slots", DummyConfig.dummyCurioExtraSlots::get, DummyConfig.dummyCurioExtraSlots::set, 0, 53)
                .onSave(DummyConfig.SPEC::save)
                .build());
        DummyInit.register();
        DummyNetwork.register();
        GlobalDamageHandler.register();
        DummyCommandExtension.install();

        KineticPlatform.runOnClient(() -> DummyModuleClientBootstrap::register);
    }
}
