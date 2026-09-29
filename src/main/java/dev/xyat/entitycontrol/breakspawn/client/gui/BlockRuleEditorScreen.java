package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetwork;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.list.ItemGridDensity;
import dev.xyat.kineticcore.api.client.gui.widget.list.ItemGridItem;
import dev.xyat.kineticcore.api.client.gui.widget.list.ItemGridOutline;
import dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class BlockRuleEditorScreen extends KineticPage {
    private static final int LEFT_X = 12;
    private static final int LEFT_W = 224;
    private static final int SEARCH_Y = 40;
    private static final int LIST_Y = 67;
    private static final int LIST_H = 277;
    private static final int RIGHT_X = 244;
    private static final int RIGHT_W = 384;

    private final BreakSpawnConfig.ConfigRoot config;
    private final List<String> filteredBlocks = new ArrayList<>();
    private KineticItemGrid blockGrid;
    private int blockScrollOffset;
    private String searchQuery = "";
    private String selectedBlockId;
    private int activeTab;

    public BlockRuleEditorScreen(BreakSpawnConfig.ConfigRoot config) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.title"));
        this.config = config;
        refreshBlocks("");
        if (!config.blocks.isEmpty()) selectedBlockId = config.blocks.keySet().iterator().next();
        configureStandaloneDraft(() -> BreakSpawnConfig.copyForEdit(config),
                snapshot -> BreakSpawnConfig.restoreFromEditCopy(config, snapshot));
    }

    @Override
    protected void build(KineticUi ui) {
        ui.textField(LEFT_X, SEARCH_Y, 142)
                .label(KineticI18n.translatable("gui.entitycontrol.breakspawn.search.blocks"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.breakspawn.search.blocks.placeholder"))
                .maxLength(128)
                .value(searchQuery)
                .onChange(value -> {
                    searchQuery = value == null ? "" : value;
                    refreshBlocks(searchQuery);
                })
                .firstShownTextAsDefault().build();
        ui.button(LEFT_X + 147, SEARCH_Y, 77).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.add"))
                .onClick(this::openBlockSelector).build();
        ui.button(325, 12, 72).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.global.open"))
                .onClick(() -> openChild(new GlobalSettingsScreen(config))).build();
        var remove = ui.button(402, 12, 72).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.remove"))
                .onClick(this::removeSelectedBlock).build();
        remove.setEnabled(selectedRule() != null);
        ui.button(479, 12, 72).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.save"))
                .onClick(this::saveConfig).build();
        ui.button(556, 12, 72).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.back"))
                .onClick(this::navigateBack).build();

        blockGrid = ui.itemGrid(LEFT_X, LIST_Y, LEFT_W, LIST_H, ItemGridDensity.COMPACT, blockItems())
                .scrollOffset(blockScrollOffset).onClick(this::selectBlock).build();

        boolean hasRule = selectedRule() != null;
        var tab0 = ui.button(RIGHT_X, 40, 92).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.tab.probability"))
                .onClick(() -> switchTab(0)).build();
        var tab1 = ui.button(RIGHT_X + 97, 40, 92).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.tab.spawn"))
                .onClick(() -> switchTab(1)).build();
        var tab2 = ui.button(RIGHT_X + 194, 40, 92).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.tab.conditions"))
                .onClick(() -> switchTab(2)).build();
        tab0.setSelected(activeTab == 0);
        tab1.setSelected(activeTab == 1);
        tab2.setSelected(activeTab == 2);
        tab0.setEnabled(hasRule);
        tab1.setEnabled(hasRule);
        tab2.setEnabled(hasRule);
        var pool = ui.button(RIGHT_X + 291, 40, 92).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.pool"))
                .onClick(this::openPoolEditor).build();
        pool.setEnabled(hasRule);

        if (hasRule) {
            if (activeTab == 0) buildProbability(ui);
            else if (activeTab == 1) buildSpawn(ui);
            else buildConditions(ui);
        }
    }

    private void buildProbability(KineticUi ui) {
        BreakSpawnConfig.BlockRule rule = selectedRule();
        number(ui, 350, 88, 78, NumberType.DECIMAL, rule.baseChance * 100D, 0D, 100D,
                number -> rule.baseChance = number.doubleValue() / 100D);
        number(ui, 548, 88, 70, NumberType.DECIMAL, rule.chancePerFailure * 100D, 0D, 100D,
                number -> rule.chancePerFailure = number.doubleValue() / 100D);
        number(ui, 350, 120, 78, NumberType.DECIMAL, rule.maxChance * 100D, 0D, 100D,
                number -> rule.maxChance = number.doubleValue() / 100D);
        number(ui, 548, 120, 70, NumberType.INT, rule.resetAfterTicks, 0, null,
                number -> rule.resetAfterTicks = number.intValue());

        toggle(ui, 255, 158, 176, "gui.entitycontrol.breakspawn.blocks.enabled", rule.enabled, value -> rule.enabled = value);
        toggle(ui, 442, 158, 176, "gui.entitycontrol.breakspawn.blocks.stacking", rule.stackingEnabled, value -> rule.stackingEnabled = value);
        toggle(ui, 255, 183, 176, "gui.entitycontrol.breakspawn.blocks.reset_trigger", rule.resetOnTrigger, value -> rule.resetOnTrigger = value);
        toggle(ui, 442, 183, 176, "gui.entitycontrol.breakspawn.blocks.reset_different", rule.resetOnDifferentBlock, value -> rule.resetOnDifferentBlock = value);
    }

    private void buildSpawn(KineticUi ui) {
        BreakSpawnConfig.BlockRule rule = selectedRule();
        number(ui, 350, 88, 78, NumberType.INT, rule.minSpawnCount, 0, null, n -> rule.minSpawnCount = n.intValue());
        number(ui, 548, 88, 70, NumberType.INT, rule.maxSpawnCount, 0, null, n -> rule.maxSpawnCount = n.intValue());
        number(ui, 350, 120, 78, NumberType.INT, rule.minDistance, 0, null, n -> rule.minDistance = n.intValue());
        number(ui, 548, 120, 70, NumberType.INT, rule.horizontalRadius, 0, null, n -> rule.horizontalRadius = n.intValue());
        number(ui, 350, 152, 78, NumberType.INT, rule.verticalRadius, 0, null, n -> rule.verticalRadius = n.intValue());
        number(ui, 548, 152, 70, NumberType.INT, rule.maxSpawnAttempts, 1, null, n -> rule.maxSpawnAttempts = n.intValue());
    }

    private void buildConditions(KineticUi ui) {
        BreakSpawnConfig.BlockRule rule = selectedRule();
        ui.textField(350, 88, 268).maxLength(4096).value(rule.dimensions == null ? "" : rule.dimensions)
                .onChange(value -> rule.dimensions = value).firstShownTextAsDefault().build();
        ui.textField(350, 120, 268).maxLength(4096).value(rule.biomes == null ? "" : rule.biomes)
                .onChange(value -> rule.biomes = value).firstShownTextAsDefault().build();
        number(ui, 350, 152, 78, NumberType.INT, rule.minY, null, null, n -> rule.minY = n.intValue());
        number(ui, 548, 152, 70, NumberType.INT, rule.maxY, null, null, n -> rule.maxY = n.intValue());
        number(ui, 350, 184, 78, NumberType.INT, rule.minLight, 0, 15, n -> rule.minLight = n.intValue());
        number(ui, 548, 184, 70, NumberType.INT, rule.maxLight, 0, 15, n -> rule.maxLight = n.intValue());
    }

    private void number(KineticUi ui, int x, int y, int width, NumberType type, Number initial,
                        Number min, Number max, Consumer<Number> consumer) {
        ui.numberField(x, y, width, type)
                .allowNegative(min == null || min.doubleValue() < 0D)
                .range(min, max)
                .value(initial)
                .onChange(raw -> {
                    try {
                        double value = Double.parseDouble(raw.trim());
                        if (Double.isFinite(value)) consumer.accept(type == NumberType.INT ? (int) value : value);
                    } catch (RuntimeException ignored) {
                    }
                })
                .firstShownTextAsDefault().build();
    }

    private void toggle(KineticUi ui, int x, int y, int width, String key, boolean value, Consumer<Boolean> consumer) {
        ui.toggle(x, y, width)
                .value(value)
                .labels(toggleLabel(key, true), toggleLabel(key, false))
                .onChange(consumer)
                .build();
    }

    private net.minecraft.network.chat.Component toggleLabel(String key, boolean value) {
        return KineticI18n.translatable(key, KineticI18n.translatable(value
                ? "gui.entitycontrol.breakspawn.switch.on"
                : "gui.entitycontrol.breakspawn.switch.off"));
    }

    private void switchTab(int tab) {
        activeTab = Math.max(0, Math.min(2, tab));
        rebuild();
    }

    private void selectBlock(int index) {
        if (index < 0 || index >= filteredBlocks.size()) return;
        blockScrollOffset = blockGrid == null ? 0 : blockGrid.scrollOffset();
        selectedBlockId = filteredBlocks.get(index);
        rebuild();
    }

    private List<ItemGridItem> blockItems() {
        List<ItemGridItem> items = new ArrayList<>(filteredBlocks.size());
        for (String id : filteredBlocks) {
            Block block = blockById(id);
            ItemStack stack = block == null ? ItemStack.EMPTY : new ItemStack(block.asItem());
            BreakSpawnConfig.BlockRule rule = config.blocks.get(id);
            Component tooltip = block == null ? Component.literal(id)
                    : block.getName().copy().append(Component.literal(" (" + id + ")"));
            items.add(new ItemGridItem(stack, tooltip, true, id.equals(selectedBlockId), false,
                    rule != null && rule.enabled ? ItemGridOutline.SUCCESS : ItemGridOutline.NONE));
        }
        return items;
    }

    private void saveConfig() {
        BreakSpawnNetwork.saveConfig(BreakSpawnConfig.GSON.toJson(config));
    }

    public void handleSaveResult(boolean success) {
        if (success) commitDraft();
    }

    private void openBlockSelector() {
        KineticSelectors.openItemSelectorWithOptions(
                KineticSelectors.ItemSelectorOptions.itemsOnly(null, List.of(), stack -> stack.getItem() instanceof BlockItem),
                selection -> {
                    if (!selection.isItem() || !(selection.stack().getItem() instanceof BlockItem blockItem)) return;
                    ResourceLocation id = KineticRegistries.blocks().id(blockItem.getBlock());
                    if (id == null) return;
                    selectedBlockId = id.toString();
                    config.blocks.computeIfAbsent(selectedBlockId,
                            ignored -> BreakSpawnConfig.createBlockRuleFromDefaults(config.global));
                    refreshBlocks(searchQuery);
                    rebuild();
                }
        );
    }

    private void openPoolEditor() {
        BreakSpawnConfig.BlockRule rule = selectedRule();
        if (rule != null && selectedBlockId != null) {
            openChild(new BlockEntityPoolScreen(config, selectedBlockId, rule));
        }
    }

    private void removeSelectedBlock() {
        if (selectedBlockId == null) return;
        config.blocks.remove(selectedBlockId);
        selectedBlockId = config.blocks.keySet().stream().findFirst().orElse(null);
        refreshBlocks(searchQuery);
        rebuild();
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
            if (normalized.isEmpty() || KineticSearch.match(searchData, normalized)) filteredBlocks.add(id);
        }
        filteredBlocks.sort(Comparator.naturalOrder());
        if (blockGrid != null) blockGrid.setItems(blockItems());
    }

    private Block blockById(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        return location == null ? null : KineticRegistries.blocks().get(location);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 6, 6, 628, 348);
        KineticTheme.panelAlt(graphics, RIGHT_X, LIST_Y, RIGHT_W, LIST_H);
        graphics.text(title(), 14, 16, KineticTheme.current().text());
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.step_block"), 12, 30, KineticTheme.current().text());
        if (selectedRule() != null) {
            graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.step_entity"), 244, 30, KineticTheme.current().text());
            renderRightLabels(graphics);
        } else {
            graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.select_hint"),
                    RIGHT_X + RIGHT_W / 2, 178, KineticTheme.current().text(), false);
        }
    }

    private void renderRightLabels(KineticGraphics graphics) {
        if (activeTab == 0) {
            label(graphics, "gui.entitycontrol.breakspawn.blocks.base_chance", 255, 94);
            label(graphics, "gui.entitycontrol.breakspawn.blocks.stack_increase", 442, 94);
            label(graphics, "gui.entitycontrol.breakspawn.blocks.max_chance", 255, 126);
            label(graphics, "gui.entitycontrol.breakspawn.blocks.reset_ticks", 442, 126);
            graphics.wrappedText(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.stack_hint"), 255, 226, 355,
                    KineticTheme.current().mutedText());
        } else if (activeTab == 1) {
            label(graphics, "gui.entitycontrol.breakspawn.min_count", 255, 94);
            label(graphics, "gui.entitycontrol.breakspawn.max_count", 442, 94);
            label(graphics, "gui.entitycontrol.breakspawn.min_distance", 255, 126);
            label(graphics, "gui.entitycontrol.breakspawn.radius", 442, 126);
            label(graphics, "gui.entitycontrol.breakspawn.vertical_radius", 255, 158);
            label(graphics, "gui.entitycontrol.breakspawn.spawn_attempts", 442, 158);
            graphics.wrappedText(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.pool_hint"), 255, 214, 355,
                    KineticTheme.current().mutedText());
        } else {
            label(graphics, "gui.entitycontrol.breakspawn.dimensions", 255, 94);
            label(graphics, "gui.entitycontrol.breakspawn.biomes", 255, 126);
            label(graphics, "gui.entitycontrol.breakspawn.min_y", 255, 158);
            label(graphics, "gui.entitycontrol.breakspawn.max_y", 442, 158);
            label(graphics, "gui.entitycontrol.breakspawn.min_light", 255, 190);
            label(graphics, "gui.entitycontrol.breakspawn.max_light", 442, 190);
            graphics.wrappedText(KineticI18n.translatable("gui.entitycontrol.breakspawn.csv_hint"), 255, 228, 355,
                    KineticTheme.current().mutedText());
        }
    }

    private void label(KineticGraphics graphics, String key, int x, int y) {
        graphics.text(KineticI18n.translatable(key), x, y, KineticTheme.current().text());
    }

}
