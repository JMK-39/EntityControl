package dev.xyat.entitycontrol.spawn.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.Scroll;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EditedEntryTracker;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

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
    public EditBox searchBox;
    public String selectedId;

    private EditBox rotationSpeedBox;
    private int rotationSpeedPercent = DEFAULT_ROTATION_SPEED_PERCENT;
    private boolean clockwiseRotation = true;
    private boolean updatingRotationSpeedBox;

    private Button btnTabRules;
    private Button btnTabBiomes;
    private Button btnTabCategories;
    private Button btnRestore;
    private EditBox invalidEntityIdBox;
    private Button btnRepairInvalidEntity;
    private Button btnDeleteInvalidEntity;

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
    private int profileMenuMouseX;
    private int profileMenuMouseY;

    private final EntityPreviewRenderer entityPreviewRenderer =
            new EntityPreviewRenderer();
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
        this.backupProfile = new BiomeSpawnConfig.ConfigProfile();
        this.currentEditIndex = editIndex;

        refreshAllEditedEntities();

        this.displayList = new ArrayList<>(
                profile.entities.keySet()
        );

        sortEntityList(this.displayList);

        useCanvas(
                V_WIDTH,
                V_HEIGHT,
                6
        );
        this.scaleMultiplier = 1f;
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

    public <T extends AbstractWidget> T addTabWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    @Override
    protected void buildUi() {
        tabBtnW = (rw - 10) / 3;

        int saveButtonW = 70;
        int exitButtonW = 46;
        int searchGap = 5;
        int searchW = gridW - saveButtonW - exitButtonW - searchGap * 2;

        searchBox = new EditBox(
                font,
                gridX,
                15,
                searchW,
                20,
                Component.empty()
        );

        searchBox.setResponder(this::updateSearch);

        searchBox.setTooltip(
                Tooltip.create(Component.translatable(
                        "gui.entitycontrol.spawn.spawn.tooltip.search"
                ))
        );

        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.save"
                                ),
                                button -> this.saveAndClose()
                        )
                        .bounds(
                                gridX + searchW + searchGap,
                                15,
                                saveButtonW,
                                20
                        )
                        .tooltip(Tooltip.create(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.tooltip.save"
                                )
                        ))
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.exit"
                                ),
                                button -> this.closeEditor()
                        )
                        .bounds(
                                gridX + searchW + searchGap + saveButtonW + searchGap,
                                15,
                                exitButtonW,
                                20
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                getRuleOverrideText(),
                                button -> {
                                    globals.enable_rule_override =
                                            !globals.enable_rule_override;

                                    button.setMessage(
                                            getRuleOverrideText()
                                    );
                                }
                        )
                        .bounds(
                                rx,
                                15,
                                tabBtnW,
                                20
                        )
                        .tooltip(Tooltip.create(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.tooltip.rule.override"
                                )
                        ))
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                getBiomeOverrideText(),
                                button -> {
                                    globals.enable_biome_override =
                                            !globals.enable_biome_override;

                                    button.setMessage(
                                            getBiomeOverrideText()
                                    );
                                }
                        )
                        .bounds(
                                rx + tabBtnW + 5,
                                15,
                                tabBtnW,
                                20
                        )
                        .tooltip(Tooltip.create(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.tooltip.biome.override"
                                )
                        ))
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                                "gui.entitycontrol.spawn.spawn.profile_btn"
                                        )
                                        .append(": " + currentEditIndex),
                                button ->
                                        showProfileMenu =
                                                !showProfileMenu
                        )
                        .bounds(
                                rx + (tabBtnW + 5) * 2,
                                15,
                                tabBtnW,
                                20
                        )
                        .tooltip(Tooltip.create(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.tooltip.profile"
                                )
                        ))
                        .build()
        );

        btnTabRules = Button.builder(
                        Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tab.rules"
                        ),
                        button -> switchTab(tabRules)
                )
                .bounds(
                        rx,
                        40,
                        tabBtnW,
                        20
                )
                .tooltip(Tooltip.create(
                        Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tooltip.tab.rules"
                        )
                ))
                .build();

        btnTabBiomes = Button.builder(
                        Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tab.biomes"
                        ),
                        button -> switchTab(tabBiomes)
                )
                .bounds(
                        rx + tabBtnW + 5,
                        40,
                        tabBtnW,
                        20
                )
                .tooltip(Tooltip.create(
                        Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tooltip.tab.biomes"
                        )
                ))
                .build();

        btnTabCategories = Button.builder(
                        Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tab.categories"
                        ),
                        button -> switchTab(tabCategories)
                )
                .bounds(
                        rx + (tabBtnW + 5) * 2,
                        40,
                        tabBtnW,
                        20
                )
                .tooltip(Tooltip.create(
                        Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tooltip.tab.cats"
                        )
                ))
                .build();

        this.addRenderableWidget(btnTabRules);
        this.addRenderableWidget(btnTabBiomes);
        this.addRenderableWidget(btnTabCategories);

        this.addRenderableWidget(
                Button.builder(
                                getAutoScanText(),
                                button -> {
                                    globals.auto_scan =
                                            !globals.auto_scan;

                                    button.setMessage(
                                            getAutoScanText()
                                    );

                                    if (globals.auto_scan) {
                                        SpawnNetwork.CHANNEL.sendToServer(
                                                new SpawnNetwork.RefreshSpawnBackupPacket(
                                                        currentEditIndex
                                                )
                                        );
                                    }
                                }
                        )
                        .bounds(
                                rx,
                                65,
                                tabBtnW,
                                20
                        )
                        .tooltip(Tooltip.create(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.tooltip.autoscan"
                                )
                        ))
                        .build()
        );

        btnRestore = this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.restore"
                                ),
                                button -> restoreSelectedEntity()
                        )
                        .bounds(
                                rx + tabBtnW + 5,
                                65,
                                tabBtnW,
                                20
                        )
                        .tooltip(Tooltip.create(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.spawn.tooltip.restore"
                                )
                        ))
                        .build()
        );
        btnRestore.active = false;

        int rotationControlX = rx + (tabBtnW + 5) * 2;
        int rotationLabelW = 70;
        int rotationInputW = Math.max(31, tabBtnW - rotationLabelW - 9);

        this.addRenderableWidget(
                Button.builder(
                                getRotationDirectionText(),
                                button -> {
                                    clockwiseRotation = !clockwiseRotation;
                                    entityPreviewRenderer.setClockwise(clockwiseRotation);
                                    button.setMessage(getRotationDirectionText());
                                }
                        )
                        .bounds(
                                rotationControlX,
                                65,
                                rotationLabelW,
                                20
                        )
                        .tooltip(Tooltip.create(
                                Component.translatable(
                                        "gui.entitycontrol.spawn.rotation.direction.tooltip"
                                )
                        ))
                        .build()
        );

        rotationSpeedBox = new EditBox(
                font,
                rotationControlX + rotationLabelW + 4,
                65,
                rotationInputW,
                20,
                Component.translatable(
                        "gui.entitycontrol.spawn.spawn.rotation_speed"
                )
        );

        rotationSpeedBox.setMaxLength(3);
        rotationSpeedBox.setFilter(this::isValidRotationSpeedInput);
        rotationSpeedBox.setValue(
                Integer.toString(rotationSpeedPercent)
        );
        rotationSpeedBox.setResponder(
                value -> applyRotationSpeedFromBox(false)
        );
        rotationSpeedBox.setTooltip(
                Tooltip.create(Component.translatable(
                        "gui.entitycontrol.spawn.spawn.tooltip.rotation_speed"
                ))
        );

        this.addRenderableWidget(rotationSpeedBox);

        int invalidActionWidth = (rw - 5) / 2;
        invalidEntityIdBox = new EditBox(
                font,
                rx,
                116,
                rw,
                20,
                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.id")
        );
        invalidEntityIdBox.setMaxLength(256);
        invalidEntityIdBox.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.invalid_entity.id.tooltip"
        )));
        invalidEntityIdBox.visible = false;
        this.addRenderableWidget(invalidEntityIdBox);

        btnRepairInvalidEntity = this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.repair"),
                                button -> repairSelectedInvalidEntity()
                        )
                        .bounds(rx, 141, invalidActionWidth, 20)
                        .tooltip(Tooltip.create(Component.translatable(
                                "gui.entitycontrol.spawn.spawn.invalid_entity.repair.tooltip"
                        )))
                        .build()
        );
        btnRepairInvalidEntity.visible = false;

        btnDeleteInvalidEntity = this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.delete"),
                                button -> deleteSelectedInvalidEntity()
                        )
                        .bounds(rx + invalidActionWidth + 5, 141, invalidActionWidth, 20)
                        .tooltip(Tooltip.create(Component.translatable(
                                "gui.entitycontrol.spawn.spawn.invalid_entity.delete.tooltip"
                        )))
                        .build()
        );
        btnDeleteInvalidEntity.visible = false;

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
        GuiOverlay.toast(
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
            invalidEntityIdBox.visible = invalid;
            invalidEntityIdBox.active = invalid;
            if (invalid && !invalidEntityIdBox.isFocused()) {
                invalidEntityIdBox.setValue(selectedId);
            }
        }

        if (btnRepairInvalidEntity != null) {
            btnRepairInvalidEntity.visible = invalid;
            btnRepairInvalidEntity.active = invalid;
        }

        if (btnDeleteInvalidEntity != null) {
            btnDeleteInvalidEntity.visible = invalid;
            btnDeleteInvalidEntity.active = invalid;
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
        ResourceLocation targetLocation = ResourceLocation.tryParse(rawTarget);
        if (targetLocation == null || !BiomeSpawnConfig.isEntityIdValid(targetLocation.toString())) {
            GuiOverlay.toast(Component.translatable(
                    "msg.entitycontrol.spawn.spawn.invalid_entity.repair_invalid"
            ));
            return;
        }

        String targetId = targetLocation.toString();
        if (profile.entities.containsKey(targetId)) {
            GuiOverlay.toast(Component.translatable(
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

        GuiOverlay.toast(Component.translatable(
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

        GuiOverlay.toast(Component.translatable(
                "msg.entitycontrol.spawn.spawn.invalid_entity.delete_success",
                removedId
        ));
    }

    private void saveAndClose() {
        applyRotationSpeedFromBox(true);
        String profileJson = BiomeSpawnConfig.GSON.toJson(profile);
        SpawnNetwork.CHANNEL.sendToServer(
                new SpawnNetwork.SavePacket(
                        globals.enable_rule_override,
                        globals.enable_biome_override,
                        globals.auto_scan,
                        globals.config_amount,
                        globals.current_index,
                        profileJson,
                        currentEditIndex
                )
        );
        commitDraft();
        closeEditor();
    }

    private void closeEditor() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeEditor();
    }

    private void switchTab(ITabModule tab) {
        tabRules.setVisible(false);
        tabBiomes.setVisible(false);
        tabCategories.setVisible(false);

        this.currentTab = tab;

        btnTabRules.active = tab != tabRules;
        btnTabBiomes.active = tab != tabBiomes;
        btnTabCategories.active = tab != tabCategories;

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
        ResourceLocation location = ResourceLocation.tryParse(id);

        if (lower.startsWith("@")) {
            return location != null
                    && location.getNamespace().contains(lower.substring(1));
        }

        if (lower.startsWith("#")) {
            if (location == null) return false;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(location);
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
            GuiOverlay.toast(Component.translatable(
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

        GuiOverlay.toast(Component.translatable(
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

        btnRestore.active = hasBackup && hasPersistentEntityCustomData(selectedId);
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

        searchBox.setFocused(false);

        if (currentTab != null && !isSelectedEntityInvalid()) {
            currentTab.updateSelection();
        }

        updateInvalidEntityEditorState();
        updateRestoreButtonState();
    }

    public String getEntityName(String id) {
        return entityNameCache.computeIfAbsent(id, key -> {
            ResourceLocation location = ResourceLocation.tryParse(key);
            if (location == null || !ForgeRegistries.ENTITY_TYPES.containsKey(location)) {
                return key;
            }

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(location);
            return type == null ? key : type.getDescription().getString();
        });
    }

    public String getTranslatedBiomeName(String id) {
        String key = Util.makeDescriptionId(
                "biome",
                new ResourceLocation(id)
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
    public void render(
            @NotNull GuiGraphics g,
            int mx,
            int my,
            float pt
    ) {
        deferredTooltip = null;
        profileMenuMouseX = (int) Math.floor(toVirtualX(mx));
        profileMenuMouseY = (int) Math.floor(toVirtualY(my));

        boolean intercept = false;

        try {
            double virtualMouseX =
                    toVirtualX(mx);

            double virtualMouseY =
                    toVirtualY(my);

            intercept =
                    showProfileMenu
                            || (
                            currentTab != null
                                    && currentTab.isMenuOpen(
                                    virtualMouseX,
                                    virtualMouseY
                            )
                    );
        } catch (Throwable ignored) {
        }

        if (intercept) {
            super.render(
                    g,
                    -1000,
                    -1000,
                    pt
            );
        } else {
            super.render(
                    g,
                    mx,
                    my,
                    pt
            );
        }

        if (deferredTooltip != null
                && !deferredTooltip.isEmpty()) {
            GuiOverlay.requestTooltip(deferredTooltip, mx, my);
        }
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics g,
            int mx,
            int my,
            float pt
    ) {
        g.fill(
                0,
                0,
                V_WIDTH,
                V_HEIGHT,
                0xFA1E1E1E
        );

        g.renderOutline(
                0,
                0,
                V_WIDTH,
                V_HEIGHT,
                0xFF444444
        );

        g.fill(
                gridX - 2,
                gridY - 2,
                gridX + gridW + 2,
                gridY + gridH + 2,
                0x88000000
        );

        g.renderOutline(
                gridX - 2,
                gridY - 2,
                gridW + 4,
                gridH + 4,
                0xFF555555
        );
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics g,
            int mx,
            int my,
            float pt
    ) {
        renderSearchPlaceholder(
                g,
                searchBox,
                "gui.entitycontrol.spawn.spawn.search_hint"
        );

        if (isSelectedEntityInvalid()) {
            g.drawString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawn.invalid_entity.title"),
                    rx,
                    96,
                    0xFFFFFFFF,
                    false
            );
            renderSearchPlaceholder(
                    g,
                    invalidEntityIdBox,
                    "gui.entitycontrol.spawn.spawn.invalid_entity.id_hint"
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

        enableCanvasScissor(g, gridX, gridY, gridX + gridW, gridY + gridH);
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
                renderThickOutline(
                        g,
                        x,
                        y,
                        0xFFFF3333,
                        3
                );
            } else if (hoveringCell) {
                renderThickOutline(
                        g,
                        x,
                        y,
                        0xFF3399FF,
                        3
                );
            } else if (selected) {
                renderThickOutline(
                        g,
                        x,
                        y,
                        0xFFFFD700,
                        3
                );
            } else if (modified) {
                renderThickOutline(
                        g,
                        x,
                        y,
                        0xFF33DD66,
                        3
                );
            } else {
                g.renderOutline(
                        x,
                        y,
                        CELL_SIZE,
                        CELL_SIZE,
                        0xFF555555
                );
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

            int color =
                    config.block_all
                            ? 0xFFFF5555
                            : config.enable_control
                            ? 0xFF55FF55
                            : 0xFF888888;

            g.fill(
                    x + 4,
                    y + 4,
                    x + 8,
                    y + 8,
                    color
            );

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
        g.disableScissor();

        if (maxScroll > 0) {
            int barX =
                    gridX
                            + gridW
                            + 4;

            int thumbH =
                    Scroll.calculateThumbHeight(
                            gridH,
                            visibleRows,
                            maxScroll + visibleRows,
                            20
                    );

            GuiTheme.scrollbar(
                    g,
                    mx,
                    my,
                    barX,
                    gridY,
                    4,
                    gridH,
                    thumbH,
                    maxScroll,
                    gridScroll.smoothOffset(),
                    draggingGridScroll
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

            g.fill(
                    0,
                    0,
                    V_WIDTH,
                    V_HEIGHT,
                    0xD0000000
            );

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
            boolean hoverMinus = menuMouseX >= minusX && menuMouseX <= minusX + 20 && menuMouseY >= countY && menuMouseY <= countY + 18;
            boolean hoverPlus = menuMouseX >= plusX && menuMouseX <= plusX + 20 && menuMouseY >= countY && menuMouseY <= countY + 18;

            g.fill(
                    panelX,
                    panelY,
                    panelX + panelWidth,
                    panelY + panelHeight,
                    0xFF1C1C1C
            );

            g.renderOutline(
                    panelX,
                    panelY,
                    panelWidth,
                    panelHeight,
                    0xFFAA00
            );

            g.drawCenteredString(
                    font,
                    Component.translatable(
                            "gui.entitycontrol.spawn.spawn.profile.title"
                    ),
                    panelX + panelWidth / 2,
                    panelY + 12,
                    0xFFFFFF
            );

            g.fill(
                    minusX,
                    countY,
                    minusX + 20,
                    countY + 18,
                    canRemove && hoverMinus ? 0xFF555555 : canRemove ? 0xFF333333 : 0xFF202020
            );
            g.renderOutline(minusX, countY, 20, 18, 0xFF000000);
            g.drawCenteredString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawn.profile_remove"),
                    minusX + 10,
                    countY + 5,
                    0xFFFFFF
            );

            g.fill(
                    plusX,
                    countY,
                    plusX + 20,
                    countY + 18,
                    canAdd && hoverPlus ? 0xFF555555 : canAdd ? 0xFF333333 : 0xFF202020
            );
            g.renderOutline(plusX, countY, 20, 18, 0xFF000000);
            g.drawCenteredString(
                    font,
                    Component.translatable("gui.entitycontrol.spawn.spawn.profile_add"),
                    plusX + 10,
                    countY + 5,
                    0xFFFFFF
            );

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

            enableCanvasScissor(g, panelX + 10, listY, panelX + panelWidth - 10, listY + actualVisible * 25);
            for (int i = profileStart; i < profileEnd; i++) {
                int profileId = i + 1;
                int buttonY = listY + (int) Math.round(i * 25D - profileVisual);
                boolean hover =
                        menuMouseX >= panelX + 10
                                && menuMouseX <= panelX + panelWidth - 10
                                && menuMouseY >= buttonY
                                && menuMouseY <= buttonY + 20;

                int background = hover ? 0xFF555555 : 0xFF333333;
                if (profileId == currentEditIndex) {
                    background = 0xFF22AA22;
                }

                g.fill(
                        panelX + 10,
                        buttonY,
                        panelX + panelWidth - 10,
                        buttonY + 20,
                        background
                );

                g.renderOutline(
                        panelX + 10,
                        buttonY,
                        panelWidth - 20,
                        20,
                        0xFF000000
                );

                g.drawCenteredString(
                        font,
                        Component.translatable(
                                "gui.entitycontrol.spawn.spawn.profile_btn_numbered",
                                profileId
                        ),
                        panelX + panelWidth / 2,
                        buttonY + 6,
                        0xFFFFFF
                );
            }
            g.disableScissor();

            int profileMaxScroll = Math.max(0, globals.config_amount - maxVisible);
            if (profileMaxScroll > 0) {
                int thumbH = Scroll.calculateThumbHeight(
                        actualVisible * 25,
                        maxVisible,
                        globals.config_amount,
                        15
                );

                GuiTheme.scrollbar(
                        profileScroll, g, menuMouseX, menuMouseY,
                        panelX + panelWidth + 2, listY, 4, actualVisible * 25, 15
                );
            }

            if (hoverMinus || hoverPlus
                    || (menuMouseX >= minusX + 24 && menuMouseX <= plusX - 4 && menuMouseY >= countY && menuMouseY <= countY + 18)) {
                GuiOverlay.requestTooltip(List.of(Component.translatable("gui.entitycontrol.spawn.spawn.profile_count.tooltip")), menuMouseX, menuMouseY);
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

    private static void renderThickOutline(
            GuiGraphics g,
            int x,
            int y,
            int color,
            int thickness
    ) {
        int safeThickness = Math.max(1, thickness);
        for (int i = 0; i < safeThickness; i++) {
            int innerWidth = 72 - i * 2;
            int innerHeight = 72 - i * 2;
            if (innerWidth <= 0 || innerHeight <= 0) break;
            g.renderOutline(
                    x + i,
                    y + i,
                    innerWidth,
                    innerHeight,
                    color
            );
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
                ResourceLocation.tryParse(
                        id
                );

        boolean registered =
                location != null
                        && ForgeRegistries.ENTITY_TYPES.containsKey(
                        location
                );

        boolean rendered =
                registered
                        && entityPreviewRenderer.render(
                        g,
                        id,
                        rotationKey,
                        boxX,
                        boxY,
                        boxW,
                        boxH,
                        this.canvasScale,
                        this.canvasX,
                        this.canvasY,
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

            if (btn == 0 && my >= countY && my <= countY + 18) {
                int minimumCount = Math.max(
                        BiomeSpawnConfig.MIN_PROFILE_COUNT,
                        Math.max(globals.current_index, currentEditIndex)
                );
                if (mx >= minusX && mx <= minusX + 20 && globals.config_amount > minimumCount) {
                    updateProfileCount(globals.config_amount - 1);
                    return true;
                }
                if (mx >= plusX && mx <= plusX + 20
                        && globals.config_amount < BiomeSpawnConfig.MAX_PROFILE_COUNT) {
                    updateProfileCount(globals.config_amount + 1);
                    return true;
                }
            }

            profileScroll.update(globals.config_amount * 25, Math.min(globals.config_amount, maxVisible) * 25);
            if (btn == 0
                    && mx >= panelX + 10
                    && mx <= panelX + panelWidth - 10
                    && my >= listY
                    && my < listY + Math.min(globals.config_amount, maxVisible) * 25) {
                int i = (int) Math.floor((my - listY + profileScroll.smoothOffset()) / 25D);
                if (i >= 0 && i < globals.config_amount) {
                    SpawnNetwork.CHANNEL.sendToServer(new SpawnNetwork.SwitchProfilePacket(i + 1));
                    showProfileMenu = false;
                    return true;
                }
            }

            return true;
        }

        if (btn == 0
                && currentTab != null
                && !isSelectedEntityInvalid()
                && currentTab.mouseClicked(
                mx,
                my,
                btn
        )) {
            this.setFocused(null);
            return true;
        }

        boolean handled =
                super.canvasMouseClicked(
                        mx,
                        my,
                        btn
                );

        if (btn == 0) {
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

                    this.setFocused(null);
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
        if (btn == 0
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
                profileScroll.scroll(delta, 25 / 3.0D);
            }

            return true;
        }

        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
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
    public void removed() {
        entityPreviewRenderer.clear();
        editedEntities.clear();
        entityNameCache.clear();
        entitySearchDataCache.clear();
        super.removed();
    }

}

