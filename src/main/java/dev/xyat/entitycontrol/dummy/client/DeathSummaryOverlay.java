package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.util.ColorText;
import dev.xyat.entitycontrol.dummy.config.DummyClientConfig;
import dev.xyat.entitycontrol.dummy.DummyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

public class DeathSummaryOverlay implements IGuiOverlay {
    public static final DeathSummaryOverlay INSTANCE = new DeathSummaryOverlay();
    private static final List<Component> lines = new ArrayList<>();
    private static long expireTime = 0;

    public static void show(Component targetName, float total, float dps, float duration, int hits) {
        lines.clear();

        if (DummyClientConfig.showSummaryKill.get()) {
            int titleColor = DummyClientConfig.colorSummaryTitle.get();
            MutableComponent nameComp = targetName.copy();
            lines.add(ColorText.translatable("dummy.entitycontrol.summary.kill", nameComp)
                    .withStyle(s -> s.withColor(titleColor)));
        }

        if (hits == 1) {
            lines.add(ColorText.translatable("dummy.entitycontrol.summary.instant_kill"));
            lines.add(ColorText.translatable("dummy.entitycontrol.summary.damage_amount", DummyUtils.formatNum(total))
                    .withStyle(s -> s.withColor(DummyClientConfig.colorSummaryStats.get())));
        } else {
            if (DummyClientConfig.showSummaryStats.get()) {
                lines.add(ColorText.translatable("gui.entitycontrol.dummy.dummy.stats", DummyUtils.formatNum(total), hits)
                        .withStyle(s -> s.withColor(DummyClientConfig.colorSummaryStats.get())));
            }
            if (DummyClientConfig.showSummaryTime.get()) {
                String tStr = duration < 0.05 ? ColorText.translatable("dummy.entitycontrol.instant").getString() : String.format("%.1fs", duration);
                lines.add(ColorText.translatable("dummy.entitycontrol.summary.time", DummyUtils.formatNum(dps), tStr)
                        .withStyle(s -> s.withColor(DummyClientConfig.colorSummaryTime.get())));
            }
        }

        expireTime = System.currentTimeMillis() + (DummyClientConfig.summaryDuration.get() * 50L);
    }

    public static void clear() {
        lines.clear();
        expireTime = 0;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float pt, int w, int h) {
        if (System.currentTimeMillis() > expireTime || lines.isEmpty() || Minecraft.getInstance().options.hideGui) return;

        guiGraphics.pose().pushPose();
        float s = DummyClientConfig.summaryScale.get().floatValue();

        float totalHeight = lines.size() * 10 * s;
        guiGraphics.pose().translate(w, h - 50 - totalHeight, 0);
        guiGraphics.pose().scale(s, s, 1.0f);

        Font font = Minecraft.getInstance().font;
        int y = 0;
        int bgColor = (int)(0.5f * 255.0F) << 24;

        for (Component line : lines) {
            int lineWidth = font.width(line);
            guiGraphics.fill(-lineWidth - 2, y - 1, 0, y + 9, bgColor);
            guiGraphics.drawString(font, line, -lineWidth, y, 0xFFFFFF, true);
            y += 10;
        }
        guiGraphics.pose().popPose();
    }
}
