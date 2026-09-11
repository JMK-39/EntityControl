package dev.xyat.entitycontrol.modifier.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.entitycontrol.modifier.ModifierModule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class EntityModifierConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("kineticcore").resolve("entity_modifier.json");

    public static Map<String, EntityEditData> ENTITY_DATA = new TreeMap<>();

    public static class PotionBuff {
        public double chance = 1.0;
        public int minLevel = 1;
        public int maxLevel = 1;
        public String dimensions = "";
    }

    public static class EntityEditData {
        public Map<String, Double> attributes = new TreeMap<>();
        public Map<String, PotionBuff> buffs = new TreeMap<>();
    }

    public static void load() {
        Map<String, EntityEditData> loaded = readConfigFile();
        if (loaded != null) {
            ENTITY_DATA = loaded;
        }
    }

    public static boolean loadAndClean(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        Map<String, EntityEditData> loaded = readConfigFile();
        if (loaded == null) {
            return false;
        }

        Map<String, EntityEditData> validated = validateForServer(loaded, server);
        if (!Files.exists(CONFIG_PATH) && validated.isEmpty()) {
            ENTITY_DATA = validated;
            return true;
        }
        if (!save(validated)) {
            return false;
        }
        ENTITY_DATA = validated;
        return true;
    }

    public static Map<String, EntityEditData> parseConfigJson(String json) {
        if (json == null) {
            return null;
        }

        try (Reader reader = new StringReader(json)) {
            return parseConfig(reader);
        } catch (Exception e) {
            ModifierModule.LOGGER.error("Failed to parse entity modifier config JSON", e);
            return null;
        }
    }

    private static Map<String, EntityEditData> readConfigFile() {
        if (!Files.exists(CONFIG_PATH)) {
            return new TreeMap<>();
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            return parseConfig(reader);
        } catch (Exception e) {
            ModifierModule.LOGGER.error("Failed to load entity_modifier.json", e);
            return null;
        }
    }

    private static Map<String, EntityEditData> parseConfig(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root == null || root.isJsonNull()) {
            return new TreeMap<>();
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("entity_modifier.json root must be an object");
        }

        Map<String, EntityEditData> loaded = new TreeMap<>();
        JsonObject object = root.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String entityId = normalizeResourceId(entry.getKey());
            if (entityId == null && !"__global__".equals(entry.getKey())) {
                ModifierModule.LOGGER.warn("Removed malformed entity modifier config entry: {}", entry.getKey());
                continue;
            }

            String key = "__global__".equals(entry.getKey()) ? "__global__" : entityId;
            try {
                EntityEditData parsed = GSON.fromJson(entry.getValue(), EntityEditData.class);
                EntityEditData normalized = normalizeStructure(parsed);
                if (normalized == null) {
                    ModifierModule.LOGGER.warn("Removed malformed entity modifier config entry: {}", entry.getKey());
                    continue;
                }
                loaded.put(key, normalized);
            } catch (Exception e) {
                ModifierModule.LOGGER.warn("Removed malformed entity modifier config entry: {}", entry.getKey());
            }
        }

        return loaded;
    }

    private static EntityEditData normalizeStructure(EntityEditData source) {
        if (source == null || source.attributes == null || source.buffs == null) {
            return null;
        }

        EntityEditData target = new EntityEditData();

        for (Map.Entry<String, Double> attributeEntry : source.attributes.entrySet()) {
            String attributeId = normalizeResourceId(attributeEntry.getKey());
            Double value = attributeEntry.getValue();
            if (attributeId == null || value == null || !Double.isFinite(value)) {
                return null;
            }
            target.attributes.put(attributeId, value);
        }

        for (Map.Entry<String, PotionBuff> buffEntry : source.buffs.entrySet()) {
            String effectId = normalizeResourceId(buffEntry.getKey());
            PotionBuff sourceBuff = buffEntry.getValue();
            if (effectId == null || sourceBuff == null
                    || !Double.isFinite(sourceBuff.chance)
                    || sourceBuff.chance < 0.0D
                    || sourceBuff.chance > 1.0D
                    || sourceBuff.minLevel < 0
                    || sourceBuff.maxLevel < sourceBuff.minLevel) {
                return null;
            }

            String normalizedDimensions = normalizeDimensionList(sourceBuff.dimensions);
            if (normalizedDimensions == null) {
                return null;
            }

            PotionBuff targetBuff = new PotionBuff();
            targetBuff.chance = sourceBuff.chance;
            targetBuff.minLevel = sourceBuff.minLevel;
            targetBuff.maxLevel = sourceBuff.maxLevel;
            targetBuff.dimensions = normalizedDimensions;
            target.buffs.put(effectId, targetBuff);
        }

        return target;
    }

    private static String normalizeDimensionList(String dimensions) {
        String rawDimensions = dimensions == null ? "" : dimensions.trim();
        if (rawDimensions.isEmpty()) {
            return "";
        }

        Set<String> seen = new HashSet<>();
        StringBuilder normalized = new StringBuilder();
        for (String rawDimension : rawDimensions.split(",")) {
            String dimensionId = normalizeResourceId(rawDimension);
            if (dimensionId == null) {
                return null;
            }
            if (seen.add(dimensionId)) {
                if (normalized.length() > 0) {
                    normalized.append(',');
                }
                normalized.append(dimensionId);
            }
        }
        return normalized.toString();
    }

    private static String normalizeResourceId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(trimmed);
        return key == null ? null : key.toString();
    }

    public static Map<String, EntityEditData> validateForServer(Map<String, EntityEditData> data, MinecraftServer server) {
        Map<String, EntityEditData> validated = new TreeMap<>();
        if (data == null || server == null) {
            return validated;
        }

        Set<ResourceLocation> dimensions = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            dimensions.add(level.dimension().location());
        }

        for (Map.Entry<String, EntityEditData> entityEntry : data.entrySet()) {
            String entityId = entityEntry.getKey();
            if ("__global__".equals(entityId)) {
                continue;
            }

            EntityEditData target = validateEntityEntry(entityId, entityEntry.getValue(), dimensions);
            if (target == null) {
                ModifierModule.LOGGER.warn("Removed invalid entity modifier config entry: {}", entityId);
                continue;
            }

            if (!target.attributes.isEmpty() || !target.buffs.isEmpty()) {
                validated.put(normalizeResourceId(entityId), target);
            }
        }

        return validated;
    }

    private static EntityEditData validateEntityEntry(String entityId, EntityEditData source, Set<ResourceLocation> dimensions) {
        String normalizedEntityId = normalizeResourceId(entityId);
        if (normalizedEntityId == null) {
            return null;
        }

        ResourceLocation entityKey = ResourceLocation.tryParse(normalizedEntityId);
        if (entityKey == null || !ForgeRegistries.ENTITY_TYPES.containsKey(entityKey)) {
            return null;
        }

        EntityEditData normalized = normalizeStructure(source);
        if (normalized == null) {
            return null;
        }

        EntityEditData target = new EntityEditData();

        for (Map.Entry<String, Double> attributeEntry : normalized.attributes.entrySet()) {
            ResourceLocation attributeKey = ResourceLocation.tryParse(attributeEntry.getKey());
            if (attributeKey == null || !ForgeRegistries.ATTRIBUTES.containsKey(attributeKey)) {
                return null;
            }
            target.attributes.put(attributeKey.toString(), attributeEntry.getValue());
        }

        for (Map.Entry<String, PotionBuff> buffEntry : normalized.buffs.entrySet()) {
            ResourceLocation effectKey = ResourceLocation.tryParse(buffEntry.getKey());
            if (effectKey == null || !ForgeRegistries.MOB_EFFECTS.containsKey(effectKey)) {
                return null;
            }

            PotionBuff sourceBuff = buffEntry.getValue();
            StringBuilder normalizedDimensions = new StringBuilder();
            if (!sourceBuff.dimensions.isEmpty()) {
                for (String rawDimension : sourceBuff.dimensions.split(",")) {
                    ResourceLocation dimensionKey = ResourceLocation.tryParse(rawDimension);
                    if (dimensionKey == null || !dimensions.contains(dimensionKey)) {
                        return null;
                    }
                    if (normalizedDimensions.length() > 0) {
                        normalizedDimensions.append(',');
                    }
                    normalizedDimensions.append(dimensionKey);
                }
            }

            PotionBuff targetBuff = new PotionBuff();
            targetBuff.chance = sourceBuff.chance;
            targetBuff.minLevel = sourceBuff.minLevel;
            targetBuff.maxLevel = sourceBuff.maxLevel;
            targetBuff.dimensions = normalizedDimensions.toString();
            target.buffs.put(effectKey.toString(), targetBuff);
        }

        return target;
    }

    public static boolean save() {
        return save(ENTITY_DATA);
    }

    public static boolean save(Map<String, EntityEditData> data) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Path tempPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(data == null ? new TreeMap<>() : data, writer);
            }
            try {
                Files.move(tempPath, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempPath, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            ModifierModule.LOGGER.error("Failed to save entity_modifier.json", e);
            return false;
        }
    }
}
