package dev.xyat.entitycontrol.spawn.event;

import dev.xyat.entitycontrol.spawn.SpawnModule;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = SpawnModule.MODID)
public class GlobalSpawnControlHandler {

    private static final Set<MobSpawnType> NATURAL_TYPES = EnumSet.of(
            MobSpawnType.NATURAL,
            MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.STRUCTURE,
            MobSpawnType.PATROL
    );

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        BiomeSpawnConfig.onServerStart(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!BiomeSpawnConfig.globals.enable_rule_override) return;

        ServerLevel level = event.getLevel().getLevel();
        EntityType<?> entityType = event.getEntityType();
        MobSpawnType spawnType = event.getSpawnType();
        BlockPos pos = event.getPos();

        BiomeSpawnConfig.SpawnDecision decision = BiomeSpawnConfig.getSpawnDecision(
                entityType,
                spawnType,
                level.dimension().location(),
                pos.getY()
        );

        if (decision == BiomeSpawnConfig.SpawnDecision.VANILLA) return;

        if (decision == BiomeSpawnConfig.SpawnDecision.DENY) {
            event.setResult(Event.Result.DENY);
            return;
        }

        if (NATURAL_TYPES.contains(spawnType)
                && !BiomeSpawnConfig.isSpawnLightAllowed(
                level,
                entityType,
                pos,
                spawnType
        )) {
            event.setResult(Event.Result.DENY);
            return;
        }

        if (NATURAL_TYPES.contains(spawnType)
                && spawnType != MobSpawnType.NATURAL
                && BiomeSpawnConfig.hasCustomSpawnDistance(entityType)
                && !isDistanceAllowed(level, pos, entityType)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        if (event.getResult() != Event.Result.DENY) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!BiomeSpawnConfig.globals.enable_rule_override) return;

        ServerLevel level = event.getLevel().getLevel();
        BiomeSpawnConfig.SpawnDecision decision = BiomeSpawnConfig.getSpawnDecision(
                event.getEntity().getType(),
                event.getSpawnType(),
                level.dimension().location(),
                event.getEntity().blockPosition().getY()
        );

        if (decision == BiomeSpawnConfig.SpawnDecision.DENY) {
            event.setSpawnCancelled(true);
            return;
        }

        if (NATURAL_TYPES.contains(event.getSpawnType())) {
            BlockPos pos = event.getEntity().blockPosition();
            EntityType<?> entityType = event.getEntity().getType();

            if (!BiomeSpawnConfig.isSpawnLightAllowed(
                    level,
                    entityType,
                    pos,
                    event.getSpawnType()
            )) {
                event.setSpawnCancelled(true);
                return;
            }

            if (BiomeSpawnConfig.hasCustomSpawnDistance(entityType)
                    && !isDistanceAllowed(level, pos, entityType)) {
                event.setSpawnCancelled(true);
            }
        }
    }

    private static boolean isDistanceAllowed(
            ServerLevel level,
            BlockPos pos,
            EntityType<?> entityType
    ) {
        Player nearestPlayer = level.getNearestPlayer(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                -1.0D,
                false
        );
        if (nearestPlayer == null) return true;

        double squaredDistance = nearestPlayer.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
        return BiomeSpawnConfig.isSpawnDistanceAllowed(entityType, squaredDistance);
    }
}
