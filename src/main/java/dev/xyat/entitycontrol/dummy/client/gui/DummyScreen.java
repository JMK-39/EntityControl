package dev.xyat.entitycontrol.dummy.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.xyat.kineticcore.api.client.screen.KineticContainerScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.client.NotifyManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DummyScreen extends KineticContainerScreen<DummyMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(DummyModule.MODID, "textures/gui/dummy_gui.png");
    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final Set<Attribute> EDITABLE_ATTRIBUTES = Set.of(
            Attributes.MAX_HEALTH,
            Attributes.KNOCKBACK_RESISTANCE,
            Attributes.MOVEMENT_SPEED,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS
    );

    private static int lastEntityId = -1;
    private static String tempAttribute = "";
    private static String tempValue = "0.0";

    private AutoCompleteBox attributeInput;
    private NumericEditBox valueInput;

    private boolean currentIFrames;
    private boolean currentHealthDrop;
    private boolean currentEnvironmentDamage;

    public DummyScreen(DummyMenu dummyMenu, Inventory inventory, Component title) {
        super(dummyMenu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 200;
        this.inventoryLabelY = Integer.MIN_VALUE;
        this.titleLabelY = Integer.MIN_VALUE;
        useResponsiveContainer(640f, 360f, 6);
    }

    @Override
    protected void init() {
        super.init();

        currentIFrames = this.menu.entity.hasIFrames();
        currentHealthDrop = this.menu.entity.isHealthDropEnabled();
        currentEnvironmentDamage = this.menu.entity.isEnvironmentDamageEnabled();

        if (lastEntityId != this.menu.entity.getId()) {
            tempAttribute = "";
            tempValue = "0.0";
            lastEntityId = this.menu.entity.getId();
        }

        this.attributeInput = new AutoCompleteBox(
                this.font,
                this.leftPos + 25,
                this.topPos + 41,
                100,
                12,
                Component.translatable("gui.entitycontrol.dummy.dummy.attribute"),
                this::getDict
        );
        this.attributeInput.setBordered(false);
        this.attributeInput.setTextColor(0xFFFFFF);
        this.attributeInput.setValue(tempAttribute);
        this.attributeInput.setResponder(text -> {
            tempAttribute = text;
            this.attributeInput.loadSuggestions();
            ResourceLocation id = ResourceLocation.tryParse(AutoCompleteBox.normalizeValue(text));
            if (id == null) return;

            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
            if (attribute == null
                    || !EDITABLE_ATTRIBUTES.contains(attribute)
                    || this.menu.entity.getAttribute(attribute) == null) {
                return;
            }

            double currentBaseValue = this.menu.entity.getAttributeBaseValue(attribute);
            if (this.valueInput != null) {
                String newValue = String.format("%.1f", currentBaseValue);
                this.valueInput.setValue(newValue);
                tempValue = newValue;
            }
        });
        this.addRenderableWidget(attributeInput);

        this.valueInput = NumericEditBox.decimal(
                font,
                leftPos + 25,
                topPos + 70,
                60,
                12,
                Component.translatable("gui.entitycontrol.dummy.dummy.value"),
                true,
                null,
                null
        );
        this.valueInput.setBordered(false);
        this.valueInput.setValue(tempValue);
        this.valueInput.setTextColor(0xFFFFFF);
        this.valueInput.setResponder(text -> tempValue = text);
        addRenderableWidget(valueInput);

        int rightX = this.leftPos + 133;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.entitycontrol.dummy.dummy.apply"), btn -> applyAttribute())
                .pos(rightX, this.topPos + 8).size(50, 16).build());

        Button curiosButton = Button.builder(Component.translatable("gui.entitycontrol.dummy.dummy.curios_ext"), btn -> {
            if (this.minecraft != null) this.minecraft.setScreen(new CuriosScreen(this, this.menu));
        }).pos(rightX, this.topPos + 26).size(50, 16).build();
        curiosButton.active = CuriosCompat.isAvailable();
        this.addRenderableWidget(curiosButton);

        this.addRenderableWidget(Button.builder(getMobTypeName(this.menu.entity.getCustomMobTypeId()), btn -> {
            int nextType = (this.menu.entity.getCustomMobTypeId() + 1) % 5;
            this.menu.entity.setCustomMobType(nextType);
            btn.setMessage(getMobTypeName(nextType));
            sendUpdatePacket(new CompoundTag());
        }).pos(rightX, this.topPos + 59).size(50, 16).build());

        this.addRenderableWidget(Button.builder(getIFramesText(currentIFrames), btn -> {
            currentIFrames = !currentIFrames;
            btn.setMessage(getIFramesText(currentIFrames));
            sendUpdatePacket(new CompoundTag());
        }).pos(rightX, this.topPos + 77).size(50, 16).build());

        this.addRenderableWidget(Button.builder(getEnvironmentDamageText(currentEnvironmentDamage), btn -> {
                    currentEnvironmentDamage = !currentEnvironmentDamage;
                    btn.setMessage(getEnvironmentDamageText(currentEnvironmentDamage));
                    sendUpdatePacket(new CompoundTag());
                }).pos(rightX - 52, this.topPos + 95).size(50, 16)
                .tooltip(Tooltip.create(Component.translatable("tip.entitycontrol.dummy.dummy.environment_damage")))
                .build());

        this.addRenderableWidget(Button.builder(getHealthDropText(currentHealthDrop), btn -> {
            currentHealthDrop = !currentHealthDrop;
            btn.setMessage(getHealthDropText(currentHealthDrop));
            sendUpdatePacket(new CompoundTag());
        }).pos(rightX, this.topPos + 95).size(50, 16).build());
    }

    private List<String> getDict() {
        return ForgeRegistries.ATTRIBUTES.getEntries().stream()
                .filter(entry -> EDITABLE_ATTRIBUTES.contains(entry.getValue()))
                .map(e -> {
                    String id = e.getKey().location().toString();
                    String descId = e.getValue().getDescriptionId();
                    String translated = Component.translatable(descId).getString();
                    return translated.equals(descId) || translated.isEmpty() || translated.startsWith("attribute.")
                            ? id
                            : id + " - " + translated;
                })
                .sorted()
                .collect(Collectors.toList());
    }

    private Component getIFramesText(boolean enabled) {
        return Component.translatable(enabled ? "gui.entitycontrol.dummy.dummy.iframes.on" : "gui.entitycontrol.dummy.dummy.iframes.off");
    }

    private Component getHealthDropText(boolean enabled) {
        return Component.translatable(enabled ? "gui.entitycontrol.dummy.dummy.health_drop.on" : "gui.entitycontrol.dummy.dummy.health_drop.off");
    }

    private Component getEnvironmentDamageText(boolean enabled) {
        return Component.translatable(enabled ? "gui.entitycontrol.dummy.dummy.environment_damage.on" : "gui.entitycontrol.dummy.dummy.environment_damage.off");
    }

    private void sendUpdatePacket(CompoundTag tag) {
        DummyNetwork.sendToServer(new DummyNetwork.UpdateV2(
                this.menu.containerId,
                this.menu.entity.getId(),
                this.menu.entity.getCustomMobTypeId(),
                tag,
                currentIFrames,
                currentHealthDrop,
                currentEnvironmentDamage
        ));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.attributeInput != null && this.attributeInput.handleKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (attributeInput != null
                && attributeInput.handleMouseClick(toVirtualX(mouseX), toVirtualY(mouseY))) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (attributeInput != null && attributeInput.handleMouseReleased(button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (attributeInput != null
                && attributeInput.handleMouseDragged(toVirtualX(mouseX), toVirtualY(mouseY))) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (attributeInput != null && attributeInput.handleMouseScrolled(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private Component getMobTypeName(int id) {
        return switch (id) {
            case 1 -> Component.translatable("mob_type.entitycontrol.undead");
            case 2 -> Component.translatable("mob_type.entitycontrol.arthropod");
            case 3 -> Component.translatable("mob_type.entitycontrol.illager");
            case 4 -> Component.translatable("mob_type.entitycontrol.water");
            default -> Component.translatable("mob_type.entitycontrol.normal");
        };
    }

    private void applyAttribute() {
        if (attributeInput == null || valueInput == null) return;

        String attributeName = AutoCompleteBox.normalizeValue(attributeInput.getValue());
        ResourceLocation id = ResourceLocation.tryParse(attributeName);
        Double value = valueInput.getDoubleValue();

        if (id == null || value == null) {
            NotifyManager.notify(Component.translatable("msg.entitycontrol.dummy.invalid_number"));
            return;
        }

        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
        if (attribute == null
                || !EDITABLE_ATTRIBUTES.contains(attribute)
                || !Double.isFinite(value)
                || !(attribute instanceof RangedAttribute range)
                || value < range.getMinValue()
                || value > range.getMaxValue()
                || value != attribute.sanitizeValue(value)) {
            NotifyManager.notify(Component.translatable("msg.entitycontrol.dummy.invalid_number"));
            return;
        }

        CompoundTag tag = new CompoundTag();
        tag.putDouble(attributeName, value);
        sendUpdatePacket(tag);

        tempAttribute = "";
        tempValue = "0.0";
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFFC6C6C6);
        gui.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, 200, 101, 200, 200);
        gui.blit(INVENTORY_TEXTURE, this.leftPos + 14, this.topPos + 101, 0, 125, 176, 90, 256, 256);

        for (int i = 0; i < Math.min(6, this.menu.slots.size()); i++) {
            Slot slot = this.menu.slots.get(i);
            if (slot.isActive()) {
                GuiTheme.itemSlot(gui, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, slot == this.hoveredSlot);
            }
        }

        for (int i = 0; i < 6; i++) {
            if (this.menu.slots.get(i).getItem().isEmpty()) {
                ResourceLocation icon = switch (i) {
                    case 0 -> new ResourceLocation("minecraft", "textures/item/empty_armor_slot_helmet.png");
                    case 1 -> new ResourceLocation("minecraft", "textures/item/empty_armor_slot_chestplate.png");
                    case 2 -> new ResourceLocation("minecraft", "textures/item/empty_armor_slot_leggings.png");
                    case 3 -> new ResourceLocation("minecraft", "textures/item/empty_armor_slot_boots.png");
                    case 4 -> new ResourceLocation("minecraft", "textures/item/empty_slot_sword.png");
                    case 5 -> new ResourceLocation("minecraft", "textures/item/empty_armor_slot_shield.png");
                    default -> null;
                };
                if (icon != null) {
                    gui.blit(icon, this.leftPos + 21 + i * 18, this.topPos + 8, 0, 0, 16, 16, 16, 16);
                }
            }
        }
    }

    @Override
    protected void renderUiForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        NotifyManager.renderAt(graphics, uiWidth() / 2, topPos + imageHeight + 15);

        int color = 0x404040;
        graphics.drawString(font, Component.translatable("gui.entitycontrol.dummy.dummy.attribute"), leftPos + 21, topPos + 27, color, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.dummy.dummy.value"), leftPos + 21, topPos + 56, color, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.dummy.dummy.inventory"), leftPos + 21, topPos + 100, color, false);

        if (attributeInput != null) {
            attributeInput.renderSuggestions(graphics, mouseX, mouseY);
        }
    }
}
