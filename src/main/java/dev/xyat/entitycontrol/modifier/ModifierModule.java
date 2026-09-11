package dev.xyat.entitycontrol.modifier;

import com.mojang.logging.LogUtils;
import dev.xyat.entitycontrol.modifier.command.ModifierCommandExtension;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.config.ModifierConfigGui;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

public final class ModifierModule {
    public static final String MODID = "entitycontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModifierModule() {
        EntityModifierConfig.load();
        KTServerConfigApi.registerActionPage("entitycontrol:modifier");
        EntityModifierNetwork.register();
        ModifierCommandExtension.install();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModifierConfigGui.load());
    }
}
