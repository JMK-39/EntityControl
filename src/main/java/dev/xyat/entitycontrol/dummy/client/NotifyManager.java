package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.DummyModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DummyModule.MODID, value = Dist.CLIENT)
public class NotifyManager {
    private static Component currentMsg = null;
    private static long expiryTime = 0;

    public static void notify(Component msg) {
        currentMsg = msg;
        expiryTime = System.currentTimeMillis() + 3000;
    }

    public static Component getActiveMessage() {
        if (System.currentTimeMillis() > expiryTime) {
            currentMsg = null;
            return null;
        }
        return currentMsg;
    }

    /**
     * 通用绘制逻辑：由具体的界面决定位置
     * @param gui 绘图上下文
     * @param centerX 绘制区域的水平中心点
     * @param y 绘制区域的起始高度
     */
    public static void renderAt(GuiGraphics gui, int centerX, int y) {
        Component msg = getActiveMessage();
        if (msg == null) return;

        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(msg);
        int drawX = centerX - (textWidth / 2);

        gui.pose().pushPose();
        // 设置层级为500，确保在所有物品图标之上
        gui.pose().translate(0, 0, 500);

        // 绘制背景黑框 (阴影美感)
        gui.fill(drawX - 5, y - 2, drawX + textWidth + 5, y + 11, 0x90000000);
        // 绘制文字
        gui.drawString(font, msg, drawX, y, 0xFFFFFF, true);

        gui.pose().popPose();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        currentMsg = null;
        expiryTime = 0;
    }
}
