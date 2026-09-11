package dev.xyat.entitycontrol.spawn.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
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
    private static final int CONTEXT_W = 116;
    private static final int CONTEXT_H = 22;

    private final Screen parent;
    private final SpawnerConfig.SpawnerData data;
    private final List<String> allEntityIds;
    private final List<String> displayList = new ArrayList<>();
    private final Map<String, String> searchIndex = new HashMap<>();
    private final Map<String, LocalBaseline> baselines = new HashMap<>();
    private final Set<String> modifiedEntities = new HashSet<>();
    private final EditedEntryTracker<String> editedEntities = new EditedEntryTracker<>();
    private final GridScrollController gridScroll = new GridScrollController();
    private final EntityPreviewRenderer entityPreviewRenderer = new EntityPreviewRenderer();

    private EditBox searchBox;
    private String selectedId;
    private boolean tuningTab = true;
    private boolean globalMode;
    private boolean updating;
    private String lastSavedJson;
    private String pendingSaveJson;
    private long nextSaveRequestId;
    private long pendingSaveRequestId = -1L;
    private boolean closeAfterPendingSave;

    private Button btnTabTuning;
    private Button btnTabBreaker;
    private Button btnScope;
    private Button btnRestore;
    private Button btnTuning;
    private Button btnFixedDelay;
    private Button btnBreaker;
    private Button btnMode;

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

    private boolean contextMenuOpen;
    private int contextMenuX;
    private int contextMenuY;
    private String contextEntityId;
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
            ForgeRegistries.ENTITY_TYPES.getKeys().forEach(id -> allEntityIds.add(id.toString()));
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
        useCanvas(V_WIDTH, V_HEIGHT, 6);
        scaleMultiplier = 1.0f;
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
        searchBox = addRenderableWidget(new EditBox(
                font,
                GRID_X,
                15,
                GRID_W,
                20,
                Component.empty()
        ));
        searchBox.setResponder(this::updateSearch);
        searchBox.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawner.tooltip.search"
        )));

        int topW = (RW - 15) / 4;
        addRenderableWidget(Button.builder(
                getMasterText(),
                button -> {
                    data.enabled = !data.enabled;
                    button.setMessage(getMasterText());
                }
        ).bounds(RX, 15, topW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.master")
        )).build());

        addRenderableWidget(Button.builder(
                getNotificationText(),
                button -> {
                    data.notification = !data.notification;
                    button.setMessage(getNotificationText());
                }
        ).bounds(RX + topW + 5, 15, topW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.notification")
        )).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.entitycontrol.spawn.spawner.save"),
                button -> saveAndApply(true)
        ).bounds(RX + (topW + 5) * 2, 15, topW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.save")
        )).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.entitycontrol.spawn.spawner.close"),
                button -> onClose()
        ).bounds(RX + (topW + 5) * 3, 15, topW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.close")
        )).build());

        int tabW = (RW - 5) / 2;
        btnTabTuning = addRenderableWidget(Button.builder(
                Component.translatable("gui.entitycontrol.spawn.spawner.tab.tuning"),
                button -> switchTab(true)
        ).bounds(RX, 40, tabW, 20).build());

        btnTabBreaker = addRenderableWidget(Button.builder(
                Component.translatable("gui.entitycontrol.spawn.spawner.tab.breaker"),
                button -> switchTab(false)
        ).bounds(RX + tabW + 5, 40, tabW, 20).build());

        int actionW = (RW - 5) / 2;
        btnScope = addRenderableWidget(Button.builder(
                getScopeText(),
                button -> {
                    globalMode = !globalMode;
                    contextMenuOpen = false;
                    contextEntityId = null;
                    updateControlValues();
                }
        ).bounds(RX, 65, actionW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.scope")
        )).build());

        btnRestore = addRenderableWidget(Button.builder(
                Component.translatable("gui.entitycontrol.spawn.spawner.restore"),
                button -> restoreSelected()
        ).bounds(RX + actionW + 5, 65, actionW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.restore")
        )).build());

        btnTuning = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) {
                        return;
                    }
                    rule.tuningEnabled = !rule.tuningEnabled;
                    afterRuleChanged();
                    updateControlValues();
                }
        ).bounds(RX, 91, tabW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.tuning")
        )).build());

        btnFixedDelay = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) {
                        return;
                    }
                    rule.fixedSpawnDelaySeconds = rule.fixedSpawnDelaySeconds >= 0.0D ? -1.0D : rule.minSpawnDelaySeconds;
                    afterRuleChanged();
                    updateControlValues();
                }
        ).bounds(RX + tabW + 5, 91, tabW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.fixed_toggle")
        )).build());

        btnBreaker = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) {
                        return;
                    }
                    rule.breakerEnabled = !rule.breakerEnabled;
                    afterRuleChanged();
                    updateControlValues();
                }
        ).bounds(RX, 91, tabW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.breaker")
        )).build());

        btnMode = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> {
                    SpawnerConfig.SpawnerRule rule = getEditableRule();
                    if (rule == null) {
                        return;
                    }
                    rule.mode = "BREAK".equalsIgnoreCase(rule.mode) ? "COOLDOWN" : "BREAK";
                    afterRuleChanged();
                    updateControlValues();
                }
        ).bounds(RX + tabW + 5, 91, tabW, 20).tooltip(Tooltip.create(
                Component.translatable("gui.entitycontrol.spawn.spawner.tooltip.mode")
        )).build());

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
        if (!displayList.isEmpty()) {
            updateSelection(displayList.get(0));
        }
        switchTab(true);
    }

    private NumericEditBox addIntegerBox(
            int y,
            int column,
            int min,
            int max,
            String tooltipKey
    ) {
        int columnW = (RW - 5) / 2;
        int x = RX + column * (columnW + 5);
        NumericEditBox box = NumericEditBox.integer(
                font,
                x,
                y + 11,
                columnW,
                18,
                Component.empty(),
                false,
                min,
                max
        );
        box.setMaxLength(7);
        box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return addRenderableWidget(box);
    }

    private NumericEditBox addDecimalBox(
            int y,
            int column,
            Double max,
            String tooltipKey
    ) {
        int columnW = (RW - 5) / 2;
        int x = RX + column * (columnW + 5);
        NumericEditBox box = NumericEditBox.decimal(
                font,
                x,
                y + 11,
                columnW,
                18,
                Component.empty(),
                false,
                null,
                max
        );
        box.setMaxLength(8);
        box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return addRenderableWidget(box);
    }

    private void switchTab(boolean showTuning) {
        tuningTab = showTuning;
        btnTabTuning.active = !showTuning;
        btnTabBreaker.active = showTuning;
        updateControlVisibility();
    }

    private void buildSearchIndex() {
        searchIndex.clear();
        for (String id : allEntityIds) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
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
                    ResourceLocation location = ResourceLocation.tryParse(id);
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
        contextMenuOpen = false;
        if (searchBox != null) {
            searchBox.setFocused(false);
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

        boolean modified = !matchesBaseline(current, baseline);
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

    private boolean matchesBaseline(SpawnerConfig.SpawnerRule current, LocalBaseline baseline) {
        if (!baseline.hadCustomRule) {
            return current == null;
        }
        return sameRule(current, baseline.rule);
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
        GuiOverlay.toast(Component.translatable("msg.entitycontrol.spawn.spawner.restored"));
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
            GuiOverlay.toast(Component.translatable("msg.entitycontrol.spawn.invalid_number"));
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
            GuiOverlay.toast(Component.translatable("msg.entitycontrol.spawn.invalid_number"));
            updateControlValues();
            return;
        }

        double minimum = field == Field.SPEED ? 0.01D : 0.05D;
        double maximum = field == Field.SPEED ? 10.0D : 1638.35D;
        if (value <= 0.0D) {
            return;
        }
        if (value < minimum || value > maximum) {
            GuiOverlay.toast(Component.translatable("msg.entitycontrol.spawn.invalid_number"));
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
            btnScope.setMessage(getScopeText());
        }

        SpawnerConfig.SpawnerRule rule = getDisplayRule();
        updating = true;
        if (rule != null) {
            btnTuning.setMessage(Component.translatable(
                    rule.tuningEnabled
                            ? "gui.entitycontrol.spawn.spawner.tuning.on"
                            : "gui.entitycontrol.spawn.spawner.tuning.off"
            ));
            btnFixedDelay.setMessage(Component.translatable(
                    rule.fixedSpawnDelaySeconds >= 0.0D
                            ? "gui.entitycontrol.spawn.spawner.fixed.on"
                            : "gui.entitycontrol.spawn.spawner.fixed.off"
            ));
            btnBreaker.setMessage(Component.translatable(
                    rule.breakerEnabled
                            ? "gui.entitycontrol.spawn.spawner.breaker.on"
                            : "gui.entitycontrol.spawn.spawner.breaker.off"
            ));
            btnMode.setMessage(Component.translatable(
                    "BREAK".equalsIgnoreCase(rule.mode)
                            ? "gui.entitycontrol.spawn.spawner.mode.break"
                            : "gui.entitycontrol.spawn.spawner.mode.cooldown"
            ));

            boxMinDelay.setValue(NumericEditBox.format(rule.minSpawnDelaySeconds));
            boxMaxDelay.setValue(NumericEditBox.format(rule.maxSpawnDelaySeconds));
            boxFixedDelay.setValue(NumericEditBox.format(
                    rule.fixedSpawnDelaySeconds >= 0.0D
                            ? rule.fixedSpawnDelaySeconds
                            : rule.minSpawnDelaySeconds
            ));
            boxSpeed.setValue(NumericEditBox.format(rule.speedMultiplier));
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
            btnRestore.active = !globalMode
                    && selectedId != null
                    && modifiedEntities.contains(selectedId);
        }
    }

    private void updateControlVisibility() {
        if (btnTuning == null) {
            return;
        }

        if (searchBox != null) {
            searchBox.visible = !globalMode;
            searchBox.active = !globalMode;
            if (globalMode) {
                searchBox.setFocused(false);
            }
        }

        boolean selected = selectedId != null;
        boolean editable = globalMode || selected;
        SpawnerConfig.SpawnerRule rule = getDisplayRule();
        boolean fixedEnabled = rule != null && rule.fixedSpawnDelaySeconds >= 0.0D;

        btnRestore.visible = selected && !globalMode;
        btnRestore.active = selected && !globalMode && modifiedEntities.contains(selectedId);
        btnTuning.visible = editable && tuningTab;
        btnFixedDelay.visible = editable && tuningTab;
        btnBreaker.visible = editable && !tuningTab;
        btnMode.visible = editable && !tuningTab;

        boxMinDelay.visible = editable && tuningTab;
        boxMaxDelay.visible = editable && tuningTab;
        boxFixedDelay.visible = editable && tuningTab && fixedEnabled;
        boxSpeed.visible = editable && tuningTab;
        boxMinSpawnCount.visible = editable && tuningTab;
        boxMaxSpawnCount.visible = editable && tuningTab;
        boxMaxNearby.visible = editable && tuningTab;
        boxPlayerRange.visible = editable && tuningTab;
        boxSpawnRange.visible = editable && tuningTab;
        boxThreshold.visible = editable && !tuningTab;
        boxCooldown.visible = editable && !tuningTab;
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
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        EntityType<?> type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
        return type == null ? entityId : type.getDescription().getString();
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fill(0, 0, V_WIDTH, V_HEIGHT, 0xFA1E1E1E);
        graphics.renderOutline(0, 0, V_WIDTH, V_HEIGHT, 0xFF444444);
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
        renderSearchPlaceholder(
                graphics,
                searchBox,
                "gui.entitycontrol.spawn.spawner.search_hint"
        );

        if (globalMode) {
            renderGlobalModePanel(graphics);
        } else {
            renderGrid(graphics, mouseX, mouseY);
            renderContextMenu(graphics, mouseX, mouseY);
        }

        renderRightPanel(graphics);

    }

    private void renderSearchPlaceholder(
            GuiGraphics graphics,
            EditBox box,
            String translationKey
    ) {
        if (box == null
                || !box.visible
                || !box.getValue().isEmpty()
                || box.isFocused()) {
            return;
        }

        String text = font.plainSubstrByWidth(
                Component.translatable(translationKey).getString(),
                Math.max(0, box.getWidth() - 10)
        );
        graphics.drawString(
                font,
                text,
                box.getX() + 5,
                box.getY() + (box.getHeight() - font.lineHeight) / 2,
                0xFFAAAAAA,
                false
        );
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

        graphics.fill(
                panelX,
                panelY + 78,
                panelX + panelW,
                panelY + 79,
                0xFF555555
        );

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

        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
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

            if (hover) {
                renderThickOutline(graphics, x, y, 0xFF3399FF, 3);
            } else if (selected) {
                renderThickOutline(graphics, x, y, 0xFFFFD700, 3);
            } else if (modified) {
                renderThickOutline(graphics, x, y, 0xFF33DD66, 3);
            } else {
                graphics.renderOutline(
                        x,
                        y,
                        CELL_SIZE,
                        CELL_SIZE,
                        0xFF555555
                );
            }

            boolean rendered = entityPreviewRenderer.render(
                    graphics,
                    entityId,
                    "spawner:" + entityId,
                    x + 3,
                    y + 3,
                    CELL_SIZE - 6,
                    CELL_SIZE - 6,
                    canvasScale,
                    canvasX,
                    canvasY,
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
                graphics.fill(x + 4, y + 4, x + 8, y + 8, 0xFF33DD66);
            }

            if (hover && !contextMenuOpen) {
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
        graphics.disableScissor();

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

    private void renderContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!contextMenuOpen || contextEntityId == null) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        graphics.fill(
                contextMenuX,
                contextMenuY,
                contextMenuX + CONTEXT_W,
                contextMenuY + CONTEXT_H,
                0xFF1C1C1C
        );
        graphics.renderOutline(
                contextMenuX,
                contextMenuY,
                CONTEXT_W,
                CONTEXT_H,
                0xFFFFA500
        );

        boolean canRestore = modifiedEntities.contains(contextEntityId);
        boolean hover = mouseX >= contextMenuX
                && mouseX <= contextMenuX + CONTEXT_W
                && mouseY >= contextMenuY
                && mouseY <= contextMenuY + CONTEXT_H;
        if (hover && canRestore) {
            graphics.fill(
                    contextMenuX + 1,
                    contextMenuY + 1,
                    contextMenuX + CONTEXT_W - 1,
                    contextMenuY + CONTEXT_H - 1,
                    0xFF555555
            );
        }

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        canRestore
                                ? "gui.entitycontrol.spawn.spawner.context.reset"
                                : "gui.entitycontrol.spawn.spawner.context.no_reset"
                ),
                contextMenuX + CONTEXT_W / 2,
                contextMenuY + 7,
                canRestore ? 0xFFFFFF : 0xAAAAAA
        );
        graphics.pose().popPose();
    }

    private void renderThickOutline(
            GuiGraphics graphics,
            int x,
            int y,
            int color,
            int thickness
    ) {
        for (int i = 0; i < thickness; i++) {
            graphics.renderOutline(
                    x + i,
                    y + i,
                    SpawnerControlScreen.CELL_SIZE - i * 2,
                    SpawnerControlScreen.CELL_SIZE - i * 2,
                    color
            );
        }
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (deferredTooltip != null && !deferredTooltip.isEmpty() && !contextMenuOpen) {
            GuiOverlay.requestTooltip(deferredTooltip, mouseX, mouseY);
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenuOpen) {
            boolean inside = mouseX >= contextMenuX
                    && mouseX <= contextMenuX + CONTEXT_W
                    && mouseY >= contextMenuY
                    && mouseY <= contextMenuY + CONTEXT_H;
            if (inside && button == 0 && contextEntityId != null && modifiedEntities.contains(contextEntityId)) {
                updateSelection(contextEntityId);
                restoreSelected();
            }
            contextMenuOpen = false;
            contextEntityId = null;
            return true;
        }

        if (isGridArea(mouseX, mouseY)) {
            int index = getGridIndex(mouseX, mouseY);
            if (index >= 0 && index < displayList.size()) {
                String entityId = displayList.get(index);
                if (button == 1) {
                    updateSelection(entityId);
                    openContextMenu(mouseX, mouseY, entityId);
                    setFocused(null);
                    return true;
                }
                if (button == 0) {
                    updateSelection(entityId);
                    setFocused(null);
                    return true;
                }
            }
        }

        if (button == 0 && gridScroll.beginDrag(
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

    private void openContextMenu(double mouseX, double mouseY, String entityId) {
        contextEntityId = entityId;
        contextMenuX = Math.max(0, Math.min((int) mouseX, V_WIDTH - CONTEXT_W));
        contextMenuY = Math.max(0, Math.min((int) mouseY, V_HEIGHT - CONTEXT_H));
        contextMenuOpen = true;
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
        if (Screen.hasControlDown() && isGridArea(mouseX, mouseY)) {
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

    private void saveAndApply(boolean force) {
        String currentJson = SpawnerConfig.GSON.toJson(data);
        if (!force && Objects.equals(currentJson, lastSavedJson)) {
            return;
        }

        long requestId = ++nextSaveRequestId;
        pendingSaveJson = currentJson;
        pendingSaveRequestId = requestId;
        SpawnNetwork.CHANNEL.sendToServer(
                new SpawnNetwork.SpawnerSavePacket(currentJson, requestId)
        );
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
        if (success && closeAfterPendingSave) {
            closeAfterPendingSave = false;
            returnToParent();
        } else if (!success) {
            closeAfterPendingSave = false;
        }
    }

    private boolean hasUnsavedChanges() {
        return !Objects.equals(SpawnerConfig.GSON.toJson(data), lastSavedJson);
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public void removed() {
        entityPreviewRenderer.clear();
        editedEntities.clear();
        super.removed();
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

    private record LocalBaseline(boolean hadCustomRule, SpawnerConfig.SpawnerRule rule) {
    }
}
