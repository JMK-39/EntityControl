package dev.xyat.entitycontrol.reset.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.render.KineticEntityPreview.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EntityResetRuleListScreen extends KineticScreen {
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
    private static final int SEARCH_H = 20;
    private static final int BUTTON_W = 100;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_X = PANEL_X + PANEL_W - BUTTON_W - 16;
    private static final int BUTTON_Y = PANEL_Y + 24;
    private static final int GRID_X = PANEL_X + 18;
    private static final int GRID_Y = SEARCH_Y + SEARCH_H + 10;
    private static final int SCROLL_X = GRID_X + GRID_W + 6;
    private static final int SCROLL_W = 4;

    private final Screen parent;
    private final List<String> allEntityIds = new ArrayList<>();
    private final List<String> filteredEntityIds = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final GridScrollController scroll = new GridScrollController();
    private final EntityPreviewRenderer previewRenderer = KineticWidgets.createEntityPreviewRenderer();

    private KineticEditBox searchBox;
    private String searchQuery = "";
    private List<Component> deferredTooltip;
    private boolean syncRequested;
    private boolean initialSyncPending;

    public EntityResetRuleListScreen(Screen parent) {
        super(Component.translatable("gui.entitycontrol.reset.rule_list.title"));
        this.parent = parent;
        setParentScreen(parent);
        rebuildEntityData();
    }

    @Override
    protected void buildUi() {
        searchBox = addTextField(
                SEARCH_X,
                SEARCH_Y,
                SEARCH_W,
                Component.translatable("gui.entitycontrol.reset.rule_list.search_hint"),
                Component.translatable("gui.entitycontrol.reset.rule_list.search_hint"),
                null,
                null
        );
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value == null ? "" : value;
            updateSearch(searchQuery);
        });

        addButton(
                BUTTON_X, BUTTON_Y, BUTTON_W,
                Component.translatable("gui.entitycontrol.reset.rule_list.back"),
                null,
                this::closeToParent
        );

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
            if (!allEntityIds.contains(configuredId)) {
                allEntityIds.add(configuredId);
            }
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
        scroll.updateRange(Math.max(0, totalRows - VISIBLE_ROWS), totalRows, VISIBLE_ROWS);
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        GuiTheme.canvasBackground(graphics, canvasWidth(), canvasHeight());
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        GuiTheme.itemGrid(graphics, GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8);
        GuiTheme.stateOutline(graphics, GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8, false, false, false);
        graphics.drawCenteredString(font, title, canvasWidth() / 2, 18, 0xFFFFFFFF);
        renderEntityCount(graphics);
        renderGrid(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(
                scroll,
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                SCROLL_W,
                GRID_H,
                18
        );

        if (filteredEntityIds.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.entitycontrol.reset.rule_list.empty"),
                    GRID_X + GRID_W / 2,
                    GRID_Y + GRID_H / 2,
                    0xFFFFFFFF
            );
        }
    }

    private void renderEntityCount(GuiGraphics graphics) {
        graphics.drawString(
                font,
                Component.translatable(
                        "gui.entitycontrol.reset.rule_list.count",
                        filteredEntityIds.size(),
                        allEntityIds.size()
                ),
                PANEL_X + 18,
                PANEL_Y + 9,
                0xFFFFFFFF,
                false
        );
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int firstRow = scroll.smoothIndexOffset();
        int shift = scroll.visualShift(CELL_H);
        int first = firstRow * COLS;
        int last = Math.min(first + (VISIBLE_ROWS + 1) * COLS, filteredEntityIds.size());

        enableUiScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        try {
            for (int index = first; index < last; index++) {
                int localIndex = index - first;
                int x = GRID_X + localIndex % COLS * CELL_W;
                int y = GRID_Y + localIndex / COLS * CELL_H - shift;
                String id = filteredEntityIds.get(index);
                boolean configured = EntityReseConfig.hasRule(id);
                boolean hovered = inGrid(mouseX, mouseY)
                        && mouseX >= x && mouseX < x + CELL_W
                        && mouseY >= y && mouseY < y + CELL_H;

                GuiTheme.itemSlot(graphics, x, y, CELL_W, CELL_H, 4, false, hovered, false);
                if (configured) {
                    GuiTheme.indicatorOutline(
                            graphics, x, y, CELL_W, CELL_H, GuiTheme.Indicator.SUCCESS, 2
                    );
                }

                boolean rendered = previewRenderer.render(
                        graphics,
                        id,
                        "entityrese:list:" + id,
                        x + 3,
                        y + 3,
                        CELL_W - 6,
                        CELL_H - 6,
                        canvasScale(),
                        canvasX(),
                        canvasY(),
                        hovered
                );
                if (!rendered) {
                    graphics.drawCenteredString(font, "?", x + CELL_W / 2, y + CELL_H / 2 - 4, 0xFFFFFFFF);
                }

                if (hovered) {
                    deferredTooltip = buildTooltip(id);
                }
            }
        } finally {
            disableUiScissor(graphics);
        }
    }

    private List<Component> buildTooltip(String id) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(entityName(id)));
        tooltip.add(Component.literal(id));

        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(id);
        if (rule == null) {
            tooltip.add(Component.translatable("gui.entitycontrol.reset.rule_list.rule.none"));
        } else {
            tooltip.add(Component.translatable("gui.entitycontrol.reset.rule_list.rule.configured"));
            tooltip.add(Component.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.threshold",
                    Component.literal(String.valueOf(rule.threshold))
            ));
            tooltip.add(Component.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.real",
                    switchState(rule.countRealDeath)
            ));
            tooltip.add(Component.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.prevented",
                    switchState(rule.countPreventedDeath)
            ));
            tooltip.add(Component.translatable(
                    "gui.entitycontrol.reset.rule_list.rule.cancelled",
                    switchState(rule.countCancelledDeath)
            ));
        }

        tooltip.add(Component.translatable("gui.entitycontrol.reset.rule_list.left_click_hint"));
        tooltip.add(Component.translatable("gui.entitycontrol.reset.rule_list.zoom_hint"));
        return tooltip;
    }

    private Component switchState(boolean enabled) {
        return Component.translatable(enabled
                ? "gui.entitycontrol.reset.rule_list.rule.cancelled.on"
                : "gui.entitycontrol.reset.rule_list.rule.cancelled.off");
    }

    private String entityName(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        return type == null ? id : type.getDescription().getString();
    }

    private boolean inGrid(double mouseX, double mouseY) {
        return mouseX >= GRID_X && mouseX < GRID_X + GRID_W
                && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H;
    }

    private int entityIndex(double mouseX, double mouseY) {
        int column = (int) ((mouseX - GRID_X) / CELL_W);
        int row = (int) ((mouseY - GRID_Y + scroll.visualShift(CELL_H)) / CELL_H);
        if (column < 0 || column >= COLS || row < 0 || row >= VISIBLE_ROWS) return -1;
        return scroll.smoothIndexOffset() * COLS + row * COLS + column;
    }

    private void openRuleEditor(String entityId) {
        if (initialSyncPending) return;
        KineticClientRuntime.openScreen(new EntityResetRuleEditScreen(this, entityId));
    }

    public void onServerOperationResult(byte result) {
        rebuildEntityData();
        updateSearch(searchQuery);
        if (result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS
                || result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS) {
            KTConfigApi.notifySaved(EntityReseConfigGui.PAGE_ID);
        } else {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
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
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (super.canvasMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (KineticMouseButtons.isPrimary(button) && scroll.beginDrag(
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                SCROLL_W,
                GRID_H,
                18,
                2
        )) {
            return true;
        }

        if (KineticMouseButtons.isPrimary(button) && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                openRuleEditor(filteredEntityIds.get(index));
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, GRID_Y, GRID_H, 18)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button)
                || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (KineticClientRuntime.controlModifierDown() && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                String id = filteredEntityIds.get(index);
                previewRenderer.adjustZoom("entityrese:list:" + id, delta);
                return true;
            }
        }
        if (inGrid(mouseX, mouseY) && scroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (deferredTooltip != null) {
            KineticOverlays.requestTooltip(deferredTooltip, mouseX, mouseY);
        }
    }

    private void closeToParent() {
        navigateBack();
    }

    @Override
    protected boolean handleCloseRequest() {
        return false;
    }

    @Override
    protected void screenRemoved() {
        previewRenderer.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
