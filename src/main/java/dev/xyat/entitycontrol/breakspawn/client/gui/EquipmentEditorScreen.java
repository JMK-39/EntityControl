package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.EmptySlotIcons;
import dev.xyat.kineticcore.api.client.gui.input.MouseButton;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tip;
import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tr;

/** 生成时的装备：原版装备槽位（空槽显示原版轮廓），左键空槽选择物品，右键槽位打开菜单（替换 / NBT / 清空）；旁边是掉落概率。
 * Spawn equipment: vanilla equipment slots (empty ones show the vanilla outline); left-click an empty slot to pick an
 * item, right-click a slot for replace / NBT / clear; the number beside each slot is the drop chance. */
public final class EquipmentEditorScreen extends EcPage {
    private static final String[] SLOTS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
    private static final int SLOT = dev.xyat.kineticcore.api.client.gui.theme.KineticSizes.ITEM_SLOT;
    private static final int SLOT_PITCH = SLOT + GAP;

    private final String entityId;
    private final BreakSpawnConfig.EntityRule rule;
    private final Runnable onChange;
    private final Map<String, KineticLayout.Rect> icons = new LinkedHashMap<>();
    private KineticLayout.Rect helpRect;
    private KineticLayout.Rect previewRect;
    private String hoveredSlot;
    // The mob wearing the configured equipment, between the armor and hand columns like the vanilla inventory.
    private final dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview preview = dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview.create();
    private net.minecraft.world.entity.LivingEntity previewEntity;
    private boolean previewCreated;

    public EquipmentEditorScreen(String entityId, BreakSpawnConfig.EntityRule rule, Runnable onChange) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.title"));
        this.entityId = entityId;
        this.rule = rule;
        this.onChange = onChange;
    }

    private BreakSpawnConfig.EquipmentSpec spec(String slot) {
        return rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
    }

    private static ItemStack stack(BreakSpawnConfig.EquipmentSpec spec) {
        if (spec == null || spec.itemId == null || spec.itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation id = KineticResourceIds.tryParse(spec.itemId);
        Item item = id == null ? null : KineticRegistries.items().get(id);
//? if >=1.21 {
/*        if (item == null) return ItemStack.EMPTY;
        try { var stack = dev.xyat.entitycontrol.breakspawn.data.EquipmentData.compile(spec.itemId, spec.nbt); stack.setCount(Math.max(1, spec.count)); return stack; }
        catch (RuntimeException invalid) { return new ItemStack(item, Math.max(1, spec.count)); }
*///?} else {
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, spec.count));
        if (spec.nbt != null && !spec.nbt.isBlank()) {
            try { stack.setTag(net.minecraft.nbt.TagParser.parseTag(spec.nbt)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) { }
        }
        return stack;
//?}
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        bindDeleteAction(() -> hoveredSlot != null && !stack(rule.equipment.get(hoveredSlot)).isEmpty(), () -> clearSlot(hoveredSlot));
        icons.clear();
        KineticLayout.Split columns = leftColumn(body, (body.width() - GAP) * 3 / 5);
        KineticLayout.Rect slots = section(columns.first(), Component.empty());
        int column = SLOT + GAP + NUMBER_WIDTH;
        int handsX = slots.right() - column;
        int previewX = slots.x() + column + GAP, previewW = handsX - GAP - previewX;
        // Square, like the entity cards: the preview's checkerboard fills it exactly.
        previewRect = new KineticLayout.Rect(previewX, slots.y(), previewW, Math.min(previewW, slots.height()));
        for (int index = 0; index < SLOTS.length; index++) {
            String slot = SLOTS[index];
            BreakSpawnConfig.EquipmentSpec spec = rule.equipment.get(slot);
            // Armor top to bottom on the left, the two hands on the right, the mob between them.
            int x = index < 4 ? slots.x() : handsX;
            int y = slots.y() + (index < 4 ? index : index - 4) * SLOT_PITCH;
            icons.put(slot, new KineticLayout.Rect(x, y, SLOT, SLOT));
            decimalField(ui, new KineticLayout.Rect(x + SLOT + GAP, y + (SLOT - H) / 2, NUMBER_WIDTH, H),
                    spec == null ? 0D : spec.dropChance * 100D, 0D, 100D, tip("equipment.drop"), value -> {
                        if (value == null) return;
                        spec(slot).dropChance = Math.max(0D, Math.min(1D, value / 100D));
                        onChange.run();
                    });
        }
        // How the slots work is in their tooltips; no separate help column.
        helpRect = null;
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        for (Map.Entry<String, KineticLayout.Rect> entry : icons.entrySet()) {
            if (!entry.getValue().contains(input.x(), input.y())) continue;
            String slot = entry.getKey();
            if (input.button() == MouseButton.RIGHT) openMenu(input.x(), input.y(), slotMenu(slot));
            else if (input.button() == MouseButton.LEFT && stack(rule.equipment.get(slot)).isEmpty()) pickItem(slot);
            else if (input.button() == MouseButton.LEFT) openMenu(input.x(), input.y(), slotMenu(slot));
            return true;
        }
        return false;
    }

    private List<KineticOverlays.MenuItem> slotMenu(String slot) {
        BreakSpawnConfig.EquipmentSpec spec = rule.equipment.get(slot);
        boolean hasItem = !stack(spec).isEmpty();
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        items.add(KineticOverlays.MenuItem.action(tr("equipment.pick"), tip("equipment.pick"), () -> pickItem(slot)));
        items.add(hasItem
//? if >=1.21 {
/*                // Item component text is edited in Core's NBT editor, the same screen Forge uses for NBT; blank saves as [].
                ? KineticOverlays.MenuItem.action(tr("equipment.nbt"), tip("equipment.nbt"), () -> KineticSelectors.openNbtEditor(
                spec.nbt == null ? "" : spec.nbt, text -> {
                    try { dev.xyat.entitycontrol.breakspawn.data.EquipmentData.compile(spec.itemId, text); return null; }
                    catch (RuntimeException invalid) { return invalid.getMessage() == null ? invalid.toString() : invalid.getMessage(); }
                }, value -> {
                    spec(slot).nbt = value.isBlank() ? "[]" : value;
                    onChange.run();
                }))
*///?} else {
                ? KineticOverlays.MenuItem.action(tr("equipment.nbt"), tip("equipment.nbt"), () -> KineticSelectors.openNbtEditor(
                spec.nbt == null ? "" : spec.nbt, value -> {
                    spec(slot).nbt = value == null ? "" : value;
                    onChange.run();
                }))
//?}
                : KineticOverlays.MenuItem.disabled(tr("equipment.nbt"), tip("equipment.pick_first")));
        items.add(hasItem
                ? KineticOverlays.MenuItem.danger(tr("equipment.clear"), tip("equipment.clear"), () -> clearSlot(slot))
                : KineticOverlays.MenuItem.disabled(tr("equipment.clear"), tip("equipment.pick_first")));
        return items;
    }

    private void clearSlot(String slot) {
        if (slot == null) return;
        BreakSpawnConfig.EquipmentSpec target = spec(slot);
        target.itemId = ""; target.nbt = ""; target.count = 1; target.dropChance = 0D;
        onChange.run(); rebuild();
    }

    private void pickItem(String slot) {
        KineticSelectors.openItemSelectorWithOptions(KineticSelectors.ItemSelectorOptions.itemsOnly(null, List.of(), stack -> true),
                selection -> {
                    if (!selection.isItem()) return;
                    ItemStack stack = selection.stack().copy();
                    ResourceLocation id = KineticRegistries.items().id(stack.getItem());
                    if (id == null) return;
                    BreakSpawnConfig.EquipmentSpec target = spec(slot);
                    target.itemId = id.toString();
                    target.count = 1;
//? if >=1.21 {
/*                    target.nbt = dev.xyat.entitycontrol.breakspawn.data.EquipmentData.format(stack);
*///?} else {
                    target.nbt = stack.hasTag() && stack.getTag() != null ? stack.getTag().toString() : "";
//?}
                    onChange.run();
                });
    }

    @Override
    protected Component headerTitle() {
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        var type = id == null ? null : KineticRegistries.entityTypes().get(id);
        return tr("equipment.title", type == null ? Component.literal(entityId) : type.getDescription());
    }

    private net.minecraft.world.entity.LivingEntity previewEntity() {
        if (!previewCreated) {
            previewCreated = true;
            ResourceLocation id = KineticResourceIds.tryParse(entityId);
            var type = id == null ? null : KineticRegistries.entityTypes().get(id);
            var level = dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentLevel();
            try {
                if (type != null && level != null) {
                    net.minecraft.world.entity.Entity entity = type.create(level);
                    if (entity instanceof net.minecraft.world.entity.LivingEntity living) previewEntity = living;
                }
            } catch (Throwable ignored) {
                previewEntity = null;
            }
        }
        return previewEntity;
    }

    private static net.minecraft.world.entity.EquipmentSlot equipmentSlot(String slot) {
        return switch (slot) {
            case "head" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chest" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "legs" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "feet" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case "mainhand" -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            default -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
        };
    }

    private void renderPreview(KineticGraphics graphics, int mouseX, int mouseY) {
        if (previewRect == null || previewRect.width() < 24) return;
        KineticTheme.itemSlot(graphics, previewRect.x(), previewRect.y(), previewRect.width(), previewRect.height(), 4, false, false, false);
        var living = previewEntity();
        boolean hovered = previewRect.contains(mouseX, mouseY);
        if (living != null) {
            for (String slot : SLOTS) living.setItemSlot(equipmentSlot(slot), stack(rule.equipment.get(slot)));
            preview.render(graphics, living, "equipment:" + entityId, previewRect.x() + 1, previewRect.y() + 1,
                    previewRect.width() - 2, previewRect.height() - 2, hovered);
        } else {
            preview.render(graphics, entityId, "equipment:" + entityId, previewRect.x() + 1, previewRect.y() + 1,
                    previewRect.width() - 2, previewRect.height() - 2, hovered);
        }
    }

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPreview(graphics, mouseX, mouseY);
        hoveredSlot = null;
        for (Map.Entry<String, KineticLayout.Rect> entry : icons.entrySet()) {
            KineticLayout.Rect rect = entry.getValue();
            if (rect.contains(mouseX, mouseY)) hoveredSlot = entry.getKey();
            ItemStack stack = stack(rule.equipment.get(entry.getKey()));
            KineticTheme.itemSlot(graphics, rect.x(), rect.y(), rect.width(), rect.contains(mouseX, mouseY));
            if (!stack.isEmpty()) {
                KineticTheme.item(graphics, stack, rect.x(), rect.y(), rect.width(), 1.0F, false);
            } else {
                var outline = EmptySlotIcons.of(entry.getKey());
                if (outline != null) graphics.texture(outline, rect.x() + (SLOT - 16) / 2, rect.y() + (SLOT - 16) / 2, 0, 0, 16, 16);
            }
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        for (Map.Entry<String, KineticLayout.Rect> entry : icons.entrySet()) {
            if (!entry.getValue().contains(mouseX, mouseY)) continue;
            ItemStack stack = stack(rule.equipment.get(entry.getKey()));
            // A filled slot shows the item's full tooltip; an empty one names the slot (the outline shows it on the page).
            if (!stack.isEmpty()) {
                itemTooltip(stack);
                return true;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(KineticI18n.translatable("gui.entitycontrol.breakspawn.slot." + entry.getKey()));
            lines.add(tip("equipment.item"));
            tooltipLines(lines);
            return true;
        }
        return false;
    }
}
