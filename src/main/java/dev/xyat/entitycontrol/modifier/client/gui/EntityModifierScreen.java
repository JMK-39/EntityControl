package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.EntityCardGrid;
import dev.xyat.entitycontrol.modifier.client.gui.panel.AttributePanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.BuffPanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.IModifierPanel;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
//? if >=1.21 {
/*import net.minecraft.tags.EntityTypeTags;
*///?} else {
import net.minecraft.world.entity.MobType;
//?}
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 实体属性修改。顶栏：返回 / 当前页 ▾（属性 · 效果）/ 范围 ▾（单个实体 · 全局规则）/ 保存 / 更多 ▾。
 * 左侧 3D 卡片选择实体（全局规则时点击卡片切换规则的作用目标），右侧为属性 / 效果面板。
 */
public final class EntityModifierScreen extends EcPage {
    private enum CategoryFilter { FRIENDLY, AQUATIC, NEUTRAL, MONSTER, UNDEAD, MISC }

    private static final int CARD = 40;
    private static final int LIST_WIDTH = EntityCardGrid.widthFor(4, CARD) + PAD * 2;
    private static final int MODS_PER_PAGE = 8;
    private static final java.lang.reflect.Type DATA_TYPE =
            new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType();

    public record EntityGuiInfo(String id, String translatedName, LivingEntity entity) {
    }

    private final Map<String, EntityModifierConfig.EntityEditData> localData = new TreeMap<>();
    private final List<EntityGuiInfo> allEntities = new ArrayList<>();
    private final Map<String, EntityGuiInfo> byId = new HashMap<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final List<String> availableMods = new ArrayList<>();
    private final EnumSet<CategoryFilter> selectedCategories = EnumSet.noneOf(CategoryFilter.class);
    private final Set<String> selectedMods = new TreeSet<>();
    private final EditedEntryTracker<EntityGuiInfo> editedEntities = new EditedEntryTracker<>();
    private final EntityCardGrid cards = new EntityCardGrid(CARD);
    private final AttributePanel attributePanel = new AttributePanel();
    private final BuffPanel buffPanel = new BuffPanel();
    private final List<IModifierPanel> panels = List.of(attributePanel, buffPanel);

    private EntityGuiInfo selectedEntity;
    private IModifierPanel currentPanel = attributePanel;
    private KineticButton filterButton;
    private KineticLayout.Rect helpRect;
    private boolean globalMode;
    private String entityQuery = "";
    private String selectedGlobalAttribute;
    private String selectedIndividualAttribute;
    private String savedJson;
    private int tickCounter;

    public EntityModifierScreen(String serverSnapshotJson) {
        super(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.title"));
        Map<String, EntityModifierConfig.EntityEditData> snapshot = EntityModifierConfig.GSON.fromJson(serverSnapshotJson, DATA_TYPE);
        if (snapshot == null) throw new IllegalArgumentException("Server entity-modifier snapshot is null");
        for (Map.Entry<String, EntityModifierConfig.EntityEditData> entry : snapshot.entrySet()) {
            EntityModifierConfig.EntityEditData value = entry.getValue();
            if (entry.getKey() == null || value == null || value.attributes == null
                    || value.attributeRules == null || value.buffs == null) {
                throw new IllegalArgumentException("Server entity-modifier snapshot is malformed");
            }
        }
        localData.putAll(snapshot);
        localData.computeIfAbsent(EntityModifierConfig.GLOBAL_KEY, key -> new EntityModifierConfig.EntityEditData());
        savedJson = EntityModifierConfig.GSON.toJson(localData);

        allEntities.addAll(EntityModifierGuiCache.getEntities());
        for (EntityGuiInfo info : allEntities) {
            byId.put(info.id(), info);
            searchData.put(info.id(), (info.id() + " " + info.translatedName() + " " + KineticSearch.pinyin(info.translatedName()))
                    .toLowerCase(Locale.ROOT));
        }
        allEntities.stream().map(info -> KineticResourceIds.tryParse(info.id())).filter(java.util.Objects::nonNull)
                .map(ResourceLocation::getNamespace).distinct().sorted(String::compareToIgnoreCase).forEach(availableMods::add);
        editedEntities.refresh(allEntities, this::isTrulyModified);
        configureStandaloneDraft(this::copyLocalData, this::restoreLocalData);
    }

    // ------------------------------------------------------------------ 数据（面板使用）

    public Map<String, EntityModifierConfig.EntityEditData> getLocalData() {
        return localData;
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

    public List<String> selectableLivingEntityIds() {
        return allEntities.stream().map(EntityGuiInfo::id).toList();
    }

    /** 面板自带搜索框的宽度：不再与页签按钮并排，只需一个紧凑宽度。 */
    public int panelSearchWidth(int panelWidth) {
        return Math.max(80, Math.min(200, panelWidth - 8));
    }

    public void showModifierContextMenu(double x, double y, List<KineticOverlays.MenuItem> items) {
        openContextMenu(x, y, items);
    }

    private Map<String, EntityModifierConfig.EntityEditData> copyLocalData() {
        Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                EntityModifierConfig.GSON.toJson(localData), DATA_TYPE);
        return copy == null ? new TreeMap<>() : new TreeMap<>(copy);
    }

    private void restoreLocalData(Map<String, EntityModifierConfig.EntityEditData> snapshot) {
        localData.clear();
        if (snapshot != null) {
            Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                    EntityModifierConfig.GSON.toJson(snapshot), DATA_TYPE);
            if (copy != null) localData.putAll(copy);
        }
        localData.computeIfAbsent(EntityModifierConfig.GLOBAL_KEY, key -> new EntityModifierConfig.EntityEditData());
        editedEntities.refresh(allEntities, this::isTrulyModified);
        if (isAttached()) rebuild();
    }

    private boolean dirty() {
        return !EntityModifierConfig.GSON.toJson(localData).equals(savedJson);
    }

    private boolean isTrulyModified(EntityGuiInfo info) {
        EntityModifierConfig.EntityEditData data = localData.get(info.id());
        if (data == null) return false;
        if (!data.buffs.isEmpty() || !data.attributeRules.isEmpty()) return true;
        for (Map.Entry<String, Double> entry : data.attributes.entrySet()) {
            ResourceLocation attributeId = KineticResourceIds.tryParse(entry.getKey());
            Attribute attribute = attributeId == null ? null : KineticRegistries.attributes().get(attributeId);
            if (attribute == null) continue;
//? if >=1.21 {
/*            double base = info.entity().getAttributes().hasAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute))
*///?} else {
            double base = info.entity().getAttributes().hasAttribute(attribute)
//?}
//? if >=1.21 {
/*                    ? info.entity().getAttributes().getBaseValue(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute)) : attribute.getDefaultValue();
*///?} else {
                    ? info.entity().getAttributes().getBaseValue(attribute) : attribute.getDefaultValue();
//?}
            if (Math.abs(entry.getValue() - base) > 0.0001D) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 列表

    private CategoryFilter categoryOf(EntityGuiInfo info) {
        LivingEntity entity = info.entity();
        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS) {
            return CategoryFilter.AQUATIC;
        }
//? if >=1.21 {
/*        if (entity.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) return CategoryFilter.UNDEAD;
*///?} else {
        if (entity.getMobType() == MobType.UNDEAD) return CategoryFilter.UNDEAD;
//?}
        if (entity instanceof NeutralMob) return CategoryFilter.NEUTRAL;
        if (category == MobCategory.MONSTER) return CategoryFilter.MONSTER;
        if (category == MobCategory.CREATURE) return CategoryFilter.FRIENDLY;
        return CategoryFilter.MISC;
    }

    private List<String> keys() {
        String query = entityQuery.toLowerCase(Locale.ROOT).trim();
        List<EntityGuiInfo> result = new ArrayList<>();
        for (EntityGuiInfo info : allEntities) {
            if (!selectedCategories.isEmpty() && !selectedCategories.contains(categoryOf(info))) continue;
            if (!selectedMods.isEmpty()) {
                ResourceLocation id = KineticResourceIds.tryParse(info.id());
                if (id == null || !selectedMods.contains(id.getNamespace())) continue;
            }
            if (!query.isEmpty() && !KineticSearch.match(searchData.get(info.id()), query)) continue;
            result.add(info);
        }
        Comparator<EntityGuiInfo> order = Comparator.comparing(EntityGuiInfo::id);
        // 单个实体模式下，已修改的排在前面。
        if (!globalMode) order = Comparator.comparing((EntityGuiInfo info) -> !editedEntities.isEdited(info)).thenComparing(order);
        result.sort(order);
        return result.stream().map(EntityGuiInfo::id).toList();
    }

    private boolean globalTarget(String id) {
        if (!globalMode) return false;
        EntityModifierConfig.EntityEditData data = localData.get(EntityModifierConfig.GLOBAL_KEY);
        if (data == null) return false;
        if (selectedGlobalAttribute != null) {
            EntityModifierConfig.AttributeRule rule = data.attributeRules.get(selectedGlobalAttribute);
            return rule != null && rule.appliesTo(id);
        }
        for (EntityModifierConfig.AttributeRule rule : data.attributeRules.values()) {
            if (rule != null && rule.appliesTo(id)) return true;
        }
        return false;
    }

    private static MutableComponent tr(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.modifier.editor." + key, args);
    }

    // ------------------------------------------------------------------ 外框

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(
                HeaderAction.menu(tr(currentPanel == attributePanel ? "tab.attributes" : "tab.effects"), tr("tab.tooltip"), () -> List.of(
                        KineticOverlays.MenuItem.toggle(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.attributes"),
                                KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.attributes.tooltip"),
                                currentPanel == attributePanel, () -> switchPanel(attributePanel)),
                        KineticOverlays.MenuItem.toggle(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.buffs"),
                                KineticI18n.translatable("gui.entitycontrol.modifier.modifier.tab.buffs.tooltip"),
                                currentPanel == buffPanel, () -> switchPanel(buffPanel)))),
                HeaderAction.menu(tr(globalMode ? "scope.global" : "scope.entity"), tr("scope.tooltip"), () -> List.of(
                        KineticOverlays.MenuItem.toggle(tr("scope.entity"), tr("scope.entity.tooltip"), !globalMode, () -> setGlobal(false)),
                        KineticOverlays.MenuItem.toggle(tr("scope.global"), KineticI18n.translatable("gui.entitycontrol.modifier.global.expand_tooltip"),
                                globalMode, () -> setGlobal(true)))),
                HeaderAction.button("save", KineticI18n.translatable("gui.entitycontrol.modifier.modifier.save"),
                        KineticI18n.translatable("gui.entitycontrol.modifier.modifier.save.tooltip"),
                        () -> EntityModifierNetwork.saveConfig(EntityModifierConfig.GSON.toJson(localData))).enabled(dirty()),
                HeaderAction.more(() -> {
                    boolean hasTarget = globalMode || selectedEntity != null;
                    List<KineticOverlays.MenuItem> items = new ArrayList<>();
                    items.add(hasTarget
                            ? KineticOverlays.MenuItem.danger(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.reset"),
                            KineticI18n.translatable("gui.entitycontrol.modifier.modifier.reset.tooltip"), this::resetSelected)
                            : KineticOverlays.MenuItem.disabled(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.reset"),
                            tr("select_first")));
                    items.add(dirty()
                            ? KineticOverlays.MenuItem.danger(tr("revert"), tr("revert.tooltip"), () -> restoreLocalData(
                            EntityModifierConfig.GSON.fromJson(savedJson, DATA_TYPE)))
                            : KineticOverlays.MenuItem.disabled(tr("revert"), tr("revert.tooltip")));
                    return items;
                })
        );
    }

    @Override
    protected boolean onBack() {
        if (!dirty()) return false;
        openDialog(tr("unsaved.title"), tr("unsaved.message"), tr("unsaved.discard"),
                KineticI18n.translatable("gui.entitycontrol.common.cancel"), () -> {
                    restoreLocalData(EntityModifierConfig.GSON.fromJson(savedJson, DATA_TYPE));
                    commitDraft();
                    navigateBack();
                }, () -> {
                });
        return true;
    }

    public void handleSaveResult(boolean success) {
        if (!success) return;
        savedJson = EntityModifierConfig.GSON.toJson(localData);
        commitDraft();
        updateSaveButton();
    }

    private void updateSaveButton() {
        KineticButton save = headerButton("save");
        if (save != null) save.setEnabled(dirty());
    }

    private void switchPanel(IModifierPanel panel) {
        currentPanel = panel;
        rebuild();
    }

    private void setGlobal(boolean global) {
        if (globalMode == global) return;
        globalMode = global;
        cards.reset();
        rebuild();
    }

    private void resetSelected() {
        if (globalMode) {
            selectedGlobalAttribute = null;
            localData.put(EntityModifierConfig.GLOBAL_KEY, new EntityModifierConfig.EntityEditData());
        } else if (selectedEntity != null) {
            selectedIndividualAttribute = null;
            localData.remove(selectedEntity.id());
            editedEntities.update(selectedEntity, false);
        }
        rebuild();
    }

    // ------------------------------------------------------------------ 内容

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        helpRect = null;
        editedEntities.refresh(allEntities, this::isTrulyModified);
        KineticLayout.Split columns = KineticLayout.splitHorizontal(body, LIST_WIDTH, GAP);
        KineticLayout.Rect left = section(columns.first(), tr(globalMode ? "list.global" : "list", allEntities.size()));
        KineticLayout.Split rows = takeRow(left);
        KineticLayout.Rect bar = rows.first();
        int filterWidth = 56;
        textInput(ui, new KineticLayout.Rect(bar.x(), bar.y(), bar.width() - filterWidth - GAP, H), entityQuery,
                KineticI18n.translatable("gui.entitycontrol.modifier.modifier.search_entity"), tr("search.tooltip"), value -> {
                    entityQuery = value;
                    cards.reset();
                    cards.setKeys(keys());
                });
        filterButton = actionButton(ui, new KineticLayout.Rect(bar.right() - filterWidth, bar.y(), filterWidth, H),
                tr("filter").copy().append(" ▾"), KineticI18n.translatable("gui.entitycontrol.modifier.modifier.filter.tooltip"),
                true, this::showEntityFilterMenu);
        filterButton.setSelected(!selectedCategories.isEmpty() || !selectedMods.isEmpty());
        cards.entities(id -> byId.containsKey(id) ? byId.get(id).entity() : null)
                .selected(id -> !globalMode && selectedEntity != null && selectedEntity.id().equals(id))
                .modified(id -> globalMode ? globalTarget(id) : byId.containsKey(id) && editedEntities.isEdited(byId.get(id)));
        cards.layout(this, rows.second(), keys());

        boolean hasTarget = globalMode || selectedEntity != null;
        Component title = globalMode ? tr("section.global")
                : selectedEntity == null ? KineticI18n.translatable("gui.entitycontrol.modifier.modifier.no_entity")
                : Component.literal(selectedEntity.translatedName());
        KineticLayout.Rect panelArea = section(columns.second(), title);
        if (!hasTarget) {
            helpRect = panelArea;
            return;
        }
        for (IModifierPanel panel : panels) {
            panel.build(this, ui, panelArea.x(), panelArea.y(), panelArea.width(), panelArea.height());
        }
        String targetId = globalMode ? EntityModifierConfig.GLOBAL_KEY : selectedEntity.id();
        LivingEntity preview = globalMode ? null : selectedEntity.entity();
        for (IModifierPanel panel : panels) {
            panel.onEntitySelected(targetId, preview);
            panel.setVisible(panel == currentPanel);
        }
        String restoreId = globalMode ? selectedGlobalAttribute : selectedIndividualAttribute;
        if (restoreId != null) attributePanel.restoreSelection(restoreId);
    }

    private void showEntityFilterMenu() {
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        entries.add(KineticOverlays.MenuItem.action(KineticI18n.translatable("gui.kineticcore.entity_selector.filter.reset"), () -> {
            selectedCategories.clear();
            selectedMods.clear();
            filterChanged();
            showEntityFilterMenu();
        }));
        entries.add(KineticOverlays.MenuItem.separator());
        Component allCategories = KineticI18n.translatable("gui.kineticcore.entity_selector.category.all");
        entries.add(KineticOverlays.MenuItem.toggle(allCategories, allCategories, selectedCategories.isEmpty(), () -> {
            selectedCategories.clear();
            filterChanged();
            showEntityFilterMenu();
        }));
        for (CategoryFilter category : CategoryFilter.values()) {
            Component name = KineticI18n.translatable("gui.kineticcore.entity_selector.category." + category.name().toLowerCase(Locale.ROOT));
            entries.add(KineticOverlays.MenuItem.toggle(name, name, selectedCategories.contains(category), () -> {
                if (!selectedCategories.add(category)) selectedCategories.remove(category);
                filterChanged();
                showEntityFilterMenu();
            }));
        }
        entries.add(KineticOverlays.MenuItem.separator());
        entries.add(KineticOverlays.MenuItem.action(
                KineticI18n.translatable("gui.kineticcore.entity_selector.filter.mods", selectedMods.size()), () -> showModMenu(0)));
        openFilterMenu(entries);
    }

    private void showModMenu(int requestedPage) {
        int lastPage = Math.max(0, (availableMods.size() - 1) / MODS_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, lastPage));
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        entries.add(KineticOverlays.MenuItem.action(KineticI18n.translatable("gui.kineticcore.entity_selector.filter.back"),
                this::showEntityFilterMenu));
        Component allMods = KineticI18n.translatable("gui.kineticcore.entity_selector.filter.mod_all");
        entries.add(KineticOverlays.MenuItem.toggle(allMods, allMods, selectedMods.isEmpty(), () -> {
            selectedMods.clear();
            filterChanged();
            showModMenu(page);
        }));
        entries.add(KineticOverlays.MenuItem.separator());
        for (int i = page * MODS_PER_PAGE; i < Math.min(availableMods.size(), (page + 1) * MODS_PER_PAGE); i++) {
            String mod = availableMods.get(i);
            Component name = Component.literal(mod);
            entries.add(KineticOverlays.MenuItem.toggle(name, name, selectedMods.contains(mod), () -> {
                if (!selectedMods.add(mod)) selectedMods.remove(mod);
                filterChanged();
                showModMenu(page);
            }));
        }
        entries.add(KineticOverlays.MenuItem.separator());
        if (page > 0) entries.add(KineticOverlays.MenuItem.action(
                KineticI18n.translatable("gui.kineticcore.entity_selector.filter.previous"), () -> showModMenu(page - 1)));
        if (page < lastPage) entries.add(KineticOverlays.MenuItem.action(
                KineticI18n.translatable("gui.kineticcore.entity_selector.filter.next"), () -> showModMenu(page + 1)));
        openFilterMenu(entries);
    }

    private void openFilterMenu(List<KineticOverlays.MenuItem> entries) {
        if (filterButton == null) return;
        openContextMenu(filterButton.controlX(), filterButton.controlY() + filterButton.controlHeight() + 2, entries, 140);
    }

    private void filterChanged() {
        if (filterButton != null) filterButton.setSelected(!selectedCategories.isEmpty() || !selectedMods.isEmpty());
        cards.reset();
        cards.setKeys(keys());
    }

    // ------------------------------------------------------------------ 绘制与交互

    @Override
    protected void onTick() {
        if (++tickCounter % 10 != 0) return;
        updateSaveButton();
        if (!globalMode && selectedEntity != null && editedEntities.update(selectedEntity, isTrulyModified(selectedEntity))) {
            cards.setKeys(keys());
        }
    }

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cards.render(graphics, mouseX, mouseY);
        if (helpRect != null) {
            graphics.wrappedText(tr("help"), helpRect.x(), helpRect.y(), helpRect.width(), KineticTheme.current().text());
        } else if (currentPanel != null) {
            currentPanel.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        String id = cards.keyAt(mouseX, mouseY);
        EntityGuiInfo info = id == null ? null : byId.get(id);
        if (info == null) return false;
        List<Component> lines = new ArrayList<>();
        lines.add(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.entity_name", info.translatedName()));
        lines.add(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.entity_id", info.id()));
        if (globalMode) lines.add(tr(globalTarget(id) ? "card.targeted" : "card.not_targeted"));
        else if (editedEntities.isEdited(info)) lines.add(tr("card.edited"));
        lines.add(tr(globalMode ? "card.hint.global" : "card.hint"));
        tooltipLines(lines);
        return true;
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        String id = cards.keyAt(input.x(), input.y());
        EntityGuiInfo info = id == null ? null : byId.get(id);
        if (info == null || !input.isLeft()) return info != null;
        if (globalMode) {
            if (currentPanel == attributePanel) attributePanel.toggleGlobalTarget(id, selectableLivingEntityIds());
            updateSaveButton();
            return true;
        }
        if (info != selectedEntity) {
            selectedEntity = info;
            selectedIndividualAttribute = null;
            clearFocus();
            rebuild();
        }
        return true;
    }

    @Override
    protected void onRemoved() {
        cards.clear();
    }
}
