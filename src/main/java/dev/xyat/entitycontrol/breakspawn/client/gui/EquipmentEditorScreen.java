package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.selector.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EquipmentEditorScreen extends KineticScreen {
    private static final String[] SLOTS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
    private static final int[] CARD_X = {20, 225, 430, 20, 225, 430};
    private static final int[] CARD_Y = {70, 70, 70, 190, 190, 190};
    private static final int CARD_W = 190;
    private static final int CARD_H = 105;

    private final Screen parent;
    private final String entityId;
    private final BreakSpawnConfig.EntityRule rule;
    private final Map<String, EditBox> dropBoxes = new LinkedHashMap<>();

    public EquipmentEditorScreen(Screen parent, String entityId, BreakSpawnConfig.EntityRule rule) {
        super(Component.translatable("gui.entitycontrol.breakspawn.equipment.title"));
        this.parent = parent;
        this.entityId = entityId;
        this.rule = rule;
        useCanvas(640f, 360f, 6);
    }

    @Override
    protected void buildUi() {
        dropBoxes.clear();
        for (int index = 0; index < SLOTS.length; index++) {
            String slot = SLOTS[index];
            int x = CARD_X[index];
            int y = CARD_Y[index];
            BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
            EditBox dropBox = addRenderableWidget(new EditBox(font, x + 109, y + 27, 68, 18, Component.empty()));
            dropBox.setMaxLength(16);
            dropBox.setValue(String.valueOf(spec.dropChance * 100.0D));
            dropBox.setResponder(value -> {
                try {
                    spec.dropChance = Math.max(0.0D, Math.min(1.0D, Double.parseDouble(value.trim()) / 100.0D));
                } catch (Exception ignored) {
                }
            });
            dropBoxes.put(slot, dropBox);

            addRenderableWidget(Button.builder(
                            Component.translatable("gui.entitycontrol.breakspawn.equipment.select"),
                            ignored -> openItemSelector(slot))
                    .bounds(x + 8, y + 72, 54, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.entitycontrol.breakspawn.equipment.nbt"),
                            ignored -> openNbtEditor(slot))
                    .bounds(x + 67, y + 72, 54, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.entitycontrol.breakspawn.equipment.clear"),
                            ignored -> clearSlot(slot))
                    .bounds(x + 126, y + 72, 54, 20).build());
        }
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.back"),
                        ignored -> onClose())
                .bounds(430, 322, 190, 22).build());
    }

    private void openItemSelector(String slot) {
        Minecraft.getInstance().setScreen(new ItemSelectorScreen(this, selection -> {
            if (!selection.isItem()) {
                return;
            }
            ItemStack stack = selection.stack().copy();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) {
                return;
            }
            BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
            spec.itemId = id.toString();
            spec.count = 1;
            spec.nbt = stack.hasTag() && stack.getTag() != null ? stack.getTag().toString() : "";
        }));
    }

    private void openNbtEditor(String slot) {
        BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
        Minecraft.getInstance().setScreen(new NbtEditorScreen(
                spec.nbt == null ? "" : spec.nbt,
                value -> spec.nbt = value == null ? "" : value,
                this
        ));
    }

    private void clearSlot(String slot) {
        BreakSpawnConfig.EquipmentSpec spec = rule.equipment.computeIfAbsent(slot, ignored -> new BreakSpawnConfig.EquipmentSpec());
        spec.itemId = "";
        spec.nbt = "";
        spec.count = 1;
        spec.dropChance = 0.0D;
        EditBox dropBox = dropBoxes.get(slot);
        if (dropBox != null) {
            dropBox.setValue("0.0");
        }
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, canvasWidth, canvasHeight, 0xD9000000);
        GuiTheme.panel(graphics, 8, 8, 624, 344, 0xD91A1E26, 0xFF506070);
        graphics.drawString(font, title, 20, 18, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal(entityId), 20, 38, 0xFFB8C8D8, false);
        for (int index = 0; index < SLOTS.length; index++) {
            renderCard(graphics, SLOTS[index], CARD_X[index], CARD_Y[index]);
        }
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.equipment.hint"), 20, 326, 0xFFFFFFFF, false);
    }

    private void renderCard(GuiGraphics graphics, String slot, int x, int y) {
        GuiTheme.panel(graphics, x, y, CARD_W, CARD_H, 0xB010141A, 0xFF43515F);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.slot." + slot), x + 8, y + 8, 0xFFFFFFFF, false);
        BreakSpawnConfig.EquipmentSpec spec = rule.equipment.get(slot);
        ItemStack stack = stackFromSpec(spec);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 8, y + 29);
            String id = spec == null ? "" : spec.itemId;
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, id, 78)), x + 30, y + 33, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.equipment.empty"), x + 8, y + 33, 0xFFFFFFFF, false);
        }
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.equipment.drop_chance"), x + 109, y + 16, 0xFFFFFFFF, false);
    }

    private ItemStack stackFromSpec(BreakSpawnConfig.EquipmentSpec spec) {
        if (spec == null || spec.itemId == null || spec.itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(spec.itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, 1);
        if (spec.nbt != null && !spec.nbt.isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(spec.nbt));
            } catch (Exception ignored) {
            }
        }
        return stack;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
