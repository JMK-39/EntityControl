package dev.xyat.entitycontrol.breakspawn.event;

import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.data.PlacedBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BreakSpawnModule.MODID)
public final class BreakSpawnEventHandler {
    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Map<String, ChanceState>> PLAYER_CHANCE_STATES = new HashMap<>();
    private static final Map<UUID, String> LAST_TRACKED_BLOCK = new HashMap<>();

    private BreakSpawnEventHandler() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        BreakSpawnConfig.loadAndClean(event.getServer());
        clearRuntimeState();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearRuntimeState();
    }

    private static void clearRuntimeState() {
        PLAYER_COOLDOWNS.clear();
        PLAYER_CHANCE_STATES.clear();
        LAST_TRACKED_BLOCK.clear();
    }

    public static void resetRuntimeState() {
        clearRuntimeState();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PlacedBlockTracker.get(level).mark(event.getPos().asLong());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        BreakSpawnConfig.ConfigRoot config = BreakSpawnConfig.CURRENT;
        if (config == null || config.global == null || !config.global.enabled || config.blocks.isEmpty()) {
            return;
        }

        ResourceLocation blockIdLocation = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
        if (blockIdLocation == null) {
            return;
        }
        String blockId = blockIdLocation.toString();
        long gameTime = level.getGameTime();

        resetPreviousStackIfNeeded(player.getUUID(), blockId, config, gameTime);

        if (PlacedBlockTracker.get(level).consumeIfPlaced(event.getPos().asLong())) {
            return;
        }

        BreakSpawnConfig.BlockRule blockRule = config.blocks.get(blockId);
        if (blockRule == null || !blockRule.enabled || blockRule.entityWeights.isEmpty()) {
            return;
        }
        LAST_TRACKED_BLOCK.put(player.getUUID(), blockId);

        if (player.isCreative() && !config.global.creativeCanTrigger) {
            return;
        }
        if (!matchesBlockRuleConditions(level, event.getPos(), blockRule)) {
            return;
        }

        long readyAt = PLAYER_COOLDOWNS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (gameTime < readyAt) {
            return;
        }

        ChanceState chanceState = getChanceState(player.getUUID(), blockId);
        if (blockRule.resetAfterTicks > 0
                && chanceState.lastRollTick >= 0L
                && gameTime - chanceState.lastRollTick > blockRule.resetAfterTicks) {
            chanceState.failures = 0;
        }

        double chance = calculateChance(blockRule, chanceState.failures);
        chanceState.lastRollTick = gameTime;
        if (chance <= 0.0D || level.random.nextDouble() >= chance) {
            registerFailure(blockRule, chanceState);
            return;
        }

        BlockPos brokenPos = event.getPos().immutable();
        BlockState brokenState = event.getState();
        int spawned = spawnEncounter(level, brokenPos, brokenState, config, blockRule);
        if (spawned > 0) {
            if (blockRule.resetOnTrigger) {
                chanceState.failures = 0;
            }
            if (config.global.playerCooldownTicks > 0) {
                PLAYER_COOLDOWNS.put(player.getUUID(), gameTime + config.global.playerCooldownTicks);
            }
        } else {
            registerFailure(blockRule, chanceState);
        }
    }

    private static void resetPreviousStackIfNeeded(
            UUID playerId,
            String currentBlockId,
            BreakSpawnConfig.ConfigRoot config,
            long gameTime
    ) {
        String previousBlockId = LAST_TRACKED_BLOCK.get(playerId);
        if (previousBlockId == null || previousBlockId.equals(currentBlockId)) {
            return;
        }
        BreakSpawnConfig.BlockRule previousRule = config.blocks.get(previousBlockId);
        if (previousRule != null && previousRule.resetOnDifferentBlock) {
            ChanceState state = getChanceState(playerId, previousBlockId);
            state.failures = 0;
            state.lastRollTick = gameTime;
        }
        LAST_TRACKED_BLOCK.remove(playerId);
    }

    private static ChanceState getChanceState(UUID playerId, String blockId) {
        return PLAYER_CHANCE_STATES
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(blockId, ignored -> new ChanceState());
    }

    private static void registerFailure(BreakSpawnConfig.BlockRule rule, ChanceState state) {
        if (rule.stackingEnabled && rule.chancePerFailure > 0.0D) {
            state.failures = Math.min(1_000_000, state.failures + 1);
        }
    }

    public static double calculateChance(BreakSpawnConfig.BlockRule rule, int failures) {
        if (rule == null) {
            return 0.0D;
        }
        double base = Math.max(0.0D, Math.min(1.0D, rule.baseChance));
        double max = Math.max(base, Math.min(1.0D, rule.maxChance));
        if (!rule.stackingEnabled || failures <= 0 || rule.chancePerFailure <= 0.0D) {
            return Math.min(base, max);
        }
        double chance = base + Math.max(0, failures) * rule.chancePerFailure;
        return Math.max(0.0D, Math.min(max, chance));
    }

    private static boolean matchesBlockRuleConditions(
            ServerLevel level,
            BlockPos pos,
            BreakSpawnConfig.BlockRule rule
    ) {
        if (pos.getY() < rule.minY || pos.getY() > rule.maxY) {
            return false;
        }
        ResourceLocation dimensionId = level.dimension().location();
        if (!matchesCsv(rule.dimensions, dimensionId)) {
            return false;
        }
        ResourceLocation biomeId = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(level.getBiome(pos).value());
        if (!matchesCsv(rule.biomes, biomeId)) {
            return false;
        }
        int light = level.getMaxLocalRawBrightness(pos);
        return light >= rule.minLight && light <= rule.maxLight;
    }

    private static int spawnEncounter(
            ServerLevel level,
            BlockPos brokenPos,
            BlockState brokenState,
            BreakSpawnConfig.ConfigRoot config,
            BreakSpawnConfig.BlockRule blockRule
    ) {
        RandomSource random = level.random;
        int minCount = Math.max(0, blockRule.minSpawnCount);
        int maxCount = Math.max(minCount, blockRule.maxSpawnCount);
        int targetCount = minCount + (maxCount > minCount ? random.nextInt(maxCount - minCount + 1) : 0);
        if (targetCount <= 0) {
            return 0;
        }

        List<WeightedRule> eligible = collectEligible(level, brokenPos, brokenState, config.entities, blockRule.entityWeights);
        if (eligible.isEmpty()) {
            return 0;
        }

        int spawned = 0;
        for (int index = 0; index < targetCount; index++) {
            WeightedRule weightedRule = chooseWeighted(eligible, random);
            if (weightedRule == null) {
                break;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(weightedRule.id());
            if (type == null) {
                continue;
            }
            BlockPos spawnPos = findSpawnPosition(level, brokenPos, type, weightedRule.rule(), blockRule, random);
            if (spawnPos == null) {
                continue;
            }
            if (spawnOne(level, type, weightedRule.rule(), spawnPos, random)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static List<WeightedRule> collectEligible(
            ServerLevel level,
            BlockPos brokenPos,
            BlockState brokenState,
            Map<String, BreakSpawnConfig.EntityRule> rules,
            Map<String, Integer> poolWeights
    ) {
        List<WeightedRule> result = new ArrayList<>();
        ResourceLocation dimensionId = level.dimension().location();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(brokenState.getBlock());
        ResourceLocation biomeId = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(level.getBiome(brokenPos).value());

        for (Map.Entry<String, Integer> poolEntry : poolWeights.entrySet()) {
            int weight = poolEntry.getValue() == null ? 0 : poolEntry.getValue();
            if (weight <= 0) {
                continue;
            }
            ResourceLocation entityId = ResourceLocation.tryParse(poolEntry.getKey());
            BreakSpawnConfig.EntityRule rule = rules.get(poolEntry.getKey());
            if (entityId == null || rule == null || !rule.enabled) {
                continue;
            }
            if (brokenPos.getY() < rule.minY || brokenPos.getY() > rule.maxY) {
                continue;
            }
            if (!matchesCsv(rule.dimensions, dimensionId)) {
                continue;
            }
            if (!matchesCsv(rule.biomes, biomeId)) {
                continue;
            }
            if (!matchesAllowedBlock(rule.allowedBlocks, blockId)) {
                continue;
            }
            if (matchesBlockedBlock(rule.blockedBlocks, blockId)) {
                continue;
            }
            result.add(new WeightedRule(entityId, rule, weight));
        }
        return result;
    }

    private static boolean matchesCsv(String csv, ResourceLocation current) {
        if (csv == null || csv.isBlank()) {
            return true;
        }
        if (current == null) {
            return false;
        }
        for (String value : BreakSpawnConfig.splitCsv(csv)) {
            if (current.toString().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAllowedBlock(String csv, ResourceLocation current) {
        return csv == null || csv.isBlank() || matchesCsv(csv, current);
    }

    private static boolean matchesBlockedBlock(String csv, ResourceLocation current) {
        if (csv == null || csv.isBlank() || current == null) {
            return false;
        }
        for (String value : BreakSpawnConfig.splitCsv(csv)) {
            if (current.toString().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static WeightedRule chooseWeighted(List<WeightedRule> entries, RandomSource random) {
        long total = 0L;
        for (WeightedRule entry : entries) {
            total += Math.max(0, entry.weight());
        }
        if (total <= 0L) {
            return null;
        }
        long roll = Math.floorMod(random.nextLong(), total);
        for (WeightedRule entry : entries) {
            roll -= Math.max(0, entry.weight());
            if (roll < 0L) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private static BlockPos findSpawnPosition(
            ServerLevel level,
            BlockPos origin,
            EntityType<?> type,
            BreakSpawnConfig.EntityRule rule,
            BreakSpawnConfig.BlockRule blockRule,
            RandomSource random
    ) {
        int attempts = Math.max(1, blockRule.maxSpawnAttempts);
        int radius = Math.max(blockRule.minDistance, blockRule.horizontalRadius);
        int minDistance = Math.max(0, blockRule.minDistance);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int dx = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            int dz = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz < minDistance * minDistance) {
                continue;
            }
            int centerY = origin.getY() + (blockRule.verticalRadius == 0
                    ? 0
                    : random.nextInt(blockRule.verticalRadius * 2 + 1) - blockRule.verticalRadius);
            BlockPos candidate = findModePosition(
                    level,
                    new BlockPos(origin.getX() + dx, centerY, origin.getZ() + dz),
                    type,
                    rule,
                    blockRule.verticalRadius
            );
            if (candidate == null || candidate.getY() < rule.minY || candidate.getY() > rule.maxY) {
                continue;
            }
            int light = level.getMaxLocalRawBrightness(candidate);
            if (light < rule.minLight || light > rule.maxLight) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static BlockPos findModePosition(
            ServerLevel level,
            BlockPos start,
            EntityType<?> type,
            BreakSpawnConfig.EntityRule rule,
            int verticalRadius
    ) {
        String mode = rule.spawnMode == null ? "AUTO" : rule.spawnMode;
        if ("AUTO".equals(mode)) {
            MobCategory category = type.getCategory();
            mode = category == MobCategory.WATER_CREATURE
                    || category == MobCategory.WATER_AMBIENT
                    || category == MobCategory.UNDERGROUND_WATER_CREATURE
                    || category == MobCategory.AXOLOTLS
                    ? "WATER"
                    : "SURFACE";
        }
        if ("ANY".equals(mode) || "AIR".equals(mode)) {
            BlockState state = level.getBlockState(start);
            BlockState above = level.getBlockState(start.above());
            if (state.getCollisionShape(level, start).isEmpty()
                    && above.getCollisionShape(level, start.above()).isEmpty()) {
                return start;
            }
            return null;
        }
        if ("WATER".equals(mode)) {
            int radius = Math.max(1, verticalRadius);
            for (int offset = 0; offset <= radius; offset++) {
                BlockPos down = start.below(offset);
                if (level.getFluidState(down).is(FluidTags.WATER)) {
                    return down;
                }
                BlockPos up = start.above(offset);
                if (level.getFluidState(up).is(FluidTags.WATER)) {
                    return up;
                }
            }
            return null;
        }
        int radius = Math.max(1, verticalRadius);
        for (int y = start.getY() + radius; y >= start.getY() - radius; y--) {
            BlockPos feet = new BlockPos(start.getX(), y, start.getZ());
            BlockPos head = feet.above();
            BlockPos floor = feet.below();
            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(head);
            BlockState floorState = level.getBlockState(floor);
            if (feetState.getCollisionShape(level, feet).isEmpty()
                    && headState.getCollisionShape(level, head).isEmpty()
                    && floorState.isFaceSturdy(level, floor, Direction.UP)) {
                return feet;
            }
        }
        return null;
    }

    private static boolean spawnOne(
            ServerLevel level,
            EntityType<?> type,
            BreakSpawnConfig.EntityRule rule,
            BlockPos pos,
            RandomSource random
    ) {
        Entity created;
        try {
            created = type.create(level);
        } catch (Throwable throwable) {
            return false;
        }
        if (!(created instanceof LivingEntity living)) {
            return false;
        }
        living.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        if (living instanceof Mob mob) {
            try {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null, null);
            } catch (Throwable ignored) {
            }
        }
        applyEntityNbt(living, rule.entityNbt);
        living.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, living.getYRot(), living.getXRot());
        applyAttributes(living, rule.attributes, random);
        applyEquipment(living, rule.equipment);
        applyFlags(living, rule);
        if (!level.noCollision(living, living.getBoundingBox())) {
            return false;
        }
        return level.addFreshEntity(living);
    }

    private static void applyEntityNbt(LivingEntity entity, String rawNbt) {
        if (rawNbt == null || rawNbt.isBlank()) {
            return;
        }
        try {
            CompoundTag tag = TagParser.parseTag(rawNbt);
            tag.remove("UUID");
            tag.remove("Pos");
            tag.remove("Motion");
            tag.remove("Rotation");
            tag.remove("Dimension");
            tag.remove("id");
            tag.remove("Passengers");
            tag.remove("Leash");
            entity.load(tag);
        } catch (Exception ignored) {
        }
    }

    private static void applyAttributes(
            LivingEntity living,
            Map<String, BreakSpawnConfig.AttributeRange> attributes,
            RandomSource random
    ) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        boolean healthChanged = false;
        for (Map.Entry<String, BreakSpawnConfig.AttributeRange> entry : attributes.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            Attribute attribute = id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
            BreakSpawnConfig.AttributeRange range = entry.getValue();
            if (attribute == null || range == null) {
                continue;
            }
            AttributeInstance instance = living.getAttributes().getInstance(attribute);
            if (instance == null) {
                continue;
            }
            double value = randomRange(random, range.min, range.max);
            instance.setBaseValue(value);
            if (attribute == Attributes.MAX_HEALTH) {
                healthChanged = true;
            }
        }
        if (healthChanged) {
            living.setHealth(living.getMaxHealth());
        }
    }

    private static double randomRange(RandomSource random, double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    private static void applyEquipment(LivingEntity living, Map<String, BreakSpawnConfig.EquipmentSpec> equipment) {
        if (equipment == null || equipment.isEmpty()) {
            return;
        }
        for (Map.Entry<String, BreakSpawnConfig.EquipmentSpec> entry : equipment.entrySet()) {
            EquipmentSlot slot = slotByName(entry.getKey());
            BreakSpawnConfig.EquipmentSpec spec = entry.getValue();
            if (slot == null || spec == null || spec.itemId == null || spec.itemId.isBlank()) {
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(spec.itemId);
            Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, spec.count));
            if (spec.nbt != null && !spec.nbt.isBlank()) {
                try {
                    stack.setTag(TagParser.parseTag(spec.nbt));
                } catch (Exception ignored) {
                }
            }
            living.setItemSlot(slot, stack);
            if (living instanceof Mob mob) {
                mob.setDropChance(slot, (float) Math.max(0.0D, Math.min(1.0D, spec.dropChance)));
            }
        }
    }

    private static EquipmentSlot slotByName(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static void applyFlags(LivingEntity living, BreakSpawnConfig.EntityRule rule) {
        living.setSilent(rule.silent);
        living.setGlowingTag(rule.glowing);
        living.setInvulnerable(rule.invulnerable);
        if (rule.customName != null && !rule.customName.isBlank()) {
            living.setCustomName(Component.literal(rule.customName));
            living.setCustomNameVisible(rule.customNameVisible);
        }
        if (living instanceof Mob mob) {
            mob.setNoAi(rule.noAi);
            if (rule.persistent) {
                mob.setPersistenceRequired();
            }
        }
    }

    private static final class ChanceState {
        private int failures;
        private long lastRollTick = -1L;
    }

    private record WeightedRule(ResourceLocation id, BreakSpawnConfig.EntityRule rule, int weight) {
    }
}
