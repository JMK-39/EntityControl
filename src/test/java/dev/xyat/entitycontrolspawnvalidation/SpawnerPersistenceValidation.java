package dev.xyat.entitycontrolspawnvalidation;

import dev.xyat.entitycontrol.spawn.api.SpawnerRuntimeAccessor;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.entitycontrol.util.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;

/** Opt-in round trip through the transformed vanilla spawner, including temporary tuning. */
@net.minecraftforge.fml.common.Mod("entitycontrol_spawner_validation")
public final class SpawnerPersistenceValidation {
    private static final long COOLDOWN = 12_345_678_901L;

    public SpawnerPersistenceValidation() {
        if (Boolean.getBoolean("entitycontrol.spawnerValidation")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::started);
        }
    }

    private void started(net.minecraftforge.event.server.ServerStartedEvent event) {
        var server = event.getServer();
        var tag = new CompoundTag();
        tag.putInt("entitycontrolSpawnCount", 7);
        tag.putLong("entitycontrolCooldownEnd", COOLDOWN);
        tag.putInt("MinSpawnDelay", 200);
        tag.putInt("MaxSpawnDelay", 800);
        tag.putInt("SpawnCount", 4);
        tag.putInt("MaxNearbyEntities", 6);
        tag.putInt("RequiredPlayerRange", 16);
        tag.putInt("SpawnRange", 4);
        var entity = new CompoundTag();
        entity.putString("id", "minecraft:pig");
        var data = new CompoundTag();
        data.put("entity", entity);
        tag.put("SpawnData", data);
        var spawner = newSpawner();
        load(spawner, server, tag);

        var rule = new SpawnerConfig.SpawnerRule();
        rule.tuningEnabled = true;
        rule.minSpawnDelaySeconds = 2;
        rule.maxSpawnDelaySeconds = 3;
        rule.minSpawnCount = rule.maxSpawnCount = 9;
        rule.maxNearbyEntities = 11;
        rule.requiredPlayerRange = 24;
        rule.spawnRange = 7;
        var previous = SpawnerConfig.SPAWNER_RULES_CACHE.put("minecraft:pig", rule);
        boolean enabled = SpawnerConfig.enableSpawnerBreaker;
        try {
            SpawnerConfig.enableSpawnerBreaker = true;
            // The future cooldown prevents vanilla spawning while the normal server tick applies the rule.
            spawner.serverTick(server.overworld(), BlockPos.ZERO);
            requireTuned((SpawnerRuntimeAccessor) spawner);
            var saved = save(spawner, server);
            requirePersistent(spawner, saved);
            requireTuned((SpawnerRuntimeAccessor) spawner);
            if (Nbt.intValue(saved, "MinSpawnDelay") != 200 || Nbt.intValue(saved, "MaxSpawnDelay") != 800
                    || Nbt.intValue(saved, "SpawnCount") != 4 || Nbt.intValue(saved, "MaxNearbyEntities") != 6
                    || Nbt.intValue(saved, "RequiredPlayerRange") != 16 || Nbt.intValue(saved, "SpawnRange") != 4) {
                throw new AssertionError("Save must retain original spawner settings rather than temporary tuning");
            }
            var restored = newSpawner();
            load(restored, server, saved);
            requirePersistent(restored, save(restored, server));
            var accessor = (SpawnerRuntimeAccessor) restored;
            if (accessor.entitycontrol_spawn$getMinDelaySeconds() != 10
                    || accessor.entitycontrol_spawn$getMaxDelaySeconds() != 40
                    || accessor.entitycontrol_spawn$getSpawnCountPerWave() != 4) {
                throw new AssertionError("Reload must restore original settings before tuning is reapplied");
            }
            org.slf4j.LoggerFactory.getLogger(getClass()).info(
                    "ENTITY_SPAWNER_PERSISTENCE_PASS count=7 cooldown={} entity=minecraft:pig tuningRestored=true roundTrip=true", COOLDOWN);
        } finally {
            SpawnerConfig.enableSpawnerBreaker = enabled;
            if (previous == null) SpawnerConfig.SPAWNER_RULES_CACHE.remove("minecraft:pig");
            else SpawnerConfig.SPAWNER_RULES_CACHE.put("minecraft:pig", previous);
        }
    }

    private static BaseSpawner newSpawner() {
        return new BaseSpawner() {
            @Override public void broadcastEvent(Level level, BlockPos pos, int eventId) { }
        };
    }

    private static void requirePersistent(BaseSpawner spawner, CompoundTag saved) {
        var accessor = (SpawnerRuntimeAccessor) spawner;
        if (accessor.entitycontrol_spawn$getSpawnCount() != 7 || accessor.entitycontrol_spawn$getCooldownEnd() != COOLDOWN
                || Nbt.intValue(saved, "entitycontrolSpawnCount") != 7 || longValue(saved, "entitycontrolCooldownEnd") != COOLDOWN
                || !"minecraft:pig".equals(String.valueOf(accessor.entitycontrol_spawn$getEntityId()))) {
            throw new AssertionError("Spawner count, 64-bit cooldown and entity must survive native load/save");
        }
    }

    private static void requireTuned(SpawnerRuntimeAccessor accessor) {
        if (accessor.entitycontrol_spawn$getMinDelaySeconds() != 2 || accessor.entitycontrol_spawn$getMaxDelaySeconds() != 3
                || accessor.entitycontrol_spawn$getSpawnCountPerWave() != 9 || accessor.entitycontrol_spawn$getMaxNearbyEntities() != 11
                || accessor.entitycontrol_spawn$getRequiredPlayerRange() != 24 || accessor.entitycontrol_spawn$getSpawnRange() != 7) {
            throw new AssertionError("Active tuning must be restored after serialization");
        }
    }

    private static void load(BaseSpawner spawner, net.minecraft.server.MinecraftServer server, CompoundTag tag) {
        //? if >=26.1 {
        /*spawner.load(server.overworld(), BlockPos.ZERO, net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess(), tag));
        *///?} else {
        spawner.load(server.overworld(), BlockPos.ZERO, tag);
        //?}
    }

    private static CompoundTag save(BaseSpawner spawner, net.minecraft.server.MinecraftServer server) {
        //? if >=26.1 {
        /*var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess());
        spawner.save(output);
        return output.buildResult();
        *///?} else {
        return spawner.save(new CompoundTag());
        //?}
    }

    private static long longValue(CompoundTag tag, String key) {
        //? if >=26.1 {
        /*return tag.getLongOr(key, 0L);
        *///?} else {
        return tag.getLong(key);
        //?}
    }
}
