package dev.xyat.entitycontrol.spawn.mixin;

import net.minecraft.ChatFormatting;
import dev.xyat.entitycontrol.spawn.api.SpawnerRuntimeAccessor;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Locale;

@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin implements SpawnerRuntimeAccessor {
    @Shadow private int spawnDelay;
    @Shadow private int minSpawnDelay;
    @Shadow private int maxSpawnDelay;
    @Shadow private int spawnCount;
    @Shadow private int maxNearbyEntities;
    @Shadow private int requiredPlayerRange;
    @Shadow private int spawnRange;

    @Unique private int entitycontrol_spawn$spawnWaveCount;
    @Unique private long entitycontrol_spawn$cooldownEnd;
    @Unique private ResourceLocation entitycontrol_spawn$entityId;
    @Unique private int entitycontrol_spawn$previousTickStartDelay;
    @Unique private boolean entitycontrol_spawn$previousTickInitialized;

    @Unique private boolean entitycontrol_spawn$baseSettingsCaptured;
    @Unique private boolean entitycontrol_spawn$tuningApplied;
    @Unique private boolean entitycontrol_spawn$restoreTuningAfterSave;
    @Unique private SpawnerConfig.SpawnerRule entitycontrol_spawn$appliedTuningRule;
    @Unique private int entitycontrol_spawn$baseMinSpawnDelay;
    @Unique private int entitycontrol_spawn$baseMaxSpawnDelay;
    @Unique private int entitycontrol_spawn$baseSpawnCount;
    @Unique private int entitycontrol_spawn$baseMaxNearbyEntities;
    @Unique private int entitycontrol_spawn$baseRequiredPlayerRange;
    @Unique private int entitycontrol_spawn$baseSpawnRange;

    @Unique private static final String NBT_SPAWN_COUNT = "entitycontrolSpawnCount";
    @Unique private static final String NBT_COOLDOWN_END = "entitycontrolCooldownEnd";

    @Override
    public int entitycontrol_spawn$getSpawnCount() {
        return entitycontrol_spawn$spawnWaveCount;
    }

    @Override
    public long entitycontrol_spawn$getCooldownEnd() {
        return entitycontrol_spawn$cooldownEnd;
    }

    @Override
    public ResourceLocation entitycontrol_spawn$getEntityId() {
        return entitycontrol_spawn$entityId;
    }

    @Override
    public double entitycontrol_spawn$getCurrentDelaySeconds() {
        return Math.max(0, spawnDelay) / 20.0D;
    }

    @Override
    public double entitycontrol_spawn$getMinDelaySeconds() {
        return minSpawnDelay / 20.0D;
    }

    @Override
    public double entitycontrol_spawn$getMaxDelaySeconds() {
        return maxSpawnDelay / 20.0D;
    }

    @Override
    public int entitycontrol_spawn$getSpawnCountPerWave() {
        return spawnCount;
    }

    @Override
    public int entitycontrol_spawn$getMaxNearbyEntities() {
        return maxNearbyEntities;
    }

    @Override
    public int entitycontrol_spawn$getRequiredPlayerRange() {
        return requiredPlayerRange;
    }

    @Override
    public int entitycontrol_spawn$getSpawnRange() {
        return spawnRange;
    }

    @Override
    public double entitycontrol_spawn$getBaseMinDelaySeconds() {
        return (entitycontrol_spawn$baseSettingsCaptured
                ? entitycontrol_spawn$baseMinSpawnDelay
                : minSpawnDelay) / 20.0D;
    }

    @Override
    public double entitycontrol_spawn$getBaseMaxDelaySeconds() {
        return (entitycontrol_spawn$baseSettingsCaptured
                ? entitycontrol_spawn$baseMaxSpawnDelay
                : maxSpawnDelay) / 20.0D;
    }

    @Override
    public int entitycontrol_spawn$getBaseSpawnCountPerWave() {
        return entitycontrol_spawn$baseSettingsCaptured
                ? entitycontrol_spawn$baseSpawnCount
                : spawnCount;
    }

    @Override
    public int entitycontrol_spawn$getBaseMaxNearbyEntities() {
        return entitycontrol_spawn$baseSettingsCaptured
                ? entitycontrol_spawn$baseMaxNearbyEntities
                : maxNearbyEntities;
    }

    @Override
    public int entitycontrol_spawn$getBaseRequiredPlayerRange() {
        return entitycontrol_spawn$baseSettingsCaptured
                ? entitycontrol_spawn$baseRequiredPlayerRange
                : requiredPlayerRange;
    }

    @Override
    public int entitycontrol_spawn$getBaseSpawnRange() {
        return entitycontrol_spawn$baseSettingsCaptured
                ? entitycontrol_spawn$baseSpawnRange
                : spawnRange;
    }

    @ModifyVariable(
            method = "load",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private CompoundTag entitycontrol_spawn$readPersistentState(CompoundTag tag) {
        entitycontrol_spawn$spawnWaveCount = tag.contains(NBT_SPAWN_COUNT)
                ? tag.getInt(NBT_SPAWN_COUNT)
                : 0;
        entitycontrol_spawn$cooldownEnd = tag.contains(NBT_COOLDOWN_END)
                ? tag.getLong(NBT_COOLDOWN_END)
                : 0L;
        entitycontrol_spawn$entityId = entitycontrol_spawn$readEntityIdFromSpawnerTag(tag);
        entitycontrol_spawn$baseSettingsCaptured = false;
        entitycontrol_spawn$tuningApplied = false;
        entitycontrol_spawn$restoreTuningAfterSave = false;
        entitycontrol_spawn$appliedTuningRule = null;
        entitycontrol_spawn$previousTickInitialized = false;
        return tag;
    }

    @ModifyVariable(
            method = "setNextSpawnData",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private SpawnData entitycontrol_spawn$captureNextSpawnData(SpawnData spawnData) {
        entitycontrol_spawn$entityId = entitycontrol_spawn$readEntityId(spawnData);
        return spawnData;
    }

    @ModifyVariable(
            method = "save",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private CompoundTag entitycontrol_spawn$prepareSave(CompoundTag tag) {
        entitycontrol_spawn$restoreTuningAfterSave = entitycontrol_spawn$tuningApplied;
        if (entitycontrol_spawn$restoreTuningAfterSave) {
            entitycontrol_spawn$restoreBaseSettings();
        }
        return tag;
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void entitycontrol_spawn$finishSave(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag savedTag = cir.getReturnValue();
        if (savedTag != null) {
            savedTag.putInt(NBT_SPAWN_COUNT, entitycontrol_spawn$spawnWaveCount);
            savedTag.putLong(NBT_COOLDOWN_END, entitycontrol_spawn$cooldownEnd);
        }

        if (entitycontrol_spawn$restoreTuningAfterSave && SpawnerConfig.enableSpawnerBreaker) {
            ResourceLocation entityId = entitycontrol_spawn$getEntityId();
            if (entityId != null) {
                SpawnerConfig.SpawnerRule rule = SpawnerConfig.getSpawnerRule(entityId.toString());
                entitycontrol_spawn$applyTuning(rule);
            }
        }
        entitycontrol_spawn$restoreTuningAfterSave = false;
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void entitycontrol_spawn$serverTickHead(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        int tickStartDelay = spawnDelay;
        boolean completedWave = entitycontrol_spawn$previousTickInitialized
                && entitycontrol_spawn$previousTickStartDelay <= 0
                && tickStartDelay > 0;
        entitycontrol_spawn$previousTickStartDelay = tickStartDelay;
        entitycontrol_spawn$previousTickInitialized = true;

        entitycontrol_spawn$captureBaseSettings();

        if (!SpawnerConfig.enableSpawnerBreaker) {
            entitycontrol_spawn$restoreBaseSettings();
            return;
        }

        ResourceLocation entityId = entitycontrol_spawn$getEntityId();
        if (entityId == null) {
            entitycontrol_spawn$restoreBaseSettings();
            return;
        }

        SpawnerConfig.SpawnerRule rule = SpawnerConfig.getSpawnerRule(entityId.toString());
        entitycontrol_spawn$applyTuning(rule);

        if (completedWave
                && rule.breakerEnabled
                && entitycontrol_spawn$handleCompletedWave(level, pos, entityId, rule)) {
            ci.cancel();
            return;
        }

        if (rule.tuningEnabled && spawnDelay <= 0) {
            spawnCount = entitycontrol_spawn$resolveSpawnCount(level, rule);
        }

        if (!rule.breakerEnabled) {
            return;
        }

        if (entitycontrol_spawn$cooldownEnd > 0L) {
            long currentTime = level.getGameTime();
            if (currentTime < entitycontrol_spawn$cooldownEnd) {
                spawnDelay = 20;
                if (currentTime % 20L == 0L) {
                    level.sendParticles(
                            ParticleTypes.HEART,
                            pos.getX() + 0.5D,
                            pos.getY() + 1.2D,
                            pos.getZ() + 0.5D,
                            1,
                            0.2D,
                            0.2D,
                            0.2D,
                            0.05D
                    );
                }
                ci.cancel();
                return;
            }

            entitycontrol_spawn$cooldownEnd = 0L;
            entitycontrol_spawn$spawnWaveCount = 0;
            if (SpawnerConfig.spawnerBreakerNotification) {
                entitycontrol_spawn$notifyPlayers(
                        level,
                        pos,
                        "msg.entitycontrol.spawn.spawner.cooldown.end",
                        entityId
                );
            }
        }

    }

    @Unique
    private boolean entitycontrol_spawn$handleCompletedWave(
            ServerLevel level,
            BlockPos pos,
            ResourceLocation entityId,
            SpawnerConfig.SpawnerRule rule
    ) {
        entitycontrol_spawn$spawnWaveCount++;
        if (entitycontrol_spawn$spawnWaveCount < rule.threshold) {
            return false;
        }

        if ("BREAK".equalsIgnoreCase(rule.mode)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.levelEvent(
                    2001,
                    pos,
                    net.minecraft.world.level.block.Block.getId(Blocks.SPAWNER.defaultBlockState())
            );
            if (SpawnerConfig.spawnerBreakerNotification) {
                entitycontrol_spawn$notifyPlayers(
                        level,
                        pos,
                        "msg.entitycontrol.spawn.spawner.broken",
                        entityId
                );
            }
            return true;
        }

        long cooldownTicks = (long) rule.cooldown * 20L;
        entitycontrol_spawn$cooldownEnd = level.getGameTime() + cooldownTicks;
        if (SpawnerConfig.spawnerBreakerNotification) {
            entitycontrol_spawn$notifyPlayers(
                    level,
                    pos,
                    "msg.entitycontrol.spawn.spawner.cooldown.start",
                    entityId,
                    entitycontrol_spawn$formatTime(rule.cooldown)
            );
        }
        return false;
    }

    @Unique
    private ResourceLocation entitycontrol_spawn$readEntityIdFromSpawnerTag(CompoundTag tag) {
        CompoundTag spawnDataTag = tag.getCompound(BaseSpawner.SPAWN_DATA_TAG);
        CompoundTag entityTag = spawnDataTag.contains("entity")
                ? spawnDataTag.getCompound("entity")
                : spawnDataTag;
        return entitycontrol_spawn$parseEntityId(entityTag);
    }

    @Unique
    private ResourceLocation entitycontrol_spawn$readEntityId(SpawnData spawnData) {
        if (spawnData == null) {
            return null;
        }
        return entitycontrol_spawn$parseEntityId(spawnData.getEntityToSpawn());
    }

    @Unique
    private ResourceLocation entitycontrol_spawn$parseEntityId(CompoundTag entityTag) {
        if (entityTag == null || !entityTag.contains("id")) {
            return null;
        }

        ResourceLocation id = KineticResourceIds.tryParse(entityTag.getString("id"));
        if (id == null || !KineticRegistries.entityTypes().contains(id)) {
            return null;
        }
        return id;
    }

    @Unique
    private void entitycontrol_spawn$captureBaseSettings() {
        if (entitycontrol_spawn$baseSettingsCaptured) {
            return;
        }
        entitycontrol_spawn$baseMinSpawnDelay = minSpawnDelay;
        entitycontrol_spawn$baseMaxSpawnDelay = maxSpawnDelay;
        entitycontrol_spawn$baseSpawnCount = spawnCount;
        entitycontrol_spawn$baseMaxNearbyEntities = maxNearbyEntities;
        entitycontrol_spawn$baseRequiredPlayerRange = requiredPlayerRange;
        entitycontrol_spawn$baseSpawnRange = spawnRange;
        entitycontrol_spawn$baseSettingsCaptured = true;
    }

    @Unique
    private void entitycontrol_spawn$applyTuning(SpawnerConfig.SpawnerRule rule) {
        if (rule == null || !rule.tuningEnabled) {
            entitycontrol_spawn$restoreBaseSettings();
            return;
        }
        if (entitycontrol_spawn$tuningApplied && entitycontrol_spawn$appliedTuningRule == rule) {
            return;
        }

        double configuredMinSeconds = rule.fixedSpawnDelaySeconds >= 0.0D
                ? rule.fixedSpawnDelaySeconds
                : rule.minSpawnDelaySeconds;
        double configuredMaxSeconds = rule.fixedSpawnDelaySeconds >= 0.0D
                ? rule.fixedSpawnDelaySeconds
                : rule.maxSpawnDelaySeconds;

        int effectiveMin = entitycontrol_spawn$toVanillaDelay(
                configuredMinSeconds,
                rule.speedMultiplier
        );
        int effectiveMax = entitycontrol_spawn$toVanillaDelay(
                configuredMaxSeconds,
                rule.speedMultiplier
        );
        if (effectiveMax < effectiveMin) {
            effectiveMax = effectiveMin;
        }

        minSpawnDelay = effectiveMin;
        maxSpawnDelay = effectiveMax;
        spawnCount = rule.minSpawnCount;
        maxNearbyEntities = rule.maxNearbyEntities < 0
                ? entitycontrol_spawn$baseMaxNearbyEntities
                : rule.maxNearbyEntities;
        requiredPlayerRange = rule.requiredPlayerRange;
        spawnRange = rule.spawnRange;

        if (spawnDelay > effectiveMax) {
            spawnDelay = effectiveMax;
        }
        entitycontrol_spawn$tuningApplied = true;
        entitycontrol_spawn$appliedTuningRule = rule;
    }

    @Unique
    private void entitycontrol_spawn$restoreBaseSettings() {
        if (!entitycontrol_spawn$baseSettingsCaptured || !entitycontrol_spawn$tuningApplied) {
            return;
        }
        minSpawnDelay = entitycontrol_spawn$baseMinSpawnDelay;
        maxSpawnDelay = entitycontrol_spawn$baseMaxSpawnDelay;
        spawnCount = entitycontrol_spawn$baseSpawnCount;
        maxNearbyEntities = entitycontrol_spawn$baseMaxNearbyEntities;
        requiredPlayerRange = entitycontrol_spawn$baseRequiredPlayerRange;
        spawnRange = entitycontrol_spawn$baseSpawnRange;
        entitycontrol_spawn$tuningApplied = false;
        entitycontrol_spawn$appliedTuningRule = null;
    }

    @Unique
    private int entitycontrol_spawn$resolveSpawnCount(
            ServerLevel level,
            SpawnerConfig.SpawnerRule rule
    ) {
        int minimum = Math.max(0, Math.min(128, rule.minSpawnCount));
        int maximum = Math.max(minimum, Math.min(128, rule.maxSpawnCount));
        if (maximum == minimum) {
            return minimum;
        }
        return minimum + level.random.nextInt(maximum - minimum + 1);
    }

    @Unique
    private int entitycontrol_spawn$toVanillaDelay(
            double seconds,
            double speedMultiplier
    ) {
        double safeSeconds = Math.max(0.05D, seconds);
        double safeMultiplier = Math.max(0.01D, speedMultiplier);
        double vanillaDelay = safeSeconds * 20.0D / safeMultiplier;
        return Math.max(
                1,
                Math.min(
                        32767,
                        (int) Math.round(vanillaDelay)
                )
        );
    }

    @Unique
    private Component entitycontrol_spawn$formatTime(int totalSeconds) {
        if (totalSeconds >= 3600) {
            String hours = String.format(Locale.ROOT, "%.1f", totalSeconds / 3600.0D);
            return Component.translatable(
                    "msg.entitycontrol.spawn.spawner.hours",
                    Component.literal(hours).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY);
        }
        if (totalSeconds >= 60) {
            return Component.translatable(
                    "msg.entitycontrol.spawn.spawner.minutes",
                    Component.literal(String.valueOf(totalSeconds / 60)).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable(
                "msg.entitycontrol.spawn.spawner.seconds",
                Component.literal(String.valueOf(totalSeconds)).withStyle(ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.GRAY);
    }

    @Unique
    private void entitycontrol_spawn$notifyPlayers(
            ServerLevel level,
            BlockPos pos,
            String langKey,
            ResourceLocation entityId,
            Object... extraArgs
    ) {
        AABB searchBox = new AABB(pos).inflate(16.0D);
        List<Player> players = level.getEntitiesOfClass(Player.class, searchBox);
        if (players.isEmpty()) {
            return;
        }

        Component entityName = Component.literal(entityId.toString()).withStyle(ChatFormatting.GOLD);
        var type = net.minecraft.world.entity.EntityType.byString(entityId.toString());
        if (type.isPresent()) {
            entityName = type.get().getDescription().copy().withStyle(ChatFormatting.GOLD);
        }

        Object[] args = new Object[4 + (extraArgs == null ? 0 : extraArgs.length)];
        args[0] = entityName;
        args[1] = Component.literal(String.valueOf(pos.getX())).withStyle(ChatFormatting.AQUA);
        args[2] = Component.literal(String.valueOf(pos.getY())).withStyle(ChatFormatting.AQUA);
        args[3] = Component.literal(String.valueOf(pos.getZ())).withStyle(ChatFormatting.AQUA);
        if (extraArgs != null) {
            for (int i = 0; i < extraArgs.length; i++) {
                Object value = extraArgs[i];
                args[4 + i] = value instanceof Component
                        ? value
                        : Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
            }
        }

        ChatFormatting messageColor = langKey.endsWith(".broken")
                ? ChatFormatting.RED
                : (langKey.endsWith(".cooldown.end") ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        Component message = Component.translatable(langKey, args).withStyle(messageColor);
        for (Player player : players) {
            player.displayClientMessage(message, true);
        }
    }
}
