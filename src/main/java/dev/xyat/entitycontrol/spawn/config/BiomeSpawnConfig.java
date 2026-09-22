package dev.xyat.entitycontrol.spawn.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.xyat.entitycontrol.spawn.SpawnModule;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weight;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BiomeSpawnConfig {
    public static final int MIN_PROFILE_COUNT = 1;
    public static final int MAX_PROFILE_COUNT = 64;
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path BASE_DIR = KineticPaths.configDirectory().resolve("kineticcore").resolve("spawn_control");
    private static final Path GLOBALS_PATH = BASE_DIR.resolve("globals.toml");
    private static final Path BACKUP_PATH = BASE_DIR.resolve("spawn_backup.json");
    private static final String PROFILE_PREFIX = "profile_";

    public static GlobalSettings globals = new GlobalSettings();
    public static ConfigProfile currentProfile = new ConfigProfile();

    public static final Map<MobSpawnType, String> TYPE_MAPPING = new EnumMap<>(MobSpawnType.class);
    static {
        TYPE_MAPPING.put(MobSpawnType.NATURAL, "A");
        TYPE_MAPPING.put(MobSpawnType.CHUNK_GENERATION, "A");
        TYPE_MAPPING.put(MobSpawnType.STRUCTURE, "A");
        TYPE_MAPPING.put(MobSpawnType.PATROL, "A");
        TYPE_MAPPING.put(MobSpawnType.CONVERSION, "B");
        TYPE_MAPPING.put(MobSpawnType.COMMAND, "C");
        TYPE_MAPPING.put(MobSpawnType.SPAWN_EGG, "D");
        TYPE_MAPPING.put(MobSpawnType.SPAWNER, "E");
        TYPE_MAPPING.put(MobSpawnType.MOB_SUMMONED, "F");
        TYPE_MAPPING.put(MobSpawnType.EVENT, "G");
    }

    private static final Map<ResourceKey<Biome>, Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>>> VANILLA_SPAWNS = new HashMap<>();
    private static final Map<String, String> REGISTERED_LIVING_ENTITIES = new HashMap<>();
    private static final Map<EntityType<?>, RuntimeEntityRule> RUNTIME_ENTITY_RULES_BY_TYPE = new IdentityHashMap<>();
    private static boolean entityRegistryCacheBuilt = false;

    private static final int RULE_A = 1;
    private static final int RULE_B = 1 << 1;
    private static final int RULE_C = 1 << 2;
    private static final int RULE_D = 1 << 3;
    private static final int RULE_E = 1 << 4;
    private static final int RULE_F = 1 << 5;
    private static final int RULE_G = 1 << 6;

    private record RuntimeEntityRule(
            boolean blockAll,
            boolean invertRules,
            int ruleMask,
            Set<ResourceLocation> dimWhitelist,
            Set<ResourceLocation> dimBlacklist,
            int minDistance,
            int maxDistance,
            double minDistanceSqr,
            double maxDistanceSqr,
            int minHeight,
            int maxHeight,
            int minLight,
            int maxLight,
            boolean customLightRange
    ) {
    }

    public enum SpawnDecision {
        VANILLA,
        ALLOW,
        DENY
    }

    public static class GlobalSettings {
        public boolean enable_rule_override = false;
        public boolean enable_biome_override = false;
        public boolean auto_scan = true;
        public int config_amount = 4;
        public int current_index = 1;
    }

    public static class ConfigProfile {
        public Map<String, Integer> category_caps = new LinkedHashMap<>();
        public Map<String, Integer> category_weights = new LinkedHashMap<>();
        public Map<String, Double> category_spawn_rates = new LinkedHashMap<>();
        public Map<String, EntityNode> entities = new LinkedHashMap<>();
    }

    public static class EntityNode {
        public boolean manual_edit = false;
        public boolean enable_control = false;
        public boolean block_all = false;
        public boolean invert_rules = false;
        public String rules = "ABCDEFG";
        public List<String> dim_whitelist = new ArrayList<>();
        public List<String> dim_blacklist = new ArrayList<>();
        public int min_spawn_distance = 24;
        public int max_spawn_distance = 128;
        public Integer min_spawn_height;
        public Integer max_spawn_height;
        public Integer min_spawn_light;
        public Integer max_spawn_light;
        public String category = "misc";
        public Map<String, SpawnerDataNode> biomes = new LinkedHashMap<>();
        public List<String> deleted_biomes = new ArrayList<>();
    }

    public static class SpawnerDataNode {
        public int weight = 20;
        public int min = 1;
        public int max = 4;
    }

    public static void loadGlobals() {
        if (!Files.exists(BASE_DIR)) {
            try {
                Files.createDirectories(BASE_DIR);
            } catch (Exception ignored) {}
        }
        if (!Files.exists(GLOBALS_PATH)) {
            saveGlobals();
            return;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(GLOBALS_PATH).sync().build()) {
            config.load();

            if (config.contains("enable_override")) {
                boolean old = config.get("enable_override");
                globals.enable_rule_override = old;
                globals.enable_biome_override = old;
            } else {
                globals.enable_rule_override = config.getOrElse("enable_rule_override", false);
                globals.enable_biome_override = config.getOrElse("enable_biome_override", false);
            }

            globals.auto_scan = config.getOrElse("auto_scan", true);
            globals.config_amount = config.getOrElse("config_amount", 4);
            globals.current_index = config.getOrElse("current_index", 1);
            sanitizeGlobals();
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to load globals.toml", e);
        }
    }

    public static boolean saveGlobals() {
        return saveGlobals(globals);
    }

    private static boolean saveGlobals(GlobalSettings settings) {
        sanitizeGlobals(settings);
        try {
            Files.createDirectories(BASE_DIR);
            Path tempPath = GLOBALS_PATH.resolveSibling("globals.tmp.toml");
            try (CommentedFileConfig config = CommentedFileConfig.builder(tempPath)
                    .sync().writingMode(WritingMode.REPLACE).build()) {

                config.set("enable_rule_override", settings.enable_rule_override);
                config.setComment("enable_rule_override",
                        """
                         [全局] 是否启用生成规则限制 (如黑白名单、维度限制、生成类型限制、刷怪范围限制)
                         [Global] Enable spawn restriction rules (e.g., black/whitelists, dimension limits, spawn types, distances)""");

                config.set("enable_biome_override", settings.enable_biome_override);
                config.setComment("enable_biome_override",
                        """
                         [全局] 是否启用属性深度修改 (如群系生成列表、大类生成上限、权重、生成倍率)
                         [Global] Enable biome properties modification (e.g., biomes, category caps, weights, rates)""");

                config.set("auto_scan", settings.auto_scan);
                config.setComment("auto_scan",
                        """
                         [全局] 是否自动扫描全部已注册生物，并补全到当前配置文件中。会优先读取群系生成表，再补全所有已注册 LivingEntity，最后在实际生成事件中兜底补漏。
                         [Global] Automatically scans all registered living entities and appends them to the current profile. It reads biome spawn tables first, then all registered LivingEntity types, and finally uses live spawn discovery as a fallback.""");

                config.set("config_amount", settings.config_amount);
                config.setComment("config_amount",
                        """
                         [全局] 可用配置方案数量，范围 1-64；请在生成控制 GUI 的方案菜单中调整。
                         [Global] Number of available profiles, from 1 to 64. Adjust it from the profile menu in the spawn-control GUI.""");

                config.set("current_index", settings.current_index);
                config.setComment("current_index",
                        """
                         [全局] 当前正在生效的配置文件编号 (如设置为 1 则读取并应用 profile_1.json)
                         [Global] Currently active configuration profile index (e.g. 1 reads profile_1.json)""");

                config.save();
            }
            try {
                Files.move(tempPath, GLOBALS_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempPath, GLOBALS_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to save globals.toml", e);
            return false;
        }
        return true;
    }

    public static void sanitizeGlobals() {
        sanitizeGlobals(globals);
    }

    private static void sanitizeGlobals(GlobalSettings settings) {
        settings.config_amount = Math.max(MIN_PROFILE_COUNT, Math.min(MAX_PROFILE_COUNT, settings.config_amount));
        settings.current_index = Math.max(1, Math.min(settings.config_amount, settings.current_index));
    }

    public static boolean isValidProfileCount(int count) {
        return count >= MIN_PROFILE_COUNT && count <= MAX_PROFILE_COUNT;
    }

    public static boolean isValidProfileIndex(int index) {
        return index >= 1 && index <= globals.config_amount;
    }

    public static synchronized boolean saveEditorState(
            MinecraftServer server,
            boolean ruleOverride,
            boolean biomeOverride,
            boolean autoScan,
            int amount,
            int currentIndex,
            int editIndex,
            ConfigProfile profile
    ) {
        GlobalSettings previousGlobals = globals;
        ConfigProfile previousCurrentProfile = currentProfile;
        Path profilePath = BASE_DIR.resolve(PROFILE_PREFIX + editIndex + ".json");
        FileState previousGlobalsFile = snapshotFile(GLOBALS_PATH);
        FileState previousProfileFile = snapshotFile(profilePath);
        if (previousGlobalsFile == null || previousProfileFile == null) {
            return false;
        }

        GlobalSettings candidateGlobals = new GlobalSettings();
        candidateGlobals.enable_rule_override = ruleOverride;
        candidateGlobals.enable_biome_override = biomeOverride;
        candidateGlobals.auto_scan = autoScan;
        candidateGlobals.config_amount = amount;
        candidateGlobals.current_index = currentIndex;
        sanitizeGlobals(candidateGlobals);

        ConfigProfile candidateProfile = copyProfile(profile);
        if (candidateGlobals.auto_scan) {
            performAutoScan(server, candidateProfile);
        }

        if (!saveGlobals(candidateGlobals)) {
            return false;
        }
        if (!saveProfileData(editIndex, candidateProfile)) {
            restoreFile(GLOBALS_PATH, previousGlobalsFile);
            restoreFile(profilePath, previousProfileFile);
            return false;
        }

        globals = candidateGlobals;
        try {
            if (editIndex == globals.current_index) {
                currentProfile = candidateProfile;
                applyToGame(server);
            }
            if (globals.auto_scan) {
                refreshAutoScanBackup(server);
            }
            return true;
        } catch (RuntimeException exception) {
            globals = previousGlobals;
            currentProfile = previousCurrentProfile;
            restoreFile(GLOBALS_PATH, previousGlobalsFile);
            restoreFile(profilePath, previousProfileFile);
            try {
                applyToGame(server);
            } catch (RuntimeException rollbackError) {
                exception.addSuppressed(rollbackError);
            }
            SpawnModule.LOGGER.error("Failed to apply saved spawn-control configuration; previous state restored", exception);
            return false;
        }
    }

    private static ConfigProfile copyProfile(ConfigProfile profile) {
        ConfigProfile copy = GSON.fromJson(GSON.toJson(profile == null ? new ConfigProfile() : profile), ConfigProfile.class);
        return normalizeProfile(copy);
    }

    private record FileState(boolean existed, byte[] contents) {
    }

    private static FileState snapshotFile(Path path) {
        try {
            if (!Files.exists(path)) {
                return new FileState(false, new byte[0]);
            }
            return new FileState(true, Files.readAllBytes(path));
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to snapshot config file {}", path, e);
            return null;
        }
    }

    private static void restoreFile(Path path, FileState state) {
        try {
            if (!state.existed()) {
                Files.deleteIfExists(path);
                return;
            }
            Files.createDirectories(path.getParent());
            Path tempPath = path.resolveSibling(path.getFileName() + ".rollback.tmp");
            Files.write(tempPath, state.contents());
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to restore config file {}", path, e);
        }
    }

    public static ConfigProfile getProfileData(int index) {
        Path path =
                BASE_DIR.resolve(
                        PROFILE_PREFIX
                                + index
                                + ".json"
                );

        ConfigProfile profile =
                new ConfigProfile();

        if (Files.exists(path)) {
            try (Reader reader =
                         Files.newBufferedReader(
                                 path,
                                 StandardCharsets.UTF_8
                         )) {
                ConfigProfile loaded =
                        GSON.fromJson(
                                reader,
                                ConfigProfile.class
                        );

                if (loaded != null) {
                    profile = loaded;
                }
            } catch (Exception ignored) {
            }
        }

        profile =
                normalizeProfile(profile);


        return profile;
    }

    public static boolean saveProfileData(int index, ConfigProfile profile) {
        ConfigProfile normalized = normalizeProfile(profile);
        Path path = BASE_DIR.resolve(PROFILE_PREFIX + index + ".json");
        try {
            Files.createDirectories(BASE_DIR);
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(normalized, writer);
            }
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to save spawn profile {}", path, e);
            return false;
        }
        return true;
    }

    public static ConfigProfile getBackupProfileData() {
        Path path = BACKUP_PATH;
        ConfigProfile profile = new ConfigProfile();

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ConfigProfile loaded = GSON.fromJson(reader, ConfigProfile.class);
                if (loaded != null) {
                    profile = loaded;
                }
            } catch (Exception e) {
                SpawnModule.LOGGER.error("Failed to load spawn control backup {}", path, e);
            }
        }

        return normalizeProfile(profile);
    }

    public static ConfigProfile refreshAutoScanBackup(MinecraftServer server) {
        if (server == null) {
            return getBackupProfileData();
        }

        ConfigProfile backup = new ConfigProfile();
        performAutoScan(server, backup);
        saveBackupProfileData(backup);
        return backup;
    }

    public static EntityNode copyEntityNode(EntityNode source) {
        if (source == null) {
            return null;
        }

        EntityNode copy = new EntityNode();
        copy.manual_edit = source.manual_edit;
        copy.enable_control = source.enable_control;
        copy.block_all = source.block_all;
        copy.invert_rules = source.invert_rules;
        copy.rules = source.rules;
        copy.dim_whitelist = source.dim_whitelist == null
                ? new ArrayList<>()
                : new ArrayList<>(source.dim_whitelist);
        copy.dim_blacklist = source.dim_blacklist == null
                ? new ArrayList<>()
                : new ArrayList<>(source.dim_blacklist);
        copy.min_spawn_distance = source.min_spawn_distance;
        copy.max_spawn_distance = source.max_spawn_distance;
        copy.min_spawn_height = source.min_spawn_height;
        copy.max_spawn_height = source.max_spawn_height;
        copy.min_spawn_light = source.min_spawn_light;
        copy.max_spawn_light = source.max_spawn_light;
        copy.category = source.category;
        copy.biomes = new LinkedHashMap<>();
        if (source.biomes != null) {
            for (Map.Entry<String, SpawnerDataNode> entry : source.biomes.entrySet()) {
                SpawnerDataNode sourceData = entry.getValue();
                SpawnerDataNode copiedData = new SpawnerDataNode();
                if (sourceData != null) {
                    copiedData.weight = sourceData.weight;
                    copiedData.min = sourceData.min;
                    copiedData.max = sourceData.max;
                }
                copy.biomes.put(entry.getKey(), copiedData);
            }
        }
        copy.deleted_biomes = source.deleted_biomes == null
                ? new ArrayList<>()
                : new ArrayList<>(source.deleted_biomes);
        normalizeNode(copy);
        return copy;
    }

    private static void saveBackupProfileData(ConfigProfile profile) {
        ConfigProfile normalized = normalizeProfile(profile);
        try {
            Files.createDirectories(BASE_DIR);
            try (Writer writer = Files.newBufferedWriter(BACKUP_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(normalized, writer);
            }
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to save spawn control backup {}", BACKUP_PATH, e);
        }
    }

    public static void loadProfile(int index) {
        currentProfile = getProfileData(index);
    }

    public static void saveProfile(int index) {
        saveProfileData(index, currentProfile);
    }

    private static ConfigProfile normalizeProfile(ConfigProfile profile) {
        if (profile == null) profile = new ConfigProfile();
        profile.category_caps = toLinkedMap(profile.category_caps);
        profile.category_weights = toLinkedMap(profile.category_weights);
        profile.category_spawn_rates = toLinkedMap(profile.category_spawn_rates);
        profile.entities = toLinkedMap(profile.entities);

        profile.category_caps.replaceAll((key, value) -> value == null ? 0 : Math.max(0, value));
        profile.category_weights.replaceAll((key, value) -> value == null ? 0 : Math.max(0, value));
        profile.category_spawn_rates.replaceAll((key, value) ->
                value == null || !Double.isFinite(value) ? 0.0D : Math.max(0.0D, value));

        initDefaultMacros(profile);

        for (Map.Entry<String, EntityNode> entry : profile.entities.entrySet()) {
            EntityNode node = entry.getValue();
            if (node == null) {
                node = new EntityNode();
                entry.setValue(node);
            }
            normalizeNode(node);
        }
        return profile;
    }

    private static <K, V> Map<K, V> toLinkedMap(Map<K, V> source) {
        if (source == null) return new LinkedHashMap<>();
        if (source instanceof LinkedHashMap<?, ?>) return source;
        return new LinkedHashMap<>(source);
    }

    private static void normalizeNode(EntityNode node) {
        if (node.rules == null || node.rules.isEmpty()) node.rules = "ABCDEFG";
        node.rules = node.rules.toUpperCase(Locale.ROOT).replaceAll("[^A-G]", "");
        if (node.rules.isEmpty()) node.rules = "ABCDEFG";
        node.min_spawn_distance = Math.max(0, Math.min(128, node.min_spawn_distance));
        node.max_spawn_distance = Math.max(0, Math.min(128, node.max_spawn_distance));
        if (node.max_spawn_distance < node.min_spawn_distance) node.max_spawn_distance = node.min_spawn_distance;
        if (node.dim_whitelist == null) node.dim_whitelist = new ArrayList<>();
        if (node.dim_blacklist == null) node.dim_blacklist = new ArrayList<>();
        if (node.category == null || node.category.isEmpty()) node.category = "misc";
        node.biomes = toLinkedMap(node.biomes);
        if (node.deleted_biomes == null) node.deleted_biomes = new ArrayList<>();
        if (node.min_spawn_height != null
                && node.max_spawn_height != null
                && node.min_spawn_height > node.max_spawn_height) {
            int lower = node.max_spawn_height;
            node.max_spawn_height = node.min_spawn_height;
            node.min_spawn_height = lower;
        }

        if (node.min_spawn_light != null) {
            node.min_spawn_light = Math.max(0, Math.min(15, node.min_spawn_light));
        }
        if (node.max_spawn_light != null) {
            node.max_spawn_light = Math.max(0, Math.min(15, node.max_spawn_light));
        }
        if (node.min_spawn_light != null
                && node.max_spawn_light != null
                && node.min_spawn_light > node.max_spawn_light) {
            int lower = node.max_spawn_light;
            node.max_spawn_light = node.min_spawn_light;
            node.min_spawn_light = lower;
        }

        for (Map.Entry<String, SpawnerDataNode> entry : node.biomes.entrySet()) {
            if (entry.getValue() == null) entry.setValue(new SpawnerDataNode());
            SpawnerDataNode data = entry.getValue();
            data.weight = Math.max(0, data.weight);
            data.min = Math.max(0, data.min);
            data.max = Math.max(data.min, data.max);
        }
    }

    private static void initDefaultMacros(ConfigProfile profile) {
        profile.category_weights.putIfAbsent("monster", 100);
        profile.category_weights.putIfAbsent("creature", 60);
        profile.category_weights.putIfAbsent("ambient", 30);
        profile.category_weights.putIfAbsent("axolotls", 80);
        profile.category_weights.putIfAbsent("underground_water_creature", 80);
        profile.category_weights.putIfAbsent("water_creature", 60);
        profile.category_weights.putIfAbsent("water_ambient", 50);
        profile.category_weights.putIfAbsent("misc", 20);
        for (MobCategory cat : MobCategory.values()) {
            profile.category_spawn_rates.putIfAbsent(cat.getName(), 1.0);
            profile.category_caps.putIfAbsent(cat.getName(), cat.getMaxInstancesPerChunk());
        }
    }

    public static void onServerStart(MinecraftServer server) {
        captureOriginalBiomeSpawns(server);
        buildRegisteredEntityCache(server);

        loadGlobals();
        loadProfile(globals.current_index);

        if (globals.auto_scan) {
            refreshAutoScanBackup(server);
        }

        for (int i = 2; i <= globals.config_amount; i++) {
            Path path = BASE_DIR.resolve(PROFILE_PREFIX + i + ".json");
            if (!Files.exists(path)) {
                try {
                    Files.copy(BASE_DIR.resolve(PROFILE_PREFIX + "1.json"), path);
                } catch (Exception ignored) {
                }
            }
        }

        for (int i = 1; i <= globals.config_amount; i++) {
            ConfigProfile profile;
            if (i == globals.current_index) {
                profile = currentProfile;
            } else {
                Path path = BASE_DIR.resolve(PROFILE_PREFIX + i + ".json");
                if (!Files.exists(path)) continue;
                profile = getProfileData(i);
            }

            boolean changed = false;
            if (globals.auto_scan) {
                changed = performAutoScan(server, profile);
            }

            if (changed) {
                saveProfileData(i, profile);
            }
        }

        applyToGame(server);
    }

    private static void captureOriginalBiomeSpawns(MinecraftServer server) {
        if (!VANILLA_SPAWNS.isEmpty()) return;
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomeRegistry.entrySet()) {
            if (entry.getValue().getMobSettings() instanceof IMobSpawnSettingsAccess access) {
                Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> map = access.entitycontrol_spawn$getSpawners();
                VANILLA_SPAWNS.put(entry.getKey(), copySpawnMap(map));
            }
        }
    }

    private static Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> copySpawnMap(Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> source) {
        Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> copy = new EnumMap<>(MobCategory.class);
        if (source != null) copy.putAll(source);
        return copy;
    }

    private static void buildRegisteredEntityCache(MinecraftServer server) {
        if (entityRegistryCacheBuilt) return;
        REGISTERED_LIVING_ENTITIES.clear();
        Map<ResourceLocation, EntityType<?>> entries = KineticRegistries.entityTypes().entries();
        for (Map.Entry<ResourceLocation, EntityType<?>> entry : entries.entrySet()) {
            EntityType<?> type = entry.getValue();
            ResourceLocation id = entry.getKey();
            if (type == null) continue;
            if (!shouldScanEntityType(id, type, server)) continue;
            REGISTERED_LIVING_ENTITIES.put(id.toString(), type.getCategory().getName());
        }
        entityRegistryCacheBuilt = true;
    }

    private static boolean shouldScanEntityType(ResourceLocation id, EntityType<?> type, MinecraftServer server) {
        if (type.getCategory() != MobCategory.MISC) return true;

        try {
            Entity entity = type.create(server.overworld());
            if (entity instanceof LivingEntity) return true;
        } catch (Throwable ignored) {}

        if ("minecraft".equals(id.getNamespace())) return false;
        return !isObviousNonLivingMiscEntity(id);
    }

    private static boolean isObviousNonLivingMiscEntity(ResourceLocation id) {
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("arrow")
                || path.contains("projectile")
                || path.contains("bullet")
                || path.contains("missile")
                || path.contains("rocket")
                || path.contains("grenade")
                || path.contains("bomb")
                || path.contains("fireball")
                || path.contains("laser")
                || path.contains("beam")
                || path.contains("bolt")
                || path.contains("thrown")
                || path.contains("throwable")
                || path.contains("particle")
                || path.contains("effect")
                || path.contains("seat")
                || path.contains("chair")
                || path.contains("hook")
                || path.contains("bobber")
                || path.contains("boat")
                || path.contains("cart")
                || path.contains("minecart")
                || path.contains("item")
                || path.contains("display")
                || path.contains("falling_block")
                || path.contains("area_effect_cloud");
    }

    public static boolean isEntityIdValid(String entityId) {
        if (entityId == null || entityId.isBlank()) return false;
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        return id != null
                && KineticRegistries.entityTypes().contains(id)
                && KineticRegistries.entityTypes().get(id) != null;
    }

    public static boolean cleanupMissingEntities(ConfigProfile profile) {
        if (profile == null) return false;

        ConfigProfile target = normalizeProfile(profile);
        return target.entities.entrySet().removeIf(entry -> !isEntityIdValid(entry.getKey()));
    }

    public static boolean performAutoScan(
            MinecraftServer server,
            ConfigProfile profile
    ) {
        if (server == null || profile == null) {
            return false;
        }

        captureOriginalBiomeSpawns(server);
        buildRegisteredEntityCache(server);

        return performScanAdditions(server, profile);
    }

    private static boolean performScanAdditions(
            MinecraftServer server,
            ConfigProfile profile
    ) {
        ConfigProfile target = normalizeProfile(profile);
        buildRegisteredEntityCache(server);
        boolean needsSave = false;

        for (Map.Entry<ResourceKey<Biome>, Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>>> entry
                : VANILLA_SPAWNS.entrySet()) {
            String biomeId = entry.getKey().location().toString();
            Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawnMap = entry.getValue();

            for (Map.Entry<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> categoryEntry
                    : spawnMap.entrySet()) {
                MobCategory category = categoryEntry.getKey();
                WeightedRandomList<MobSpawnSettings.SpawnerData> list = categoryEntry.getValue();

                if (category == null || list == null || list.unwrap().isEmpty()) {
                    continue;
                }

                for (MobSpawnSettings.SpawnerData data : list.unwrap()) {
                    ResourceLocation id = KineticRegistries.entityTypes().id(data.type);
                    if (id == null) continue;

                    String entityId = id.toString();
                    if (addEntityNode(target, entityId, category.getName())) {
                        needsSave = true;
                    }

                    EntityNode node = target.entities.get(entityId);
                    normalizeNode(node);

                    if (!node.biomes.containsKey(biomeId)
                            && !node.deleted_biomes.contains(biomeId)) {
                        SpawnerDataNode spawnData = new SpawnerDataNode();
                        spawnData.weight = data.getWeight().asInt();
                        spawnData.min = data.minCount;
                        spawnData.max = data.maxCount;
                        node.biomes.put(biomeId, spawnData);
                        needsSave = true;
                    }
                }
            }
        }

        for (Map.Entry<String, String> entry : REGISTERED_LIVING_ENTITIES.entrySet()) {
            if (addEntityNode(target, entry.getKey(), entry.getValue())) {
                needsSave = true;
            }
        }

        return needsSave;
    }

    private static boolean addEntityNode(
            ConfigProfile profile,
            String entityId,
            String category
    ) {
        if (profile == null
                || entityId == null
                || entityId.isEmpty()) {
            return false;
        }

        EntityNode existing =
                profile.entities.get(
                        entityId
                );

        if (existing != null) {
            normalizeNode(existing);

            if (!existing.manual_edit
                    && (
                    existing.category == null
                            || existing.category.isEmpty()
                            || "misc".equals(existing.category)
            )
                    && category != null
                    && !category.isEmpty()
                    && !category.equals(existing.category)) {
                existing.category = category;
                return true;
            }

            return false;
        }

        EntityNode node =
                new EntityNode();

        node.category =
                category == null
                        || category.isEmpty()
                        ? "misc"
                        : category;

        normalizeNode(node);

        profile.entities.put(
                entityId,
                node
        );

        return true;
    }

    public static void applyToGame(MinecraftServer server) {
        currentProfile = normalizeProfile(currentProfile);
        rebuildRuntimeEntityRules();

        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);

        if (!globals.enable_biome_override) {
            for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomeRegistry.entrySet()) {
                Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> original = VANILLA_SPAWNS.get(entry.getKey());
                if (original == null) continue;
                if (entry.getValue().getMobSettings() instanceof IMobSpawnSettingsAccess access) {
                    access.entitycontrol_spawn$setSpawners(copySpawnMap(original));
                }
            }
            return;
        }

        Map<String, Map<MobCategory, List<MobSpawnSettings.SpawnerData>>> biomeTargetSpawns = new HashMap<>();
        int processEntityCount = 0;
        int rulesAddedCount = 0;

        for (Map.Entry<String, EntityNode> e : currentProfile.entities.entrySet()) {
            String entityId = e.getKey();
            EntityNode node = e.getValue();
            normalizeNode(node);
            if (node.biomes.isEmpty()) continue;

            ResourceLocation entityLocation = KineticResourceIds.tryParse(entityId);
            if (entityLocation == null) continue;

            EntityType<?> entityType = KineticRegistries.entityTypes().get(entityLocation);
            if (entityType == null) continue;

            processEntityCount++;
            int multiplier = currentProfile.category_weights.getOrDefault(node.category, 100);
            MobCategory targetCat = getCategoryByName(node.category);
            MobCategory realCat = entityType.getCategory();
            if (targetCat != realCat && realCat != MobCategory.MISC) targetCat = realCat;

            for (Map.Entry<String, SpawnerDataNode> b : node.biomes.entrySet()) {
                SpawnerDataNode data = b.getValue();
                if (data == null) continue;
                int effectiveWeight = (int) Math.round(data.weight * (multiplier / 100.0));
                if (effectiveWeight <= 0) continue;

                biomeTargetSpawns.computeIfAbsent(b.getKey(), k -> new EnumMap<>(MobCategory.class))
                        .computeIfAbsent(targetCat, k -> new ArrayList<>())
                        .add(new MobSpawnSettings.SpawnerData(entityType, Weight.of(effectiveWeight), data.min, data.max));
                rulesAddedCount++;
            }
        }

        SpawnModule.LOGGER.info("[生物生成] 共解析 {} 个生物，向群系池注入了 {} 条生成规则", processEntityCount, rulesAddedCount);

        for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomeRegistry.entrySet()) {
            String biomeId = entry.getKey().location().toString();
            Map<MobCategory, List<MobSpawnSettings.SpawnerData>> targets = biomeTargetSpawns.getOrDefault(biomeId, Collections.emptyMap());
            Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> newSpawnersMap = new EnumMap<>(MobCategory.class);
            for (MobCategory category : MobCategory.values()) {
                List<MobSpawnSettings.SpawnerData> list = targets.getOrDefault(category, Collections.emptyList());
                newSpawnersMap.put(category, WeightedRandomList.create(list));
            }
            if (entry.getValue().getMobSettings() instanceof IMobSpawnSettingsAccess access) {
                access.entitycontrol_spawn$setSpawners(newSpawnersMap);
            }
        }
    }

    private static MobCategory getCategoryByName(String name) {
        for (MobCategory cat : MobCategory.values()) {
            if (cat.getName().equalsIgnoreCase(name)) return cat;
        }
        return MobCategory.MISC;
    }

    private static void rebuildRuntimeEntityRules() {
        RUNTIME_ENTITY_RULES_BY_TYPE.clear();

        for (Map.Entry<String, EntityNode> entry : currentProfile.entities.entrySet()) {
            EntityNode node = entry.getValue();
            if (node == null || !node.enable_control) continue;
            normalizeNode(node);

            ResourceLocation entityId = KineticResourceIds.tryParse(entry.getKey());
            if (entityId == null) continue;

            EntityType<?> entityType = KineticRegistries.entityTypes().get(entityId);
            if (entityType == null) continue;

            int minDistance = Math.max(0, node.min_spawn_distance);
            int maxDistance = Math.max(0, node.max_spawn_distance);
            int minHeight = node.min_spawn_height == null
                    ? Integer.MIN_VALUE
                    : node.min_spawn_height;
            int maxHeight = node.max_spawn_height == null
                    ? Integer.MAX_VALUE
                    : node.max_spawn_height;
            int minLight = node.min_spawn_light == null
                    ? 0
                    : node.min_spawn_light;
            int maxLight = node.max_spawn_light == null
                    ? 15
                    : node.max_spawn_light;
            boolean customLightRange =
                    node.min_spawn_light != null
                            || node.max_spawn_light != null;

            RuntimeEntityRule runtimeRule = new RuntimeEntityRule(
                    node.block_all,
                    node.invert_rules,
                    buildRuleMask(node.rules),
                    parseResourceLocations(node.dim_whitelist),
                    parseResourceLocations(node.dim_blacklist),
                    minDistance,
                    maxDistance,
                    squareDistance(minDistance),
                    squareDistance(maxDistance),
                    minHeight,
                    maxHeight,
                    minLight,
                    maxLight,
                    customLightRange
            );

            RUNTIME_ENTITY_RULES_BY_TYPE.put(entityType, runtimeRule);
        }
    }

    private static Set<ResourceLocation> parseResourceLocations(List<String> values) {
        if (values == null || values.isEmpty()) return Set.of();

        Set<ResourceLocation> parsed = new HashSet<>();
        for (String value : values) {
            ResourceLocation id = KineticResourceIds.tryParse(value);
            if (id != null) parsed.add(id);
        }
        return parsed.isEmpty() ? Set.of() : Set.copyOf(parsed);
    }

    private static int buildRuleMask(String rules) {
        if (rules == null || rules.isEmpty()) return 0;

        int mask = 0;
        for (int i = 0; i < rules.length(); i++) {
            mask |= switch (Character.toUpperCase(rules.charAt(i))) {
                case 'A' -> RULE_A;
                case 'B' -> RULE_B;
                case 'C' -> RULE_C;
                case 'D' -> RULE_D;
                case 'E' -> RULE_E;
                case 'F' -> RULE_F;
                case 'G' -> RULE_G;
                default -> 0;
            };
        }
        return mask;
    }

    private static int getSpawnTypeMask(MobSpawnType type) {
        if (type == null) return 0;
        return switch (type) {
            case NATURAL, CHUNK_GENERATION, STRUCTURE, PATROL -> RULE_A;
            case CONVERSION -> RULE_B;
            case COMMAND -> RULE_C;
            case SPAWN_EGG -> RULE_D;
            case SPAWNER -> RULE_E;
            case MOB_SUMMONED -> RULE_F;
            case EVENT -> RULE_G;
            default -> 0;
        };
    }

    private static boolean blocksBySource(RuntimeEntityRule rule, MobSpawnType type) {
        int typeMask = getSpawnTypeMask(type);
        if (typeMask == 0) return false;

        boolean listed = (rule.ruleMask() & typeMask) != 0;
        return rule.invertRules() == listed;
    }

    private static boolean blocksByDimension(RuntimeEntityRule rule, ResourceLocation dimension) {
        if (dimension == null) return false;
        if (!rule.dimWhitelist().isEmpty() && !rule.dimWhitelist().contains(dimension)) return true;
        return !rule.dimBlacklist().isEmpty() && rule.dimBlacklist().contains(dimension);
    }

    private static boolean blocksByHeight(RuntimeEntityRule rule, MobSpawnType type, int y) {
        if (getSpawnTypeMask(type) != RULE_A) return false;
        return y < rule.minHeight() || y > rule.maxHeight();
    }

    private static boolean isBlocked(
            RuntimeEntityRule rule,
            MobSpawnType type,
            ResourceLocation dimension,
            int y
    ) {
        return rule.blockAll()
                || blocksByDimension(rule, dimension)
                || blocksBySource(rule, type)
                || blocksByHeight(rule, type, y);
    }

    private static double squareDistance(int distance) {
        return (double) distance * distance;
    }

    public static SpawnDecision getSpawnDecision(
            EntityType<?> entityType,
            MobSpawnType type,
            ResourceLocation dimension,
            int y
    ) {
        if (!globals.enable_rule_override || entityType == null) {
            return SpawnDecision.VANILLA;
        }

        RuntimeEntityRule rule = RUNTIME_ENTITY_RULES_BY_TYPE.get(entityType);
        if (rule == null) return SpawnDecision.VANILLA;

        return isBlocked(rule, type, dimension, y)
                ? SpawnDecision.DENY
                : SpawnDecision.ALLOW;
    }

    public static boolean shouldBlockNaturalSpawnEarly(
            ServerLevel level,
            EntityType<?> entityType,
            BlockPos pos,
            double squaredDistance
    ) {
        if (!globals.enable_rule_override
                || level == null
                || entityType == null
                || pos == null) {
            return false;
        }

        RuntimeEntityRule rule = RUNTIME_ENTITY_RULES_BY_TYPE.get(entityType);
        if (rule == null) return false;

        if (isBlocked(
                rule,
                MobSpawnType.NATURAL,
                level.dimension().location(),
                pos.getY()
        )) {
            return true;
        }

        if (squaredDistance < rule.minDistanceSqr()
                || squaredDistance > rule.maxDistanceSqr()) {
            return true;
        }

        if (!rule.customLightRange()) {
            return false;
        }

        int brightness = level.getMaxLocalRawBrightness(pos);
        return brightness < rule.minLight()
                || brightness > rule.maxLight();
    }

    public static boolean isSpawnLightAllowed(
            ServerLevel level,
            EntityType<?> entityType,
            BlockPos pos,
            MobSpawnType spawnType
    ) {
        if (!globals.enable_rule_override
                || level == null
                || entityType == null
                || pos == null
                || getSpawnTypeMask(spawnType) != RULE_A) {
            return true;
        }

        RuntimeEntityRule rule = RUNTIME_ENTITY_RULES_BY_TYPE.get(entityType);
        if (rule == null || !rule.customLightRange()) {
            return true;
        }

        int brightness = level.getMaxLocalRawBrightness(pos);
        return brightness >= rule.minLight()
                && brightness <= rule.maxLight();
    }

    public static boolean isSpawnDistanceAllowed(
            EntityType<?> entityType,
            double squaredDistance
    ) {
        if (!globals.enable_rule_override || entityType == null) return true;

        RuntimeEntityRule rule = RUNTIME_ENTITY_RULES_BY_TYPE.get(entityType);
        if (rule == null) return true;

        return squaredDistance >= rule.minDistanceSqr()
                && squaredDistance <= rule.maxDistanceSqr();
    }

    public static boolean hasCustomSpawnDistance(EntityType<?> entityType) {
        if (!globals.enable_rule_override || entityType == null) return false;

        RuntimeEntityRule rule = RUNTIME_ENTITY_RULES_BY_TYPE.get(entityType);
        return rule != null
                && (rule.minDistance() != 24 || rule.maxDistance() != 128);
    }

    public static double getCategorySpawnRate(MobCategory category) {
        if (!globals.enable_biome_override) return 1.0;
        return currentProfile.category_spawn_rates.getOrDefault(category.getName(), 1.0);
    }

    public static Integer getCategoryCapOverride(String categoryName) {
        if (!globals.enable_biome_override
                || categoryName == null
                || currentProfile == null
                || currentProfile.category_caps == null) {
            return null;
        }
        return currentProfile.category_caps.get(categoryName);
    }

    public interface IMobSpawnSettingsAccess {
        Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> entitycontrol_spawn$getSpawners();
        void entitycontrol_spawn$setSpawners(Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawners);
    }
}
