package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.render.KineticEntityPreview.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.entitycontrol.modifier.client.gui.panel.AttributePanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.BuffPanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.IModifierPanel;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class EntityModifierScreen extends KineticScreen {
    private enum CategoryFilter { FRIENDLY, AQUATIC, NEUTRAL, MONSTER, UNDEAD, MISC }

    private static final int CELL_SIZE = 68;
    private static final int CELL_GAP = 2;
    private static final int CELL_STRIDE = CELL_SIZE + CELL_GAP;
    private static final int GRID_LEFT_INSET = 7;
    private static final int GRID_RIGHT_GAP = 26;
    private static final int PANEL_W = 640;
    private static final int PANEL_H = 360;
    private static final int FILTER_BUTTON_WIDTH = 60;
    private static final int MODS_PER_PAGE = 8;

    private final Map<String, EntityModifierConfig.EntityEditData> localData =
            new TreeMap<>();

    record EntityGuiInfo(
            String id,
            String translatedName,
            LivingEntity entity
    ) {
    }

    private final List<EntityGuiInfo> allEntities =
            new ArrayList<>();
    private final List<String> availableMods = new ArrayList<>();
    private final EnumSet<CategoryFilter> selectedCategories = EnumSet.noneOf(CategoryFilter.class);
    private final Set<String> selectedMods = new TreeSet<>();

    private final EditedEntryTracker<EntityGuiInfo> editedEntities =
            new EditedEntryTracker<>();

    private final KineticSearch.Model<EntityGuiInfo> entityModel;
    private final GridScrollController gridScroll =
            new GridScrollController();

    private final EntityPreviewRenderer entityPreviewRenderer =
            KineticWidgets.createEntityPreviewRenderer();

    private EntityGuiInfo selectedEntity;
    private KineticEditBox searchBox;
    private List<Component> deferredEntityTooltip;

    private int gridCols;
    private int gridRowsVisible;
    private int gridActualWidth;
    private int startX;
    private int startY;

    private final List<IModifierPanel> panels =
            new ArrayList<>();

    private IModifierPanel currentPanel;
    private int savedActiveIdx;

    private StateButton saveBtn;
    private StateButton resetBtn;
    private StateButton attrTabBtn;
    private StateButton buffTabBtn;
    private StateButton globalExpandButton;
    private StateButton filterButton;
    private boolean globalMode;
    private String entityQuery = "";
    private String selectedGlobalAttribute;
    private String selectedIndividualAttribute;
    private int individualPanelIdx;

    private int gridX() { return startX + GRID_LEFT_INSET; }
    private int gridY() { return startY + 6; }
    private int gridHeight() { return gridRowsVisible * CELL_STRIDE - CELL_GAP; }
    private int rightPanelX() { return startX + gridActualWidth + GRID_RIGHT_GAP; }
    private int rightPanelWidth() { return PANEL_W - (rightPanelX() - startX) - 15; }

    private int tabWidth(String key) {
        return font.width(Component.translatable(key).getString()) + 16;
    }

    public int panelSearchWidth(int panelWidth) {
        int attributeTabWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.attributes");
        int effectTabWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.buffs");
        return Math.max(80, panelWidth - attributeTabWidth - effectTabWidth - 22);
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        int gridX = gridX();
        int gridY = gridY();
        if (mouseX < gridX || mouseX >= gridX + gridActualWidth
                || mouseY < gridY || mouseY >= gridY + gridHeight()) return -1;
        int columnOffset = (int) (mouseX - gridX);
        int rowOffset = (int) (mouseY - gridY + gridScroll.visualShift(CELL_STRIDE));
        if (columnOffset % CELL_STRIDE >= CELL_SIZE || rowOffset % CELL_STRIDE >= CELL_SIZE) return -1;
        int column = columnOffset / CELL_STRIDE;
        int row = rowOffset / CELL_STRIDE;
        return (gridScroll.smoothIndexOffset() + row) * gridCols + column;
    }

    public EntityModifierScreen(String serverSnapshotJson) {
        this(null, serverSnapshotJson);
    }

    public EntityModifierScreen(Screen parent, String serverSnapshotJson) {
        super(Component.translatable(
                "gui.entitycontrol.modifier.modifier.title"
        ));
        setParentScreen(parent);

        Map<String, EntityModifierConfig.EntityEditData> snapshot =
                EntityModifierConfig.GSON.fromJson(
                        serverSnapshotJson,
                        new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }
                                .getType()
                );
        if (snapshot == null) {
            throw new IllegalArgumentException("Server entity-modifier snapshot is null");
        }
        for (Map.Entry<String, EntityModifierConfig.EntityEditData> entry : snapshot.entrySet()) {
            EntityModifierConfig.EntityEditData value = entry.getValue();
            if (entry.getKey() == null || value == null || value.attributes == null || value.buffs == null) {
                throw new IllegalArgumentException("Server entity-modifier snapshot is malformed");
            }
        }
        if (snapshot != null) localData.putAll(snapshot);

        localData.computeIfAbsent(
                "__global__",
                key -> new EntityModifierConfig.EntityEditData()
        );

        allEntities.addAll(
                EntityModifierGuiCache.getEntities()
        );
        allEntities.stream()
                .map(info -> KineticResourceIds.tryParse(info.id()))
                .filter(java.util.Objects::nonNull)
                .map(ResourceLocation::getNamespace)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .forEach(availableMods::add);

        editedEntities.refresh(
                allEntities,
                this::isTrulyModified
        );

        entityModel = new KineticSearch.Model<>(
                allEntities,
                (info, query) ->
                        KineticSearch.match(
                                buildSearchData(info),
                                query
                        )
        );

        entityModel.setComparator(
                editedEntities.comparator(
                        Comparator.comparing(
                                EntityGuiInfo::id
                        )
                )
        );

        refreshEntityModel("");
        configureStandaloneDraft(
                this::copyLocalData,
                this::restoreLocalData
        );
    }

    private Map<String, EntityModifierConfig.EntityEditData> copyLocalData() {
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType();
        Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                EntityModifierConfig.GSON.toJson(localData),
                type
        );
        return copy == null ? new TreeMap<>() : new TreeMap<>(copy);
    }

    private void restoreLocalData(Map<String, EntityModifierConfig.EntityEditData> snapshot) {
        localData.clear();
        if (snapshot != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType();
            Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                    EntityModifierConfig.GSON.toJson(snapshot),
                    type
            );
            if (copy != null) localData.putAll(copy);
        }
        localData.computeIfAbsent("__global__", key -> new EntityModifierConfig.EntityEditData());
        editedEntities.refresh(allEntities, this::isTrulyModified);
        refreshEntityModel(searchBox == null ? "" : searchBox.getValue());
        updateGridScrollRange();
        rebuildUi();
    }

    public Map<String, EntityModifierConfig.EntityEditData> getLocalData() {
        return localData;
    }

    public Font getFont() {
        return font;
    }


    private String buildSearchData(EntityGuiInfo info) {
        return info.id().toLowerCase()
                + " "
                + info.translatedName().toLowerCase()
                + " "
                + KineticSearch.pinyin(
                        info.translatedName()
                );
    }

    private CategoryFilter categoryOf(EntityGuiInfo info) {
        LivingEntity entity = info.entity();
        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS) {
            return CategoryFilter.AQUATIC;
        }
        if (entity.getMobType() == MobType.UNDEAD) return CategoryFilter.UNDEAD;
        if (entity instanceof NeutralMob) return CategoryFilter.NEUTRAL;
        if (category == MobCategory.MONSTER) return CategoryFilter.MONSTER;
        if (category == MobCategory.CREATURE) return CategoryFilter.FRIENDLY;
        return CategoryFilter.MISC;
    }

    private boolean matchesEntityFilters(EntityGuiInfo info) {
        if (!selectedCategories.isEmpty() && !selectedCategories.contains(categoryOf(info))) return false;
        if (selectedMods.isEmpty()) return true;
        ResourceLocation id = KineticResourceIds.tryParse(info.id());
        return id != null && selectedMods.contains(id.getNamespace());
    }

    private void refreshEntityModel(String query) {
        entityModel.setSource(allEntities.stream().filter(this::matchesEntityFilters).toList());
        entityModel.refresh(query);
    }

    private boolean isTrulyModified(EntityGuiInfo info) {
        if (!localData.containsKey(info.id())) {
            return false;
        }

        EntityModifierConfig.EntityEditData data =
                localData.get(info.id());

        if (!data.buffs.isEmpty() || !data.attributeRules.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Double> entry
                : data.attributes.entrySet()) {
            ResourceLocation attributeId = KineticResourceIds.tryParse(entry.getKey());
            Attribute attribute = attributeId == null
                    ? null
                    : KineticRegistries.attributes().get(attributeId);

            if (attribute == null) {
                continue;
            }

            double defaultValue =
                    info.entity().getAttributes()
                            .hasAttribute(attribute)
                            ? info.entity().getAttributes()
                                    .getBaseValue(attribute)
                            : attribute.getDefaultValue();

            if (Math.abs(
                    entry.getValue() - defaultValue
            ) > 0.0001D) {
                return true;
            }
        }

        return false;
    }

    private void refreshSelectedEditedState() {
        if (selectedEntity == null) {
            return;
        }

        boolean edited =
                isTrulyModified(selectedEntity);

        if (!editedEntities.update(
                selectedEntity,
                edited
        )) {
            return;
        }

        refreshEntityModel(
                searchBox == null
                        ? ""
                        : searchBox.getValue()
        );

        updateGridScrollRange();

        if (edited) {
            gridScroll.reset();
        }
    }

    private void setActivePanel(
            IModifierPanel panel,
            int index
    ) {
        currentPanel = panel;
        savedActiveIdx = index;
        updatePanelVisibility();
    }

    private void updatePanelVisibility() {
        for (IModifierPanel panel : panels) {
            panel.setVisible(
                    panel == currentPanel
                            && (selectedEntity != null || globalMode)
            );
        }

        if (resetBtn != null) {
            resetBtn.setEnabled(selectedEntity != null || globalMode);
        }
    }

    @Override
    protected void buildUi() {
        editedEntities.refresh(allEntities, this::isTrulyModified);
        startX = (canvasWidth() - PANEL_W) / 2;
        startY = (canvasHeight() - PANEL_H) / 2;

        gridCols = 4;
        gridActualWidth = gridCols * CELL_STRIDE - CELL_GAP;
        gridRowsVisible = 5;

        int rightX = rightPanelX();
        int rightWidth = rightPanelWidth();
        int actionButtonY = startY + PANEL_H - 24;

        searchBox = addTextField(
                rightX + 8,
                startY + 5,
                rightWidth - 82,
                Component.empty(),
                Component.translatable("gui.entitycontrol.modifier.modifier.search_entity"),
                null,
                null
        );
        searchBox.setResponder(this::updateSearch);
        if (!entityQuery.isEmpty()) searchBox.setValue(entityQuery);
        filterButton = addCompactButton(
                rightX + rightWidth - FILTER_BUTTON_WIDTH - 8,
                startY + 5,
                FILTER_BUTTON_WIDTH,
                Component.translatable("gui.kineticcore.entity_selector.filter"),
                Component.translatable("gui.entitycontrol.modifier.modifier.filter.tooltip"),
                this::showEntityFilterMenu);
        filterButton.setSelected(!selectedCategories.isEmpty() || !selectedMods.isEmpty());
        // The global editor expands inside this screen; the individual selection is retained
        // so collapsing it restores exactly the entity that was being edited.
        globalExpandButton = addButton(
                rightX + 8, actionButtonY, 92,
                Component.translatable(globalMode
                        ? "gui.entitycontrol.modifier.global.collapse"
                        : "gui.entitycontrol.modifier.global.expand"),
                Component.translatable("gui.entitycontrol.modifier.global.expand_tooltip"),
                () -> setGlobalExpanded(!globalMode, globalExpandButton));
        globalExpandButton.setSelected(globalMode);

        panels.clear();
        panels.add(new AttributePanel());
        panels.add(new BuffPanel());

        for (IModifierPanel panel : panels) {
            panel.init(
                    this,
                    rightX + 4,
                    startY + 30,
                    rightWidth - 8,
                    PANEL_H - 70
            );
        }

        currentPanel = panels.get(Math.min(savedActiveIdx, panels.size() - 1));

        if (selectedEntity != null || globalMode) {
            for (IModifierPanel panel : panels) {
                panel.onEntitySelected(globalMode ? EntityModifierConfig.GLOBAL_KEY : selectedEntity.id(),
                        globalMode ? null : selectedEntity.entity());
            }
            String restoreId = globalMode ? selectedGlobalAttribute : selectedIndividualAttribute;
            if (restoreId != null && panels.get(0) instanceof AttributePanel attributes) {
                attributes.restoreSelection(restoreId);
            }
        }

        int tabY = startY + 34;
        int attrWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.attributes");
        int buffWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.buffs");

        attrTabBtn = addButton(
                rightX + 8 + panelSearchWidth(rightWidth - 8) + 6,
                tabY,
                attrWidth,
                Component.translatable("gui.entitycontrol.modifier.modifier.tab.attributes"),
                null,
                () -> setActivePanel(panels.get(0), 0)
        );

        buffTabBtn = addButton(
                rightX + 8 + panelSearchWidth(rightWidth - 8) + 12 + attrWidth,
                tabY,
                buffWidth,
                Component.translatable("gui.entitycontrol.modifier.modifier.tab.buffs"),
                null,
                () -> setActivePanel(panels.get(1), 1)
        );

        saveBtn = addButton(
                rightX + rightWidth - 215,
                actionButtonY,
                70,
                Component.translatable("gui.entitycontrol.modifier.modifier.save"),
                null,
                () -> EntityModifierNetwork.saveConfig(EntityModifierConfig.GSON.toJson(localData))
        );

        resetBtn = addButton(
                rightX + rightWidth - 140,
                actionButtonY,
                65,
                Component.translatable("gui.entitycontrol.modifier.modifier.reset"),
                null,
                this::resetSelectedEntity
        );

        addButton(
                rightX + rightWidth - 70,
                actionButtonY,
                65,
                Component.translatable("gui.entitycontrol.modifier.config.back"),
                null,
                this::navigateBack
        );

        updateSearch(searchBox.getValue());
        updatePanelVisibility();
    }

    private void resetSelectedEntity() {
        if (globalMode) {
            selectedGlobalAttribute = null;
            localData.put(EntityModifierConfig.GLOBAL_KEY, new EntityModifierConfig.EntityEditData());
            for (IModifierPanel panel : panels) panel.onEntitySelected(EntityModifierConfig.GLOBAL_KEY, null);
            rebuildUi();
            return;
        }
        if (selectedEntity == null) {
            return;
        }

        selectedIndividualAttribute = null;
        localData.remove(
                selectedEntity.id()
        );

        for (IModifierPanel panel : panels) {
            panel.onEntitySelected(
                    selectedEntity.id(),
                    selectedEntity.entity()
            );
        }

        editedEntities.update(
                selectedEntity,
                false
        );

        refreshEntityModel(
                searchBox.getValue()
        );

        updateGridScrollRange();
    }

    private void updateSearch(String query) {
        entityQuery = query == null ? "" : query;
        refreshEntityModel(entityQuery);
        gridScroll.reset();
        updateGridScrollRange();
        if (filterButton != null) {
            filterButton.setSelected(!selectedCategories.isEmpty() || !selectedMods.isEmpty());
        }
    }

    private void showEntityFilterMenu() {
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        entries.add(KineticOverlays.MenuItem.action(
                Component.translatable("gui.kineticcore.entity_selector.filter.reset"), () -> {
                    selectedCategories.clear();
                    selectedMods.clear();
                    updateSearch(searchBox.getValue());
                    showEntityFilterMenu();
                }));
        entries.add(KineticOverlays.MenuItem.separator());
        Component allCategories = Component.translatable("gui.kineticcore.entity_selector.category.all");
        entries.add(KineticOverlays.MenuItem.toggle(allCategories, allCategories,
                selectedCategories.isEmpty(), () -> {
                    selectedCategories.clear();
                    updateSearch(searchBox.getValue());
                    showEntityFilterMenu();
                }));
        for (CategoryFilter category : CategoryFilter.values()) {
            Component name = Component.translatable("gui.kineticcore.entity_selector.category."
                    + category.name().toLowerCase(Locale.ROOT));
            entries.add(KineticOverlays.MenuItem.toggle(name, name,
                    selectedCategories.contains(category), () -> {
                        if (!selectedCategories.add(category)) selectedCategories.remove(category);
                        updateSearch(searchBox.getValue());
                        showEntityFilterMenu();
                    }));
        }
        entries.add(KineticOverlays.MenuItem.separator());
        entries.add(KineticOverlays.MenuItem.action(
                Component.translatable("gui.kineticcore.entity_selector.filter.mods", selectedMods.size()),
                () -> showModMenu(0)));
        openContextMenu(filterButton.getX(), filterButton.getY() + 20, entries, 140);
    }

    private void showModMenu(int requestedPage) {
        int lastPage = Math.max(0, (availableMods.size() - 1) / MODS_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, lastPage));
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        entries.add(KineticOverlays.MenuItem.action(
                Component.translatable("gui.kineticcore.entity_selector.filter.back"),
                this::showEntityFilterMenu));
        Component allMods = Component.translatable("gui.kineticcore.entity_selector.filter.mod_all");
        entries.add(KineticOverlays.MenuItem.toggle(allMods, allMods, selectedMods.isEmpty(), () -> {
            selectedMods.clear();
            updateSearch(searchBox.getValue());
            showModMenu(page);
        }));
        entries.add(KineticOverlays.MenuItem.separator());
        int from = page * MODS_PER_PAGE;
        int to = Math.min(availableMods.size(), from + MODS_PER_PAGE);
        for (int i = from; i < to; i++) {
            String mod = availableMods.get(i);
            Component name = Component.literal(mod);
            entries.add(KineticOverlays.MenuItem.toggle(name, name, selectedMods.contains(mod), () -> {
                if (!selectedMods.add(mod)) selectedMods.remove(mod);
                updateSearch(searchBox.getValue());
                showModMenu(page);
            }));
        }
        entries.add(KineticOverlays.MenuItem.separator());
        if (page > 0) {
            entries.add(KineticOverlays.MenuItem.action(
                    Component.translatable("gui.kineticcore.entity_selector.filter.previous"),
                    () -> showModMenu(page - 1)));
        }
        if (page < lastPage) {
            entries.add(KineticOverlays.MenuItem.action(
                    Component.translatable("gui.kineticcore.entity_selector.filter.next"),
                    () -> showModMenu(page + 1)));
        }
        openContextMenu(filterButton.getX(), filterButton.getY() + 20, entries, 140);
    }

    private EntityModifierConfig.EntityEditData globalData() {
        return localData.computeIfAbsent(EntityModifierConfig.GLOBAL_KEY,
                key -> new EntityModifierConfig.EntityEditData());
    }

    /** Exact selectable living entity IDs; only the core selector owns category filtering. */
    public List<String> selectableLivingEntityIds() {
        return allEntities.stream().map(EntityGuiInfo::id).toList();
    }

    public boolean isGlobalMode() {
        return globalMode;
    }

    public void selectedGlobalAttribute(String id) {
        selectedGlobalAttribute = id;
    }

    public String selectedGlobalAttribute() {
        return selectedGlobalAttribute;
    }

    public void selectedIndividualAttribute(String id) {
        selectedIndividualAttribute = id;
    }

    /** Expand the global editor in place; never destroy widgets during a click callback. */
    private void setGlobalExpanded(boolean expanded, StateButton toggle) {
        if (globalMode == expanded) return;
        if (expanded) individualPanelIdx = savedActiveIdx;
        globalMode = expanded;
        toggle.setMessage(Component.translatable(globalMode
                ? "gui.entitycontrol.modifier.global.collapse"
                : "gui.entitycontrol.modifier.global.expand"));
        toggle.setSelected(globalMode);
        if (panels.isEmpty()) return;
        for (IModifierPanel panel : panels) {
            panel.onEntitySelected(globalMode ? EntityModifierConfig.GLOBAL_KEY
                            : selectedEntity == null ? null : selectedEntity.id(),
                    globalMode || selectedEntity == null ? null : selectedEntity.entity());
        }
        setActivePanel(panels.get(globalMode ? 0
                : Math.min(individualPanelIdx, panels.size() - 1)),
                globalMode ? 0 : Math.min(individualPanelIdx, panels.size() - 1));
        String restoreId = globalMode ? selectedGlobalAttribute : selectedIndividualAttribute;
        if (restoreId != null && panels.get(0) instanceof AttributePanel attributes) {
            attributes.restoreSelection(restoreId);
        }
    }

    private boolean globalTargetSelected(EntityGuiInfo info) {
        if (!globalMode || selectedGlobalAttribute == null) return false;
        EntityModifierConfig.AttributeRule rule = globalData().attributeRules.get(selectedGlobalAttribute);
        return rule != null && rule.appliesTo(info.id());
    }

    private void updateGridScrollRange() {
        int totalRows = (
                entityModel.items().size()
                        + gridCols
                        - 1
        ) / gridCols;

        gridScroll.update(
                totalRows,
                gridRowsVisible
        );
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.panel(
                graphics,
                startX,
                startY,
                PANEL_W,
                PANEL_H
        );

        GuiTheme.panelAlt(
                graphics,
                rightPanelX(),
                startY + 27,
                rightPanelWidth() - 2,
                PANEL_H - 63
        );
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        deferredEntityTooltip = null;
        refreshSelectedEditedState();
        int gridX = gridX();
        int gridY = gridY();
        int gridHeight = gridHeight();

        GuiTheme.panelAlt(
                graphics, gridX - 2, gridY - 3,
                gridActualWidth + 14, gridHeight + 6
        );

        List<EntityGuiInfo> displayEntities =
                entityModel.items();

        int firstRow = gridScroll.smoothIndexOffset();
        int shift = gridScroll.visualShift(CELL_STRIDE);
        int startIndex = firstRow * gridCols;

        int endIndex = Math.min(
                startIndex
                        + (gridRowsVisible + 1) * gridCols,
                displayEntities.size()
        );

                enableUiScissor(graphics, gridX, gridY, gridX + gridActualWidth, gridY + gridHeight);
        try {
for (int i = startIndex;
             i < endIndex;
             i++) {
            EntityGuiInfo info =
                    displayEntities.get(i);

            int localIndex =
                    i - startIndex;

            int cellX =
                    gridX
                            + localIndex % gridCols
                            * CELL_STRIDE;

            int cellY =
                    gridY
                            + localIndex / gridCols
                            * CELL_STRIDE
                            - shift;

            boolean selected = !globalMode && selectedEntity == info;
            boolean applies = globalMode && globalTargetSelected(info);

            boolean edited =
                    editedEntities.isEdited(info);

            boolean hovered =
                    mouseX >= gridX && mouseX < gridX + gridActualWidth
                            && mouseY >= gridY && mouseY < gridY + gridHeight
                            && mouseX >= cellX && mouseX < cellX + CELL_SIZE
                            && mouseY >= cellY && mouseY < cellY + CELL_SIZE;

            EntityPreviewRenderer.drawCheckerboard(
                    graphics,
                    cellX + 1,
                    cellY + 1,
                    CELL_SIZE - 2,
                    CELL_SIZE - 2
            );

            if (selected) {
                GuiTheme.stateOutline(
                        graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, true, false, false
                );
            } else if (applies) {
                GuiTheme.indicatorOutline(
                        graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, GuiTheme.Indicator.SUCCESS
                );
            } else if (hovered) {
                GuiTheme.stateOutline(
                        graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, false, true, false
                );
            } else if (edited) {
                GuiTheme.indicatorOutline(
                        graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, GuiTheme.Indicator.SUCCESS
                );
            } else {
                GuiTheme.stateOutline(
                        graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, false, false, false
                );
            }

            entityPreviewRenderer.renderCanvas(
                    graphics,
                    info.entity(),
                    "modifier:grid:" + info.id(),
                    cellX + 2,
                    cellY + 2,
                    CELL_SIZE - 4,
                    CELL_SIZE - 4,
                    hovered
            );
            int previewTop = Math.max(cellY, gridY);
            int previewBottom = Math.min(cellY + CELL_SIZE, gridY + gridHeight);
            if (previewBottom > previewTop) {
                registerPreviewWheelTarget(entityPreviewRenderer, "modifier:grid:" + info.id(),
                        cellX, previewTop, CELL_SIZE, previewBottom - previewTop);
            }
            if (hovered) {
                deferredEntityTooltip = previewTooltip(info, "modifier:grid:" + info.id());
            }
        }
        } finally {
            disableUiScissor(graphics);
        }

        GuiTheme.scrollbar(
                gridScroll,
                graphics,
                mouseX,
                mouseY,
                gridX + gridActualWidth + 4,
                gridY,
                4,
                gridHeight,
                20
        );


        int rightX = rightPanelX() + 15;
        int rightWidth = rightPanelWidth() - 15;

        if (selectedEntity != null || globalMode) {
            if (currentPanel != null) {
                currentPanel.render(
                        graphics,
                        mouseX,
                        mouseY,
                        partialTick
                );
            }
        } else {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "gui.entitycontrol.modifier.modifier.no_entity"
                    ).getString(),
                    rightX + rightWidth / 2,
                    startY + PANEL_H / 2,
                    0xFFFFFF
            );
        }

        if (saveBtn != null
                && saveBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            KineticOverlays.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.save.tooltip"
                            ),
                            200
                    ), mouseX, mouseY);
        }

        if (resetBtn != null
                && resetBtn.isEnabled()
                && resetBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            KineticOverlays.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.reset.tooltip"
                            ),
                            200
                    ), mouseX, mouseY);
        }

        if (attrTabBtn != null
                && attrTabBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            KineticOverlays.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.tab.attributes.tooltip"
                            ),
                            220
                    ), mouseX, mouseY);
        }

        if (buffTabBtn != null
                && buffTabBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            KineticOverlays.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.tab.buffs.tooltip"
                            ),
                            220
                    ), mouseX, mouseY);
        }
    }

    private List<Component> previewTooltip(EntityGuiInfo info, String stateKey) {
        return List.of(
                Component.translatable("gui.entitycontrol.modifier.modifier.entity_name", info.translatedName()),
                Component.translatable("gui.entitycontrol.modifier.modifier.entity_id", info.id()),
                Component.translatable("gui.entitycontrol.modifier.modifier.preview_zoom_hint",
                        entityPreviewRenderer.getZoomPercent(stateKey))
        );
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int virtualMouseX, int virtualMouseY,
                                  int screenMouseX, int screenMouseY) {
        if (deferredEntityTooltip != null) {
            showTooltip(deferredEntityTooltip, null);
        }
    }

    @Override
    protected boolean canvasMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        boolean clickedWidget =
                super.canvasMouseClicked(
                        mouseX,
                        mouseY,
                        button
                );

        int gridX = gridX();
        int gridY = gridY();
        int gridHeight = gridHeight();

        if (KineticMouseButtons.isPrimary(button)
                && gridScroll.beginDrag(
                        mouseX,
                        mouseY,
                        gridX + gridActualWidth + 4,
                        gridY,
                        4,
                        gridHeight,
                        20,
                        0
                )) {
            return true;
        }

        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0) {
            List<EntityGuiInfo> displayEntities =
                    entityModel.items();

            if (index < displayEntities.size()) {
                if (globalMode) {
                    if (KineticMouseButtons.isPrimary(button)
                            && currentPanel instanceof AttributePanel attributes) {
                        attributes.toggleGlobalTarget(displayEntities.get(index).id(), selectableLivingEntityIds());
                    }
                    return true;
                }
                selectedEntity =
                        displayEntities.get(index);
                selectedIndividualAttribute = null;

                for (IModifierPanel panel : panels) {
                    panel.onEntitySelected(
                            selectedEntity.id(),
                            selectedEntity.entity()
                    );
                }

                updatePanelVisibility();
                return true;
            }
        }

        return clickedWidget
                || currentPanel != null
                && currentPanel.mouseClicked(
                        mouseX,
                        mouseY,
                        button
                );
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (gridScroll.drag(
                mouseY,
                gridY(),
                gridHeight(),
                20
        )) {
            return true;
        }

        if (currentPanel != null
                && currentPanel.mouseDragged(
                        mouseX,
                        mouseY,
                        button,
                        dragX,
                        dragY
                )) {
            return true;
        }

        return super.canvasMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean canvasMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        boolean released = gridScroll.release(button);

        if (currentPanel != null) {
            currentPanel.mouseReleased(
                    mouseX,
                    mouseY,
                    button
            );
        }

        return released
                || super.canvasMouseReleased(
                        mouseX,
                        mouseY,
                        button
                );
    }

    @Override
    protected boolean canvasMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (delta != 0D) {
            int index = gridIndexAt(mouseX, mouseY);
            List<EntityGuiInfo> visible = entityModel.items();
            if (index >= 0 && index < visible.size()) {
                if (entityPreviewRenderer.handleControlWheel(
                        "modifier:grid:" + visible.get(index).id(), true, delta)) return true;
            }
        }
        if (mouseX
                < rightPanelX()) {
            return gridScroll.scroll(delta)
                    || super.canvasMouseScrolled(
                            mouseX,
                            mouseY,
                            delta
                    );
        }

        if (currentPanel != null
                && currentPanel.mouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                )) {
            return true;
        }

        return super.canvasMouseScrolled(
                mouseX,
                mouseY,
                delta
        );
    }

    public void handleSaveResult(boolean success) {
        if (success) commitDraft();
    }

    @Override
    protected void screenRemoved() {
        entityPreviewRenderer.clear();
        editedEntities.clear();
    }
}
