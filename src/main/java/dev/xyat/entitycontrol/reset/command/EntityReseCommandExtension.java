package dev.xyat.entitycontrol.reset.command;

import dev.xyat.kineticcore.api.command.KineticCommands;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.entitycontrol.reset.ResetModule;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import net.minecraft.commands.CommandSourceStack;

public final class EntityReseCommandExtension implements CommandExtension {
    private EntityReseCommandExtension() {
    }

    public static void install() {
        KineticCommands.registerExtension(ResetModule.MODID, new EntityReseCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        EntityReseConfig.load();
    }
}
