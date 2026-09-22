package dev.xyat.entitycontrol.modifier;

import com.mojang.logging.LogUtils;
import dev.xyat.entitycontrol.modifier.command.ModifierCommandExtension;
import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierGuiCache;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.config.ModifierConfigGui;
import dev.xyat.entitycontrol.modifier.event.ModifierEventHandler;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetworkClient;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import org.slf4j.Logger;

public final class ModifierModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModifierModule() {
        ModifierEventHandler.register();
        EntityModifierConfig.load();
        KTServerConfigApi.registerActionPage("entitycontrol:modifier");
        EntityModifierNetwork.register();
        ModifierCommandExtension.install();

        KineticPlatform.runOnClient(() -> () -> {
            ModifierConfigGui.load();
            EntityModifierGuiCache.register();
            EntityModifierNetworkClient.register();
        });
    }
}
