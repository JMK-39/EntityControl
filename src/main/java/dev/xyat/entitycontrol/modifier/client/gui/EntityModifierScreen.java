package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.entitycontrol.modifier.client.gui.panel.AttributePanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.BuffPanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.IModifierPanel;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl;
import dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class EntityModifierScreen extends KineticPage {
    private enum CategoryFilter { FRIENDLY, AQUATIC, NEUTRAL, MONSTER, UNDEAD, MISC }

    private static final Comparator<EntityGuiInfo> ENTITY_ID_ORDER = Comparator.comparing(EntityGuiInfo::id);
    private static final int CELL_SIZE = 68;
    private static final int CELL_GAP = 2;
    private static final int CELL_STRIDE = CELL_SIZE + CELL_GAP;
    private static final int GRID_LEFT_INSET = 7;
    private static final int GRID_RIGHT_GAP = 26;
    private static final int PANEL_W = 640;
    private static final int PANEL_H = 360;
    private static final int FILTER_BUTTON_WIDTH = 60;
    private static final int MODS_PER_PAGE = 8;

    private final Map<String, EntityModifierConfig.EntityEditData> localData = new TreeMap<>();

    public record EntityGuiInfo(String id, String translatedName, LivingEntity entity) {
    }

    private final List<EntityGuiInfo> allEntities = new ArrayList<>();
    private final List<String> availableMods = new ArrayList<>();
    private final EnumSet<CategoryFilter> selectedCategories = EnumSet.noneOf(CategoryFilter.class);
    private final Set<String> selectedMods = new TreeSet<>();
    private final EditedEntryTracker<EntityGuiInfo> editedEntities = new EditedEntryTracker<>();
    private final KineticSearch.Model<EntityGuiInfo> entityModel;
    private final KineticScrollController gridScroll = new KineticScrollController();
    private final KineticEntityPreview entityPreview = KineticEntityPreview.create();
    private final AttributePanel attributePanel = new AttributePanel();
    private final BuffPanel buffPanel = new BuffPanel();
    private final List<IModifierPanel> panels = List.of(attributePanel, buffPanel);

    private EntityGuiInfo selectedEntity;
    private KineticTextField searchBox;
    private List<Component> deferredEntityTooltip;
    private EntityGridControl gridControl;

    private int gridCols;
    private int gridRowsVisible;
    private int gridActualWidth;
    private int startX;
    private int startY;
    private int savedActiveIdx;
    private IModifierPanel currentPanel;

    private KineticButton saveBtn;
    private KineticButton resetBtn;
    private KineticButton attrTabBtn;
    private KineticButton buffTabBtn;
    private KineticButton globalExpandButton;
    private KineticButton filterButton;

    private boolean globalMode;
    private String entityQuery = "";
    private String selectedGlobalAttribute;
    private String selectedIndividualAttribute;
    private int individualPanelIdx;

    public EntityModifierScreen(String serverSnapshotJson) {
        super(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.title"));

        Map<String, EntityModifierConfig.EntityEditData> snapshot = EntityModifierConfig.GSON.fromJson(
                serverSnapshotJson,
                new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType()
        );
        if (snapshot == null) {
            throw new IllegalArgumentException("Server entity-modifier snapshot is null");
        }
        for (Map.Entry<String, EntityModifierConfig.EntityEditData> entry : snapshot.entrySet()) {
            EntityModifierConfig.EntityEditData value = entry.getValue();
            if (entry.getKey() == null || value == null || value.attributes == null
                    || value.attributeRules == null || value.buffs == null) {
                throw new IllegalArgumentException("Server entity-modifier snapshot is malformed");
            }
        }
        localData.putAll(snapshot);
        localData.computeIfAbsent(EntityModifierConfig.GLOBAL_KEY, key -> new EntityModifierConfig.EntityEditData());

        allEntities.addAll(EntityModifierGuiCache.getEntities());
        allEntities.stream()
                .map(info -> KineticResourceIds.tryParse(info.id()))
                .filter(java.util.Objects::nonNull)
                .map(ResourceLocation::getNamespace)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .forEach(availableMods::add);

        editedEntities.refresh(allEntities, this::isTrulyModified);
        entityModel = new KineticSearch.Model<>(
                allEntities,
                (info, query) -> KineticSearch.match(buildSearchData(info), query)
        );
        updateEntityComparator();
        refreshEntityModel("");
        gridScroll.bindSelection(
                this::selectedGridIndex,
                index -> index / Math.max(1, gridCols) - Math.max(1, gridRowsVisible) / 2
        );

        configureStandaloneDraft(this::copyLocalData, this::restoreLocalData);
    }

    private int gridX() {
        return startX + GRID_LEFT_INSET;
    }

    private int gridY() {
        return startY + 6;
    }

    private int gridHeight() {
        return gridRowsVisible * CELL_STRIDE - CELL_GAP;
    }

    private int rightPanelX() {
        return startX + gridActualWidth + GRID_RIGHT_GAP;
    }

    private int rightPanelWidth() {
        return PANEL_W - (rightPanelX() - startX) - 15;
    }

    private int tabWidth(String key) {
        return KineticText.width(KineticI18n.translatable(key)) + 16;
    }

    public int panelSearchWidth(int panelWidth) {
        int attributeTabWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.attributes");
        int effectTabWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.buffs");
        return Math.max(80, panelWidth - attributeTabWidth - effectTabWidth - 22);
    }

    private int selectedGridIndex() {
        if (globalMode || selectedEntity == null) return -1;
        return entityModel.items().indexOf(selectedEntity);
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        int x = gridX();
        int y = gridY();
        if (mouseX < x || mouseX >= x + gridActualWidth || mouseY < y || mouseY >= y + gridHeight()) return -1;
        int columnOffset = (int) (mouseX - x);
        int rowOffset = (int) (mouseY - y + gridScroll.visualShift(CELL_STRIDE));
        if (columnOffset % CELL_STRIDE >= CELL_SIZE || rowOffset % CELL_STRIDE >= CELL_SIZE) return -1;
        int column = columnOffset / CELL_STRIDE;
        int row = rowOffset / CELL_STRIDE;
        return (gridScroll.smoothIndexOffset() + row) * gridCols + column;
    }

    private Map<String, EntityModifierConfig.EntityEditData> copyLocalData() {
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType();
        Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                EntityModifierConfig.GSON.toJson(localData), type
        );
        return copy == null ? new TreeMap<>() : new TreeMap<>(copy);
    }

    private void restoreLocalData(Map<String, EntityModifierConfig.EntityEditData> snapshot) {
        localData.clear();
        if (snapshot != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType();
            Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                    EntityModifierConfig.GSON.toJson(snapshot), type
            );
            if (copy != null) localData.putAll(copy);
        }
        localData.computeIfAbsent(EntityModifierConfig.GLOBAL_KEY, key -> new EntityModifierConfig.EntityEditData());
        editedEntities.refresh(allEntities, this::isTrulyModified);
        refreshEntityModel(entityQuery);
        updateGridScrollRange();
        if (isAttached()) rebuild();
    }

    public Map<String, EntityModifierConfig.EntityEditData> getLocalData() {
        return localData;
    }

    private String buildSearchData(EntityGuiInfo info) {
        return info.id().toLowerCase(Locale.ROOT)
                + " " + info.translatedName().toLowerCase(Locale.ROOT)
                + " " + KineticSearch.pinyin(info.translatedName());
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
        entityModel.refresh(query == null ? "" : query);
    }

    private void updateEntityComparator() {
        entityModel.setComparator(globalMode ? ENTITY_ID_ORDER : editedEntities.comparator(ENTITY_ID_ORDER));
    }

    private boolean isTrulyModified(EntityGuiInfo info) {
        EntityModifierConfig.EntityEditData data = localData.get(info.id());
        if (data == null) return false;
        if (!data.buffs.isEmpty() || !data.attributeRules.isEmpty()) return true;

        for (Map.Entry<String, Double> entry : data.attributes.entrySet()) {
            ResourceLocation attributeId = KineticResourceIds.tryParse(entry.getKey());
            Attribute attribute = attributeId == null ? null : KineticRegistries.attributes().get(attributeId);
            if (attribute == null) continue;
            double defaultValue = info.entity().getAttributes().hasAttribute(attribute)
                    ? info.entity().getAttributes().getBaseValue(attribute)
                    : attribute.getDefaultValue();
            if (Math.abs(entry.getValue() - defaultValue) > 0.0001D) return true;
        }
        return false;
    }

    private void refreshSelectedEditedState() {
        if (globalMode || selectedEntity == null) return;
        boolean edited = isTrulyModified(selectedEntity);
        if (!editedEntities.update(selectedEntity, edited)) return;
        refreshEntityModel(entityQuery);
        updateGridScrollRange();
        if (edited) gridScroll.reset();
    }

    private void setActivePanel(int index) {
        savedActiveIdx = Math.max(0, Math.min(index, panels.size() - 1));
        currentPanel = panels.get(savedActiveIdx);
        updatePanelVisibility();
    }

    private void updatePanelVisibility() {
        boolean hasTarget = selectedEntity != null || globalMode;
        for (IModifierPanel panel : panels) {
            panel.setVisible(hasTarget && panel == currentPanel);
        }
        if (resetBtn != null) resetBtn.setEnabled(hasTarget);
        if (attrTabBtn != null) attrTabBtn.setSelected(currentPanel == attributePanel);
        if (buffTabBtn != null) buffTabBtn.setSelected(currentPanel == buffPanel);
    }

    @Override
    protected void build(KineticUi ui) {
        editedEntities.refresh(allEntities, this::isTrulyModified);
        startX = (width() - PANEL_W) / 2;
        startY = (height() - PANEL_H) / 2;
        gridCols = 4;
        gridActualWidth = gridCols * CELL_STRIDE - CELL_GAP;
        gridRowsVisible = 5;

        int rightX = rightPanelX();
        int rightWidth = rightPanelWidth();
        int actionButtonY = startY + PANEL_H - 24;

        gridControl = ui.add(new EntityGridControl(
                gridX() - 2,
                gridY() - 3,
                gridActualWidth + 14,
                gridHeight() + 6
        ));

        searchBox = ui.textField(rightX + 8, startY + 5, rightWidth - 82)
                .label(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.search_entity"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.search_entity"))
                .value(entityQuery)
                .onChange(this::updateSearch)
                .firstShownTextAsDefault().build();

        filterButton = ui.button(rightX + rightWidth - FILTER_BUTTON_WIDTH - 8, startY + 5, FILTER_BUTTON_WIDTH)
                .compact()
                .text(KineticI18n.translatable("gui.kineticcore.entity_selector.filter"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.filter.tooltip"))
                .onClick(this::showEntityFilterMenu)
                .build();
        filterButton.setSelected(!selectedCategories.isEmpty() || !selectedMods.isEmpty());

        globalExpandButton = ui.button(rightX + 8, actionButtonY, 92)
                .text(KineticI18n.translatable(globalMode
                        ? "gui.entitycontrol.modifier.global.collapse"
                        : "gui.entitycontrol.modifier.global.expand"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.global.expand_tooltip"))
                .onClick(() -> setGlobalExpanded(!globalMode))
                .build();
        globalExpandButton.setSelected(globalMode);

        for (IModifierPanel panel : panels) {
            panel.build(this, ui, rightX + 4, startY + 30, rightWidth - 8, PANEL_H - 70);
        }
        currentPanel = panels.get(Math.min(savedActiveIdx, panels.size() - 1));

        if (selectedEntity != null || globalMode) {
            String targetId = globalMode ? EntityModifierConfig.GLOBAL_KEY : selectedEntity.id();
            LivingEntity preview = globalMode ? null : selectedEntity.entity();
            for (IModifierPanel panel : panels) panel.onEntitySelected(targetId, preview);
            String restoreId = globalMode ? selectedGlobalAttribute : selectedIndividualAttribute;
            if (restoreId != null) attributePanel.restoreSelection(restoreId);
        }

        int tabY = startY + 34;
        int attrWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.attributes");
        int buffWidth = tabWidth("gui.entitycontrol.modifier.modifier.tab.buffs");

        attrTabBtn = ui.button(rightX + 8 + panelSearchWidth(rightWidth - 8) + 6, tabY, attrWidth)
                .text(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.attributes"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.attributes.tooltip"))
                .onClick(() -> setActivePanel(0))
                .build();
        buffTabBtn = ui.button(rightX + 8 + panelSearchWidth(rightWidth - 8) + 12 + attrWidth, tabY, buffWidth)
                .text(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.buffs"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.buffs.tooltip"))
                .onClick(() -> setActivePanel(1))
                .build();

        saveBtn = ui.button(rightX + rightWidth - 215, actionButtonY, 70)
                .text(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.save"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.save.tooltip"))
                .onClick(() -> EntityModifierNetwork.saveConfig(EntityModifierConfig.GSON.toJson(localData)))
                .build();
        resetBtn = ui.button(rightX + rightWidth - 140, actionButtonY, 65)
                .text(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.reset"))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.reset.tooltip"))
                .onClick(this::resetSelectedEntity)
                .build();
        ui.button(rightX + rightWidth - 70, actionButtonY, 65)
                .text(KineticI18n.translatable("gui.entitycontrol.modifier.config.back"))
                .onClick(this::navigateBack)
                .build();

        updateSearch(entityQuery);
        updatePanelVisibility();
    }

    private void resetSelectedEntity() {
        if (globalMode) {
            selectedGlobalAttribute = null;
            localData.put(EntityModifierConfig.GLOBAL_KEY, new EntityModifierConfig.EntityEditData());
            for (IModifierPanel panel : panels) panel.onEntitySelected(EntityModifierConfig.GLOBAL_KEY, null);
            attributePanel.restoreSelection(selectedGlobalAttribute);
            updatePanelVisibility();
            return;
        }
        if (selectedEntity == null) return;

        selectedIndividualAttribute = null;
        localData.remove(selectedEntity.id());
        for (IModifierPanel panel : panels) panel.onEntitySelected(selectedEntity.id(), selectedEntity.entity());
        editedEntities.update(selectedEntity, false);
        refreshEntityModel(entityQuery);
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
                KineticI18n.translatable("gui.kineticcore.entity_selector.filter.reset"),
                () -> {
                    selectedCategories.clear();
                    selectedMods.clear();
                    updateSearch(entityQuery);
                    showEntityFilterMenu();
                }
        ));
        entries.add(KineticOverlays.MenuItem.separator());
        Component allCategories = KineticI18n.translatable("gui.kineticcore.entity_selector.category.all");
        entries.add(KineticOverlays.MenuItem.toggle(allCategories, allCategories, selectedCategories.isEmpty(), () -> {
            selectedCategories.clear();
            updateSearch(entityQuery);
            showEntityFilterMenu();
        }));
        for (CategoryFilter category : CategoryFilter.values()) {
            Component name = KineticI18n.translatable(
                    "gui.kineticcore.entity_selector.category." + category.name().toLowerCase(Locale.ROOT)
            );
            entries.add(KineticOverlays.MenuItem.toggle(name, name, selectedCategories.contains(category), () -> {
                if (!selectedCategories.add(category)) selectedCategories.remove(category);
                updateSearch(entityQuery);
                showEntityFilterMenu();
            }));
        }
        entries.add(KineticOverlays.MenuItem.separator());
        entries.add(KineticOverlays.MenuItem.action(
                KineticI18n.translatable("gui.kineticcore.entity_selector.filter.mods", selectedMods.size()),
                () -> showModMenu(0)
        ));
        if (filterButton != null) {
            openContextMenu(filterButton.controlX(), filterButton.controlY() + CONTROL_HEIGHT + 4, entries, 140);
        }
    }

    private void showModMenu(int requestedPage) {
        int lastPage = Math.max(0, (availableMods.size() - 1) / MODS_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, lastPage));
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        entries.add(KineticOverlays.MenuItem.action(
                KineticI18n.translatable("gui.kineticcore.entity_selector.filter.back"),
                this::showEntityFilterMenu
        ));
        Component allMods = KineticI18n.translatable("gui.kineticcore.entity_selector.filter.mod_all");
        entries.add(KineticOverlays.MenuItem.toggle(allMods, allMods, selectedMods.isEmpty(), () -> {
            selectedMods.clear();
            updateSearch(entityQuery);
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
                updateSearch(entityQuery);
                showModMenu(page);
            }));
        }
        entries.add(KineticOverlays.MenuItem.separator());
        if (page > 0) {
            entries.add(KineticOverlays.MenuItem.action(
                    KineticI18n.translatable("gui.kineticcore.entity_selector.filter.previous"),
                    () -> showModMenu(page - 1)
            ));
        }
        if (page < lastPage) {
            entries.add(KineticOverlays.MenuItem.action(
                    KineticI18n.translatable("gui.kineticcore.entity_selector.filter.next"),
                    () -> showModMenu(page + 1)
            ));
        }
        if (filterButton != null) {
            openContextMenu(filterButton.controlX(), filterButton.controlY() + CONTROL_HEIGHT + 4, entries, 140);
        }
    }

    public void showModifierContextMenu(double x, double y, List<KineticOverlays.MenuItem> items) {
        openContextMenu(x, y, items);
    }

    private EntityModifierConfig.EntityEditData globalData() {
        return localData.computeIfAbsent(EntityModifierConfig.GLOBAL_KEY,
                key -> new EntityModifierConfig.EntityEditData());
    }

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

    private void setGlobalExpanded(boolean expanded) {
        if (globalMode == expanded) return;
        if (expanded) individualPanelIdx = savedActiveIdx;
        globalMode = expanded;
        if (globalExpandButton != null) {
            globalExpandButton.setText(KineticI18n.translatable(globalMode
                    ? "gui.entitycontrol.modifier.global.collapse"
                    : "gui.entitycontrol.modifier.global.expand"));
            globalExpandButton.setSelected(globalMode);
        }
        updateEntityComparator();
        updateSearch(entityQuery);

        String targetId = globalMode
                ? EntityModifierConfig.GLOBAL_KEY
                : selectedEntity == null ? null : selectedEntity.id();
        LivingEntity preview = globalMode || selectedEntity == null ? null : selectedEntity.entity();
        for (IModifierPanel panel : panels) panel.onEntitySelected(targetId, preview);

        int targetPanel = globalMode ? 0 : Math.min(individualPanelIdx, panels.size() - 1);
        setActivePanel(targetPanel);
        String restoreId = globalMode ? selectedGlobalAttribute : selectedIndividualAttribute;
        if (restoreId != null) attributePanel.restoreSelection(restoreId);
    }

    private boolean globalTargetSelected(EntityGuiInfo info) {
        if (!globalMode) return false;
        EntityModifierConfig.EntityEditData data = globalData();
        if (selectedGlobalAttribute != null) {
            EntityModifierConfig.AttributeRule rule = data.attributeRules.get(selectedGlobalAttribute);
            return rule != null && rule.appliesTo(info.id());
        }
        for (EntityModifierConfig.AttributeRule rule : data.attributeRules.values()) {
            if (rule != null && rule.appliesTo(info.id())) return true;
        }
        return false;
    }

    private void updateGridScrollRange() {
        int totalRows = (entityModel.items().size() + Math.max(1, gridCols) - 1) / Math.max(1, gridCols);
        gridScroll.update(totalRows, Math.max(1, gridRowsVisible));
    }

    @Override
    protected void onTick() {
        refreshSelectedEditedState();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredEntityTooltip = null;
        KineticTheme.panel(graphics, startX, startY, PANEL_W, PANEL_H);
        KineticTheme.panelAlt(
                graphics,
                rightPanelX(),
                startY + 27,
                rightPanelWidth() - 2,
                PANEL_H - 63
        );
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (selectedEntity != null || globalMode) {
            if (currentPanel != null) currentPanel.render(graphics, mouseX, mouseY, partialTick);
        } else {
            int rightX = rightPanelX() + 15;
            int rightWidth = rightPanelWidth() - 15;
            graphics.centeredText(
                    KineticI18n.translatable("gui.entitycontrol.modifier.modifier.no_entity"),
                    rightX + rightWidth / 2,
                    startY + PANEL_H / 2,
                    KineticTheme.current().mutedText(),
                    false
            );
        }
        if (deferredEntityTooltip != null) showTooltip(deferredEntityTooltip);
    }

    private List<Component> previewTooltip(EntityGuiInfo info, String stateKey) {
        return List.of(
                KineticI18n.translatable("gui.entitycontrol.modifier.modifier.entity_name", info.translatedName()),
                KineticI18n.translatable("gui.entitycontrol.modifier.modifier.entity_id", info.id()),
                KineticI18n.translatable(
                        "gui.entitycontrol.modifier.modifier.preview_zoom_hint",
                        entityPreview.zoomPercent(stateKey)
                )
        );
    }

    public void handleSaveResult(boolean success) {
        if (success) commitDraft();
    }

    @Override
    protected void onRemoved() {
        entityPreview.clear();
    }

    private final class EntityGridControl extends KineticCustomControl {
        private EntityGridControl(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        protected void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
            deferredEntityTooltip = null;
            updateGridScrollRange();
            int gx = gridX();
            int gy = gridY();
            int gh = gridHeight();
            KineticTheme.panelAlt(graphics, gx - 2, gy - 3, gridActualWidth + 14, gh + 6);

            List<EntityGuiInfo> displayEntities = entityModel.items();
            int firstRow = gridScroll.smoothIndexOffset();
            int shift = gridScroll.visualShift(CELL_STRIDE);
            int startIndex = firstRow * gridCols;
            int endIndex = Math.min(startIndex + (gridRowsVisible + 1) * gridCols, displayEntities.size());

            graphics.clipped(gx, gy, gx + gridActualWidth, gy + gh, () -> {
                for (int i = startIndex; i < endIndex; i++) {
                    EntityGuiInfo info = displayEntities.get(i);
                    int localIndex = i - startIndex;
                    int cellX = gx + localIndex % gridCols * CELL_STRIDE;
                    int cellY = gy + localIndex / gridCols * CELL_STRIDE - shift;
                    boolean selected = !globalMode && selectedEntity == info;
                    boolean applies = globalMode && globalTargetSelected(info);
                    boolean edited = !globalMode && editedEntities.isEdited(info);
                    boolean hovered = mouseX >= gx && mouseX < gx + gridActualWidth
                            && mouseY >= gy && mouseY < gy + gh
                            && mouseX >= cellX && mouseX < cellX + CELL_SIZE
                            && mouseY >= cellY && mouseY < cellY + CELL_SIZE;

                    KineticEntityPreview.drawCheckerboard(
                            graphics, cellX + 1, cellY + 1, CELL_SIZE - 2, CELL_SIZE - 2
                    );
                    if (selected) {
                        KineticTheme.stateOutline(graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, true, false, false);
                    } else if (applies || edited) {
                        KineticTheme.indicatorOutline(
                                graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, KineticTheme.Indicator.SUCCESS
                        );
                    } else {
                        KineticTheme.stateOutline(
                                graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, false, hovered, false
                        );
                    }

                    String stateKey = "modifier:grid:" + info.id();
                    entityPreview.render(
                            graphics,
                            info.entity(),
                            stateKey,
                            cellX + 2,
                            cellY + 2,
                            CELL_SIZE - 4,
                            CELL_SIZE - 4,
                            hovered
                    );
                    int previewTop = Math.max(cellY, gy);
                    int previewBottom = Math.min(cellY + CELL_SIZE, gy + gh);
                    if (previewBottom > previewTop) {
                        registerPreviewZoomArea(
                                entityPreview,
                                stateKey,
                                cellX,
                                previewTop,
                                CELL_SIZE,
                                previewBottom - previewTop
                        );
                    }
                    gridScroll.renderSelectionFlash(graphics, i, cellX, cellY, CELL_SIZE, CELL_SIZE);
                    if (hovered) deferredEntityTooltip = previewTooltip(info, stateKey);
                }
            });

            gridScroll.render(
                    graphics,
                    mouseX,
                    mouseY,
                    gx + gridActualWidth + 4,
                    gy,
                    4,
                    gh,
                    20
            );
        }

        @Override
        protected boolean onMouseClick(MouseInput input) {
            if (gridScroll.beginDrag(
                    input.x(), input.y(), input.button(),
                    gridX() + gridActualWidth + 4,
                    gridY(),
                    4,
                    gridHeight(),
                    20,
                    0
            )) {
                return true;
            }

            int index = gridIndexAt(input.x(), input.y());
            List<EntityGuiInfo> visible = entityModel.items();
            if (index < 0 || index >= visible.size()) return false;

            if (globalMode) {
                if (input.isLeft() && currentPanel == attributePanel) {
                    attributePanel.toggleGlobalTarget(visible.get(index).id(), selectableLivingEntityIds());
                }
                return true;
            }

            if (!input.isLeft()) return false;
            selectedEntity = visible.get(index);
            selectedIndividualAttribute = null;
            for (IModifierPanel panel : panels) {
                panel.onEntitySelected(selectedEntity.id(), selectedEntity.entity());
            }
            updatePanelVisibility();
            return true;
        }

        @Override
        protected boolean onMouseDrag(MouseDragInput input) {
            return gridScroll.drag(input.y(), gridY(), gridHeight(), 20);
        }

        @Override
        protected boolean onMouseRelease(MouseInput input) {
            return gridScroll.release(input.button());
        }

        @Override
        protected boolean onMouseScroll(ScrollInput input) {
            return gridScroll.scroll(input.deltaY());
        }
    }
}
