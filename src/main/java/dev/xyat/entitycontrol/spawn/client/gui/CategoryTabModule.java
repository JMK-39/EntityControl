package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;

final class CategoryTabModule implements ITabModule {
    private final SpawnControlScreen screen;
    private String selectedCategory;

    CategoryTabModule(SpawnControlScreen screen) {
        this.screen = screen;
    }

    @Override
    public void build(KineticUi ui) {
        List<String> categories = categories();
        if (categories.isEmpty()) {
            selectedCategory = null;
            return;
        }
        if (selectedCategory == null || !categories.contains(selectedCategory)) selectedCategory = categories.get(0);

        int x = screen.rightX();
        int w = screen.rightWidth();
        int section = w / 3;
        Integer cap = screen.profile.category_caps.get(selectedCategory);
        Integer weight = screen.profile.category_weights.get(selectedCategory);
        Double rate = screen.profile.category_spawn_rates.get(selectedCategory);

        ui.numberField(x + 40, 91, 58, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.cap"))
                .allowNegative(false).range(0, null).value(cap == null ? 0 : cap)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.cap"))
                .onChange(raw -> putInt(raw, screen.profile.category_caps)).firstShownTextAsDefault().build();
        ui.numberField(x + section + 46, 91, 58, NumberType.INT)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.weight"))
                .allowNegative(false).range(0, null).value(weight == null ? 0 : weight)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.weight"))
                .onChange(raw -> putInt(raw, screen.profile.category_weights)).firstShownTextAsDefault().build();
        ui.numberField(x + section * 2 + 40, 91, 58, NumberType.DECIMAL)
                .label(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.rate"))
                .allowNegative(false).range(0D, null).value(rate == null ? 1D : rate)
                .tooltip(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.rate"))
                .onChange(this::putRate).firstShownTextAsDefault().build();

        ui.button(x, 175, 120).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.category.reset"))
                .onClick(this::resetSelected).build();

        List<SelectionItem> rows = new ArrayList<>();
        for (String category : categories) {
            rows.add(new SelectionItem(
                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.category." + category),
                    Component.literal(category),
                    Component.empty(), true, false
            ));
        }
        int selected = categories.indexOf(selectedCategory);
        ui.selectionList(x, 205, w, SpawnControlScreen.V_HEIGHT - 215, rows)
                .selected(selected)
                .onSelect(index -> {
                    if (index < 0 || index >= categories.size()) return;
                    selectedCategory = categories.get(index);
                    screen.rebuildPage();
                }).build();
    }

    private List<String> categories() {
        List<String> list = new ArrayList<>(screen.profile.category_caps.keySet());
        for (String key : screen.profile.category_weights.keySet()) if (!list.contains(key)) list.add(key);
        for (String key : screen.profile.category_spawn_rates.keySet()) if (!list.contains(key)) list.add(key);
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    private void putInt(String raw, java.util.Map<String, Integer> map) {
        if (selectedCategory == null || raw == null || raw.isBlank()) return;
        try {
            int value = Integer.parseInt(raw);
            if (value >= 0) map.put(selectedCategory, value);
        } catch (NumberFormatException ignored) {
        }
    }

    private void putRate(String raw) {
        if (selectedCategory == null || raw == null || raw.isBlank()) return;
        try {
            double value = Double.parseDouble(raw);
            if (value >= 0D) screen.profile.category_spawn_rates.put(selectedCategory, value);
        } catch (NumberFormatException ignored) {
        }
    }

    private void resetSelected() {
        if (selectedCategory == null) return;
        screen.profile.category_weights.put(selectedCategory, getDefaultWeight(selectedCategory));
        screen.profile.category_spawn_rates.put(selectedCategory, 1D);
        for (MobCategory category : MobCategory.values()) {
            if (category.getName().equals(selectedCategory)) {
                screen.profile.category_caps.put(selectedCategory, category.getMaxInstancesPerChunk());
                break;
            }
        }
        screen.rebuildPage();
    }

    private int getDefaultWeight(String category) {
        return switch (category) {
            case "monster" -> 100;
            case "creature" -> 60;
            case "ambient" -> 30;
            case "axolotls", "underground_water_creature" -> 80;
            case "water_creature" -> 60;
            case "water_ambient" -> 50;
            default -> 20;
        };
    }

    @Override
    public void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.rightX();
        int w = screen.rightWidth();
        int section = w / 3;
        int color = KineticTheme.current().mutedText();
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.cap"), x, 95, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.weight"), x + section + 5, 95, color);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.rate"), x + section * 2 + 5, 95, color);
    }
}
