package dev.xyat.entitycontrol.spawn.Network;

import dev.xyat.entitycontrol.spawn.client.gui.SpawnControlScreen;
import dev.xyat.entitycontrol.spawn.client.gui.SpawnerControlScreen;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnConfigGui;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.text.KineticI18n;

public final class SpawnNetworkClient {
    private SpawnNetworkClient() {
    }

    public static void requestOpenEditor(int editorType) {
        SpawnNetwork.requestOpenEditor(editorType);
    }

    public static void handleSync(SpawnNetwork.SyncPacket packet) {
        BiomeSpawnConfig.GlobalSettings globals = new BiomeSpawnConfig.GlobalSettings();
        globals.enable_rule_override = packet.ruleOverride();
        globals.enable_biome_override = packet.biomeOverride();
        globals.auto_scan = packet.autoScan();
        globals.config_amount = packet.amount();
        globals.current_index = packet.currentIndex();

        BiomeSpawnConfig.ConfigProfile profile = BiomeSpawnConfig.GSON.fromJson(
                packet.profileJson(), BiomeSpawnConfig.ConfigProfile.class
        );
        if (profile == null) profile = new BiomeSpawnConfig.ConfigProfile();

        SpawnControlScreen screen = KineticGui.currentPage(SpawnControlScreen.class);
        if (screen != null) {
            screen.applySync(globals, profile, packet.editIndex());
        } else {
            KineticGui.openChild(new SpawnControlScreen(globals, profile, packet.editIndex()));
        }
    }

    public static void handleSpawnBackupSync(SpawnNetwork.SpawnBackupSyncPacket packet) {
        SpawnControlScreen screen = KineticGui.currentPage(SpawnControlScreen.class);
        if (screen == null || screen.currentEditIndex != packet.editIndex()) return;
        BiomeSpawnConfig.ConfigProfile backup = BiomeSpawnConfig.GSON.fromJson(
                packet.backupJson(), BiomeSpawnConfig.ConfigProfile.class
        );
        screen.updateBackupProfile(backup == null ? new BiomeSpawnConfig.ConfigProfile() : backup);
    }

    public static void handleSpawnerSync(SpawnNetwork.SpawnerSyncPacket packet) {
        SpawnerConfig.SpawnerEditorSnapshot snapshot = SpawnerConfig.GSON.fromJson(
                packet.snapshotJson(), SpawnerConfig.SpawnerEditorSnapshot.class
        );
        if (snapshot == null) snapshot = new SpawnerConfig.SpawnerEditorSnapshot();
        KineticGui.openChild(new SpawnerControlScreen(snapshot));
    }

    public static void handleSpawnSaveResult(SpawnNetwork.SpawnSaveResultPacket packet) {
        if (packet.success()) KTConfigApi.notifySaved(SpawnConfigGui.PAGE_ID);
        else KineticOverlays.toast(KineticI18n.translatable("gui.kineticcore.config.save_failed"));
    }

    public static void handleSpawnerSaveResult(SpawnNetwork.SpawnerSaveResultPacket packet) {
        SpawnerControlScreen screen = KineticGui.currentPage(SpawnerControlScreen.class);
        if (screen != null) screen.handleSaveResult(packet.requestId(), packet.success());
        if (packet.success()) KTConfigApi.notifySaved(SpawnConfigGui.PAGE_ID);
    }
}
