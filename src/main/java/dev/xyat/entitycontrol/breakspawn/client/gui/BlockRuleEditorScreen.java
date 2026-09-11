package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetwork;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.selector.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BlockRuleEditorScreen extends KineticScreen {
    private static final int LEFT_X = 12;
    private static final int LEFT_W = 224;
    private static final int SEARCH_Y = 40;
    private static final int LIST_Y = 67;
    private static final int LIST_H = 277;
    private static final int ROW_H = 34;
    private static final int ROW_GAP = 2;
    private static final int VISIBLE_ROWS = 7;
    private static final int SCROLL_X = LEFT_X + LEFT_W - 8;
    private static final int SCROLL_W = 4;
    private static final int RIGHT_X = 244;
    private static final int RIGHT_W = 384;

    private final Screen parent;
    private final BreakSpawnConfig.ConfigRoot config;
    private final GridScrollController scroll = new GridScrollController();
    private final List<String> filteredBlocks = new ArrayList<>();
    private final List<AbstractWidget> probabilityWidgets = new ArrayList<>();
    private final List<AbstractWidget> spawnWidgets = new ArrayList<>();
    private final List<AbstractWidget> conditionWidgets = new ArrayList<>();

    private EditBox searchBox;
    private EditBox baseChanceBox;
    private EditBox stackIncreaseBox;
    private EditBox maxChanceBox;
    private EditBox resetTicksBox;
    private EditBox minCountBox;
    private EditBox maxCountBox;
    private EditBox minDistanceBox;
    private EditBox radiusBox;
    private EditBox verticalRadiusBox;
    private EditBox attemptsBox;
    private EditBox dimensionsBox;
    private EditBox biomesBox;
    private EditBox minYBox;
    private EditBox maxYBox;
    private EditBox minLightBox;
    private EditBox maxLightBox;
    private Button tabProbability;
    private Button tabSpawn;
    private Button tabConditions;
    private Button enabledButton;
    private Button stackingButton;
    private Button resetTriggerButton;
    private Button resetDifferentButton;
    private Button poolButton;
    private Button removeButton;
    private String selectedBlockId;
    private int activeTab;

    public BlockRuleEditorScreen(Screen parent, BreakSpawnConfig.ConfigRoot config) {
        super(Component.translatable("gui.entitycontrol.breakspawn.blocks.title"));
        this.parent = parent;
        this.config = config;
        useCanvas(640f, 360f, 6);
        refreshBlocks("");
        if (!config.blocks.isEmpty()) {
            selectedBlockId = config.blocks.keySet().iterator().next();
        }
    }

    @Override
    protected void buildUi() {
        probabilityWidgets.clear();
        spawnWidgets.clear();
        conditionWidgets.clear();

        searchBox = addRenderableWidget(new EditBox(
                font,
                LEFT_X,
                SEARCH_Y,
                142,
                20,
                Component.translatable("gui.entitycontrol.breakspawn.search.blocks")
        ));
        searchBox.setMaxLength(128);
        searchBox.setResponder(this::refreshBlocks);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.blocks.add"),
                        ignored -> openBlockSelector())
                .bounds(LEFT_X + 147, SEARCH_Y, 77, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.global.open"),
                        ignored -> openGlobalSettings())
                .bounds(325, 12, 72, 20)
                .build());
        removeButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.blocks.remove"),
                        ignored -> removeSelectedBlock())
                .bounds(402, 12, 72, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.save"),
                        ignored -> saveConfig())
                .bounds(479, 12, 72, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.back"),
                        ignored -> onClose())
                .bounds(556, 12, 72, 20)
                .build());

        tabProbability = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.blocks.tab.probability"),
                        ignored -> setActiveTab(0))
                .bounds(RIGHT_X, 40, 92, 20).build());
        tabSpawn = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.blocks.tab.spawn"),
                        ignored -> setActiveTab(1))
                .bounds(RIGHT_X + 97, 40, 92, 20).build());
        tabConditions = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.blocks.tab.conditions"),
                        ignored -> setActiveTab(2))
                .bounds(RIGHT_X + 194, 40, 92, 20).build());
        poolButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.blocks.pool"),
                        ignored -> openPoolEditor())
                .bounds(RIGHT_X + 291, 40, 92, 20).build());

        buildProbabilityWidgets();
        buildSpawnWidgets();
        buildConditionWidgets();

        refreshBlocks(searchBox.getValue());
        refreshSelectedFields();
        setActiveTab(activeTab);
    }

    private void buildProbabilityWidgets() {
        baseChanceBox = numberBox(350, 88, 78, probabilityWidgets, value -> selectedRule().baseChance = clampChance(value / 100.0D));
        stackIncreaseBox = numberBox(548, 88, 70, probabilityWidgets, value -> selectedRule().chancePerFailure = clampChance(value / 100.0D));
        maxChanceBox = numberBox(350, 120, 78, probabilityWidgets, value -> selectedRule().maxChance = clampChance(value / 100.0D));
        resetTicksBox = numberBox(548, 120, 70, probabilityWidgets, value -> selectedRule().resetAfterTicks = Math.max(0, (int) value));

        enabledButton = actionButton(255, 158, 176, probabilityWidgets, () -> {
            BreakSpawnConfig.BlockRule rule = selectedRule();
            if (rule != null) {
                rule.enabled = !rule.enabled;
                updateToggleLabels();
            }
        });
        stackingButton = actionButton(442, 158, 176, probabilityWidgets, () -> {
            BreakSpawnConfig.BlockRule rule = selectedRule();
            if (rule != null) {
                rule.stackingEnabled = !rule.stackingEnabled;
                updateToggleLabels();
            }
        });
        resetTriggerButton = actionButton(255, 183, 176, probabilityWidgets, () -> {
            BreakSpawnConfig.BlockRule rule = selectedRule();
            if (rule != null) {
                rule.resetOnTrigger = !rule.resetOnTrigger;
                updateToggleLabels();
            }
        });
        resetDifferentButton = actionButton(442, 183, 176, probabilityWidgets, () -> {
            BreakSpawnConfig.BlockRule rule = selectedRule();
            if (rule != null) {
                rule.resetOnDifferentBlock = !rule.resetOnDifferentBlock;
                updateToggleLabels();
            }
        });
    }

    private void buildSpawnWidgets() {
        minCountBox = numberBox(350, 88, 78, spawnWidgets, value -> selectedRule().minSpawnCount = Math.max(0, (int) value));
        maxCountBox = numberBox(548, 88, 70, spawnWidgets, value -> selectedRule().maxSpawnCount = Math.max(0, (int) value));
        minDistanceBox = numberBox(350, 120, 78, spawnWidgets, value -> selectedRule().minDistance = Math.max(0, (int) value));
        radiusBox = numberBox(548, 120, 70, spawnWidgets, value -> selectedRule().horizontalRadius = Math.max(0, (int) value));
        verticalRadiusBox = numberBox(350, 152, 78, spawnWidgets, value -> selectedRule().verticalRadius = Math.max(0, (int) value));
        attemptsBox = numberBox(548, 152, 70, spawnWidgets, value -> selectedRule().maxSpawnAttempts = Math.max(1, (int) value));
    }

    private void buildConditionWidgets() {
        dimensionsBox = textBox(350, 88, 268, conditionWidgets, value -> selectedRule().dimensions = value);
        biomesBox = textBox(350, 120, 268, conditionWidgets, value -> selectedRule().biomes = value);
        minYBox = numberBox(350, 152, 78, conditionWidgets, value -> selectedRule().minY = (int) value);
        maxYBox = numberBox(548, 152, 70, conditionWidgets, value -> selectedRule().maxY = (int) value);
        minLightBox = numberBox(350, 184, 78, conditionWidgets, value -> selectedRule().minLight = clampInt((int) value, 0, 15));
        maxLightBox = numberBox(548, 184, 70, conditionWidgets, value -> selectedRule().maxLight = clampInt((int) value, 0, 15));
    }

    private EditBox numberBox(int x, int y, int width, List<AbstractWidget> group, java.util.function.DoubleConsumer consumer) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.empty());
        box.setMaxLength(32);
        box.setResponder(text -> {
            BreakSpawnConfig.BlockRule rule = selectedRule();
            if (rule == null) {
                return;
            }
            try {
                consumer.accept(Double.parseDouble(text.trim()));
            } catch (Exception ignored) {
            }
        });
        addRenderableWidget(box);
        group.add(box);
        return box;
    }

    private EditBox textBox(int x, int y, int width, List<AbstractWidget> group, java.util.function.Consumer<String> consumer) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.empty());
        box.setMaxLength(4096);
        box.setResponder(text -> {
            if (selectedRule() != null) {
                consumer.accept(text);
            }
        });
        addRenderableWidget(box);
        group.add(box);
        return box;
    }

    private Button actionButton(int x, int y, int width, List<AbstractWidget> group, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
                .bounds(x, y, width, 20).build());
        group.add(button);
        return button;
    }

    private void openGlobalSettings() {
        Minecraft.getInstance().setScreen(new GlobalSettingsScreen(this, config));
    }

    private void saveConfig() {
        BreakSpawnNetwork.CHANNEL.sendToServer(new BreakSpawnNetwork.SaveConfigPacket(BreakSpawnConfig.GSON.toJson(config)));
    }

    private void openBlockSelector() {
        Minecraft.getInstance().setScreen(new ItemSelectorScreen(this, selection -> {
            if (!selection.isItem() || !(selection.stack().getItem() instanceof BlockItem blockItem)) {
                GuiOverlay.toast(Component.translatable("msg.entitycontrol.breakspawn.select_block_only"));
                return;
            }
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
            if (id == null) {
                return;
            }
            selectedBlockId = id.toString();
            config.blocks.computeIfAbsent(selectedBlockId, ignored -> BreakSpawnConfig.createBlockRuleFromDefaults(config.global));
            refreshBlocks(searchBox == null ? "" : searchBox.getValue());
            refreshSelectedFields();
        }));
    }

    private void openPoolEditor() {
        BreakSpawnConfig.BlockRule rule = selectedRule();
        if (rule == null || selectedBlockId == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new BlockEntityPoolScreen(this, config, selectedBlockId, rule));
    }

    private void removeSelectedBlock() {
        if (selectedBlockId == null) {
            return;
        }
        config.blocks.remove(selectedBlockId);
        selectedBlockId = null;
        if (!config.blocks.isEmpty()) {
            selectedBlockId = config.blocks.keySet().iterator().next();
        }
        refreshBlocks(searchBox == null ? "" : searchBox.getValue());
        refreshSelectedFields();
    }

    private BreakSpawnConfig.BlockRule selectedRule() {
        return selectedBlockId == null ? null : config.blocks.get(selectedBlockId);
    }

    private void refreshBlocks(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredBlocks.clear();
        for (String id : config.blocks.keySet()) {
            Block block = blockById(id);
            String name = block == null ? id : block.getName().getString();
            String searchData = (id + " " + name + " " + KineticSearch.pinyin(name)).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || KineticSearch.match(searchData, normalized)) {
                filteredBlocks.add(id);
            }
        }
        filteredBlocks.sort(Comparator.naturalOrder());
        scroll.update(filteredBlocks.size(), VISIBLE_ROWS);
        if (selectedBlockId != null && !config.blocks.containsKey(selectedBlockId)) {
            selectedBlockId = null;
        }
    }

    private Block blockById(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : ForgeRegistries.BLOCKS.getValue(location);
    }

    private void refreshSelectedFields() {
        BreakSpawnConfig.BlockRule rule = selectedRule();
        boolean hasRule = rule != null;
        for (AbstractWidget widget : probabilityWidgets) {
            widget.active = hasRule;
        }
        for (AbstractWidget widget : spawnWidgets) {
            widget.active = hasRule;
        }
        for (AbstractWidget widget : conditionWidgets) {
            widget.active = hasRule;
        }
        tabProbability.active = hasRule && activeTab != 0;
        tabSpawn.active = hasRule && activeTab != 1;
        tabConditions.active = hasRule && activeTab != 2;
        poolButton.active = hasRule;
        removeButton.active = hasRule;
        if (!hasRule) {
            return;
        }
        baseChanceBox.setValue(percent(rule.baseChance));
        stackIncreaseBox.setValue(percent(rule.chancePerFailure));
        maxChanceBox.setValue(percent(rule.maxChance));
        resetTicksBox.setValue(String.valueOf(rule.resetAfterTicks));
        minCountBox.setValue(String.valueOf(rule.minSpawnCount));
        maxCountBox.setValue(String.valueOf(rule.maxSpawnCount));
        minDistanceBox.setValue(String.valueOf(rule.minDistance));
        radiusBox.setValue(String.valueOf(rule.horizontalRadius));
        verticalRadiusBox.setValue(String.valueOf(rule.verticalRadius));
        attemptsBox.setValue(String.valueOf(rule.maxSpawnAttempts));
        dimensionsBox.setValue(rule.dimensions == null ? "" : rule.dimensions);
        biomesBox.setValue(rule.biomes == null ? "" : rule.biomes);
        minYBox.setValue(String.valueOf(rule.minY));
        maxYBox.setValue(String.valueOf(rule.maxY));
        minLightBox.setValue(String.valueOf(rule.minLight));
        maxLightBox.setValue(String.valueOf(rule.maxLight));
        updateToggleLabels();
    }

    private String percent(double chance) {
        double value = chance * 100.0D;
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private void updateToggleLabels() {
        BreakSpawnConfig.BlockRule rule = selectedRule();
        if (rule == null) {
            return;
        }
        enabledButton.setMessage(toggle("gui.entitycontrol.breakspawn.blocks.enabled", rule.enabled));
        stackingButton.setMessage(toggle("gui.entitycontrol.breakspawn.blocks.stacking", rule.stackingEnabled));
        resetTriggerButton.setMessage(toggle("gui.entitycontrol.breakspawn.blocks.reset_trigger", rule.resetOnTrigger));
        resetDifferentButton.setMessage(toggle("gui.entitycontrol.breakspawn.blocks.reset_different", rule.resetOnDifferentBlock));
    }

    private Component toggle(String key, boolean value) {
        return Component.translatable(key, Component.translatable(value
                ? "gui.entitycontrol.breakspawn.switch.on"
                : "gui.entitycontrol.breakspawn.switch.off"));
    }

    private void setActiveTab(int tab) {
        activeTab = clampInt(tab, 0, 2);
        boolean hasRule = selectedRule() != null;
        tabProbability.active = hasRule && activeTab != 0;
        tabSpawn.active = hasRule && activeTab != 1;
        tabConditions.active = hasRule && activeTab != 2;
        setVisible(probabilityWidgets, hasRule && activeTab == 0);
        setVisible(spawnWidgets, hasRule && activeTab == 1);
        setVisible(conditionWidgets, hasRule && activeTab == 2);
    }

    private void setVisible(List<AbstractWidget> widgets, boolean visible) {
        for (AbstractWidget widget : widgets) {
            widget.visible = visible;
        }
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, canvasWidth, canvasHeight, 0xD9000000);
        GuiTheme.panel(graphics, 6, 6, 628, 348, 0xD91A1E26, 0xFF506070);
        GuiTheme.panel(graphics, LEFT_X, LIST_Y, LEFT_W, LIST_H, 0xB010141A, 0xFF43515F);
        GuiTheme.panel(graphics, RIGHT_X, LIST_Y, RIGHT_W, LIST_H, 0xB010141A, 0xFF43515F);
        graphics.drawString(font, title, 14, 16, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.blocks.step_block"), 12, 30, 0xFFFFFFFF, false);
        if (selectedRule() != null) {
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.blocks.step_entity"), 244, 30, 0xFFFFFFFF, false);
        }
        renderBlockList(graphics, mouseX, mouseY);
        renderRightLabels(graphics);
    }

    private void renderBlockList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (filteredBlocks.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.blocks.empty"), LEFT_X + LEFT_W / 2, LIST_Y + 112, 0xFFFFFFFF);
            return;
        }
        int start = scroll.smoothIndexOffset();
        int shift = scroll.visualShift(ROW_H + ROW_GAP);
                enableCanvasScissor(graphics, LEFT_X + 2, LIST_Y + 2, LEFT_X + LEFT_W - 10, LIST_Y + LIST_H - 2);
        try {
for (int row = 0; row <= VISIBLE_ROWS; row++) {
            int index = start + row;
            if (index >= filteredBlocks.size()) {
                break;
            }
            String id = filteredBlocks.get(index);
            BreakSpawnConfig.BlockRule rule = config.blocks.get(id);
            int y = LIST_Y + 2 + row * (ROW_H + ROW_GAP) - shift;
            boolean hover = isInside(mouseX, mouseY, LEFT_X + 2, y, LEFT_W - 12, ROW_H);
            boolean selected = id.equals(selectedBlockId);
            int border = hover ? 0xFFAAAAAA : rule != null && rule.enabled ? 0xFF42D66B : selected ? 0xFFE5B94A : 0xFF43515F;
            GuiTheme.panel(graphics, LEFT_X + 2, y, LEFT_W - 12, ROW_H, 0xC0182028, border);
            Block block = blockById(id);
            if (block != null) {
                ItemStack stack = new ItemStack(block.asItem());
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, LEFT_X + 8, y + 9);
                }
                graphics.drawString(font, block.getName(), LEFT_X + 30, y + 6, 0xFFFFFFFF, false);
            }
            graphics.drawString(font, id, LEFT_X + 30, y + 19, 0xFFB8C8D8, false);
        }
        } finally {
            graphics.disableScissor();
        }
        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, SCROLL_X, LIST_Y + 2, SCROLL_W, LIST_H - 4, 20);
    }

    private void renderRightLabels(GuiGraphics graphics) {
        if (selectedRule() == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.blocks.select_hint"), RIGHT_X + RIGHT_W / 2, 178, 0xFFFFFFFF);
            return;
        }
        if (activeTab == 0) {
            label(graphics, "gui.entitycontrol.breakspawn.blocks.base_chance", 255, 94);
            label(graphics, "gui.entitycontrol.breakspawn.blocks.stack_increase", 442, 94);
            label(graphics, "gui.entitycontrol.breakspawn.blocks.max_chance", 255, 126);
            label(graphics, "gui.entitycontrol.breakspawn.blocks.reset_ticks", 442, 126);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.blocks.stack_hint"), 255, 226, 0xFFFFFFFF, false);
        } else if (activeTab == 1) {
            label(graphics, "gui.entitycontrol.breakspawn.min_count", 255, 94);
            label(graphics, "gui.entitycontrol.breakspawn.max_count", 442, 94);
            label(graphics, "gui.entitycontrol.breakspawn.min_distance", 255, 126);
            label(graphics, "gui.entitycontrol.breakspawn.radius", 442, 126);
            label(graphics, "gui.entitycontrol.breakspawn.vertical_radius", 255, 158);
            label(graphics, "gui.entitycontrol.breakspawn.spawn_attempts", 442, 158);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.blocks.pool_hint"), 255, 214, 0xFFFFFFFF, false);
        } else {
            label(graphics, "gui.entitycontrol.breakspawn.dimensions", 255, 94);
            label(graphics, "gui.entitycontrol.breakspawn.biomes", 255, 126);
            label(graphics, "gui.entitycontrol.breakspawn.min_y", 255, 158);
            label(graphics, "gui.entitycontrol.breakspawn.max_y", 442, 158);
            label(graphics, "gui.entitycontrol.breakspawn.min_light", 255, 190);
            label(graphics, "gui.entitycontrol.breakspawn.max_light", 442, 190);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.csv_hint"), 255, 228, 0xFFFFFFFF, false);
        }
    }

    private void label(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y, 0xFFFFFFFF, false);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            String placeholder = font.plainSubstrByWidth(
                    Component.translatable("gui.entitycontrol.breakspawn.search.blocks.placeholder").getString(),
                    Math.max(0, searchBox.getWidth() - 10)
            );
            graphics.drawString(font, placeholder, searchBox.getX() + 5, searchBox.getY() + 6, 0xFFB8C8D8, false);
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (super.canvasMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && scroll.beginDrag(mouseX, mouseY, SCROLL_X, LIST_Y + 2, SCROLL_W, LIST_H - 4, 20, 2)) {
            return true;
        }
        if (button == 0) {
            int row = rowAt(mouseX, mouseY);
            int index = scroll.smoothIndexOffset() + row;
            if (row >= 0 && index >= 0 && index < filteredBlocks.size()) {
                selectedBlockId = filteredBlocks.get(index);
                refreshSelectedFields();
                setActiveTab(activeTab);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, LIST_Y + 2, LIST_H - 4, 20)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInside(mouseX, mouseY, LEFT_X, LIST_Y, LEFT_W, LIST_H) && scroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, LEFT_X + 2, LIST_Y + 2, LEFT_W - 12, LIST_H - 4)) {
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

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampChance(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
