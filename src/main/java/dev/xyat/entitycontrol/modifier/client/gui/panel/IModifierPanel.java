package dev.xyat.entitycontrol.modifier.client.gui.panel;

import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

public interface IModifierPanel {
    void init(EntityModifierScreen parent, int x, int y, int w, int h);
    void render(GuiGraphics g, int mx, int my, float pt);
    boolean mouseClicked(double mx, double my, int btn);
    boolean mouseDragged(double mx, double my, int btn, double dx, double dy);
    boolean mouseReleased(double mx, double my, int btn);
    boolean mouseScrolled(double mx, double my, double delta);
    void onEntitySelected(String entityId, LivingEntity previewEntity);
    void setVisible(boolean visible);
}
