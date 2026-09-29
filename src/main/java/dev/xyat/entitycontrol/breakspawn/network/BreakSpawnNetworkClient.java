package dev.xyat.entitycontrol.breakspawn.network;

import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfigGui;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.text.KineticI18n;

public final class BreakSpawnNetworkClient {
    private BreakSpawnNetworkClient() {
    }

    public static void requestOpenEditor() {
        BreakSpawnNetwork.requestOpenEditor();
    }

    public static void handleSaveResult(boolean success) {
        BlockRuleEditorScreen page = KineticGui.currentPage(BlockRuleEditorScreen.class);
        if (page != null) page.handleSaveResult(success);
        if (success) {
            KTConfigApi.notifySaved(BreakSpawnConfigGui.PAGE_ID);
        } else {
            KineticOverlays.toast(KineticI18n.translatable("gui.kineticcore.config.save_failed"));
        }
    }

    public static void handleOpenScreen(BreakSpawnNetwork.OpenEditorPacket packet) {
        try {
            BreakSpawnConfig.ConfigRoot config = BreakSpawnConfig.parseConfigJson(packet.jsonConfig());
            if (config == null) throw new IllegalArgumentException("Invalid break spawn snapshot");
            KineticGui.openChild(new BlockRuleEditorScreen(config));
        } catch (RuntimeException exception) {
            BreakSpawnModule.LOGGER.error("Rejected invalid break spawn snapshot", exception);
        }
    }
}
