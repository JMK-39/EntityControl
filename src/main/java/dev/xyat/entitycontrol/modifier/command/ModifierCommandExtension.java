package dev.xyat.entitycontrol.modifier.command;

import dev.xyat.kineticcore.api.command.KineticCommands;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.commands.CommandSourceStack;

public final class ModifierCommandExtension implements CommandExtension {
    private ModifierCommandExtension() {
    }

    public static void install() {
        KineticCommands.registerExtension(ModifierModule.MODID, new ModifierCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        EntityModifierConfig.loadAndClean(source.getServer());
    }
}
