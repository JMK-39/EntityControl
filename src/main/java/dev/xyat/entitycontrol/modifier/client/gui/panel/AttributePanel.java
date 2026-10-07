package dev.xyat.entitycontrol.modifier.client.gui.panel;

import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class AttributePanel extends AbstractModifierScrollPanel<Attribute> {
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

    private KineticNumberField attrValueBox;
    private final EditedEntryTracker<Attribute> editedAttributes = new EditedEntryTracker<>();
    private KineticButton modeButton;
    private KineticButton deleteButton;
    private String selectedAttribute;
    private String selectedMode;
    private static final int VALUE_WIDTH = 64;
    // Row columns reserve room for the numeric value and a readable attribute name.
    private static final int ROW_VALUE_RESERVE = 86;
    private static final int NAMESPACE_COLUMN_WIDTH = 96;
    private boolean loadingValue;
    private int valueLabelX;

    private EntityModifierConfig.AttributeRule putRule(double value) {
        EntityModifierConfig.EntityEditData data = parent.getLocalData().computeIfAbsent(
                selectedEntityId, key -> new EntityModifierConfig.EntityEditData()
        );
        data.attributes.remove(selectedAttribute);
        boolean newRule = !data.attributeRules.containsKey(selectedAttribute);
        EntityModifierConfig.AttributeRule rule = data.attributeRules.computeIfAbsent(
                selectedAttribute,
                key -> new EntityModifierConfig.AttributeRule(selectedMode, value)
        );
        if (newRule && parent.isGlobalMode()) rule.targetEntities = new TreeSet<>();
        rule.mode = selectedMode;
        rule.value = value;
        return rule;
    }

    private EntityModifierConfig.AttributeRule ensureCurrentRule() {
        if (selectedAttribute == null || selectedEntityId == null || selectedMode == null || attrValueBox == null) {
            return null;
        }
        Double value = attrValueBox.getDoubleValue();
        if (value == null) return null;
        return putRule(value);
    }

    public void toggleGlobalTarget(String id, List<String> allowedIds) {
        if (!parent.isGlobalMode() || selectedAttribute == null || id == null || !allowedIds.contains(id)) return;
        EntityModifierConfig.AttributeRule rule = ensureCurrentRule();
        if (rule == null) return;
        if (rule.targetEntities == null) rule.targetEntities = new TreeSet<>();
        if (!rule.targetEntities.add(id)) rule.targetEntities.remove(id);
    }

    public void restoreSelection(String id) {
        ResourceLocation key = KineticResourceIds.tryParse(id);
        Attribute attr = key == null ? null : KineticRegistries.attributes().get(key);
        if (attr != null) {
            selectAttribute(attr, false);
            restoreRowSelection(attr);
        }
    }

    private Component modeLabel() {
        if (selectedMode == null) {
            return KineticI18n.translatable("gui.entitycontrol.modifier.global.mode.choose");
        }
        return KineticI18n.translatable(
                "gui.entitycontrol.modifier.global.mode." + selectedMode.toLowerCase(Locale.ROOT)
        );
    }

    private void showModeMenu() {
        if (selectedAttribute == null) return;
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        for (String mode : MODES) {
            if (parent.isGlobalMode() && "SET".equals(mode)) continue;
            Component label = KineticI18n.translatable(
                    "gui.entitycontrol.modifier.global.mode." + mode.toLowerCase(Locale.ROOT)
            );
            Component help = KineticI18n.translatable(
                    "gui.entitycontrol.modifier.global.mode." + mode.toLowerCase(Locale.ROOT) + ".tooltip"
            );
            entries.add(KineticOverlays.MenuItem.choice(
                    label, help, mode.equals(selectedMode), () -> selectMode(mode)
            ));
        }
        parent.showModifierContextMenu(modeButton.controlX(), modeButton.controlY() + modeButton.controlHeight() + 2, entries);
    }

    private void selectMode(String mode) {
        if (selectedEntityId == null || selectedAttribute == null || !MODES.contains(mode)
                || (parent.isGlobalMode() && "SET".equals(mode))) {
            return;
        }
        boolean changed = !mode.equals(selectedMode);
        selectedMode = mode;
        if (changed) {
            double value = switch (mode) {
                case "MULTIPLY" -> 1.0D;
                case "ADD", "SUBTRACT" -> 0.0D;
                default -> selectedBaseValue();
            };
            setValueSilently(value);
            putRule(value);
        }
        updateControls();
        updateSearch(searchBox == null ? "" : searchBox.textValue());
    }

    private double selectedBaseValue() {
        ResourceLocation id = KineticResourceIds.tryParse(selectedAttribute);
        Attribute attribute = id == null ? null : KineticRegistries.attributes().get(id);
        if (attribute == null) return 0.0D;
//? if >=1.21 {
/*        return previewEntity != null && previewEntity.getAttributes().hasAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute))
*///?} else {
        return previewEntity != null && previewEntity.getAttributes().hasAttribute(attribute)
//?}
//? if >=1.21 {
/*                ? previewEntity.getAttributes().getBaseValue(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute))
*///?} else {
                ? previewEntity.getAttributes().getBaseValue(attribute)
//?}
                : attribute.getDefaultValue();
    }

    private void setValueSilently(double value) {
        if (attrValueBox == null) return;
        loadingValue = true;
        try {
            attrValueBox.setDoubleValue(value);
        } finally {
            loadingValue = false;
        }
    }

    private void clearValueSilently() {
        if (attrValueBox == null) return;
        loadingValue = true;
        try {
            attrValueBox.setTextValue("");
        } finally {
            loadingValue = false;
        }
    }

    private void updateControls() {
        if (modeButton != null) {
            modeButton.setText(modeLabel());
            modeButton.setEnabled(selectedEntityId != null && selectedAttribute != null);
        }
        if (attrValueBox != null) {
            attrValueBox.setEnabled(selectedEntityId != null && selectedAttribute != null && selectedMode != null);
        }
        if (deleteButton != null) {
            EntityModifierConfig.EntityEditData data = selectedEntityId == null
                    ? null
                    : parent.getLocalData().get(selectedEntityId);
            deleteButton.setEnabled(selectedAttribute != null && data != null
                    && (data.attributes.containsKey(selectedAttribute)
                    || data.attributeRules.containsKey(selectedAttribute)));
        }
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
        clearValueSilently();
        if (rowList != null) rowList.setSelectedIndex(-1);
        updateSearch(searchBox == null ? "" : searchBox.textValue());
        updateControls();
    }

    /** 底部一行：[模式 ▾] 数值 [输入框] [删除规则]，文字宽度受面板空间限制。 */
    @Override
    protected void initExtra(KineticUi ui) {
        int rowY = y + h - EcPage.H;
        List<Component> modeLabels = new ArrayList<>();
        modeLabels.add(KineticI18n.translatable("gui.entitycontrol.modifier.global.mode.choose"));
        for (String mode : MODES) {
            modeLabels.add(KineticI18n.translatable("gui.entitycontrol.modifier.global.mode." + mode.toLowerCase(Locale.ROOT)));
        }
        int textColumnWidth = Math.max(0, (w - VALUE_WIDTH - EcPage.GAP * 4) / 3);
        int modeWidth = Math.min(textColumnWidth, EcPage.fitWidth(modeLabels));
        modeButton = ui.button(x, rowY, modeWidth)
                .text(modeLabel())
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.global.mode.tooltip"))
                .onClick(this::showModeMenu)
                .build();

        valueLabelX = x + modeWidth + EcPage.GAP * 2;
        // The "Edit value" label has the same room in every language and scrolls when longer.
        int valueX = valueLabelX + Math.min(textColumnWidth, 50) + EcPage.GAP;
        Component deleteText = KineticI18n.translatable("gui.entitycontrol.modifier.global.delete_rule");
        deleteButton = ui.button(valueX + VALUE_WIDTH + EcPage.GAP, rowY,
                        Math.min(textColumnWidth, EcPage.fitWidth(List.of(deleteText))))
                .text(deleteText)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.modifier.global.delete_rule.tooltip"))
                .onClick(this::removeSelectedRule)
                .build();

        attrValueBox = ui.numberField(valueX, rowY, VALUE_WIDTH, NumberType.DECIMAL)
                .label(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.edit_val"))
                .allowNegative(true)
                .range(-1.0E9D, 1.0E9D)
                .validator(number -> Double.isFinite(number.doubleValue()))
                .onChange(value -> {
                    if (loadingValue || selectedEntityId == null || selectedAttribute == null) return;
                    Double parsed = attrValueBox == null ? null : attrValueBox.getDoubleValue();
                    if (parsed != null) {
                        putRule(parsed);
                        updateSearch(searchBox == null ? "" : searchBox.textValue());
                        updateControls();
                    }
                })
                .firstShownTextAsDefault()
                .build();
        updateControls();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (attrValueBox != null) attrValueBox.setControlVisible(visible);
        if (modeButton != null) modeButton.setControlVisible(visible);
        if (deleteButton != null) deleteButton.setControlVisible(visible);
        updateControls();
    }

    @Override
    public void onEntitySelected(String entityId, LivingEntity previewEntity) {
        editedAttributes.clear();
        selectedAttribute = null;
        selectedMode = null;
        clearValueSilently();
        if (rowList != null) rowList.setSelectedIndex(-1);
        super.onEntitySelected(entityId, previewEntity);
        updateControls();
    }

    private boolean isAttrModified(String attrId, Attribute attr) {
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            EntityModifierConfig.EntityEditData data = parent.getLocalData().get(selectedEntityId);
            if (data.attributeRules.containsKey(attrId)) return true;
            Map<String, Double> attrs = data.attributes;
            if (attrs.containsKey(attrId)) {
//? if >=1.21 {
/*                double defaultVal = previewEntity != null && previewEntity.getAttributes().hasAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr))
*///?} else {
                double defaultVal = previewEntity != null && previewEntity.getAttributes().hasAttribute(attr)
//?}
//? if >=1.21 {
/*                        ? previewEntity.getAttributes().getBaseValue(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr))
*///?} else {
                        ? previewEntity.getAttributes().getBaseValue(attr)
//?}
                        : attr.getDefaultValue();
                return Math.abs(attrs.get(attrId) - defaultVal) > 0.0001D;
            }
        }
        return false;
    }

    private String getReadableName(Attribute attr, ResourceLocation id) {
        String translationKey = attr.getDescriptionId();
        String translated = KineticI18n.translatable(translationKey).getString();
        if (translated.equals(translationKey) && id != null) {
            StringBuilder result = new StringBuilder();
            for (String part : id.getPath().replace('.', '_').split("_")) {
                if (part.isEmpty()) continue;
                if (!result.isEmpty()) result.append(' ');
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return result.toString();
        }
        return translated;
    }

    @Override
    protected void updateSearch(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        editedAttributes.refresh(KineticRegistries.attributes().values(), attribute -> {
            ResourceLocation id = KineticRegistries.attributes().id(attribute);
            return id != null && isAttrModified(id.toString(), attribute);
        });
        displayList = KineticRegistries.attributes().values().stream()
                .filter(attribute -> {
                    ResourceLocation id = KineticRegistries.attributes().id(attribute);
                    return id != null && (q.isEmpty()
                            || id.toString().toLowerCase(Locale.ROOT).contains(q)
                            || getReadableName(attribute, id).toLowerCase(Locale.ROOT).contains(q));
                })
                .sorted(editedAttributes.comparator((left, right) -> {
                    ResourceLocation leftId = Objects.requireNonNull(KineticRegistries.attributes().id(left));
                    ResourceLocation rightId = Objects.requireNonNull(KineticRegistries.attributes().id(right));
                    int leftRank = COMMON_ATTRIBUTES.indexOf(leftId.toString());
                    int rightRank = COMMON_ATTRIBUTES.indexOf(rightId.toString());
                    if (leftRank >= 0 || rightRank >= 0) {
                        if (leftRank < 0) return 1;
                        if (rightRank < 0) return -1;
                        return Integer.compare(leftRank, rightRank);
                    }
                    boolean leftVanilla = "minecraft".equals(leftId.getNamespace());
                    boolean rightVanilla = "minecraft".equals(rightId.getNamespace());
                    if (leftVanilla != rightVanilla) return leftVanilla ? -1 : 1;
                    return leftId.toString().compareTo(rightId.toString());
                }))
                .toList();
        refreshRows();

        if (selectedAttribute != null && rowList != null) {
            for (int i = 0; i < displayList.size(); i++) {
                ResourceLocation id = KineticRegistries.attributes().id(displayList.get(i));
                if (id != null && selectedAttribute.equals(id.toString())) {
                    rowList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    @Override
    protected int getListHeight() {
        return h - listTopOffset() - EcPage.H - EcPage.GAP;
    }

    @Override
    protected Component getSearchHint() {
        return KineticI18n.translatable("gui.entitycontrol.modifier.modifier.search_attr");
    }

    @Override
    protected void renderRow(KineticGraphics graphics, Attribute attr, int index, int rowX, int rowY,
                             int rowWidth, int rowHeight, int mouseX, int mouseY,
                             boolean hovered, boolean selected) {
        ResourceLocation id = KineticRegistries.attributes().id(attr);
        String attrId = id == null ? "" : id.toString();
        String namespace = id == null ? "minecraft" : id.getNamespace();
        boolean modified = isAttrModified(attrId, attr);
        // Plain list row like the effect list: no box, only the hovered, current (yellow) or modified (green) row is outlined.
        if (selected || hovered) {
            KineticTheme.stateOutline(graphics, rowX, rowY + 1, rowWidth, rowHeight - 2, selected, hovered, false);
        } else if (modified) {
            KineticTheme.indicatorOutline(graphics, rowX, rowY + 1, rowWidth, rowHeight - 2, KineticTheme.Indicator.SUCCESS);
        }

        Component namespaceText = KineticI18n.translatable(
                "minecraft".equals(namespace)
                        ? "gui.entitycontrol.modifier.modifier.namespace.minecraft"
                        : "gui.entitycontrol.modifier.modifier.namespace.mod",
                namespace
        );
        int textX = rowX + 6;
        int nameRight = rowX + rowWidth - ROW_VALUE_RESERVE;
        int namespaceWidth = Math.min(NAMESPACE_COLUMN_WIDTH,
                Math.max(0, (nameRight - textX - EcPage.GAP) / 2));
        // Parse legacy language-file colors continuously across translation placeholders.
        graphics.scrollingText(Component.literal(namespaceText.getString()), textX, rowY + 6,
                namespaceWidth, KineticTheme.current().text(), false);

        String attrName = getReadableName(attr, id);
        // The name always starts after the full namespace column, whatever the namespace text's length.
        int nameX = textX + namespaceWidth + EcPage.GAP;
        int maxNameWidth = Math.max(0, nameRight - nameX - EcPage.GAP);
        Component nameText = KineticI18n.translatable(
                "gui.entitycontrol.modifier.modifier.name",
                attrName
        );
        graphics.scrollingText(Component.literal(nameText.getString()), nameX, rowY + 6,
                maxNameWidth, KineticTheme.current().text(), false);

//? if >=1.21 {
/*        double displayValue = previewEntity != null && previewEntity.getAttributes().hasAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr))
*///?} else {
        double displayValue = previewEntity != null && previewEntity.getAttributes().hasAttribute(attr)
//?}
//? if >=1.21 {
/*                ? previewEntity.getAttributes().getBaseValue(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr))
*///?} else {
                ? previewEntity.getAttributes().getBaseValue(attr)
//?}
                : attr.getDefaultValue();
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            EntityModifierConfig.EntityEditData data = parent.getLocalData().get(selectedEntityId);
            if (data.attributes.containsKey(attrId)) displayValue = data.attributes.get(attrId);
            EntityModifierConfig.AttributeRule rule = data.attributeRules.get(attrId);
            if (rule != null) displayValue = rule.value;
        }

        String operation = "";
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            EntityModifierConfig.AttributeRule rule = parent.getLocalData().get(selectedEntityId).attributeRules.get(attrId);
            if (rule != null) {
                operation = switch (rule.mode) {
                    case "MULTIPLY" -> "×";
                    case "ADD" -> "+";
                    case "SUBTRACT" -> "−";
                    default -> "=";
                };
            }
        }
        Component valueText = KineticI18n.translatable(
                modified
                        ? "gui.entitycontrol.modifier.modifier.value.modified"
                        : "gui.entitycontrol.modifier.modifier.value.default",
                operation + String.format(Locale.ROOT, "%.2f", displayValue)
        );
        graphics.text(
                valueText.getString(),
                rowX + rowWidth - 7 - KineticText.width(valueText),
                rowY + 6,
                KineticTheme.current().text()
        );
    }

    @Override
    protected Component rowTooltip(Attribute attr) {
        ResourceLocation id = KineticRegistries.attributes().id(attr);
        return KineticI18n.translatable(
                "gui.entitycontrol.modifier.global.attribute.tooltip",
                getReadableName(attr, id),
                id == null ? "" : id.toString()
        );
    }

    @Override
    protected void renderExtra(KineticGraphics graphics, int mouseX, int mouseY) {
        Component label = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.edit_val");
        graphics.scrollingText(label, valueLabelX, y + h - EcPage.H + 4,
                Math.max(0, attrValueBox.controlX() - valueLabelX - EcPage.GAP),
                KineticTheme.current().text(), false);
    }

    @Override
    protected boolean onRowClicked(Attribute attr, int index, MouseInput input) {
        if (!input.isLeft()) return false;
        selectAttribute(attr, true);
        if (rowList != null) rowList.select(index);
        return true;
    }

    private void selectAttribute(Attribute attr, boolean rememberSelection) {
        selectedAttribute = Objects.requireNonNull(KineticRegistries.attributes().id(attr)).toString();
        if (rememberSelection) {
            if (parent.isGlobalMode()) parent.selectedGlobalAttribute(selectedAttribute);
            else parent.selectedIndividualAttribute(selectedAttribute);
        }
        selectedMode = null;

//? if >=1.21 {
/*        double displayValue = previewEntity != null && previewEntity.getAttributes().hasAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr))
*///?} else {
        double displayValue = previewEntity != null && previewEntity.getAttributes().hasAttribute(attr)
//?}
//? if >=1.21 {
/*                ? previewEntity.getAttributes().getBaseValue(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr))
*///?} else {
                ? previewEntity.getAttributes().getBaseValue(attr)
//?}
                : attr.getDefaultValue();
        if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
            EntityModifierConfig.EntityEditData data = parent.getLocalData().get(selectedEntityId);
            if (data.attributes.containsKey(selectedAttribute)) {
                displayValue = data.attributes.get(selectedAttribute);
                if (!parent.isGlobalMode()) selectedMode = "SET";
            }
            EntityModifierConfig.AttributeRule rule = data.attributeRules.get(selectedAttribute);
            if (rule != null) {
                displayValue = rule.value;
                if (!parent.isGlobalMode() || !"SET".equals(rule.mode)) selectedMode = rule.mode;
            }
        }

        if (selectedMode == null) clearValueSilently();
        else setValueSilently(displayValue);
        updateControls();
    }
}
