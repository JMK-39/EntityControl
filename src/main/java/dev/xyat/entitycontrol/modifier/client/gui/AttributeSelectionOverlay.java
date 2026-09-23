package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.ref.WeakReference;

/** Adds the current attribute operation to the shared entity picker's unused footer row. */
public final class AttributeSelectionOverlay {
    private static WeakReference<Screen> selector = new WeakReference<>(null);
    private static Component summary = Component.empty();
    private static Component help = Component.empty();
    private static boolean registered;

    private AttributeSelectionOverlay() {
    }

    public static void show(Screen screen, Component attributeName, String attributeId,
                            Component operation, Component operationHelp) {
        if (!registered) {
            KineticClientEvents.onScreenRenderAfter(AttributeSelectionOverlay::render);
            registered = true;
        }
        selector = new WeakReference<>(screen);
        summary = Component.translatable("gui.entitycontrol.modifier.global.selector.status",
                attributeName, operation);
        help = Component.translatable("gui.entitycontrol.modifier.global.selector.status.tooltip",
                attributeName, attributeId, operation, operationHelp);
    }

    private static void render(Screen screen, net.minecraft.client.gui.GuiGraphics graphics,
                               int mouseX, int mouseY, float partialTick) {
        if (screen != selector.get() || !(screen instanceof KineticScreen kinetic)) return;
        Font font = KineticClientRuntime.font();
        int x = 42;
        int y = 281;
        int width = 556;
        int height = 17;
        graphics.pose().pushPose();
        graphics.pose().translate(kinetic.toScreenX(0), kinetic.toScreenY(0), 0);
        graphics.pose().scale(kinetic.canvasScale(), kinetic.canvasScale(), 1.0F);
        GuiTheme.surface(graphics, x, y, width, height, GuiTheme.Surface.PANEL_ALT);
        String display = summary.getString();
        if (font.width(display) > width - 12) {
            display = font.plainSubstrByWidth(display, width - 24) + "...";
        }
        graphics.drawCenteredString(font, display, x + width / 2, y + 4, 0xFFFFFF);
        graphics.pose().popPose();

        double vx = kinetic.toVirtualX(mouseX);
        double vy = kinetic.toVirtualY(mouseY);
        if (vx >= x && vx < x + width && vy >= y && vy < y + height) {
            graphics.renderTooltip(font, font.split(help, 300), mouseX, mouseY);
        }
    }
}
