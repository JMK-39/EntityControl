package dev.xyat.entitycontrol.spawn.event;

import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.Set;

public final class GlobalSpawnControlHandler {
    private static final Set<MobSpawnType> NATURAL_TYPES = EnumSet.of(
            MobSpawnType.NATURAL,
            MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.STRUCTURE,
            MobSpawnType.PATROL
    );
    private static boolean registered;

    private GlobalSpawnControlHandler() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        KineticServerEvents.onAboutToStart(KineticEventPriority.LOWEST, BiomeSpawnConfig::onServerStart);
        KineticWorldEvents.onMobSpawnPlacementCheck(
                KineticEventPriority.LOWEST,
                GlobalSpawnControlHandler::onSpawnPlacementCheck
        );
        KineticWorldEvents.onMobFinalizeSpawn(
                KineticEventPriority.HIGHEST,
                GlobalSpawnControlHandler::onFinalizeSpawn
        );
    }

    private static void onSpawnPlacementCheck(KineticWorldEvents.MobSpawnPlacementContext event) {
        if (!BiomeSpawnConfig.globals.enable_rule_override) return;

        ServerLevel level = event.level();
        EntityType<?> entityType = event.entityType();
        MobSpawnType spawnType = event.spawnType();
        BlockPos pos = event.pos();

        BiomeSpawnConfig.SpawnDecision decision = BiomeSpawnConfig.getSpawnDecision(
                entityType,
                spawnType,
                level.dimension().location(),
                pos.getY()
        );

        if (decision == BiomeSpawnConfig.SpawnDecision.VANILLA) return;

        if (decision == BiomeSpawnConfig.SpawnDecision.DENY) {
            event.result(KineticWorldEvents.SpawnPlacementResult.DENY);
            return;
        }

        if (NATURAL_TYPES.contains(spawnType)
                && !BiomeSpawnConfig.isSpawnLightAllowed(level, entityType, pos, spawnType)) {
            event.result(KineticWorldEvents.SpawnPlacementResult.DENY);
            return;
        }

        if (NATURAL_TYPES.contains(spawnType)
                && spawnType != MobSpawnType.NATURAL
                && BiomeSpawnConfig.hasCustomSpawnDistance(entityType)
                && isDistanceDisallowed(level, pos, entityType)) {
            event.result(KineticWorldEvents.SpawnPlacementResult.DENY);
            return;
        }

        if (event.result() != KineticWorldEvents.SpawnPlacementResult.DENY) {
            event.result(KineticWorldEvents.SpawnPlacementResult.ALLOW);
        }
    }

    private static void onFinalizeSpawn(KineticWorldEvents.MobFinalizeSpawnContext event) {
        if (!BiomeSpawnConfig.globals.enable_rule_override) return;

        ServerLevel level = event.serverLevel();
        BiomeSpawnConfig.SpawnDecision decision = BiomeSpawnConfig.getSpawnDecision(
                event.entity().getType(),
                event.spawnType(),
                level.dimension().location(),
                event.entity().blockPosition().getY()
        );

        if (decision == BiomeSpawnConfig.SpawnDecision.DENY) {
            event.cancelSpawn();
            return;
        }

        if (NATURAL_TYPES.contains(event.spawnType())) {
            BlockPos pos = event.entity().blockPosition();
            EntityType<?> entityType = event.entity().getType();

            if (!BiomeSpawnConfig.isSpawnLightAllowed(level, entityType, pos, event.spawnType())) {
                event.cancelSpawn();
                return;
            }

            if (BiomeSpawnConfig.hasCustomSpawnDistance(entityType)
                    && isDistanceDisallowed(level, pos, entityType)) {
                event.cancelSpawn();
            }
        }
    }

    private static boolean isDistanceDisallowed(ServerLevel level, BlockPos pos, EntityType<?> entityType) {
        Player nearestPlayer = level.getNearestPlayer(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                -1.0D,
                false
        );
        if (nearestPlayer == null) return false;

        double squaredDistance = nearestPlayer.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
        return !BiomeSpawnConfig.isSpawnDistanceAllowed(entityType, squaredDistance);
    }
}
