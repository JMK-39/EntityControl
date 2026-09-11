package dev.xyat.entitycontrol.modifier.client.gui.panel;

import net.minecraft.ChatFormatting;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Objects;
import java.util.Map;

public class AttributePanel extends AbstractModifierScrollPanel<Attribute> {
    private EditBox attrValueBox;
    private String selectedAttribute = null;

    @Override
    protected void initExtra() {
        attrValueBox = new EditBox(parent.getFont(), x + w - 60, y + h - 20, 55, 20, Component.empty());
        attrValueBox.setResponder(s -> {
            if (selectedEntityId != null && selectedAttribute != null && !s.isEmpty()) {
                try {
                    parent.getLocalData().computeIfAbsent(selectedEntityId, k -> new EntityModifierConfig.EntityEditData()).attributes.put(selectedAttribute, Double.parseDouble(s));
                } catch (Exception ignored) {}
            }
        });
        parent.addPanelWidget(attrValueBox);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (attrValueBox != null) attrValueBox.visible = visible;
    }

    @Override
    public void onEntitySelected(String entityId, net.minecraft.world.entity.LivingEntity previewEntity) {
        this.selectedAttribute = null;
        if (attrValueBox != null) attrValueBox.setValue("");
        super.onEntitySelected(entityId, previewEntity);
    }

    private boolean isAttrModified(String attrId, Attribute attr) {
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            Map<String, Double> attrs = parent.getLocalData().get(selectedEntityId).attributes;
            if (attrs.containsKey(attrId)) {
                double defaultVal = previewEntity != null && previewEntity.getAttributes().hasAttribute(attr) ? previewEntity.getAttributes().getBaseValue(attr) : attr.getDefaultValue();
                return Math.abs(attrs.get(attrId) - defaultVal) > 0.0001;
            }
        }
        return false;
    }

    private String getReadableName(Attribute attr, ResourceLocation rl) {
        String transName = Component.translatable(attr.getDescriptionId()).getString();
        if (transName.equals(attr.getDescriptionId()) && rl != null) {
            String[] parts = rl.getPath().replace("_", " ").split("\\.");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) for (String word : p.split("_")) if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            return sb.toString().trim();
        }
        return transName;
    }

    @Override
    protected void updateSearch(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        displayList = ForgeRegistries.ATTRIBUTES.getValues().stream().filter(a -> {
            ResourceLocation rl = ForgeRegistries.ATTRIBUTES.getKey(a);
            return rl != null && (q.isEmpty() || rl.toString().contains(q) || getReadableName(a, rl).toLowerCase(Locale.ROOT).contains(q));
        }).sorted((a, b) -> {
            boolean modA = isAttrModified(Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getKey(a)).toString(), a);
            boolean modB = isAttrModified(Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getKey(b)).toString(), b);
            if (modA != modB) return modA ? -1 : 1;
            return Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getKey(a)).toString().compareTo(Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getKey(b)).toString());
        }).toList();
        refreshScroll();
    }

    @Override protected int getListHeight() { return h - 50; }
    @Override protected Component getSearchHint() { return Component.translatable("gui.entitycontrol.modifier.modifier.search_attr"); }

    @Override
    protected void renderRow(GuiGraphics g, Attribute attr, int rowY, int mx, int my) {
        ResourceLocation rl = ForgeRegistries.ATTRIBUTES.getKey(attr);
        String attrId = rl != null ? rl.toString() : "";

        if (attrId.equals(selectedAttribute)) {
            g.fill(x, rowY, x + w - 10, rowY + 20, 0x66777777);
        } else if (mx >= x && mx < x + w - 10 && my >= rowY && my < rowY + 20) {
            g.fill(x, rowY, x + w - 10, rowY + 20, 0x33FFFFFF);
        }

        String namespace = rl != null ? rl.getNamespace() : "minecraft";
        Component namespaceText = Component.literal("[" + namespace + "]")
                .withStyle(namespace.equals("minecraft") ? ChatFormatting.GREEN : ChatFormatting.BLUE);
        g.drawString(parent.getFont(), namespaceText, x + 5, rowY + 6, 0xFFFFFF);

        String attrName = getReadableName(attr, rl);
        int maxW = w - 95 - parent.getFont().width("[" + namespace + "] ");
        if (parent.getFont().width(attrName) > maxW) {
            attrName = parent.getFont().plainSubstrByWidth(attrName, maxW) + "..";
        }
        Component attrNameText = Component.translatable("gui.entitycontrol.modifier.modifier.name", Component.literal(attrName).withStyle(ChatFormatting.GOLD));
        g.drawString(
                parent.getFont(),
                attrNameText,
                x + 5 + parent.getFont().width("[" + namespace + "] "),
                rowY + 6,
                0xFFFFFF
        );

        double displayVal = previewEntity != null && previewEntity.getAttributes().hasAttribute(attr)
                ? previewEntity.getAttributes().getBaseValue(attr)
                : attr.getDefaultValue();
        boolean isTrulyModified = isAttrModified(attrId, attr);

        if (selectedEntityId != null
                && parent.getLocalData().containsKey(selectedEntityId)
                && parent.getLocalData().get(selectedEntityId).attributes.containsKey(attrId)) {
            displayVal = parent.getLocalData().get(selectedEntityId).attributes.get(attrId);
        }

        String valStr = String.format("%.2f", displayVal);
        Component valueText = Component.translatable(
                isTrulyModified
                        ? "gui.entitycontrol.modifier.modifier.value.modified"
                        : "gui.entitycontrol.modifier.modifier.value.default",
                Component.literal(valStr).withStyle(isTrulyModified ? ChatFormatting.GREEN : ChatFormatting.GRAY)
        );
        g.drawString(
                parent.getFont(),
                valueText,
                x + w - 15 - parent.getFont().width(valueText),
                rowY + 6,
                0xFFFFFF
        );
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mx, int my) {
        Component editValComp = Component.translatable("gui.entitycontrol.modifier.modifier.edit_val");
        g.drawString(parent.getFont(), editValComp, attrValueBox.getX() - parent.getFont().width(editValComp) - 5, y + h - 14, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    protected boolean onRowClicked(Attribute attr, double mx, double my) {
        selectedAttribute = Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getKey(attr)).toString();
        double displayVal = previewEntity != null && previewEntity.getAttributes().hasAttribute(attr) ? previewEntity.getAttributes().getBaseValue(attr) : attr.getDefaultValue();
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId) && parent.getLocalData().get(selectedEntityId).attributes.containsKey(selectedAttribute)) displayVal = parent.getLocalData().get(selectedEntityId).attributes.get(selectedAttribute);
        attrValueBox.setValue(String.valueOf(displayVal)); return true;
    }
}
