package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.DummyUtils;
import dev.xyat.entitycontrol.dummy.config.DummyClientConfig;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class DeathSummaryOverlay {
    private static final List<Component> lines = new ArrayList<>();
    private static long expireTime = 0;

    private DeathSummaryOverlay() {
    }

    public static void show(Component targetName, float total, float dps, float duration, int hits) {
        lines.clear();

        if (DummyClientConfig.showSummaryKill.get()) {
            int titleColor = DummyClientConfig.colorSummaryTitle.get();
            MutableComponent nameComp = targetName.copy();
            lines.add(KineticI18n.translatable("dummy.entitycontrol.dummy.summary.kill", nameComp)
                    .withStyle(s -> s.withColor(titleColor)));
        }

        if (hits == 1) {
            lines.add(KineticI18n.translatable("dummy.entitycontrol.dummy.summary.instant_kill"));
            lines.add(KineticI18n.translatable("dummy.entitycontrol.dummy.summary.damage_amount", DummyUtils.formatNum(total))
                    .withStyle(s -> s.withColor(DummyClientConfig.colorSummaryStats.get())));
        } else {
            if (DummyClientConfig.showSummaryStats.get()) {
                lines.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.stats", DummyUtils.formatNum(total), hits)
                        .withStyle(s -> s.withColor(DummyClientConfig.colorSummaryStats.get())));
            }
            if (DummyClientConfig.showSummaryTime.get()) {
                String tStr = duration < 0.05
                        ? KineticI18n.translatable("dummy.entitycontrol.dummy.instant").getString()
                        : String.format("%.1fs", duration);
                lines.add(KineticI18n.translatable("dummy.entitycontrol.dummy.summary.time", DummyUtils.formatNum(dps), tStr)
                        .withStyle(s -> s.withColor(DummyClientConfig.colorSummaryTime.get())));
            }
        }

        expireTime = System.currentTimeMillis() + (DummyClientConfig.summaryDuration.get() * 50L);
    }

    public static void clear() {
        lines.clear();
        expireTime = 0;
    }

    public static void render(KineticGraphics graphics, float partialTick) {
        if (System.currentTimeMillis() > expireTime || lines.isEmpty() || KineticClientRuntime.guiHidden()) return;

        int width = KineticClientRuntime.guiScaledWidth();
        int height = KineticClientRuntime.guiScaledHeight();
        float scale = DummyClientConfig.summaryScale.get().floatValue();
        float totalHeight = lines.size() * 10 * scale;

        graphics.push();
        try {
            graphics.translate(width, height - 50 - totalHeight);
            graphics.scale(scale, scale);

            int y = 0;
            for (Component line : lines) {
                int lineWidth = graphics.textWidth(line);
                KineticTheme.surface(
                        graphics,
                        -lineWidth - 2,
                        y - 1,
                        lineWidth + 2,
                        10,
                        KineticTheme.Surface.FIELD,
                        0.5F
                );
                graphics.text(line, -lineWidth, y, KineticTheme.current().text(), true);
                y += 10;
            }
        } finally {
            graphics.pop();
        }
    }
}
