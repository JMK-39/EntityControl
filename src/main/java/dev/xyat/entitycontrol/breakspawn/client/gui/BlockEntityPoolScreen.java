package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.list.KineticRowList;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlockEntityPoolScreen extends KineticPage {
    private static final int LIST_X = 12;
    private static final int LIST_W = 356;
    private static final int SEARCH_Y = 40;
    private static final int LIST_Y = 67;
    private static final int LIST_H = 244;
    private static final int ROW_H = 54;
    private static final int RIGHT_X = 376;
    private static final int RIGHT_W = 252;

    private final BreakSpawnConfig.ConfigRoot config;
    private final String blockId;
    private final BreakSpawnConfig.BlockRule blockRule;
    private final KineticEntityPreview preview = KineticEntityPreview.create();
    private final List<String> filteredIds = new ArrayList<>();
    private EntityList entityList;
    private KineticNumberField weightBox;
    private KineticButton attributesButton;
    private KineticButton equipmentButton;
    private KineticButton nbtButton;
    private KineticButton removeButton;
    private String searchQuery = "";
    private String selectedEntityId;

    public BlockEntityPoolScreen(BreakSpawnConfig.ConfigRoot config, String blockId, BreakSpawnConfig.BlockRule blockRule) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.title"));
        this.config = config;
        this.blockId = blockId;
        this.blockRule = blockRule;
        preview.setRotationSpeedPercent(100);
        refreshFiltered("");
        if (!blockRule.entityWeights.isEmpty()) selectedEntityId = blockRule.entityWeights.keySet().iterator().next();
    }

    @Override
    protected void build(KineticUi ui) {
        ui.textField(LIST_X, SEARCH_Y, 244)
                .label(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.search"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.search.placeholder"))
                .maxLength(128)
                .value(searchQuery)
                .onChange(value -> {
                    searchQuery = value == null ? "" : value;
                    refreshFiltered(searchQuery);
                }).firstShownTextAsDefault().build();
        ui.button(LIST_X + 249, SEARCH_Y, 107).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.manage"))
                .onClick(this::openEntitySelector).build();

        entityList = ui.add(new EntityList(LIST_X, LIST_Y, LIST_W, LIST_H));
        entityList.setItems(filteredIds);
        entityList.setSelectedIndex(selectedEntityId == null ? -1 : filteredIds.indexOf(selectedEntityId));

        boolean selected = selectedEntityId != null && blockRule.entityWeights.containsKey(selectedEntityId);
        weightBox = ui.numberField(490, 103, 120, NumberType.INT)
                .allowNegative(false).range(0, null)
                .value(selected ? blockRule.entityWeights.getOrDefault(selectedEntityId, 0) : 0)
                .onChange(value -> {
                    if (selectedEntityId == null || !blockRule.entityWeights.containsKey(selectedEntityId)) return;
                    try {
                        blockRule.entityWeights.put(selectedEntityId, Math.max(0, Integer.parseInt(value.trim())));
                    } catch (RuntimeException ignored) {
                    }
                }).firstShownTextAsDefault().build();
        weightBox.setEnabled(selected);

        attributesButton = ui.button(388, 208, 72).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.attributes"))
                .onClick(this::openAttributeEditor).build();
        equipmentButton = ui.button(465, 208, 72).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.equipment"))
                .onClick(this::openEquipmentEditor).build();
        nbtButton = ui.button(542, 208, 68).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.nbt"))
                .onClick(this::openEntityNbtEditor).build();
        removeButton = ui.button(490, 238, 120)
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.remove"))
                .onClick(this::removeSelected).build();
        attributesButton.setEnabled(selected);
        equipmentButton.setEnabled(selected);
        nbtButton.setEnabled(selected);
        removeButton.setEnabled(selected);

        ui.button(514, 322, 104)
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.back"))
                .onClick(this::navigateBack).build();
    }

    private void toggleContextValue(int index) {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule == null) return;
        switch (index) {
            case 0 -> rule.enabled = !rule.enabled;
            case 1 -> rule.persistent = !rule.persistent;
            case 2 -> rule.silent = !rule.silent;
            case 3 -> rule.glowing = !rule.glowing;
            case 4 -> rule.noAi = !rule.noAi;
            case 5 -> rule.invulnerable = !rule.invulnerable;
            case 6 -> rule.customNameVisible = !rule.customNameVisible;
            default -> { return; }
        }
    }

    private void openEntityContextMenu(double mouseX, double mouseY) {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule == null) return;
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
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            items.add(KineticOverlays.MenuItem.toggle(
                    KineticI18n.translatable(keys[i]), KineticI18n.translatable(keys[i]), values[i],
                    () -> toggleContextValue(index)));
        }
        openContextMenu(mouseX, mouseY, items);
    }

    private void openEntitySelector() {
        List<String> allowed = KineticRegistries.entityTypes().ids().stream()
                .map(ResourceLocation::toString)
                .filter(this::isLivingEntity)
                .toList();
        KineticSelectors.openEntitySelector(
                KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.selector.title"),
                blockRule.entityWeights.keySet(), allowed,
                selected -> {
                    blockRule.entityWeights.keySet().removeIf(id -> !selected.contains(id));
                    for (String id : selected) {
                        BreakSpawnConfig.EntityRule entityRule = config.entities.computeIfAbsent(id, ignored -> new BreakSpawnConfig.EntityRule());
                        blockRule.entityWeights.putIfAbsent(id, Math.max(1, entityRule.weight));
                    }
                    if (selectedEntityId != null && !blockRule.entityWeights.containsKey(selectedEntityId)) selectedEntityId = null;
                    if (selectedEntityId == null && !blockRule.entityWeights.isEmpty()) {
                        selectedEntityId = blockRule.entityWeights.keySet().iterator().next();
                    }
                    refreshFiltered(searchQuery);
                    rebuild();
                }
        );
    }

    private boolean isLivingEntity(String id) {
        var level = KineticClientRuntime.currentLevel();
        if (level == null) return false;
        EntityType<?> type = entityType(id);
        if (type == null) return false;
        try {
            Entity entity = type.create(level);
            return entity instanceof LivingEntity;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private EntityType<?> entityType(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        return location == null ? null : KineticRegistries.entityTypes().get(location);
    }

    private void refreshFiltered(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredIds.clear();
        for (String id : blockRule.entityWeights.keySet()) {
            EntityType<?> type = entityType(id);
            String name = type == null ? id : type.getDescription().getString();
            String searchData = (id + " " + name + " " + KineticSearch.pinyin(name)).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || KineticSearch.match(searchData, normalized)) filteredIds.add(id);
        }
        filteredIds.sort(Comparator.naturalOrder());
        if (entityList != null) {
            entityList.setItems(filteredIds);
            entityList.setSelectedIndex(selectedEntityId == null ? -1 : filteredIds.indexOf(selectedEntityId));
        }
    }

    private void syncSelectionControls() {
        boolean selected = selectedEntityId != null && blockRule.entityWeights.containsKey(selectedEntityId);
        if (weightBox != null) {
            weightBox.setEnabled(selected);
            if (selected) weightBox.setIntValue(blockRule.entityWeights.getOrDefault(selectedEntityId, 0));
            else weightBox.setTextValue("");
        }
        if (attributesButton != null) attributesButton.setEnabled(selected);
        if (equipmentButton != null) equipmentButton.setEnabled(selected);
        if (nbtButton != null) nbtButton.setEnabled(selected);
        if (removeButton != null) removeButton.setEnabled(selected);
    }

    private BreakSpawnConfig.EntityRule selectedEntityRule() {
        if (selectedEntityId == null) return null;
        return config.entities.computeIfAbsent(selectedEntityId, ignored -> new BreakSpawnConfig.EntityRule());
    }

    private void openAttributeEditor() {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule != null) openChild(new AttributeRangeScreen(selectedEntityId, rule));
    }

    private void openEquipmentEditor() {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule != null) openChild(new EquipmentEditorScreen(selectedEntityId, rule));
    }

    private void openEntityNbtEditor() {
        BreakSpawnConfig.EntityRule rule = selectedEntityRule();
        if (rule != null) {
            KineticSelectors.openNbtEditor(rule.entityNbt == null ? "" : rule.entityNbt,
                    value -> rule.entityNbt = value == null ? "" : value);
        }
    }

    private void removeSelected() {
        if (selectedEntityId == null) return;
        blockRule.entityWeights.remove(selectedEntityId);
        selectedEntityId = blockRule.entityWeights.keySet().stream().findFirst().orElse(null);
        refreshFiltered(searchQuery);
        rebuild();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 6, 6, 628, 348);
        KineticTheme.panelAlt(graphics, LIST_X, LIST_Y, LIST_W, LIST_H);
        KineticTheme.panelAlt(graphics, RIGHT_X, LIST_Y, RIGHT_W, LIST_H);
        graphics.text(title(), 14, 16, KineticTheme.current().text());
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.block", blockName()), 380, 18,
                KineticTheme.current().text());
        renderDetails(graphics, mouseX, mouseY);
    }

    private Component blockName() {
        ResourceLocation id = KineticResourceIds.tryParse(blockId);
        if (id == null) return Component.literal("");
        var block = KineticRegistries.blocks().get(id);
        return block == null ? Component.literal(id.toString()) : block.getName();
    }

    private void renderDetails(KineticGraphics graphics, int mouseX, int mouseY) {
        if (selectedEntityId == null || !blockRule.entityWeights.containsKey(selectedEntityId)) {
            graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.select_hint"),
                    RIGHT_X + RIGHT_W / 2, 178, KineticTheme.current().text(), false);
            return;
        }
        KineticEntityPreview.drawCheckerboard(graphics, 407, 84, 72, 72);
        String stateKey = "breakspawn-pool-detail:" + blockId + ":" + selectedEntityId;
        boolean hovered = mouseX >= 407 && mouseX < 479 && mouseY >= 84 && mouseY < 156;
        preview.render(graphics, selectedEntityId, stateKey, 407, 84, 72, 72, hovered);
        registerPreviewZoomArea(preview, stateKey, 407, 84, 72, 72);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.weight"), 490, 90,
                KineticTheme.current().text());
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.share",
                        String.format(Locale.ROOT, "%.1f", weightPercent(selectedEntityId))),
                388, 172, KineticTheme.current().text());
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.hint"), 388, 196,
                KineticTheme.current().mutedText());
    }

    private double weightPercent(String id) {
        long total = 0L;
        int current = 0;
        for (Map.Entry<String, Integer> entry : blockRule.entityWeights.entrySet()) {
            int value = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            total += value;
            if (entry.getKey().equals(id)) current = value;
        }
        return total <= 0L ? 0D : current * 100D / total;
    }

    @Override
    protected void onRemoved() {
        preview.clear();
    }

    private final class EntityList extends KineticRowList<String> {
        private EntityList(int x, int y, int width, int height) {
            super(x, y, width, height, ROW_H);
        }

        @Override
        protected void renderRow(KineticGraphics graphics, String id, int index, int x, int y, int width,
                                 int height, boolean hovered, boolean selected) {
            KineticEntityPreview.drawCheckerboard(graphics, x + 3, y + 5, 44, 44);
            String stateKey = "breakspawn-pool:" + blockId + ":" + id;
            preview.render(graphics, id, stateKey, x + 3, y + 5, 44, 44, hovered);
            int previewTop = Math.max(y + 5, controlY());
            int previewBottom = Math.min(y + 49, controlY() + controlHeight());
            if (previewBottom > previewTop) {
                registerPreviewZoomArea(preview, stateKey, x + 3, previewTop, 44, previewBottom - previewTop);
            }
            EntityType<?> type = entityType(id);
            String name = type == null ? id : type.getDescription().getString();
            graphics.text(KineticText.ellipsize(name, width - 55), x + 53, y + 9, KineticTheme.current().text());
            graphics.text(KineticText.ellipsize(id, width - 55), x + 53, y + 24, KineticTheme.current().mutedText());
            int weight = blockRule.entityWeights.getOrDefault(id, 0);
            graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.weight_short", weight,
                            String.format(Locale.ROOT, "%.1f", weightPercent(id))),
                    x + 53, y + 39, KineticTheme.current().text());
        }

        @Override
        protected boolean onRowClick(String id, int index, MouseInput input) {
            if (!input.isLeft() && !input.isRight()) return false;
            selectedEntityId = id;
            setSelectedIndex(index);
            syncSelectionControls();
            if (input.isRight()) openEntityContextMenu(input.x(), input.y());
            return true;
        }

        @Override
        protected Component emptyText() {
            return KineticI18n.translatable("gui.entitycontrol.breakspawn.pool.empty");
        }
    }
}
