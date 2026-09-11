package dev.xyat.entitycontrol.spawn;

import com.mojang.logging.LogUtils;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.command.SpawnCommandExtension;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnConfigGui;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public final class SpawnModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpawnModule(FMLJavaModLoadingContext context) {
        SpawnerConfig.load();
        KTServerConfigApi.registerActionPage(SpawnConfigGui.PAGE_ID);
        BiomeSpawnConfig.loadGlobals();
        SpawnNetwork.register();
        SpawnCommandExtension.install();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpawnConfigGui.load());
    }
}
