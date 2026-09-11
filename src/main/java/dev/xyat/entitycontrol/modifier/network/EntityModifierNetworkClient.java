package dev.xyat.entitycontrol.modifier.network;

import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.entitycontrol.modifier.config.ModifierConfigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class EntityModifierNetworkClient {

    @OnlyIn(Dist.CLIENT)
    public static void handleSaveResult(boolean success) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof EntityModifierScreen screen) {
            screen.handleSaveResult(success);
        }
        if (success) {
            KTConfigApi.notifySaved(ModifierConfigGui.PAGE_ID);
        } else {
            GuiOverlay.toast(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleOpenScreen(EntityModifierNetwork.OpenModifierScreenPacket packet) {
        try {
            Minecraft.getInstance().setScreen(new EntityModifierScreen(packet.jsonConfig()));
        } catch (RuntimeException exception) {
            ModifierModule.LOGGER.error("Rejected invalid server entity-modifier snapshot", exception);
        }
    }
}
