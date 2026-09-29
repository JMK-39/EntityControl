package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EquipmentEditorScreen extends KineticPage {
    private static final String[] SLOTS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
    private static final int[] CARD_X = {20, 225, 430, 20, 225, 430};
    private static final int[] CARD_Y = {70, 70, 70, 190, 190, 190};
    private static final int CARD_W = 190;
    private static final int CARD_H = 105;

    private final String entityId;
    private final BreakSpawnConfig.EntityRule rule;
    private final Map<String, KineticNumberField> dropBoxes = new LinkedHashMap<>();

    public EquipmentEditorScreen(String entityId, BreakSpawnConfig.EntityRule rule) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.title"));
        this.entityId = entityId;
        this.rule = rule;
    }

    @Override
    protected void build(KineticUi ui) {
        dropBoxes.clear();
        for (int index = 0; index < SLOTS.length; index++) {
            String slot = SLOTS[index];
            int x = CARD_X[index];
            int y = CARD_Y[index];
            BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
            KineticNumberField dropBox = ui.numberField(x + 109, y + 27, 68, NumberType.DECIMAL)
                    .allowNegative(false)
                    .range(0D, 100D)
                    .value(spec.dropChance * 100.0D)
                    .onChange(value -> {
                        try {
                            double parsed = Double.parseDouble(value.trim());
                            if (Double.isFinite(parsed)) spec.dropChance = Math.max(0D, Math.min(1D, parsed / 100D));
                        } catch (RuntimeException ignored) {
                        }
                    })
                    .firstShownTextAsDefault().build();
            dropBoxes.put(slot, dropBox);

            ui.button(x + 8, y + 72, 54).compact()
                    .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.select"))
                    .onClick(() -> openItemSelector(slot)).build();
            ui.button(x + 67, y + 72, 54).compact()
                    .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.nbt"))
                    .onClick(() -> openNbtEditor(slot)).build();
            ui.button(x + 126, y + 72, 54).compact()
                    .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.clear"))
                    .onClick(() -> clearSlot(slot)).build();
        }
        ui.button(430, 322, 190)
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.back"))
                .onClick(this::navigateBack).build();
    }

    private void openItemSelector(String slot) {
        KineticSelectors.openItemSelectorWithOptions(
                KineticSelectors.ItemSelectorOptions.itemsOnly(null, List.of(), stack -> true),
                selection -> {
                    if (!selection.isItem()) return;
                    ItemStack stack = selection.stack().copy();
                    ResourceLocation id = KineticRegistries.items().id(stack.getItem());
                    if (id == null) return;
                    BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
                    spec.itemId = id.toString();
                    spec.count = 1;
                    spec.nbt = stack.hasTag() && stack.getTag() != null ? stack.getTag().toString() : "";
                }
        );
    }

    private void openNbtEditor(String slot) {
        BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
        KineticSelectors.openNbtEditor(spec.nbt == null ? "" : spec.nbt,
                value -> spec.nbt = value == null ? "" : value);
    }

    private void clearSlot(String slot) {
        BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
        spec.itemId = "";
        spec.nbt = "";
        spec.count = 1;
        spec.dropChance = 0.0D;
        KineticNumberField dropBox = dropBoxes.get(slot);
        if (dropBox != null) dropBox.setDoubleValue(0D);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 8, 8, 624, 344);
        graphics.text(title(), 20, 18, KineticTheme.current().text());
        graphics.text(entityId, 20, 38, KineticTheme.current().mutedText());
        for (int index = 0; index < SLOTS.length; index++) {
            renderCard(graphics, SLOTS[index], CARD_X[index], CARD_Y[index]);
        }
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.hint"), 20, 310,
                KineticTheme.current().mutedText());
    }

    private void renderCard(KineticGraphics graphics, String slot, int x, int y) {
        KineticTheme.panelAlt(graphics, x, y, CARD_W, CARD_H);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.slot." + slot), x + 8, y + 8,
                KineticTheme.current().text());
        BreakSpawnConfig.EquipmentSpec spec = rule.equipment.get(slot);
        ItemStack stack = stackFromSpec(spec);
        if (!stack.isEmpty()) {
            graphics.item(stack, x + 8, y + 29);
            String id = spec == null ? "" : spec.itemId;
            graphics.text(KineticText.ellipsize(id, 78), x + 30, y + 33, KineticTheme.current().text());
        } else {
            graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.empty"), x + 8, y + 33,
                    KineticTheme.current().text());
        }
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.drop_chance"), x + 109, y + 16,
                KineticTheme.current().text());
    }

    private ItemStack stackFromSpec(BreakSpawnConfig.EquipmentSpec spec) {
        if (spec == null || spec.itemId == null || spec.itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation id = KineticResourceIds.tryParse(spec.itemId);
        Item item = id == null ? null : KineticRegistries.items().get(id);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, 1);
        if (spec.nbt != null && !spec.nbt.isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(spec.nbt));
            } catch (Exception ignored) {
            }
        }
        return stack;
    }
}
