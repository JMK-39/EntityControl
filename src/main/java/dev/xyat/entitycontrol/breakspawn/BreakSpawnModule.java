package dev.xyat.entitycontrol.breakspawn;

import com.mojang.logging.LogUtils;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfigGui;
import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetwork;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

public final class BreakSpawnModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BreakSpawnModule() {
        BreakSpawnConfig.load();
        KTServerConfigApi.registerActionPage(BreakSpawnConfigGui.PAGE_ID);
        BreakSpawnNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BreakSpawnConfigGui.load());
    }
}
