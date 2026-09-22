package dev.xyat.entitycontrol.reset;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.config.server.KTServerConfigSpec;
import dev.xyat.kineticcore.api.runtime.KineticModLifecycle;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.entitycontrol.reset.command.EntityReseCommandExtension;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.event.EntityResetHandler;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import org.slf4j.Logger;

public final class ResetModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ResetModule() {
        KineticModLifecycle.onCommonSetup(EntityReseConfig::load);

        KTServerConfigApi.register(KTServerConfigSpec.builder("entitycontrol:entityrese")
                .booleanValue("enable_entity_reset", () -> EntityReseConfig.enableEntityReset, value -> EntityReseConfig.enableEntityReset = value)
                .intValue("check_radius", () -> EntityReseConfig.checkRadius, value -> EntityReseConfig.checkRadius = value, 0, Integer.MAX_VALUE)
                .onSave(EntityReseConfig::save)
                .build());
        EntityReseRuleNetwork.register();
        EntityResetHandler.register();
        EntityReseCommandExtension.install();
        KineticPlatform.runOnClient(() -> EntityReseConfigGui::load);
    }

}
