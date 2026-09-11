package dev.xyat.entitycontrol.modifier.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BuffEditScreen extends KineticScreen {
    private static final int VISIBLE_DIMENSIONS = 6;

    private final EntityModifierScreen parent;
    private final String entityId;
    private final String effectId;
    private final EntityModifierConfig.PotionBuff buff;

    private final List<String> addedDimensions =
            new ArrayList<>();

    private final List<String> allDims =
            new ArrayList<>();

    private final GridScrollController dimensionScroll =
            new GridScrollController();

    private NumericEditBox chanceBox;
    private NumericEditBox minBox;
    private NumericEditBox maxBox;

    public BuffEditScreen(
            EntityModifierScreen parent,
            String entityId,
            String effectId,
            EntityModifierConfig.PotionBuff buff
    ) {
        super(Component.translatable(
                "gui.entitycontrol.modifier.modifier.buff_edit"
        ));

        this.parent = parent;
        this.entityId = entityId;
        this.effectId = effectId;
        this.buff = buff;

        if (buff.dimensions != null
                && !buff.dimensions.isEmpty()) {
            addedDimensions.addAll(
                    Arrays.asList(
                            buff.dimensions.split(",")
                    )
            );
        }
    }

    private void refreshDimList() {
        allDims.clear();

        if (Minecraft.getInstance()
                .getConnection() != null) {
            Minecraft.getInstance()
                    .getConnection()
                    .levels()
                    .forEach(key ->
                            allDims.add(
                                    key.location().toString()
                            )
                    );
        }

        for (String dimension : addedDimensions) {
            if (!allDims.contains(dimension)) {
                allDims.add(dimension);
            }
        }

        allDims.sort((left, right) -> {
            boolean leftAdded =
                    addedDimensions.contains(left);

            boolean rightAdded =
                    addedDimensions.contains(right);

            if (leftAdded != rightAdded) {
                return leftAdded ? -1 : 1;
            }

            return left.compareTo(right);
        });

        dimensionScroll.update(
                allDims.size(),
                VISIBLE_DIMENSIONS
        );
    }

    @Override
    protected void buildUi() {
        int centerX = canvasWidth / 2;
        int centerY = canvasHeight / 2;

        int chanceLabelWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.buff.chance"
                        )
                );

        int minLabelWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.buff.min"
                        )
                );

        int maxLabelWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.buff.max"
                        )
                );

        int totalRowWidth =
                chanceLabelWidth
                        + 5
                        + 40
                        + 15
                        + minLabelWidth
                        + 5
                        + 30
                        + 15
                        + maxLabelWidth
                        + 5
                        + 30;

        int currentX =
                centerX - totalRowWidth / 2;

        int topRowY =
                centerY - 70;

        chanceBox = NumericEditBox.decimal(
                font,
                currentX + chanceLabelWidth + 5,
                topRowY,
                40,
                20,
                Component.empty(),
                false,
                0D,
                1D
        );

        chanceBox.setValue(
                NumericEditBox.format(buff.chance)
        );

        addRenderableWidget(chanceBox);

        currentX +=
                chanceLabelWidth + 5 + 40 + 15;

        minBox = NumericEditBox.integer(
                font,
                currentX + minLabelWidth + 5,
                topRowY,
                30,
                20,
                Component.empty(),
                false,
                0,
                null
        );

        minBox.setIntValue(buff.minLevel);
        addRenderableWidget(minBox);

        currentX +=
                minLabelWidth + 5 + 30 + 15;

        maxBox = NumericEditBox.integer(
                font,
                currentX + maxLabelWidth + 5,
                topRowY,
                30,
                20,
                Component.empty(),
                false,
                0,
                null
        );

        maxBox.setIntValue(buff.maxLevel);
        addRenderableWidget(maxBox);

        refreshDimList();

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.entitycontrol.modifier.modifier.save"
                                ),
                                button -> saveAndBack()
                        )
                        .bounds(
                                centerX - 75,
                                centerY + 115,
                                65,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.entitycontrol.modifier.config.back"
                                ),
                                button -> {
                                    if (minecraft != null) {
                                        minecraft.setScreen(parent);
                                    }
                                }
                        )
                        .bounds(
                                centerX + 10,
                                centerY + 115,
                                65,
                                20
                        )
                        .build()
        );
    }

    private void saveAndBack() {
        Double chance =
                chanceBox.getDoubleValue();

        Integer minLevel =
                minBox.getIntValue();

        Integer maxLevel =
                maxBox.getIntValue();

        if (chance == null
                || minLevel == null
                || maxLevel == null
                || maxLevel < minLevel) {
            GuiOverlay.toast(
                    Component.translatable(
                            "msg.entitycontrol.modifier.invalid_number"
                    )
            );
            return;
        }

        buff.chance = chance;
        buff.minLevel = minLevel;
        buff.maxLevel = maxLevel;
        buff.dimensions =
                String.join(",", addedDimensions);

        parent.getLocalData()
                .computeIfAbsent(
                        entityId,
                        key ->
                                new EntityModifierConfig.EntityEditData()
                )
                .buffs
                .put(
                        effectId,
                        buff
                );

        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int centerX = canvasWidth / 2;
        int centerY = canvasHeight / 2;

        GuiTheme.panel(
                graphics,
                centerX - 180,
                centerY - 120,
                360,
                265
        );

        graphics.drawCenteredString(
                font,
                title,
                centerX,
                centerY - 110,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                Component.literal(effectId),
                centerX,
                centerY - 95,
                0xFFFFFF
        );

        int chanceLabelWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.buff.chance"
                        )
                );

        int minLabelWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.buff.min"
                        )
                );

        int maxLabelWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.buff.max"
                        )
                );

        int totalRowWidth =
                chanceLabelWidth
                        + 5
                        + 40
                        + 15
                        + minLabelWidth
                        + 5
                        + 30
                        + 15
                        + maxLabelWidth
                        + 5
                        + 30;

        int currentX =
                centerX - totalRowWidth / 2;

        graphics.drawString(
                font,
                Component.translatable(
                        "gui.entitycontrol.modifier.modifier.buff.chance"
                ),
                currentX,
                centerY - 64,
                0xAAAAAA
        );

        currentX +=
                chanceLabelWidth + 5 + 40 + 15;

        graphics.drawString(
                font,
                Component.translatable(
                        "gui.entitycontrol.modifier.modifier.buff.min"
                ),
                currentX,
                centerY - 64,
                0xAAAAAA
        );

        currentX +=
                minLabelWidth + 5 + 30 + 15;

        graphics.drawString(
                font,
                Component.translatable(
                        "gui.entitycontrol.modifier.modifier.buff.max"
                ),
                currentX,
                centerY - 64,
                0xAAAAAA
        );

        int listX = centerX - 165;
        int listY = centerY - 35;
        int listW = 330;
        int listH = 132;

        graphics.fill(
                listX,
                listY,
                listX + listW,
                listY + listH,
                0xFF111111
        );

        graphics.renderOutline(
                listX,
                listY,
                listW,
                listH,
                0xFF555555
        );
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int centerX = canvasWidth / 2;
        int centerY = canvasHeight / 2;

        int listX = centerX - 165;
        int listY = centerY - 35;
        int listW = 330;
        int listH = 132;

        dimensionScroll.update(
                allDims.size(),
                VISIBLE_DIMENSIONS
        );

        int first = dimensionScroll.smoothIndexOffset();
        int shift = dimensionScroll.visualShift(22);
        int visibleCount = Math.min(
                VISIBLE_DIMENSIONS + 1,
                allDims.size() - first
        );

        enableCanvasScissor(graphics, listX, listY, listX + listW - 8, listY + listH);
        for (int row = 0; row < visibleCount; row++) {
            int drawY =
                    listY + row * 22 - shift;

            String dimension =
                    allDims.get(first + row);

            boolean added =
                    addedDimensions.contains(dimension);

            boolean hovered =
                    mouseX >= listX + 1
                            && mouseX < listX + listW - 8
                            && mouseY >= drawY
                            && mouseY < drawY + 22;

            if (hovered) {
                graphics.fill(
                        listX + 1,
                        drawY,
                        listX + listW - 8,
                        drawY + 22,
                        0x33FFFFFF
                );
            }

            graphics.drawString(
                    font,
                    Component.translatable("gui.entitycontrol.modifier.modifier.dimension_name", Component.literal(dimension).withStyle(ChatFormatting.GOLD)),
                    listX + 6,
                    drawY + 7,
                    0xFFFFFF
            );

            int buttonWidth = 44;
            int buttonX =
                    listX + listW - 10 - buttonWidth;

            int buttonY =
                    drawY + 3;

            boolean buttonHovered =
                    mouseX >= buttonX
                            && mouseX <= buttonX + buttonWidth
                            && mouseY >= buttonY
                            && mouseY <= buttonY + 16;

            graphics.fill(
                    buttonX,
                    buttonY,
                    buttonX + buttonWidth,
                    buttonY + 16,
                    added
                            ? buttonHovered
                            ? 0xFFFF5555
                            : 0xFFAA0000
                            : buttonHovered
                            ? 0xFF55FF55
                            : 0xFF00AA00
            );

            String buttonText =
                    Component.translatable(
                            added
                                    ? "gui.entitycontrol.modifier.modifier.buff.dim_remove_btn"
                                    : "gui.entitycontrol.modifier.modifier.buff.dim_add_btn"
                    ).getString();

            graphics.drawCenteredString(
                    font,
                    buttonText,
                    buttonX + buttonWidth / 2,
                    buttonY + 4,
                    0xFFFFFF
            );
        }

        graphics.disableScissor();

        dimensionScroll.render(
                graphics,
                mouseX,
                mouseY,
                listX + listW - 7,
                listY + 1,
                4,
                listH - 2,
                15
        );
    }

    @Override
    protected boolean canvasMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        int centerX = canvasWidth / 2;
        int centerY = canvasHeight / 2;

        int listX = centerX - 165;
        int listY = centerY - 35;
        int listW = 330;
        int listH = 132;

        if (button == 0
                && dimensionScroll.beginDrag(
                        mouseX,
                        mouseY,
                        listX + listW - 7,
                        listY + 1,
                        4,
                        listH - 2,
                        15,
                        0
                )) {
            return true;
        }

        if (mouseX >= listX + 1
                && mouseX < listX + listW - 8
                && mouseY >= listY
                && mouseY < listY + listH) {
            int index =
                    dimensionScroll.smoothIndexOffset()
                            + (int) ((mouseY - listY + dimensionScroll.visualShift(22)) / 22);

            if (index >= 0 && index < allDims.size()) {
                String dimension =
                        allDims.get(index);

                int buttonWidth = 44;
                int buttonX =
                        listX + listW - 10 - buttonWidth;

                if (mouseX >= buttonX
                        && mouseX <= buttonX + buttonWidth) {
                    if (addedDimensions.contains(dimension)) {
                        addedDimensions.remove(dimension);
                    } else {
                        addedDimensions.add(dimension);
                    }

                    refreshDimList();
                    return true;
                }
            }
        }

        return super.canvasMouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (dimensionScroll.drag(
                mouseY,
                canvasHeight / 2 - 34,
                130,
                15
        )) {
            return true;
        }

        return super.canvasMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean canvasMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (dimensionScroll.release(button)) {
            return true;
        }

        return super.canvasMouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    protected boolean canvasMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (dimensionScroll.scroll(delta)) {
            return true;
        }

        return super.canvasMouseScrolled(
                mouseX,
                mouseY,
                delta
        );
    }
}
