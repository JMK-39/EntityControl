package dev.xyat.entitycontrol.spawn.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.render.KineticEntityPreview.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SpawnControlScreen extends KineticScreen {
    private static final int DEFAULT_ROTATION_SPEED_PERCENT = 100;
    private static final int MIN_ROTATION_SPEED_PERCENT = 0;
    private static final int MAX_ROTATION_SPEED_PERCENT = 500;

    public final BiomeSpawnConfig.GlobalSettings globals;
    public final BiomeSpawnConfig.ConfigProfile profile;
    public final int currentEditIndex;
    private final Screen parent;
    private BiomeSpawnConfig.ConfigProfile backupProfile;

    public List<String> displayList;
    public KineticEditBox searchBox;
    public String selectedId;

    private NumericEditBox rotationSpeedBox;
    private int rotationSpeedPercent = DEFAULT_ROTATION_SPEED_PERCENT;
    private boolean clockwiseRotation = true;
    private boolean updatingRotationSpeedBox;

    private StateButton btnTabRules;
    private StateButton btnTabBiomes;
    private StateButton btnTabCategories;
    private StateButton btnRestore;
    private KineticEditBox invalidEntityIdBox;
    private StateButton btnRepairInvalidEntity;
    private StateButton btnDeleteInvalidEntity;

    private ITabModule tabRules;
    private ITabModule tabBiomes;
    private ITabModule tabCategories;
    private ITabModule currentTab;

    public static final int V_WIDTH = 640;
    public static final int V_HEIGHT = 360;

    public final int COLS = 4;
    public final int CELL_SIZE = 72;

    public int gridX = 12;
    public int gridY = 45;
    public int gridW = COLS * CELL_SIZE;
    public int visibleRows = 4;
    public int gridH = visibleRows * CELL_SIZE;

    private final GridScrollController gridScroll = new GridScrollController();
    private int maxScroll;

    public int rx = gridX + gridW + 12;
    public int rw = V_WIDTH - rx - 8;
    public int tabBtnW;

    private boolean draggingGridScroll;
    private boolean showProfileMenu;
    private final GridScrollController profileScroll = new GridScrollController();
    private StateButton profileDecreaseButton;
    private StateButton profileIncreaseButton;
    private final Map<Integer, StateButton> profileButtons = new HashMap<>();
    private int profileMenuMouseX;
    private int profileMenuMouseY;

    private final EntityPreviewRenderer entityPreviewRenderer =
            KineticWidgets.createEntityPreviewRenderer();
    private final EditedEntryTracker<String> editedEntities =
            new EditedEntryTracker<>();
    private final Map<String, String> entityNameCache = new HashMap<>();
    private final Map<String, String> entitySearchDataCache = new HashMap<>();

    private List<Component> deferredTooltip;

    public SpawnControlScreen(
            BiomeSpawnConfig.GlobalSettings globals,
            BiomeSpawnConfig.ConfigProfile profile,
            int editIndex
    ) {
        this(globals, profile, editIndex, null);
    }

    public SpawnControlScreen(
            BiomeSpawnConfig.GlobalSettings globals,
            BiomeSpawnConfig.ConfigProfile profile,
            int editIndex,
            Screen parent
    ) {
        super(Component.translatable(
                "gui.entitycontrol.spawn.spawn.title"
        ));

        this.globals = globals;
        this.profile = profile;
        this.parent = parent;
        setParentScreen(parent);
        this.backupProfile = new BiomeSpawnConfig.ConfigProfile();
        this.currentEditIndex = editIndex;

        refreshAllEditedEntities();

        this.displayList = new ArrayList<>(
                profile.entities.keySet()
        );

        sortEntityList(this.displayList);

        configureStandaloneDraft(this::captureSpawnSnapshot, this::restoreSpawnSnapshot);
    }

    public Screen getParentScreen() {
        return parent;
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
                snapshot.globalsJson(),
                BiomeSpawnConfig.GlobalSettings.class
        );
        if (restoredGlobals != null) {
            globals.enable_rule_override = restoredGlobals.enable_rule_override;
            globals.enable_biome_override = restoredGlobals.enable_biome_override;
            globals.auto_scan = restoredGlobals.auto_scan;
            globals.config_amount = restoredGlobals.config_amount;
            globals.current_index = restoredGlobals.current_index;
        }

        BiomeSpawnConfig.ConfigProfile restoredProfile = BiomeSpawnConfig.GSON.fromJson(
                snapshot.profileJson(),
                BiomeSpawnConfig.ConfigProfile.class
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
        String query = searchBox == null ? "" : searchBox.getValue();
        updateSearch(query);
        if (selectedId != null && !profile.entities.containsKey(selectedId)) {
            selectedId = displayList.isEmpty() ? null : displayList.get(0);
        }
    }

    public Font getFont() {
        return this.font;
    }

    @Override
    protected void buildUi() {
        tabBtnW = (rw - 10) / 3;

        int saveButtonW = 70;
        int exitButtonW = 46;
        int searchGap = 5;
        int searchW = gridW - saveButtonW - exitButtonW - searchGap * 2;

        searchBox = addTextField(
                gridX,
                15,
                searchW,
                Component.empty(),
                Component.translatable("gui.entitycontrol.spawn.spawn.search_hint"),
                null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.search")
        );
        searchBox.setResponder(this::updateSearch);

        addButton(
                gridX + searchW + searchGap,
                15,
                saveButtonW,
                Component.translatable("gui.entitycontrol.spawn.spawn.save"),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.save"),
                this::saveAndClose
        );

        addButton(
                gridX + searchW + searchGap + saveButtonW + searchGap,
                15,
                exitButtonW,
                Component.translatable("gui.entitycontrol.spawn.spawn.exit"),
                null,
                this::navigateBack
        );

        addButtonWithHandler(
                rx,
                15,
                tabBtnW,
                getRuleOverrideText(),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.rule.override"),
                button -> {
                    globals.enable_rule_override = !globals.enable_rule_override;
                    button.setText(getRuleOverrideText());
                }
        );

        addButtonWithHandler(
                rx + tabBtnW + 5,
                15,
                tabBtnW,
                getBiomeOverrideText(),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.biome.override"),
                button -> {
                    globals.enable_biome_override = !globals.enable_biome_override;
                    button.setText(getBiomeOverrideText());
                }
        );

        addButton(
                rx + (tabBtnW + 5) * 2,
                15,
                tabBtnW,
                Component.translatable("gui.entitycontrol.spawn.spawn.profile_btn").append(": " + currentEditIndex),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.profile"),
                () -> showProfileMenu = !showProfileMenu
        );

        btnTabRules = addButton(
                rx,
                40,
                tabBtnW,
                Component.translatable("gui.entitycontrol.spawn.spawn.tab.rules"),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.tab.rules"),
                () -> switchTab(tabRules)
        );

        btnTabBiomes = addButton(
                rx + tabBtnW + 5,
                40,
                tabBtnW,
                Component.translatable("gui.entitycontrol.spawn.spawn.tab.biomes"),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.tab.biomes"),
                () -> switchTab(tabBiomes)
        );

        btnTabCategories = addButton(
                rx + (tabBtnW + 5) * 2,
                40,
                tabBtnW,
                Component.translatable("gui.entitycontrol.spawn.spawn.tab.categories"),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.tab.cats"),
                () -> switchTab(tabCategories)
        );

        addButtonWithHandler(
                rx,
                65,
                tabBtnW,
                getAutoScanText(),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.autoscan"),
                button -> {
                    globals.auto_scan = !globals.auto_scan;
                    button.setText(getAutoScanText());
                    if (globals.auto_scan) {
                        SpawnNetwork.refreshSpawnBackup(currentEditIndex);
                    }
                }
        );

        btnRestore = addButton(
                rx + tabBtnW + 5,
                65,
                tabBtnW,
                Component.translatable("gui.entitycontrol.spawn.spawn.restore"),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.restore"),
                this::restoreSelectedEntity
        );
        btnRestore.setEnabled(false);

        int rotationControlX = rx + (tabBtnW + 5) * 2;
        int rotationLabelW = 70;
        int rotationInputW = Math.max(31, tabBtnW - rotationLabelW - 9);

        addButtonWithHandler(
                rotationControlX,
                65,
                rotationLabelW,
                getRotationDirectionText(),
                Component.translatable("gui.entitycontrol.spawn.rotation.direction.tooltip"),
                button -> {
                    clockwiseRotation = !clockwiseRotation;
                    entityPreviewRenderer.setClockwise(clockwiseRotation);
                    button.setText(getRotationDirectionText());
                }
        );

        rotationSpeedBox = addIntegerField(
                rotationControlX + rotationLabelW + 4,
                65,
                rotationInputW,
                Component.translatable("gui.entitycontrol.spawn.spawn.rotation_speed"),
                false,
                MIN_ROTATION_SPEED_PERCENT,
                MAX_ROTATION_SPEED_PERCENT,
                null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.rotation_speed")
        );
        rotationSpeedBox.setMaxLength(3);
        rotationSpeedBox.setValue(Integer.toString(rotationSpeedPercent));
        rotationSpeedBox.setResponder(value -> applyRotationSpeedFromBox(false));

        int invalidActionWidth = (rw - 5) / 2;
        invalidEntityIdBox = addTextField(
                rx,
                116,
                rw,
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.id"),
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.id_hint"),
                null,
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.id.tooltip")
        );
        invalidEntityIdBox.setMaxLength(256);
        invalidEntityIdBox.setVisible(false);

        btnRepairInvalidEntity = addButton(
                rx,
                141,
                invalidActionWidth,
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.repair"),
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.repair.tooltip"),
                this::repairSelectedInvalidEntity
        );
        btnRepairInvalidEntity.setVisible(false);

        btnDeleteInvalidEntity = addButton(
                rx + invalidActionWidth + 5,
                141,
                invalidActionWidth,
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.delete"),
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.delete.tooltip"),
                this::deleteSelectedInvalidEntity
        );
        btnDeleteInvalidEntity.setVisible(false);

        tabRules = new RuleTabModule(this);
        tabRules.init();

        tabBiomes = new BiomeTabModule(this);
        tabBiomes.init();

        tabCategories = new CategoryTabModule(this);
        tabCategories.init();

        updateSearch("");

        if (!displayList.isEmpty()) {
            updateSelection(displayList.get(0));
        }

        switchTab(tabRules);
    }

    private Component getRotationDirectionText() {
        return Component.translatable(
                clockwiseRotation
                        ? "gui.entitycontrol.spawn.rotation.clockwise"
                        : "gui.entitycontrol.spawn.rotation.counterclockwise"
        );
    }

    private boolean isValidRotationSpeedInput(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }

        try {
            return Integer.parseInt(value) <= MAX_ROTATION_SPEED_PERCENT;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void applyRotationSpeedFromBox(boolean showInvalidToast) {
        if (rotationSpeedBox == null || updatingRotationSpeedBox) {
            return;
        }

        String value = rotationSpeedBox.getValue();

        if (value.isBlank()) {
            if (showInvalidToast) {
                showInvalidRotationSpeedToast();
                restoreRotationSpeedText();
            }
            return;
        }

        try {
            int parsed = Integer.parseInt(value);

            if (parsed < MIN_ROTATION_SPEED_PERCENT
                    || parsed > MAX_ROTATION_SPEED_PERCENT) {
                if (showInvalidToast) {
                    showInvalidRotationSpeedToast();
                    restoreRotationSpeedText();
                }
                return;
            }

            rotationSpeedPercent = parsed;
            entityPreviewRenderer.setRotationSpeedPercent(rotationSpeedPercent);
        } catch (NumberFormatException ignored) {
            if (showInvalidToast) {
                showInvalidRotationSpeedToast();
                restoreRotationSpeedText();
            }
        }
    }

    private void restoreRotationSpeedText() {
        if (rotationSpeedBox == null) {
            return;
        }

        updatingRotationSpeedBox = true;
        rotationSpeedBox.setValue(
                Integer.toString(rotationSpeedPercent)
        );
        updatingRotationSpeedBox = false;
    }

    private void showInvalidRotationSpeedToast() {
        KineticOverlays.toast(
                Component.translatable(
                        "msg.entitycontrol.spawn.spawn.rotation_speed.invalid"
                )
        );
    }

    private boolean isSelectedEntityInvalid() {
        return selectedId != null && !BiomeSpawnConfig.isEntityIdValid(selectedId);
    }

    private void updateInvalidEntityEditorState() {
        boolean invalid = isSelectedEntityInvalid();

        if (invalidEntityIdBox != null) {
            invalidEntityIdBox.setVisible(invalid);
            invalidEntityIdBox.setEnabled(invalid);
            if (invalid && !isControlFocused(invalidEntityIdBox)) {
                invalidEntityIdBox.setValue(selectedId);
            }
        }

        if (btnRepairInvalidEntity != null) {
            btnRepairInvalidEntity.setVisible(invalid);
            btnRepairInvalidEntity.setEnabled(invalid);
        }

        if (btnDeleteInvalidEntity != null) {
            btnDeleteInvalidEntity.setVisible(invalid);
            btnDeleteInvalidEntity.setEnabled(invalid);
        }

        if (currentTab != null) {
            currentTab.setVisible(!invalid && selectedId != null);
        }
    }

    private void repairSelectedInvalidEntity() {
        if (!isSelectedEntityInvalid() || invalidEntityIdBox == null) {
            return;
        }

        String oldId = selectedId;
        String rawTarget = invalidEntityIdBox.getValue().trim();
        ResourceLocation targetLocation = KineticResourceIds.tryParse(rawTarget);
        if (targetLocation == null || !BiomeSpawnConfig.isEntityIdValid(targetLocation.toString())) {
            KineticOverlays.toast(Component.translatable(
                    "msg.entitycontrol.spawn.spawn.invalid_entity.repair_invalid"
            ));
            return;
        }

        String targetId = targetLocation.toString();
        if (profile.entities.containsKey(targetId)) {
            KineticOverlays.toast(Component.translatable(
                    "msg.entitycontrol.spawn.spawn.invalid_entity.repair_duplicate",
                    targetId
            ));
            return;
        }

        BiomeSpawnConfig.EntityNode node = profile.entities.remove(oldId);
        if (node == null) {
            return;
        }

        node.manual_edit = true;
        profile.entities.put(targetId, node);
        entityNameCache.remove(oldId);
        entityNameCache.remove(targetId);
        entitySearchDataCache.remove(oldId);
        entitySearchDataCache.remove(targetId);
        refreshAllEditedEntities();

        updateSearch(searchBox == null ? "" : searchBox.getValue());
        if (!displayList.contains(targetId) && searchBox != null) {
            searchBox.setValue("");
        }
        updateSelection(targetId);

        KineticOverlays.toast(Component.translatable(
                "msg.entitycontrol.spawn.spawn.invalid_entity.repair_success",
                targetId
        ));
    }

    private void deleteSelectedInvalidEntity() {
        if (!isSelectedEntityInvalid()) {
            return;
        }

        String removedId = selectedId;
        profile.entities.remove(removedId);
        entityNameCache.remove(removedId);
        entitySearchDataCache.remove(removedId);
        refreshAllEditedEntities();

        updateSearch(searchBox == null ? "" : searchBox.getValue());
        if (!displayList.isEmpty()) {
            updateSelection(displayList.get(0));
        } else {
            selectedId = null;
            if (currentTab != null) {
                currentTab.setVisible(false);
            }
            updateInvalidEntityEditorState();
            updateRestoreButtonState();
        }

        KineticOverlays.toast(Component.translatable(
                "msg.entitycontrol.spawn.spawn.invalid_entity.delete_success",
                removedId
        ));
    }

    private void saveAndClose() {
        applyRotationSpeedFromBox(true);
        String profileJson = BiomeSpawnConfig.GSON.toJson(profile);
        SpawnNetwork.saveSpawnSettings(
                globals.enable_rule_override,
                globals.enable_biome_override,
                globals.auto_scan,
                globals.config_amount,
                globals.current_index,
                profileJson,
                currentEditIndex
        );
        commitDraft();
        navigateBack();
    }

    @Override
    protected boolean handleCloseRequest() {
        return false;
    }

    private void switchTab(ITabModule tab) {
        tabRules.setVisible(false);
        tabBiomes.setVisible(false);
        tabCategories.setVisible(false);

        this.currentTab = tab;

        btnTabRules.setEnabled(tab != tabRules);
        btnTabBiomes.setEnabled(tab != tabBiomes);
        btnTabCategories.setEnabled(tab != tabCategories);

        if (selectedId != null && !isSelectedEntityInvalid()) {
            tab.updateSelection();
        }

        updateInvalidEntityEditorState();
    }

    private Component getRuleOverrideText() {
        return Component.translatable(
                        "gui.entitycontrol.spawn.spawn.rule.override.btn"
                )
                .append(" ")
                .append(Component.translatable(
                        globals.enable_rule_override
                                ? "gui.entitycontrol.spawn.spawn.rule.override.on"
                                : "gui.entitycontrol.spawn.spawn.rule.override.off"
                ));
    }

    private Component getBiomeOverrideText() {
        return Component.translatable(
                        "gui.entitycontrol.spawn.spawn.biome.override.btn"
                )
                .append(" ")
                .append(Component.translatable(
                        globals.enable_biome_override
                                ? "gui.entitycontrol.spawn.spawn.biome.override.on"
                                : "gui.entitycontrol.spawn.spawn.biome.override.off"
                ));
    }

    private Component getAutoScanText() {
        return Component.translatable(
                        "gui.entitycontrol.spawn.spawn.auto_scan_btn"
                )
                .append(" ")
                .append(Component.translatable(
                        globals.auto_scan
                                ? "gui.entitycontrol.spawn.spawn.autoscan.on"
                                : "gui.entitycontrol.spawn.spawn.autoscan.off"
                ));
    }

    private void updateSearch(String query) {
        String lower = query
                .toLowerCase(Locale.ROOT)
                .trim();

        if (lower.isEmpty()) {
            displayList = new ArrayList<>(profile.entities.keySet());
        } else {
            displayList = profile.entities
                    .keySet()
                    .stream()
                    .filter(id -> matchesEntitySearch(id, lower))
                    .collect(Collectors.toList());
        }

        sortEntityList(displayList);
        gridScroll.reset();
        recalculateMaxScroll();
    }

    private boolean matchesEntitySearch(String id, String lower) {
        ResourceLocation location = KineticResourceIds.tryParse(id);

        if (lower.startsWith("@")) {
            return location != null
                    && location.getNamespace().contains(lower.substring(1));
        }

        if (lower.startsWith("#")) {
            if (location == null) return false;
            EntityType<?> type = KineticRegistries.entityTypes().get(location);
            return type != null
                    && type.builtInRegistryHolder()
                    .tags()
                    .anyMatch(tag -> tag.location()
                            .getPath()
                            .contains(lower.substring(1)));
        }

        return KineticSearch.match(getEntitySearchData(id), lower);
    }

    private String getEntitySearchData(String id) {
        return entitySearchDataCache.computeIfAbsent(id, key -> {
            BiomeSpawnConfig.EntityNode node = profile.entities.get(key);
            String entityName = getEntityName(key).toLowerCase(Locale.ROOT);
            String categoryName = getTranslatedCategoryName(
                    node == null ? "misc" : node.category
            ).toLowerCase(Locale.ROOT);

            return (key
                    + " " + entityName
                    + " " + KineticSearch.pinyin(entityName)
                    + " " + categoryName
                    + " " + KineticSearch.pinyin(categoryName))
                    .toLowerCase(Locale.ROOT);
        });
    }

    public void updateBackupProfile(BiomeSpawnConfig.ConfigProfile backupProfile) {
        this.backupProfile = backupProfile == null
                ? new BiomeSpawnConfig.ConfigProfile()
                : backupProfile;
        updateRestoreButtonState();
    }

    private void restoreSelectedEntity() {
        if (selectedId == null
                || backupProfile == null
                || backupProfile.entities == null) {
            return;
        }

        BiomeSpawnConfig.EntityNode backupNode = backupProfile.entities.get(selectedId);
        if (backupNode == null) {
            KineticOverlays.toast(Component.translatable(
                    "msg.entitycontrol.spawn.spawn.restore.missing",
                    Component.literal(getEntityName(selectedId)).withStyle(ChatFormatting.GOLD)
            ));
            return;
        }

        BiomeSpawnConfig.EntityNode restored = BiomeSpawnConfig.copyEntityNode(backupNode);
        if (restored == null) {
            return;
        }

        restored.manual_edit = false;
        profile.entities.put(selectedId, restored);
        entitySearchDataCache.remove(selectedId);
        editedEntities.update(selectedId, false);

        sortEntityList(displayList);
        recalculateMaxScroll();

        if (currentTab != null) {
            currentTab.updateSelection();
        }

        updateRestoreButtonState();

        KineticOverlays.toast(Component.translatable(
                "msg.entitycontrol.spawn.spawn.restore.success",
                Component.literal(getEntityName(selectedId)).withStyle(ChatFormatting.GOLD)
        ));
    }

    private void updateRestoreButtonState() {
        if (btnRestore == null) {
            return;
        }

        boolean hasBackup = selectedId != null
                && backupProfile != null
                && backupProfile.entities != null
                && backupProfile.entities.containsKey(selectedId);

        btnRestore.setEnabled(hasBackup && hasPersistentEntityCustomData(selectedId));
    }

    public void refreshSelectedEntitySearchData() {
        if (selectedId == null) return;
        entitySearchDataCache.remove(selectedId);

        if (searchBox != null && !searchBox.getValue().isBlank()) {
            updateSearch(searchBox.getValue());
        } else {
            sortEntityList(displayList);
            recalculateMaxScroll();
        }
    }

    private void recalculateMaxScroll() {
        int totalRows = (int) Math.ceil((double) displayList.size() / COLS);
        gridScroll.update(totalRows, visibleRows);
        maxScroll = gridScroll.maxOffset();
    }

    private void refreshAllEditedEntities() {
        if (profile == null || profile.entities == null) {
            editedEntities.clear();
            return;
        }

        editedEntities.refresh(
                profile.entities.keySet(),
                this::hasPersistentEntityCustomData
        );
    }

    private boolean hasPersistentEntityCustomData(String id) {
        if (profile == null || profile.entities == null) {
            return false;
        }

        BiomeSpawnConfig.EntityNode node = profile.entities.get(id);
        return node != null && node.manual_edit;
    }

    public void markSelectedEntityEdited() {
        if (selectedId == null || profile == null || profile.entities == null) {
            return;
        }

        BiomeSpawnConfig.EntityNode node = profile.entities.get(selectedId);
        if (node == null) return;

        node.manual_edit = true;
        entitySearchDataCache.remove(selectedId);
        editedEntities.update(selectedId, true);
        updateRestoreButtonState();
        sortEntityList(displayList);
        gridScroll.reset();
        recalculateMaxScroll();
    }

    private void sortEntityList(List<String> list) {
        Comparator<String> fallback = (a, b) -> {
            BiomeSpawnConfig.EntityNode nodeA = profile.entities.get(a);
            BiomeSpawnConfig.EntityNode nodeB = profile.entities.get(b);

            String catA = nodeA == null || nodeA.category == null
                    ? "misc"
                    : nodeA.category;
            String catB = nodeB == null || nodeB.category == null
                    ? "misc"
                    : nodeB.category;

            boolean miscA = "misc".equalsIgnoreCase(catA);
            boolean miscB = "misc".equalsIgnoreCase(catB);
            if (miscA != miscB) {
                return miscA ? 1 : -1;
            }

            int categoryCompare = catA.compareToIgnoreCase(catB);
            if (categoryCompare != 0) {
                return categoryCompare;
            }

            return a.compareToIgnoreCase(b);
        };

        Comparator<String> editedComparator = editedEntities.comparator(fallback);
        list.sort((a, b) -> {
            boolean invalidA = !BiomeSpawnConfig.isEntityIdValid(a);
            boolean invalidB = !BiomeSpawnConfig.isEntityIdValid(b);
            if (invalidA != invalidB) {
                return invalidA ? -1 : 1;
            }
            return editedComparator.compare(a, b);
        });
    }

    private void updateSelection(String id) {
        this.selectedId = id;

        blurControl(searchBox);

        if (currentTab != null && !isSelectedEntityInvalid()) {
            currentTab.updateSelection();
        }

        updateInvalidEntityEditorState();
        updateRestoreButtonState();
    }

    public String getEntityName(String id) {
        return entityNameCache.computeIfAbsent(id, key -> {
            ResourceLocation location = KineticResourceIds.tryParse(key);
            if (location == null || !KineticRegistries.entityTypes().contains(location)) {
                return key;
            }

            EntityType<?> type = KineticRegistries.entityTypes().get(location);
            return type == null ? key : type.getDescription().getString();
        });
    }

    public String getTranslatedBiomeName(String id) {
        String key = Util.makeDescriptionId(
                "biome",
                KineticResourceIds.parse(id)
        );

        Component component =
                Component.translatable(key);

        return component
                .getString()
                .equals(key)
                ? id
                : component.getString();
    }

    public String getTranslatedCategoryName(String category) {
        if (category == null
                || category.trim().isEmpty()) {
            return "Misc";
        }

        String key =
                "gui.entitycontrol.spawn.spawn.category."
                        + category.toLowerCase(Locale.ROOT);

        Component component =
                Component.translatable(key);

        String result =
                component.getString();

        if (result.equals(key)) {
            return category
                    .substring(0, 1)
                    .toUpperCase(Locale.ROOT)
                    + category.substring(1);
        }

        return result;
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics g,
            int mx,
            int my,
            float pt
    ) {
        GuiTheme.canvasBackground(g, V_WIDTH, V_HEIGHT);
        GuiTheme.stateOutline(g, 0, 0, V_WIDTH, V_HEIGHT, false, false, false);
        GuiTheme.panelAlt(g, gridX - 2, gridY - 2, gridW + 4, gridH + 4);
    }

    private StateButton profileDecreaseButton(int x, int y, boolean enabled) {
        if (profileDecreaseButton == null) {
            profileDecreaseButton = KineticWidgets.createCompactButton(
                    0, 0, 20,
                    Component.translatable("gui.entitycontrol.spawn.spawn.profile_remove"),
                    Component.translatable("gui.entitycontrol.spawn.spawn.profile_count.tooltip"),
                    () -> updateProfileCount(globals.config_amount - 1)
            );
        }
        profileDecreaseButton.setX(x);
        profileDecreaseButton.setY(y);
        profileDecreaseButton.setWidth(20);
        profileDecreaseButton.setEnabled(enabled);
        return profileDecreaseButton;
    }

    private StateButton profileIncreaseButton(int x, int y, boolean enabled) {
        if (profileIncreaseButton == null) {
            profileIncreaseButton = KineticWidgets.createCompactButton(
                    0, 0, 20,
                    Component.translatable("gui.entitycontrol.spawn.spawn.profile_add"),
                    Component.translatable("gui.entitycontrol.spawn.spawn.profile_count.tooltip"),
                    () -> updateProfileCount(globals.config_amount + 1)
            );
        }
        profileIncreaseButton.setX(x);
        profileIncreaseButton.setY(y);
        profileIncreaseButton.setWidth(20);
        profileIncreaseButton.setEnabled(enabled);
        return profileIncreaseButton;
    }

    private StateButton profileButton(int profileId, int x, int y, int width, int clipTop, int clipBottom) {
        StateButton button = profileButtons.computeIfAbsent(profileId, id -> KineticWidgets.createCompactButton(
                0, 0, width,
                Component.translatable("gui.entitycontrol.spawn.spawn.profile_btn_numbered", id),
                null,
                () -> {
                    SpawnNetwork.switchProfile(id);
                    showProfileMenu = false;
                }
        ));
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.setSelected(profileId == currentEditIndex);
        button.setClipBounds(x, clipTop, x + width, clipBottom);
        return button;
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics g,
            int mx,
            int my,
            float pt
    ) {
        deferredTooltip = null;
        profileMenuMouseX = mx;
        profileMenuMouseY = my;

        if (isSelectedEntityInvalid()) {
            g.drawString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.title"),
                    rx,
                    96,
                    0xFFFFFFFF,
                    false
            );
            int textY = 170;
            for (var line : font.split(
                    Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.desc"),
                    rw
            )) {
                g.drawString(font, line, rx, textY, 0xFFFFFFFF, false);
                textY += font.lineHeight + 2;
            }
        }

        List<Component> tooltip = null;

        int firstRow = gridScroll.smoothIndexOffset();
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int startIndex = firstRow * COLS;
        int endIndex = Math.min(
                startIndex + (visibleRows + 1) * COLS,
                displayList.size()
        );

        enableUiScissor(g, gridX, gridY, gridX + gridW, gridY + gridH);
        for (int i = startIndex; i < endIndex; i++) {
            int column = (i - startIndex) % COLS;
            int row = (i - startIndex) / COLS;
            int x = gridX + column * CELL_SIZE;
            int y = gridY + row * CELL_SIZE - visualShift;

            String id =
                    displayList.get(i);

            BiomeSpawnConfig.EntityNode config =
                    profile.entities.get(id);

            boolean selected =
                    id.equals(selectedId);

            boolean hoveringCell =
                    mx >= x
                            && mx < x + CELL_SIZE
                            && my >= y
                            && my < y + CELL_SIZE;

            EntityPreviewRenderer.drawCheckerboard(
                    g,
                    x + 1,
                    y + 1,
                    CELL_SIZE - 2,
                    CELL_SIZE - 2
            );

            boolean modified =
                    editedEntities.isEdited(id);
            boolean invalidEntity =
                    !BiomeSpawnConfig.isEntityIdValid(id);

            if (invalidEntity) {
                GuiTheme.indicatorOutline(
                        g, x, y, CELL_SIZE, CELL_SIZE, GuiTheme.Indicator.DANGER, 3
                );
            } else if (hoveringCell) {
                GuiTheme.stateOutline(g, x, y, CELL_SIZE, CELL_SIZE, false, true, false, 3);
            } else if (selected) {
                GuiTheme.stateOutline(g, x, y, CELL_SIZE, CELL_SIZE, true, false, false, 3);
            } else if (modified) {
                GuiTheme.indicatorOutline(g, x, y, CELL_SIZE, CELL_SIZE, GuiTheme.Indicator.SUCCESS, 3);
            } else {
                GuiTheme.stateOutline(g, x, y, CELL_SIZE, CELL_SIZE, false, false, false);
            }

            renderAdaptiveEntity(
                    g,
                    x + 3,
                    y + 3,
                    CELL_SIZE - 6,
                    CELL_SIZE - 6,
                    id,
                    "grid:" + id,
                    hoveringCell
            );

            GuiTheme.Indicator indicator = config.block_all
                    ? GuiTheme.Indicator.DANGER
                    : config.enable_control
                    ? GuiTheme.Indicator.SUCCESS
                    : GuiTheme.Indicator.MUTED;
            GuiTheme.indicatorFill(g, x + 4, y + 4, 4, 4, indicator);

            if (hoveringCell) {
                tooltip = new ArrayList<>();
                tooltip.add(
                        Component.literal(
                                getEntityName(id)
                        )
                );

                tooltip.add(
                        Component.translatable(
                                "gui.entitycontrol.spawn.entity.tooltip.id",
                                Component.literal(id).withStyle(ChatFormatting.AQUA)
                        )
                );

                if (invalidEntity) {
                    tooltip.add(Component.translatable(
                            "gui.entitycontrol.spawn.spawn.invalid_entity.tooltip"
                    ));
                } else {
                    tooltip.add(
                            Component.translatable(
                                    "gui.entitycontrol.spawn.spawn.tooltip.model_zoom",
                                    Component.literal(String.valueOf(getEntityZoomPercent("grid:" + id))).withStyle(ChatFormatting.YELLOW)
                            )
                    );
                }
            }
        }
        disableUiScissor(g);

        if (maxScroll > 0) {
            GuiTheme.scrollbar(
                    gridScroll,
                    g,
                    mx,
                    my,
                    gridX + gridW + 4,
                    gridY,
                    4,
                    gridH,
                    20
            );
        }

        if (currentTab != null && !isSelectedEntityInvalid()) {
            currentTab.render(
                    g,
                    mx,
                    my,
                    pt
            );
        }

        if (showProfileMenu) {
            int menuMouseX = profileMenuMouseX;
            int menuMouseY = profileMenuMouseY;
            g.pose().pushPose();
            g.pose().translate(
                    0,
                    0,
                    500
            );

            GuiTheme.shadow(g, V_WIDTH, V_HEIGHT);

            int panelWidth = 220;
            int maxVisible = 6;
            int actualVisible = Math.min(globals.config_amount, maxVisible);
            int panelHeight = 65 + actualVisible * 25;
            int panelX = (V_WIDTH - panelWidth) / 2;
            int panelY = (V_HEIGHT - panelHeight) / 2;
            int countY = panelY + 32;
            int listY = panelY + 60;
            int minusX = panelX + 10;
            int plusX = panelX + panelWidth - 30;
            int minimumCount = Math.max(
                    BiomeSpawnConfig.MIN_PROFILE_COUNT,
                    Math.max(globals.current_index, currentEditIndex)
            );
            boolean canRemove = globals.config_amount > minimumCount;
            boolean canAdd = globals.config_amount < BiomeSpawnConfig.MAX_PROFILE_COUNT;
            GuiTheme.panelAlt(g, panelX, panelY, panelWidth, panelHeight);

            g.drawCenteredString(
                    font,
                    Component.translatable(
                            "gui.entitycontrol.spawn.spawn.profile.title"
                    ),
                    panelX + panelWidth / 2,
                    panelY + 12,
                    0xFFFFFF
            );

            StateButton decreaseButton = profileDecreaseButton(minusX, countY + 1, canRemove);
            StateButton increaseButton = profileIncreaseButton(plusX, countY + 1, canAdd);
            KineticWidgets.renderControl(decreaseButton, g, menuMouseX, menuMouseY, pt);
            KineticWidgets.renderControl(increaseButton, g, menuMouseX, menuMouseY, pt);

            g.drawCenteredString(
                    font,
                    Component.translatable(
                            "gui.entitycontrol.spawn.spawn.profile_count",
                            globals.config_amount
                    ),
                    panelX + panelWidth / 2,
                    countY + 5,
                    0xFFFFFF
            );

            profileScroll.update(globals.config_amount * 25, actualVisible * 25);
            double profileVisual = profileScroll.smoothOffset();
            int profileStart = Math.max(0, (int) Math.floor(profileVisual / 25D));
            int profileEnd = Math.min(profileStart + maxVisible + 1, globals.config_amount);

            enableUiScissor(g, panelX + 10, listY, panelX + panelWidth - 10, listY + actualVisible * 25);
            for (int i = profileStart; i < profileEnd; i++) {
                int profileId = i + 1;
                int buttonY = listY + (int) Math.round(i * 25D - profileVisual);
                StateButton profileButton = profileButton(
                        profileId,
                        panelX + 10,
                        buttonY + 2,
                        panelWidth - 20,
                        listY,
                        listY + actualVisible * 25
                );
                KineticWidgets.renderControl(profileButton, g, menuMouseX, menuMouseY, pt);
            }
            disableUiScissor(g);

            int profileMaxScroll = Math.max(0, globals.config_amount - maxVisible);
            if (profileMaxScroll > 0) {
                GuiTheme.scrollbar(
                        profileScroll, g, menuMouseX, menuMouseY,
                        panelX + panelWidth + 2, listY, 4, actualVisible * 25, 15
                );
            }

            if (menuMouseX >= minusX + 24 && menuMouseX <= plusX - 4
                    && menuMouseY >= countY && menuMouseY <= countY + 18) {
                KineticOverlays.requestTooltip(List.of(Component.translatable("gui.entitycontrol.spawn.spawn.profile_count.tooltip")), menuMouseX, menuMouseY);
            }

            g.pose().popPose();
        }

        if (!showProfileMenu
                && currentTab != null
                && !isSelectedEntityInvalid()) {
            List<Component> tabTooltip =
                    currentTab.getTooltip(
                            mx,
                            my
                    );

            if (tabTooltip != null) {
                tooltip = tabTooltip;
            }
        }

        if (tooltip != null
                && !tooltip.isEmpty()
                && !showProfileMenu
                && (
                currentTab == null
                        || isSelectedEntityInvalid()
                        || !currentTab.isMenuOpen(
                        mx,
                        my
                )
        )) {
            this.deferredTooltip =
                    tooltip;
        }
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int virtualMouseX,
            int virtualMouseY,
            int screenMouseX,
            int screenMouseY
    ) {
        if (deferredTooltip != null && !deferredTooltip.isEmpty()) {
            KineticOverlays.requestTooltip(deferredTooltip, screenMouseX, screenMouseY);
        }
    }

    public void renderAdaptiveEntity(
            GuiGraphics g,
            int boxX,
            int boxY,
            int boxW,
            int boxH,
            String id,
            String rotationKey,
            boolean hovered
    ) {
        ResourceLocation location =
                KineticResourceIds.tryParse(
                        id
                );

        boolean registered =
                location != null
                        && KineticRegistries.entityTypes().contains(
                        location
                );

        boolean rendered =
                registered
                        && entityPreviewRenderer.renderCanvas(
                        g,
                        id,
                        rotationKey,
                        boxX,
                        boxY,
                        boxW,
                        boxH,
                        hovered
                );

        if (!rendered) {
            float questionScale = 3.0F;
            g.pose().pushPose();
            g.pose().translate(
                    boxX + boxW / 2.0F,
                    boxY + boxH / 2.0F,
                    200.0F
            );
            g.pose().scale(questionScale, questionScale, 1.0F);
            g.drawCenteredString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.question_mark"),
                    0,
                    -font.lineHeight / 2,
                    0xFFFFFFFF
            );
            g.pose().popPose();
        }
    }

    private int getEntityZoomPercent(String key) {
        return entityPreviewRenderer.getZoomPercent(key);
    }

    private void adjustEntityZoom(String key, double delta) {
        entityPreviewRenderer.adjustZoom(
                key,
                delta
        );
    }

    private void updateProfileCount(int count) {
        int minimumCount = Math.max(
                BiomeSpawnConfig.MIN_PROFILE_COUNT,
                Math.max(globals.current_index, currentEditIndex)
        );
        int normalized = Math.max(minimumCount, Math.min(BiomeSpawnConfig.MAX_PROFILE_COUNT, count));
        if (normalized == globals.config_amount) return;
        globals.config_amount = normalized;
        profileScroll.update(normalized * 25, Math.min(normalized, 6) * 25);
    }

    @Override
    protected boolean canvasMouseClicked(
            double mx,
            double my,
            int btn
    ) {
        if (showProfileMenu) {
            int panelWidth = 220;
            int maxVisible = 6;
            int actualVisible = Math.min(globals.config_amount, maxVisible);
            int panelHeight = 65 + actualVisible * 25;
            int panelX = (V_WIDTH - panelWidth) / 2;
            int panelY = (V_HEIGHT - panelHeight) / 2;
            int countY = panelY + 32;
            int listY = panelY + 60;
            int minusX = panelX + 10;
            int plusX = panelX + panelWidth - 30;

            if (mx < panelX
                    || mx > panelX + panelWidth
                    || my < panelY
                    || my > panelY + panelHeight) {
                showProfileMenu = false;
                return true;
            }

            int minimumCount = Math.max(
                    BiomeSpawnConfig.MIN_PROFILE_COUNT,
                    Math.max(globals.current_index, currentEditIndex)
            );
            StateButton decreaseButton = profileDecreaseButton(
                    minusX, countY + 1, globals.config_amount > minimumCount
            );
            if (decreaseButton.mouseClicked(mx, my, btn)) return true;
            StateButton increaseButton = profileIncreaseButton(
                    plusX, countY + 1, globals.config_amount < BiomeSpawnConfig.MAX_PROFILE_COUNT
            );
            if (increaseButton.mouseClicked(mx, my, btn)) return true;

            profileScroll.update(globals.config_amount * 25, Math.min(globals.config_amount, maxVisible) * 25);
            int profileStart = Math.max(0, (int) Math.floor(profileScroll.smoothOffset() / 25D));
            int profileEnd = Math.min(profileStart + maxVisible + 1, globals.config_amount);
            for (int i = profileStart; i < profileEnd; i++) {
                int profileId = i + 1;
                int buttonY = listY + (int) Math.round(i * 25D - profileScroll.smoothOffset());
                StateButton profileButton = profileButton(
                        profileId, panelX + 10, buttonY + 2, panelWidth - 20,
                        listY, listY + actualVisible * 25
                );
                if (profileButton.mouseClicked(mx, my, btn)) return true;
            }

            return true;
        }

        if (KineticMouseButtons.isPrimary(btn)
                && currentTab != null
                && !isSelectedEntityInvalid()
                && currentTab.mouseClicked(
                mx,
                my,
                btn
        )) {
            clearControlFocus();
            return true;
        }

        boolean handled =
                super.canvasMouseClicked(
                        mx,
                        my,
                        btn
                );

        if (KineticMouseButtons.isPrimary(btn)) {
            if (maxScroll > 0
                    && mx >= gridX + gridW + 4
                    && mx <= gridX + gridW + 10
                    && my >= gridY
                    && my <= gridY + gridH) {
                draggingGridScroll = gridScroll.beginDrag(
                        mx, my, gridX + gridW + 4, gridY, 4, gridH, 20, 0
                );
                return draggingGridScroll;
            }

            if ((currentTab != tabCategories || isSelectedEntityInvalid())
                    && mx >= gridX
                    && mx < gridX + gridW
                    && my >= gridY
                    && my < gridY + gridH) {
                int index = getIndex(mx, my);

                if (index < displayList.size()) {
                    updateSelection(
                            displayList.get(
                                    index
                            )
                    );

                    clearControlFocus();
                    return true;
                }
            }
        }

        return handled;
    }

    private int getIndex(double mx, double my) {
        int column = (int) ((mx - gridX) / CELL_SIZE);
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int row = (int) Math.floor((my - gridY + visualShift) / CELL_SIZE);
        return (gridScroll.smoothIndexOffset() + row) * COLS + column;
    }

    @Override
    protected boolean canvasMouseDragged(
            double mx,
            double my,
            int btn,
            double dx,
            double dy
    ) {
        if (draggingGridScroll) {
            updateScrollFromMouse(
                    my
            );

            return true;
        }

        if (!showProfileMenu
                && currentTab != null
                && !isSelectedEntityInvalid()
                && currentTab.mouseDragged(
                mx,
                my,
                btn,
                dx,
                dy
        )) {
            return true;
        }

        return super.canvasMouseDragged(
                mx,
                my,
                btn,
                dx,
                dy
        );
    }

    @Override
    protected boolean canvasMouseReleased(
            double mx,
            double my,
            int btn
    ) {
        if (KineticMouseButtons.isPrimary(btn)
                && draggingGridScroll) {
            draggingGridScroll = false;
            gridScroll.release(btn);
            return true;
        }

        if (!showProfileMenu
                && currentTab != null
                && !isSelectedEntityInvalid()
                && currentTab.mouseReleased(
                mx,
                my,
                btn
        )) {
            return true;
        }

        return super.canvasMouseReleased(
                mx,
                my,
                btn
        );
    }

    private void updateScrollFromMouse(double my) {
        gridScroll.drag(my, gridY, gridH, 20);
    }

    @Override
    protected boolean canvasMouseScrolled(
            double mx,
            double my,
            double delta
    ) {
        if (showProfileMenu) {
            int maxVisible = 6;

            int profileMaxScroll =
                    Math.max(
                            0,
                            globals.config_amount
                                    - maxVisible
                    );

            if (profileMaxScroll > 0) {
                profileScroll.update(globals.config_amount * 25, maxVisible * 25);
                profileScroll.scroll(delta, 25D);
            }

            return true;
        }

        if (KineticClientRuntime.controlModifierDown()) {
            if (mx >= gridX
                    && mx < gridX + gridW
                    && my >= gridY
                    && my < gridY + gridH) {
                int index = getIndex(
                        mx,
                        my
                );

                if (index >= 0
                        && index < displayList.size()) {
                    adjustEntityZoom(
                            "grid:" + displayList.get(index),
                            delta
                    );
                    return true;
                }
            }
        }

        if (mx >= rx
                && mx < rx + rw) {
            return currentTab != null
                    && !isSelectedEntityInvalid()
                    && currentTab.mouseScrolled(
                    mx,
                    my,
                    delta
            );
        }

        if (maxScroll > 0
                && (currentTab != tabCategories || isSelectedEntityInvalid())) {
            return gridScroll.scroll(delta);
        }

        return super.canvasMouseScrolled(
                mx,
                my,
                delta
        );
    }

    @Override
    protected void screenRemoved() {
        entityPreviewRenderer.clear();
        editedEntities.clear();
        entityNameCache.clear();
        entitySearchDataCache.clear();
    }

}

