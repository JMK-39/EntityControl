package dev.xyat.entitycontrol.spawn.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, StateButton> whitelistButtons = new HashMap<>();
    private final Map<String, StateButton> blacklistButtons = new HashMap<>();

    private NumericEditBox boxMinDist;
    private NumericEditBox boxMaxDist;
    private NumericEditBox boxMinHeight;
    private NumericEditBox boxMaxHeight;
    private NumericEditBox boxMinLight;
    private NumericEditBox boxMaxLight;
    private KineticEditBox boxRules;
    private StateButton btnEnableControl;
    private StateButton btnBlockAll;
    private StateButton btnInvertRules;
    private StateButton btnCategory;

    private boolean isUpdating;

    private final int listY = 215;
    private final int listH = SpawnControlScreen.V_HEIGHT - 225;

    public RuleTabModule(SpawnControlScreen s) {
        this.s = s;
        KineticClientRuntime.knownLevels().forEach(
                key -> knownDimensions.add(key.location().toString())
        );
        knownDimensions.sort(String::compareTo);
    }

    @Override
    public void init() {
        int sectionWidth = (s.rw - 10) / 3;
        int row1Y = 91;
        int row2Y = 116;
        int row3Y = 141;
        int row4Y = 166;

        boxMinDist = s.addIntegerField(
                s.rx + 47, row1Y, 50, Component.empty(),
                false, 0, 128, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.min_dist")
        );
        boxMinDist.setMaxLength(3);
        boxMinDist.setResponder(this::updateMinDistance);

        boxMaxDist = s.addIntegerField(
                s.rx + sectionWidth + 47, row1Y, 50, Component.empty(),
                false, 0, 128, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.max_dist")
        );
        boxMaxDist.setMaxLength(3);
        boxMaxDist.setResponder(this::updateMaxDistance);

        int rulesX = s.rx + sectionWidth * 2 + 42;
        boxRules = s.addTextField(
                rulesX, row1Y, Math.max(45, s.rx + s.rw - rulesX), Component.empty(),
                null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.rules")
        );
        boxRules.setMaxLength(20);
        boxRules.setResponder(this::updateRules);

        boxMinHeight = s.addIntegerField(
                s.rx + 47, row2Y, 50, Component.empty(),
                true, null, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.min_height")
        );
        boxMinHeight.setMaxLength(11);
        boxMinHeight.setResponder(value -> updateHeight(value, true));

        boxMaxHeight = s.addIntegerField(
                s.rx + sectionWidth + 47, row2Y, 50, Component.empty(),
                true, null, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.max_height")
        );
        boxMaxHeight.setMaxLength(11);
        boxMaxHeight.setResponder(value -> updateHeight(value, false));

        btnCategory = s.addButton(
                s.rx + (sectionWidth + 5) * 2,
                row2Y,
                sectionWidth,
                Component.empty(),
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.category"),
                this::openCategoryMenu
        );

        boxMinLight = s.addIntegerField(
                s.rx + 47, row3Y, 50, Component.empty(),
                false, 0, 15, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.min_light")
        );
        boxMinLight.setMaxLength(2);
        boxMinLight.setResponder(value -> updateLight(value, true));

        boxMaxLight = s.addIntegerField(
                s.rx + sectionWidth + 47, row3Y, 50, Component.empty(),
                false, 0, 15, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.max_light")
        );
        boxMaxLight.setMaxLength(2);
        boxMaxLight.setResponder(value -> updateLight(value, false));

        btnEnableControl = s.addButton(
                s.rx, row4Y, sectionWidth, Component.empty(), null,
                () -> {
                    BiomeSpawnConfig.EntityNode node = selectedNode();
                    if (node == null) return;
                    node.enable_control = !node.enable_control;
                    s.markSelectedEntityEdited();
                    updateBtnState(node);
                }
        );

        btnBlockAll = s.addButton(
                s.rx + sectionWidth + 5, row4Y, sectionWidth, Component.empty(), null,
                () -> {
                    BiomeSpawnConfig.EntityNode node = selectedNode();
                    if (node == null) return;
                    node.block_all = !node.block_all;
                    s.markSelectedEntityEdited();
                    updateBtnState(node);
                }
        );

        btnInvertRules = s.addButton(
                s.rx + (sectionWidth + 5) * 2, row4Y, sectionWidth, Component.empty(), null,
                () -> {
                    BiomeSpawnConfig.EntityNode node = selectedNode();
                    if (node == null) return;
                    node.invert_rules = !node.invert_rules;
                    s.markSelectedEntityEdited();
                    updateBtnState(node);
                }
        );
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
        KineticOverlays.toast(Component.translatable(
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
        btnEnableControl.setText(
                Component.translatable("gui.entitycontrol.spawn.spawn.enabled.short")
                        .append(": ")
                        .append(enabledText)
        );
        s.registerWidgetTooltip(btnEnableControl, Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.enable",
                enabledText
        ));

        Component blockText = Component.translatable(
                node.block_all
                        ? "gui.entitycontrol.spawn.spawn.status.yes.colored"
                        : "gui.entitycontrol.spawn.spawn.status.no.colored"
        ).withStyle(node.block_all ? ChatFormatting.RED : ChatFormatting.GREEN);
        btnBlockAll.setText(
                Component.translatable("gui.entitycontrol.spawn.spawn.block.short")
                        .append(": ")
                        .append(blockText)
        );
        s.registerWidgetTooltip(btnBlockAll, Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.block",
                blockText
        ));

        Component invertText = Component.translatable(
                node.invert_rules
                        ? "gui.entitycontrol.spawn.spawn.status.blacklist.colored"
                        : "gui.entitycontrol.spawn.spawn.status.whitelist.colored"
        ).withStyle(node.invert_rules ? ChatFormatting.RED : ChatFormatting.GREEN);
        btnInvertRules.setText(
                Component.translatable("gui.entitycontrol.spawn.spawn.invert.short")
                        .append(": ")
                        .append(invertText)
        );
        s.registerWidgetTooltip(btnInvertRules, Component.translatable(
                "gui.entitycontrol.spawn.spawn.tooltip.invert",
                invertText
        ));
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
        btnCategory.setText(
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
        boxMinDist.setVisible(active);
        boxMaxDist.setVisible(active);
        boxMinHeight.setVisible(active);
        boxMaxHeight.setVisible(active);
        boxMinLight.setVisible(active);
        boxMaxLight.setVisible(active);
        boxRules.setVisible(active);
        btnCategory.setVisible(active);
        btnEnableControl.setVisible(active);
        btnBlockAll.setVisible(active);
        btnInvertRules.setVisible(active);
    }

    private void openCategoryMenu() {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        for (String category : CATEGORIES) {
            items.add(KineticOverlays.MenuItem.toggle(
                    Component.translatable("gui.entitycontrol.spawn.spawn.category." + category),
                    Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.category"),
                    category.equals(node.category),
                    () -> selectCategory(category)
            ));
        }
        s.openContextMenu(
                btnCategory.getX(),
                btnCategory.getY() + btnCategory.getHeight(),
                items
        );
    }

    private void selectCategory(String category) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null || category.equals(node.category)) return;
        node.category = category;
        s.markSelectedEntityEdited();
        s.refreshSelectedEntitySearchData();
        btnCategory.setText(
                Component.translatable("gui.entitycontrol.spawn.spawn.category.cycle")
                        .append(": ")
                        .append(s.getTranslatedCategoryName(category))
        );
    }

    private StateButton dimensionButton(String dimension, boolean whitelist, int x, int y) {
        Map<String, StateButton> buttons = whitelist ? whitelistButtons : blacklistButtons;
        StateButton button = buttons.computeIfAbsent(dimension, id -> KineticWidgets.createCompactButton(
                0,
                0,
                18,
                Component.translatable(whitelist
                        ? "gui.entitycontrol.spawn.spawn.dim_whitelist_mark"
                        : "gui.entitycontrol.spawn.spawn.dim_blacklist_mark"),
                Component.translatable(whitelist
                        ? "gui.entitycontrol.spawn.spawn.tooltip.dim_w"
                        : "gui.entitycontrol.spawn.spawn.tooltip.dim_b"),
                () -> toggleDimension(id, whitelist)
        ));
        button.setX(x);
        button.setY(y);
        button.setWidth(18);
        button.setClipBounds(s.rx, listY, s.rx + s.rw, listY + listH);
        BiomeSpawnConfig.EntityNode node = selectedNode();
        boolean active = node != null && (whitelist
                ? node.dim_whitelist.contains(dimension)
                : node.dim_blacklist.contains(dimension));
        button.setSelected(whitelist && active);
        button.setError(!whitelist && active);
        return button;
    }

    private void toggleDimension(String dimension, boolean whitelist) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        List<String> target = whitelist ? node.dim_whitelist : node.dim_blacklist;
        List<String> opposite = whitelist ? node.dim_blacklist : node.dim_whitelist;
        if (target.contains(dimension)) {
            target.remove(dimension);
        } else {
            target.add(dimension);
            opposite.remove(dimension);
        }
        s.markSelectedEntityEdited();
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

        GuiTheme.panelAlt(g, s.rx, listY, s.rw, listH);

        int visibleRows = listH / 18;
        dimensionScroll.update(knownDimensions.size(), visibleRows);
        int start = dimensionScroll.smoothIndexOffset();
        int shift = dimensionScroll.visualShift(18);
        int end = Math.min(start + visibleRows + 1, knownDimensions.size());

        s.enableUiScissor(g, s.rx, listY, s.rx + s.rw, listY + listH);
        for (int i = start; i < end; i++) {
            String dimension = knownDimensions.get(i);
            int rowY = listY + (i - start) * 18 - shift;
            boolean hovered = mx >= s.rx
                    && mx < s.rx + s.rw
                    && my >= rowY
                    && my < rowY + 18;
            GuiTheme.stateSurface(
                    g,
                    s.rx + 1,
                    rowY,
                    s.rw - 2,
                    18,
                    i % 2 == 0 ? GuiTheme.Surface.PANEL_ALT : GuiTheme.Surface.PANEL,
                    false,
                    hovered,
                    false
            );

            g.drawString(s.getFont(), dimension, s.rx + 5, rowY + 5, 0xFFAA00);

            int whitelistX = s.rx + s.rw - 45;
            int blacklistX = s.rx + s.rw - 20;

            StateButton whitelistButton = dimensionButton(dimension, true, whitelistX, rowY + 1);
            StateButton blacklistButton = dimensionButton(dimension, false, blacklistX, rowY + 1);
            KineticWidgets.renderControl(whitelistButton, g, mx, my, pt);
            KineticWidgets.renderControl(blacklistButton, g, mx, my, pt);
        }

        s.disableUiScissor(g);
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
    }

    @Override
    public List<Component> getTooltip(int vMx, int vMy) {
        if (s.selectedId == null) return null;

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

                int rowY = listY + clickedRow * 18 - shift;
                StateButton whitelistButton = dimensionButton(
                        dimension, true, s.rx + s.rw - 45, rowY + 1
                );
                if (whitelistButton.mouseClicked(mx, my, btn)) return true;
                StateButton blacklistButton = dimensionButton(
                        dimension, false, s.rx + s.rw - 20, rowY + 1
                );
                if (blacklistButton.mouseClicked(mx, my, btn)) return true;
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
