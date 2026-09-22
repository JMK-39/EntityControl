package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class GlobalSettingsScreen extends KineticScreen {
    private final Screen parent;
    private final BreakSpawnConfig.GlobalSettings global;

    private KineticEditBox chanceBox;
    private KineticEditBox minCountBox;
    private KineticEditBox maxCountBox;
    private KineticEditBox minDistanceBox;
    private KineticEditBox radiusBox;
    private KineticEditBox verticalRadiusBox;
    private KineticEditBox attemptsBox;
    private KineticEditBox cooldownBox;
    private StateButton enabledButton;
    private StateButton creativeButton;

    public GlobalSettingsScreen(Screen parent, BreakSpawnConfig.ConfigRoot config) {
        super(Component.translatable("gui.entitycontrol.breakspawn.global.title"));
        this.parent = parent;
        setParentScreen(parent);
        this.global = config.global;
    }

    @Override
    protected void buildUi() {
        chanceBox = numberBox(240, 72, 90, global.triggerChance * 100.0D, value -> global.triggerChance = clampChance(value / 100.0D));
        minCountBox = numberBox(470, 72, 90, global.minSpawnCount, value -> global.minSpawnCount = Math.max(0, (int) value));
        maxCountBox = numberBox(240, 112, 90, global.maxSpawnCount, value -> global.maxSpawnCount = Math.max(0, (int) value));
        minDistanceBox = numberBox(470, 112, 90, global.minDistance, value -> global.minDistance = Math.max(0, (int) value));
        radiusBox = numberBox(240, 152, 90, global.horizontalRadius, value -> global.horizontalRadius = Math.max(0, (int) value));
        verticalRadiusBox = numberBox(470, 152, 90, global.verticalRadius, value -> global.verticalRadius = Math.max(0, (int) value));
        attemptsBox = numberBox(240, 192, 90, global.maxSpawnAttempts, value -> global.maxSpawnAttempts = Math.max(1, (int) value));
        cooldownBox = numberBox(470, 192, 90, global.playerCooldownTicks, value -> global.playerCooldownTicks = Math.max(0, (int) value));

        enabledButton = addButton(
                80, 246, 220, Component.empty(), null,
                () -> {
                    global.enabled = !global.enabled;
                    updateButtons();
                }
        );
        creativeButton = addButton(
                340, 246, 220, Component.empty(), null,
                () -> {
                    global.creativeCanTrigger = !global.creativeCanTrigger;
                    updateButtons();
                }
        );

        addButton(
                514, 322, 104,
                Component.translatable("gui.entitycontrol.breakspawn.back"),
                null,
                this::onClose
        );
        updateButtons();
    }

    private KineticEditBox numberBox(int x, int y, int width, double value, java.util.function.DoubleConsumer consumer) {
        KineticEditBox box = addTextField(x, y, width, Component.empty());
        box.setMaxLength(32);
        box.setValue(format(value));
        box.setResponder(text -> {
            try {
                consumer.accept(Double.parseDouble(text.trim()));
            } catch (Exception ignored) {
            }
        });
        return box;
    }

    private String format(double value) {
        return Math.rint(value) == value ? String.valueOf((long) value) : String.valueOf(value);
    }

    private double clampChance(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private void updateButtons() {
        enabledButton.setText(Component.translatable(
                "gui.entitycontrol.breakspawn.global.enabled",
                Component.translatable(global.enabled ? "gui.entitycontrol.breakspawn.switch.on" : "gui.entitycontrol.breakspawn.switch.off")
        ));
        creativeButton.setText(Component.translatable(
                "gui.entitycontrol.breakspawn.global.creative",
                Component.translatable(global.creativeCanTrigger ? "gui.entitycontrol.breakspawn.switch.on" : "gui.entitycontrol.breakspawn.switch.off")
        ));
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 6, 6, 628, 348);
        GuiTheme.panelAlt(graphics, 30, 50, 580, 242);
        graphics.drawString(font, title, 14, 16, 0xFFFFFFFF, false);

        label(graphics, "gui.entitycontrol.breakspawn.default_trigger_chance", 80, 78);
        label(graphics, "gui.entitycontrol.breakspawn.default_min_count", 350, 78);
        label(graphics, "gui.entitycontrol.breakspawn.default_max_count", 80, 118);
        label(graphics, "gui.entitycontrol.breakspawn.default_min_distance", 350, 118);
        label(graphics, "gui.entitycontrol.breakspawn.default_radius", 80, 158);
        label(graphics, "gui.entitycontrol.breakspawn.default_vertical_radius", 350, 158);
        label(graphics, "gui.entitycontrol.breakspawn.default_spawn_attempts", 80, 198);
        label(graphics, "gui.entitycontrol.breakspawn.cooldown", 350, 198);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.global.hint"), 80, 278, 0xFFFFFFFF, false);
    }

    private void label(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y, 0xFFFFFFFF, false);
    }

    @Override
    protected boolean handleCloseRequest() {
        return false;
    }
}
