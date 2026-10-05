package dev.xyat.entitycontrol.dummy.client.gui;

import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.client.NotifyManager;
import dev.xyat.kineticcore.api.client.gui.page.KineticContainerPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticAutoCompleteField;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.search.KineticSuggestion;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.inventory.Slot;

import java.util.Comparator;
import java.util.List;

public class DummyScreen extends KineticContainerPage<DummyMenu> {
    private static final KineticTexture TEXTURE = KineticTexture.of(
            DummyModule.MODID,
            "textures/gui/dummy_gui.png",
            200,
            200
    );
    private static final KineticTexture EMPTY_HELMET = KineticTexture.of("minecraft", "textures/item/empty_armor_slot_helmet.png", 16, 16);
    private static final KineticTexture INVENTORY_TEXTURE = KineticTexture.of("minecraft", "textures/gui/container/generic_54.png", 256, 256);
    private static final KineticTexture EMPTY_CHEST = KineticTexture.of("minecraft", "textures/item/empty_armor_slot_chestplate.png", 16, 16);
    private static final KineticTexture EMPTY_LEGS = KineticTexture.of("minecraft", "textures/item/empty_armor_slot_leggings.png", 16, 16);
    private static final KineticTexture EMPTY_BOOTS = KineticTexture.of("minecraft", "textures/item/empty_armor_slot_boots.png", 16, 16);
    private static final KineticTexture EMPTY_SWORD = KineticTexture.of("minecraft", "textures/item/empty_slot_sword.png", 16, 16);
    private static final KineticTexture EMPTY_SHIELD = KineticTexture.of("minecraft", "textures/item/empty_armor_slot_shield.png", 16, 16);

    // Labels stop before controls in the same row, with a four-pixel gap.
    private static final int LABEL_X = 25;
    private static final int RIGHT_CONTROL_X = 133;
    private static final int ENVIRONMENT_CONTROL_X = RIGHT_CONTROL_X - 52;
    private static final int TEXT_GAP = 4;

    private static int lastEntityId = -1;
    private static String tempAttribute = "";
    private static String tempValue = "0.0";

    private KineticAutoCompleteField attributeInput;
    private KineticNumberField valueInput;

    private boolean currentIFrames;
    private boolean currentHealthDrop;
    private boolean currentEnvironmentDamage;

    public DummyScreen(DummyMenu dummyMenu, Component title) {
        super(dummyMenu, title);
        setImageSize(200, 200);
        setInventoryLabelPosition(8, Integer.MIN_VALUE);
        setTitleLabelPosition(8, Integer.MIN_VALUE);
    }

    @Override
    protected void build(KineticUi ui) {
        // Vanilla init resets the inventory label position after the page constructor.
        setInventoryLabelPosition(8, Integer.MIN_VALUE);
        setTitleLabelPosition(8, Integer.MIN_VALUE);
        currentIFrames = menu().entity.hasIFrames();
        currentHealthDrop = menu().entity.isHealthDropEnabled();
        currentEnvironmentDamage = menu().entity.isEnvironmentDamageEnabled();

        if (lastEntityId != menu().entity.getId()) {
            tempAttribute = "";
            tempValue = "0.0";
            lastEntityId = menu().entity.getId();
        }

        this.attributeInput = ui.autoComplete(
                        leftPos() + 21,
                        topPos() + 39,
                        100,
                        this::getDict
                )
                .label(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.attribute"))
                .value(tempAttribute)
                .onChange(this::onAttributeChanged)
                .firstShownTextAsDefault().build();
        this.attributeInput.setMaxVisibleSuggestions(5);
        this.attributeInput.setSuggestionPopupMaxWidth(300);

        this.valueInput = ui.numberField(leftPos() + 21, topPos() + 76, 60, NumberType.DECIMAL)
                .label(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.value"))
                .allowNegative(true)
                .onChange(text -> tempValue = text)
                .firstShownTextAsDefault()
                .build();
        this.valueInput.setTextValue(tempValue);

        int rightX = leftPos() + RIGHT_CONTROL_X;
        ui.button(rightX, topPos() + 8, 50)
                .text(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.apply"))
                .onClick(this::applyAttribute)
                .build();

        KineticButton curiosButton = ui.button(rightX, topPos() + 26, 50)
                .text(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.curios_ext"))
                .onClick(() -> openChild(new CuriosScreen(menu())))
                .build();
        curiosButton.setEnabled(CuriosCompat.isAvailable());

        ui.button(rightX, topPos() + 59, 50)
                .text(getMobTypeName(menu().entity.getCustomMobTypeId()))
                .onClick(button -> {
                    int nextType = (menu().entity.getCustomMobTypeId() + 1) % 5;
                    menu().entity.setCustomMobType(nextType);
                    button.setText(getMobTypeName(nextType));
                    sendUpdatePacket(new CompoundTag());
                })
                .build();

        ui.button(rightX, topPos() + 77, 50)
                .text(getIFramesText(currentIFrames))
                .onClick(button -> {
                    currentIFrames = !currentIFrames;
                    button.setText(getIFramesText(currentIFrames));
                    sendUpdatePacket(new CompoundTag());
                })
                .build();

        ui.button(rightX - 52, topPos() + 95, 50)
                .text(getEnvironmentDamageText(currentEnvironmentDamage))
                .tooltip(KineticI18n.translatable("tip.entitycontrol.dummy.dummy.environment_damage"))
                .onClick(button -> {
                    currentEnvironmentDamage = !currentEnvironmentDamage;
                    button.setText(getEnvironmentDamageText(currentEnvironmentDamage));
                    sendUpdatePacket(new CompoundTag());
                })
                .build();

        ui.button(rightX, topPos() + 95, 50)
                .text(getHealthDropText(currentHealthDrop))
                .onClick(button -> {
                    currentHealthDrop = !currentHealthDrop;
                    button.setText(getHealthDropText(currentHealthDrop));
                    sendUpdatePacket(new CompoundTag());
                })
                .build();
    }

    private void onAttributeChanged(String text) {
        tempAttribute = text;
        ResourceLocation id = KineticResourceIds.tryParse(text);
        if (id == null) return;

        Attribute attribute = KineticRegistries.attributes().get(id);
        if (attribute == null || !id.equals(KineticRegistries.attributes().id(attribute))) return;

//? if >=1.21 {
/*        double currentBaseValue = menu().entity.getAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute)) != null
*///?} else {
        double currentBaseValue = menu().entity.getAttribute(attribute) != null
//?}
//? if >=1.21 {
/*                ? menu().entity.getAttributeBaseValue(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute))
*///?} else {
                ? menu().entity.getAttributeBaseValue(attribute)
//?}
                : attribute.getDefaultValue();
        if (this.valueInput != null) {
            String newValue = String.format("%.1f", currentBaseValue);
            this.valueInput.setTextValue(newValue);
            tempValue = newValue;
        }
    }

    private List<KineticSuggestion> getDict() {
        return KineticRegistries.attributes().entries().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> {
                    String id = entry.getKey().toString();
                    String descId = entry.getValue().getDescriptionId();
                    Component translation = KineticI18n.translatable(descId);
                    String translated = translation.getString();
                    if (translated.equals(descId) || translated.isEmpty() || translated.startsWith("attribute.")) {
                        translation = Component.empty();
                    }
                    return new KineticSuggestion(id, translation);
                })
                .sorted(Comparator.comparing(KineticSuggestion::value))
                .toList();
    }

    private Component getIFramesText(boolean enabled) {
        return KineticI18n.translatable(enabled
                ? "gui.entitycontrol.dummy.dummy.iframes.on"
                : "gui.entitycontrol.dummy.dummy.iframes.off");
    }

    private Component getHealthDropText(boolean enabled) {
        return KineticI18n.translatable(enabled
                ? "gui.entitycontrol.dummy.dummy.health_drop.on"
                : "gui.entitycontrol.dummy.dummy.health_drop.off");
    }

    private Component getEnvironmentDamageText(boolean enabled) {
        return KineticI18n.translatable(enabled
                ? "gui.entitycontrol.dummy.dummy.environment_damage.on"
                : "gui.entitycontrol.dummy.dummy.environment_damage.off");
    }

    private void sendUpdatePacket(CompoundTag tag) {
        DummyNetwork.sendToServer(new DummyNetwork.UpdateV2(
                menu().containerId,
                menu().entity.getId(),
                menu().entity.getCustomMobTypeId(),
                tag,
                currentIFrames,
                currentHealthDrop,
                currentEnvironmentDamage
        ));
    }

    private Component getMobTypeName(int id) {
        return switch (id) {
            case 1 -> KineticI18n.translatable("mob_type.entitycontrol.dummy.undead");
            case 2 -> KineticI18n.translatable("mob_type.entitycontrol.dummy.arthropod");
            case 3 -> KineticI18n.translatable("mob_type.entitycontrol.dummy.illager");
            case 4 -> KineticI18n.translatable("mob_type.entitycontrol.dummy.water");
            default -> KineticI18n.translatable("mob_type.entitycontrol.dummy.normal");
        };
    }

    private void applyAttribute() {
        if (attributeInput == null || valueInput == null) return;

        String attributeName = attributeInput.textValue();
        ResourceLocation id = KineticResourceIds.tryParse(attributeName);
        Double value = valueInput.getDoubleValue();

        if (id == null || value == null) {
            NotifyManager.notify(KineticI18n.translatable("msg.entitycontrol.dummy.invalid_number"));
            return;
        }

        Attribute attribute = KineticRegistries.attributes().get(id);
        if (attribute == null
                || !id.equals(KineticRegistries.attributes().id(attribute))
                || !Double.isFinite(value)
                || value != attribute.sanitizeValue(value)) {
            NotifyManager.notify(KineticI18n.translatable("msg.entitycontrol.dummy.invalid_number"));
            return;
        }

        CompoundTag tag = new CompoundTag();
        tag.putDouble(attributeName, value);
        sendUpdatePacket(tag);

        tempAttribute = "";
        tempValue = "0.0";
        attributeInput.setTextValue(tempAttribute);
        valueInput.setTextValue(tempValue);
    }

    @Override
    protected void renderContainerBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the original equipment panel above the vanilla player inventory background.
        graphics.fill(leftPos(), topPos(), leftPos() + 200, topPos() + 200, 0xFFC6C6C6);
        graphics.texture(TEXTURE, leftPos(), topPos(), 0, 0, 200, 101);
        graphics.texture(INVENTORY_TEXTURE, leftPos() + 13, topPos() + 101, 0, 126, 176, 90);

        for (int i = 0; i < Math.min(6, menu().slots.size()); i++) {
            Slot slot = menu().slots.get(i);
            if (slot.isActive()) {
                KineticTheme.itemSlot(
                        graphics,
                        leftPos() + slot.x - 1,
                        topPos() + slot.y - 1,
                        slot == hoveredSlot()
                );
            }
        }

        for (int i = 0; i < Math.min(6, menu().slots.size()); i++) {
            if (menu().slots.get(i).getItem().isEmpty()) {
                KineticTexture icon = switch (i) {
                    case 0 -> EMPTY_HELMET;
                    case 1 -> EMPTY_CHEST;
                    case 2 -> EMPTY_LEGS;
                    case 3 -> EMPTY_BOOTS;
                    case 4 -> EMPTY_SWORD;
                    case 5 -> EMPTY_SHIELD;
                    default -> null;
                };
                graphics.texture(icon, leftPos() + 21 + i * 18, topPos() + 8, 0, 0, 16, 16);
            }
        }
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int textColor = KineticTheme.current().text();
        graphics.scrollingText(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.attribute"), leftPos() + LABEL_X, topPos() + 27, RIGHT_CONTROL_X - LABEL_X - TEXT_GAP, textColor, false);
        graphics.scrollingText(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.value"), leftPos() + LABEL_X, topPos() + 65, RIGHT_CONTROL_X - LABEL_X - TEXT_GAP, textColor, false);
        graphics.scrollingText(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.inventory"), leftPos() + LABEL_X, topPos() + 99, ENVIRONMENT_CONTROL_X - LABEL_X - TEXT_GAP, textColor, false);
    }
}
