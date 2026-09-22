package dev.xyat.entitycontrol.spawn.command;

import dev.xyat.kineticcore.api.command.KineticCommands;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.entitycontrol.spawn.SpawnModule;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import net.minecraft.commands.CommandSourceStack;

public final class SpawnCommandExtension implements CommandExtension {
    private SpawnCommandExtension() {
    }

    public static void install() {
        KineticCommands.registerExtension(SpawnModule.MODID, new SpawnCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        SpawnerConfig.load();
        BiomeSpawnConfig.loadGlobals();
        BiomeSpawnConfig.loadProfile(BiomeSpawnConfig.globals.current_index);
        BiomeSpawnConfig.applyToGame(source.getServer());
    }
}
