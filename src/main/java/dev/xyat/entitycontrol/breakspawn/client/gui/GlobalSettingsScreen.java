package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;

public final class GlobalSettingsScreen extends KineticPage {
    private final BreakSpawnConfig.GlobalSettings global;

    public GlobalSettingsScreen(BreakSpawnConfig.ConfigRoot config) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.global.title"));
        this.global = config.global;
    }

    @Override
    protected void build(KineticUi ui) {
        number(ui, 240, 72, 90, NumberType.DECIMAL, global.triggerChance * 100.0D, 0D, 100D,
                value -> global.triggerChance = value.doubleValue() / 100.0D);
        number(ui, 470, 72, 90, NumberType.INT, global.minSpawnCount, 0, null,
                value -> global.minSpawnCount = value.intValue());
        number(ui, 240, 112, 90, NumberType.INT, global.maxSpawnCount, 0, null,
                value -> global.maxSpawnCount = value.intValue());
        number(ui, 470, 112, 90, NumberType.INT, global.minDistance, 0, null,
                value -> global.minDistance = value.intValue());
        number(ui, 240, 152, 90, NumberType.INT, global.horizontalRadius, 0, null,
                value -> global.horizontalRadius = value.intValue());
        number(ui, 470, 152, 90, NumberType.INT, global.verticalRadius, 0, null,
                value -> global.verticalRadius = value.intValue());
        number(ui, 240, 192, 90, NumberType.INT, global.maxSpawnAttempts, 1, null,
                value -> global.maxSpawnAttempts = value.intValue());
        number(ui, 470, 192, 90, NumberType.INT, global.playerCooldownTicks, 0, null,
                value -> global.playerCooldownTicks = value.intValue());

        ui.toggle(80, 246, 220)
                .value(global.enabled)
                .labels(toggleLabel("gui.entitycontrol.breakspawn.global.enabled", true),
                        toggleLabel("gui.entitycontrol.breakspawn.global.enabled", false))
                .onChange(value -> global.enabled = value)
                .build();
        ui.toggle(340, 246, 220)
                .value(global.creativeCanTrigger)
                .labels(toggleLabel("gui.entitycontrol.breakspawn.global.creative", true),
                        toggleLabel("gui.entitycontrol.breakspawn.global.creative", false))
                .onChange(value -> global.creativeCanTrigger = value)
                .build();

        ui.button(514, 322, 104)
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.back"))
                .onClick(this::navigateBack)
                .build();
    }

    private void number(KineticUi ui, int x, int y, int width, NumberType type, Number initial,
                        Number min, Number max, java.util.function.Consumer<Number> consumer) {
        ui.numberField(x, y, width, type)
                .range(min, max)
                .allowNegative(min == null || min.doubleValue() < 0D)
                .value(initial)
                .onChange(raw -> {
                    try {
                        double parsed = Double.parseDouble(raw.trim());
                        if (Double.isFinite(parsed)) consumer.accept(parsed);
                    } catch (RuntimeException ignored) {
                    }
                })
                .firstShownTextAsDefault().build();
    }

    private Component toggleLabel(String key, boolean value) {
        return KineticI18n.translatable(key, KineticI18n.translatable(value
                ? "gui.entitycontrol.breakspawn.switch.on"
                : "gui.entitycontrol.breakspawn.switch.off"));
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 6, 6, 628, 348);
        KineticTheme.panelAlt(graphics, 30, 50, 580, 242);
        graphics.text(title(), 14, 16, KineticTheme.current().text());

        label(graphics, "gui.entitycontrol.breakspawn.default_trigger_chance", 80, 78);
        label(graphics, "gui.entitycontrol.breakspawn.default_min_count", 350, 78);
        label(graphics, "gui.entitycontrol.breakspawn.default_max_count", 80, 118);
        label(graphics, "gui.entitycontrol.breakspawn.default_min_distance", 350, 118);
        label(graphics, "gui.entitycontrol.breakspawn.default_radius", 80, 158);
        label(graphics, "gui.entitycontrol.breakspawn.default_vertical_radius", 350, 158);
        label(graphics, "gui.entitycontrol.breakspawn.default_spawn_attempts", 80, 198);
        label(graphics, "gui.entitycontrol.breakspawn.cooldown", 350, 198);
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.global.hint"), 80, 278,
                KineticTheme.current().text());
    }

    private void label(KineticGraphics graphics, String key, int x, int y) {
        graphics.text(KineticI18n.translatable(key), x, y, KineticTheme.current().text());
    }
}
