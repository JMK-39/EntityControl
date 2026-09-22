package dev.xyat.entitycontrol.breakspawn.network;

import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfigGui;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BreakSpawnNetworkClient {
    private static Screen returnScreen;

    private BreakSpawnNetworkClient() {
    }

    public static void captureReturnScreen() {
        returnScreen = KineticClientRuntime.currentScreen();
    }

    public static void handleSaveResult(boolean success) {
        if (success) {
            KTConfigApi.notifySaved(BreakSpawnConfigGui.PAGE_ID);
        } else {
            KineticOverlays.toast(Component.translatable("gui.kineticcore.config.save_failed"));
        }
    }

    public static void handleOpenScreen(BreakSpawnNetwork.OpenEditorPacket packet) {
        try {
            Screen parent = returnScreen != null ? returnScreen : BreakSpawnConfigGui.create(null);
            returnScreen = null;
            BreakSpawnConfig.ConfigRoot config = BreakSpawnConfig.parseConfigJson(packet.jsonConfig());
            if (config == null) {
                throw new IllegalArgumentException("Invalid break spawn snapshot");
            }
            KineticClientRuntime.openScreen(new BlockRuleEditorScreen(parent, config));
        } catch (RuntimeException exception) {
            BreakSpawnModule.LOGGER.error("Rejected invalid break spawn snapshot", exception);
        }
    }
}
