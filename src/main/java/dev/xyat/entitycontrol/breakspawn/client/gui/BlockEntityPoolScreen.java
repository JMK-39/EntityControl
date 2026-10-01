package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.EntityCardGrid;
import dev.xyat.kineticcore.api.client.gui.input.MouseButton;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.blockName;
import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tip;
import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tr;

/**
 * 某个方块的刷怪池：左侧 3D 卡片，右侧为选中生物在此方块的权重，以及它的生成规则（模式、条件、开关、属性、装备、NBT）。
 * 生物规则按生物保存，所有方块共用；修改直接进入编辑器草稿，在主界面保存。
 */
public final class BlockEntityPoolScreen extends EcPage {
    private static final int CARD = 40;
    private static final int LIST_WIDTH = EntityCardGrid.widthFor(4, CARD) + PAD * 2;
    private static final int LABEL_WIDTH = 104;
    private static final int PAIR_WIDTH = 50;
    private static final List<String> MODES = List.of("AUTO", "SURFACE", "WATER", "AIR", "ANY");

    private final BreakSpawnConfig.ConfigRoot config;
    private final String blockId;
    private final BreakSpawnConfig.BlockRule blockRule;
    private final Runnable onChange;
    private final EntityCardGrid cards = new EntityCardGrid(CARD);
    private final ScrollForm form = new ScrollForm();
    private String query = "";
    private String selectedEntityId;
    private KineticLayout.Rect helpRect;

    public BlockEntityPoolScreen(BreakSpawnConfig.ConfigRoot config, String blockId, BreakSpawnConfig.BlockRule blockRule,
                                 Runnable onChange) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.title"));
        this.config = config;
        this.blockId = blockId;
        this.blockRule = blockRule;
        this.onChange = onChange;
        if (!blockRule.entityWeights.isEmpty()) selectedEntityId = blockRule.entityWeights.keySet().iterator().next();
    }

    @Override
    protected Component headerTitle() {
        return tr("pool.title", blockName(blockId));
    }

    private static Component entityName(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        return type == null ? Component.literal(id) : type.getDescription();
    }

    private BreakSpawnConfig.EntityRule entityRule(String id) {
        return config.entities.computeIfAbsent(id, ignored -> new BreakSpawnConfig.EntityRule());
    }

    private double share(String id) {
        long total = 0L;
        int current = 0;
        for (Map.Entry<String, Integer> entry : blockRule.entityWeights.entrySet()) {
            int value = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            total += value;
            if (entry.getKey().equals(id)) current = value;
        }
        return total <= 0L ? 0D : current * 100D / total;
    }

    // ------------------------------------------------------------------ 外框

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(
                HeaderAction.button("manage", tr("pool.manage"), tip("pool.manage"), this::manageEntities),
                HeaderAction.more(() -> List.of(selectedEntityId != null
                        ? KineticOverlays.MenuItem.danger(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.remove"),
                        tip("pool.remove"), this::removeSelected)
                        : KineticOverlays.MenuItem.disabled(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.remove"),
                        tip("pool.select_first"))))
        );
    }

    private void manageEntities() {
        List<String> allowed = KineticRegistries.entityTypes().ids().stream().map(ResourceLocation::toString)
                .filter(this::isLivingEntity).toList();
        KineticSelectors.openEntitySelector(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.selector.title"),
                blockRule.entityWeights.keySet(), allowed, selected -> {
                    blockRule.entityWeights.keySet().removeIf(id -> !selected.contains(id));
                    for (String id : selected) blockRule.entityWeights.putIfAbsent(id, Math.max(1, entityRule(id).weight));
                    if (selectedEntityId != null && !blockRule.entityWeights.containsKey(selectedEntityId)) selectedEntityId = null;
                    if (selectedEntityId == null && !blockRule.entityWeights.isEmpty()) {
                        selectedEntityId = blockRule.entityWeights.keySet().iterator().next();
                    }
                    onChange.run();
                });
    }

    private boolean isLivingEntity(String id) {
        var level = KineticClientRuntime.currentLevel();
        ResourceLocation location = KineticResourceIds.tryParse(id);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        if (level == null || type == null) return false;
        try {
            Entity entity = type.create(level);
            return entity instanceof LivingEntity;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void removeSelected() {
        if (selectedEntityId == null) return;
        blockRule.entityWeights.remove(selectedEntityId);
        selectedEntityId = blockRule.entityWeights.keySet().stream().findFirst().orElse(null);
        onChange.run();
        rebuild();
    }

    // ------------------------------------------------------------------ 内容

    private List<String> keys() {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String id : blockRule.entityWeights.keySet()) {
            String name = entityName(id).getString();
            if (!normalized.isEmpty() && !KineticSearch.match((id + " " + name + " " + KineticSearch.pinyin(name))
                    .toLowerCase(Locale.ROOT), normalized)) continue;
            result.add(id);
        }
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        helpRect = null;
        if (selectedEntityId != null && !blockRule.entityWeights.containsKey(selectedEntityId)) selectedEntityId = null;
        KineticLayout.Split columns = KineticLayout.splitHorizontal(body, LIST_WIDTH, GAP);
        KineticLayout.Rect left = section(columns.first(), tr("pool.list", blockRule.entityWeights.size()));
        KineticLayout.Split rows = takeRow(left);
        textInput(ui, rows.first(), query, KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.search.placeholder"),
                tip("search"), value -> {
                    query = value;
                    cards.reset();
                    cards.setKeys(keys());
                });
        cards.types(id -> id)
                .fallback(id -> Component.literal("?"))
                .selected(id -> id.equals(selectedEntityId))
                .dimmed(id -> !entityRule(id).enabled);
        cards.layout(this, rows.second(), keys());

        if (selectedEntityId == null) {
            helpRect = section(columns.second(), KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.select_hint"));
            return;
        }
        String id = selectedEntityId;
        BreakSpawnConfig.EntityRule rule = entityRule(id);
        String[] labels = {"weight", "spawn_mode", "entity_switches", "custom_name", "y_range", "light", "dimensions", "biomes",
                "allowed_blocks", "blocked_blocks", "attributes", "equipment", "entity_nbt"};
        KineticLayout.Rect area = section(columns.second(), entityName(id));
        KineticUi rowsUi = form.attach(this, ui, area, labels.length, LABEL_WIDTH);
        for (int index = 0; index < labels.length; index++) {
            String key = labels[index];
            form.label(index, "weight".equals(key) ? tr("weight", String.format(Locale.ROOT, "%.1f", share(id))) : tr(key), tip(key));
        }
        int row = 0;
        intField(rowsUi, number(form.control(row++)), blockRule.entityWeights.getOrDefault(id, 0), 0, 1_000_000, tip("weight"), value -> {
            if (value == null) return;
            blockRule.entityWeights.put(id, value);
            onChange.run();
        });
        KineticLayout.Rect mode = compact(form.control(row++));
        List<dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown.Option> modes = new ArrayList<>();
        for (String value : MODES) {
            String lower = value.toLowerCase(Locale.ROOT);
            modes.add(option(value, KineticI18n.translatable("gui.entitycontrol.breakspawn.spawn_mode." + lower), tip("mode." + lower)));
        }
        choice(rowsUi, mode.x(), mode.y(), mode.width(), modes)
                .selected(rule.spawnMode == null ? "AUTO" : rule.spawnMode)
                .tooltip(tip("spawn_mode"))
                .onChange(value -> {
                    rule.spawnMode = value;
                    onChange.run();
                }).build();
        toggleMenu(rowsUi, compact(form.control(row++)), tr("entity_switches.button"), tip("entity_switches"), List.of(
                toggle("entity_enabled", () -> rule.enabled, value -> rule.enabled = value),
                toggle("persistent", () -> rule.persistent, value -> rule.persistent = value),
                toggle("silent", () -> rule.silent, value -> rule.silent = value),
                toggle("glowing", () -> rule.glowing, value -> rule.glowing = value),
                toggle("no_ai", () -> rule.noAi, value -> rule.noAi = value),
                toggle("invulnerable", () -> rule.invulnerable, value -> rule.invulnerable = value),
                toggle("name_visible", () -> rule.customNameVisible, value -> rule.customNameVisible = value)
        ), true, onChange);
        text(rowsUi, form.control(row++), rule.customName, tip("custom_name"), value -> rule.customName = value);
        pair(rowsUi, form.control(row++), rule.minY, rule.maxY, -2048, 4096, value -> rule.minY = value, value -> rule.maxY = value);
        pair(rowsUi, form.control(row++), rule.minLight, rule.maxLight, 0, 15, value -> rule.minLight = value, value -> rule.maxLight = value);
        text(rowsUi, form.control(row++), rule.dimensions, tip("dimensions"), value -> rule.dimensions = value);
        text(rowsUi, form.control(row++), rule.biomes, tip("biomes"), value -> rule.biomes = value);
        text(rowsUi, form.control(row++), rule.allowedBlocks, tip("allowed_blocks"), value -> rule.allowedBlocks = value);
        text(rowsUi, form.control(row++), rule.blockedBlocks, tip("blocked_blocks"), value -> rule.blockedBlocks = value);
        actionButton(rowsUi, compact(form.control(row++)), tr("attributes.button", rule.attributes.size()), tip("attributes"), true,
                () -> openChild(new AttributeRangeScreen(id, rule, onChange)));
        long equipped = rule.equipment.values().stream().filter(spec -> spec != null && spec.itemId != null && !spec.itemId.isBlank()).count();
        actionButton(rowsUi, compact(form.control(row++)), tr("equipment.button", equipped), tip("equipment"), true,
                () -> openChild(new EquipmentEditorScreen(id, rule, onChange)));
        boolean hasNbt = rule.entityNbt != null && !rule.entityNbt.isBlank();
        actionButton(rowsUi, compact(form.control(row)), tr(hasNbt ? "nbt.set" : "nbt.none"), tip("entity_nbt"), true,
                () -> KineticSelectors.openNbtEditor(hasNbt ? rule.entityNbt : "", value -> {
                    rule.entityNbt = value == null ? "" : value;
                    onChange.run();
                }));
    }

    private ToggleOption toggle(String key, java.util.function.BooleanSupplier getter, Consumer<Boolean> setter) {
        return new ToggleOption(tr("switch." + key), tip("switch." + key), getter, setter);
    }

    private void text(KineticUi ui, KineticLayout.Rect row, String value, Component tip, Consumer<String> setter) {
        textInput(ui, new KineticLayout.Rect(row.x(), row.y(), Math.min(row.width(), 150), H), value == null ? "" : value,
                KineticI18n.translatable("gui.entitycontrol.breakspawn.csv_hint"), tip, changed -> {
                    setter.accept(changed.trim());
                    onChange.run();
                });
    }

    private void pair(KineticUi ui, KineticLayout.Rect row, int minValue, int maxValue, int min, int max, IntConsumer minSetter,
                      IntConsumer maxSetter) {
        intField(ui, new KineticLayout.Rect(row.x(), row.y(), PAIR_WIDTH, H), minValue, min, max, tr("pair.min"), value -> {
            if (value == null) return;
            minSetter.accept(value);
            onChange.run();
        });
        intField(ui, new KineticLayout.Rect(row.x() + PAIR_WIDTH + GAP, row.y(), PAIR_WIDTH, H), maxValue, min, max, tr("pair.max"),
                value -> {
                    if (value == null) return;
                    maxSetter.accept(value);
                    onChange.run();
                });
    }

    // ------------------------------------------------------------------ 绘制与交互

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cards.render(graphics, mouseX, mouseY);
        if (helpRect != null) {
            graphics.wrappedText(tr(blockRule.entityWeights.isEmpty() ? "help.pool_empty" : "help.pool"), helpRect.x(), helpRect.y(),
                    helpRect.width(), KineticTheme.current().text());
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        String id = cards.keyAt(mouseX, mouseY);
        if (id == null) return false;
        List<Component> lines = new ArrayList<>();
        lines.add(entityName(id));
        lines.add(Component.literal(id));
        lines.add(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.weight_short", blockRule.entityWeights.getOrDefault(id, 0),
                String.format(Locale.ROOT, "%.1f", share(id))));
        if (!entityRule(id).enabled) lines.add(tr("card.entity_disabled"));
        lines.add(tr("card.hint"));
        tooltipLines(lines);
        return true;
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        String id = cards.keyAt(input.x(), input.y());
        if (id == null) return false;
        if (!id.equals(selectedEntityId)) {
            selectedEntityId = id;
            form.reset();
            clearFocus();
            rebuild();
        }
        if (input.button() == MouseButton.RIGHT) {
            openMenu(input.x(), input.y(), List.of(KineticOverlays.MenuItem.danger(
                    KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.remove"), tip("pool.remove"), this::removeSelected)));
        }
        return true;
    }

    @Override
    protected void onRemoved() {
        cards.clear();
    }
}
