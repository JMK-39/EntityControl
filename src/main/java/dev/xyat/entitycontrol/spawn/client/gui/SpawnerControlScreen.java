package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl;
import dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SpawnerControlScreen extends KineticPage {
    public static final int V_WIDTH = 640;
    public static final int V_HEIGHT = 360;
    private static final int COLS = 4;
    private static final int CELL_SIZE = 72;
    private static final int GRID_X = 12;
    private static final int GRID_Y = 45;
    private static final int GRID_W = COLS * CELL_SIZE;
    private static final int VISIBLE_ROWS = 4;
    private static final int GRID_H = VISIBLE_ROWS * CELL_SIZE;
    private static final int RX = GRID_X + GRID_W + 12;
    private static final int RW = V_WIDTH - RX - 8;

    private final SpawnerConfig.SpawnerData data;
    private final List<String> allEntityIds;
    private final List<String> displayList = new ArrayList<>();
    private final Map<String, String> searchIndex = new HashMap<>();
    private final Map<String, LocalBaseline> baselines = new HashMap<>();
    private final Set<String> modifiedEntities = new HashSet<>();
    private final EditedEntryTracker<String> editedEntities = new EditedEntryTracker<>();
    private final KineticEntityPreview entityPreview = KineticEntityPreview.create();
    private final EntityGridControl entityGrid = new EntityGridControl();

    private String searchQuery = "";
    private String selectedId;
    private boolean tuningTab = true;
    private boolean globalMode;
    private String lastSavedJson;
    private String pendingSaveJson;
    private long nextSaveRequestId;
    private long pendingSaveRequestId = -1L;
    private List<Component> deferredTooltip;

    public SpawnerControlScreen(SpawnerConfig.SpawnerEditorSnapshot snapshot) {
        super(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.title"));
        SpawnerConfig.SpawnerEditorSnapshot safe = snapshot == null
                ? new SpawnerConfig.SpawnerEditorSnapshot() : snapshot;
        data = safe.data == null ? new SpawnerConfig.SpawnerData() : safe.data;
        SpawnerConfig.SpawnerBackupData backup = safe.backup == null
                ? new SpawnerConfig.SpawnerBackupData() : safe.backup;
        if (data.global == null) data.global = new SpawnerConfig.SpawnerRule();
        if (data.entities == null) data.entities = new java.util.TreeMap<>();
        if (backup.entities == null) backup.entities = new java.util.TreeMap<>();

        allEntityIds = new ArrayList<>(safe.entityIds == null ? List.of() : safe.entityIds);
        if (allEntityIds.isEmpty()) KineticRegistries.entityTypes().ids().forEach(id -> allEntityIds.add(id.toString()));
        allEntityIds.sort(String::compareToIgnoreCase);
        for (Map.Entry<String, SpawnerConfig.BackupEntry> entry : backup.entities.entrySet()) {
            SpawnerConfig.BackupEntry value = entry.getValue();
            if (value == null) continue;
            baselines.put(entry.getKey(), new LocalBaseline(
                    value.hadCustomRule,
                    value.rule == null ? null : value.rule.copy()
            ));
            modifiedEntities.add(entry.getKey());
        }
        editedEntities.refresh(allEntityIds, modifiedEntities::contains);
        buildSearchIndex();
        updateSearch("");
        if (!displayList.isEmpty()) selectedId = displayList.get(0);
        lastSavedJson = SpawnerConfig.GSON.toJson(data);
        configureStandaloneDraft(this::captureSpawnerSnapshot, this::restoreSpawnerSnapshot);
    }

    private record SpawnerSnapshot(String dataJson, Map<String, LocalBaseline> baselines,
                                   Set<String> modifiedEntities, String selectedId,
                                   boolean tuningTab, boolean globalMode) {
    }

    private SpawnerSnapshot captureSpawnerSnapshot() {
        Map<String, LocalBaseline> copy = new HashMap<>();
        baselines.forEach((id, baseline) -> copy.put(id, baseline == null ? null : new LocalBaseline(
                baseline.hadCustomRule(), baseline.rule() == null ? null : baseline.rule().copy()
        )));
        return new SpawnerSnapshot(
                SpawnerConfig.GSON.toJson(data), copy, new HashSet<>(modifiedEntities),
                selectedId, tuningTab, globalMode
        );
    }

    private void restoreSpawnerSnapshot(SpawnerSnapshot snapshot) {
        if (snapshot == null) return;
        SpawnerConfig.SpawnerData restored = SpawnerConfig.GSON.fromJson(snapshot.dataJson(), SpawnerConfig.SpawnerData.class);
        if (restored != null) {
            data.enabled = restored.enabled;
            data.notification = restored.notification;
            data.global = restored.global == null ? new SpawnerConfig.SpawnerRule() : restored.global;
            data.entities.clear();
            if (restored.entities != null) data.entities.putAll(restored.entities);
        }
        baselines.clear();
        snapshot.baselines().forEach((id, baseline) -> baselines.put(id, baseline == null ? null : new LocalBaseline(
                baseline.hadCustomRule(), baseline.rule() == null ? null : baseline.rule().copy()
        )));
        modifiedEntities.clear();
        modifiedEntities.addAll(snapshot.modifiedEntities());
        editedEntities.refresh(allEntityIds, modifiedEntities::contains);
        selectedId = snapshot.selectedId();
        tuningTab = snapshot.tuningTab();
        globalMode = snapshot.globalMode();
        updateSearch(searchQuery);
    }

    @Override
    protected void build(KineticUi ui) {
        if (!globalMode) {
            ui.textField(GRID_X, 15, GRID_W)
                    .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.search_hint"))
                    .placeholder(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.search_hint"))
                    .value(searchQuery).maxLength(256)
                    .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.search"))
                    .onChange(value -> {
                        searchQuery = value == null ? "" : value;
                        updateSearch(searchQuery);
                    }).firstShownTextAsDefault().build();
        }

        int topW = (RW - 15) / 4;
        ui.toggle(RX, 15, topW).compact().value(data.enabled)
                .labels(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.master.on"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.master.off"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.master"))
                .onChange(value -> data.enabled = value).build();
        ui.toggle(RX + topW + 5, 15, topW).compact().value(data.notification)
                .labels(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.notification.on"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.notification.off"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.notification"))
                .onChange(value -> data.notification = value).build();
        ui.button(RX + (topW + 5) * 2, 15, topW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.save"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.save"))
                .onClick(this::saveAndApply).build();
        ui.button(RX + (topW + 5) * 3, 15, topW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.close"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.close"))
                .onClick(this::navigateBack).build();

        ui.tabBar(RX, 40, RW, List.of(
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tab.tuning"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tab.breaker")
                ))
                .selected(tuningTab ? 0 : 1)
                .onSelect(index -> {
                    tuningTab = index == 0;
                    rebuild();
                }).build();

        int actionW = (RW - 5) / 2;
        ui.button(RX, 65, actionW).compact()
                .text(scopeText())
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.scope"))
                .onClick(() -> {
                    globalMode = !globalMode;
                    closeContextMenu();
                    rebuild();
                }).build();
        if (!globalMode) {
            ui.button(RX + actionW + 5, 65, actionW).compact()
                    .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.restore"))
                    .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.restore"))
                    .enabled(selectedId != null && modifiedEntities.contains(selectedId))
                    .onClick(this::restoreSelected).build();
            ui.add(entityGrid);
        }

        SpawnerConfig.SpawnerRule rule = getDisplayRule();
        if (rule == null) return;
        if (tuningTab) buildTuning(ui, rule);
        else buildBreaker(ui, rule);
    }

    private void buildTuning(KineticUi ui, SpawnerConfig.SpawnerRule rule) {
        int half = (RW - 5) / 2;
        ui.toggle(RX, 91, half).compact().value(rule.tuningEnabled)
                .labels(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tuning.on"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tuning.off"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.tuning"))
                .onChange(value -> {
                    getEditableRule().tuningEnabled = value;
                    afterRuleChanged();
                }).build();
        boolean fixed = rule.fixedSpawnDelaySeconds >= 0D;
        ui.toggle(RX + half + 5, 91, half).compact().value(fixed)
                .labels(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.fixed.on"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.fixed.off"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.fixed_toggle"))
                .onChange(value -> {
                    SpawnerConfig.SpawnerRule editable = getEditableRule();
                    editable.fixedSpawnDelaySeconds = value ? editable.minSpawnDelaySeconds : -1D;
                    afterRuleChanged();
                    rebuild();
                }).build();

        number(ui, 120, 0, NumberType.DECIMAL, rule.minSpawnDelaySeconds, 0.05D, 1638.35D,
                "gui.entitycontrol.spawn.spawner.tooltip.min_delay", Field.MIN_DELAY);
        number(ui, 120, 1, NumberType.DECIMAL, rule.maxSpawnDelaySeconds, 0.05D, 1638.35D,
                "gui.entitycontrol.spawn.spawner.tooltip.max_delay", Field.MAX_DELAY);
        if (fixed) {
            number(ui, 159, 0, NumberType.DECIMAL, rule.fixedSpawnDelaySeconds, 0.05D, 1638.35D,
                    "gui.entitycontrol.spawn.spawner.tooltip.fixed_delay", Field.FIXED_DELAY);
        }
        number(ui, 159, 1, NumberType.DECIMAL, rule.speedMultiplier, 0.01D, 10D,
                "gui.entitycontrol.spawn.spawner.tooltip.speed", Field.SPEED);
        number(ui, 198, 0, NumberType.INT, rule.minSpawnCount, 0, 128,
                "gui.entitycontrol.spawn.spawner.tooltip.min_spawn_count", Field.MIN_SPAWN_COUNT);
        number(ui, 198, 1, NumberType.INT, rule.maxSpawnCount, 0, 128,
                "gui.entitycontrol.spawn.spawner.tooltip.max_spawn_count", Field.MAX_SPAWN_COUNT);
        number(ui, 237, 0, NumberType.INT, rule.maxNearbyEntities, -1, 1024,
                "gui.entitycontrol.spawn.spawner.tooltip.max_nearby", Field.MAX_NEARBY);
        number(ui, 237, 1, NumberType.INT, rule.requiredPlayerRange, 0, 256,
                "gui.entitycontrol.spawn.spawner.tooltip.player_range", Field.PLAYER_RANGE);
        number(ui, 276, 0, NumberType.INT, rule.spawnRange, 0, 128,
                "gui.entitycontrol.spawn.spawner.tooltip.spawn_range", Field.SPAWN_RANGE);
    }

    private void buildBreaker(KineticUi ui, SpawnerConfig.SpawnerRule rule) {
        int half = (RW - 5) / 2;
        ui.toggle(RX, 91, half).compact().value(rule.breakerEnabled)
                .labels(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.breaker.on"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.breaker.off"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.breaker"))
                .onChange(value -> {
                    getEditableRule().breakerEnabled = value;
                    afterRuleChanged();
                }).build();
        boolean breakMode = "BREAK".equalsIgnoreCase(rule.mode);
        ui.toggle(RX + half + 5, 91, half).compact().value(breakMode)
                .labels(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.mode.break"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawner.mode.cooldown"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.mode"))
                .onChange(value -> {
                    getEditableRule().mode = value ? "BREAK" : "COOLDOWN";
                    afterRuleChanged();
                }).build();
        number(ui, 120, 0, NumberType.INT, rule.threshold, 1, 1_000_000,
                "gui.entitycontrol.spawn.spawner.tooltip.threshold", Field.THRESHOLD);
        number(ui, 120, 1, NumberType.INT, rule.cooldown, 0, 604800,
                "gui.entitycontrol.spawn.spawner.tooltip.cooldown", Field.COOLDOWN);
    }

    private void number(KineticUi ui, int labelY, int column, NumberType type, Number value,
                        Number min, Number max, String tooltip, Field field) {
        int columnW = (RW - 5) / 2;
        int x = RX + column * (columnW + 5);
        ui.numberField(x, labelY + 11, columnW, type)
                .allowNegative(min != null && min.doubleValue() < 0D)
                .range(min, max).value(value)
                .tooltip(KineticI18n.translatable(tooltip))
                .onChange(raw -> updateField(raw, field)).firstShownTextAsDefault().build();
    }

    private void updateField(String raw, Field field) {
        if (raw == null || raw.isBlank() || "-".equals(raw) || ".".equals(raw)) return;
        SpawnerConfig.SpawnerRule rule = getEditableRule();
        if (rule == null) return;
        try {
            switch (field) {
                case MIN_DELAY -> {
                    double value = Double.parseDouble(raw);
                    if (value < 0.05D || value > 1638.35D) return;
                    rule.minSpawnDelaySeconds = value;
                    if (rule.maxSpawnDelaySeconds < value) rule.maxSpawnDelaySeconds = value;
                }
                case MAX_DELAY -> {
                    double value = Double.parseDouble(raw);
                    if (value < 0.05D || value > 1638.35D) return;
                    rule.maxSpawnDelaySeconds = value;
                    if (rule.minSpawnDelaySeconds > value) rule.minSpawnDelaySeconds = value;
                }
                case FIXED_DELAY -> {
                    double value = Double.parseDouble(raw);
                    if (value < 0.05D || value > 1638.35D) return;
                    rule.fixedSpawnDelaySeconds = value;
                }
                case SPEED -> {
                    double value = Double.parseDouble(raw);
                    if (value < 0.01D || value > 10D) return;
                    rule.speedMultiplier = value;
                }
                case MIN_SPAWN_COUNT -> {
                    int value = Integer.parseInt(raw);
                    if (value < 0 || value > 128) return;
                    rule.minSpawnCount = value;
                    if (rule.maxSpawnCount < value) rule.maxSpawnCount = value;
                }
                case MAX_SPAWN_COUNT -> {
                    int value = Integer.parseInt(raw);
                    if (value < 0 || value > 128) return;
                    rule.maxSpawnCount = value;
                    if (rule.minSpawnCount > value) rule.minSpawnCount = value;
                }
                case MAX_NEARBY -> {
                    int value = Integer.parseInt(raw);
                    if (value < -1 || value > 1024) return;
                    rule.maxNearbyEntities = value;
                }
                case PLAYER_RANGE -> {
                    int value = Integer.parseInt(raw);
                    if (value < 0 || value > 256) return;
                    rule.requiredPlayerRange = value;
                }
                case SPAWN_RANGE -> {
                    int value = Integer.parseInt(raw);
                    if (value < 0 || value > 128) return;
                    rule.spawnRange = value;
                }
                case THRESHOLD -> {
                    int value = Integer.parseInt(raw);
                    if (value < 1 || value > 1_000_000) return;
                    rule.threshold = value;
                }
                case COOLDOWN -> {
                    int value = Integer.parseInt(raw);
                    if (value < 0 || value > 604800) return;
                    rule.cooldown = value;
                }
            }
        } catch (NumberFormatException ignored) {
            return;
        }
        afterRuleChanged();
        if (field == Field.MIN_DELAY || field == Field.MAX_DELAY
                || field == Field.MIN_SPAWN_COUNT || field == Field.MAX_SPAWN_COUNT) rebuild();
    }

    private void buildSearchIndex() {
        searchIndex.clear();
        for (String id : allEntityIds) {
            ResourceLocation location = KineticResourceIds.tryParse(id);
            EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
            String name = type == null ? id : type.getDescription().getString();
            String raw = id + " " + name;
            searchIndex.put(id, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private void updateSearch(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        displayList.clear();
        for (String id : allEntityIds) {
            boolean match;
            if (lower.isEmpty()) match = true;
            else if (lower.startsWith("@")) {
                ResourceLocation location = KineticResourceIds.tryParse(id);
                match = location != null && location.getNamespace().contains(lower.substring(1));
            } else match = KineticSearch.match(searchIndex.getOrDefault(id, id.toLowerCase(Locale.ROOT)), lower);
            if (match) displayList.add(id);
        }
        sortDisplayList();
        entityGrid.resetScroll();
    }

    private void sortDisplayList() {
        displayList.sort(editedEntities.comparator(String::compareToIgnoreCase));
    }

    private void selectEntity(String entityId) {
        selectedId = entityId;
        globalMode = false;
        closeContextMenu();
        clearFocus();
        rebuild();
    }

    private SpawnerConfig.SpawnerRule getEditableRule() {
        if (globalMode) return data.global;
        if (selectedId == null) return null;
        ensureBaseline(selectedId);
        return data.entities.computeIfAbsent(selectedId, key -> SpawnerConfig.createRuleForEditor(data));
    }

    private SpawnerConfig.SpawnerRule getDisplayRule() {
        if (globalMode) return data.global;
        if (selectedId == null) return null;
        SpawnerConfig.SpawnerRule rule = data.entities.get(selectedId);
        return rule == null ? data.global : rule;
    }

    private void ensureBaseline(String entityId) {
        if (entityId == null || baselines.containsKey(entityId)) return;
        SpawnerConfig.SpawnerRule current = data.entities.get(entityId);
        baselines.put(entityId, new LocalBaseline(current != null, current == null ? null : current.copy()));
    }

    private void refreshModifiedState(String entityId) {
        if (entityId == null) return;
        LocalBaseline baseline = baselines.get(entityId);
        if (baseline == null) return;
        SpawnerConfig.SpawnerRule current = data.entities.get(entityId);
        if (!baseline.hadCustomRule() && current != null && sameRule(current, SpawnerConfig.createRuleForEditor(data))) {
            data.entities.remove(entityId);
            current = null;
        }
        boolean modified = differsFromBaseline(current, baseline);
        if (modified) modifiedEntities.add(entityId); else modifiedEntities.remove(entityId);
        editedEntities.update(entityId, modified);
        sortDisplayList();
    }

    private void afterRuleChanged() {
        if (!globalMode) refreshModifiedState(selectedId);
    }

    private boolean differsFromBaseline(SpawnerConfig.SpawnerRule current, LocalBaseline baseline) {
        if (!baseline.hadCustomRule()) return current != null;
        return !sameRule(current, baseline.rule());
    }

    private boolean sameRule(SpawnerConfig.SpawnerRule left, SpawnerConfig.SpawnerRule right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
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

    private String normalizeMode(String mode) {
        return "BREAK".equalsIgnoreCase(mode) ? "BREAK" : "COOLDOWN";
    }

    private void restoreSelected() {
        if (globalMode || selectedId == null) return;
        LocalBaseline baseline = baselines.get(selectedId);
        if (baseline == null) return;
        if (baseline.hadCustomRule() && baseline.rule() != null) data.entities.put(selectedId, baseline.rule().copy());
        else data.entities.remove(selectedId);
        modifiedEntities.remove(selectedId);
        editedEntities.update(selectedId, false);
        baselines.remove(selectedId);
        sortDisplayList();
        KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawner.restored"));
        rebuild();
    }

    private Component scopeText() {
        return KineticI18n.translatable(globalMode
                ? "gui.entitycontrol.spawn.spawner.scope.entity"
                : "gui.entitycontrol.spawn.spawner.scope.global");
    }

    private String getEntityName(String entityId) {
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = id == null ? null : KineticRegistries.entityTypes().get(id);
        return type == null ? entityId : type.getDescription().getString();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        KineticTheme.canvasBackground(graphics, width(), height());
        KineticTheme.panel(graphics, RX - 6, 6, RW + 12, 348);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (globalMode) renderGlobalMode(graphics);
        renderLabels(graphics);
        if (deferredTooltip != null && !deferredTooltip.isEmpty()) showTooltip(deferredTooltip);
    }

    private void renderGlobalMode(KineticGraphics graphics) {
        int center = GRID_X + GRID_W / 2;
        graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.global_mode.title"), center, 78,
                KineticTheme.current().accent(), false);
        graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.global_mode.desc"), center, 110,
                KineticTheme.current().text(), false);
        graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.global_mode.priority"), center, 130,
                KineticTheme.current().mutedText(), false);
        KineticTheme.separator(graphics, GRID_X + 8, 156, GRID_W - 16);
        graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.global_mode.hint"), center, 178,
                KineticTheme.current().translatedText(), false);
    }

    private void renderLabels(KineticGraphics graphics) {
        if (!globalMode && selectedId == null) {
            graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.no_selection"), RX + RW / 2, 170,
                    KineticTheme.current().mutedText(), false);
            return;
        }
        if (tuningTab) {
            label(graphics, "gui.entitycontrol.spawn.spawner.min_delay", 120, 0);
            label(graphics, "gui.entitycontrol.spawn.spawner.max_delay", 120, 1);
            SpawnerConfig.SpawnerRule rule = getDisplayRule();
            if (rule != null && rule.fixedSpawnDelaySeconds >= 0D) label(graphics, "gui.entitycontrol.spawn.spawner.fixed_delay", 159, 0);
            else if (rule != null) graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.fixed.random"), RX, 171, KineticTheme.current().mutedText());
            label(graphics, "gui.entitycontrol.spawn.spawner.speed", 159, 1);
            label(graphics, "gui.entitycontrol.spawn.spawner.min_spawn_count", 198, 0);
            label(graphics, "gui.entitycontrol.spawn.spawner.max_spawn_count", 198, 1);
            label(graphics, "gui.entitycontrol.spawn.spawner.max_nearby", 237, 0);
            label(graphics, "gui.entitycontrol.spawn.spawner.player_range", 237, 1);
            label(graphics, "gui.entitycontrol.spawn.spawner.spawn_range", 276, 0);
        } else {
            label(graphics, "gui.entitycontrol.spawn.spawner.threshold", 120, 0);
            label(graphics, "gui.entitycontrol.spawn.spawner.cooldown", 120, 1);
            graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.breaker.desc"), RX, 171, KineticTheme.current().mutedText());
            graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.backup.desc"), RX, 191, KineticTheme.current().mutedText());
        }
    }

    private void label(KineticGraphics graphics, String key, int y, int column) {
        int columnW = (RW - 5) / 2;
        graphics.text(KineticI18n.translatable(key), RX + column * (columnW + 5), y, KineticTheme.current().mutedText());
    }

    private void saveAndApply() {
        String json = SpawnerConfig.GSON.toJson(data);
        long requestId = ++nextSaveRequestId;
        pendingSaveJson = json;
        pendingSaveRequestId = requestId;
        SpawnNetwork.saveSpawner(json, requestId);
    }

    public void handleSaveResult(long requestId, boolean success) {
        if (requestId != pendingSaveRequestId) return;
        if (success && pendingSaveJson != null) {
            lastSavedJson = pendingSaveJson;
            commitDraft();
        }
        pendingSaveJson = null;
        pendingSaveRequestId = -1L;
    }

    @Override
    protected void onRemoved() {
        entityPreview.clear();
        editedEntities.clear();
    }

    private enum Field {
        MIN_DELAY, MAX_DELAY, FIXED_DELAY, SPEED,
        MIN_SPAWN_COUNT, MAX_SPAWN_COUNT, MAX_NEARBY,
        PLAYER_RANGE, SPAWN_RANGE, THRESHOLD, COOLDOWN
    }

    private record LocalBaseline(boolean hadCustomRule, SpawnerConfig.SpawnerRule rule) {
    }

    private final class EntityGridControl extends KineticCustomControl {
        private final KineticScrollController scroll = new KineticScrollController();

        private EntityGridControl() {
            super(GRID_X - 4, GRID_Y - 4, GRID_W + 16, GRID_H + 8);
            scroll.bindSelection(() -> selectedId == null ? -1 : displayList.indexOf(selectedId),
                    index -> Math.max(0, index / COLS - VISIBLE_ROWS / 2));
        }

        private void resetScroll() {
            scroll.reset();
        }

        private void updateRange() {
            int rows = (displayList.size() + COLS - 1) / COLS;
            scroll.update(rows, VISIBLE_ROWS);
        }

        private int indexAt(double mouseX, double mouseY) {
            if (mouseX < GRID_X || mouseX >= GRID_X + GRID_W || mouseY < GRID_Y || mouseY >= GRID_Y + GRID_H) return -1;
            int column = (int) ((mouseX - GRID_X) / CELL_SIZE);
            int row = (int) Math.floor((mouseY - GRID_Y + scroll.visualShift(CELL_SIZE)) / CELL_SIZE);
            return (scroll.smoothIndexOffset() + row) * COLS + column;
        }

        @Override
        protected void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
            deferredTooltip = null;
            updateRange();
            int firstRow = scroll.smoothIndexOffset();
            int shift = scroll.visualShift(CELL_SIZE);
            int first = firstRow * COLS;
            int last = Math.min(displayList.size(), first + (VISIBLE_ROWS + 1) * COLS);
            graphics.clipped(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H, () -> {
                for (int index = first; index < last; index++) {
                    int local = index - first;
                    int x = GRID_X + local % COLS * CELL_SIZE;
                    int y = GRID_Y + local / COLS * CELL_SIZE - shift;
                    String id = displayList.get(index);
                    boolean selected = id.equals(selectedId);
                    boolean modified = modifiedEntities.contains(id);
                    boolean hovered = mouseX >= GRID_X && mouseX < GRID_X + GRID_W
                            && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H
                            && mouseX >= x && mouseX < x + CELL_SIZE && mouseY >= y && mouseY < y + CELL_SIZE;
                    KineticEntityPreview.drawCheckerboard(graphics, x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);
                    if (selected) {
                        KineticTheme.stateOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, true, false, false);
                    } else if (modified) {
                        KineticTheme.indicatorOutline(graphics, x, y, CELL_SIZE, CELL_SIZE,
                                KineticTheme.Indicator.SUCCESS);
                    } else {
                        KineticTheme.stateOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, false, hovered, false);
                    }
                    String stateKey = "spawner:" + id;
                    boolean rendered = entityPreview.render(graphics, id, stateKey, x + 3, y + 3,
                            CELL_SIZE - 6, CELL_SIZE - 6, hovered);
                    if (!rendered) {
                        graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.question_mark"),
                                x + CELL_SIZE / 2, y + CELL_SIZE / 2 - 4, KineticTheme.current().danger(), false);
                    } else {
                        registerPreviewZoomArea(entityPreview, stateKey, x, Math.max(y, GRID_Y), CELL_SIZE,
                                Math.max(0, Math.min(y + CELL_SIZE, GRID_Y + GRID_H) - Math.max(y, GRID_Y)));
                    }
                    scroll.renderSelectionFlash(graphics, index, x, y, CELL_SIZE, CELL_SIZE);
                    if (hovered) {
                        deferredTooltip = new ArrayList<>();
                        deferredTooltip.add(Component.literal(getEntityName(id)));
                        deferredTooltip.add(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.entity_id", id));
                        deferredTooltip.add(KineticI18n.translatable(modified
                                ? "gui.entitycontrol.spawn.spawner.tooltip.modified"
                                : "gui.entitycontrol.spawn.spawner.tooltip.unmodified"));
                        deferredTooltip.add(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.right_click"));
                        deferredTooltip.add(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.model_zoom",
                                entityPreview.zoomPercent(stateKey)));
                    }
                }
            });
            scroll.render(graphics, mouseX, mouseY, GRID_X + GRID_W + 4, GRID_Y, 4, GRID_H, 20);
        }

        @Override
        protected boolean onMouseClick(MouseInput input) {
            if (scroll.beginDrag(input.x(), input.y(), input.button(), GRID_X + GRID_W + 4, GRID_Y, 4, GRID_H, 20, 3)) return true;
            int index = indexAt(input.x(), input.y());
            if (index < 0 || index >= displayList.size()) return false;
            String id = displayList.get(index);
            if (input.isLeft()) {
                selectEntity(id);
                return true;
            }
            if (input.isRight()) {
                selectedId = id;
                openEntityContextMenu(input.x(), input.y(), id);
                return true;
            }
            return false;
        }

        private void openEntityContextMenu(double x, double y, String entityId) {
            boolean canRestore = modifiedEntities.contains(entityId);
            Component label = KineticI18n.translatable(canRestore
                    ? "gui.entitycontrol.spawn.spawner.context.reset"
                    : "gui.entitycontrol.spawn.spawner.context.no_reset");
            KineticOverlays.MenuItem item = canRestore
                    ? KineticOverlays.MenuItem.action(label, () -> {
                        selectedId = entityId;
                        restoreSelected();
                    })
                    : KineticOverlays.MenuItem.disabled(label);
            openContextMenu(x, y, List.of(item));
        }

        @Override
        protected boolean onMouseDrag(MouseDragInput input) {
            return scroll.drag(input.y(), GRID_Y, GRID_H, 20);
        }

        @Override
        protected boolean onMouseRelease(MouseInput input) {
            return scroll.release(input.button());
        }

        @Override
        protected boolean onMouseScroll(ScrollInput input) {
            return scroll.scroll(input.deltaY());
        }
    }
}
