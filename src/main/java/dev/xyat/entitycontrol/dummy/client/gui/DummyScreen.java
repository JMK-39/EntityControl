package dev.xyat.entitycontrol.dummy.client.gui;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.xyat.kineticcore.api.client.screen.KineticContainerScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.client.NotifyManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class DummyScreen extends KineticContainerScreen<DummyMenu> {
    private static final ResourceLocation TEXTURE = KineticResourceIds.of(DummyModule.MODID, "textures/gui/dummy_gui.png");
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
    }

    @Override
    protected void buildUi() {

        currentIFrames = this.menu.entity.hasIFrames();
        currentHealthDrop = this.menu.entity.isHealthDropEnabled();
        currentEnvironmentDamage = this.menu.entity.isEnvironmentDamageEnabled();

        if (lastEntityId != this.menu.entity.getId()) {
            tempAttribute = "";
            tempValue = "0.0";
            lastEntityId = this.menu.entity.getId();
        }

        this.attributeInput = addAutoCompleteField(
                this.leftPos + 21,
                this.topPos + 39,
                100,
                Component.translatable("gui.entitycontrol.dummy.dummy.attribute"),
                null,
                this::getDict,
                null
        );
        // Keep the whole registry searchable while displaying only five rows at a time.
        // The API's scrollbar remains visible for the remaining suggestions.
        this.attributeInput.setMaxVisibleSuggestions(5);
        this.attributeInput.setSuggestionPopupMaxWidth(300);
        this.attributeInput.setValue(tempAttribute);
        this.attributeInput.setResponder(text -> {
            tempAttribute = text;
            this.attributeInput.loadSuggestions();
            ResourceLocation id = KineticResourceIds.tryParse(text);
            if (id == null) return;

            Attribute attribute = KineticRegistries.attributes().get(id);
            if (attribute == null
                    || !id.equals(KineticRegistries.attributes().id(attribute))) {
                return;
            }

            double currentBaseValue = this.menu.entity.getAttribute(attribute) != null
                    ? this.menu.entity.getAttributeBaseValue(attribute) : attribute.getDefaultValue();
            if (this.valueInput != null) {
                String newValue = String.format("%.1f", currentBaseValue);
                this.valueInput.setValue(newValue);
                tempValue = newValue;
            }
        });

        this.valueInput = addDecimalField(
                leftPos + 21,
                topPos + 76,
                60,
                Component.translatable("gui.entitycontrol.dummy.dummy.value"),
                true,
                null,
                null,
                null
        );
        this.valueInput.setValue(tempValue);
        this.valueInput.setResponder(text -> tempValue = text);

        int rightX = this.leftPos + 133;
        addButton(
                rightX, this.topPos + 8, 50,
                Component.translatable("gui.entitycontrol.dummy.dummy.apply"),
                null,
                this::applyAttribute
        );

        StateButton curiosButton = addButton(
                rightX, this.topPos + 26, 50,
                Component.translatable("gui.entitycontrol.dummy.dummy.curios_ext"),
                null,
                () -> KineticClientRuntime.openScreen(new CuriosScreen(this, this.menu))
        );
        curiosButton.setEnabled(CuriosCompat.isAvailable());

        addButtonWithHandler(
                rightX, this.topPos + 59, 50,
                getMobTypeName(this.menu.entity.getCustomMobTypeId()),
                null,
                btn -> {
                    int nextType = (this.menu.entity.getCustomMobTypeId() + 1) % 5;
                    this.menu.entity.setCustomMobType(nextType);
                    btn.setText(getMobTypeName(nextType));
                    sendUpdatePacket(new CompoundTag());
                }
        );

        addButtonWithHandler(
                rightX, this.topPos + 77, 50,
                getIFramesText(currentIFrames),
                null,
                btn -> {
                    currentIFrames = !currentIFrames;
                    btn.setText(getIFramesText(currentIFrames));
                    sendUpdatePacket(new CompoundTag());
                }
        );

        addButtonWithHandler(
                rightX - 52, this.topPos + 95, 50,
                getEnvironmentDamageText(currentEnvironmentDamage),
                Component.translatable("tip.entitycontrol.dummy.dummy.environment_damage"),
                btn -> {
                    currentEnvironmentDamage = !currentEnvironmentDamage;
                    btn.setText(getEnvironmentDamageText(currentEnvironmentDamage));
                    sendUpdatePacket(new CompoundTag());
                }
        );

        addButtonWithHandler(
                rightX, this.topPos + 95, 50,
                getHealthDropText(currentHealthDrop),
                null,
                btn -> {
                    currentHealthDrop = !currentHealthDrop;
                    btn.setText(getHealthDropText(currentHealthDrop));
                    sendUpdatePacket(new CompoundTag());
                }
        );
    }

    private List<KineticAutoComplete.Suggestion> getDict() {
        return KineticRegistries.attributes().entries().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> {
                    String id = entry.getKey().toString();
                    String descId = entry.getValue().getDescriptionId();
                    Component translation = Component.translatable(descId);
                    String translated = translation.getString();
                    if (translated.equals(descId) || translated.isEmpty() || translated.startsWith("attribute.")) {
                        translation = Component.empty();
                    }
                    return new KineticAutoComplete.Suggestion(id, translation);
                })
                .sorted((left, right) -> left.value().compareTo(right.value()))
                .toList();
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

    private Component getMobTypeName(int id) {
        return switch (id) {
            case 1 -> Component.translatable("mob_type.entitycontrol.dummy.undead");
            case 2 -> Component.translatable("mob_type.entitycontrol.dummy.arthropod");
            case 3 -> Component.translatable("mob_type.entitycontrol.dummy.illager");
            case 4 -> Component.translatable("mob_type.entitycontrol.dummy.water");
            default -> Component.translatable("mob_type.entitycontrol.dummy.normal");
        };
    }

    private void applyAttribute() {
        if (attributeInput == null || valueInput == null) return;

        String attributeName = attributeInput.getValue();
        ResourceLocation id = KineticResourceIds.tryParse(attributeName);
        Double value = valueInput.getDoubleValue();

        if (id == null || value == null) {
            NotifyManager.notify(Component.translatable("msg.entitycontrol.dummy.invalid_number"));
            return;
        }

        Attribute attribute = KineticRegistries.attributes().get(id);
        if (attribute == null
                || !id.equals(KineticRegistries.attributes().id(attribute))
                || !Double.isFinite(value)
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
    protected void renderBg(@Nonnull GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // The dedicated texture already contains both equipment and player inventory.
        // Rendering a narrower vanilla inventory over its bottom half splits the frame.
        gui.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, 200, 200, 200, 200);

        for (int i = 0; i < Math.min(6, this.menu.slots.size()); i++) {
            Slot slot = this.menu.slots.get(i);
            if (slot.isActive()) {
                GuiTheme.itemSlot(gui, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, slot == this.hoveredSlot);
            }
        }

        for (int i = 0; i < 6; i++) {
            if (this.menu.slots.get(i).getItem().isEmpty()) {
                ResourceLocation icon = switch (i) {
                    case 0 -> KineticResourceIds.of("minecraft", "textures/item/empty_armor_slot_helmet.png");
                    case 1 -> KineticResourceIds.of("minecraft", "textures/item/empty_armor_slot_chestplate.png");
                    case 2 -> KineticResourceIds.of("minecraft", "textures/item/empty_armor_slot_leggings.png");
                    case 3 -> KineticResourceIds.of("minecraft", "textures/item/empty_armor_slot_boots.png");
                    case 4 -> KineticResourceIds.of("minecraft", "textures/item/empty_slot_sword.png");
                    case 5 -> KineticResourceIds.of("minecraft", "textures/item/empty_armor_slot_shield.png");
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

        // Neutral tint: the language keys own these labels' colors.
        graphics.drawString(font, Component.translatable("gui.entitycontrol.dummy.dummy.attribute"), leftPos + 25, topPos + 27, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.dummy.dummy.value"), leftPos + 25, topPos + 65, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.dummy.dummy.inventory"), leftPos + 25, topPos + 99, 0xFFFFFF, false);

    }
}
