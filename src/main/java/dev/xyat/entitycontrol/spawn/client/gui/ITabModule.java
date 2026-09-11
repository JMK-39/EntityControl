package dev.xyat.entitycontrol.spawn.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

public interface ITabModule {
    void init();
    void render(GuiGraphics g, int mx, int my, float pt);
    boolean mouseClicked(double mx, double my, int btn);
    boolean mouseDragged(double mx, double my, int btn, double dx, double dy);
    boolean mouseReleased(double mx, double my, int btn);
    boolean mouseScrolled(double mx, double my, double delta);
    void updateSelection();
    void setVisible(boolean visible);
    default List<Component> getTooltip(int vMx, int vMy) { return null; }
    // 用于判定目前是否正在显示下沉菜单层（方便控制阻挡下层交互）
    default boolean isMenuOpen(double mx, double my) { return false; }
}
