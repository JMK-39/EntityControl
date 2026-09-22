package dev.xyat.entitycontrol.spawn.client.jade;

import net.minecraft.ChatFormatting;
import dev.xyat.entitycontrol.spawn.api.SpawnerRuntimeAccessor;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

public class SpawnProvider {
    public static final ResourceLocation ID = KineticResourceIds.of(
            "entitycontrol",
            "spawner_info"
    );

    public enum Server implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!SpawnerConfig.enableSpawnerBreaker) {
                return;
            }
            if (!(accessor.getBlockEntity() instanceof SpawnerBlockEntity blockEntity)) {
                return;
            }
            if (!(blockEntity.getSpawner() instanceof SpawnerRuntimeAccessor runtimeAccessor)) {
                return;
            }

            ResourceLocation entityId = runtimeAccessor.entitycontrol_spawn$getEntityId();
            if (entityId == null) {
                return;
            }

            SpawnerConfig.SpawnerRule rule = SpawnerConfig.getSpawnerRule(entityId.toString());
            if (!rule.tuningEnabled && !rule.breakerEnabled) {
                return;
            }

            data.putBoolean("KT_Active", true);
            data.putBoolean("KT_BreakerEnabled", rule.breakerEnabled);
            data.putBoolean("KT_TuningEnabled", rule.tuningEnabled);

            if (rule.breakerEnabled) {
                data.putInt("KT_WaveCount", runtimeAccessor.entitycontrol_spawn$getSpawnCount());
                data.putLong("KT_CooldownEnd", runtimeAccessor.entitycontrol_spawn$getCooldownEnd());
                data.putInt("KT_Threshold", rule.threshold);
                data.putInt("KT_CooldownSeconds", rule.cooldown);
                data.putString("KT_Mode", rule.mode);
            }

            if (rule.tuningEnabled) {
                data.putDouble("KT_SpeedMultiplier", rule.speedMultiplier);
                data.putDouble("KT_FixedDelaySeconds", rule.fixedSpawnDelaySeconds);

                data.putDouble("KT_MinDelaySeconds", runtimeAccessor.entitycontrol_spawn$getMinDelaySeconds());
                data.putDouble("KT_MaxDelaySeconds", runtimeAccessor.entitycontrol_spawn$getMaxDelaySeconds());
                data.putInt("KT_MinSpawnCount", rule.minSpawnCount);
                data.putInt("KT_MaxSpawnCount", rule.maxSpawnCount);
                data.putInt("KT_MaxNearby", runtimeAccessor.entitycontrol_spawn$getMaxNearbyEntities());
                data.putInt("KT_PlayerRange", runtimeAccessor.entitycontrol_spawn$getRequiredPlayerRange());
                data.putInt("KT_SpawnRange", runtimeAccessor.entitycontrol_spawn$getSpawnRange());

                data.putDouble("KT_BaseMinDelaySeconds", runtimeAccessor.entitycontrol_spawn$getBaseMinDelaySeconds());
                data.putDouble("KT_BaseMaxDelaySeconds", runtimeAccessor.entitycontrol_spawn$getBaseMaxDelaySeconds());
                data.putInt("KT_BaseSpawnCount", runtimeAccessor.entitycontrol_spawn$getBaseSpawnCountPerWave());
                data.putInt("KT_BaseMaxNearby", runtimeAccessor.entitycontrol_spawn$getBaseMaxNearbyEntities());
                data.putInt("KT_BasePlayerRange", runtimeAccessor.entitycontrol_spawn$getBaseRequiredPlayerRange());
                data.putInt("KT_BaseSpawnRange", runtimeAccessor.entitycontrol_spawn$getBaseSpawnRange());
            }
        }

        @Override
        public ResourceLocation getUid() {
            return ID;
        }
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(
                ITooltip tooltip,
                BlockAccessor accessor,
                IPluginConfig config
        ) {
            CompoundTag data = accessor.getServerData();
            if (!data.getBoolean("KT_Active")) {
                return;
            }

            if (data.getBoolean("KT_TuningEnabled")) {
                appendTuningInfo(tooltip, data);
            }

            if (data.getBoolean("KT_BreakerEnabled")) {
                appendBreakerInfo(tooltip, accessor, data);
            }
        }

        private void appendTuningInfo(ITooltip tooltip, CompoundTag data) {
            double minDelay = data.getDouble("KT_MinDelaySeconds");
            double maxDelay = data.getDouble("KT_MaxDelaySeconds");
            double baseMinDelay = data.getDouble("KT_BaseMinDelaySeconds");
            double baseMaxDelay = data.getDouble("KT_BaseMaxDelaySeconds");
            double speed = data.getDouble("KT_SpeedMultiplier");
            double fixedDelay = data.getDouble("KT_FixedDelaySeconds");

            if (different(minDelay, baseMinDelay) || different(maxDelay, baseMaxDelay)) {
                tooltip.add(Component.translatable(
                        "jade.entitycontrol.spawner.tuning.delay",
                        Component.literal(formatDecimal(minDelay)).withStyle(ChatFormatting.AQUA),
                        Component.literal(formatDecimal(maxDelay)).withStyle(ChatFormatting.AQUA)
                ));
            }

            if (fixedDelay >= 0.0D) {
                tooltip.add(Component.translatable(
                        "jade.entitycontrol.spawner.tuning.fixed_delay",
                        Component.literal(formatDecimal(fixedDelay)).withStyle(ChatFormatting.GOLD)
                ));
            }

            if (different(speed, 1.0D)) {
                tooltip.add(Component.translatable(
                        "jade.entitycontrol.spawner.tuning.speed",
                        Component.literal(formatDecimal(speed)).withStyle(ChatFormatting.GREEN)
                ));
            }

            int minSpawnCount = data.getInt("KT_MinSpawnCount");
            int maxSpawnCount = data.getInt("KT_MaxSpawnCount");
            int baseSpawnCount = data.getInt("KT_BaseSpawnCount");
            if (minSpawnCount != baseSpawnCount || maxSpawnCount != baseSpawnCount) {
                if (minSpawnCount == maxSpawnCount) {
                    tooltip.add(Component.translatable(
                            "jade.entitycontrol.spawner.tuning.spawn_count",
                            Component.literal(String.valueOf(minSpawnCount)).withStyle(ChatFormatting.AQUA)
                    ));
                } else {
                    tooltip.add(Component.translatable(
                            "jade.entitycontrol.spawner.tuning.spawn_count_range",
                            Component.literal(String.valueOf(minSpawnCount)).withStyle(ChatFormatting.AQUA),
                            Component.literal(String.valueOf(maxSpawnCount)).withStyle(ChatFormatting.GREEN)
                    ));
                }
            }

            int maxNearby = data.getInt("KT_MaxNearby");
            if (maxNearby != data.getInt("KT_BaseMaxNearby")) {
                tooltip.add(Component.translatable(
                        "jade.entitycontrol.spawner.tuning.max_nearby",
                        Component.literal(String.valueOf(maxNearby)).withStyle(ChatFormatting.LIGHT_PURPLE)
                ));
            }

            int playerRange = data.getInt("KT_PlayerRange");
            if (playerRange != data.getInt("KT_BasePlayerRange")) {
                tooltip.add(Component.translatable(
                        "jade.entitycontrol.spawner.tuning.player_range",
                        Component.literal(String.valueOf(playerRange)).withStyle(ChatFormatting.GREEN)
                ));
            }

            int spawnRange = data.getInt("KT_SpawnRange");
            if (spawnRange != data.getInt("KT_BaseSpawnRange")) {
                tooltip.add(Component.translatable(
                        "jade.entitycontrol.spawner.tuning.spawn_range",
                        Component.literal(String.valueOf(spawnRange)).withStyle(ChatFormatting.AQUA)
                ));
            }
        }

        private void appendBreakerInfo(
                ITooltip tooltip,
                BlockAccessor accessor,
                CompoundTag data
        ) {
            int waveCount = data.getInt("KT_WaveCount");
            int threshold = data.getInt("KT_Threshold");
            long cooldownEnd = data.getLong("KT_CooldownEnd");
            long currentTime = accessor.getLevel().getGameTime();
            String mode = data.getString("KT_Mode");

            tooltip.add(Component.translatable(
                    "BREAK".equalsIgnoreCase(mode)
                            ? "jade.entitycontrol.spawner.mode.break"
                            : "jade.entitycontrol.spawner.mode.cooldown"
            ));

            if (cooldownEnd > currentTime) {
                long secondsLeft = Math.max(0L, (cooldownEnd - currentTime) / 20L);
                tooltip.add(Component.translatable(
                        "jade.entitycontrol.spawner.cooldown",
                        Component.literal(formatTime(secondsLeft)).withStyle(ChatFormatting.LIGHT_PURPLE)
                ));
            }

            tooltip.add(Component.translatable(
                    "jade.entitycontrol.spawner.progress",
                    Component.literal(String.valueOf(waveCount)).withStyle(ChatFormatting.AQUA),
                    Component.literal(String.valueOf(threshold)).withStyle(ChatFormatting.GREEN),
                    Component.literal(String.valueOf(data.getInt("KT_CooldownSeconds"))).withStyle(ChatFormatting.GOLD)
            ));
        }

        private boolean different(double left, double right) {
            return Math.abs(left - right) > 0.0001D;
        }

        private String formatDecimal(double value) {
            if (!Double.isFinite(value)) {
                return "0";
            }
            return java.math.BigDecimal.valueOf(value)
                    .stripTrailingZeros()
                    .toPlainString();
        }

        private String formatTime(long seconds) {
            if (seconds >= 3600L) {
                return String.format(
                        Locale.ROOT,
                        "%d:%02d:%02d",
                        seconds / 3600L,
                        seconds % 3600L / 60L,
                        seconds % 60L
                );
            }
            return String.format(
                    Locale.ROOT,
                    "%02d:%02d",
                    seconds / 60L,
                    seconds % 60L
            );
        }

        @Override
        public ResourceLocation getUid() {
            return ID;
        }
    }
}
