package dev.xyat.entitycontrol.breakspawn.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class BreakSpawnConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = KineticPaths.configDirectory().resolve("kineticcore").resolve("break_spawn.json");
    private static final int MAX_NBT_LENGTH = 131072;

    public static volatile ConfigRoot CURRENT = new ConfigRoot();

    private BreakSpawnConfig() {
    }

    public static final class ConfigRoot {
        public GlobalSettings global = new GlobalSettings();
        public Map<String, BlockRule> blocks = new TreeMap<>();
        public Map<String, EntityRule> entities = new TreeMap<>();
    }

    public static ConfigRoot copyForEdit(ConfigRoot source) {
        if (source == null) return new ConfigRoot();
        ConfigRoot copy = GSON.fromJson(GSON.toJson(source), ConfigRoot.class);
        return copy == null ? new ConfigRoot() : normalizeClientStructure(copy);
    }

    public static void restoreFromEditCopy(ConfigRoot target, ConfigRoot source) {
        if (target == null || source == null) return;
        ConfigRoot copy = copyForEdit(source);
        target.global = copy.global;
        target.blocks.clear();
        target.blocks.putAll(copy.blocks);
        target.entities.clear();
        target.entities.putAll(copy.entities);
    }

    public static final class GlobalSettings {
        public boolean enabled = true;
        public boolean creativeCanTrigger = false;
        public int playerCooldownTicks = 0;
        public double triggerChance = 0.05D;
        public int minSpawnCount = 1;
        public int maxSpawnCount = 2;
        public int minDistance = 1;
        public int horizontalRadius = 5;
        public int verticalRadius = 3;
        public int maxSpawnAttempts = 16;
    }

    public static final class BlockRule {
        public boolean enabled = true;
        public double baseChance = 0.05D;
        public boolean stackingEnabled = false;
        public double chancePerFailure = 0.01D;
        public double maxChance = 1.0D;
        public boolean resetOnTrigger = true;
        public boolean resetOnDifferentBlock = false;
        public int resetAfterTicks = 0;
        public int minSpawnCount = 1;
        public int maxSpawnCount = 2;
        public int minDistance = 1;
        public int horizontalRadius = 5;
        public int verticalRadius = 3;
        public int maxSpawnAttempts = 16;
        public int minY = -64;
        public int maxY = 320;
        public int minLight = 0;
        public int maxLight = 15;
        public String dimensions = "";
        public String biomes = "";
        public Map<String, Integer> entityWeights = new TreeMap<>();
    }

    public static final class AttributeRange {
        public double min = 1.0D;
        public double max = 1.0D;
    }

    public static final class EquipmentSpec {
        public String itemId = "";
        public String nbt = "";
        public int count = 1;
        public double dropChance = 0.0D;
    }

    public static final class EntityRule {
        public boolean enabled = true;
        public int weight = 100;
        public int minY = -64;
        public int maxY = 320;
        public String spawnMode = "AUTO";
        public String dimensions = "";
        public String biomes = "";
        public String allowedBlocks = "";
        public String blockedBlocks = "";
        public int minLight = 0;
        public int maxLight = 15;
        public String customName = "";
        public boolean customNameVisible = false;
        public boolean persistent = true;
        public boolean silent = false;
        public boolean glowing = false;
        public boolean noAi = false;
        public boolean invulnerable = false;
        public String entityNbt = "";
        public Map<String, AttributeRange> attributes = new TreeMap<>();
        public Map<String, EquipmentSpec> equipment = new TreeMap<>();
    }

    public static BlockRule createBlockRuleFromDefaults(GlobalSettings global) {
        BlockRule rule = new BlockRule();
        if (global != null) {
            rule.baseChance = clamp(global.triggerChance, 0.0D, 1.0D);
            rule.minSpawnCount = Math.max(0, global.minSpawnCount);
            rule.maxSpawnCount = Math.max(rule.minSpawnCount, global.maxSpawnCount);
            rule.minDistance = Math.max(0, global.minDistance);
            rule.horizontalRadius = Math.max(rule.minDistance, global.horizontalRadius);
            rule.verticalRadius = Math.max(0, global.verticalRadius);
            rule.maxSpawnAttempts = Math.max(1, global.maxSpawnAttempts);
        }
        return rule;
    }

    public static void load() {
        ConfigRoot loaded = readConfigFile();
        if (loaded != null) {
            CURRENT = normalizeClientStructure(loaded);
        }
    }

    public static boolean loadAndClean(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        ConfigRoot loaded = readConfigFile();
        if (loaded == null) {
            return false;
        }
        ConfigRoot validated = validateForServer(loaded, server);
        if (validated == null) {
            return false;
        }
        if (!Files.exists(CONFIG_PATH) && validated.entities.isEmpty() && validated.blocks.isEmpty()) {
            CURRENT = validated;
            return true;
        }
        if (!save(validated)) {
            return false;
        }
        CURRENT = validated;
        return true;
    }

    public static ConfigRoot parseConfigJson(String json) {
        if (json == null) {
            return null;
        }
        try (Reader reader = new StringReader(json)) {
            ConfigRoot parsed = GSON.fromJson(reader, ConfigRoot.class);
            return parsed == null ? new ConfigRoot() : normalizeClientStructure(parsed);
        } catch (Exception e) {
            BreakSpawnModule.LOGGER.error("Failed to parse break spawn config JSON", e);
            return null;
        }
    }

    private static ConfigRoot readConfigFile() {
        if (!Files.exists(CONFIG_PATH)) {
            return new ConfigRoot();
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ConfigRoot parsed = GSON.fromJson(reader, ConfigRoot.class);
            return parsed == null ? new ConfigRoot() : parsed;
        } catch (Exception e) {
            BreakSpawnModule.LOGGER.error("Failed to load break_spawn.json", e);
            return null;
        }
    }

    public static ConfigRoot normalizeClientStructure(ConfigRoot source) {
        ConfigRoot target = new ConfigRoot();
        if (source == null) {
            return target;
        }
        target.global = source.global == null ? new GlobalSettings() : source.global;

        if (source.entities != null) {
            for (Map.Entry<String, EntityRule> entry : source.entities.entrySet()) {
                String id = normalizeResourceId(entry.getKey());
                EntityRule rule = normalizeEntityRule(entry.getValue());
                if (id != null && rule != null) {
                    target.entities.put(id, rule);
                }
            }
        }

        if (source.blocks != null) {
            for (Map.Entry<String, BlockRule> entry : source.blocks.entrySet()) {
                String id = normalizeResourceId(entry.getKey());
                BlockRule rule = normalizeBlockRule(entry.getValue());
                if (id != null && rule != null) {
                    target.blocks.put(id, rule);
                }
            }
        }
        return target;
    }

    private static BlockRule normalizeBlockRule(BlockRule source) {
        if (source == null) {
            return null;
        }
        source.dimensions = normalizeResourceList(source.dimensions, false);
        source.biomes = normalizeResourceList(source.biomes, false);
        if (source.entityWeights == null) {
            source.entityWeights = new TreeMap<>();
        }
        Map<String, Integer> weights = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entityWeights.entrySet()) {
            String entityId = normalizeResourceId(entry.getKey());
            if (entityId != null && entry.getValue() != null) {
                weights.put(entityId, entry.getValue());
            }
        }
        source.entityWeights = weights;
        return source;
    }

    private static EntityRule normalizeEntityRule(EntityRule source) {
        if (source == null) {
            return null;
        }
        source.spawnMode = normalizeSpawnMode(source.spawnMode);
        source.dimensions = normalizeResourceList(source.dimensions, false);
        source.biomes = normalizeResourceList(source.biomes, false);
        source.allowedBlocks = normalizeResourceList(source.allowedBlocks, false);
        source.blockedBlocks = normalizeResourceList(source.blockedBlocks, false);
        source.customName = source.customName == null ? "" : source.customName;
        source.entityNbt = source.entityNbt == null ? "" : source.entityNbt.trim();
        if (source.attributes == null) {
            source.attributes = new TreeMap<>();
        }
        if (source.equipment == null) {
            source.equipment = new TreeMap<>();
        }
        Map<String, AttributeRange> normalizedAttributes = new TreeMap<>();
        for (Map.Entry<String, AttributeRange> entry : source.attributes.entrySet()) {
            String id = normalizeResourceId(entry.getKey());
            if (id != null && entry.getValue() != null) {
                normalizedAttributes.put(id, entry.getValue());
            }
        }
        source.attributes = normalizedAttributes;
        Map<String, EquipmentSpec> normalizedEquipment = new TreeMap<>();
        for (Map.Entry<String, EquipmentSpec> entry : source.equipment.entrySet()) {
            String slot = normalizeSlot(entry.getKey());
            EquipmentSpec spec = entry.getValue();
            if (slot != null && spec != null) {
                spec.itemId = spec.itemId == null ? "" : spec.itemId.trim();
                spec.nbt = spec.nbt == null ? "" : spec.nbt.trim();
                normalizedEquipment.put(slot, spec);
            }
        }
        source.equipment = normalizedEquipment;
        return source;
    }

    public static ConfigRoot validateForServer(ConfigRoot source, MinecraftServer server) {
        if (source == null || server == null) {
            return null;
        }
        ConfigRoot normalized = normalizeClientStructure(source);
        if (!validateGlobal(normalized.global)) {
            return null;
        }
        ConfigRoot target = new ConfigRoot();
        target.global = normalized.global;
        ServerLevel validationLevel = server.overworld();

        for (Map.Entry<String, EntityRule> entry : normalized.entities.entrySet()) {
            ResourceLocation entityId = KineticResourceIds.tryParse(entry.getKey());
            if (entityId == null || !KineticRegistries.entityTypes().contains(entityId)) {
                return null;
            }
            Entity created;
            try {
                var type = KineticRegistries.entityTypes().get(entityId);
                created = type == null ? null : type.create(validationLevel);
            } catch (Throwable throwable) {
                return null;
            }
            if (!(created instanceof LivingEntity)) {
                return null;
            }
            EntityRule rule = validateEntityRule(entry.getValue());
            if (rule == null) {
                return null;
            }
            target.entities.put(entityId.toString(), rule);
        }

        for (Map.Entry<String, BlockRule> entry : normalized.blocks.entrySet()) {
            ResourceLocation blockId = KineticResourceIds.tryParse(entry.getKey());
            if (blockId == null || !KineticRegistries.blocks().contains(blockId)) {
                return null;
            }
            BlockRule rule = validateBlockRule(entry.getValue(), target.entities);
            if (rule == null) {
                return null;
            }
            target.blocks.put(blockId.toString(), rule);
        }
        return target;
    }

    private static boolean validateGlobal(GlobalSettings global) {
        return global != null
                && Double.isFinite(global.triggerChance)
                && global.triggerChance >= 0.0D
                && global.triggerChance <= 1.0D
                && global.minSpawnCount >= 0
                && global.maxSpawnCount >= global.minSpawnCount
                && global.maxSpawnCount <= 256
                && global.minDistance >= 0
                && global.horizontalRadius >= global.minDistance
                && global.horizontalRadius <= 128
                && global.verticalRadius >= 0
                && global.verticalRadius <= 64
                && global.maxSpawnAttempts >= 1
                && global.maxSpawnAttempts <= 256
                && global.playerCooldownTicks >= 0
                && global.playerCooldownTicks <= 72000;
    }

    private static BlockRule validateBlockRule(BlockRule source, Map<String, EntityRule> entities) {
        BlockRule rule = normalizeBlockRule(source);
        if (rule == null
                || invalidChance(rule.baseChance)
                || invalidChance(rule.chancePerFailure)
                || invalidChance(rule.maxChance)
                || rule.maxChance < rule.baseChance
                || rule.resetAfterTicks < 0
                || rule.resetAfterTicks > 7_200_000
                || rule.minSpawnCount < 0
                || rule.maxSpawnCount < rule.minSpawnCount
                || rule.maxSpawnCount > 256
                || rule.minDistance < 0
                || rule.horizontalRadius < rule.minDistance
                || rule.horizontalRadius > 128
                || rule.verticalRadius < 0
                || rule.verticalRadius > 64
                || rule.maxSpawnAttempts < 1
                || rule.maxSpawnAttempts > 256
                || rule.minY < -2048
                || rule.maxY < rule.minY
                || rule.maxY > 4096
                || rule.minLight < 0
                || rule.maxLight < rule.minLight
                || rule.maxLight > 15
                || hasInvalidRegistryList(rule.dimensions, RegistryKind.DIMENSION)
                || hasInvalidRegistryList(rule.biomes, RegistryKind.BIOME)) {
            return null;
        }
        Map<String, Integer> validWeights = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : rule.entityWeights.entrySet()) {
            ResourceLocation entityId = KineticResourceIds.tryParse(entry.getKey());
            Integer weight = entry.getValue();
            if (entityId == null || weight == null || weight < 0 || weight > 1_000_000
                    || !entities.containsKey(entityId.toString())) {
                return null;
            }
            validWeights.put(entityId.toString(), weight);
        }
        rule.entityWeights = validWeights;
        return rule;
    }

    private static boolean invalidChance(double value) {
        return !Double.isFinite(value) || value < 0.0D || value > 1.0D;
    }

    private static EntityRule validateEntityRule(EntityRule source) {
        EntityRule rule = normalizeEntityRule(source);
        if (rule == null
                || rule.weight < 0
                || rule.weight > 1_000_000
                || rule.minY < -2048
                || rule.maxY < rule.minY
                || rule.maxY > 4096
                || rule.minLight < 0
                || rule.maxLight < rule.minLight
                || rule.maxLight > 15
                || rule.entityNbt.length() > MAX_NBT_LENGTH) {
            return null;
        }
        if (hasInvalidRegistryList(rule.dimensions, RegistryKind.DIMENSION)
                || hasInvalidRegistryList(rule.biomes, RegistryKind.BIOME)
                || hasInvalidRegistryList(rule.allowedBlocks, RegistryKind.BLOCK)
                || hasInvalidRegistryList(rule.blockedBlocks, RegistryKind.BLOCK)) {
            return null;
        }
        if (!rule.entityNbt.isEmpty()) {
            try {
                TagParser.parseTag(rule.entityNbt);
            } catch (Exception e) {
                return null;
            }
        }
        Map<String, AttributeRange> validAttributes = new TreeMap<>();
        for (Map.Entry<String, AttributeRange> entry : rule.attributes.entrySet()) {
            ResourceLocation id = KineticResourceIds.tryParse(entry.getKey());
            AttributeRange range = entry.getValue();
            if (id == null || !KineticRegistries.attributes().contains(id) || range == null
                    || !Double.isFinite(range.min) || !Double.isFinite(range.max)
                    || range.max < range.min) {
                return null;
            }
            validAttributes.put(id.toString(), range);
        }
        rule.attributes = validAttributes;
        Map<String, EquipmentSpec> validEquipment = new TreeMap<>();
        for (Map.Entry<String, EquipmentSpec> entry : rule.equipment.entrySet()) {
            String slot = normalizeSlot(entry.getKey());
            EquipmentSpec spec = entry.getValue();
            if (slot == null || spec == null) {
                return null;
            }
            if (spec.itemId.isBlank()) {
                continue;
            }
            ResourceLocation itemId = KineticResourceIds.tryParse(spec.itemId);
            Item item = itemId == null ? null : KineticRegistries.items().get(itemId);
            if (itemId == null || item == null
                    || spec.count < 1 || spec.count > 64
                    || !Double.isFinite(spec.dropChance)
                    || spec.dropChance < 0.0D || spec.dropChance > 1.0D
                    || spec.nbt.length() > MAX_NBT_LENGTH) {
                return null;
            }
            if (!spec.nbt.isEmpty()) {
                try {
                    TagParser.parseTag(spec.nbt);
                } catch (Exception e) {
                    return null;
                }
            }
            spec.itemId = itemId.toString();
            validEquipment.put(slot, spec);
        }
        rule.equipment = validEquipment;
        return rule;
    }

    private enum RegistryKind {
        DIMENSION,
        BIOME,
        BLOCK
    }

    private static boolean hasInvalidRegistryList(String csv, RegistryKind kind) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        for (String raw : csv.split(",")) {
            ResourceLocation id = KineticResourceIds.tryParse(raw.trim());
            if (id == null) {
                return true;
            }
            if (kind == RegistryKind.BLOCK && !KineticRegistries.blocks().contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeResourceList(String raw, boolean keepInvalid) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Set<String> values = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String normalized = normalizeResourceId(part);
            if (normalized != null) {
                values.add(normalized);
            } else if (keepInvalid && !part.isBlank()) {
                values.add(part.trim());
            }
        }
        return String.join(",", values);
    }

    private static String normalizeResourceId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = KineticResourceIds.tryParse(raw.trim());
        return id == null ? null : id.toString();
    }

    private static String normalizeSpawnMode(String raw) {
        if (raw == null) {
            return "AUTO";
        }
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "SURFACE", "WATER", "AIR", "ANY" -> value;
            default -> "AUTO";
        };
    }

    private static String normalizeSlot(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "head", "chest", "legs", "feet", "mainhand", "offhand" -> raw.trim().toLowerCase();
            default -> null;
        };
    }

    public static boolean save(ConfigRoot config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Path tempPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config == null ? new ConfigRoot() : config, writer);
            }
            try {
                Files.move(tempPath, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempPath, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            BreakSpawnModule.LOGGER.error("Failed to save break_spawn.json", e);
            return false;
        }
    }

    public static List<String> splitCsv(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String value : csv.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
