package dev.xyat.entitycontrol.breakspawn;

import com.mojang.logging.LogUtils;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfigGui;
import dev.xyat.entitycontrol.breakspawn.event.BreakSpawnEventHandler;
import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetwork;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import org.slf4j.Logger;

public final class BreakSpawnModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BreakSpawnModule() {
        BreakSpawnConfig.load();
        KTServerConfigApi.registerActionPage(BreakSpawnConfigGui.PAGE_ID);
        BreakSpawnNetwork.register();
        BreakSpawnEventHandler.register();
        KineticPlatform.runOnClient(() -> BreakSpawnConfigGui::load);
    }
}
