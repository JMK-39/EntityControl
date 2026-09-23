package dev.xyat.entitycontrol.modifier.client.gui.panel;

import net.minecraft.ChatFormatting;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.client.gui.GuiGraphics;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.entitycontrol.modifier.client.gui.AttributeSelectionOverlay;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;

import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class AttributePanel extends AbstractModifierScrollPanel<Attribute> {
    private KineticEditBox attrValueBox;
    private String selectedAttribute = null;
    private StateButton modeButton;
    private static final List<String> MODES = List.of("SET", "MULTIPLY", "ADD", "SUBTRACT");
    private static final List<String> COMMON_ATTRIBUTES = List.of(
            "minecraft:generic.max_health",
            "minecraft:generic.attack_damage",
            "minecraft:generic.movement_speed",
            "minecraft:generic.armor",
            "minecraft:generic.armor_toughness",
            "minecraft:generic.attack_speed",
            "minecraft:generic.follow_range",
            "minecraft:generic.knockback_resistance"
    );
    private String selectedMode;
    private boolean loadingValue;
    private StateButton deleteButton;
    private StateButton targetsButton;

    private EntityModifierConfig.AttributeRule putRule(double value) {
        EntityModifierConfig.EntityEditData data = parent.getLocalData().computeIfAbsent(
                selectedEntityId, key -> new EntityModifierConfig.EntityEditData());
        data.attributes.remove(selectedAttribute);
        EntityModifierConfig.AttributeRule rule = data.attributeRules.computeIfAbsent(selectedAttribute,
                key -> new EntityModifierConfig.AttributeRule(selectedMode, value));
        rule.mode = selectedMode;
        rule.value = value;
        return rule;
    }

    private EntityModifierConfig.AttributeRule ensureCurrentRule() {
        if (selectedAttribute == null || selectedEntityId == null || selectedMode == null) return null;
        Double value = attrValueBox instanceof dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox box
                ? box.getDoubleValue() : null;
        if (value == null) return null;
        return putRule(value);
    }

    public void toggleGlobalTarget(String id, List<String> allowedIds) {
        if (!parent.isGlobalMode() || selectedAttribute == null || id == null) return;
        EntityModifierConfig.AttributeRule rule = ensureCurrentRule();
        if (rule == null) return;
        if (rule.targetEntities == null) rule.targetEntities = new TreeSet<>(allowedIds);
        if (!rule.targetEntities.add(id)) rule.targetEntities.remove(id);
        updateTargetsButton();
    }

    public void restoreSelection(String id) {
        ResourceLocation key = dev.xyat.kineticcore.api.resource.KineticResourceIds.tryParse(id);
        Attribute attr = key == null ? null : KineticRegistries.attributes().get(key);
        if (attr != null) onRowClicked(attr, 0, 0, 0);
    }

    private void updateTargetsButton() {
        if (targetsButton == null) return;
        EntityModifierConfig.EntityEditData data = selectedEntityId == null
                ? null : parent.getLocalData().get(selectedEntityId);
        EntityModifierConfig.AttributeRule rule = data == null || selectedAttribute == null
                ? null : data.attributeRules.get(selectedAttribute);
        Component label = selectedAttribute == null
                ? Component.translatable("gui.entitycontrol.modifier.global.targets.choose_attribute")
                : selectedMode == null
                ? Component.translatable("gui.entitycontrol.modifier.global.targets.choose_operation")
                : rule == null || rule.targetEntities == null
                ? Component.translatable("gui.entitycontrol.modifier.global.targets.all")
                : Component.translatable("gui.entitycontrol.modifier.global.targets.selected", rule.targetEntities.size());
        targetsButton.setMessage(label);
        targetsButton.setEnabled(parent.isGlobalMode() && selectedAttribute != null && selectedMode != null);
    }

    private void chooseGlobalTargets() {
        if (!parent.isGlobalMode() || selectedAttribute == null) return;
        EntityModifierConfig.AttributeRule rule = ensureCurrentRule();
        if (rule == null) return;
        List<String> allowed = parent.selectableLivingEntityIds();
        List<String> current = rule.targetEntities == null ? allowed : new ArrayList<>(rule.targetEntities);
        KineticSelectors.openEntitySelector(parent,
                Component.translatable("gui.entitycontrol.modifier.global.targets"), current, allowed,
                selected -> {
                    EntityModifierConfig.EntityEditData data = parent.getLocalData().get(EntityModifierConfig.GLOBAL_KEY);
                    EntityModifierConfig.AttributeRule latest = data == null ? null
                            : data.attributeRules.get(selectedAttribute);
                    if (latest != null) {
                        latest.targetEntities = new TreeSet<>(selected);
                        // Selecting every available entity is the stable all-entities mode.
                        if (latest.targetEntities.containsAll(allowed) && allowed.containsAll(latest.targetEntities)) {
                            latest.targetEntities = null;
                        }
                        updateTargetsButton();
                    }
                });
        ResourceLocation attributeId = KineticResourceIds.tryParse(selectedAttribute);
        Attribute attribute = attributeId == null ? null : KineticRegistries.attributes().get(attributeId);
        Component name = attribute == null ? Component.literal(selectedAttribute)
                : Component.literal(getReadableName(attribute, attributeId));
        AttributeSelectionOverlay.show(KineticClientRuntime.currentScreen(), name, selectedAttribute,
                modeLabel(), Component.translatable("gui.entitycontrol.modifier.global.mode."
                        + selectedMode.toLowerCase(Locale.ROOT) + ".tooltip"));
    }

    private Component modeLabel() {
        if (selectedMode == null) {
            return Component.translatable("gui.entitycontrol.modifier.global.mode.choose");
        }
        return Component.translatable("gui.entitycontrol.modifier.global.mode." + selectedMode.toLowerCase(java.util.Locale.ROOT));
    }

    /** Opens the four available operations as a standard API menu instead of silently cycling. */
    private void showModeMenu() {
        if (selectedAttribute == null) return;
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        for (String mode : MODES) {
            if (parent.isGlobalMode() && "SET".equals(mode)) continue;
            Component label = Component.translatable(
                    "gui.entitycontrol.modifier.global.mode." + mode.toLowerCase(Locale.ROOT));
            Component help = Component.translatable(
                    "gui.entitycontrol.modifier.global.mode." + mode.toLowerCase(Locale.ROOT) + ".tooltip");
            entries.add(KineticOverlays.MenuItem.toggle(label, help,
                    mode.equals(selectedMode), () -> selectMode(mode)));
        }
        parent.openContextMenu(x + 4, y + h - 44, entries);
    }

    private void selectMode(String mode) {
        if (selectedEntityId == null || selectedAttribute == null || !MODES.contains(mode)
                || (parent.isGlobalMode() && "SET".equals(mode))) return;
        boolean changed = !mode.equals(selectedMode);
        selectedMode = mode;
        if (changed) {
            double value = switch (mode) {
                case "MULTIPLY" -> 1.0D;
                case "ADD", "SUBTRACT" -> 0.0D;
                default -> selectedBaseValue();
            };
            loadingValue = true;
            try { attrValueBox.setValue(String.valueOf(value)); }
            finally { loadingValue = false; }
            putRule(value);
        }
        updateControls();
        updateSearch(searchBox.getValue());
    }

    private double selectedBaseValue() {
        ResourceLocation id = KineticResourceIds.tryParse(selectedAttribute);
        Attribute attribute = id == null ? null : KineticRegistries.attributes().get(id);
        if (attribute == null) return 0.0D;
        return previewEntity != null && previewEntity.getAttributes().hasAttribute(attribute)
                ? previewEntity.getAttributes().getBaseValue(attribute) : attribute.getDefaultValue();
    }

    private void updateControls() {
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
            modeButton.setEnabled(selectedEntityId != null && selectedAttribute != null);
        }
        if (attrValueBox != null) {
            attrValueBox.setEnabled(selectedEntityId != null && selectedAttribute != null && selectedMode != null);
        }
        if (deleteButton != null) {
            EntityModifierConfig.EntityEditData data = selectedEntityId == null ? null
                    : parent.getLocalData().get(selectedEntityId);
            deleteButton.setEnabled(selectedAttribute != null && data != null
                    && (data.attributes.containsKey(selectedAttribute)
                    || data.attributeRules.containsKey(selectedAttribute)));
        }
        updateTargetsButton();
    }

    private void removeSelectedRule() {
        if (selectedEntityId == null || selectedAttribute == null) return;
        EntityModifierConfig.EntityEditData data = parent.getLocalData().get(selectedEntityId);
        if (data == null) return;
        data.attributes.remove(selectedAttribute);
        data.attributeRules.remove(selectedAttribute);
        selectedAttribute = null;
        if (parent.isGlobalMode()) parent.selectedGlobalAttribute(null);
        else parent.selectedIndividualAttribute(null);
        selectedMode = null;
        loadingValue = true;
        try { attrValueBox.setValue(""); } finally { loadingValue = false; }
        updateSearch(searchBox.getValue());
        updateControls();
    }

    @Override
    protected void initExtra() {
        modeButton = parent.addCompactButton(x + 4, y + h - 44, 120,
                modeLabel(), Component.translatable("gui.entitycontrol.modifier.global.mode.tooltip"),
                this::showModeMenu);
        deleteButton = parent.addCompactButton(x + 130, y + h - 44, 100,
                Component.translatable("gui.entitycontrol.modifier.global.delete_rule"),
                Component.translatable("gui.entitycontrol.modifier.global.delete_rule.tooltip"),
                this::removeSelectedRule);
        targetsButton = parent.addCompactButton(x + 234, y + h - 44, w - 238,
                Component.translatable("gui.entitycontrol.modifier.global.targets.all"),
                Component.translatable("gui.entitycontrol.modifier.global.targets.tooltip"),
                this::chooseGlobalTargets);
        attrValueBox = KineticWidgets.createDecimalField(
                parent.getFont(), x + w - 136, y + h - 20, 130,
                Component.empty(), true, -1.0E9D, 1.0E9D,
                number -> Double.isFinite(number.doubleValue()), null
        );
        attrValueBox.setResponder(s -> {
            if (!loadingValue && selectedEntityId != null && selectedAttribute != null) {
                var box = (dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox) attrValueBox;
                Double value = box.getDoubleValue();
                if (value != null) {
                    putRule(value);
                    updateSearch(searchBox.getValue());
                    updateControls();
                }
            }
        });
        parent.addControl(attrValueBox, null);
        updateControls();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (attrValueBox != null) attrValueBox.setVisible(visible);
        if (modeButton != null) modeButton.setVisible(visible);
        if (deleteButton != null) deleteButton.setVisible(visible);
        if (targetsButton != null) targetsButton.setVisible(visible && parent.isGlobalMode());
        updateControls();
    }

    @Override
    public void onEntitySelected(String entityId, net.minecraft.world.entity.LivingEntity previewEntity) {
        this.selectedAttribute = null;
        this.selectedMode = null;
        if (attrValueBox != null) {
            loadingValue = true;
            try { attrValueBox.setValue(""); } finally { loadingValue = false; }
        }
        super.onEntitySelected(entityId, previewEntity);
        updateControls();
    }

    private boolean isAttrModified(String attrId, Attribute attr) {
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            EntityModifierConfig.EntityEditData data = parent.getLocalData().get(selectedEntityId);
            if (data.attributeRules.containsKey(attrId)) return true;
            Map<String, Double> attrs = data.attributes;
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
        displayList = KineticRegistries.attributes().values().stream().filter(a -> {
            ResourceLocation rl = KineticRegistries.attributes().id(a);
            return rl != null && (q.isEmpty() || rl.toString().contains(q) || getReadableName(a, rl).toLowerCase(Locale.ROOT).contains(q));
        }).sorted((a, b) -> {
            int rankA = COMMON_ATTRIBUTES.indexOf(Objects.requireNonNull(KineticRegistries.attributes().id(a)).toString());
            int rankB = COMMON_ATTRIBUTES.indexOf(Objects.requireNonNull(KineticRegistries.attributes().id(b)).toString());
            if (rankA >= 0 || rankB >= 0) {
                if (rankA < 0) return 1;
                if (rankB < 0) return -1;
                return Integer.compare(rankA, rankB);
            }
            boolean modA = isAttrModified(Objects.requireNonNull(KineticRegistries.attributes().id(a)).toString(), a);
            boolean modB = isAttrModified(Objects.requireNonNull(KineticRegistries.attributes().id(b)).toString(), b);
            if (modA != modB) return modA ? -1 : 1;
            return Objects.requireNonNull(KineticRegistries.attributes().id(a)).toString().compareTo(Objects.requireNonNull(KineticRegistries.attributes().id(b)).toString());
        }).toList();
        refreshScroll();
    }

    @Override protected int getListHeight() { return h - 75; }
    @Override protected int rowStride() { return ROW_HEIGHT + 2; }
    @Override protected Component getSearchHint() { return Component.translatable("gui.entitycontrol.modifier.modifier.search_attr"); }

    @Override
    protected void renderRow(GuiGraphics g, Attribute attr, int rowY, int mx, int my) {
        ResourceLocation rl = KineticRegistries.attributes().id(attr);
        String attrId = rl != null ? rl.toString() : "";

        boolean selected = attrId.equals(selectedAttribute);
        int listTop = y + 28;
        int listBottom = listTop + getListHeight();
        boolean hovered = mx >= x + 4 && mx < x + w - 12
                && my >= listTop && my < listBottom
                && my >= rowY && my < rowY + ROW_HEIGHT;
        GuiTheme.stateSurface(g, x + 4, rowY, w - 16, ROW_HEIGHT,
                GuiTheme.Surface.PANEL_ALT, selected, hovered, false);

        String namespace = rl != null ? rl.getNamespace() : "minecraft";
        Component namespaceText = Component.literal("[" + namespace + "]")
                .withStyle(namespace.equals("minecraft") ? ChatFormatting.GREEN : ChatFormatting.BLUE);
        g.drawString(parent.getFont(), namespaceText, x + 8, rowY + 6, 0xFFFFFF);

        String attrName = getReadableName(attr, rl);
        int maxW = w - 95 - parent.getFont().width("[" + namespace + "] ");
        if (parent.getFont().width(attrName) > maxW) {
            attrName = parent.getFont().plainSubstrByWidth(attrName, maxW) + "..";
        }
        Component attrNameText = Component.translatable("gui.entitycontrol.modifier.modifier.name", Component.literal(attrName).withStyle(ChatFormatting.GOLD));
        g.drawString(
                parent.getFont(),
                attrNameText,
                x + 8 + parent.getFont().width("[" + namespace + "] "),
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
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)
                && parent.getLocalData().get(selectedEntityId).attributeRules.containsKey(attrId)) {
            displayVal = parent.getLocalData().get(selectedEntityId).attributeRules.get(attrId).value;
        }

        String operation = "";
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            EntityModifierConfig.AttributeRule rule = parent.getLocalData().get(selectedEntityId).attributeRules.get(attrId);
            if (rule != null) operation = switch (rule.mode) {
                case "MULTIPLY" -> "×";
                case "ADD" -> "+";
                case "SUBTRACT" -> "−";
                default -> "=";
            };
        }
        String valStr = operation + String.format(Locale.ROOT, "%.2f", displayVal);
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
        if (hovered) {
            Component detail = Component.translatable("gui.entitycontrol.modifier.global.attribute.tooltip",
                    attr.getDescriptionId() == null ? attrId : getReadableName(attr, rl), attrId);
            KineticOverlays.requestFormattedTooltip(parent.getFont().split(detail, 260), mx, my);
        }
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mx, int my) {
        Component editValComp = Component.translatable("gui.entitycontrol.modifier.modifier.edit_val");
        g.drawString(parent.getFont(), editValComp, x + 4, y + h - 14, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    protected boolean onRowClicked(Attribute attr, double mx, double my, int button) {
        selectedAttribute = Objects.requireNonNull(KineticRegistries.attributes().id(attr)).toString();
        if (parent.isGlobalMode()) parent.selectedGlobalAttribute(selectedAttribute);
        else parent.selectedIndividualAttribute(selectedAttribute);
        selectedMode = null;
        double displayVal = previewEntity != null && previewEntity.getAttributes().hasAttribute(attr) ? previewEntity.getAttributes().getBaseValue(attr) : attr.getDefaultValue();
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            EntityModifierConfig.EntityEditData data = parent.getLocalData().get(selectedEntityId);
            if (data.attributes.containsKey(selectedAttribute)) {
                displayVal = data.attributes.get(selectedAttribute);
                if (!parent.isGlobalMode()) selectedMode = "SET";
            }
            EntityModifierConfig.AttributeRule rule = data.attributeRules.get(selectedAttribute);
            if (rule != null) {
                displayVal = rule.value;
                if (!parent.isGlobalMode() || !"SET".equals(rule.mode)) selectedMode = rule.mode;
            }
        }
        loadingValue = true;
        try {
            attrValueBox.setValue(selectedMode == null ? "" : String.valueOf(displayVal));
        } finally {
            loadingValue = false;
        }
        updateControls();
        return true;
    }
}
