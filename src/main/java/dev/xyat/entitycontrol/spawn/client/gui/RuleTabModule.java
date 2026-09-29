package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.list.MultiToggleItem;
import dev.xyat.kineticcore.api.client.gui.widget.list.RowToggle;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RuleTabModule implements ITabModule {
    private static final String[] CATEGORIES = {
            "monster", "creature", "ambient", "axolotls",
            "underground_water_creature", "water_creature", "water_ambient", "misc"
    };

    private final SpawnControlScreen screen;
    private final List<String> knownDimensions = new ArrayList<>();
    private boolean updatingNumbers;

    RuleTabModule(SpawnControlScreen screen) {
        this.screen = screen;
        KineticClientRuntime.knownLevels().forEach(key -> knownDimensions.add(key.location().toString()));
        knownDimensions.sort(String::compareToIgnoreCase);
    }

    private BiomeSpawnConfig.EntityNode selectedNode() {
        return screen.selectedNode();
    }

    @Override
    public void build(KineticUi ui) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;

        int x = screen.rightX();
        int w = screen.rightWidth();
        int section = (w - 10) / 3;

        KineticNumberField minDist = ui.numberField(x + 47, 91, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min_dist"))
                .allowNegative(false).range(0, 128).value(node.min_spawn_distance)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.min_dist"))
                .onChange(raw -> updateDistance(raw, true)).firstShownTextAsDefault().build();
        KineticNumberField maxDist = ui.numberField(x + section + 52, 91, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max_dist"))
                .allowNegative(false).range(0, 128).value(node.max_spawn_distance)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.max_dist"))
                .onChange(raw -> updateDistance(raw, false)).firstShownTextAsDefault().build();

        ui.textField(x + section * 2 + 42, 91, Math.max(45, x + w - (x + section * 2 + 42)))
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.rules.label"))
                .value(node.rules == null ? "" : node.rules)
                .maxLength(20)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.rules"))
                .onChange(this::updateRules).firstShownTextAsDefault().build();

        KineticNumberField minHeight = ui.numberField(x + 47, 116, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min_height"))
                .allowNegative(true).value(node.min_spawn_height)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.min_height"))
                .onChange(raw -> updateNullablePair(raw, true, false)).firstShownTextAsDefault().build();
        KineticNumberField maxHeight = ui.numberField(x + section + 52, 116, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max_height"))
                .allowNegative(true).value(node.max_spawn_height)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.max_height"))
                .onChange(raw -> updateNullablePair(raw, false, false)).firstShownTextAsDefault().build();

        List<KineticDropdown.Option> categoryOptions = new ArrayList<>();
        for (String category : CATEGORIES) {
            categoryOptions.add(new KineticDropdown.Option(
                    category,
                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.category." + category)
            ));
        }
        ui.dropdown(x + (section + 5) * 2, 116, section, categoryOptions)
                .selected(normalizeCategory(node.category))
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.category"))
                .onChange(category -> {
                    if (category == null || category.equals(node.category)) return;
                    node.category = category;
                    screen.markSelectedEntityEdited();
                    screen.refreshSelectedEntitySearchData();
                }).build();

        KineticNumberField minLight = ui.numberField(x + 47, 141, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min_light"))
                .allowNegative(false).range(0, 15).value(node.min_spawn_light)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.min_light"))
                .onChange(raw -> updateNullablePair(raw, true, true)).firstShownTextAsDefault().build();
        KineticNumberField maxLight = ui.numberField(x + section + 52, 141, 50, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max_light"))
                .allowNegative(false).range(0, 15).value(node.max_spawn_light)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.max_light"))
                .onChange(raw -> updateNullablePair(raw, false, true)).firstShownTextAsDefault().build();

        // Keep references alive for counterpart correction without rebuilding while the user types.
        screen.bindRuleNumberFields(minDist, maxDist, minHeight, maxHeight, minLight, maxLight);

        ui.toggle(x, 166, section).compact()
                .value(node.enable_control)
                .labels(
                        labeled("gui.entitycontrol.spawn.spawn.enabled.short", "gui.entitycontrol.spawn.spawn.status.active.colored"),
                        labeled("gui.entitycontrol.spawn.spawn.enabled.short", "gui.entitycontrol.spawn.spawn.status.ignored.colored")
                )
                .tooltip(() -> KineticI18n.translatable(
                        "gui.entitycontrol.spawn.spawn.tooltip.enable",
                        KineticI18n.translatable(node.enable_control
                                ? "gui.entitycontrol.spawn.spawn.status.active.colored"
                                : "gui.entitycontrol.spawn.spawn.status.ignored.colored")
                ))
                .onChange(value -> {
                    node.enable_control = value;
                    screen.markSelectedEntityEdited();
                    screen.rebuildPage();
                }).build();

        ui.toggle(x + section + 5, 166, section).compact()
                .value(node.block_all)
                .labels(
                        labeled("gui.entitycontrol.spawn.spawn.block.short", "gui.entitycontrol.spawn.spawn.status.yes.colored"),
                        labeled("gui.entitycontrol.spawn.spawn.block.short", "gui.entitycontrol.spawn.spawn.status.no.colored")
                )
                .tooltip(() -> KineticI18n.translatable(
                        "gui.entitycontrol.spawn.spawn.tooltip.block",
                        KineticI18n.translatable(node.block_all
                                ? "gui.entitycontrol.spawn.spawn.status.yes.colored"
                                : "gui.entitycontrol.spawn.spawn.status.no.colored")
                ))
                .onChange(value -> {
                    node.block_all = value;
                    screen.markSelectedEntityEdited();
                    screen.rebuildPage();
                }).build();

        ui.toggle(x + (section + 5) * 2, 166, section).compact()
                .value(node.invert_rules)
                .labels(
                        labeled("gui.entitycontrol.spawn.spawn.invert.short", "gui.entitycontrol.spawn.spawn.status.blacklist.colored"),
                        labeled("gui.entitycontrol.spawn.spawn.invert.short", "gui.entitycontrol.spawn.spawn.status.whitelist.colored")
                )
                .tooltip(() -> KineticI18n.translatable(
                        "gui.entitycontrol.spawn.spawn.tooltip.invert",
                        KineticI18n.translatable(node.invert_rules
                                ? "gui.entitycontrol.spawn.spawn.status.blacklist.colored"
                                : "gui.entitycontrol.spawn.spawn.status.whitelist.colored")
                ))
                .onChange(value -> {
                    node.invert_rules = value;
                    screen.markSelectedEntityEdited();
                    screen.rebuildPage();
                }).build();

        List<MultiToggleItem> dimensions = new ArrayList<>();
        for (String dimension : knownDimensions) {
            boolean white = node.dim_whitelist != null && node.dim_whitelist.contains(dimension);
            boolean black = node.dim_blacklist != null && node.dim_blacklist.contains(dimension);
            dimensions.add(new MultiToggleItem(
                    Component.literal(dimension),
                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.dim_id"),
                    List.of(
                            new RowToggle(
                                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.dim_whitelist_mark"),
                                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.dim_whitelist_mark"),
                                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.dim_w"),
                                    28, white
                            ),
                            new RowToggle(
                                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.dim_blacklist_mark"),
                                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.dim_blacklist_mark"),
                                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.dim_b"),
                                    28, black
                            )
                    )
            ));
        }
        ui.multiToggleList(x, 215, w, SpawnControlScreen.V_HEIGHT - 225, dimensions)
                .onToggle((hit, value) -> toggleDimension(hit.rowIndex(), hit.toggleIndex(), value))
                .build();
    }

    private Component labeled(String labelKey, String stateKey) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawn.label_state",
                KineticI18n.translatable(labelKey), KineticI18n.translatable(stateKey));
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "misc";
        String normalized = category.toLowerCase(Locale.ROOT);
        for (String known : CATEGORIES) if (known.equals(normalized)) return normalized;
        return "misc";
    }

    private void updateDistance(String raw, boolean minimum) {
        if (updatingNumbers) return;
        Integer value = parse(raw);
        if (value == null || value < 0 || value > 128) return;
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        updatingNumbers = true;
        if (minimum) {
            node.min_spawn_distance = value;
            if (node.max_spawn_distance < value) {
                node.max_spawn_distance = value;
                screen.ruleMaxDistanceField().setIntValue(value);
            }
        } else {
            node.max_spawn_distance = value;
            if (node.min_spawn_distance > value) {
                node.min_spawn_distance = value;
                screen.ruleMinDistanceField().setIntValue(value);
            }
        }
        updatingNumbers = false;
        screen.markSelectedEntityEdited();
    }

    private void updateRules(String raw) {
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        String normalized = raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[^A-G]", "");
        if (!normalized.equals(node.rules)) {
            node.rules = normalized;
            screen.markSelectedEntityEdited();
        }
    }

    private void updateNullablePair(String raw, boolean minimum, boolean light) {
        if (updatingNumbers) return;
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        Integer value = raw == null || raw.isBlank() ? null : parse(raw);
        if (value == null && raw != null && !raw.isBlank()) return;
        if (light && value != null && (value < 0 || value > 15)) return;

        updatingNumbers = true;
        if (light) {
            if (minimum) {
                node.min_spawn_light = value;
                if (value != null && node.max_spawn_light != null && node.max_spawn_light < value) {
                    node.max_spawn_light = value;
                    screen.ruleMaxLightField().setIntValue(value);
                }
            } else {
                node.max_spawn_light = value;
                if (value != null && node.min_spawn_light != null && node.min_spawn_light > value) {
                    node.min_spawn_light = value;
                    screen.ruleMinLightField().setIntValue(value);
                }
            }
        } else {
            if (minimum) {
                node.min_spawn_height = value;
                if (value != null && node.max_spawn_height != null && node.max_spawn_height < value) {
                    node.max_spawn_height = value;
                    screen.ruleMaxHeightField().setIntValue(value);
                }
            } else {
                node.max_spawn_height = value;
                if (value != null && node.min_spawn_height != null && node.min_spawn_height > value) {
                    node.min_spawn_height = value;
                    screen.ruleMinHeightField().setIntValue(value);
                }
            }
        }
        updatingNumbers = false;
        screen.markSelectedEntityEdited();
    }

    private void toggleDimension(int rowIndex, int toggleIndex, boolean value) {
        if (rowIndex < 0 || rowIndex >= knownDimensions.size()) return;
        BiomeSpawnConfig.EntityNode node = selectedNode();
        if (node == null) return;
        String dimension = knownDimensions.get(rowIndex);
        if (node.dim_whitelist == null) node.dim_whitelist = new ArrayList<>();
        if (node.dim_blacklist == null) node.dim_blacklist = new ArrayList<>();
        List<String> target = toggleIndex == 0 ? node.dim_whitelist : node.dim_blacklist;
        List<String> other = toggleIndex == 0 ? node.dim_blacklist : node.dim_whitelist;
        if (value) {
            if (!target.contains(dimension)) target.add(dimension);
            other.remove(dimension);
        } else {
            target.remove(dimension);
        }
        screen.markSelectedEntityEdited();
        screen.rebuildPage();
    }

    private Integer parse(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.rightX();
        int w = screen.rightWidth();
        int section = (w - 10) / 3;
        int color = KineticTheme.current().mutedText();
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min_dist"), x, 95, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max_dist"), x + section + 5, 95, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.rules.label"), x + section * 2 + 10, 95, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min_height"), x, 120, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max_height"), x + section + 5, 120, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.min_light"), x, 145, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.max_light"), x + section + 5, 145, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.dim.list.desc"), x, 201, KineticTheme.current().text());
    }
}
