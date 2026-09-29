package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
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
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class SpawnControlScreen extends KineticPage {
    public static final int V_WIDTH = 640;
    public static final int V_HEIGHT = 360;

    private static final int DEFAULT_ROTATION_SPEED_PERCENT = 100;
    private static final int MIN_ROTATION_SPEED_PERCENT = 0;
    private static final int MAX_ROTATION_SPEED_PERCENT = 500;
    private static final int COLS = 4;
    private static final int CELL_SIZE = 72;
    private static final int CELL_GAP = 2;
    private static final int CELL_PITCH = CELL_SIZE + CELL_GAP;
    private static final int GRID_X = 12;
    private static final int GRID_Y = 45;
    private static final int GRID_W = COLS * CELL_SIZE + (COLS - 1) * CELL_GAP;
    private static final int VISIBLE_ROWS = 4;
    private static final int GRID_H = VISIBLE_ROWS * CELL_SIZE + (VISIBLE_ROWS - 1) * CELL_GAP;
    private static final int RIGHT_X = GRID_X + GRID_W + 12;
    private static final int RIGHT_W = V_WIDTH - RIGHT_X - 8;

    public BiomeSpawnConfig.GlobalSettings globals;
    public BiomeSpawnConfig.ConfigProfile profile;
    public int currentEditIndex;

    private BiomeSpawnConfig.ConfigProfile backupProfile = new BiomeSpawnConfig.ConfigProfile();
    private final List<String> displayList = new ArrayList<>();
    private final Map<String, String> entityNameCache = new HashMap<>();
    private final Map<String, String> entitySearchDataCache = new HashMap<>();
    private final EditedEntryTracker<String> editedEntities = new EditedEntryTracker<>();
    private final KineticEntityPreview entityPreview = KineticEntityPreview.create();
    private final EntityGridControl entityGrid = new EntityGridControl();
    private final RuleTabModule ruleTab;
    private final BiomeTabModule biomeTab;
    private final CategoryTabModule categoryTab;

    private KineticTextField searchBox;
    private KineticTextField invalidEntityIdBox;
    private KineticNumberField rotationSpeedBox;
    private KineticNumberField ruleMinDistanceField;
    private KineticNumberField ruleMaxDistanceField;
    private KineticNumberField ruleMinHeightField;
    private KineticNumberField ruleMaxHeightField;
    private KineticNumberField ruleMinLightField;
    private KineticNumberField ruleMaxLightField;
    private String searchQuery = "";
    private String selectedId;
    private int activeTab;
    private int rotationSpeedPercent = DEFAULT_ROTATION_SPEED_PERCENT;
    private boolean clockwiseRotation = true;
    private List<Component> deferredTooltip;

    public SpawnControlScreen(BiomeSpawnConfig.GlobalSettings globals,
                              BiomeSpawnConfig.ConfigProfile profile,
                              int editIndex) {
        super(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.title"));
        this.globals = globals == null ? new BiomeSpawnConfig.GlobalSettings() : globals;
        this.profile = profile == null ? new BiomeSpawnConfig.ConfigProfile() : profile;
        this.currentEditIndex = editIndex;
        this.ruleTab = new RuleTabModule(this);
        this.biomeTab = new BiomeTabModule(this);
        this.categoryTab = new CategoryTabModule(this);
        refreshAllEditedEntities();
        updateSearch("");
        if (!displayList.isEmpty()) selectedId = displayList.get(0);
        configureStandaloneDraft(this::captureSpawnSnapshot, this::restoreSpawnSnapshot);
    }

    public void applySync(BiomeSpawnConfig.GlobalSettings globals,
                          BiomeSpawnConfig.ConfigProfile profile, int editIndex) {
        this.globals = globals == null ? new BiomeSpawnConfig.GlobalSettings() : globals;
        this.profile = profile == null ? new BiomeSpawnConfig.ConfigProfile() : profile;
        this.currentEditIndex = editIndex;
        backupProfile = new BiomeSpawnConfig.ConfigProfile();
        searchQuery = "";
        selectedId = null;
        activeTab = 0;
        entityNameCache.clear();
        entitySearchDataCache.clear();
        refreshAllEditedEntities();
        updateSearch("");
        if (!displayList.isEmpty()) selectedId = displayList.get(0);
        commitDraft();
        rebuild();
    }

    private record SpawnSnapshot(String globalsJson, String profileJson) {
    }

    private SpawnSnapshot captureSpawnSnapshot() {
        return new SpawnSnapshot(
                BiomeSpawnConfig.GSON.toJson(globals),
                BiomeSpawnConfig.GSON.toJson(profile)
        );
    }

    private void restoreSpawnSnapshot(SpawnSnapshot snapshot) {
        if (snapshot == null) return;
        BiomeSpawnConfig.GlobalSettings restoredGlobals = BiomeSpawnConfig.GSON.fromJson(
                snapshot.globalsJson(), BiomeSpawnConfig.GlobalSettings.class
        );
        if (restoredGlobals != null) {
            globals.enable_rule_override = restoredGlobals.enable_rule_override;
            globals.enable_biome_override = restoredGlobals.enable_biome_override;
            globals.auto_scan = restoredGlobals.auto_scan;
            globals.config_amount = restoredGlobals.config_amount;
            globals.current_index = restoredGlobals.current_index;
        }
        BiomeSpawnConfig.ConfigProfile restoredProfile = BiomeSpawnConfig.GSON.fromJson(
                snapshot.profileJson(), BiomeSpawnConfig.ConfigProfile.class
        );
        if (restoredProfile != null) {
            profile.category_caps.clear();
            if (restoredProfile.category_caps != null) profile.category_caps.putAll(restoredProfile.category_caps);
            profile.category_weights.clear();
            if (restoredProfile.category_weights != null) profile.category_weights.putAll(restoredProfile.category_weights);
            profile.category_spawn_rates.clear();
            if (restoredProfile.category_spawn_rates != null) profile.category_spawn_rates.putAll(restoredProfile.category_spawn_rates);
            profile.entities.clear();
            if (restoredProfile.entities != null) profile.entities.putAll(restoredProfile.entities);
        }
        refreshAllEditedEntities();
        updateSearch(searchQuery);
        if (selectedId != null && !profile.entities.containsKey(selectedId)) {
            selectedId = displayList.isEmpty() ? null : displayList.get(0);
        }
    }

    @Override
    protected void build(KineticUi ui) {
        int saveButtonW = 70;
        int exitButtonW = 46;
        int gap = 5;
        int searchW = GRID_W - saveButtonW - exitButtonW - gap * 2;
        int tabBtnW = (RIGHT_W - 10) / 3;

        searchBox = ui.textField(GRID_X, 15, searchW)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.search_hint"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.search_hint"))
                .value(searchQuery).maxLength(256)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.search"))
                .onChange(value -> {
                    searchQuery = value == null ? "" : value;
                    updateSearch(searchQuery);
                }).firstShownTextAsDefault().build();
        ui.button(GRID_X + searchW + gap, 15, saveButtonW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.save"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.save"))
                .onClick(this::save).build();
        ui.button(GRID_X + searchW + gap + saveButtonW + gap, 15, exitButtonW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.exit"))
                .onClick(this::navigateBack).build();

        ui.toggle(RIGHT_X, 15, tabBtnW).compact()
                .value(globals.enable_rule_override)
                .labels(ruleOverrideText(true), ruleOverrideText(false))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.rule.override"))
                .onChange(value -> globals.enable_rule_override = value).build();
        ui.toggle(RIGHT_X + tabBtnW + 5, 15, tabBtnW).compact()
                .value(globals.enable_biome_override)
                .labels(biomeOverrideText(true), biomeOverrideText(false))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.biome.override"))
                .onChange(value -> globals.enable_biome_override = value).build();
        ui.button(RIGHT_X + (tabBtnW + 5) * 2, 15, tabBtnW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.profile_btn_numbered", currentEditIndex))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.profile"))
                .onClick(() -> openChild(new SpawnProfilePage(globals, currentEditIndex))).build();

        ui.tabBar(RIGHT_X, 40, RIGHT_W, List.of(
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tab.rules"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tab.biomes"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tab.categories")
                ))
                .tooltips(List.of(
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.tab.rules"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.tab.biomes"),
                        KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.tab.cats")
                ))
                .selected(activeTab)
                .onSelect(index -> {
                    if (index == activeTab) return;
                    activeTab = Math.max(0, Math.min(2, index));
                    currentModule().updateSelection();
                    rebuild();
                }).build();

        ui.toggle(RIGHT_X, 65, tabBtnW).compact()
                .value(globals.auto_scan)
                .labels(autoScanText(true), autoScanText(false))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.autoscan"))
                .onChange(value -> {
                    globals.auto_scan = value;
                    if (value) SpawnNetwork.refreshSpawnBackup(currentEditIndex);
                }).build();
        ui.button(RIGHT_X + tabBtnW + 5, 65, tabBtnW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.restore"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.restore"))
                .enabled(canRestoreSelected())
                .onClick(this::restoreSelectedEntity).build();

        int rotationX = RIGHT_X + (tabBtnW + 5) * 2;
        int directionW = Math.max(58, tabBtnW - 42);
        ui.button(rotationX, 65, directionW).compact()
                .text(rotationDirectionText())
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.rotation.direction.tooltip"))
                .onClick(() -> {
                    clockwiseRotation = !clockwiseRotation;
                    entityPreview.setClockwise(clockwiseRotation);
                    rebuild();
                }).build();
        rotationSpeedBox = ui.numberField(rotationX + directionW + 4, 65, Math.max(30, tabBtnW - directionW - 4), NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.rotation_speed"))
                .allowNegative(false).range(MIN_ROTATION_SPEED_PERCENT, MAX_ROTATION_SPEED_PERCENT)
                .value(rotationSpeedPercent)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.rotation_speed"))
                .onChange(this::applyRotationSpeed).firstShownTextAsDefault().build();

        ui.add(entityGrid);

        if (isSelectedEntityInvalid()) {
            buildInvalidEntityEditor(ui);
        } else if (activeTab == 2 || selectedNode() != null) {
            currentModule().build(ui);
        }
    }

    private void buildInvalidEntityEditor(KineticUi ui) {
        invalidEntityIdBox = ui.textField(RIGHT_X, 116, RIGHT_W)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.id"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.id_hint"))
                .value(selectedId).maxLength(256)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.id.tooltip"))
                .firstShownTextAsDefault().build();
        int actionW = (RIGHT_W - 5) / 2;
        ui.button(RIGHT_X, 141, actionW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.repair"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.repair.tooltip"))
                .onClick(this::repairSelectedInvalidEntity).build();
        ui.button(RIGHT_X + actionW + 5, 141, actionW).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.delete"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.delete.tooltip"))
                .onClick(this::deleteSelectedInvalidEntity).build();
    }

    private Component ruleOverrideText(boolean enabled) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawn.label_state",
                KineticI18n.translatable("gui.entitycontrol.spawn.spawn.rule.override.btn"),
                KineticI18n.translatable(enabled
                        ? "gui.entitycontrol.spawn.spawn.rule.override.on"
                        : "gui.entitycontrol.spawn.spawn.rule.override.off"));
    }

    private Component biomeOverrideText(boolean enabled) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawn.label_state",
                KineticI18n.translatable("gui.entitycontrol.spawn.spawn.biome.override.btn"),
                KineticI18n.translatable(enabled
                        ? "gui.entitycontrol.spawn.spawn.biome.override.on"
                        : "gui.entitycontrol.spawn.spawn.biome.override.off"));
    }

    private Component autoScanText(boolean enabled) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawn.label_state",
                KineticI18n.translatable("gui.entitycontrol.spawn.spawn.auto_scan_btn"),
                KineticI18n.translatable(enabled
                        ? "gui.entitycontrol.spawn.spawn.autoscan.on"
                        : "gui.entitycontrol.spawn.spawn.autoscan.off"));
    }

    private Component rotationDirectionText() {
        return KineticI18n.translatable(clockwiseRotation
                ? "gui.entitycontrol.spawn.rotation.clockwise"
                : "gui.entitycontrol.spawn.rotation.counterclockwise");
    }

    private void applyRotationSpeed(String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            int value = Integer.parseInt(raw);
            if (value < MIN_ROTATION_SPEED_PERCENT || value > MAX_ROTATION_SPEED_PERCENT) return;
            rotationSpeedPercent = value;
            entityPreview.setRotationSpeedPercent(value);
        } catch (NumberFormatException ignored) {
        }
    }

    private void save() {
        SpawnNetwork.saveSpawnSettings(
                globals.enable_rule_override,
                globals.enable_biome_override,
                globals.auto_scan,
                globals.config_amount,
                globals.current_index,
                BiomeSpawnConfig.GSON.toJson(profile),
                currentEditIndex
        );
        commitDraft();
    }

    private void updateSearch(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        displayList.clear();
        if (lower.isEmpty()) displayList.addAll(profile.entities.keySet());
        else displayList.addAll(profile.entities.keySet().stream()
                .filter(id -> matchesEntitySearch(id, lower)).collect(Collectors.toList()));
        sortEntityList(displayList);
        entityGrid.resetScroll();
        if (selectedId != null && !profile.entities.containsKey(selectedId)) selectedId = null;
    }

    private boolean matchesEntitySearch(String id, String lower) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        if (lower.startsWith("@")) {
            return location != null && location.getNamespace().contains(lower.substring(1));
        }
        if (lower.startsWith("#")) {
            if (location == null) return false;
            EntityType<?> type = KineticRegistries.entityTypes().get(location);
            return type != null && type.builtInRegistryHolder().tags()
                    .anyMatch(tag -> tag.location().getPath().contains(lower.substring(1)));
        }
        return KineticSearch.match(getEntitySearchData(id), lower);
    }

    private String getEntitySearchData(String id) {
        return entitySearchDataCache.computeIfAbsent(id, key -> {
            BiomeSpawnConfig.EntityNode node = profile.entities.get(key);
            String name = getEntityName(key).toLowerCase(Locale.ROOT);
            String category = getTranslatedCategoryName(node == null ? "misc" : node.category).toLowerCase(Locale.ROOT);
            return (key + " " + name + " " + KineticSearch.pinyin(name) + " " + category + " " + KineticSearch.pinyin(category))
                    .toLowerCase(Locale.ROOT);
        });
    }

    public void updateBackupProfile(BiomeSpawnConfig.ConfigProfile backupProfile) {
        this.backupProfile = backupProfile == null ? new BiomeSpawnConfig.ConfigProfile() : backupProfile;
        if (isHosted()) rebuild();
    }

    private boolean isHosted() {
        try {
            width();
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private boolean canRestoreSelected() {
        return selectedId != null && backupProfile != null && backupProfile.entities != null
                && backupProfile.entities.containsKey(selectedId) && hasPersistentEntityCustomData(selectedId);
    }

    private void restoreSelectedEntity() {
        if (selectedId == null || backupProfile == null || backupProfile.entities == null) return;
        BiomeSpawnConfig.EntityNode backupNode = backupProfile.entities.get(selectedId);
        if (backupNode == null) {
            KineticOverlays.toast(KineticI18n.translatable(
                    "msg.entitycontrol.spawn.spawn.restore.missing", Component.literal(getEntityName(selectedId))
            ));
            return;
        }
        BiomeSpawnConfig.EntityNode restored = BiomeSpawnConfig.copyEntityNode(backupNode);
        if (restored == null) return;
        restored.manual_edit = false;
        profile.entities.put(selectedId, restored);
        entitySearchDataCache.remove(selectedId);
        editedEntities.update(selectedId, false);
        currentModule().updateSelection();
        sortEntityList(displayList);
        KineticOverlays.toast(KineticI18n.translatable(
                "msg.entitycontrol.spawn.spawn.restore.success", Component.literal(getEntityName(selectedId))
        ));
        rebuild();
    }

    public void refreshSelectedEntitySearchData() {
        if (selectedId == null) return;
        entitySearchDataCache.remove(selectedId);
        updateSearch(searchQuery);
    }

    private void refreshAllEditedEntities() {
        if (profile.entities == null) {
            editedEntities.clear();
            return;
        }
        editedEntities.refresh(profile.entities.keySet(), this::hasPersistentEntityCustomData);
    }

    private boolean hasPersistentEntityCustomData(String id) {
        BiomeSpawnConfig.EntityNode node = profile.entities == null ? null : profile.entities.get(id);
        return node != null && node.manual_edit;
    }

    public void markSelectedEntityEdited() {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null || selectedId == null) return;
        node.manual_edit = true;
        entitySearchDataCache.remove(selectedId);
        editedEntities.update(selectedId, true);
        sortEntityList(displayList);
    }

    private void sortEntityList(List<String> list) {
        Comparator<String> fallback = (left, right) -> {
            BiomeSpawnConfig.EntityNode a = profile.entities.get(left);
            BiomeSpawnConfig.EntityNode b = profile.entities.get(right);
            String catA = a == null || a.category == null ? "misc" : a.category;
            String catB = b == null || b.category == null ? "misc" : b.category;
            boolean miscA = "misc".equalsIgnoreCase(catA);
            boolean miscB = "misc".equalsIgnoreCase(catB);
            if (miscA != miscB) return miscA ? 1 : -1;
            int byCategory = catA.compareToIgnoreCase(catB);
            return byCategory != 0 ? byCategory : left.compareToIgnoreCase(right);
        };
        Comparator<String> edited = editedEntities.comparator(fallback);
        list.sort((a, b) -> {
            boolean invalidA = !BiomeSpawnConfig.isEntityIdValid(a);
            boolean invalidB = !BiomeSpawnConfig.isEntityIdValid(b);
            if (invalidA != invalidB) return invalidA ? -1 : 1;
            return edited.compare(a, b);
        });
    }

    private void selectEntity(String id) {
        selectedId = id;
        clearFocus();
        currentModule().updateSelection();
        rebuild();
    }

    private boolean isSelectedEntityInvalid() {
        return selectedId != null && !BiomeSpawnConfig.isEntityIdValid(selectedId);
    }

    private void repairSelectedInvalidEntity() {
        if (!isSelectedEntityInvalid() || invalidEntityIdBox == null) return;
        String oldId = selectedId;
        String raw = invalidEntityIdBox.textValue().trim();
        ResourceLocation targetLocation = KineticResourceIds.tryParse(raw);
        if (targetLocation == null || !BiomeSpawnConfig.isEntityIdValid(targetLocation.toString())) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawn.invalid_entity.repair_invalid"));
            return;
        }
        String target = targetLocation.toString();
        if (profile.entities.containsKey(target)) {
            KineticOverlays.toast(KineticI18n.translatable(
                    "msg.entitycontrol.spawn.spawn.invalid_entity.repair_duplicate", target
            ));
            return;
        }
        BiomeSpawnConfig.EntityNode node = profile.entities.remove(oldId);
        if (node == null) return;
        node.manual_edit = true;
        profile.entities.put(target, node);
        entityNameCache.remove(oldId);
        entitySearchDataCache.remove(oldId);
        entityNameCache.remove(target);
        entitySearchDataCache.remove(target);
        refreshAllEditedEntities();
        selectedId = target;
        updateSearch(searchQuery);
        if (!displayList.contains(target)) {
            searchQuery = "";
            updateSearch("");
        }
        KineticOverlays.toast(KineticI18n.translatable(
                "msg.entitycontrol.spawn.spawn.invalid_entity.repair_success", target
        ));
        rebuild();
    }

    private void deleteSelectedInvalidEntity() {
        if (!isSelectedEntityInvalid()) return;
        String removed = selectedId;
        profile.entities.remove(removed);
        entityNameCache.remove(removed);
        entitySearchDataCache.remove(removed);
        refreshAllEditedEntities();
        updateSearch(searchQuery);
        selectedId = displayList.isEmpty() ? null : displayList.get(0);
        KineticOverlays.toast(KineticI18n.translatable(
                "msg.entitycontrol.spawn.spawn.invalid_entity.delete_success", removed
        ));
        rebuild();
    }

    public String getEntityName(String id) {
        return entityNameCache.computeIfAbsent(id, key -> {
            ResourceLocation location = KineticResourceIds.tryParse(key);
            EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
            return type == null ? key : type.getDescription().getString();
        });
    }

    public String getTranslatedBiomeName(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        if (location == null) return id;
        String key = Util.makeDescriptionId("biome", location);
        Component component = KineticI18n.translatable(key);
        String value = component.getString();
        return value.equals(key) ? id : value;
    }

    public String getTranslatedCategoryName(String category) {
        String normalized = category == null || category.isBlank() ? "misc" : category.toLowerCase(Locale.ROOT);
        String key = "gui.entitycontrol.spawn.spawn.category." + normalized;
        Component component = KineticI18n.translatable(key);
        String value = component.getString();
        if (!value.equals(key)) return value;
        if ("misc".equals(normalized)) return KineticI18n.translatable("gui.entitycontrol.spawn.spawn.category.misc").getString();
        return category;
    }

    BiomeSpawnConfig.EntityNode selectedNode() {
        return selectedId == null || profile.entities == null ? null : profile.entities.get(selectedId);
    }

    int rightX() {
        return RIGHT_X;
    }

    int rightWidth() {
        return RIGHT_W;
    }

    void rebuildPage() {
        rebuild();
    }

    void bindRuleNumberFields(KineticNumberField minDistance, KineticNumberField maxDistance,
                              KineticNumberField minHeight, KineticNumberField maxHeight,
                              KineticNumberField minLight, KineticNumberField maxLight) {
        ruleMinDistanceField = minDistance;
        ruleMaxDistanceField = maxDistance;
        ruleMinHeightField = minHeight;
        ruleMaxHeightField = maxHeight;
        ruleMinLightField = minLight;
        ruleMaxLightField = maxLight;
    }

    KineticNumberField ruleMinDistanceField() { return ruleMinDistanceField; }
    KineticNumberField ruleMaxDistanceField() { return ruleMaxDistanceField; }
    KineticNumberField ruleMinHeightField() { return ruleMinHeightField; }
    KineticNumberField ruleMaxHeightField() { return ruleMaxHeightField; }
    KineticNumberField ruleMinLightField() { return ruleMinLightField; }
    KineticNumberField ruleMaxLightField() { return ruleMaxLightField; }

    private ITabModule currentModule() {
        return activeTab == 1 ? biomeTab : activeTab == 2 ? categoryTab : ruleTab;
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        KineticTheme.canvasBackground(graphics, width(), height());
        KineticTheme.panel(graphics, RIGHT_X - 6, 6, RIGHT_W + 12, 348);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isSelectedEntityInvalid()) {
            graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.title"), RIGHT_X, 96,
                    KineticTheme.current().danger());
            graphics.wrappedText(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.desc"),
                    RIGHT_X, 170, RIGHT_W, KineticTheme.current().text());
        } else if (activeTab == 2 || selectedNode() != null) {
            currentModule().render(graphics, mouseX, mouseY, partialTick);
        }
        if (deferredTooltip != null && !deferredTooltip.isEmpty()) showTooltip(deferredTooltip);
    }

    @Override
    protected void onRemoved() {
        entityPreview.clear();
        entityNameCache.clear();
        entitySearchDataCache.clear();
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
            int totalRows = (displayList.size() + COLS - 1) / COLS;
            scroll.update(totalRows, VISIBLE_ROWS);
        }

        private int indexAt(double mouseX, double mouseY) {
            if (mouseX < GRID_X || mouseX >= GRID_X + GRID_W || mouseY < GRID_Y || mouseY >= GRID_Y + GRID_H) return -1;
            double localX = mouseX - GRID_X;
            int col = (int) (localX / CELL_PITCH);
            if (col < 0 || col >= COLS || localX - col * CELL_PITCH >= CELL_SIZE) return -1;
            double localY = mouseY - GRID_Y + scroll.visualShift(CELL_PITCH);
            int row = (int) Math.floor(localY / CELL_PITCH);
            if (row < 0 || localY - row * CELL_PITCH >= CELL_SIZE) return -1;
            return (scroll.smoothIndexOffset() + row) * COLS + col;
        }

        @Override
        protected void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
            deferredTooltip = null;
            updateRange();
            int firstRow = scroll.smoothIndexOffset();
            int shift = scroll.visualShift(CELL_PITCH);
            int first = firstRow * COLS;
            int last = Math.min(displayList.size(), first + (VISIBLE_ROWS + 1) * COLS);
            graphics.clipped(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H, () -> {
                for (int index = first; index < last; index++) {
                    int local = index - first;
                    int x = GRID_X + local % COLS * CELL_PITCH;
                    int y = GRID_Y + local / COLS * CELL_PITCH - shift;
                    String id = displayList.get(index);
                    boolean invalid = !BiomeSpawnConfig.isEntityIdValid(id);
                    boolean selected = id.equals(selectedId);
                    boolean hovered = mouseX >= GRID_X && mouseX < GRID_X + GRID_W
                            && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H
                            && mouseX >= x && mouseX < x + CELL_SIZE && mouseY >= y && mouseY < y + CELL_SIZE;
                    KineticEntityPreview.drawCheckerboard(graphics, x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);
                    if (selected || invalid) {
                        KineticTheme.stateOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, selected, false, invalid);
                    } else if (editedEntities.isEdited(id)) {
                        KineticTheme.indicatorOutline(graphics, x, y, CELL_SIZE, CELL_SIZE,
                                KineticTheme.Indicator.SUCCESS);
                    } else {
                        KineticTheme.stateOutline(graphics, x, y, CELL_SIZE, CELL_SIZE, false, hovered, false);
                    }
                    String stateKey = "spawn:grid:" + id;
                    boolean rendered = !invalid && entityPreview.render(
                            graphics, id, stateKey, x + 3, y + 3, CELL_SIZE - 6, CELL_SIZE - 6, hovered
                    );
                    if (!rendered) {
                        graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.question_mark"),
                                x + CELL_SIZE / 2, y + CELL_SIZE / 2 - 4, KineticTheme.current().text(), false);
                    } else {
                        int visibleTop = Math.max(GRID_Y, y);
                        int visibleBottom = Math.min(GRID_Y + GRID_H, y + CELL_SIZE);
                        if (visibleBottom > visibleTop) {
                            registerPreviewZoomArea(entityPreview, stateKey, x, visibleTop, CELL_SIZE, visibleBottom - visibleTop);
                        }
                    }
                    scroll.renderSelectionFlash(graphics, index, x, y, CELL_SIZE, CELL_SIZE);
                    if (hovered) {
                        deferredTooltip = new ArrayList<>();
                        deferredTooltip.add(Component.literal(getEntityName(id)));
                        deferredTooltip.add(KineticI18n.translatable("gui.entitycontrol.spawn.entity.tooltip.id", id));
                        if (invalid) {
                            deferredTooltip.add(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.tooltip"));
                        } else {
                            deferredTooltip.add(KineticI18n.translatable(
                                    "gui.entitycontrol.spawn.spawn.tooltip.model_zoom",
                                    entityPreview.zoomPercent(stateKey)
                            ));
                        }
                    }
                }
            });
            scroll.render(graphics, mouseX, mouseY, GRID_X + GRID_W + 4, GRID_Y, 4, GRID_H, 20);
        }

        @Override
        protected boolean onMouseClick(MouseInput input) {
            if (scroll.beginDrag(input.x(), input.y(), input.button(), GRID_X + GRID_W + 4, GRID_Y, 4, GRID_H, 20, 3)) {
                return true;
            }
            if (!input.isLeft() || activeTab == 2) return false;
            int index = indexAt(input.x(), input.y());
            if (index < 0 || index >= displayList.size()) return false;
            selectEntity(displayList.get(index));
            return true;
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
