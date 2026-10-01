package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.text.KineticI18n;

import java.util.List;
import java.util.function.IntConsumer;

import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tip;
import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tr;

/** 全局开关与新方块默认值。修改直接进入编辑器草稿，在主界面保存。 */
public final class GlobalSettingsScreen extends EcPage {
    private static final int LABEL_WIDTH = 104;
    private static final int PAIR_WIDTH = 50;

    private final BreakSpawnConfig.GlobalSettings global;
    private final Runnable onChange;
    private KineticLayout.Rect helpRect;

    public GlobalSettingsScreen(BreakSpawnConfig.ConfigRoot config, Runnable onChange) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.global.title"));
        this.global = config.global;
        this.onChange = onChange;
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        KineticLayout.Split columns = halves(body);
        KineticLayout.Split left = takeTop(columns.first(), PAD * 2 + 10 + GAP + ROW * 2);
        Form switches = form(section(left.first(), tr("section.global")), LABEL_WIDTH);
        toggleMenu(ui, compact(switches.row(tr("switches"), tip("global_switches"))), tr("switches.button"), tip("global_switches"),
                List.of(
                        new ToggleOption(tr("switch.global_enabled"), tip("switch.global_enabled"), () -> global.enabled,
                                value -> global.enabled = value),
                        new ToggleOption(tr("switch.creative"), tip("switch.creative"), () -> global.creativeCanTrigger,
                                value -> global.creativeCanTrigger = value)
                ), true, onChange);
        intRow(ui, switches, "player_cooldown", global.playerCooldownTicks, 0, 1_000_000, value -> global.playerCooldownTicks = value);
        helpRect = left.second();

        Form defaults = form(section(columns.second(), tr("section.defaults")), LABEL_WIDTH);
        decimalField(ui, number(defaults.row(tr("base_chance"), tip("default_chance"))), global.triggerChance * 100D, 0D, 100D,
                tip("default_chance"), value -> {
                    if (value == null) return;
                    global.triggerChance = value / 100D;
                    onChange.run();
                });
        KineticLayout.Rect count = defaults.row(tr("count"), tip("count"));
        intField(ui, new KineticLayout.Rect(count.x(), count.y(), PAIR_WIDTH, H), global.minSpawnCount, 0, 1024, tr("pair.min"), value -> {
            if (value == null) return;
            global.minSpawnCount = value;
            onChange.run();
        });
        intField(ui, new KineticLayout.Rect(count.x() + PAIR_WIDTH + GAP, count.y(), PAIR_WIDTH, H), global.maxSpawnCount, 0, 1024,
                tr("pair.max"), value -> {
                    if (value == null) return;
                    global.maxSpawnCount = value;
                    onChange.run();
                });
        intRow(ui, defaults, "min_distance", global.minDistance, 0, 1024, value -> global.minDistance = value);
        intRow(ui, defaults, "radius", global.horizontalRadius, 0, 1024, value -> global.horizontalRadius = value);
        intRow(ui, defaults, "vertical_radius", global.verticalRadius, 0, 1024, value -> global.verticalRadius = value);
        intRow(ui, defaults, "attempts", global.maxSpawnAttempts, 1, 1024, value -> global.maxSpawnAttempts = value);
    }

    private void intRow(KineticUi ui, Form form, String key, int value, int min, int max, IntConsumer setter) {
        intField(ui, number(form.row(tr(key), tip(key))), value, min, max, tip(key), changed -> {
            if (changed == null) return;
            setter.accept(changed);
            onChange.run();
        });
    }

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (helpRect != null) {
            graphics.wrappedText(KineticI18n.translatable("gui.entitycontrol.breakspawn.global.hint"), helpRect.x() + PAD,
                    helpRect.y() + GAP, helpRect.width() - PAD * 2, KineticTheme.current().text());
        }
    }
}
