package dev.xyat.entitycontrol.spawn;

import com.mojang.logging.LogUtils;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.command.SpawnCommandExtension;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnConfigGui;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.entitycontrol.spawn.event.GlobalSpawnControlHandler;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import org.slf4j.Logger;

public final class SpawnModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpawnModule() {
        SpawnerConfig.load();
        KTServerConfigApi.registerActionPage(SpawnConfigGui.PAGE_ID);
        BiomeSpawnConfig.loadGlobals();
        SpawnNetwork.register();
        GlobalSpawnControlHandler.register();
        SpawnCommandExtension.install();

        KineticPlatform.runOnClient(() -> SpawnConfigGui::load);
    }
}
