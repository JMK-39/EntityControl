package dev.xyat.entitycontrol.reset;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.config.server.KTServerConfigSpec;
import dev.xyat.entitycontrol.reset.command.EntityReseCommandExtension;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public final class ResetModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ResetModule() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        KTServerConfigApi.register(KTServerConfigSpec.builder("entitycontrol:entityrese")
                .booleanValue("enable_entity_reset", () -> EntityReseConfig.enableEntityReset, value -> EntityReseConfig.enableEntityReset = value)
                .intValue("check_radius", () -> EntityReseConfig.checkRadius, value -> EntityReseConfig.checkRadius = value, 0, Integer.MAX_VALUE)
                .onSave(EntityReseConfig::save)
                .build());
        EntityReseRuleNetwork.register();
        EntityReseCommandExtension.install();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EntityReseConfigGui.load());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(EntityReseConfig::load);
    }
}
