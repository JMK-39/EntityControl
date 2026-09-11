package dev.xyat.entitycontrol.reset.command;

import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.entitycontrol.reset.ResetModule;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import net.minecraft.commands.CommandSourceStack;

public final class EntityReseCommandExtension implements KTCommandExtension {
    private EntityReseCommandExtension() {
    }

    public static void install() {
        KTCommandApi.register(ResetModule.MODID, new EntityReseCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        EntityReseConfig.load();
    }
}
