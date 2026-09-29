package dev.xyat.entitycontrol.reset.client.gui;

import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl;
import dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EntityResetRuleListScreen extends KineticPage {
    private static final int PANEL_X = 20;
    private static final int PANEL_Y = 12;
    private static final int PANEL_W = 600;
    private static final int PANEL_H = 342;
    private static final int COLS = 9;
    private static final int CELL_W = 60;
    private static final int CELL_H = 48;
    private static final int VISIBLE_ROWS = 5;
    private static final int GRID_W = COLS * CELL_W;
    private static final int GRID_H = VISIBLE_ROWS * CELL_H;
    private static final int SEARCH_X = PANEL_X + 16;
    private static final int SEARCH_Y = PANEL_Y + 24;
    private static final int SEARCH_W = 450;
    private static final int BUTTON_W = 100;
    private static final int BUTTON_X = PANEL_X + PANEL_W - BUTTON_W - 16;
    private static final int BUTTON_Y = PANEL_Y + 24;
    private static final int GRID_X = PANEL_X + 18;
    private static final int GRID_Y = SEARCH_Y + 20 + 10;
    private static final int SCROLL_X = GRID_X + GRID_W + 6;
    private static final int SCROLL_W = 4;

    private final List<String> allEntityIds = new ArrayList<>();
    private final List<String> filteredEntityIds = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final KineticScrollController scroll = new KineticScrollController();
    private final KineticEntityPreview preview = KineticEntityPreview.create();

    private KineticTextField searchBox;
    private String searchQuery = "";
    private List<Component> deferredTooltip;
    private boolean syncRequested;
    private boolean initialSyncPending;

    public EntityResetRuleListScreen() {
        super(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.title"));
        rebuildEntityData();
    }

    @Override
    protected void build(KineticUi ui) {
        searchBox = ui.textField(SEARCH_X, SEARCH_Y, SEARCH_W)
                .label(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.search_hint"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.search_hint"))
                .value(searchQuery)
                .maxLength(256)
                .onChange(value -> {
                    searchQuery = value == null ? "" : value;
                    updateSearch(searchQuery);
                })
                .firstShownTextAsDefault().build();

        ui.button(BUTTON_X, BUTTON_Y, BUTTON_W)
                .text(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.back"))
                .onClick(this::navigateBack)
                .build();

        ui.add(new EntityGridControl(GRID_X - 4, GRID_Y - 4, GRID_W + 16, GRID_H + 8));
        updateSearch(searchQuery);
        requestServerRulesOnce();
    }

    private void requestServerRulesOnce() {
        if (syncRequested || !KineticClientRuntime.connected()) return;
        syncRequested = true;
        initialSyncPending = true;
        EntityReseRuleNetwork.requestRules();
    }

    private void rebuildEntityData() {
        buildEntityList();
        buildSearchData();
    }

    private void buildEntityList() {
        allEntityIds.clear();
        KineticRegistries.entityTypes().ids().stream()
                .sorted(ResourceLocation::compareTo)
                .forEach(id -> {
                    EntityType<?> type = KineticRegistries.entityTypes().get(id);
                    String value = id.toString();
                    if (EntityReseConfig.hasRule(value)
                            || (type != null && type.getCategory() != MobCategory.MISC)) {
                        allEntityIds.add(value);
                    }
                });

        for (String configuredId : EntityReseConfig.ENTITY_RULES_CACHE.keySet()) {
            if (!allEntityIds.contains(configuredId)) allEntityIds.add(configuredId);
        }
    }

    private void buildSearchData() {
        searchData.clear();
        for (String id : allEntityIds) {
            String name = entityName(id);
            String raw = id + " " + name;
            searchData.put(id, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private void updateSearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredEntityIds.clear();
        for (String id : allEntityIds) {
            if (normalized.isEmpty()
                    || KineticSearch.match(searchData.getOrDefault(id, id.toLowerCase(Locale.ROOT)), normalized)) {
                filteredEntityIds.add(id);
            }
        }
        filteredEntityIds.sort((left, right) -> {
            int configuredCompare = Boolean.compare(
                    EntityReseConfig.ENTITY_RULES_CACHE.containsKey(right),
                    EntityReseConfig.ENTITY_RULES_CACHE.containsKey(left)
            );
            return configuredCompare != 0 ? configuredCompare : left.compareToIgnoreCase(right);
        });
        scroll.reset();
        updateScrollRange();
    }

    private void updateScrollRange() {
        int totalRows = (filteredEntityIds.size() + COLS - 1) / COLS;
        scroll.update(totalRows, VISIBLE_ROWS);
    }

    private String entityName(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        return type == null ? id : type.getDescription().getString();
    }

    private Component switchState(boolean enabled) {
        return KineticI18n.translatable(enabled
                ? "gui.entitycontrol.reset.rule_list.rule.cancelled.on"
                : "gui.entitycontrol.reset.rule_list.rule.cancelled.off");
    }

    private List<Component> buildTooltip(String id) {
        List<Component> tooltip = new ArrayList<>();
        ResourceLocation location = KineticResourceIds.tryParse(id);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        tooltip.add(type == null ? Component.literal(id) : type.getDescription());
        tooltip.add(Component.literal(id));

        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(id);
        if (rule == null) {
            tooltip.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.rule.none"));
        } else {
            tooltip.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.rule.configured"));
            tooltip.add(KineticI18n.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.threshold", rule.threshold
            ));
            tooltip.add(KineticI18n.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.real", switchState(rule.countRealDeath)
            ));
            tooltip.add(KineticI18n.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.prevented", switchState(rule.countPreventedDeath)
            ));
            tooltip.add(KineticI18n.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.cancelled", switchState(rule.countCancelledDeath)
            ));
        }
        tooltip.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.left_click_hint"));
        tooltip.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.zoom_hint"));
        return tooltip;
    }

    private int entityIndex(double mouseX, double mouseY) {
        if (mouseX < GRID_X || mouseX >= GRID_X + GRID_W || mouseY < GRID_Y || mouseY >= GRID_Y + GRID_H) return -1;
        int column = (int) ((mouseX - GRID_X) / CELL_W);
        int row = (int) ((mouseY - GRID_Y + scroll.visualShift(CELL_H)) / CELL_H);
        if (column < 0 || column >= COLS || row < 0 || row >= VISIBLE_ROWS) return -1;
        return scroll.smoothIndexOffset() * COLS + row * COLS + column;
    }

    private void openRuleEditor(String entityId) {
        if (!initialSyncPending) openChild(new EntityResetRuleEditScreen(this, entityId));
    }

    public void onServerOperationResult(byte result) {
        rebuildEntityData();
        updateSearch(searchQuery);
        if (result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS
                || result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS) {
            KTConfigApi.notifySaved(EntityReseConfigGui.PAGE_ID);
        } else {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
        }
    }

    public void onRemoteRulesUpdated() {
        initialSyncPending = false;
        rebuildEntityData();
        updateSearch(searchQuery);
    }

    void onRuleSaved() {
        rebuildEntityData();
        updateSearch(searchQuery);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.canvasBackground(graphics, width(), height());
        KineticTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        graphics.centeredText(title(), width() / 2, 18, KineticTheme.current().text(), false);
        graphics.text(
                KineticI18n.translatable(
                        "gui.entitycontrol.reset.rule_list.count",
                        filteredEntityIds.size(),
                        allEntityIds.size()
                ),
                PANEL_X + 18,
                PANEL_Y + 9,
                KineticTheme.current().text()
        );
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        if (deferredTooltip != null) showTooltip(deferredTooltip);
    }

    @Override
    protected void onRemoved() {
        preview.clear();
    }

    private final class EntityGridControl extends KineticCustomControl {
        private EntityGridControl(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        protected void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
            deferredTooltip = null;
            updateScrollRange();
            KineticTheme.itemGrid(graphics, GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8);
            KineticTheme.stateOutline(graphics, GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8, false, false, false);

            if (filteredEntityIds.isEmpty()) {
                graphics.centeredText(
                        KineticI18n.translatable("gui.entitycontrol.reset.rule_list.empty"),
                        GRID_X + GRID_W / 2,
                        GRID_Y + GRID_H / 2,
                        KineticTheme.current().mutedText(),
                        false
                );
            }

            int firstRow = scroll.smoothIndexOffset();
            int shift = scroll.visualShift(CELL_H);
            int first = firstRow * COLS;
            int last = Math.min(first + (VISIBLE_ROWS + 1) * COLS, filteredEntityIds.size());
            graphics.clipped(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H, () -> {
                for (int index = first; index < last; index++) {
                    int localIndex = index - first;
                    int x = GRID_X + localIndex % COLS * CELL_W;
                    int y = GRID_Y + localIndex / COLS * CELL_H - shift;
                    String id = filteredEntityIds.get(index);
                    boolean configured = EntityReseConfig.hasRule(id);
                    boolean hovered = mouseX >= GRID_X && mouseX < GRID_X + GRID_W
                            && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H
                            && mouseX >= x && mouseX < x + CELL_W
                            && mouseY >= y && mouseY < y + CELL_H;

                    KineticTheme.itemSlot(graphics, x, y, CELL_W, CELL_H, 4, false, hovered, false);
                    if (configured) {
                        KineticTheme.indicatorOutline(
                                graphics, x, y, CELL_W, CELL_H, KineticTheme.Indicator.SUCCESS, 2
                        );
                    }

                    String stateKey = "entityrese:list:" + id;
                    boolean rendered = preview.render(
                            graphics, id, stateKey,
                            x + 3, y + 3, CELL_W - 6, CELL_H - 6,
                            hovered
                    );
                    int visibleTop = Math.max(y, GRID_Y);
                    int visibleBottom = Math.min(y + CELL_H, GRID_Y + GRID_H);
                    if (visibleBottom > visibleTop) {
                        registerPreviewZoomArea(preview, stateKey, x, visibleTop, CELL_W, visibleBottom - visibleTop);
                    }
                    if (!rendered) {
                        graphics.centeredText(
                                KineticI18n.translatable("gui.entitycontrol.reset.rule_list.invalid_entity"),
                                x + CELL_W / 2,
                                y + CELL_H / 2 - 4,
                                KineticTheme.current().text(),
                                false
                        );
                    }
                    if (hovered) deferredTooltip = buildTooltip(id);
                }
            });

            scroll.render(graphics, mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18);
        }

        @Override
        protected boolean onMouseClick(MouseInput input) {
            if (scroll.beginDrag(
                    input.x(), input.y(), input.button(), SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18, 2
            )) return true;
            if (!input.isLeft()) return false;
            int index = entityIndex(input.x(), input.y());
            if (index < 0 || index >= filteredEntityIds.size()) return false;
            openRuleEditor(filteredEntityIds.get(index));
            return true;
        }

        @Override
        protected boolean onMouseDrag(MouseDragInput input) {
            return scroll.drag(input.y(), GRID_Y, GRID_H, 18);
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
