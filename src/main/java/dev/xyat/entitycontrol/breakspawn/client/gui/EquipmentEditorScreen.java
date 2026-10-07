package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.client.gui.kit.EcPage;
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

/** 生成时的装备：每个槽位一行——物品（选择 / NBT / 清空）与掉落概率。 */
public final class EquipmentEditorScreen extends EcPage {
    private static final String[] SLOTS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
    private static final int LABEL_WIDTH = 72;

    private final String entityId;
    private final BreakSpawnConfig.EntityRule rule;
    private final Runnable onChange;
    private final Map<String, KineticLayout.Rect> icons = new LinkedHashMap<>();
    private KineticLayout.Rect helpRect;

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
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, spec.count));
//?}
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        icons.clear();
        KineticLayout.Split columns = halves(body);
        Form form = form(section(columns.first(), tr("equipment.section")), LABEL_WIDTH);
        for (String slot : SLOTS) {
            BreakSpawnConfig.EquipmentSpec spec = rule.equipment.get(slot);
            ItemStack stack = stack(spec);
            KineticLayout.Rect row = form.row(KineticI18n.translatable("gui.entitycontrol.breakspawn.slot." + slot), tip("equipment.slot"));
            icons.put(slot, new KineticLayout.Rect(row.x(), row.y() + (H - 18) / 2, 18, 18));
            Component itemText = stack.isEmpty() ? KineticI18n.translatable("gui.entitycontrol.breakspawn.equipment.empty") : stack.getHoverName();
            KineticLayout.Rect itemRect = new KineticLayout.Rect(row.x() + 16 + GAP, row.y(), CONTROL_WIDTH, H);
            menuButton(ui, itemRect, itemText, tip("equipment.item"), true, () -> slotMenu(slot));
            decimalField(ui, new KineticLayout.Rect(itemRect.x() + CONTROL_WIDTH + GAP, row.y(), NUMBER_WIDTH, H),
                    spec == null ? 0D : spec.dropChance * 100D, 0D, 100D, tip("equipment.drop"), value -> {
                        if (value == null) return;
                        spec(slot).dropChance = Math.max(0D, Math.min(1D, value / 100D));
                        onChange.run();
                    });
        }
        helpRect = section(columns.second(), tr("equipment.help.title"));
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
                ? KineticOverlays.MenuItem.danger(tr("equipment.clear"), tip("equipment.clear"), () -> {
                    BreakSpawnConfig.EquipmentSpec target = spec(slot);
                    target.itemId = "";
                    target.nbt = "";
                    target.count = 1;
                    target.dropChance = 0D;
                    onChange.run();
                    rebuild();
                })
                : KineticOverlays.MenuItem.disabled(tr("equipment.clear"), tip("equipment.pick_first")));
        return items;
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

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Map.Entry<String, KineticLayout.Rect> entry : icons.entrySet()) {
            KineticLayout.Rect rect = entry.getValue();
            ItemStack stack = stack(rule.equipment.get(entry.getKey()));
            KineticTheme.itemSlot(graphics, rect.x(), rect.y(), rect.width(), rect.contains(mouseX, mouseY));
            if (!stack.isEmpty()) KineticTheme.item(graphics, stack, rect.x(), rect.y(), rect.width(), 0.75F, false);
        }
        if (helpRect != null) {
            graphics.wrappedText(tr("equipment.help"), helpRect.x(), helpRect.y(), helpRect.width(), KineticTheme.current().text());
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        for (Map.Entry<String, KineticLayout.Rect> entry : icons.entrySet()) {
            ItemStack stack = stack(rule.equipment.get(entry.getKey()));
            if (!stack.isEmpty() && entry.getValue().contains(mouseX, mouseY)) {
                itemTooltip(stack);
                return true;
            }
        }
        return false;
    }
}
