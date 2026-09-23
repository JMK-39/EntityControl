package dev.xyat.entitycontrol.spawn.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.render.KineticEntityPreview.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.state.EditedEntryTracker;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SpawnerControlScreen extends KineticScreen {
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

    private final Screen parent;
    private final SpawnerConfig.SpawnerData data;
    private final List<String> allEntityIds;
    private final List<String> displayList = new ArrayList<>();
    private final Map<String, String> searchIndex = new HashMap<>();
    private final Map<String, LocalBaseline> baselines = new HashMap<>();
    private final Set<String> modifiedEntities = new HashSet<>();
    private final EditedEntryTracker<String> editedEntities = new EditedEntryTracker<>();
    private final GridScrollController gridScroll = new GridScrollController();
    private final EntityPreviewRenderer entityPreviewRenderer = KineticWidgets.createEntityPreviewRenderer();

    private KineticEditBox searchBox;
    private String selectedId;
    private boolean tuningTab = true;
    private boolean globalMode;
    private boolean updating;
    private String lastSavedJson;
    private String pendingSaveJson;
    private long nextSaveRequestId;
    private long pendingSaveRequestId = -1L;

    private StateButton btnTabTuning;
    private StateButton btnTabBreaker;
    private StateButton btnScope;
    private StateButton btnRestore;
    private StateButton btnTuning;
    private StateButton btnFixedDelay;
    private StateButton btnBreaker;
    private StateButton btnMode;

    private NumericEditBox boxMinDelay;
    private NumericEditBox boxMaxDelay;
    private NumericEditBox boxFixedDelay;
    private NumericEditBox boxSpeed;
    private NumericEditBox boxMinSpawnCount;
    private NumericEditBox boxMaxSpawnCount;
    private NumericEditBox boxMaxNearby;
    private NumericEditBox boxPlayerRange;
    private NumericEditBox boxSpawnRange;
    private NumericEditBox boxThreshold;
    private NumericEditBox boxCooldown;

    private List<Component> deferredTooltip;

    public SpawnerControlScreen(SpawnerConfig.SpawnerEditorSnapshot snapshot) {
        this(null, snapshot);
    }

    public SpawnerControlScreen(
            Screen parent,
            SpawnerConfig.SpawnerEditorSnapshot snapshot
    ) {
        super(Component.translatable("gui.entitycontrol.spawn.spawner.title"));
        this.parent = parent;
        setParentScreen(parent);

        SpawnerConfig.SpawnerEditorSnapshot safe = snapshot == null
                ? new SpawnerConfig.SpawnerEditorSnapshot()
                : snapshot;
        data = safe.data == null ? new SpawnerConfig.SpawnerData() : safe.data;
        SpawnerConfig.SpawnerBackupData backupData = safe.backup == null
                ? new SpawnerConfig.SpawnerBackupData()
                : safe.backup;
        if (data.global == null) {
            data.global = new SpawnerConfig.SpawnerRule();
        }
        if (data.entities == null) {
            data.entities = new java.util.TreeMap<>();
        }
        if (backupData.entities == null) {
            backupData.entities = new java.util.TreeMap<>();
        }

        allEntityIds = new ArrayList<>(safe.entityIds == null ? List.of() : safe.entityIds);
        if (allEntityIds.isEmpty()) {
            KineticRegistries.entityTypes().ids().forEach(id -> allEntityIds.add(id.toString()));
        }
        allEntityIds.sort(String::compareToIgnoreCase);

        for (Map.Entry<String, SpawnerConfig.BackupEntry> entry : backupData.entities.entrySet()) {
            SpawnerConfig.BackupEntry backup = entry.getValue();
            if (backup == null) {
                continue;
            }
            baselines.put(
                    entry.getKey(),
                    new LocalBaseline(
                            backup.hadCustomRule,
                            backup.rule == null ? null : backup.rule.copy()
                    )
            );
            modifiedEntities.add(entry.getKey());
        }

        editedEntities.refresh(allEntityIds, modifiedEntities::contains);
        buildSearchIndex();
        lastSavedJson = SpawnerConfig.GSON.toJson(data);
        configureStandaloneDraft(this::captureSpawnerSnapshot, this::restoreSpawnerSnapshot);
    }

    private record SpawnerSnapshot(
            String dataJson,
            Map<String, LocalBaseline> baselines,
            Set<String> modifiedEntities,
            String selectedId,
            boolean tuningTab,
            boolean globalMode
    ) {
    }

    private SpawnerSnapshot captureSpawnerSnapshot() {
        Map<String, LocalBaseline> baselineCopy = new HashMap<>();
        for (Map.Entry<String, LocalBaseline> entry : baselines.entrySet()) {
            LocalBaseline value = entry.getValue();
            baselineCopy.put(
                    entry.getKey(),
                    value == null ? null : new LocalBaseline(
                            value.hadCustomRule(),
                            value.rule() == null ? null : value.rule().copy()
                    )
            );
        }
        return new SpawnerSnapshot(
                SpawnerConfig.GSON.toJson(data),
                baselineCopy,
                new HashSet<>(modifiedEntities),
                selectedId,
                tuningTab,
                globalMode
        );
    }

    private void restoreSpawnerSnapshot(SpawnerSnapshot snapshot) {
        if (snapshot == null) return;
        SpawnerConfig.SpawnerData restored = SpawnerConfig.GSON.fromJson(
                snapshot.dataJson(),
                SpawnerConfig.SpawnerData.class
        );
        if (restored != null) {
            data.enabled = restored.enabled;
            data.notification = restored.notification;
            data.global = restored.global == null ? new SpawnerConfig.SpawnerRule() : restored.global;
            data.entities.clear();
            if (restored.entities != null) data.entities.putAll(restored.entities);
        }

        baselines.clear();
        for (Map.Entry<String, LocalBaseline> entry : snapshot.baselines().entrySet()) {
            LocalBaseline value = entry.getValue();
            baselines.put(
                    entry.getKey(),
                    value == null ? null : new LocalBaseline(
                            value.hadCustomRule(),
                            value.rule() == null ? null : value.rule().copy()
                    )
            );
        }
        modifiedEntities.clear();
        modifiedEntities.addAll(snapshot.modifiedEntities());
        editedEntities.refresh(allEntityIds, modifiedEntities::contains);
        selectedId = snapshot.selectedId();
        tuningTab = snapshot.tuningTab();
        globalMode = snapshot.globalMode();
        updateSearch(searchBox == null ? "" : searchBox.getValue());
    }

    @Override
    protected void buildUi() {
        searchBox = addTextField(
                GRID_X,
                15,
                GRID_W,
                Component.empty(),
                Component.translatable("gui.entitycontrol.spawn.spawner.search_hint"),
                null,
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.search")
        );
        searchBox.setResponder(this::updateSearch);

        int topW = (RW - 15) / 4;
        addButtonWithHandler(
                RX,
                15,
                topW,
                getMasterText(),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.master"),
                button -> {
                    data.enabled = !data.enabled;
                    button.setText(getMasterText());
                }
        );

        addButtonWithHandler(
                RX + topW + 5,
                15,
                topW,
                getNotificationText(),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.notification"),
                button -> {
                    data.notification = !data.notification;
                    button.setText(getNotificationText());
                }
        );

        addButton(
                RX + (topW + 5) * 2,
                15,
                topW,
                Component.translatable("gui.entitycontrol.spawn.spawner.save"),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.save"),
                () -> saveAndApply()
        );

        addButton(
                RX + (topW + 5) * 3,
                15,
                topW,
                Component.translatable("gui.entitycontrol.spawn.spawner.close"),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.close"),
                this::navigateBack
        );

        int tabW = (RW - 5) / 2;
        btnTabTuning = addButton(
                RX,
                40,
                tabW,
                Component.translatable("gui.entitycontrol.spawn.spawner.tab.tuning"),
                null,
                () -> switchTab(true)
        );

        btnTabBreaker = addButton(
                RX + tabW + 5,
                40,
                tabW,
                Component.translatable("gui.entitycontrol.spawn.spawner.tab.breaker"),
                null,
                () -> switchTab(false)
        );

        int actionW = (RW - 5) / 2;
        btnScope = addButton(
                RX,
                65,
                actionW,
                getScopeText(),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.scope"),
                () -> {
                    globalMode = !globalMode;
                    closeContextMenu();
                    updateControlValues();
                }
        );

        btnRestore = addButton(
                RX + actionW + 5,
                65,
                actionW,
                Component.translatable("gui.entitycontrol.spawn.spawner.restore"),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.restore"),
                this::restoreSelected
        );

        btnTuning = addButton(
                RX,
                91,
                tabW,
                Component.empty(),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.tuning"),
                () -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) return;
                    rule.tuningEnabled = !rule.tuningEnabled;
                    afterRuleChanged();
                    updateControlValues();
                }
        );

        btnFixedDelay = addButton(
                RX + tabW + 5,
                91,
                tabW,
                Component.empty(),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.fixed_toggle"),
                () -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) return;
                    rule.fixedSpawnDelaySeconds = rule.fixedSpawnDelaySeconds >= 0.0D
                            ? -1.0D
                            : rule.minSpawnDelaySeconds;
                    afterRuleChanged();
                    updateControlValues();
                }
        );

        btnBreaker = addButton(
                RX,
                91,
                tabW,
                Component.empty(),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.breaker"),
                () -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) return;
                    rule.breakerEnabled = !rule.breakerEnabled;
                    afterRuleChanged();
                    updateControlValues();
                }
        );

        btnMode = addButton(
                RX + tabW + 5,
                91,
                tabW,
                Component.empty(),
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.mode"),
                () -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) return;
                    rule.mode = "BREAK".equalsIgnoreCase(rule.mode) ? "COOLDOWN" : "BREAK";
                    afterRuleChanged();
                    updateControlValues();
                }
        );

        boxMinDelay = addDecimalBox(120, 0, 1638.35D, "gui.entitycontrol.spawn.spawner.tooltip.min_delay");
        boxMaxDelay = addDecimalBox(120, 1, 1638.35D, "gui.entitycontrol.spawn.spawner.tooltip.max_delay");
        boxFixedDelay = addDecimalBox(159, 0, 1638.35D, "gui.entitycontrol.spawn.spawner.tooltip.fixed_delay");
        boxSpeed = addDecimalBox(159, 1, 10.0D, "gui.entitycontrol.spawn.spawner.tooltip.speed");
        boxMinSpawnCount = addIntegerBox(198, 0, 0, 128, "gui.entitycontrol.spawn.spawner.tooltip.min_spawn_count");
        boxMaxSpawnCount = addIntegerBox(198, 1, 0, 128, "gui.entitycontrol.spawn.spawner.tooltip.max_spawn_count");
        boxMaxNearby = addIntegerBox(237, 0, -1, 1024, "gui.entitycontrol.spawn.spawner.tooltip.max_nearby");
        boxPlayerRange = addIntegerBox(237, 1, 0, 256, "gui.entitycontrol.spawn.spawner.tooltip.player_range");
        boxSpawnRange = addIntegerBox(276, 0, 0, 128, "gui.entitycontrol.spawn.spawner.tooltip.spawn_range");

        boxThreshold = addIntegerBox(120, 0, 1, 1_000_000, "gui.entitycontrol.spawn.spawner.tooltip.threshold");
        boxCooldown = addIntegerBox(120, 1, 0, 604800, "gui.entitycontrol.spawn.spawner.tooltip.cooldown");

        boxMinDelay.setResponder(value -> updateDecimalField(boxMinDelay, Field.MIN_DELAY));
        boxMaxDelay.setResponder(value -> updateDecimalField(boxMaxDelay, Field.MAX_DELAY));
        boxFixedDelay.setResponder(value -> updateDecimalField(boxFixedDelay, Field.FIXED_DELAY));
        boxSpeed.setResponder(value -> updateDecimalField(boxSpeed, Field.SPEED));
        boxMinSpawnCount.setResponder(value -> updateIntField(boxMinSpawnCount, Field.MIN_SPAWN_COUNT));
        boxMaxSpawnCount.setResponder(value -> updateIntField(boxMaxSpawnCount, Field.MAX_SPAWN_COUNT));
        boxMaxNearby.setResponder(value -> updateIntField(boxMaxNearby, Field.MAX_NEARBY));
        boxPlayerRange.setResponder(value -> updateIntField(boxPlayerRange, Field.PLAYER_RANGE));
        boxSpawnRange.setResponder(value -> updateIntField(boxSpawnRange, Field.SPAWN_RANGE));
        boxThreshold.setResponder(value -> updateIntField(boxThreshold, Field.THRESHOLD));
        boxCooldown.setResponder(value -> updateIntField(boxCooldown, Field.COOLDOWN));

        updateSearch("");
        if (!displayList.isEmpty()) updateSelection(displayList.get(0));
        switchTab(true);
    }

    private NumericEditBox addIntegerBox(int y, int column, int min, int max, String tooltipKey) {
        int columnW = (RW - 5) / 2;
        int x = RX + column * (columnW + 5);
        NumericEditBox box = addIntegerField(
                x, y + 11, columnW, Component.empty(), false, min, max, null, Component.translatable(tooltipKey)
        );
        box.setMaxLength(7);
        return box;
    }

    private NumericEditBox addDecimalBox(int y, int column, Double max, String tooltipKey) {
        int columnW = (RW - 5) / 2;
        int x = RX + column * (columnW + 5);
        NumericEditBox box = addDecimalField(
                x, y + 11, columnW, Component.empty(), false, null, max, null, Component.translatable(tooltipKey)
        );
        box.setMaxLength(8);
        return box;
    }

    private void switchTab(boolean showTuning) {
        tuningTab = showTuning;
        btnTabTuning.setEnabled(!showTuning);
        btnTabBreaker.setEnabled(showTuning);
        updateControlVisibility();
    }

    private void buildSearchIndex() {
        searchIndex.clear();
        for (String id : allEntityIds) {
            ResourceLocation location = KineticResourceIds.tryParse(id);
            EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
            String name = type == null ? id : type.getDescription().getString();
            String searchData = (
                    id + " "
                            + name + " "
                            + KineticSearch.pinyin(name)
            ).toLowerCase(Locale.ROOT);
            searchIndex.put(id, searchData);
        }
    }

    private void updateSearch(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        displayList.clear();

        if (lower.isEmpty()) {
            displayList.addAll(allEntityIds);
        } else {
            for (String id : allEntityIds) {
                if (lower.startsWith("@")) {
                    ResourceLocation location = KineticResourceIds.tryParse(id);
                    if (location != null && location.getNamespace().contains(lower.substring(1))) {
                        displayList.add(id);
                    }
                    continue;
                }

                String indexed = searchIndex.getOrDefault(id, id.toLowerCase(Locale.ROOT));
                if (KineticSearch.match(indexed, lower)) {
                    displayList.add(id);
                }
            }
        }

        sortDisplayList();
        gridScroll.reset();
        updateGridScrollRange();
    }

    private void sortDisplayList() {
        displayList.sort(editedEntities.comparator(String::compareToIgnoreCase));
    }

    private void updateGridScrollRange() {
        int totalRows = (displayList.size() + COLS - 1) / COLS;
        int maxRows = Math.max(0, totalRows - VISIBLE_ROWS);
        gridScroll.updateRange(maxRows, totalRows, VISIBLE_ROWS);
    }

    private void updateSelection(String entityId) {
        selectedId = entityId;
        globalMode = false;
        closeContextMenu();
        if (searchBox != null) {
            blurControl(searchBox);
        }
        updateControlValues();
    }

    private SpawnerConfig.SpawnerRule getEditableRule() {
        if (globalMode) {
            if (data.global == null) {
                data.global = new SpawnerConfig.SpawnerRule();
            }
            return data.global;
        }
        if (selectedId == null) {
            return null;
        }
        ensureBaseline(selectedId);
        return data.entities.computeIfAbsent(
                selectedId,
                key -> SpawnerConfig.createRuleForEditor(data)
        );
    }

    private SpawnerConfig.SpawnerRule getDisplayRule() {
        if (globalMode) {
            return data.global;
        }
        if (selectedId == null) {
            return null;
        }
        SpawnerConfig.SpawnerRule rule = data.entities.get(selectedId);
        return rule == null ? data.global : rule;
    }

    private void ensureBaseline(String entityId) {
        if (entityId == null || baselines.containsKey(entityId)) {
            return;
        }
        SpawnerConfig.SpawnerRule current = data.entities.get(entityId);
        baselines.put(
                entityId,
                new LocalBaseline(
                        current != null,
                        current == null ? null : current.copy()
                )
        );
    }

    private void refreshModifiedState(String entityId) {
        if (entityId == null) {
            return;
        }
        LocalBaseline baseline = baselines.get(entityId);
        if (baseline == null) {
            return;
        }

        SpawnerConfig.SpawnerRule current = data.entities.get(entityId);
        if (!baseline.hadCustomRule
                && current != null
                && sameRule(current, SpawnerConfig.createRuleForEditor(data))) {
            data.entities.remove(entityId);
            current = null;
        }

        boolean modified = differsFromBaseline(current, baseline);
        boolean changed = modified
                ? modifiedEntities.add(entityId)
                : modifiedEntities.remove(entityId);
        editedEntities.update(entityId, modified);

        if (changed) {
            sortDisplayList();
            updateGridScrollRange();
            if (modified) {
                gridScroll.setOffset(0);
            }
        }
        updateRestoreState();
    }

    private void afterRuleChanged() {
        if (!globalMode) {
            refreshModifiedState(selectedId);
        }
    }

    private boolean differsFromBaseline(SpawnerConfig.SpawnerRule current, LocalBaseline baseline) {
        if (!baseline.hadCustomRule) {
            return current != null;
        }
        return !sameRule(current, baseline.rule);
    }

    private boolean sameRule(SpawnerConfig.SpawnerRule left, SpawnerConfig.SpawnerRule right) {
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

    private String normalizeMode(String mode) {
        return "BREAK".equalsIgnoreCase(mode) ? "BREAK" : "COOLDOWN";
    }

    private void restoreSelected() {
        if (globalMode || selectedId == null) {
            return;
        }
        LocalBaseline baseline = baselines.get(selectedId);
        if (baseline == null) {
            return;
        }

        if (baseline.hadCustomRule && baseline.rule != null) {
            data.entities.put(selectedId, baseline.rule.copy());
        } else {
            data.entities.remove(selectedId);
        }

        modifiedEntities.remove(selectedId);
        editedEntities.update(selectedId, false);
        baselines.remove(selectedId);
        sortDisplayList();
        updateGridScrollRange();
        updateControlValues();
        KineticOverlays.toast(Component.translatable("msg.entitycontrol.spawn.spawner.restored"));
    }

    private void updateIntField(NumericEditBox box, Field field) {
        if (updating || (!globalMode && selectedId == null)) {
            return;
        }
        String raw = box.getValue();
        if (raw.isEmpty()) {
            if (field == Field.MAX_NEARBY) {
                SpawnerConfig.SpawnerRule rule = getEditableRule();
                if (rule != null && rule.maxNearbyEntities != -1) {
                    rule.maxNearbyEntities = -1;
                    afterRuleChanged();
                }
            }
            return;
        }

        Integer value = box.getIntValue();
        if (value == null) {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.spawn.invalid_number"));
            updateControlValues();
            return;
        }

        SpawnerConfig.SpawnerRule rule = getEditableRule();
        if (rule == null) {
            return;
        }

        switch (field) {
            case MIN_SPAWN_COUNT -> {
                rule.minSpawnCount = value;
                if (rule.maxSpawnCount < value) {
                    rule.maxSpawnCount = value;
                }
            }
            case MAX_SPAWN_COUNT -> {
                rule.maxSpawnCount = value;
                if (rule.minSpawnCount > value) {
                    rule.minSpawnCount = value;
                }
            }
            case MAX_NEARBY -> rule.maxNearbyEntities = value;
            case PLAYER_RANGE -> rule.requiredPlayerRange = value;
            case SPAWN_RANGE -> rule.spawnRange = value;
            case THRESHOLD -> rule.threshold = value;
            case COOLDOWN -> rule.cooldown = value;
            default -> {
                return;
            }
        }

        afterRuleChanged();
        if (field == Field.MIN_SPAWN_COUNT || field == Field.MAX_SPAWN_COUNT) {
            updateControlValues();
        }
    }

    private void updateDecimalField(NumericEditBox box, Field field) {
        if (updating || (!globalMode && selectedId == null)) {
            return;
        }

        String raw = box.getValue();
        if (raw.isEmpty() || ".".equals(raw)) {
            return;
        }

        Double value = box.getDoubleValue();
        if (value == null) {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.spawn.invalid_number"));
            updateControlValues();
            return;
        }

        double minimum = field == Field.SPEED ? 0.01D : 0.05D;
        double maximum = field == Field.SPEED ? 10.0D : 1638.35D;
        if (value <= 0.0D) {
            return;
        }
        if (value < minimum || value > maximum) {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.spawn.invalid_number"));
            updateControlValues();
            return;
        }

        SpawnerConfig.SpawnerRule rule = getEditableRule();
        if (rule == null) {
            return;
        }

        switch (field) {
            case MIN_DELAY -> {
                rule.minSpawnDelaySeconds = value;
                if (rule.maxSpawnDelaySeconds < value) {
                    rule.maxSpawnDelaySeconds = value;
                }
            }
            case MAX_DELAY -> {
                rule.maxSpawnDelaySeconds = value;
                if (rule.minSpawnDelaySeconds > value) {
                    rule.minSpawnDelaySeconds = value;
                }
            }
            case FIXED_DELAY -> rule.fixedSpawnDelaySeconds = value;
            case SPEED -> rule.speedMultiplier = value;
            default -> {
                return;
            }
        }

        afterRuleChanged();
        if (field == Field.MIN_DELAY || field == Field.MAX_DELAY) {
            updateControlValues();
        }
    }

    private void updateControlValues() {
        if (btnRestore == null) {
            return;
        }

        if (btnScope != null) {
            btnScope.setText(getScopeText());
        }

        SpawnerConfig.SpawnerRule rule = getDisplayRule();
        updating = true;
        if (rule != null) {
            btnTuning.setText(Component.translatable(
                    rule.tuningEnabled
                            ? "gui.entitycontrol.spawn.spawner.tuning.on"
                            : "gui.entitycontrol.spawn.spawner.tuning.off"
            ));
            btnFixedDelay.setText(Component.translatable(
                    rule.fixedSpawnDelaySeconds >= 0.0D
                            ? "gui.entitycontrol.spawn.spawner.fixed.on"
                            : "gui.entitycontrol.spawn.spawner.fixed.off"
            ));
            btnBreaker.setText(Component.translatable(
                    rule.breakerEnabled
                            ? "gui.entitycontrol.spawn.spawner.breaker.on"
                            : "gui.entitycontrol.spawn.spawner.breaker.off"
            ));
            btnMode.setText(Component.translatable(
                    "BREAK".equalsIgnoreCase(rule.mode)
                            ? "gui.entitycontrol.spawn.spawner.mode.break"
                            : "gui.entitycontrol.spawn.spawner.mode.cooldown"
            ));

            boxMinDelay.setValue(KineticNumericFields.formatDecimal(rule.minSpawnDelaySeconds));
            boxMaxDelay.setValue(KineticNumericFields.formatDecimal(rule.maxSpawnDelaySeconds));
            boxFixedDelay.setValue(KineticNumericFields.formatDecimal(
                    rule.fixedSpawnDelaySeconds >= 0.0D
                            ? rule.fixedSpawnDelaySeconds
                            : rule.minSpawnDelaySeconds
            ));
            boxSpeed.setValue(KineticNumericFields.formatDecimal(rule.speedMultiplier));
            boxMinSpawnCount.setIntValue(rule.minSpawnCount);
            boxMaxSpawnCount.setIntValue(rule.maxSpawnCount);
            if (rule.maxNearbyEntities < 0) {
                boxMaxNearby.setValue("");
            } else {
                boxMaxNearby.setIntValue(rule.maxNearbyEntities);
            }
            boxPlayerRange.setIntValue(rule.requiredPlayerRange);
            boxSpawnRange.setIntValue(rule.spawnRange);
            boxThreshold.setIntValue(rule.threshold);
            boxCooldown.setIntValue(rule.cooldown);
        }
        updating = false;
        updateRestoreState();
        updateControlVisibility();
    }

    private void updateRestoreState() {
        if (btnRestore != null) {
            btnRestore.setEnabled(!globalMode
                    && selectedId != null
                    && modifiedEntities.contains(selectedId));
        }
    }

    private void updateControlVisibility() {
        if (btnTuning == null) {
            return;
        }

        if (searchBox != null) {
            searchBox.setVisible(!globalMode);
            searchBox.setEnabled(!globalMode);
            if (globalMode) blurControl(searchBox);
        }

        boolean selected = selectedId != null;
        boolean editable = globalMode || selected;
        SpawnerConfig.SpawnerRule rule = getDisplayRule();
        boolean fixedEnabled = rule != null && rule.fixedSpawnDelaySeconds >= 0.0D;

        btnRestore.setVisible(selected && !globalMode);
        btnRestore.setEnabled(selected && !globalMode && modifiedEntities.contains(selectedId));
        btnTuning.setVisible(editable && tuningTab);
        btnFixedDelay.setVisible(editable && tuningTab);
        btnBreaker.setVisible(editable && !tuningTab);
        btnMode.setVisible(editable && !tuningTab);

        boxMinDelay.setVisible(editable && tuningTab);
        boxMaxDelay.setVisible(editable && tuningTab);
        boxFixedDelay.setVisible(editable && tuningTab && fixedEnabled);
        boxSpeed.setVisible(editable && tuningTab);
        boxMinSpawnCount.setVisible(editable && tuningTab);
        boxMaxSpawnCount.setVisible(editable && tuningTab);
        boxMaxNearby.setVisible(editable && tuningTab);
        boxPlayerRange.setVisible(editable && tuningTab);
        boxSpawnRange.setVisible(editable && tuningTab);
        boxThreshold.setVisible(editable && !tuningTab);
        boxCooldown.setVisible(editable && !tuningTab);
    }

    private Component getScopeText() {
        return Component.translatable(
                globalMode
                        ? "gui.entitycontrol.spawn.spawner.scope.entity"
                        : "gui.entitycontrol.spawn.spawner.scope.global"
        );
    }

    private Component getMasterText() {
        return Component.translatable(
                data.enabled
                        ? "gui.entitycontrol.spawn.spawner.master.on"
                        : "gui.entitycontrol.spawn.spawner.master.off"
        );
    }

    private Component getNotificationText() {
        return Component.translatable(
                data.notification
                        ? "gui.entitycontrol.spawn.spawner.notification.on"
                        : "gui.entitycontrol.spawn.spawner.notification.off"
        );
    }

    private String getEntityName(String entityId) {
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = id == null ? null : KineticRegistries.entityTypes().get(id);
        return type == null ? entityId : type.getDescription().getString();
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.canvasBackground(graphics, V_WIDTH, V_HEIGHT);
        GuiTheme.stateOutline(graphics, 0, 0, V_WIDTH, V_HEIGHT, false, false, false);
        GuiTheme.panelAlt(
                graphics,
                GRID_X - 2,
                GRID_Y - 2,
                GRID_W + 4,
                GRID_H + 4
        );
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        deferredTooltip = null;
        if (globalMode) {
            renderGlobalModePanel(graphics);
        } else {
            renderGrid(graphics, mouseX, mouseY);
        }

        renderRightPanel(graphics);

    }

    private void renderGlobalModePanel(GuiGraphics graphics) {
        int panelX = GRID_X + 8;
        int panelY = GRID_Y + 24;
        int panelW = GRID_W - 16;

        graphics.drawCenteredString(
                font,
                Component.translatable("gui.entitycontrol.spawn.spawner.global_mode.title"),
                GRID_X + GRID_W / 2,
                panelY,
                0xFF55FFFF
        );

        graphics.drawCenteredString(
                font,
                Component.translatable("gui.entitycontrol.spawn.spawner.global_mode.desc"),
                GRID_X + GRID_W / 2,
                panelY + 32,
                0xFFFFFFFF
        );

        graphics.drawCenteredString(
                font,
                Component.translatable("gui.entitycontrol.spawn.spawner.global_mode.priority"),
                GRID_X + GRID_W / 2,
                panelY + 52,
                0xFFFFAA00
        );

        GuiTheme.separator(graphics, panelX, panelY + 78, panelW);

        graphics.drawCenteredString(
                font,
                Component.translatable("gui.entitycontrol.spawn.spawner.global_mode.hint"),
                GRID_X + GRID_W / 2,
                panelY + 98,
                0xFF55FF55
        );
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int baseRow = gridScroll.smoothIndexOffset();
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int startIndex = baseRow * COLS;
        int endIndex = Math.min(startIndex + (VISIBLE_ROWS + 2) * COLS, displayList.size());

        enableUiScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int i = startIndex; i < endIndex; i++) {
            int relative = i - startIndex;
            int x = GRID_X + relative % COLS * CELL_SIZE;
            int y = GRID_Y + relative / COLS * CELL_SIZE - visualShift;
            String entityId = displayList.get(i);
            boolean selected = entityId.equals(selectedId);
            boolean modified = modifiedEntities.contains(entityId);
            boolean hover = mouseX >= x && mouseX < x + CELL_SIZE
                    && mouseY >= y && mouseY < y + CELL_SIZE;

            EntityPreviewRenderer.drawCheckerboard(
                    graphics,
                    x + 1,
                    y + 1,
                    CELL_SIZE - 2,
                    CELL_SIZE - 2
            );

            if (hover || selected) {
                GuiTheme.stateOutline(
                        graphics, x, y, CELL_SIZE, CELL_SIZE,
                        selected, hover, false, 3
                );
            } else if (modified) {
                GuiTheme.indicatorOutline(
                        graphics, x, y, CELL_SIZE, CELL_SIZE,
                        GuiTheme.Indicator.SUCCESS, 3
                );
            } else {
                GuiTheme.stateOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, false, false, false);
            }

            boolean rendered = entityPreviewRenderer.renderCanvas(
                    graphics,
                    entityId,
                    "spawner:" + entityId,
                    x + 3,
                    y + 3,
                    CELL_SIZE - 6,
                    CELL_SIZE - 6,
                    hover
            );
            if (!rendered) {
                graphics.drawCenteredString(
                        font,
                        Component.literal("?"),
                        x + CELL_SIZE / 2,
                        y + CELL_SIZE / 2 - 4,
                        0xFF5555
                );
            }

            if (modified) {
                GuiTheme.indicatorFill(graphics, x + 4, y + 4, 4, 4, GuiTheme.Indicator.SUCCESS);
            }

            if (hover) {
                ArrayList<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(getEntityName(entityId)));
                tooltip.add(Component.translatable(
                        "gui.entitycontrol.spawn.spawner.tooltip.entity_id",
                        Component.literal(entityId).withStyle(ChatFormatting.AQUA)
                ));
                tooltip.add(Component.translatable(
                        modified
                                ? "gui.entitycontrol.spawn.spawner.tooltip.modified"
                                : "gui.entitycontrol.spawn.spawner.tooltip.unmodified"
                ));
                tooltip.add(Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.right_click"));
                tooltip.add(Component.translatable(
                        "gui.entitycontrol.spawn.spawn.tooltip.model_zoom",
                        Component.literal(String.valueOf(entityPreviewRenderer.getZoomPercent("spawner:" + entityId))).withStyle(ChatFormatting.YELLOW)
                ));
                deferredTooltip = tooltip;
            }
        }
        disableUiScissor(graphics);

        GuiTheme.scrollbar(
                gridScroll,
                graphics,
                mouseX,
                mouseY,
                GRID_X + GRID_W + 4,
                GRID_Y,
                4,
                GRID_H,
                20
        );
    }

    private void renderRightPanel(GuiGraphics graphics) {
        if (!globalMode && selectedId == null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawner.no_selection"),
                    RX + RW / 2,
                    170,
                    0xAAAAAA
            );
            return;
        }

        if (tuningTab) {
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.min_delay", 120, 0);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.max_delay", 120, 1);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.fixed_delay", 159, 0);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.speed", 159, 1);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.min_spawn_count", 198, 0);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.max_spawn_count", 198, 1);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.max_nearby", 237, 0);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.player_range", 237, 1);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.spawn_range", 276, 0);

            SpawnerConfig.SpawnerRule rule = getDisplayRule();
            if (rule != null && rule.fixedSpawnDelaySeconds < 0.0D) {
                graphics.drawString(
                        font,
                        Component.translatable("gui.entitycontrol.spawn.spawner.fixed.random"),
                        RX + 5,
                        174,
                        0xAAAAAA
                );
            }
        } else {
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.threshold", 120, 0);
            renderFieldLabel(graphics, "gui.entitycontrol.spawn.spawner.cooldown", 120, 1);
            graphics.drawString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawner.breaker.desc"),
                    RX,
                    171,
                    0xAAAAAA
            );
            graphics.drawString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawner.backup.desc"),
                    RX,
                    191,
                    0xAAAAAA
            );
        }
    }

    private void renderFieldLabel(GuiGraphics graphics, String key, int y, int column) {
        int columnW = (RW - 5) / 2;
        int x = RX + column * (columnW + 5);
        graphics.drawString(font, Component.translatable(key), x, y, 0xAAAAAA);
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (deferredTooltip != null && !deferredTooltip.isEmpty()) {
            KineticOverlays.requestTooltip(deferredTooltip, mouseX, mouseY);
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (isGridArea(mouseX, mouseY)) {
            int index = getGridIndex(mouseX, mouseY);
            if (index >= 0 && index < displayList.size()) {
                String entityId = displayList.get(index);
                if (KineticMouseButtons.isSecondary(button)) {
                    updateSelection(entityId);
                    openEntityContextMenu(mouseX, mouseY, entityId);
                    clearControlFocus();
                    return true;
                }
                if (KineticMouseButtons.isPrimary(button)) {
                    updateSelection(entityId);
                    clearControlFocus();
                    return true;
                }
            }
        }

        if (KineticMouseButtons.isPrimary(button) && gridScroll.beginDrag(
                mouseX,
                mouseY,
                GRID_X + GRID_W + 4,
                GRID_Y,
                4,
                GRID_H,
                20,
                2
        )) {
            return true;
        }

        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    private void openEntityContextMenu(double mouseX, double mouseY, String entityId) {
        boolean canRestore = modifiedEntities.contains(entityId);
        Component label = Component.translatable(
                canRestore
                        ? "gui.entitycontrol.spawn.spawner.context.reset"
                        : "gui.entitycontrol.spawn.spawner.context.no_reset"
        );
        KineticOverlays.MenuItem item = canRestore
                ? KineticOverlays.MenuItem.action(label, () -> {
                    updateSelection(entityId);
                    restoreSelected();
                })
                : KineticOverlays.MenuItem.disabled(label);
        openContextMenu(mouseX, mouseY, List.of(item));
    }

    private boolean isGridArea(double mouseX, double mouseY) {
        return !globalMode
                && mouseX >= GRID_X
                && mouseX < GRID_X + GRID_W
                && mouseY >= GRID_Y
                && mouseY < GRID_Y + GRID_H;
    }

    private int getGridIndex(double mouseX, double mouseY) {
        int column = (int) ((mouseX - GRID_X) / CELL_SIZE);
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int row = (int) Math.floor((mouseY - GRID_Y + visualShift) / CELL_SIZE);
        return gridScroll.smoothIndexOffset() * COLS + row * COLS + column;
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (!globalMode && gridScroll.drag(mouseY, GRID_Y, GRID_H, 20)) {
            return true;
        }
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        if (!globalMode && gridScroll.release(button)) {
            return true;
        }
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (KineticClientRuntime.controlModifierDown() && isGridArea(mouseX, mouseY)) {
            int index = getGridIndex(mouseX, mouseY);
            if (index >= 0 && index < displayList.size()) {
                entityPreviewRenderer.adjustZoom(
                        "spawner:" + displayList.get(index),
                        delta
                );
                return true;
            }
        }

        if (isGridArea(mouseX, mouseY) && gridScroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private void saveAndApply() {
        String currentJson = SpawnerConfig.GSON.toJson(data);

        long requestId = ++nextSaveRequestId;
        pendingSaveJson = currentJson;
        pendingSaveRequestId = requestId;
        SpawnNetwork.saveSpawner(currentJson, requestId);
    }

    public void handleSaveResult(long requestId, boolean success) {
        if (requestId != pendingSaveRequestId) {
            return;
        }
        if (success && pendingSaveJson != null) {
            lastSavedJson = pendingSaveJson;
            commitDraft();
        }
        pendingSaveJson = null;
        pendingSaveRequestId = -1L;
    }

    private boolean hasUnsavedChanges() {
        return !Objects.equals(SpawnerConfig.GSON.toJson(data), lastSavedJson);
    }

    @Override
    protected boolean handleCloseRequest() {
        return false;
    }

    @Override
    protected void screenRemoved() {
        entityPreviewRenderer.clear();
        editedEntities.clear();
    }

    private enum Field {
        MIN_DELAY,
        MAX_DELAY,
        FIXED_DELAY,
        SPEED,
        MIN_SPAWN_COUNT,
        MAX_SPAWN_COUNT,
        MAX_NEARBY,
        PLAYER_RANGE,
        SPAWN_RANGE,
        THRESHOLD,
        COOLDOWN
    }

    public Screen getParentScreen() {
        return parent;
    }

    private record LocalBaseline(boolean hadCustomRule, SpawnerConfig.SpawnerRule rule) {
    }
}
