package dev.xyat.entitycontrol.spawn.mixin;

import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Unique
    private static final ThreadLocal<Boolean> entitycontrol_spawn$IS_EXTRA_SPAWN =
            ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "isValidSpawnPostitionForType",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void entitycontrol_spawn$rejectControlledSpawnEarly(
            ServerLevel level,
            MobCategory category,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            MobSpawnSettings.SpawnerData spawnerData,
            BlockPos.MutableBlockPos pos,
            double squaredDistance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (structureManager == null || chunkGenerator == null || spawnerData == null || spawnerData.type.getCategory() != category) {
            return;
        }

        if (BiomeSpawnConfig.shouldBlockNaturalSpawnEarly(
                level,
                spawnerData.type,
                pos,
                squaredDistance
        )) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "spawnCategoryForChunk",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void entitycontrol_spawn$modifySpawnRates(
            MobCategory category,
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback,
            CallbackInfo ci
    ) {
        if (chunk == null || predicate == null || callback == null) {
            return;
        }
        if (!BiomeSpawnConfig.globals.enable_biome_override) {
            return;
        }
        if (entitycontrol_spawn$IS_EXTRA_SPAWN.get()) {
            return;
        }

        double rate = BiomeSpawnConfig.getCategorySpawnRate(category);
        if (rate < 1.0D && level.random.nextDouble() > rate) {
            ci.cancel();
        }
    }

    @Inject(
            method = "spawnCategoryForChunk",
            at = @At("TAIL")
    )
    private static void entitycontrol_spawn$extraSpawns(
            MobCategory category,
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback,
            CallbackInfo ci
    ) {
        if (ci.isCancelled()) {
            return;
        }
        if (!BiomeSpawnConfig.globals.enable_biome_override) {
            return;
        }
        if (entitycontrol_spawn$IS_EXTRA_SPAWN.get()) {
            return;
        }

        double rate = BiomeSpawnConfig.getCategorySpawnRate(category);
        if (rate <= 1.01D) {
            return;
        }

        double extraAttempts = rate - 1.0D;
        int fullExtras = (int) extraAttempts;
        double fractional = extraAttempts - fullExtras;

        entitycontrol_spawn$IS_EXTRA_SPAWN.set(true);
        try {
            for (int i = 0; i < fullExtras; i++) {
                NaturalSpawner.spawnCategoryForChunk(
                        category,
                        level,
                        chunk,
                        predicate,
                        callback
                );
            }

            if (level.random.nextDouble() < fractional) {
                NaturalSpawner.spawnCategoryForChunk(
                        category,
                        level,
                        chunk,
                        predicate,
                        callback
                );
            }
        } finally {
            entitycontrol_spawn$IS_EXTRA_SPAWN.set(false);
        }
    }
}
