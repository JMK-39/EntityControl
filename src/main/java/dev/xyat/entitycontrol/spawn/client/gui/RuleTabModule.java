package dev.xyat.entitycontrol.spawn.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class RuleTabModule implements ITabModule {
    private static final String[] CATEGORIES = {
            "monster",
            "creature",
            "ambient",
            "axolotls",
            "underground_water_creature",
            "water_creature",
            "water_ambient",
            "misc"
    };

    private final SpawnControlScreen s;
    private final List<String> knownDimensions = new ArrayList<>();
    private final GridScrollController dimensionScroll = new GridScrollController();

    private NumericEditBox boxMinDist;
    private NumericEditBox boxMaxDist;
    private NumericEditBox boxMinHeight;
    private NumericEditBox boxMaxHeight;
    private NumericEditBox boxMinLight;
    private NumericEditBox boxMaxLight;
    private EditBox boxRules;
    private Button btnEnableControl;
    private Button btnBlockAll;
    private Button btnInvertRules;
    private Button btnCategory;

    private boolean isUpdating;
    private boolean showCategoryMenu;

    private final int listY = 215;
    private final int listH = SpawnControlScreen.V_HEIGHT - 225;

    public RuleTabModule(SpawnControlScreen s) {
        this.s = s;
        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().levels().forEach(
                    key -> knownDimensions.add(key.location().toString())
            );
            knownDimensions.sort(String::compareTo);
        }
    }

    @Override
    public void init() {
        int sectionWidth = (s.rw - 10) / 3;
        int row1Y = 91;
        int row2Y = 116;
        int row3Y = 141;
        int row4Y = 166;

        boxMinDist = s.addTabWidget(NumericEditBox.integer(
                s.getFont(), s.rx + 47, row1Y, 50, 20,
                Component.empty(), false, 0, 128
        ));
        boxMinDist.setMaxLength(3);
        boxMinDist.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.min_dist"
        )));
        boxMinDist.setResponder(this::updateMinDistance);

        boxMaxDist = s.addTabWidget(NumericEditBox.integer(
                s.getFont(), s.rx + sectionWidth + 47, row1Y, 50, 20,
                Component.empty(), false, 0, 128
        ));
        boxMaxDist.setMaxLength(3);
        boxMaxDist.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.max_dist"
        )));
        boxMaxDist.setResponder(this::updateMaxDistance);

        int rulesX = s.rx + sectionWidth * 2 + 42;
        boxRules = s.addTabWidget(new EditBox(
                s.getFont(), rulesX, row1Y,
                Math.max(45, s.rx + s.rw - rulesX), 20,
                Component.empty()
        ));
        boxRules.setMaxLength(20);
        boxRules.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.rules"
        )));
        boxRules.setResponder(this::updateRules);

        boxMinHeight = s.addTabWidget(NumericEditBox.integer(
                s.getFont(), s.rx + 47, row2Y, 50, 20,
                Component.empty(), true, null, null
        ));
        boxMinHeight.setMaxLength(11);
        boxMinHeight.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.min_height"
        )));
        boxMinHeight.setResponder(value -> updateHeight(value, true));

        boxMaxHeight = s.addTabWidget(NumericEditBox.integer(
                s.getFont(), s.rx + sectionWidth + 47, row2Y, 50, 20,
                Component.empty(), true, null, null
        ));
        boxMaxHeight.setMaxLength(11);
        boxMaxHeight.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.max_height"
        )));
        boxMaxHeight.setResponder(value -> updateHeight(value, false));

        btnCategory = s.addTabWidget(Button.builder(
                        Component.empty(),
                        button -> showCategoryMenu = !showCategoryMenu
                )
                .bounds(
                        s.rx + (sectionWidth + 5) * 2,
                        row2Y,
                        sectionWidth,
                        20
                )
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.entitycontrol.spawn.spawn.tooltip.category"
                )))
                .build());

        boxMinLight = s.addTabWidget(NumericEditBox.integer(
                s.getFont(), s.rx + 47, row3Y, 50, 20,
                Component.empty(), true, 0, 15
        ));
        boxMinLight.setMaxLength(2);
        boxMinLight.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.min_light"
        )));
        boxMinLight.setResponder(value -> updateLight(value, true));

        boxMaxLight = s.addTabWidget(NumericEditBox.integer(
                s.getFont(), s.rx + sectionWidth + 47, row3Y, 50, 20,
                Component.empty(), true, 0, 15
        ));
        boxMaxLight.setMaxLength(2);
        boxMaxLight.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.max_light"
        )));
        boxMaxLight.setResponder(value -> updateLight(value, false));

        btnEnableControl = s.addTabWidget(Button.builder(Component.empty(), button -> {
            BiomeSpawnConfig.EntityNode node = selectedNode();
            if (node == null) return;
            node.enable_control = !node.enable_control;
            s.markSelectedEntityEdited();
            updateBtnState(node);
        }).bounds(s.rx, row4Y, sectionWidth, 20).build());

        btnBlockAll = s.addTabWidget(Button.builder(Component.empty(), button -> {
            BiomeSpawnConfig.EntityNode node = selectedNode();
            if (node == null) return;
            node.block_all = !node.block_all;
            s.markSelectedEntityEdited();
            updateBtnState(node);
        }).bounds(s.rx + sectionWidth + 5, row4Y, sectionWidth, 20).build());

        btnInvertRules = s.addTabWidget(Button.builder(Component.empty(), button -> {
            BiomeSpawnConfig.EntityNode node = selectedNode();
            if (node == null) return;
            node.invert_rules = !node.invert_rules;
            s.markSelectedEntityEdited();
            updateBtnState(node);
        }).bounds(s.rx + (sectionWidth + 5) * 2, row4Y, sectionWidth, 20).build());
    }

    private BiomeSpawnConfig.EntityNode selectedNode() {
        if (s.selectedId == null) return null;
        return s.profile.entities.get(s.selectedId);
    }

    private void updateMinDistance(String raw) {
        if (isUpdating || s.selectedId == null || raw.isEmpty()) return;
        Integer value = boxMinDist.getIntValue();
        if (value == null) {
            restoreDistanceBoxes();
            showInvalidNumber();
            return;
        }

        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node != null && node.min_spawn_distance != value) {
            node.min_spawn_distance = value;
            s.markSelectedEntityEdited();
        }
    }

    private void updateMaxDistance(String raw) {
        if (isUpdating || s.selectedId == null || raw.isEmpty()) return;
        Integer value = boxMaxDist.getIntValue();
        if (value == null) {
            restoreDistanceBoxes();
            showInvalidNumber();
            return;
        }

        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node != null && node.max_spawn_distance != value) {
            node.max_spawn_distance = value;
            s.markSelectedEntityEdited();
        }
    }

    private void restoreDistanceBoxes() {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        isUpdating = true;
        boxMinDist.setIntValue(node.min_spawn_distance);
        boxMaxDist.setIntValue(node.max_spawn_distance);
        isUpdating = false;
    }

    private void updateRules(String value) {
        if (isUpdating || s.selectedId == null) return;

        String filtered = value.toUpperCase(Locale.ROOT).replaceAll("[^A-G]", "");
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        if (!filtered.equals(node.rules)) {
            node.rules = filtered;
            s.markSelectedEntityEdited();
        }

        if (!value.equals(filtered)) {
            isUpdating = true;
            boxRules.setValue(filtered);
            isUpdating = false;
        }
    }

    private void updateHeight(String raw, boolean minimum) {
        if (isUpdating || s.selectedId == null) return;
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        if (raw.isEmpty()) {
            return;
        }

        if ("-".equals(raw)) return;

        Integer value = minimum
                ? boxMinHeight.getIntValue()
                : boxMaxHeight.getIntValue();

        if (value == null) {
            restoreHeightBoxes();
            showInvalidNumber();
            return;
        }

        Integer previous = minimum ? node.min_spawn_height : node.max_spawn_height;
        if (Objects.equals(previous, value)) return;

        if (minimum) {
            node.min_spawn_height = value;
            if (node.max_spawn_height != null && value > node.max_spawn_height) {
                node.max_spawn_height = value;
            }
        } else {
            node.max_spawn_height = value;
            if (node.min_spawn_height != null && value < node.min_spawn_height) {
                node.min_spawn_height = value;
            }
        }

        s.markSelectedEntityEdited();
        restoreHeightBoxes();
    }

    private void restoreHeightBoxes() {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        isUpdating = true;
        boxMinHeight.setValue(node.min_spawn_height == null
                ? ""
                : Integer.toString(node.min_spawn_height));
        boxMaxHeight.setValue(node.max_spawn_height == null
                ? ""
                : Integer.toString(node.max_spawn_height));
        isUpdating = false;
    }

    private void updateLight(String raw, boolean minimum) {
        if (isUpdating || s.selectedId == null) return;

        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        if (raw.isEmpty()) {
            Integer previous = minimum
                    ? node.min_spawn_light
                    : node.max_spawn_light;
            if (previous == null) return;

            if (minimum) {
                node.min_spawn_light = null;
            } else {
                node.max_spawn_light = null;
            }

            s.markSelectedEntityEdited();
            return;
        }

        Integer value = minimum
                ? boxMinLight.getIntValue()
                : boxMaxLight.getIntValue();

        if (value == null || value < 0 || value > 15) {
            restoreLightBoxes();
            showInvalidNumber();
            return;
        }

        Integer previous = minimum
                ? node.min_spawn_light
                : node.max_spawn_light;
        if (Objects.equals(previous, value)) return;

        if (minimum) {
            node.min_spawn_light = value;
            if (node.max_spawn_light != null
                    && value > node.max_spawn_light) {
                node.max_spawn_light = value;
            }
        } else {
            node.max_spawn_light = value;
            if (node.min_spawn_light != null
                    && value < node.min_spawn_light) {
                node.min_spawn_light = value;
            }
        }

        s.markSelectedEntityEdited();
        restoreLightBoxes();
    }

    private void restoreLightBoxes() {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        isUpdating = true;
        boxMinLight.setValue(node.min_spawn_light == null
                ? ""
                : Integer.toString(node.min_spawn_light));
        boxMaxLight.setValue(node.max_spawn_light == null
                ? ""
                : Integer.toString(node.max_spawn_light));
        isUpdating = false;
    }

    private void showInvalidNumber() {
        GuiOverlay.toast(Component.translatable(
                "msg.entitycontrol.spawn.invalid_number"
        ));
    }

    private void updateBtnState(BiomeSpawnConfig.EntityNode node) {
        if (node == null) return;

        Component enabledText = Component.translatable(
                node.enable_control
                        ? "gui.entitycontrol.spawn.spawn.status.active.colored"
                        : "gui.entitycontrol.spawn.spawn.status.ignored.colored"
        ).withStyle(node.enable_control ? ChatFormatting.GREEN : ChatFormatting.RED);
        btnEnableControl.setMessage(
                Component.translatable("gui.entitycontrol.spawn.spawn.enabled.short")
                        .append(": ")
                        .append(enabledText)
        );
        btnEnableControl.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.enable",
                enabledText
        )));

        Component blockText = Component.translatable(
                node.block_all
                        ? "gui.entitycontrol.spawn.spawn.status.yes.colored"
                        : "gui.entitycontrol.spawn.spawn.status.no.colored"
        ).withStyle(node.block_all ? ChatFormatting.RED : ChatFormatting.GREEN);
        btnBlockAll.setMessage(
                Component.translatable("gui.entitycontrol.spawn.spawn.block.short")
                        .append(": ")
                        .append(blockText)
        );
        btnBlockAll.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.block",
                blockText
        )));

        Component invertText = Component.translatable(
                node.invert_rules
                        ? "gui.entitycontrol.spawn.spawn.status.blacklist.colored"
                        : "gui.entitycontrol.spawn.spawn.status.whitelist.colored"
        ).withStyle(node.invert_rules ? ChatFormatting.RED : ChatFormatting.GREEN);
        btnInvertRules.setMessage(
                Component.translatable("gui.entitycontrol.spawn.spawn.invert.short")
                        .append(": ")
                        .append(invertText)
        );
        btnInvertRules.setTooltip(Tooltip.create(Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.invert",
                invertText
        )));
    }

    @Override
    public void updateSelection() {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        isUpdating = true;
        boxRules.setValue(node.rules);
        boxMinDist.setIntValue(node.min_spawn_distance);
        boxMaxDist.setIntValue(node.max_spawn_distance);
        boxMinHeight.setValue(node.min_spawn_height == null
                ? ""
                : Integer.toString(node.min_spawn_height));
        boxMaxHeight.setValue(node.max_spawn_height == null
                ? ""
                : Integer.toString(node.max_spawn_height));
        boxMinLight.setValue(node.min_spawn_light == null
                ? ""
                : Integer.toString(node.min_spawn_light));
        boxMaxLight.setValue(node.max_spawn_light == null
                ? ""
                : Integer.toString(node.max_spawn_light));
        btnCategory.setMessage(
                Component.translatable("gui.entitycontrol.spawn.spawn.category.cycle")
                        .append(": ")
                        .append(s.getTranslatedCategoryName(node.category))
        );
        updateBtnState(node);
        isUpdating = false;
        setVisible(true);
    }

    @Override
    public void setVisible(boolean visible) {
        boolean active = visible && s.selectedId != null;
        boxMinDist.visible = active;
        boxMaxDist.visible = active;
        boxMinHeight.visible = active;
        boxMaxHeight.visible = active;
        boxMinLight.visible = active;
        boxMaxLight.visible = active;
        boxRules.visible = active;
        btnCategory.visible = active;
        btnEnableControl.visible = active;
        btnBlockAll.visible = active;
        btnInvertRules.visible = active;
    }

    @Override
    public boolean isMenuOpen(double mx, double my) {
        if (!showCategoryMenu) return false;
        int panelWidth = 120;
        int panelHeight = CATEGORIES.length * 20;
        int panelX = btnCategory.getX() + (btnCategory.getWidth() - panelWidth) / 2;
        int panelY = btnCategory.getY() + 22;
        return mx >= panelX
                && mx <= panelX + panelWidth
                && my >= panelY
                && my <= panelY + panelHeight;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        int sectionWidth = (s.rw - 10) / 3;

        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.min_dist"),
                s.rx,
                97,
                0xAAAAAA
        );
        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.max_dist"),
                s.rx + sectionWidth,
                97,
                0xAAAAAA
        );
        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.rules.label"),
                s.rx + sectionWidth * 2,
                97,
                0xAAAAAA
        );
        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.min_height"),
                s.rx,
                122,
                0xFFFFFF
        );
        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.max_height"),
                s.rx + sectionWidth,
                122,
                0xFFFFFF
        );
        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.min_light"),
                s.rx,
                147,
                0xFFFFFF
        );
        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.max_light"),
                s.rx + sectionWidth,
                147,
                0xFFFFFF
        );
        g.drawString(
                s.getFont(),
                Component.translatable("gui.entitycontrol.spawn.spawn.dim.list.desc"),
                s.rx,
                listY - 12,
                0xAAAAAA
        );

        g.fill(s.rx, listY, s.rx + s.rw, listY + listH, 0xFF111111);
        g.renderOutline(s.rx, listY, s.rw, listH, 0xFF333333);

        int visibleRows = listH / 18;
        dimensionScroll.update(knownDimensions.size(), visibleRows);
        int start = dimensionScroll.smoothIndexOffset();
        int shift = dimensionScroll.visualShift(18);
        int end = Math.min(start + visibleRows + 1, knownDimensions.size());

        s.enableCanvasScissor(g, s.rx, listY, s.rx + s.rw, listY + listH);
        for (int i = start; i < end; i++) {
            String dimension = knownDimensions.get(i);
            int rowY = listY + (i - start) * 18 - shift;
            boolean inWhitelist = node.dim_whitelist.contains(dimension);
            boolean inBlacklist = node.dim_blacklist.contains(dimension);

            g.fill(
                    s.rx + 1,
                    rowY,
                    s.rx + s.rw - 1,
                    rowY + 18,
                    (i % 2 == 0) ? 0xFF2C2C2C : 0xFF181818
            );
            if (mx >= s.rx
                    && mx < s.rx + s.rw
                    && my >= rowY
                    && my < rowY + 18) {
                g.fill(s.rx + 1, rowY, s.rx + s.rw - 1, rowY + 18, 0x33FFFFFF);
            }

            g.drawString(s.getFont(), dimension, s.rx + 5, rowY + 5, 0xFFAA00);

            int whitelistX = s.rx + s.rw - 45;
            int blacklistX = s.rx + s.rw - 20;

            g.fill(
                    whitelistX,
                    rowY + 2,
                    whitelistX + 18,
                    rowY + 16,
                    inWhitelist ? 0xFF00AA00 : 0xFF444444
            );
            g.renderOutline(whitelistX, rowY + 2, 18, 14, 0xFF222222);
            g.drawCenteredString(s.getFont(), "W", whitelistX + 9, rowY + 5, 0xFFFFFF);

            g.fill(
                    blacklistX,
                    rowY + 2,
                    blacklistX + 18,
                    rowY + 16,
                    inBlacklist ? 0xFFAA0000 : 0xFF444444
            );
            g.renderOutline(blacklistX, rowY + 2, 18, 14, 0xFF222222);
            g.drawCenteredString(s.getFont(), "B", blacklistX + 9, rowY + 5, 0xFFFFFF);
        }

        g.disableScissor();
        dimensionScroll.render(
                g,
                mx,
                my,
                s.rx + s.rw + 2,
                listY,
                4,
                listH,
                15
        );

        if (showCategoryMenu) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);

            int panelWidth = 120;
            int panelHeight = CATEGORIES.length * 20;
            int panelX = btnCategory.getX() + (btnCategory.getWidth() - panelWidth) / 2;
            int panelY = btnCategory.getY() + 22;

            g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF1C1C1C);
            g.renderOutline(panelX, panelY, panelWidth, panelHeight, 0xFFAA00);

            for (int i = 0; i < CATEGORIES.length; i++) {
                String category = CATEGORIES[i];
                int itemY = panelY + i * 20;
                boolean hover = mx >= panelX
                        && mx <= panelX + panelWidth
                        && my >= itemY
                        && my < itemY + 20;
                int background = hover ? 0xFF555555 : 0xFF1C1C1C;
                if (category.equals(node.category)) {
                    background = 0xFF22AA22;
                }

                g.fill(
                        panelX + 1,
                        itemY + 1,
                        panelX + panelWidth - 1,
                        itemY + 19,
                        background
                );
                g.drawCenteredString(
                        s.getFont(),
                        s.getTranslatedCategoryName(category),
                        panelX + panelWidth / 2,
                        itemY + 6,
                        0xFFFFFF
                );
            }

            g.pose().popPose();
        }
    }

    @Override
    public List<Component> getTooltip(int vMx, int vMy) {
        if (s.selectedId == null) return null;
        if (isMenuOpen(vMx, vMy)) return Collections.emptyList();

        if (vMx >= s.rx
                && vMx < s.rx + s.rw
                && vMy >= listY
                && vMy < listY + listH) {
            int shift = dimensionScroll.visualShift(18);
            int clickedRow = (vMy - listY + shift) / 18;
            int index = dimensionScroll.smoothIndexOffset() + clickedRow;

            if (index >= 0 && index < knownDimensions.size()) {
                double rowY = listY + clickedRow * 18 - shift;
                double relativeY = vMy - rowY;
                if (relativeY >= 2 && relativeY <= 16) {
                    if (vMx >= s.rx + s.rw - 45
                            && vMx <= s.rx + s.rw - 27) {
                        return Collections.singletonList(Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tooltip.dim_w"
                        ));
                    }
                    if (vMx >= s.rx + s.rw - 20
                            && vMx <= s.rx + s.rw - 2) {
                        return Collections.singletonList(Component.translatable(
                                "gui.entitycontrol.spawn.spawn.tooltip.dim_b"
                        ));
                    }
                    return Collections.singletonList(Component.translatable(
                            "gui.entitycontrol.spawn.spawn.tooltip.dim_id"
                    ));
                }
            }
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (s.selectedId == null) return false;

        if (showCategoryMenu) {
            int panelWidth = 120;
            int panelHeight = CATEGORIES.length * 20;
            int panelX = btnCategory.getX() + (btnCategory.getWidth() - panelWidth) / 2;
            int panelY = btnCategory.getY() + 22;

            if (mx >= btnCategory.getX()
                    && mx <= btnCategory.getX() + btnCategory.getWidth()
                    && my >= btnCategory.getY()
                    && my <= btnCategory.getY() + btnCategory.getHeight()) {
                return false;
            }

            if (mx < panelX
                    || mx > panelX + panelWidth
                    || my < panelY
                    || my > panelY + panelHeight) {
                showCategoryMenu = false;
                return true;
            }

            for (int i = 0; i < CATEGORIES.length; i++) {
                int itemY = panelY + i * 20;
                if (mx >= panelX
                        && mx <= panelX + panelWidth
                        && my >= itemY
                        && my < itemY + 20) {
                    BiomeSpawnConfig.EntityNode node = selectedNode();
                    if (node == null) return true;

                    String category = CATEGORIES[i];
                    if (!category.equals(node.category)) {
                        node.category = category;
                        s.markSelectedEntityEdited();
                        s.refreshSelectedEntitySearchData();
                    }

                    btnCategory.setMessage(
                            Component.translatable("gui.entitycontrol.spawn.spawn.category.cycle")
                                    .append(": ")
                                    .append(s.getTranslatedCategoryName(category))
                    );
                    showCategoryMenu = false;
                    return true;
                }
            }

            return true;
        }

        if (mx >= s.rx
                && mx < s.rx + s.rw
                && my >= listY
                && my < listY + listH) {
            int shift = dimensionScroll.visualShift(18);
            int clickedRow = (int) ((my - listY + shift) / 18);
            int index = dimensionScroll.smoothIndexOffset() + clickedRow;

            if (index < knownDimensions.size()) {
                String dimension = knownDimensions.get(index);
                BiomeSpawnConfig.EntityNode node = selectedNode();
                if (node == null) return false;

                double rowY = listY + clickedRow * 18 - shift;
                double relativeY = my - rowY;
                if (relativeY >= 2 && relativeY <= 16) {
                    if (mx >= s.rx + s.rw - 45
                            && mx <= s.rx + s.rw - 27) {
                        if (node.dim_whitelist.contains(dimension)) {
                            node.dim_whitelist.remove(dimension);
                        } else {
                            node.dim_whitelist.add(dimension);
                            node.dim_blacklist.remove(dimension);
                        }
                        s.markSelectedEntityEdited();
                        return true;
                    }

                    if (mx >= s.rx + s.rw - 20
                            && mx <= s.rx + s.rw - 2) {
                        if (node.dim_blacklist.contains(dimension)) {
                            node.dim_blacklist.remove(dimension);
                        } else {
                            node.dim_blacklist.add(dimension);
                            node.dim_whitelist.remove(dimension);
                        }
                        s.markSelectedEntityEdited();
                        return true;
                    }
                }
            }
        }

        return dimensionScroll.beginDrag(
                mx,
                my,
                s.rx + s.rw + 2,
                listY,
                4,
                listH,
                15,
                0
        );
    }

    @Override
    public boolean mouseDragged(
            double mx,
            double my,
            int btn,
            double dx,
            double dy
    ) {
        return dimensionScroll.drag(my, listY, listH, 15);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        return dimensionScroll.release(btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return my >= listY
                && my < listY + listH
                && dimensionScroll.scroll(delta);
    }
}
