package dev.xyat.entitycontrol.reset.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
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
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer();

    private EditBox searchBox;
    private String searchQuery = "";
    private List<Component> deferredTooltip;
    private boolean syncRequested;
    private boolean initialSyncPending;

    public EntityResetRuleListScreen(Screen parent) {
        super(Component.translatable("gui.entitycontrol.reset.rule_list.title"));
        this.parent = parent;
        useCanvas(640F, 360F, 6);
        rebuildEntityData();
    }

    @Override
    protected void buildUi() {
        searchBox = addRenderableWidget(new EditBox(
                font,
                SEARCH_X,
                SEARCH_Y,
                SEARCH_W,
                SEARCH_H,
                Component.translatable("gui.entitycontrol.reset.rule_list.search_hint")
        ));
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setSuggestion(Component.translatable("gui.entitycontrol.reset.rule_list.search_hint").getString());
        searchBox.setResponder(value -> {
            searchQuery = value == null ? "" : value;
            updateSearch(searchQuery);
        });

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.reset.rule_list.back"),
                        button -> closeToParent())
                .bounds(BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H)
                .build());

        updateSearch(searchQuery);
        requestServerRulesOnce();
    }

    private void requestServerRulesOnce() {
        if (syncRequested || Minecraft.getInstance().getConnection() == null) return;
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
        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .sorted(ResourceLocation::compareTo)
                .forEach(id -> {
                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
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
        graphics.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF171717, 0xFF0E0E0E);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        GuiTheme.itemGrid(graphics, GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8, 6);
        graphics.renderOutline(GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 18, 0xFFFFFFFF);
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

        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
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

                GuiTheme.itemSlot(graphics, x, y, CELL_W, CELL_H, 4, hovered);
                if (configured) {
                    graphics.renderOutline(x, y, CELL_W, CELL_H, 0xFF55FF55);
                    graphics.renderOutline(x + 1, y + 1, CELL_W - 2, CELL_H - 2, 0xFF55FF55);
                }

                boolean rendered = previewRenderer.render(
                        graphics,
                        id,
                        "entityrese:list:" + id,
                        x + 3,
                        y + 3,
                        CELL_W - 6,
                        CELL_H - 6,
                        canvasScale,
                        canvasX,
                        canvasY,
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
            graphics.disableScissor();
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
        ResourceLocation location = ResourceLocation.tryParse(id);
        EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
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
        if (minecraft == null || initialSyncPending) return;
        minecraft.setScreen(new EntityResetRuleEditScreen(this, entityId));
    }

    public void onServerOperationResult(byte result) {
        rebuildEntityData();
        updateSearch(searchQuery);
        if (result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS
                || result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS) {
            KTConfigApi.notifySaved(EntityReseConfigGui.PAGE_ID);
        } else {
            GuiOverlay.toast(Component.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
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

        if (button == 0 && scroll.beginDrag(
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

        if (button == 0 && inGrid(mouseX, mouseY)) {
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
        if (Screen.hasControlDown() && inGrid(mouseX, mouseY)) {
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
            GuiOverlay.requestTooltip(deferredTooltip, mouseX, mouseY);
        }
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public void removed() {
        previewRenderer.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
