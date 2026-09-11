package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.selector.EntitySelectorScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.HighZButton;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlockEntityPoolScreen extends KineticScreen {
    private static final int LIST_X = 12;
    private static final int LIST_W = 356;
    private static final int SEARCH_Y = 40;
    private static final int LIST_Y = 67;
    private static final int LIST_H = 244;
    private static final int ROW_H = 52;
    private static final int ROW_GAP = 2;
    private static final int VISIBLE_ROWS = 4;
    private static final int SCROLL_X = LIST_X + LIST_W - 8;
    private static final int SCROLL_W = 4;
    private static final int RIGHT_X = 376;
    private static final int RIGHT_W = 252;

    private final Screen parent;
    private final BreakSpawnConfig.ConfigRoot config;
    private final String blockId;
    private final BreakSpawnConfig.BlockRule blockRule;
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer();
    private final GridScrollController scroll = new GridScrollController();
    private final List<String> filteredIds = new ArrayList<>();
    private final List<Button> contextButtons = new ArrayList<>();

    private EditBox searchBox;
    private EditBox weightBox;
    private String selectedEntityId;
    private int contextX;
    private int contextY;
    private boolean contextOpen;

    public BlockEntityPoolScreen(
            Screen parent,
            BreakSpawnConfig.ConfigRoot config,
            String blockId,
            BreakSpawnConfig.BlockRule blockRule
    ) {
        super(Component.translatable("gui.entitycontrol.breakspawn.pool.title"));
        this.parent = parent;
        this.config = config;
        this.blockId = blockId;
        this.blockRule = blockRule;
        useCanvas(640f, 360f, 6);
        previewRenderer.setRotationSpeedPercent(100);
        refreshFiltered("");
        if (!blockRule.entityWeights.isEmpty()) {
            selectedEntityId = blockRule.entityWeights.keySet().iterator().next();
        }
    }

    @Override
    protected void buildUi() {
        searchBox = addRenderableWidget(new EditBox(
                font,
                LIST_X,
                SEARCH_Y,
                244,
                20,
                Component.translatable("gui.entitycontrol.breakspawn.pool.search")
        ));
        searchBox.setMaxLength(128);
        searchBox.setResponder(this::refreshFiltered);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.pool.manage"),
                        ignored -> openEntitySelector())
                .bounds(LIST_X + 249, SEARCH_Y, 107, 20)
                .build());

        weightBox = addRenderableWidget(new EditBox(font, 490, 103, 120, 20, Component.empty()));
        weightBox.setMaxLength(16);
        weightBox.setResponder(value -> {
            if (selectedEntityId == null || !blockRule.entityWeights.containsKey(selectedEntityId)) {
                return;
            }
            try {
                blockRule.entityWeights.put(selectedEntityId, Math.max(0, Integer.parseInt(value.trim())));
            } catch (Exception ignored) {
            }
        });

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.pool.attributes"),
                        ignored -> openAttributeEditor())
                .bounds(388, 208, 72, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.pool.equipment"),
                        ignored -> openEquipmentEditor())
                .bounds(465, 208, 72, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.pool.nbt"),
                        ignored -> openEntityNbtEditor())
                .bounds(542, 208, 68, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.pool.remove"),
                        ignored -> removeSelected())
                .bounds(490, 238, 120, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.back"),
                        ignored -> onClose())
                .bounds(514, 322, 104, 22).build());

        buildContextButtons();
        refreshFiltered(searchBox.getValue());
        refreshSelected();
    }

    private void buildContextButtons() {
        contextButtons.clear();
        for (int i = 0; i < 7; i++) {
            final int index = i;
            HighZButton button = new HighZButton(0, 0, 150, 18, Component.empty(), ignored -> toggleContextValue(index), null, 300);
            button.visible = false;
            contextButtons.add(addRenderableWidget(button));
        }
    }

    private void openContextMenu(double mouseX, double mouseY) {
        contextX = Math.max(8, Math.min(470, (int) mouseX + 6));
        contextY = Math.max(8, Math.min(156, (int) mouseY + 6));
        contextOpen = true;
        for (int i = 0; i < contextButtons.size(); i++) {
            Button button = contextButtons.get(i);
            button.setX(contextX + 6);
            button.setY(contextY + 26 + i * 23);
            button.visible = true;
        }
        updateContextLabels();
    }

    private void closeContextMenu() {
        contextOpen = false;
        for (Button button : contextButtons) {
            button.visible = false;
        }
    }

    private void toggleContextValue(int index) {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule == null) {
            return;
        }
        switch (index) {
            case 0 -> rule.enabled = !rule.enabled;
            case 1 -> rule.persistent = !rule.persistent;
            case 2 -> rule.silent = !rule.silent;
            case 3 -> rule.glowing = !rule.glowing;
            case 4 -> rule.noAi = !rule.noAi;
            case 5 -> rule.invulnerable = !rule.invulnerable;
            case 6 -> rule.customNameVisible = !rule.customNameVisible;
            default -> {
            }
        }
        updateContextLabels();
    }

    private void updateContextLabels() {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule == null) {
            return;
        }
        boolean[] values = {rule.enabled, rule.persistent, rule.silent, rule.glowing, rule.noAi, rule.invulnerable, rule.customNameVisible};
        String[] keys = {
                "gui.entitycontrol.breakspawn.switch.entity_enabled",
                "gui.entitycontrol.breakspawn.switch.persistent",
                "gui.entitycontrol.breakspawn.switch.silent",
                "gui.entitycontrol.breakspawn.switch.glowing",
                "gui.entitycontrol.breakspawn.switch.no_ai",
                "gui.entitycontrol.breakspawn.switch.invulnerable",
                "gui.entitycontrol.breakspawn.switch.name_visible"
        };
        for (int i = 0; i < contextButtons.size(); i++) {
            contextButtons.get(i).setMessage(Component.translatable(
                    keys[i],
                    Component.translatable(values[i] ? "gui.entitycontrol.breakspawn.switch.on" : "gui.entitycontrol.breakspawn.switch.off")
            ));
        }
    }

    private void openEntitySelector() {
        Minecraft.getInstance().setScreen(new EntitySelectorScreen(
                this,
                Component.translatable("gui.entitycontrol.breakspawn.pool.selector.title"),
                blockRule.entityWeights.keySet(),
                selected -> {
                    List<String> valid = new ArrayList<>();
                    for (String id : selected) {
                        if (isLivingEntity(id)) {
                            valid.add(id);
                        }
                    }
                    blockRule.entityWeights.keySet().removeIf(id -> !valid.contains(id));
                    for (String id : valid) {
                        BreakSpawnConfig.EntityRule entityRule = config.entities.computeIfAbsent(id, ignored -> new BreakSpawnConfig.EntityRule());
                        blockRule.entityWeights.putIfAbsent(id, Math.max(1, entityRule.weight));
                    }
                    if (selectedEntityId != null && !blockRule.entityWeights.containsKey(selectedEntityId)) {
                        selectedEntityId = null;
                    }
                    if (selectedEntityId == null && !blockRule.entityWeights.isEmpty()) {
                        selectedEntityId = blockRule.entityWeights.keySet().iterator().next();
                    }
                    refreshFiltered(searchBox == null ? "" : searchBox.getValue());
                    refreshSelected();
                }
        ));
    }

    private boolean isLivingEntity(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        EntityType<?> type = entityType(id);
        if (type == null) {
            return false;
        }
        try {
            Entity entity = type.create(minecraft.level);
            return entity instanceof LivingEntity;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private EntityType<?> entityType(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
    }

    private void refreshFiltered(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredIds.clear();
        for (String id : blockRule.entityWeights.keySet()) {
            EntityType<?> type = entityType(id);
            String name = type == null ? id : type.getDescription().getString();
            String searchData = (id + " " + name + " " + KineticSearch.pinyin(name)).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || KineticSearch.match(searchData, normalized)) {
                filteredIds.add(id);
            }
        }
        filteredIds.sort(Comparator.naturalOrder());
        scroll.update(filteredIds.size(), VISIBLE_ROWS);
    }

    private void refreshSelected() {
        Integer weight = selectedEntityId == null ? null : blockRule.entityWeights.get(selectedEntityId);
        weightBox.active = weight != null;
        weightBox.setValue(weight == null ? "" : String.valueOf(weight));
    }


    private BreakSpawnConfig.EntityRule selectedEntityRule() {
        if (selectedEntityId == null) {
            return null;
        }
        return config.entities.computeIfAbsent(selectedEntityId, ignored -> new BreakSpawnConfig.EntityRule());
    }

    private void openAttributeEditor() {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule != null) {
            Minecraft.getInstance().setScreen(new AttributeRangeScreen(this, selectedEntityId, rule));
        }
    }

    private void openEquipmentEditor() {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule != null) {
            Minecraft.getInstance().setScreen(new EquipmentEditorScreen(this, selectedEntityId, rule));
        }
    }

    private void openEntityNbtEditor() {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule != null) {
            Minecraft.getInstance().setScreen(new NbtEditorScreen(
                    rule.entityNbt == null ? "" : rule.entityNbt,
                    value -> rule.entityNbt = value == null ? "" : value,
                    this
            ));
        }
    }

    private void removeSelected() {
        if (selectedEntityId == null) {
            return;
        }
        blockRule.entityWeights.remove(selectedEntityId);
        selectedEntityId = null;
        if (!blockRule.entityWeights.isEmpty()) {
            selectedEntityId = blockRule.entityWeights.keySet().iterator().next();
        }
        refreshFiltered(searchBox == null ? "" : searchBox.getValue());
        refreshSelected();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, canvasWidth, canvasHeight, 0xD9000000);
        GuiTheme.panel(graphics, 6, 6, 628, 348, 0xD91A1E26, 0xFF506070);
        GuiTheme.panel(graphics, LIST_X, LIST_Y, LIST_W, LIST_H, 0xB010141A, 0xFF43515F);
        GuiTheme.panel(graphics, RIGHT_X, LIST_Y, RIGHT_W, LIST_H, 0xB010141A, 0xFF43515F);
        graphics.drawString(font, title, 14, 16, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.pool.block", blockName()), 380, 18, 0xFFFFFFFF, false);
        renderList(graphics, mouseX, mouseY, partialTick);
        renderDetails(graphics, mouseX, mouseY, partialTick);
        if (contextOpen) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            GuiTheme.panel(graphics, contextX, contextY, 162, 196, 0xF018202A, 0xFFAAAAAA);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.quick_switches"), contextX + 8, contextY + 7, 0xFFFFFFFF, false);
            graphics.pose().popPose();
        }
    }

    private Component blockName() {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        BlockNameLookup lookup = new BlockNameLookup(id);
        return Component.literal(lookup.name());
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (filteredIds.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.pool.empty"), LIST_X + LIST_W / 2, LIST_Y + 110, 0xFFFFFFFF);
            return;
        }
        int start = scroll.smoothIndexOffset();
        int shift = scroll.visualShift(ROW_H + ROW_GAP);
                enableCanvasScissor(graphics, LIST_X + 2, LIST_Y + 2, LIST_X + LIST_W - 10, LIST_Y + LIST_H - 2);
        try {
for (int row = 0; row <= VISIBLE_ROWS; row++) {
            int index = start + row;
            if (index >= filteredIds.size()) {
                break;
            }
            String id = filteredIds.get(index);
            int y = LIST_Y + 2 + row * (ROW_H + ROW_GAP) - shift;
            boolean hovered = isInside(mouseX, mouseY, LIST_X + 2, y, LIST_W - 12, ROW_H);
            boolean selected = id.equals(selectedEntityId);
            int border = hovered ? 0xFFAAAAAA : 0xFF42D66B;
            int background = selected ? 0xB0283440 : 0xA0182028;
            GuiTheme.panel(graphics, LIST_X + 2, y, LIST_W - 12, ROW_H, background, border);
            EntityPreviewRenderer.drawCheckerboard(graphics, LIST_X + 5, y + 4, 44, 44);
            previewRenderer.render(
                    graphics,
                    id,
                    "breakspawn-pool:" + blockId + ":" + id,
                    LIST_X + 5,
                    y + 4,
                    44,
                    44,
                    canvasScale,
                    canvasX,
                    canvasY,
                    hovered
            );
            EntityType<?> type = entityType(id);
            String name = type == null ? id : type.getDescription().getString();
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, name, 270)), LIST_X + 55, y + 9, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, id, 270)), LIST_X + 55, y + 25, 0xFFB8C8D8, false);
            int weight = blockRule.entityWeights.getOrDefault(id, 0);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.pool.weight_short", weight, String.format(Locale.ROOT, "%.1f", weightPercent(id))), LIST_X + 55, y + 39, 0xFFFFFFFF, false);
        }
        } finally {
            graphics.disableScissor();
        }
        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, SCROLL_X, LIST_Y + 2, SCROLL_W, LIST_H - 4, 24);
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (selectedEntityId == null || !blockRule.entityWeights.containsKey(selectedEntityId)) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.pool.select_hint"), RIGHT_X + RIGHT_W / 2, 178, 0xFFFFFFFF);
            return;
        }
        EntityPreviewRenderer.drawCheckerboard(graphics, 407, 84, 72, 72);
        previewRenderer.render(
                graphics,
                selectedEntityId,
                "breakspawn-pool-detail:" + blockId + ":" + selectedEntityId,
                407,
                84,
                72,
                72,
                canvasScale,
                canvasX,
                canvasY,
                isInside(mouseX, mouseY, 407, 84, 72, 72)
        );
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.pool.weight"), 490, 90, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.pool.share", String.format(Locale.ROOT, "%.1f", weightPercent(selectedEntityId))), 388, 172, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.pool.hint"), 388, 196, 0xFFFFFFFF, false);
    }

    private double weightPercent(String id) {
        long total = 0L;
        int current = 0;
        for (Map.Entry<String, Integer> entry : blockRule.entityWeights.entrySet()) {
            int value = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            total += value;
            if (entry.getKey().equals(id)) {
                current = value;
            }
        }
        return total <= 0L ? 0.0D : current * 100.0D / total;
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            String placeholder = font.plainSubstrByWidth(
                    Component.translatable("gui.entitycontrol.breakspawn.pool.search.placeholder").getString(),
                    Math.max(0, searchBox.getWidth() - 10)
            );
            graphics.drawString(font, placeholder, searchBox.getX() + 5, searchBox.getY() + 6, 0xFFB8C8D8, false);
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (contextOpen && !isInside(mouseX, mouseY, contextX, contextY, 162, 196)) {
            closeContextMenu();
        }
        if (super.canvasMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && scroll.beginDrag(mouseX, mouseY, SCROLL_X, LIST_Y + 2, SCROLL_W, LIST_H - 4, 24, 2)) {
            return true;
        }
        int row = rowAt(mouseX, mouseY);
        int index = scroll.smoothIndexOffset() + row;
        if (row >= 0 && index >= 0 && index < filteredIds.size()) {
            selectedEntityId = filteredIds.get(index);
            refreshSelected();
            if (button == 1) {
                openContextMenu(mouseX, mouseY);
            }
            return button == 0 || button == 1;
        }
        return false;
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, LIST_Y + 2, LIST_H - 4, 24)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        int row = rowAt(mouseX, mouseY);
        int index = scroll.smoothIndexOffset() + row;
        if (Screen.hasControlDown() && row >= 0 && index >= 0 && index < filteredIds.size()) {
            String id = filteredIds.get(index);
            previewRenderer.adjustZoom("breakspawn-pool:" + blockId + ":" + id, delta);
            return true;
        }
        if (Screen.hasControlDown() && selectedEntityId != null && isInside(mouseX, mouseY, 407, 84, 72, 72)) {
            previewRenderer.adjustZoom("breakspawn-pool-detail:" + blockId + ":" + selectedEntityId, delta);
            return true;
        }
        if (isInside(mouseX, mouseY, LIST_X, LIST_Y, LIST_W, LIST_H) && scroll.scroll(delta)) {
            closeContextMenu();
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, LIST_X + 2, LIST_Y + 2, LIST_W - 12, LIST_H - 4)) {
            return -1;
        }
        int stride = ROW_H + ROW_GAP;
        int localY = (int) mouseY - (LIST_Y + 2) + scroll.visualShift(stride);
        int row = localY / stride;
        if (row < 0 || row >= VISIBLE_ROWS || localY % stride >= ROW_H) {
            return -1;
        }
        return row;
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private record BlockNameLookup(ResourceLocation id) {
        String name() {
            if (id == null) {
                return "";
            }
            var block = ForgeRegistries.BLOCKS.getValue(id);
            return block == null ? id.toString() : block.getName().getString();
        }
    }
}
