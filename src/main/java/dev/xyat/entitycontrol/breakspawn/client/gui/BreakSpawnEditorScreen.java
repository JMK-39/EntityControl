package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetwork;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class BreakSpawnEditorScreen extends KineticScreen {
    private static final int PANEL_X = 6;
    private static final int PANEL_Y = 6;
    private static final int PANEL_W = 628;
    private static final int PANEL_H = 348;
    private static final int LEFT_X = 12;
    private static final int LEFT_W = 224;
    private static final int SEARCH_Y = 40;
    private static final int LIST_Y = 67;
    private static final int LIST_H = 244;
    private static final int ROW_H = 52;
    private static final int ROW_GAP = 2;
    private static final int RIGHT_X = 244;
    private static final int RIGHT_W = 384;
    private static final int RIGHT_Y = 67;
    private static final int RIGHT_H = 244;
    private static final int TAB_Y = 40;
    private static final int SCROLL_X = LEFT_X + LEFT_W - 8;
    private static final int SCROLL_W = 4;
    private static final int VISIBLE_ROWS = 4;

    private final Screen parent;
    private final BreakSpawnConfig.ConfigRoot localConfig;
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer();
    private final GridScrollController listScroll = new GridScrollController();
    private final List<String> filteredIds = new ArrayList<>();
    private final List<Button> globalWidgets = new ArrayList<>();
    private final List<Button> entityButtons = new ArrayList<>();
    private final List<EditBox> globalBoxes = new ArrayList<>();
    private final List<EditBox> entityBoxes = new ArrayList<>();
    private final List<EditBox> conditionBoxes = new ArrayList<>();
    private final List<Button> contextButtons = new ArrayList<>();

    private EditBox searchBox;
    private EditBox chanceBox;
    private EditBox minCountBox;
    private EditBox maxCountBox;
    private EditBox minDistanceBox;
    private EditBox radiusBox;
    private EditBox verticalRadiusBox;
    private EditBox attemptsBox;
    private EditBox cooldownBox;
    private EditBox weightBox;
    private EditBox minYBox;
    private EditBox maxYBox;
    private EditBox customNameBox;
    private EditBox dimensionsBox;
    private EditBox biomesBox;
    private EditBox allowedBlocksBox;
    private EditBox blockedBlocksBox;
    private EditBox minLightBox;
    private EditBox maxLightBox;
    private Button globalEnabledButton;
    private Button creativeButton;
    private Button spawnModeButton;
    private Button globalTabButton;
    private Button entityTabButton;
    private Button conditionsTabButton;
    private String selectedId;
    private int activeTab = 0;
    private int contextX;
    private int contextY;
    private boolean contextOpen;
    private List<Component> deferredTooltip;

    public BreakSpawnEditorScreen(Screen parent, String serverSnapshotJson) {
        super(Component.translatable("gui.entitycontrol.breakspawn.editor.title"));
        this.parent = parent;
        BreakSpawnConfig.ConfigRoot parsed = BreakSpawnConfig.parseConfigJson(serverSnapshotJson);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid break spawn snapshot");
        }
        this.localConfig = parsed;
        useCanvas(640f, 360f, 6);
        previewRenderer.setRotationSpeedPercent(100);
        refreshFiltered("");
        configureStandaloneDraft(
                () -> BreakSpawnConfig.copyForEdit(localConfig),
                snapshot -> {
                    BreakSpawnConfig.restoreFromEditCopy(localConfig, snapshot);
                    refreshFiltered(searchBox == null ? "" : searchBox.getValue());
                    refreshSelectionWidgets();
                }
        );
    }

    @Override
    protected void buildUi() {
        clearWidgetLists();
        searchBox = addRenderableWidget(new EditBox(
                font,
                LEFT_X,
                SEARCH_Y,
                142,
                20,
                Component.translatable("gui.entitycontrol.breakspawn.search.entities")
        ));
        searchBox.setMaxLength(128);
        searchBox.setResponder(this::refreshFiltered);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.manage_entities"),
                        ignored -> openEntitySelector())
                .bounds(LEFT_X + 147, SEARCH_Y, 77, 20)
                .build());

        globalTabButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.tab.global"),
                        ignored -> setActiveTab(0))
                .bounds(RIGHT_X, TAB_Y, 121, 20).build());
        entityTabButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.tab.entity"),
                        ignored -> setActiveTab(1))
                .bounds(RIGHT_X + 126, TAB_Y, 121, 20).build());
        conditionsTabButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.tab.conditions"),
                        ignored -> setActiveTab(2))
                .bounds(RIGHT_X + 252, TAB_Y, 121, 20).build());

        buildGlobalWidgets();
        buildEntityWidgets();
        buildConditionWidgets();
        buildContextButtons();

        Button saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.save"),
                        ignored -> saveConfig())
                .bounds(405, 322, 104, 22)
                .build());
        saveButton.setTooltip(Tooltip.create(Component.translatable("gui.entitycontrol.breakspawn.save.tooltip")));
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.back"),
                        ignored -> onClose())
                .bounds(514, 322, 104, 22)
                .build());

        refreshFiltered(searchBox.getValue());
        refreshSelectionWidgets();
        setActiveTab(activeTab);
    }

    private void clearWidgetLists() {
        globalWidgets.clear();
        entityButtons.clear();
        globalBoxes.clear();
        entityBoxes.clear();
        conditionBoxes.clear();
        contextButtons.clear();
    }

    private void buildGlobalWidgets() {
        chanceBox = createNumberBox(350, 88, 78, String.valueOf(localConfig.global.triggerChance * 100.0D), globalBoxes,
                value -> localConfig.global.triggerChance = clampDouble(value / 100.0D));
        minCountBox = createNumberBox(548, 88, 70, String.valueOf(localConfig.global.minSpawnCount), globalBoxes,
                value -> localConfig.global.minSpawnCount = Math.max(0, (int) value));
        maxCountBox = createNumberBox(350, 120, 78, String.valueOf(localConfig.global.maxSpawnCount), globalBoxes,
                value -> localConfig.global.maxSpawnCount = Math.max(0, (int) value));
        minDistanceBox = createNumberBox(548, 120, 70, String.valueOf(localConfig.global.minDistance), globalBoxes,
                value -> localConfig.global.minDistance = Math.max(0, (int) value));
        radiusBox = createNumberBox(350, 152, 78, String.valueOf(localConfig.global.horizontalRadius), globalBoxes,
                value -> localConfig.global.horizontalRadius = Math.max(0, (int) value));
        verticalRadiusBox = createNumberBox(548, 152, 70, String.valueOf(localConfig.global.verticalRadius), globalBoxes,
                value -> localConfig.global.verticalRadius = Math.max(0, (int) value));
        attemptsBox = createNumberBox(350, 184, 78, String.valueOf(localConfig.global.maxSpawnAttempts), globalBoxes,
                value -> localConfig.global.maxSpawnAttempts = Math.max(1, (int) value));
        cooldownBox = createNumberBox(548, 184, 70, String.valueOf(localConfig.global.playerCooldownTicks), globalBoxes,
                value -> localConfig.global.playerCooldownTicks = Math.max(0, (int) value));

        globalEnabledButton = addGlobalButton(255, 226, 176, () -> {
            localConfig.global.enabled = !localConfig.global.enabled;
            updateGlobalToggleLabels();
        });
        creativeButton = addGlobalButton(442, 226, 176, () -> {
            localConfig.global.creativeCanTrigger = !localConfig.global.creativeCanTrigger;
            updateGlobalToggleLabels();
        });
        Button blockRulesButton = addGlobalButton(255, 258, 363, this::openBlockRuleEditor);
        blockRulesButton.setMessage(Component.translatable("gui.entitycontrol.breakspawn.block_rules"));
        updateGlobalToggleLabels();
    }

    private void buildEntityWidgets() {
        weightBox = createNumberBox(350, 88, 78, "", entityBoxes,
                value -> selectedRule().ifPresent(rule -> rule.weight = Math.max(0, (int) value)));
        minYBox = createNumberBox(548, 88, 70, "", entityBoxes,
                value -> selectedRule().ifPresent(rule -> rule.minY = (int) value));
        maxYBox = createNumberBox(350, 120, 78, "", entityBoxes,
                value -> selectedRule().ifPresent(rule -> rule.maxY = (int) value));
        customNameBox = createTextBox(152, entityBoxes,
                value -> selectedRule().ifPresent(rule -> rule.customName = value));

        spawnModeButton = addEntityButton(255, 184, () -> selectedRule().ifPresent(rule -> {
            rule.spawnMode = nextSpawnMode(rule.spawnMode);
            updateSpawnModeLabel();
        }));
        addEntityButton(442, 184, this::openAttributeEditor)
                .setMessage(Component.translatable("gui.entitycontrol.breakspawn.attributes"));
        addEntityButton(255, 216, this::openEquipmentEditor)
                .setMessage(Component.translatable("gui.entitycontrol.breakspawn.equipment"));
        addEntityButton(442, 216, this::openEntityNbtEditor)
                .setMessage(Component.translatable("gui.entitycontrol.breakspawn.entity_nbt"));
        addEntityButton(255, 248, this::openEntitySelector)
                .setMessage(Component.translatable("gui.entitycontrol.breakspawn.manage_entities"));
        addEntityButton(442, 248, this::removeSelected)
                .setMessage(Component.translatable("gui.entitycontrol.breakspawn.remove"));
    }

    private void buildConditionWidgets() {
        dimensionsBox = createTextBox(88, conditionBoxes,
                value -> selectedRule().ifPresent(rule -> rule.dimensions = value));
        biomesBox = createTextBox(120, conditionBoxes,
                value -> selectedRule().ifPresent(rule -> rule.biomes = value));
        allowedBlocksBox = createTextBox(152, conditionBoxes,
                value -> selectedRule().ifPresent(rule -> rule.allowedBlocks = value));
        blockedBlocksBox = createTextBox(184, conditionBoxes,
                value -> selectedRule().ifPresent(rule -> rule.blockedBlocks = value));
        minLightBox = createNumberBox(350, 216, 78, "", conditionBoxes,
                value -> selectedRule().ifPresent(rule -> rule.minLight = clampInt((int) value, 0, 15)));
        maxLightBox = createNumberBox(548, 216, 70, "", conditionBoxes,
                value -> selectedRule().ifPresent(rule -> rule.maxLight = clampInt((int) value, 0, 15)));
    }

    private void buildContextButtons() {
        for (int i = 0; i < 7; i++) {
            final int index = i;
            HighZButton button = new HighZButton(0, 0, 150, 18, Component.empty(), ignored -> toggleContextValue(index), null, 300);
            button.visible = false;
            contextButtons.add(addRenderableWidget(button));
        }
    }

    private Button addGlobalButton(int x, int y, int width, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
                .bounds(x, y, width, 20).build());
        globalWidgets.add(button);
        return button;
    }

    private Button addEntityButton(int x, int y, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
                .bounds(x, y, 176, 20).build());
        entityButtons.add(button);
        return button;
    }

    private EditBox createNumberBox(
            int x,
            int y,
            int width,
            String initial,
            List<? super EditBox> group,
            java.util.function.DoubleConsumer consumer
    ) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.empty());
        box.setMaxLength(32);
        box.setValue(initial);
        box.setResponder(value -> {
            try {
                consumer.accept(Double.parseDouble(value.trim()));
            } catch (Exception ignored) {
            }
        });
        addRenderableWidget(box);
        group.add(box);
        return box;
    }

    private EditBox createTextBox(
            int y,
            List<? super EditBox> group,
            Consumer<String> consumer
    ) {
        EditBox box = new EditBox(font, 350, y, 268, 20, Component.empty());
        box.setMaxLength(4096);
        box.setValue("");
        box.setResponder(consumer);
        addRenderableWidget(box);
        group.add(box);
        return box;
    }

    private void setActiveTab(int tab) {
        activeTab = clampInt(tab, 0, 2);
        boolean hasEntity = selectedId != null && localConfig.entities.containsKey(selectedId);
        globalTabButton.active = activeTab != 0;
        entityTabButton.active = hasEntity && activeTab != 1;
        conditionsTabButton.active = hasEntity && activeTab != 2;
        setVisible(globalBoxes, activeTab == 0);
        for (Button button : globalWidgets) {
            button.visible = activeTab == 0;
        }
        setVisible(entityBoxes, activeTab == 1 && hasEntity);
        for (Button button : entityButtons) {
            button.visible = activeTab == 1 && hasEntity;
        }
        setVisible(conditionBoxes, activeTab == 2 && hasEntity);
    }

    private void setVisible(Collection<? extends net.minecraft.client.gui.components.AbstractWidget> widgets, boolean visible) {
        for (net.minecraft.client.gui.components.AbstractWidget widget : widgets) {
            widget.visible = visible;
        }
    }

    private void refreshSelectionWidgets() {
        BreakSpawnConfig.EntityRule rule = selectedId == null ? null : localConfig.entities.get(selectedId);
        boolean hasEntity = rule != null;
        if (hasEntity) {
            weightBox.setValue(String.valueOf(rule.weight));
            minYBox.setValue(String.valueOf(rule.minY));
            maxYBox.setValue(String.valueOf(rule.maxY));
            customNameBox.setValue(rule.customName == null ? "" : rule.customName);
            dimensionsBox.setValue(rule.dimensions == null ? "" : rule.dimensions);
            biomesBox.setValue(rule.biomes == null ? "" : rule.biomes);
            allowedBlocksBox.setValue(rule.allowedBlocks == null ? "" : rule.allowedBlocks);
            blockedBlocksBox.setValue(rule.blockedBlocks == null ? "" : rule.blockedBlocks);
            minLightBox.setValue(String.valueOf(rule.minLight));
            maxLightBox.setValue(String.valueOf(rule.maxLight));
            updateSpawnModeLabel();
        }
        if (!hasEntity && activeTab != 0) {
            activeTab = 0;
        }
        setActiveTab(activeTab);
        closeContextMenu();
    }

    private void updateGlobalToggleLabels() {
        globalEnabledButton.setMessage(toggleLabel("gui.entitycontrol.breakspawn.global_enabled", localConfig.global.enabled));
        creativeButton.setMessage(toggleLabel("gui.entitycontrol.breakspawn.creative_trigger", localConfig.global.creativeCanTrigger));
    }

    private void updateSpawnModeLabel() {
        selectedRule().ifPresent(rule -> spawnModeButton.setMessage(Component.translatable(
                "gui.entitycontrol.breakspawn.spawn_mode",
                Component.translatable("gui.entitycontrol.breakspawn.spawn_mode." + rule.spawnMode.toLowerCase(Locale.ROOT))
        )));
    }

    private Component toggleLabel(String key, boolean value) {
        return Component.translatable(key, Component.translatable(value
                ? "gui.entitycontrol.breakspawn.switch.on"
                : "gui.entitycontrol.breakspawn.switch.off"));
    }

    private java.util.Optional<BreakSpawnConfig.EntityRule> selectedRule() {
        return java.util.Optional.ofNullable(selectedId == null ? null : localConfig.entities.get(selectedId));
    }

    private void refreshFiltered(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredIds.clear();
        for (String id : localConfig.entities.keySet()) {
            EntityType<?> type = entityType(id);
            String name = type == null ? id : type.getDescription().getString();
            String searchData = (id + " " + name + " " + KineticSearch.pinyin(name)).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || KineticSearch.match(searchData, normalized)) {
                filteredIds.add(id);
            }
        }
        filteredIds.sort(Comparator.naturalOrder());
        listScroll.update(filteredIds.size(), VISIBLE_ROWS);
        if (selectedId != null && !localConfig.entities.containsKey(selectedId)) {
            selectedId = null;
        }
    }

    private void openBlockRuleEditor() {
        syncFieldValues();
        Minecraft.getInstance().setScreen(new BlockRuleEditorScreen(this, localConfig));
    }

    private void openEntitySelector() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new EntitySelectorScreen(
                this,
                Component.translatable("gui.entitycontrol.breakspawn.entity_selector.title"),
                localConfig.entities.keySet(),
                selected -> {
                    List<String> valid = new ArrayList<>();
                    for (String id : selected) {
                        if (isLivingEntity(id)) {
                            valid.add(id);
                        }
                    }
                    List<String> removed = new ArrayList<>();
                    for (String id : new ArrayList<>(localConfig.entities.keySet())) {
                        if (!valid.contains(id)) {
                            removed.add(id);
                            localConfig.entities.remove(id);
                        }
                    }
                    for (BreakSpawnConfig.BlockRule blockRule : localConfig.blocks.values()) {
                        for (String id : removed) {
                            blockRule.entityWeights.remove(id);
                        }
                    }
                    for (String id : valid) {
                        localConfig.entities.computeIfAbsent(id, ignored -> new BreakSpawnConfig.EntityRule());
                    }
                    if (selectedId != null && !localConfig.entities.containsKey(selectedId)) {
                        selectedId = null;
                    }
                    refreshFiltered(searchBox == null ? "" : searchBox.getValue());
                    refreshSelectionWidgets();
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

    private void openAttributeEditor() {
        if (selectedId == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new AttributeRangeScreen(this, selectedId, localConfig.entities.get(selectedId)));
    }

    private void openEquipmentEditor() {
        if (selectedId == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new EquipmentEditorScreen(this, selectedId, localConfig.entities.get(selectedId)));
    }

    private void openEntityNbtEditor() {
        selectedRule().ifPresent(rule -> Minecraft.getInstance().setScreen(new NbtEditorScreen(
                rule.entityNbt == null ? "" : rule.entityNbt,
                value -> rule.entityNbt = value == null ? "" : value,
                this
        )));
    }

    private void removeSelected() {
        if (selectedId == null) {
            return;
        }
        String removedId = selectedId;
        localConfig.entities.remove(removedId);
        for (BreakSpawnConfig.BlockRule blockRule : localConfig.blocks.values()) {
            blockRule.entityWeights.remove(removedId);
        }
        selectedId = null;
        refreshFiltered(searchBox.getValue());
        refreshSelectionWidgets();
    }

    private String nextSpawnMode(String current) {
        String value = current == null ? "AUTO" : current;
        return switch (value) {
            case "AUTO" -> "SURFACE";
            case "SURFACE" -> "WATER";
            case "WATER" -> "AIR";
            case "AIR" -> "ANY";
            default -> "AUTO";
        };
    }

    private void saveConfig() {
        syncFieldValues();
        BreakSpawnNetwork.CHANNEL.sendToServer(new BreakSpawnNetwork.SaveConfigPacket(BreakSpawnConfig.GSON.toJson(localConfig)));
        commitDraft();
    }

    private void syncFieldValues() {
        applyDouble(chanceBox, value -> localConfig.global.triggerChance = clampDouble(value / 100.0D));
        applyDouble(minCountBox, value -> localConfig.global.minSpawnCount = Math.max(0, (int) value));
        applyDouble(maxCountBox, value -> localConfig.global.maxSpawnCount = Math.max(0, (int) value));
        applyDouble(minDistanceBox, value -> localConfig.global.minDistance = Math.max(0, (int) value));
        applyDouble(radiusBox, value -> localConfig.global.horizontalRadius = Math.max(0, (int) value));
        applyDouble(verticalRadiusBox, value -> localConfig.global.verticalRadius = Math.max(0, (int) value));
        applyDouble(attemptsBox, value -> localConfig.global.maxSpawnAttempts = Math.max(1, (int) value));
        applyDouble(cooldownBox, value -> localConfig.global.playerCooldownTicks = Math.max(0, (int) value));
        selectedRule().ifPresent(rule -> {
            applyDouble(weightBox, value -> rule.weight = Math.max(0, (int) value));
            applyDouble(minYBox, value -> rule.minY = (int) value);
            applyDouble(maxYBox, value -> rule.maxY = (int) value);
            applyDouble(minLightBox, value -> rule.minLight = clampInt((int) value, 0, 15));
            applyDouble(maxLightBox, value -> rule.maxLight = clampInt((int) value, 0, 15));
            rule.customName = customNameBox.getValue();
            rule.dimensions = dimensionsBox.getValue();
            rule.biomes = biomesBox.getValue();
            rule.allowedBlocks = allowedBlocksBox.getValue();
            rule.blockedBlocks = blockedBlocksBox.getValue();
        });
    }

    private void applyDouble(EditBox box, java.util.function.DoubleConsumer consumer) {
        if (box == null) {
            return;
        }
        try {
            consumer.accept(Double.parseDouble(box.getValue().trim()));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        graphics.fill(0, 0, canvasWidth, canvasHeight, 0xD9000000);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 0xD91A1E26, 0xFF506070);
        GuiTheme.panel(graphics, LEFT_X, LIST_Y, LEFT_W, LIST_H, 0xB010141A, 0xFF43515F);
        GuiTheme.panel(graphics, RIGHT_X, RIGHT_Y, RIGHT_W, RIGHT_H, 0xB010141A, 0xFF43515F);
        graphics.drawString(font, title, 14, 16, 0xFFFFFFFF, false);
        renderEntityList(graphics, mouseX, mouseY, partialTick);
        renderRightPanel(graphics, mouseX, mouseY);
        if (contextOpen) {
            renderContextCard(graphics);
        }
    }

    private void renderEntityList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (filteredIds.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.empty_entities"), LEFT_X + LEFT_W / 2, LIST_Y + 110, 0xFFFFFFFF);
        }
        int start = listScroll.smoothIndexOffset();
        int shift = listScroll.visualShift(ROW_H + ROW_GAP);
        int end = Math.min(filteredIds.size(), start + VISIBLE_ROWS + 1);
        int rowIndex = 0;
                enableCanvasScissor(graphics, LEFT_X + 2, LIST_Y + 2, LEFT_X + LEFT_W - 10, LIST_Y + LIST_H - 2);
        try {
for (int index = start; index < end; index++) {
            int rowY = LIST_Y + rowIndex * (ROW_H + ROW_GAP) + 2 - shift;
            if (rowY + ROW_H > LIST_Y + LIST_H - 2) {
                break;
            }
            String id = filteredIds.get(index);
            BreakSpawnConfig.EntityRule rule = localConfig.entities.get(id);
            boolean hovered = isInside(mouseX, mouseY, LEFT_X + 2, rowY, LEFT_W - 12, ROW_H);
            boolean selected = id.equals(selectedId);
            int outline = hovered ? 0xFFAAAAAA : rule != null && rule.enabled ? 0xFF00C853 : 0xFF59636E;
            int background = selected ? 0xB0283440 : 0xA0182028;
            GuiTheme.panel(graphics, LEFT_X + 2, rowY, LEFT_W - 12, ROW_H, background, outline);
            EntityPreviewRenderer.drawCheckerboard(graphics, LEFT_X + 5, rowY + 4, 44, 44);
            previewRenderer.render(
                    graphics,
                    id,
                    "breakspawn:" + id,
                    LEFT_X + 5,
                    rowY + 4,
                    44,
                    44,
                    canvasScale,
                    canvasX,
                    canvasY,
                    hovered
            );
            EntityType<?> type = entityType(id);
            String name = type == null ? id : type.getDescription().getString();
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, name, 150)), LEFT_X + 55, rowY + 9, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, id, 150)), LEFT_X + 55, rowY + 26, 0xFFB8C8D8, false);
            if (rule != null) {
                Component weight = Component.translatable("gui.entitycontrol.breakspawn.entity_default_weight_short", rule.weight);
                graphics.drawString(font, weight, LEFT_X + 55, rowY + 39, 0xFFFFFFFF, false);
            }
            rowIndex++;
        }
        } finally {
            graphics.disableScissor();
        }
        GuiTheme.scrollbar(listScroll, graphics, mouseX, mouseY, SCROLL_X, LIST_Y + 2, SCROLL_W, LIST_H - 4, 24);
    }


    private double weightPercent(String id) {
        long total = 0L;
        int value = 0;
        for (Map.Entry<String, BreakSpawnConfig.EntityRule> entry : localConfig.entities.entrySet()) {
            BreakSpawnConfig.EntityRule rule = entry.getValue();
            if (rule == null || !rule.enabled || rule.weight <= 0) {
                continue;
            }
            total += rule.weight;
            if (entry.getKey().equals(id)) {
                value = rule.weight;
            }
        }
        return total <= 0L ? 0.0D : value * 100.0D / total;
    }

    private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeTab == 0) {
            drawLabel(graphics, "gui.entitycontrol.breakspawn.default_trigger_chance", 255, 94);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.default_min_count", 442, 94);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.default_max_count", 255, 126);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.default_min_distance", 442, 126);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.default_radius", 255, 158);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.default_vertical_radius", 442, 158);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.default_spawn_attempts", 255, 190);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.cooldown", 442, 190);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.natural_only_notice"), 255, 288, 0xFFFFFFFF, false);
            return;
        }
        if (selectedId == null || !localConfig.entities.containsKey(selectedId)) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.no_entity"), RIGHT_X + RIGHT_W / 2, RIGHT_Y + 110, 0xFFFFFFFF);
            return;
        }
        if (activeTab == 1) {
            drawLabel(graphics, "gui.entitycontrol.breakspawn.weight", 255, 94);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.min_y", 442, 94);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.max_y", 255, 126);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.custom_name", 255, 158);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.right_click_hint"), 255, 282, 0xFFFFFFFF, false);
        } else {
            drawLabel(graphics, "gui.entitycontrol.breakspawn.dimensions", 255, 94);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.biomes", 255, 126);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.allowed_blocks", 255, 158);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.blocked_blocks", 255, 190);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.min_light", 255, 222);
            drawLabel(graphics, "gui.entitycontrol.breakspawn.max_light", 442, 222);
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.csv_hint"), 255, 260, 0xFFFFFFFF, false);
        }
    }

    private void drawLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y, 0xFFFFFFFF, false);
    }

    private void renderContextCard(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        GuiTheme.panel(graphics, contextX, contextY, 162, 196, 0xF018202A, 0xFFAAAAAA);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.quick_switches"), contextX + 8, contextY + 7, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            String placeholder = font.plainSubstrByWidth(
                    Component.translatable("gui.entitycontrol.breakspawn.search.entities.placeholder").getString(),
                    Math.max(0, searchBox.getWidth() - 10)
            );
            graphics.drawString(font, placeholder, searchBox.getX() + 5, searchBox.getY() + 6, 0xFFB8C8D8, false);
        }
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (deferredTooltip != null) {
            GuiOverlay.requestTooltip(deferredTooltip, mouseX, mouseY);
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (contextOpen && !isInside(mouseX, mouseY, contextX, contextY, 162, 196)) {
            closeContextMenu();
        }
        boolean widgetHandled = super.canvasMouseClicked(mouseX, mouseY, button);
        if (widgetHandled) {
            return true;
        }
        if (button == 0 && listScroll.beginDrag(mouseX, mouseY, SCROLL_X, LIST_Y + 2, SCROLL_W, LIST_H - 4, 24, 2)) {
            return true;
        }
        int index = rowIndexAt(mouseX, mouseY);
        if (index >= 0 && index < filteredIds.size()) {
            String id = filteredIds.get(index);
            if (button == 0) {
                selectedId = id;
                refreshSelectionWidgets();
                return true;
            }
            if (button == 1) {
                selectedId = id;
                refreshSelectionWidgets();
                openContextMenu(mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return listScroll.drag(mouseY, LIST_Y + 2, LIST_H - 4, 24)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return listScroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        int index = rowIndexAt(mouseX, mouseY);
        if (net.minecraft.client.gui.screens.Screen.hasControlDown() && index >= 0 && index < filteredIds.size()) {
            String id = filteredIds.get(index);
            previewRenderer.adjustZoom("breakspawn:" + id, delta);
            return true;
        }
        if (isInside(mouseX, mouseY, LEFT_X, LIST_Y, LEFT_W, LIST_H) && listScroll.scroll(delta)) {
            closeContextMenu();
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, LEFT_X + 2, LIST_Y + 2, LEFT_W - 12, LIST_H - 4)) {
            return -1;
        }
        int stride = ROW_H + ROW_GAP;
        int localY = (int) mouseY - (LIST_Y + 2) + listScroll.visualShift(stride);
        int row = localY / stride;
        if (row < 0 || row >= VISIBLE_ROWS || localY % stride >= ROW_H) {
            return -1;
        }
        return listScroll.smoothIndexOffset() + row;
    }

    private void openContextMenu(double mouseX, double mouseY) {
        contextX = clampInt((int) mouseX + 6, 8, 640 - 170);
        contextY = clampInt((int) mouseY + 6, 8, 360 - 204);
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
        selectedRule().ifPresent(rule -> {
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
        });
    }

    private void updateContextLabels() {
        selectedRule().ifPresent(rule -> {
            boolean[] values = {
                    rule.enabled,
                    rule.persistent,
                    rule.silent,
                    rule.glowing,
                    rule.noAi,
                    rule.invulnerable,
                    rule.customNameVisible
            };
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
                contextButtons.get(i).setMessage(toggleLabel(keys[i], values[i]));
            }
        });
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
