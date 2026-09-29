package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.list.KineticRowList;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BuffEditScreen extends KineticPage {
    private static final int LIST_WIDTH = 330;
    private static final int LIST_HEIGHT = 132;
    private static final int ROW_HEIGHT = 22;
    private static final int INLINE_BUTTON_WIDTH = 44;

    private final EntityModifierScreen parent;
    private final String entityId;
    private final String effectId;
    private final EntityModifierConfig.PotionBuff buff;
    private final List<String> addedDimensions = new ArrayList<>();
    private final List<String> allDims = new ArrayList<>();

    private KineticNumberField chanceBox;
    private KineticNumberField minBox;
    private KineticNumberField maxBox;
    private DimensionList dimensionList;

    public BuffEditScreen(EntityModifierScreen parent, String entityId, String effectId,
                          EntityModifierConfig.PotionBuff buff) {
        super(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff_edit"));
        this.parent = parent;
        this.entityId = entityId;
        this.effectId = effectId;
        this.buff = buff;

        if (buff.dimensions != null && !buff.dimensions.isEmpty()) {
            addedDimensions.addAll(Arrays.asList(buff.dimensions.split(",")));
        }
    }

    private void refreshDimList() {
        allDims.clear();
        KineticClientRuntime.knownLevels().forEach(key -> allDims.add(key.location().toString()));
        for (String dimension : addedDimensions) {
            if (!allDims.contains(dimension)) allDims.add(dimension);
        }
        allDims.sort((left, right) -> {
            boolean leftAdded = addedDimensions.contains(left);
            boolean rightAdded = addedDimensions.contains(right);
            if (leftAdded != rightAdded) return leftAdded ? -1 : 1;
            return left.compareTo(right);
        });
        if (dimensionList != null) dimensionList.setItems(allDims);
    }

    @Override
    protected void build(KineticUi ui) {
        int centerX = width() / 2;
        int centerY = height() / 2;
        Component chanceLabel = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.chance");
        Component minLabel = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.min");
        Component maxLabel = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.max");

        int chanceLabelWidth = KineticText.width(chanceLabel);
        int minLabelWidth = KineticText.width(minLabel);
        int maxLabelWidth = KineticText.width(maxLabel);
        int totalRowWidth = chanceLabelWidth + 5 + 40 + 15
                + minLabelWidth + 5 + 30 + 15
                + maxLabelWidth + 5 + 30;
        int currentX = centerX - totalRowWidth / 2;
        int topRowY = centerY - 70;

        chanceBox = ui.numberField(currentX + chanceLabelWidth + 5, topRowY, 40, NumberType.DECIMAL)
                .label(chanceLabel)
                .allowNegative(false)
                .range(0D, 1D)
                .value(buff.chance)
                .firstShownTextAsDefault().build();

        currentX += chanceLabelWidth + 5 + 40 + 15;
        minBox = ui.numberField(currentX + minLabelWidth + 5, topRowY, 30, NumberType.INT)
                .label(minLabel)
                .allowNegative(false)
                .range(0, null)
                .value(buff.minLevel)
                .firstShownTextAsDefault().build();

        currentX += minLabelWidth + 5 + 30 + 15;
        maxBox = ui.numberField(currentX + maxLabelWidth + 5, topRowY, 30, NumberType.INT)
                .label(maxLabel)
                .allowNegative(false)
                .range(0, null)
                .value(buff.maxLevel)
                .firstShownTextAsDefault().build();

        refreshDimList();
        int listX = centerX - 165;
        int listY = centerY - 35;
        dimensionList = ui.add(new DimensionList(listX, listY, LIST_WIDTH, LIST_HEIGHT));
        dimensionList.setItems(allDims);

        ui.button(centerX - 75, centerY + 115, 65)
                .text(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.save"))
                .onClick(this::save)
                .build();
        ui.button(centerX + 10, centerY + 115, 65)
                .text(KineticI18n.translatable("gui.entitycontrol.modifier.config.back"))
                .onClick(this::navigateBack)
                .build();
    }

    private void save() {
        Double chance = chanceBox == null ? null : chanceBox.getDoubleValue();
        Integer minLevel = minBox == null ? null : minBox.getIntValue();
        Integer maxLevel = maxBox == null ? null : maxBox.getIntValue();
        if (chance == null || minLevel == null || maxLevel == null || maxLevel < minLevel) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.modifier.invalid_number"));
            if (maxBox != null) maxBox.flashValidationError();
            return;
        }

        buff.chance = chance;
        buff.minLevel = minLevel;
        buff.maxLevel = maxLevel;
        buff.dimensions = String.join(",", addedDimensions);
        parent.getLocalData()
                .computeIfAbsent(entityId, key -> new EntityModifierConfig.EntityEditData())
                .buffs
                .put(effectId, buff);
    }

    private void toggleDimension(String dimension) {
        if (!addedDimensions.remove(dimension)) addedDimensions.add(dimension);
        refreshDimList();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width() / 2;
        int centerY = height() / 2;
        KineticTheme.panel(graphics, centerX - 180, centerY - 120, 360, 265);
        graphics.centeredText(title(), centerX, centerY - 110, KineticTheme.current().text(), false);
        graphics.centeredText(effectId, centerX, centerY - 95, KineticTheme.current().text(), false);

        Component chanceLabel = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.chance");
        Component minLabel = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.min");
        Component maxLabel = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.max");
        int chanceLabelWidth = KineticText.width(chanceLabel);
        int minLabelWidth = KineticText.width(minLabel);
        int maxLabelWidth = KineticText.width(maxLabel);
        int totalRowWidth = chanceLabelWidth + 5 + 40 + 15
                + minLabelWidth + 5 + 30 + 15
                + maxLabelWidth + 5 + 30;
        int currentX = centerX - totalRowWidth / 2;

        graphics.text(chanceLabel, currentX, centerY - 64, KineticTheme.current().text());
        currentX += chanceLabelWidth + 5 + 40 + 15;
        graphics.text(minLabel, currentX, centerY - 64, KineticTheme.current().text());
        currentX += minLabelWidth + 5 + 30 + 15;
        graphics.text(maxLabel, currentX, centerY - 64, KineticTheme.current().text());

        KineticTheme.panelAlt(graphics, centerX - 165, centerY - 35, LIST_WIDTH, LIST_HEIGHT);
    }

    private final class DimensionList extends KineticRowList<String> {
        private DimensionList(int x, int y, int width, int height) {
            super(x, y, width, height, ROW_HEIGHT);
        }

        @Override
        protected void renderRow(KineticGraphics graphics, String dimension, int index, int x, int y, int width,
                                 int height, boolean hovered, boolean selected) {
            Component label = KineticI18n.translatable(
                    "gui.entitycontrol.modifier.modifier.dimension_name",
                    KineticText.ellipsize(dimension, Math.max(30, width - INLINE_BUTTON_WIDTH - 18))
            );
            graphics.text(label, x + 6, y + 7, KineticTheme.current().text());

            boolean added = addedDimensions.contains(dimension);
            int buttonX = x + width - INLINE_BUTTON_WIDTH - 5;
            boolean buttonHovered = mouseX() >= buttonX && mouseX() < buttonX + INLINE_BUTTON_WIDTH
                    && mouseY() >= y + 3 && mouseY() < y + height - 3;
            KineticTheme.button(
                    graphics,
                    buttonX,
                    y + 3,
                    INLINE_BUTTON_WIDTH,
                    height - 6,
                    KineticI18n.translatable(added
                            ? "gui.entitycontrol.modifier.modifier.buff.dim_remove_btn"
                            : "gui.entitycontrol.modifier.modifier.buff.dim_add_btn"),
                    buttonHovered,
                    true,
                    added
            );
        }

        @Override
        protected boolean onRowClick(String dimension, int index, MouseInput input) {
            if (!input.isLeft()) return false;
            int rowY = rowTop(index);
            int buttonX = controlX() + rowsWidth() - INLINE_BUTTON_WIDTH - 5;
            if (!input.inside(buttonX, rowY + 3, INLINE_BUTTON_WIDTH, rowHeight() - 6)) return false;
            toggleDimension(dimension);
            return true;
        }
    }
}
