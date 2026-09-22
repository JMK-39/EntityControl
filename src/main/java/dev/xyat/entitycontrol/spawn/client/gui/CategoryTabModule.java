package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CategoryTabModule implements ITabModule {
    private final SpawnControlScreen s;
    private NumericEditBox boxCatCap, boxCatWeight, boxCatRate;
    private StateButton btnReset;
    private String selectedCategory = null;
    private boolean isUpdating = false;

    private final int listY = 205;
    private final int listH = SpawnControlScreen.V_HEIGHT - 215;

    private final GridScrollController categoryScroll = new GridScrollController();

    public CategoryTabModule(SpawnControlScreen s) { this.s = s; }

    @Override
    public void init() {
        int inputY = 91;
        int inputW = 50;
        int labelOffset = 40;
        int sectionWidth = s.rw / 3;

        boxCatCap = s.addIntegerField(
                s.rx + labelOffset, inputY, inputW, Component.empty(),
                false, 0, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.cap")
        );
        boxCatCap.setResponder(v -> {
            if (isUpdating || selectedCategory == null || v.isEmpty()
                    || "-".equals(v) || ".".equals(v) || "-.".equals(v)) return;
            Integer value = boxCatCap.getIntValue();
            if (value != null) {
                s.profile.category_caps.put(selectedCategory, value);
            }
        });

        boxCatWeight = s.addIntegerField(
                s.rx + sectionWidth + labelOffset, inputY, inputW, Component.empty(),
                false, 0, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.weight")
        );
        boxCatWeight.setResponder(v -> {
            if (isUpdating || selectedCategory == null || v.isEmpty()
                    || "-".equals(v) || ".".equals(v) || "-.".equals(v)) return;
            Integer value = boxCatWeight.getIntValue();
            if (value != null) {
                s.profile.category_weights.put(selectedCategory, value);
            }
        });

        boxCatRate = s.addDecimalField(
                s.rx + sectionWidth * 2 + labelOffset, inputY, inputW, Component.empty(),
                false, 0D, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.rate")
        );
        boxCatRate.setResponder(v -> {
            if (isUpdating || selectedCategory == null || v.isEmpty()
                    || "-".equals(v) || ".".equals(v) || "-.".equals(v)) return;
            Double value = boxCatRate.getDoubleValue();
            if (value != null) {
                s.profile.category_spawn_rates.put(selectedCategory, value);
            }
        });

        btnReset = s.addButton(
                s.rx, 175, 100,
                Component.translatable("gui.entitycontrol.spawn.spawn.category.reset"),
                null,
                () -> {
                    if (selectedCategory != null) {
                        s.profile.category_weights.put(selectedCategory, getDefaultWeight(selectedCategory));
                        s.profile.category_spawn_rates.put(selectedCategory, 1.0);
                        for (net.minecraft.world.entity.MobCategory cat : net.minecraft.world.entity.MobCategory.values()) {
                            if (cat.getName().equals(selectedCategory)) {
                                s.profile.category_caps.put(selectedCategory, cat.getMaxInstancesPerChunk());
                                break;
                            }
                        }
                        selectCategory(selectedCategory);
                    }
                }
        );
    }

    private int getDefaultWeight(String cat) {
        return switch (cat) {
            case "monster" -> 100; case "creature" -> 60;
            case "ambient" -> 30; case "axolotls" -> 80;
            case "underground_water_creature" -> 80; case "water_creature" -> 60;
            case "water_ambient" -> 50; default -> 20;
        };
    }

    @Override
    public void updateSelection() {
        if (s.profile != null && !s.profile.category_caps.isEmpty()) {
            List<String> cList = new ArrayList<>(s.profile.category_caps.keySet());
            cList.sort(String::compareTo);
            if (selectedCategory == null || !cList.contains(selectedCategory)) {
                selectCategory(cList.get(0));
                return;
            }
        } else {
            selectedCategory = null;
        }
        setVisible(true);
    }

    public void selectCategory(String cat) {
        selectedCategory = cat; isUpdating = true;
        boxCatCap.setValue(String.valueOf(s.profile.category_caps.get(cat)));
        boxCatWeight.setValue(String.valueOf(s.profile.category_weights.get(cat)));
        boxCatRate.setValue(String.valueOf(s.profile.category_spawn_rates.get(cat)));
        isUpdating = false; setVisible(true);
    }

    @Override
    public void setVisible(boolean visible) {
        boolean active = visible && selectedCategory != null;
        if (boxCatCap != null) {
            boxCatCap.setVisible(active); boxCatWeight.setVisible(active); boxCatRate.setVisible(active);
        }
        if (btnReset != null) btnReset.setVisible(active);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.panelAlt(g, s.rx, listY, s.rw, listH);

        List<String> cList = new ArrayList<>(s.profile.category_caps.keySet());
        cList.sort(String::compareTo);

        int visibleRows = listH / 18;
        categoryScroll.update(cList.size(), visibleRows);
        int cStart = categoryScroll.smoothIndexOffset();
        int shift = categoryScroll.visualShift(18);
        int cEnd = Math.min(cStart + visibleRows + 1, cList.size());

        s.enableUiScissor(g, s.rx, listY, s.rx + s.rw, listY + listH);
        for (int i = cStart; i < cEnd; i++) {
            String c = cList.get(i); int y = listY + (i - cStart) * 18 - shift;
            boolean selected = c.equals(selectedCategory);
            boolean hovered = mx >= s.rx && mx < s.rx + s.rw && my >= y && my < y + 18;
            GuiTheme.stateSurface(
                    g, s.rx + 1, y, s.rw - 2, 18,
                    i % 2 == 0 ? GuiTheme.Surface.PANEL_ALT : GuiTheme.Surface.PANEL,
                    selected, hovered, false
            );
            g.drawString(s.getFont(), s.getTranslatedCategoryName(c), s.rx + 5, y + 5, 0xFFAA00);
        }

        s.disableUiScissor(g);
        categoryScroll.render(
                g, mx, my, s.rx + s.rw + 2, listY,
                4, listH, 15
        );

        if (selectedCategory != null) {
            int sectionWidth = s.rw / 3;
            int labelY = 97;
            g.drawString(s.getFont(), Component.translatable("gui.entitycontrol.spawn.spawn.cap"), s.rx, labelY, 0xAAAAAA);
            g.drawString(s.getFont(), Component.translatable("gui.entitycontrol.spawn.spawn.weight"), s.rx + sectionWidth, labelY, 0xAAAAAA);
            g.drawString(s.getFont(), Component.translatable("gui.entitycontrol.spawn.spawn.rate"), s.rx + sectionWidth * 2, labelY, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (mx >= s.rx && mx < s.rx + s.rw && my >= listY && my < listY + listH) {
            int clickedRow = (int) ((my - listY + categoryScroll.visualShift(18)) / 18);
            int idx = categoryScroll.smoothIndexOffset() + clickedRow;
            List<String> cList = new ArrayList<>(s.profile.category_caps.keySet());
            cList.sort(String::compareTo);
            if (idx < cList.size()) { selectCategory(cList.get(idx)); return true; }
        }
        return categoryScroll.beginDrag(
                mx, my, s.rx + s.rw + 2, listY,
                4, listH, 15, 0
        );
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return categoryScroll.drag(my, listY, listH, 15);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        return categoryScroll.release(btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return my >= listY
                && my < listY + listH
                && categoryScroll.scroll(delta);
    }
}
