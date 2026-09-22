package dev.xyat.entitycontrol.spawn.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.xyat.entitycontrol.spawn.SpawnModule;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class SpawnerConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path BASE_DIR = KineticPaths.configDirectory().resolve("kineticcore");
    private static final Path CONFIG_PATH = BASE_DIR.resolve("spawner.json");
    private static final Path BACKUP_PATH = BASE_DIR.resolve("spawner_backup.json");

    public static final Map<String, SpawnerRule> SPAWNER_RULES_CACHE = new HashMap<>();
    public static final Set<String> SPAWNER_BLACKLIST_CACHE = new HashSet<>();
    private static final Set<String> MODIFIED_ENTITY_CACHE = new HashSet<>();

    public static boolean enableSpawnerBreaker = true;
    public static String spawnerBreakMode = "COOLDOWN";
    public static int spawnerCooldownSeconds = 3600;
    public static int defaultSpawnerThreshold = 20;
    public static boolean spawnerBreakerNotification = true;
    public static List<String> spawnerRulesRaw = new ArrayList<>();
    public static List<String> spawnerBlacklist = new ArrayList<>();

    private static SpawnerData data = new SpawnerData();
    private static SpawnerBackupData backupData = new SpawnerBackupData();
    private static SpawnerRule defaultRule = createRuleForEditor(data);
    private static List<String> editableEntityCache = List.of();
    private static boolean editableEntityCacheBuilt;

    public static class SpawnerData {
        public boolean enabled = true;
        public boolean notification = true;
        public SpawnerRule global = new SpawnerRule();
        public Map<String, SpawnerRule> entities = new TreeMap<>();
    }

    public static class SpawnerRule {
        public boolean breakerEnabled = true;
        public boolean tuningEnabled = false;
        public int threshold = 20;
        public int cooldown = 3600;
        public String mode = "COOLDOWN";
        public double minSpawnDelaySeconds = 10.0D;
        public double maxSpawnDelaySeconds = 40.0D;
        public double fixedSpawnDelaySeconds = -1.0D;
        public int minSpawnCount = 4;
        public int maxSpawnCount = 4;
        public int maxNearbyEntities = -1;
        public int requiredPlayerRange = 16;
        public int spawnRange = 4;
        public double speedMultiplier = 1.0D;

        public SpawnerRule() {
        }

        public SpawnerRule(int threshold, int cooldown, String mode) {
            this.threshold = threshold;
            this.cooldown = cooldown;
            this.mode = mode;
        }

        public SpawnerRule copy() {
            SpawnerRule copy = new SpawnerRule();
            copy.breakerEnabled = breakerEnabled;
            copy.tuningEnabled = tuningEnabled;
            copy.threshold = threshold;
            copy.cooldown = cooldown;
            copy.mode = mode;
            copy.minSpawnDelaySeconds = minSpawnDelaySeconds;
            copy.maxSpawnDelaySeconds = maxSpawnDelaySeconds;
            copy.fixedSpawnDelaySeconds = fixedSpawnDelaySeconds;
            copy.minSpawnCount = minSpawnCount;
            copy.maxSpawnCount = maxSpawnCount;
            copy.maxNearbyEntities = maxNearbyEntities;
            copy.requiredPlayerRange = requiredPlayerRange;
            copy.spawnRange = spawnRange;
            copy.speedMultiplier = speedMultiplier;
            return copy;
        }
    }

    public static class SpawnerBackupData {
        public Map<String, BackupEntry> entities = new TreeMap<>();

        public SpawnerBackupData copy() {
            SpawnerBackupData copy = new SpawnerBackupData();
            if (entities != null) {
                for (Map.Entry<String, BackupEntry> entry : entities.entrySet()) {
                    copy.entities.put(
                            entry.getKey(),
                            entry.getValue() == null ? new BackupEntry() : entry.getValue().copy()
                    );
                }
            }
            return copy;
        }
    }

    public static class BackupEntry {
        public boolean hadCustomRule;
        public SpawnerRule rule;

        public BackupEntry copy() {
            BackupEntry copy = new BackupEntry();
            copy.hadCustomRule = hadCustomRule;
            copy.rule = rule == null ? null : rule.copy();
            return copy;
        }
    }

    public static class SpawnerEditorSnapshot {
        public SpawnerData data = new SpawnerData();
        public SpawnerBackupData backup = new SpawnerBackupData();
        public List<String> entityIds = new ArrayList<>();
    }

    public static void load() {
        try {
            Files.createDirectories(BASE_DIR);
            boolean configExisted = Files.exists(CONFIG_PATH);
            boolean backupExisted = Files.exists(BACKUP_PATH);
            data = configExisted ? readData() : new SpawnerData();
            backupData = readBackup();

            normalizeData(data);
            normalizeBackup(backupData);

            boolean changed = false;
            if (!backupExisted && !data.entities.isEmpty()) {
                for (String entityId : data.entities.keySet()) {
                    BackupEntry entry = new BackupEntry();
                    entry.hadCustomRule = false;
                    backupData.entities.putIfAbsent(entityId, entry);
                }
                changed = true;
            }

            changed |= cleanupInvalidEntityRulesInternal(data, backupData);
            refreshRuntimeCaches();
            if (!configExisted || !backupExisted || changed) {
                saveAllInternal();
            }
        } catch (Exception e) {
            SpawnModule.LOGGER.error("SpawnerConfig load failed", e);
            data = new SpawnerData();
            backupData = new SpawnerBackupData();
            normalizeData(data);
            normalizeBackup(backupData);
            refreshRuntimeCaches();
        }
    }

    public static synchronized void save() {
        SpawnerData previousData = copyData(data);
        SpawnerBackupData previousBackup = backupData.copy();
        data.enabled = enableSpawnerBreaker;
        data.notification = spawnerBreakerNotification;
        if (data.global == null) {
            data.global = new SpawnerRule();
        }
        data.global.mode = normalizeMode(spawnerBreakMode);
        data.global.cooldown = clamp(spawnerCooldownSeconds, 0, 604800);
        data.global.threshold = clamp(defaultSpawnerThreshold, 1, 1_000_000);
        normalizeData(data);
        cleanupInvalidEntityRulesInternal(data, backupData);
        refreshRuntimeCaches();
        if (!saveAllInternal()) {
            data = previousData;
            backupData = previousBackup;
            refreshRuntimeCaches();
            throw new IllegalStateException("Failed to save spawner configuration");
        }
    }

    public static synchronized SpawnerData snapshot() {
        return copyData(data);
    }

    public static synchronized SpawnerBackupData backupSnapshot() {
        return backupData.copy();
    }

    public static synchronized SpawnerEditorSnapshot createEditorSnapshot(MinecraftServer server) {
        if (cleanupInvalidEntityRulesInternal(data, backupData)) {
            refreshRuntimeCaches();
            saveAllInternal();
        }

        SpawnerEditorSnapshot snapshot = new SpawnerEditorSnapshot();
        snapshot.data = copyData(data);
        snapshot.backup = backupData.copy();
        snapshot.entityIds = new ArrayList<>(getEditableEntityIds(server));
        return snapshot;
    }

    public static synchronized boolean applyEditorSnapshot(SpawnerData incoming) {
        SpawnerData previousData = data;
        SpawnerBackupData previousBackup = backupData;
        SpawnerData normalizedIncoming = incoming == null ? new SpawnerData() : copyData(incoming);
        SpawnerBackupData candidateBackup = previousBackup.copy();
        normalizeData(normalizedIncoming);
        cleanupInvalidEntityRulesInternal(normalizedIncoming, candidateBackup);
        reconcileBackups(previousData, normalizedIncoming, candidateBackup);
        normalizeBackup(candidateBackup);
        cleanupInvalidEntityRulesInternal(normalizedIncoming, candidateBackup);
        if (!saveAllInternal(normalizedIncoming, candidateBackup)) {
            return false;
        }
        data = normalizedIncoming;
        backupData = candidateBackup;
        refreshRuntimeCaches();
        return true;
    }

    public static SpawnerRule getSpawnerRule(String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            return defaultRule;
        }
        return SPAWNER_RULES_CACHE.getOrDefault(entityId, defaultRule);
    }

    public static SpawnerRule getGlobalRule() {
        return defaultRule;
    }

    public static boolean hasCustomRule(String entityId) {
        return entityId != null && SPAWNER_RULES_CACHE.containsKey(entityId);
    }

    public static boolean isSpawnerBlacklisted(String entityId) {
        return entityId != null && !getSpawnerRule(entityId).breakerEnabled;
    }

    public static synchronized void cleanupInvalidEntityRules() {
        boolean changed = cleanupInvalidEntityRulesInternal(data, backupData);
        if (changed) {
            refreshRuntimeCaches();
            saveAllInternal();
        }
    }

    public static synchronized Set<String> getModifiedEntityIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(MODIFIED_ENTITY_CACHE));
    }

    public static boolean isModifiedEntity(String entityId) {
        return entityId != null && MODIFIED_ENTITY_CACHE.contains(entityId);
    }

    public static SpawnerRule createRuleForEditor(SpawnerData owner) {
        SpawnerData source = owner == null ? new SpawnerData() : owner;
        if (source.global == null) {
            source.global = new SpawnerRule();
        }
        normalizeRule(source.global);
        return source.global.copy();
    }

    public static synchronized List<String> getEditableEntityIds(MinecraftServer server) {
        if (!editableEntityCacheBuilt) {
            ArrayList<String> ids = new ArrayList<>();
            for (Map.Entry<ResourceLocation, EntityType<?>> entry : KineticRegistries.entityTypes().entries().entrySet()) {
                EntityType<?> type = entry.getValue();
                ResourceLocation id = entry.getKey();
                if (type == null || !isEditableEntity(type, server)) {
                    continue;
                }
                ids.add(id.toString());
            }
            ids.sort(String::compareToIgnoreCase);
            editableEntityCache = Collections.unmodifiableList(ids);
            editableEntityCacheBuilt = true;
        }
        return editableEntityCache;
    }

    private static boolean isEditableEntity(EntityType<?> type, MinecraftServer server) {
        if (type.getCategory() != MobCategory.MISC) {
            return true;
        }
        if (server == null) {
            return false;
        }

        try {
            Entity entity = type.create(server.overworld());
            if (entity == null) {
                return false;
            }
            boolean living = entity instanceof LivingEntity;
            entity.discard();
            return living;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SpawnerData readData() {
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            SpawnerData loaded = GSON.fromJson(reader, SpawnerData.class);
            return loaded == null ? new SpawnerData() : loaded;
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to read spawner.json", e);
            return new SpawnerData();
        }
    }

    private static SpawnerBackupData readBackup() {
        if (!Files.exists(BACKUP_PATH)) {
            return new SpawnerBackupData();
        }

        try (Reader reader = Files.newBufferedReader(BACKUP_PATH, StandardCharsets.UTF_8)) {
            SpawnerBackupData loaded = GSON.fromJson(reader, SpawnerBackupData.class);
            return loaded == null ? new SpawnerBackupData() : loaded;
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to read spawner_backup.json", e);
            return new SpawnerBackupData();
        }
    }

    private static void reconcileBackups(
            SpawnerData before,
            SpawnerData after,
            SpawnerBackupData backups
    ) {
        normalizeData(before);
        normalizeData(after);
        normalizeBackup(backups);

        Set<String> allIds = new HashSet<>();
        allIds.addAll(before.entities.keySet());
        allIds.addAll(after.entities.keySet());

        for (String entityId : allIds) {
            SpawnerRule oldRule = before.entities.get(entityId);
            SpawnerRule newRule = after.entities.get(entityId);
            if (sameNullableRule(oldRule, newRule)) {
                continue;
            }

            BackupEntry backup = backups.entities.get(entityId);
            if (backup == null) {
                backup = new BackupEntry();
                backup.hadCustomRule = oldRule != null;
                backup.rule = oldRule == null ? null : oldRule.copy();
                backups.entities.put(entityId, backup);
            }

            if (matchesBackup(newRule, backup)) {
                backups.entities.remove(entityId);
            }
        }
    }

    private static boolean matchesBackup(SpawnerRule current, BackupEntry backup) {
        if (backup == null) {
            return current == null;
        }
        if (!backup.hadCustomRule) {
            return current == null;
        }
        return sameNullableRule(current, backup.rule);
    }

    private static boolean sameNullableRule(SpawnerRule left, SpawnerRule right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.breakerEnabled == right.breakerEnabled
                && left.tuningEnabled == right.tuningEnabled
                && left.threshold == right.threshold
                && left.cooldown == right.cooldown
                && Objects.equals(normalizeMode(left.mode), normalizeMode(right.mode))
                && Double.compare(left.minSpawnDelaySeconds, right.minSpawnDelaySeconds) == 0
                && Double.compare(left.maxSpawnDelaySeconds, right.maxSpawnDelaySeconds) == 0
                && Double.compare(left.fixedSpawnDelaySeconds, right.fixedSpawnDelaySeconds) == 0
                && left.minSpawnCount == right.minSpawnCount
                && left.maxSpawnCount == right.maxSpawnCount
                && left.maxNearbyEntities == right.maxNearbyEntities
                && left.requiredPlayerRange == right.requiredPlayerRange
                && left.spawnRange == right.spawnRange
                && Double.compare(left.speedMultiplier, right.speedMultiplier) == 0;
    }

    private static boolean cleanupInvalidEntityRulesInternal(
            SpawnerData target,
            SpawnerBackupData backups
    ) {
        normalizeData(target);
        normalizeBackup(backups);

        boolean changed = target.entities.entrySet().removeIf(entry -> isRegisteredEntityId(entry.getKey()));
        changed |= backups.entities.entrySet().removeIf(entry -> isRegisteredEntityId(entry.getKey()));
        return changed;
    }

    private static boolean isRegisteredEntityId(String entityId) {
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        return id == null || !KineticRegistries.entityTypes().contains(id);
    }

    private static boolean saveAllInternal() {
        return saveAllInternal(data, backupData);
    }

    private static boolean saveAllInternal(SpawnerData dataToSave, SpawnerBackupData backupToSave) {
        FileState previousConfig = snapshotFile(CONFIG_PATH);
        FileState previousBackupFile = snapshotFile(BACKUP_PATH);
        if (previousConfig == null || previousBackupFile == null) {
            return false;
        }
        if (!writeJsonAtomic(CONFIG_PATH, dataToSave)) {
            return false;
        }
        if (!writeJsonAtomic(BACKUP_PATH, backupToSave)) {
            restoreFile(CONFIG_PATH, previousConfig);
            restoreFile(BACKUP_PATH, previousBackupFile);
            return false;
        }
        return true;
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
            SpawnModule.LOGGER.error("Failed to snapshot {}", path.getFileName(), e);
            return null;
        }
    }

    private static void restoreFile(Path path, FileState state) {
        try {
            if (!state.existed()) {
                Files.deleteIfExists(path);
                return;
            }
            Files.createDirectories(BASE_DIR);
            Path tempPath = path.resolveSibling(path.getFileName() + ".rollback.tmp");
            Files.write(tempPath, state.contents());
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to restore {}", path.getFileName(), e);
        }
    }

    private static boolean writeJsonAtomic(Path path, Object value) {
        try {
            Files.createDirectories(BASE_DIR);
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(
                        tempPath,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (Exception ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            SpawnModule.LOGGER.error("Failed to save {}", path.getFileName(), e);
            return false;
        }
    }

    private static void refreshRuntimeCaches() {
        normalizeData(data);

        enableSpawnerBreaker = data.enabled;
        spawnerBreakMode = data.global.mode;
        spawnerCooldownSeconds = data.global.cooldown;
        defaultSpawnerThreshold = data.global.threshold;
        spawnerBreakerNotification = data.notification;

        SPAWNER_RULES_CACHE.clear();
        SPAWNER_BLACKLIST_CACHE.clear();
        MODIFIED_ENTITY_CACHE.clear();
        MODIFIED_ENTITY_CACHE.addAll(backupData.entities.keySet());
        spawnerRulesRaw = new ArrayList<>();
        spawnerBlacklist = new ArrayList<>();

        for (Map.Entry<String, SpawnerRule> entry : data.entities.entrySet()) {
            SpawnerRule rule = entry.getValue();
            if (rule == null) {
                continue;
            }
            SPAWNER_RULES_CACHE.put(entry.getKey(), rule);
            if (!rule.breakerEnabled) {
                SPAWNER_BLACKLIST_CACHE.add(entry.getKey());
                spawnerBlacklist.add(entry.getKey());
            } else {
                spawnerRulesRaw.add(
                        entry.getKey()
                                + ";" + rule.threshold
                                + ";" + rule.cooldown
                                + ";" + rule.mode
                );
            }
        }

        defaultRule = data.global;
    }

    private static void normalizeData(SpawnerData target) {
        if (target == null) {
            return;
        }
        if (target.global == null) {
            target.global = new SpawnerRule();
        }
        normalizeRule(target.global);
        if (target.entities == null) {
            target.entities = new TreeMap<>();
        }

        TreeMap<String, SpawnerRule> normalized = new TreeMap<>();
        for (Map.Entry<String, SpawnerRule> entry : target.entities.entrySet()) {
            ResourceLocation id = KineticResourceIds.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            SpawnerRule rule = entry.getValue() == null
                    ? target.global.copy()
                    : entry.getValue();
            normalizeRule(rule);
            normalized.put(id.toString(), rule);
        }
        target.entities = normalized;
    }

    private static void normalizeBackup(SpawnerBackupData target) {
        if (target.entities == null) {
            target.entities = new TreeMap<>();
        }

        TreeMap<String, BackupEntry> normalized = new TreeMap<>();
        for (Map.Entry<String, BackupEntry> entry : target.entities.entrySet()) {
            ResourceLocation id = KineticResourceIds.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            BackupEntry backup = entry.getValue() == null ? new BackupEntry() : entry.getValue();
            if (backup.hadCustomRule) {
                if (backup.rule == null) {
                    backup.rule = data.global.copy();
                }
                normalizeRule(backup.rule);
            } else {
                backup.rule = null;
            }
            normalized.put(id.toString(), backup);
        }
        target.entities = normalized;
    }

    private static void normalizeRule(SpawnerRule rule) {
        rule.threshold = clamp(rule.threshold, 1, 1_000_000);
        rule.cooldown = clamp(rule.cooldown, 0, 604800);
        rule.mode = normalizeMode(rule.mode);
        rule.minSpawnDelaySeconds = clamp(rule.minSpawnDelaySeconds, 0.05D, 1638.35D);
        rule.maxSpawnDelaySeconds = clamp(rule.maxSpawnDelaySeconds, 0.05D, 1638.35D);
        if (rule.maxSpawnDelaySeconds < rule.minSpawnDelaySeconds) {
            rule.maxSpawnDelaySeconds = rule.minSpawnDelaySeconds;
        }
        if (rule.fixedSpawnDelaySeconds < 0.0D) {
            rule.fixedSpawnDelaySeconds = -1.0D;
        } else {
            rule.fixedSpawnDelaySeconds = clamp(rule.fixedSpawnDelaySeconds, 0.05D, 1638.35D);
        }
        rule.minSpawnCount = clamp(rule.minSpawnCount, 0, 128);
        rule.maxSpawnCount = clamp(rule.maxSpawnCount, 0, 128);
        if (rule.maxSpawnCount < rule.minSpawnCount) {
            rule.maxSpawnCount = rule.minSpawnCount;
        }
        rule.maxNearbyEntities = clamp(rule.maxNearbyEntities, -1, 1024);
        rule.requiredPlayerRange = clamp(rule.requiredPlayerRange, 0, 256);
        rule.spawnRange = clamp(rule.spawnRange, 0, 128);
        rule.speedMultiplier = clamp(rule.speedMultiplier, 0.01D, 10.0D);
    }

    private static SpawnerData copyData(SpawnerData source) {
        SpawnerData copy = new SpawnerData();
        copy.enabled = source.enabled;
        copy.notification = source.notification;
        copy.global = source.global == null ? new SpawnerRule() : source.global.copy();
        copy.entities = new TreeMap<>();
        if (source.entities != null) {
            for (Map.Entry<String, SpawnerRule> entry : source.entities.entrySet()) {
                copy.entities.put(
                        entry.getKey(),
                        entry.getValue() == null ? createRuleForEditor(source) : entry.getValue().copy()
                );
            }
        }
        return copy;
    }

    private static String normalizeMode(String mode) {
        return "BREAK".equalsIgnoreCase(mode) ? "BREAK" : "COOLDOWN";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
