package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.list.ActionItem;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BiomeTabModule implements ITabModule {
    private final SpawnControlScreen screen;
    private final List<String> allBiomes = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private String selectedBiome;
    private String searchQuery = "";
    private boolean updatingNumbers;
    private KineticNumberField weightField;
    private KineticNumberField minField;
    private KineticNumberField maxField;

    BiomeTabModule(SpawnControlScreen screen) {
        this.screen = screen;
        try {
            var level = KineticClientRuntime.currentLevel();
            if (level != null) {
                var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
                registry.keySet().forEach(id -> allBiomes.add(id.toString()));
                allBiomes.sort(String::compareToIgnoreCase);
            }
        } catch (RuntimeException ignored) {
        }
        for (String biome : allBiomes) {
            String name = screen.getTranslatedBiomeName(biome).toLowerCase(Locale.ROOT);
            String raw = biome + " " + name;
            searchData.put(biome, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private BiomeSpawnConfig.EntityNode selectedNode() {
        return screen.selectedNode();
    }

    @Override
    public void build(KineticUi ui) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        if (node.biomes == null) node.biomes = new java.util.LinkedHashMap<>();
        if (node.deleted_biomes == null) node.deleted_biomes = new ArrayList<>();
        if (selectedBiome != null && !node.biomes.containsKey(selectedBiome)) selectedBiome = null;

        int x = screen.rightX();
        int w = screen.rightWidth();
        int section = 90;
        BiomeSpawnConfig.SpawnerDataNode data = selectedBiome == null ? null : node.biomes.get(selectedBiome);

        weightField = ui.numberField(x + 25, 91, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.weight"))
                .allowNegative(false).range(0, null).value(data == null ? null : data.weight)
                .enabled(data != null)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.weight"))
                .onChange(raw -> updateNumber(raw, 0)).firstShownTextAsDefault().build();
        minField = ui.numberField(x + section + 25, 91, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min"))
                .allowNegative(false).range(0, null).value(data == null ? null : data.min)
                .enabled(data != null)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.min"))
                .onChange(raw -> updateNumber(raw, 1)).firstShownTextAsDefault().build();
        maxField = ui.numberField(x + section * 2 + 25, 91, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max"))
                .allowNegative(false).range(0, null).value(data == null ? null : data.max)
                .enabled(data != null)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.max"))
                .onChange(raw -> updateNumber(raw, 2)).firstShownTextAsDefault().build();

        ui.textField(x, 182, 220)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.biome.search_hint"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.biome.search_hint"))
                .value(searchQuery).maxLength(256)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.biome_search"))
                .onChange(value -> {
                    searchQuery = value == null ? "" : value;
                    screen.rebuildPage();
                }).firstShownTextAsDefault().build();

        List<String> visible = visibleBiomes(node);
        List<ActionItem> rows = new ArrayList<>();
        for (String biome : visible) {
            BiomeSpawnConfig.SpawnerDataNode rowData = node.biomes.get(biome);
            boolean configured = rowData != null;
            Component secondary = configured
                    ? KineticI18n.translatable("gui.entitycontrol.spawn.spawn.biome.summary", rowData.weight, rowData.min, rowData.max)
                    : Component.literal(biome);
            rows.add(new ActionItem(
                    Component.literal(screen.getTranslatedBiomeName(biome)),
                    secondary,
                    Component.literal(biome),
                    true,
                    false,
                    KineticI18n.translatable(configured
                            ? "gui.entitycontrol.spawn.spawn.biome.remove"
                            : "gui.entitycontrol.spawn.spawn.biome.add"),
                    KineticI18n.translatable(configured
                            ? "gui.entitycontrol.spawn.spawn.biome.remove.tooltip"
                            : "gui.entitycontrol.spawn.spawn.tooltip.biome_search"),
                    true,
                    configured
            ));
        }
        ui.actionList(x, 205, w, SpawnControlScreen.V_HEIGHT - 215, rows)
                .actionWidth(52)
                .selected(selectedBiome == null ? -1 : visible.indexOf(selectedBiome))
                .onSelect(index -> {
                    if (index < 0 || index >= visible.size()) return;
                    String biome = visible.get(index);
                    if (!node.biomes.containsKey(biome)) return;
                    selectedBiome = biome;
                    screen.rebuildPage();
                })
                .onAction(index -> {
                    if (index < 0 || index >= visible.size()) return;
                    toggleBiome(node, visible.get(index));
                }).build();
    }

    private List<String> visibleBiomes(BiomeSpawnConfig.EntityNode node) {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (query.isEmpty()) {
            result.addAll(node.biomes.keySet());
            result.sort(String::compareToIgnoreCase);
            return result;
        }
        for (String biome : allBiomes) {
            String haystack = searchData.getOrDefault(biome, biome.toLowerCase(Locale.ROOT));
            if (KineticSearch.match(haystack, query)) result.add(biome);
        }
        return result;
    }

    private void toggleBiome(BiomeSpawnConfig.EntityNode node, String biome) {
        if (node.biomes.containsKey(biome)) {
            node.biomes.remove(biome);
            if (!node.deleted_biomes.contains(biome)) node.deleted_biomes.add(biome);
            if (biome.equals(selectedBiome)) selectedBiome = null;
        } else {
            node.biomes.put(biome, new BiomeSpawnConfig.SpawnerDataNode());
            node.deleted_biomes.remove(biome);
            selectedBiome = biome;
            searchQuery = "";
        }
        screen.markSelectedEntityEdited();
        screen.rebuildPage();
    }

    private void updateNumber(String raw, int field) {
        if (updatingNumbers || selectedBiome == null || raw == null || raw.isBlank()) return;
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        BiomeSpawnConfig.SpawnerDataNode data = node.biomes.get(selectedBiome);
        if (data == null) return;
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (value < 0) return;
        updatingNumbers = true;
        if (field == 0) {
            data.weight = value;
        } else if (field == 1) {
            data.min = value;
            if (data.max < value) {
                data.max = value;
                maxField.setIntValue(value);
            }
        } else {
            data.max = value;
            if (data.min > value) {
                data.min = value;
                minField.setIntValue(value);
            }
        }
        updatingNumbers = false;
        screen.markSelectedEntityEdited();
    }

    @Override
    public void updateSelection() {
        selectedBiome = null;
        searchQuery = "";
    }

    @Override
    public void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.rightX();
        int color = KineticTheme.current().mutedText();
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.weight"), x, 95, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min"), x + 90, 95, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max"), x + 180, 95, color);
    }
}
