package dev.xyat.entitycontrol.spawn.mixin;

import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(MobSpawnSettings.class)
public interface MobSpawnSettingsMixin extends BiomeSpawnConfig.IMobSpawnSettingsAccess {
    @Override
    @Accessor("spawners")
    Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> entitycontrol_spawn$getSpawners();

    @Override
    @Mutable
    @Accessor("spawners")
    void entitycontrol_spawn$setSpawners(Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawners);
}
