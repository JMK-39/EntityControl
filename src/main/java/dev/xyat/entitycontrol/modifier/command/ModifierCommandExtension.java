package dev.xyat.entitycontrol.modifier.command;

import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.commands.CommandSourceStack;

public final class ModifierCommandExtension implements KTCommandExtension {
    private ModifierCommandExtension() {
    }

    public static void install() {
        KTCommandApi.register(ModifierModule.MODID, new ModifierCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        EntityModifierConfig.loadAndClean(source.getServer());
    }
}
