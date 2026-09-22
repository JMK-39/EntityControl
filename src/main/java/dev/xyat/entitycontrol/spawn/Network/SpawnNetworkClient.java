package dev.xyat.entitycontrol.spawn.Network;

import dev.xyat.entitycontrol.spawn.client.gui.SpawnControlScreen;
import dev.xyat.entitycontrol.spawn.client.gui.SpawnerControlScreen;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnConfigGui;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SpawnNetworkClient {
    private SpawnNetworkClient() {
    }

    public static void handleSync(SpawnNetwork.SyncPacket packet) {
        BiomeSpawnConfig.GlobalSettings globals = new BiomeSpawnConfig.GlobalSettings();
        globals.enable_rule_override = packet.ruleOverride();
        globals.enable_biome_override = packet.biomeOverride();
        globals.auto_scan = packet.autoScan();
        globals.config_amount = packet.amount();
        globals.current_index = packet.currentIndex();

        BiomeSpawnConfig.ConfigProfile profile = BiomeSpawnConfig.GSON.fromJson(
                packet.profileJson(),
                BiomeSpawnConfig.ConfigProfile.class
        );
        if (profile == null) {
            profile = new BiomeSpawnConfig.ConfigProfile();
        }

        Screen current = KineticClientRuntime.currentScreen();
        Screen parent = current instanceof SpawnControlScreen currentSpawn
                ? currentSpawn.getParentScreen()
                : current;
        KineticClientRuntime.openScreen(new SpawnControlScreen(
                globals,
                profile,
                packet.editIndex(),
                parent
        ));
    }

    public static void handleSpawnBackupSync(SpawnNetwork.SpawnBackupSyncPacket packet) {
        if (!(KineticClientRuntime.currentScreen() instanceof SpawnControlScreen screen)
                || screen.currentEditIndex != packet.editIndex()) {
            return;
        }

        BiomeSpawnConfig.ConfigProfile backupProfile = BiomeSpawnConfig.GSON.fromJson(
                packet.backupJson(),
                BiomeSpawnConfig.ConfigProfile.class
        );
        if (backupProfile == null) {
            backupProfile = new BiomeSpawnConfig.ConfigProfile();
        }

        screen.updateBackupProfile(backupProfile);
    }

    public static void handleSpawnerSync(SpawnNetwork.SpawnerSyncPacket packet) {
        SpawnerConfig.SpawnerEditorSnapshot snapshot = SpawnerConfig.GSON.fromJson(
                packet.snapshotJson(),
                SpawnerConfig.SpawnerEditorSnapshot.class
        );
        if (snapshot == null) {
            snapshot = new SpawnerConfig.SpawnerEditorSnapshot();
        }
        Screen current = KineticClientRuntime.currentScreen();
        Screen parent = current instanceof SpawnerControlScreen currentSpawner
                ? currentSpawner.getParentScreen()
                : current;
        KineticClientRuntime.openScreen(new SpawnerControlScreen(parent, snapshot));
    }

    public static void handleSpawnSaveResult(SpawnNetwork.SpawnSaveResultPacket packet) {
        if (packet.success()) {
            KTConfigApi.notifySaved(SpawnConfigGui.PAGE_ID);
        } else {
            KineticOverlays.toast(Component.translatable("gui.kineticcore.config.save_failed"));
        }
    }

    public static void handleSpawnerSaveResult(SpawnNetwork.SpawnerSaveResultPacket packet) {
        if (KineticClientRuntime.currentScreen() instanceof SpawnerControlScreen screen) {
            screen.handleSaveResult(packet.requestId(), packet.success());
        }
        if (packet.success()) {
            KTConfigApi.notifySaved(SpawnConfigGui.PAGE_ID);
        }
    }
}
