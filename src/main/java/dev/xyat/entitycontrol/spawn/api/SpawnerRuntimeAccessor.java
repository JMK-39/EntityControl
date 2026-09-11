package dev.xyat.entitycontrol.spawn.api;

import net.minecraft.resources.ResourceLocation;

public interface SpawnerRuntimeAccessor {

    ResourceLocation entitycontrol_spawn$getEntityId();

    int entitycontrol_spawn$getSpawnCount();

    long entitycontrol_spawn$getCooldownEnd();

    double entitycontrol_spawn$getCurrentDelaySeconds();

    double entitycontrol_spawn$getMinDelaySeconds();

    double entitycontrol_spawn$getMaxDelaySeconds();

    int entitycontrol_spawn$getSpawnCountPerWave();

    int entitycontrol_spawn$getMaxNearbyEntities();

    int entitycontrol_spawn$getRequiredPlayerRange();

    int entitycontrol_spawn$getSpawnRange();

    double entitycontrol_spawn$getBaseMinDelaySeconds();

    double entitycontrol_spawn$getBaseMaxDelaySeconds();

    int entitycontrol_spawn$getBaseSpawnCountPerWave();

    int entitycontrol_spawn$getBaseMaxNearbyEntities();

    int entitycontrol_spawn$getBaseRequiredPlayerRange();

    int entitycontrol_spawn$getBaseSpawnRange();
}
