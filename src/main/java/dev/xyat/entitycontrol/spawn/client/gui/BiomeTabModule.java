package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BiomeTabModule implements ITabModule {
    private final SpawnControlScreen s;
    private final List<String> allBiomes = new ArrayList<>();
    private final List<String> visibleBiomes = new ArrayList<>();
    private final Map<String, String> biomeSearchData = new HashMap<>();
    private final GridScrollController biomeScroll = new GridScrollController();
    private final Map<String, StateButton> deleteButtons = new HashMap<>();

    private NumericEditBox boxWeight;
    private NumericEditBox boxMin;
    private NumericEditBox boxMax;
    private KineticEditBox boxSearchBiome;

    private String selectedBiome;
    private String lastQuery;
    private boolean lastSearchMode;
    private boolean isUpdating;

    private final int listY = 205;
    private final int listH = SpawnControlScreen.V_HEIGHT - 215;

    public BiomeTabModule(SpawnControlScreen s) {
        this.s = s;

        try {
            var level = KineticClientRuntime.currentLevel();
            if (level != null) {
                var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
                biomeRegistry.keySet().forEach(location -> {
                    if (location != null) {
                        allBiomes.add(location.toString());
                    }
                });
                allBiomes.sort(String::compareTo);
            }
        } catch (Exception ignored) {
        }

        for (String biome : allBiomes) {
            String name = s.getTranslatedBiomeName(biome).toLowerCase(Locale.ROOT);
            biomeSearchData.put(
                    biome,
                    (biome + " " + name + " " + KineticSearch.pinyin(name))
                            .toLowerCase(Locale.ROOT)
            );
        }
    }

    @Override
    public void init() {
        int inputY = 91;
        int inputW = 50;
        int labelOffset = 25;
        int sectionWidth = 90;

        boxWeight = s.addIntegerField(
                s.rx + labelOffset, inputY, inputW, Component.empty(),
                false, 0, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.weight")
        );
        boxWeight.setMaxLength(11);
        boxWeight.setResponder(value -> updateSpawnerNumber(value, 0));

        boxMin = s.addIntegerField(
                s.rx + sectionWidth + labelOffset, inputY, inputW, Component.empty(),
                false, 0, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.min")
        );
        boxMin.setMaxLength(11);
        boxMin.setResponder(value -> updateSpawnerNumber(value, 1));

        boxMax = s.addIntegerField(
                s.rx + sectionWidth * 2 + labelOffset, inputY, inputW, Component.empty(),
                false, 0, null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.max")
        );
        boxMax.setMaxLength(11);
        boxMax.setResponder(value -> updateSpawnerNumber(value, 2));

        boxSearchBiome = s.addTextField(
                s.rx, listY - 23, 220, Component.empty(),
                null, null,
                Component.translatable("gui.entitycontrol.spawn.spawn.tooltip.biome_search")
        );
        boxSearchBiome.setPlaceholder(Component.translatable("gui.entitycontrol.spawn.spawn.biome.search_hint"));
        boxSearchBiome.setResponder(value -> invalidateVisibleBiomes(true));
    }

    private BiomeSpawnConfig.EntityNode selectedNode() {
        if (s.selectedId == null) return null;
        return s.profile.entities.get(s.selectedId);
    }

    private BiomeSpawnConfig.SpawnerDataNode selectedSpawnerData() {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null || selectedBiome == null) return null;
        return node.biomes.get(selectedBiome);
    }

    private void updateSpawnerNumber(String raw, int field) {
        if (isUpdating || selectedBiome == null || raw.isEmpty() || "-".equals(raw)) {
            return;
        }

        NumericEditBox box = switch (field) {
            case 0 -> boxWeight;
            case 1 -> boxMin;
            default -> boxMax;
        };

        Integer value = box.getIntValue();
        BiomeSpawnConfig.SpawnerDataNode data = selectedSpawnerData();
        if (data == null) return;

        if (value == null) {
            KineticOverlays.toast(Component.translatable(
                    "msg.entitycontrol.spawn.invalid_number"
            ));
            restoreSpawnerBoxes(data);
            return;
        }

        boolean changed = false;
        if (field == 0 && data.weight != value) {
            data.weight = value;
            changed = true;
        } else if (field == 1 && data.min != value) {
            data.min = value;
            if (data.max < value) {
                data.max = value;
            }
            changed = true;
        } else if (field == 2 && data.max != value) {
            data.max = value;
            if (data.min > value) {
                data.min = value;
            }
            changed = true;
        }

        if (changed) {
            s.markSelectedEntityEdited();
            restoreSpawnerBoxes(data);
        }
    }

    private void restoreSpawnerBoxes(BiomeSpawnConfig.SpawnerDataNode data) {
        isUpdating = true;
        boxWeight.setIntValue(data.weight);
        boxMin.setIntValue(data.min);
        boxMax.setIntValue(data.max);
        isUpdating = false;
    }

    @Override
    public void updateSelection() {
        selectedBiome = null;
        if (boxSearchBiome != null) {
            isUpdating = true;
            boxSearchBiome.setValue("");
            isUpdating = false;
        }

        isUpdating = true;
        if (boxWeight != null) {
            boxWeight.setValue("");
            boxMin.setValue("");
            boxMax.setValue("");
        }
        isUpdating = false;

        invalidateVisibleBiomes(true);
        setVisible(true);
    }

    public void selectBiome(String biome) {
        selectedBiome = biome;
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        BiomeSpawnConfig.SpawnerDataNode data = node.biomes.get(biome);
        isUpdating = true;
        if (data != null) {
            boxWeight.setIntValue(data.weight);
            boxMin.setIntValue(data.min);
            boxMax.setIntValue(data.max);
        }
        isUpdating = false;
        setVisible(true);
    }

    private void invalidateVisibleBiomes(boolean resetScroll) {
        lastQuery = null;
        if (resetScroll) {
            biomeScroll.reset();
        }
    }

    private void refreshVisibleBiomesIfNeeded() {
        if (boxSearchBiome == null) return;

        String query = boxSearchBiome.getValue().toLowerCase(Locale.ROOT).trim();
        boolean searchMode = s.isControlFocused(boxSearchBiome) || !query.isEmpty();

        if (query.equals(lastQuery) && searchMode == lastSearchMode) {
            return;
        }

        lastQuery = query;
        lastSearchMode = searchMode;
        visibleBiomes.clear();

        if (searchMode) {
            for (String biome : allBiomes) {
                if (query.isEmpty()
                        || KineticSearch.match(
                        biomeSearchData.getOrDefault(biome, biome),
                        query
                )) {
                    visibleBiomes.add(biome);
                }
            }
        } else {
            BiomeSpawnConfig.EntityNode node = selectedNode();
            if (node != null) {
                visibleBiomes.addAll(node.biomes.keySet());
                visibleBiomes.sort(String::compareTo);
            }
        }

        biomeScroll.update(visibleBiomes.size(), listH / 18);
    }

    @Override
    public void setVisible(boolean visible) {
        boolean active = visible && s.selectedId != null;
        if (boxSearchBiome != null) {
            boxSearchBiome.setVisible(active);
        }

        boolean showEdit = active && selectedBiome != null;
        if (boxWeight != null) {
            boxWeight.setVisible(showEdit);
            boxMin.setVisible(showEdit);
            boxMax.setVisible(showEdit);
        }
    }

    private StateButton deleteButton(String biome, int y) {
        StateButton button = deleteButtons.computeIfAbsent(biome, id -> {
            StateButton created = KineticWidgets.createCompactButton(
                    0,
                    0,
                    16,
                    Component.translatable("gui.entitycontrol.modifier.modifier.remove_mark"),
                    null,
                    () -> removeBiome(id)
            );
            created.setError(true);
            return created;
        });
        button.setX(s.rx + s.rw - 21);
        button.setY(y + 1);
        button.setWidth(16);
        button.setClipBounds(s.rx, listY, s.rx + s.rw, listY + listH);
        return button;
    }

    private void removeBiome(String biome) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null || !node.biomes.containsKey(biome)) return;
        node.biomes.remove(biome);
        if (!node.deleted_biomes.contains(biome)) {
            node.deleted_biomes.add(biome);
        }
        s.markSelectedEntityEdited();
        if (biome.equals(selectedBiome)) {
            selectedBiome = null;
            setVisible(true);
        }
        invalidateVisibleBiomes(false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        refreshVisibleBiomesIfNeeded();

        int sectionWidth = 90;
        if (selectedBiome != null) {
            g.drawString(
                    s.getFont(),
                    Component.translatable("gui.entitycontrol.spawn.spawn.weight"),
                    s.rx,
                    97,
                    0xAAAAAA
            );
            g.drawString(
                    s.getFont(),
                    Component.translatable("gui.entitycontrol.spawn.spawn.min"),
                    s.rx + sectionWidth,
                    97,
                    0xAAAAAA
            );
            g.drawString(
                    s.getFont(),
                    Component.translatable("gui.entitycontrol.spawn.spawn.max"),
                    s.rx + sectionWidth * 2,
                    97,
                    0xAAAAAA
            );
        }

        GuiTheme.panelAlt(g, s.rx, listY, s.rw, listH);

        biomeScroll.update(visibleBiomes.size(), listH / 18);
        int start = biomeScroll.smoothIndexOffset();
        int shift = biomeScroll.visualShift(18);
        int end = Math.min(start + listH / 18 + 1, visibleBiomes.size());

        s.enableUiScissor(g, s.rx, listY, s.rx + s.rw, listY + listH);
        for (int i = start; i < end; i++) {
            String biome = visibleBiomes.get(i);
            int y = listY + (i - start) * 18 - shift;
            BiomeSpawnConfig.SpawnerDataNode data = node.biomes.get(biome);
            boolean added = data != null;

            boolean selected = biome.equals(selectedBiome);
            boolean hovered = mx >= s.rx
                    && mx < s.rx + s.rw
                    && my >= y
                    && my < y + 18;
            GuiTheme.stateSurface(
                    g,
                    s.rx + 1,
                    y,
                    s.rw - 2,
                    18,
                    i % 2 == 0 ? GuiTheme.Surface.PANEL_ALT : GuiTheme.Surface.PANEL,
                    selected,
                    hovered,
                    false
            );

            g.drawString(
                    s.getFont(),
                    s.getTranslatedBiomeName(biome),
                    s.rx + 5,
                    y + 5,
                    added ? 0xFFAA00 : 0xFFFFFF
            );

            if (added) {
                String suffix = "[W:" + data.weight + " M:" + data.min + " X:" + data.max + "]";
                g.drawString(
                        s.getFont(),
                        suffix,
                        s.rx + s.rw - s.getFont().width(suffix) - 25,
                        y + 5,
                        0xAAAAAA
                );

                StateButton deleteButton = deleteButton(biome, y);
                KineticWidgets.renderControl(deleteButton, g, mx, my, pt);
            } else {
                g.drawString(
                        s.getFont(),
                        Component.translatable("gui.entitycontrol.spawn.spawn.biome.add"),
                        s.rx + s.rw - 35,
                        y + 5,
                        0x55FF55
                );
            }
        }

        s.disableUiScissor(g);
        biomeScroll.render(
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
    public boolean mouseClicked(double mx, double my, int btn) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return false;

        refreshVisibleBiomesIfNeeded();

        if (mx >= s.rx
                && mx < s.rx + s.rw
                && my >= listY
                && my < listY + listH) {
            int clickedRow = (int) ((my - listY + biomeScroll.visualShift(18)) / 18);
            int index = biomeScroll.smoothIndexOffset() + clickedRow;

            if (index >= 0 && index < visibleBiomes.size()) {
                String biome = visibleBiomes.get(index);
                boolean added = node.biomes.containsKey(biome);

                if (!added) {
                    node.biomes.put(biome, new BiomeSpawnConfig.SpawnerDataNode());
                    node.deleted_biomes.remove(biome);
                    s.markSelectedEntityEdited();
                    boxSearchBiome.setValue("");
                    s.blurControl(boxSearchBiome);
                    invalidateVisibleBiomes(true);
                    selectBiome(biome);
                } else {
                    int rowY = listY + clickedRow * 18 - biomeScroll.visualShift(18);
                    if (deleteButton(biome, rowY).mouseClicked(mx, my, btn)) return true;
                    selectBiome(biome);
                }

                return true;
            }
        }

        return biomeScroll.beginDrag(
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
        return biomeScroll.drag(my, listY, listH, 15);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        return biomeScroll.release(btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return mx >= s.rx
                && mx < s.rx + s.rw
                && my >= listY
                && my < listY + listH
                && biomeScroll.scroll(delta);
    }
}
