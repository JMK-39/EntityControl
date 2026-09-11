package dev.xyat.entitycontrol.breakspawn.network;

import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfigGui;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.config.client.KTConfigApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class BreakSpawnNetworkClient {
    private static Screen returnScreen;

    private BreakSpawnNetworkClient() {
    }

    @OnlyIn(Dist.CLIENT)
    public static void captureReturnScreen() {
        returnScreen = Minecraft.getInstance().screen;
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSaveResult(boolean success) {
        if (success) {
            KTConfigApi.notifySaved(BreakSpawnConfigGui.PAGE_ID);
        } else {
            GuiOverlay.toast(Component.translatable("gui.kineticcore.config.save_failed"));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleOpenScreen(BreakSpawnNetwork.OpenEditorPacket packet) {
        try {
            Screen parent = returnScreen != null ? returnScreen : BreakSpawnConfigGui.create(null);
            returnScreen = null;
            BreakSpawnConfig.ConfigRoot config = BreakSpawnConfig.parseConfigJson(packet.jsonConfig());
            if (config == null) {
                throw new IllegalArgumentException("Invalid break spawn snapshot");
            }
            Minecraft.getInstance().setScreen(new BlockRuleEditorScreen(parent, config));
        } catch (RuntimeException exception) {
            BreakSpawnModule.LOGGER.error("Rejected invalid break spawn snapshot", exception);
        }
    }
}
