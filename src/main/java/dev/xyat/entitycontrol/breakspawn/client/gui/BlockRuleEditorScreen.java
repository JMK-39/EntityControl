package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetwork;
import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.ItemSlotGrid;
import dev.xyat.kineticcore.api.client.gui.input.MouseButton;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自然方块破坏遭遇。顶栏：返回 / 方块操作 ▾ / 全局默认 / 保存 / 更多 ▾。
 * 左侧为已配置的方块（物品格），右侧同时显示“触发概率”“触发条件”“生成范围”和刷怪池入口，不再分页签。
 */
public final class BlockRuleEditorScreen extends EcPage {
    private static final int LIST_WIDTH = 7 * (ItemSlotGrid.SLOT + ItemSlotGrid.GAP) + 8 + PAD * 2;
    private static final int LABEL_WIDTH = 96;
    private static final int PAIR_WIDTH = 50;

    private final BreakSpawnConfig.ConfigRoot config;
    private final ItemSlotGrid blocks = new ItemSlotGrid();
    private final List<String> visibleBlocks = new ArrayList<>();
    private String query = "";
    private String selectedBlockId;
    private String savedJson;
    private KineticLayout.Rect helpRect;
    private Component helpText;

    public BlockRuleEditorScreen(BreakSpawnConfig.ConfigRoot config) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.title"));
        this.config = config;
        this.savedJson = BreakSpawnConfig.GSON.toJson(config);
        if (!config.blocks.isEmpty()) selectedBlockId = config.blocks.keySet().iterator().next();
        configureStandaloneDraft(() -> BreakSpawnConfig.copyForEdit(config),
                snapshot -> {
                    BreakSpawnConfig.restoreFromEditCopy(config, snapshot);
                    if (isAttached()) rebuild();
                });
    }

    static MutableComponent tr(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.breakspawn.ui." + key, args);
    }

    static MutableComponent tip(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.breakspawn.ui." + key + ".tooltip", args);
    }

    private boolean dirty() {
        return !BreakSpawnConfig.GSON.toJson(config).equals(savedJson);
    }

    private void changed() {
        KineticButton save = headerButton("save");
        if (save != null) save.setEnabled(dirty());
    }

    private BreakSpawnConfig.BlockRule selectedRule() {
        return selectedBlockId == null ? null : config.blocks.get(selectedBlockId);
    }

    private static Block block(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        return location == null ? null : KineticRegistries.blocks().get(location);
    }

    static Component blockName(String id) {
        Block block = block(id);
        return block == null ? Component.literal(id) : block.getName();
    }

    // ------------------------------------------------------------------ 外框

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(
                HeaderAction.menu(tr("block_actions"), tip("block_actions"), this::blockMenu),
                HeaderAction.button("global", tr("global"), tip("global"), () -> openChild(new GlobalSettingsScreen(config, this::changed))),
                HeaderAction.button("save", KineticI18n.translatable("gui.entitycontrol.breakspawn.save"),
                        KineticI18n.translatable("gui.entitycontrol.breakspawn.save.tooltip"), this::save).enabled(dirty()),
                HeaderAction.more(() -> List.of(dirty()
                        ? KineticOverlays.MenuItem.danger(tr("revert"), tip("revert"), this::revert)
                        : KineticOverlays.MenuItem.disabled(tr("revert"), tip("revert"))))
        );
    }

    private List<KineticOverlays.MenuItem> blockMenu() {
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        items.add(KineticOverlays.MenuItem.action(tr("add_block"), tip("add_block"), this::addBlock));
        items.add(selectedRule() != null
                ? KineticOverlays.MenuItem.danger(tr("remove_block"), tip("remove_block"), this::removeBlock)
                : KineticOverlays.MenuItem.disabled(tr("remove_block"), tip("select_block")));
        return items;
    }

    private void revert() {
        BreakSpawnConfig.ConfigRoot saved = BreakSpawnConfig.GSON.fromJson(savedJson, BreakSpawnConfig.ConfigRoot.class);
        if (saved != null) BreakSpawnConfig.restoreFromEditCopy(config, BreakSpawnConfig.copyForEdit(saved));
        if (selectedBlockId != null && !config.blocks.containsKey(selectedBlockId)) selectedBlockId = null;
        rebuild();
    }

    @Override
    protected boolean onBack() {
        if (!dirty()) return false;
        openDialog(tr("unsaved.title"), tr("unsaved.message"), tr("unsaved.discard"),
                KineticI18n.translatable("gui.entitycontrol.common.cancel"), () -> {
                    revert();
                    commitDraft();
                    navigateBack();
                }, () -> {
                });
        return true;
    }

    private void save() {
        BreakSpawnNetwork.saveConfig(BreakSpawnConfig.GSON.toJson(config));
    }

    public void handleSaveResult(boolean success) {
        if (!success) return;
        savedJson = BreakSpawnConfig.GSON.toJson(config);
        commitDraft();
        changed();
    }

    private void addBlock() {
        KineticSelectors.openItemSelectorWithOptions(
                KineticSelectors.ItemSelectorOptions.itemsOnly(null, List.of(), stack -> stack.getItem() instanceof BlockItem),
                selection -> {
                    if (!selection.isItem() || !(selection.stack().getItem() instanceof BlockItem blockItem)) return;
                    ResourceLocation id = KineticRegistries.blocks().id(blockItem.getBlock());
                    if (id == null) return;
                    selectedBlockId = id.toString();
                    config.blocks.computeIfAbsent(selectedBlockId, ignored -> BreakSpawnConfig.createBlockRuleFromDefaults(config.global));
                    changed();
                });
    }

    private void removeBlock() {
        if (selectedBlockId == null) return;
        config.blocks.remove(selectedBlockId);
        selectedBlockId = config.blocks.keySet().stream().findFirst().orElse(null);
        changed();
        rebuild();
    }

    // ------------------------------------------------------------------ 内容

    private List<ItemStack> blockStacks() {
        visibleBlocks.clear();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        for (String id : config.blocks.keySet()) {
            String name = blockName(id).getString();
            if (!normalized.isEmpty() && !KineticSearch.match((id + " " + name + " " + KineticSearch.pinyin(name))
                    .toLowerCase(Locale.ROOT), normalized)) continue;
            visibleBlocks.add(id);
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (String id : visibleBlocks) {
            Block block = block(id);
            stacks.add(block == null ? ItemStack.EMPTY : new ItemStack(block.asItem()));
        }
        return stacks;
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        helpRect = null;
        helpText = null;
        KineticLayout.Split columns = KineticLayout.splitHorizontal(body, LIST_WIDTH, GAP);
        KineticLayout.Rect left = section(columns.first(), tr("blocks", config.blocks.size()));
        KineticLayout.Split rows = takeRow(left);
        textInput(ui, rows.first(), query, KineticI18n.translatable("gui.entitycontrol.breakspawn.search.blocks.placeholder"),
                tip("search"), value -> {
                    query = value;
                    blocks.update(blockStacks());
                });
        blocks.layout(this, rows.second(), blockStacks());

        BreakSpawnConfig.BlockRule rule = selectedRule();
        if (rule == null) {
            helpRect = section(columns.second(), KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.select_hint"));
            helpText = tr("help.blocks");
            return;
        }
        KineticLayout.Split halves = halves(columns.second());
        KineticLayout.Split leftCol = takeTop(halves.first(), PAD * 2 + 10 + GAP + ROW * 5);
        KineticLayout.Split rightCol = takeTop(halves.second(), PAD * 2 + 10 + GAP + ROW * 6);

        Form chance = form(section(leftCol.first(), tr("section.chance", blockName(selectedBlockId))), LABEL_WIDTH);
        decimalField(ui, number(chance.row(tr("base_chance"), tip("base_chance"))), rule.baseChance * 100D, 0D, 100D,
                tip("base_chance"), value -> {
                    if (value == null) return;
                    rule.baseChance = value / 100D;
                    changed();
                });
        decimalField(ui, number(chance.row(tr("stack_increase"), tip("stack_increase"))), rule.chancePerFailure * 100D, 0D, 100D,
                tip("stack_increase"), value -> {
                    if (value == null) return;
                    rule.chancePerFailure = value / 100D;
                    changed();
                });
        decimalField(ui, number(chance.row(tr("max_chance"), tip("max_chance"))), rule.maxChance * 100D, 0D, 100D,
                tip("max_chance"), value -> {
                    if (value == null) return;
                    rule.maxChance = value / 100D;
                    changed();
                });
        intField(ui, number(chance.row(tr("reset_ticks"), tip("reset_ticks"))), rule.resetAfterTicks, 0, 1_000_000,
                tip("reset_ticks"), value -> {
                    if (value == null) return;
                    rule.resetAfterTicks = value;
                    changed();
                });
        toggleMenu(ui, compact(chance.row(tr("switches"), tip("switches"))), tr("switches.button"), tip("switches"), List.of(
                new ToggleOption(tr("switch.enabled"), tip("switch.enabled"), () -> rule.enabled, value -> rule.enabled = value),
                new ToggleOption(tr("switch.stacking"), tip("switch.stacking"), () -> rule.stackingEnabled, value -> rule.stackingEnabled = value),
                new ToggleOption(tr("switch.reset_trigger"), tip("switch.reset_trigger"), () -> rule.resetOnTrigger,
                        value -> rule.resetOnTrigger = value),
                new ToggleOption(tr("switch.reset_different"), tip("switch.reset_different"), () -> rule.resetOnDifferentBlock,
                        value -> rule.resetOnDifferentBlock = value)
        ), true, this::changed);

        Form conditions = form(section(leftCol.second(), tr("section.conditions")), LABEL_WIDTH);
        textInput(ui, csv(conditions.row(tr("dimensions"), tip("dimensions"))), rule.dimensions,
                KineticI18n.translatable("gui.entitycontrol.breakspawn.csv_hint"), tip("dimensions"), value -> {
                    rule.dimensions = value.trim();
                    changed();
                });
        textInput(ui, csv(conditions.row(tr("biomes"), tip("biomes"))), rule.biomes,
                KineticI18n.translatable("gui.entitycontrol.breakspawn.csv_hint"), tip("biomes"), value -> {
                    rule.biomes = value.trim();
                    changed();
                });
        intPair(ui, conditions.row(tr("y_range"), tip("y_range")), rule.minY, rule.maxY, -2048, 4096,
                value -> rule.minY = value, value -> rule.maxY = value);
        intPair(ui, conditions.row(tr("light"), tip("light")), rule.minLight, rule.maxLight, 0, 15,
                value -> rule.minLight = value, value -> rule.maxLight = value);

        Form spawn = form(section(rightCol.first(), tr("section.spawn")), LABEL_WIDTH);
        intPair(ui, spawn.row(tr("count"), tip("count")), rule.minSpawnCount, rule.maxSpawnCount, 0, 1024,
                value -> rule.minSpawnCount = value, value -> rule.maxSpawnCount = value);
        intRow(ui, spawn, "min_distance", rule.minDistance, 0, value -> rule.minDistance = value);
        intRow(ui, spawn, "radius", rule.horizontalRadius, 0, value -> rule.horizontalRadius = value);
        intRow(ui, spawn, "vertical_radius", rule.verticalRadius, 0, value -> rule.verticalRadius = value);
        intRow(ui, spawn, "attempts", rule.maxSpawnAttempts, 1, value -> rule.maxSpawnAttempts = value);
        actionButton(ui, compact(spawn.row(tr("pool"), tip("pool"))), tr("pool.button", rule.entityWeights.size()), tip("pool"), true,
                () -> openChild(new BlockEntityPoolScreen(config, selectedBlockId, rule, this::changed)));
        helpRect = rightCol.second();
        helpText = KineticI18n.translatable("gui.entitycontrol.breakspawn.blocks.stack_hint");
    }

    private static KineticLayout.Rect csv(KineticLayout.Rect row) {
        return new KineticLayout.Rect(row.x(), row.y(), Math.min(row.width(), 150), row.height());
    }

    private void intRow(KineticUi ui, Form form, String key, int value, int min, java.util.function.IntConsumer setter) {
        intField(ui, number(form.row(tr(key), tip(key))), value, min, 1024, tip(key), changed -> {
            if (changed == null) return;
            setter.accept(changed);
            changed();
        });
    }

    private void intPair(KineticUi ui, KineticLayout.Rect row, int minValue, int maxValue, int min, int max,
                         java.util.function.IntConsumer minSetter, java.util.function.IntConsumer maxSetter) {
        intField(ui, new KineticLayout.Rect(row.x(), row.y(), PAIR_WIDTH, H), minValue, min, max, tr("pair.min"), value -> {
            if (value == null) return;
            minSetter.accept(value);
            changed();
        });
        intField(ui, new KineticLayout.Rect(row.x() + PAIR_WIDTH + GAP, row.y(), PAIR_WIDTH, H), maxValue, min, max, tr("pair.max"),
                value -> {
                    if (value == null) return;
                    maxSetter.accept(value);
                    changed();
                });
    }

    // ------------------------------------------------------------------ 绘制与交互

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int selected = selectedBlockId == null ? -1 : visibleBlocks.indexOf(selectedBlockId);
        // 红框：规则关闭、刷怪池为空或方块不存在；绿框：规则启用且可以生成。
        blocks.render(graphics, mouseX, mouseY, selected, index -> {
            BreakSpawnConfig.BlockRule rule = config.blocks.get(visibleBlocks.get(index));
            return rule == null || block(visibleBlocks.get(index)) == null || !rule.enabled || rule.entityWeights.isEmpty();
        }, index -> {
            BreakSpawnConfig.BlockRule rule = config.blocks.get(visibleBlocks.get(index));
            return rule != null && rule.enabled && !rule.entityWeights.isEmpty();
        });
        if (helpRect != null && helpText != null) {
            graphics.wrappedText(helpText, helpRect.x(), helpRect.y() + GAP, helpRect.width(), KineticTheme.current().text());
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        int index = blocks.indexAt(mouseX, mouseY);
        if (index < 0 || index >= visibleBlocks.size()) return false;
        String id = visibleBlocks.get(index);
        BreakSpawnConfig.BlockRule rule = config.blocks.get(id);
        List<Component> lines = new ArrayList<>();
        lines.add(blockName(id));
        lines.add(Component.literal(id));
        if (rule != null) {
            lines.add(tr("card.chance", String.format(Locale.ROOT, "%.1f", rule.baseChance * 100D)));
            lines.add(tr("card.pool", rule.entityWeights.size()));
            if (!rule.enabled) lines.add(tr("card.disabled"));
            else if (rule.entityWeights.isEmpty()) lines.add(tr("card.empty_pool"));
        }
        lines.add(tr("card.hint"));
        tooltipLines(lines);
        return true;
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        int index = blocks.indexAt(input.x(), input.y());
        if (index < 0 || index >= visibleBlocks.size()) return false;
        String id = visibleBlocks.get(index);
        if (!id.equals(selectedBlockId)) {
            selectedBlockId = id;
            clearFocus();
            rebuild();
        }
        if (input.button() == MouseButton.RIGHT) openMenu(input.x(), input.y(), blockMenu());
        return true;
    }
}
