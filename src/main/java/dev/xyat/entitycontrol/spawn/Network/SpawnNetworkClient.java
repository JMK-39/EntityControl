package dev.xyat.entitycontrol.spawn.Network;

import dev.xyat.entitycontrol.spawn.client.gui.SpawnControlScreen;
import dev.xyat.entitycontrol.spawn.client.gui.SpawnerControlScreen;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.entitycontrol.spawn.config.SpawnConfigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class SpawnNetworkClient {
    @OnlyIn(Dist.CLIENT)
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

        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen parent = minecraft.screen instanceof SpawnControlScreen current
                ? current.getParentScreen()
                : minecraft.screen;
        minecraft.setScreen(new SpawnControlScreen(
                globals,
                profile,
                packet.editIndex(),
                parent
        ));
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSpawnBackupSync(SpawnNetwork.SpawnBackupSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof SpawnControlScreen screen)
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

    @OnlyIn(Dist.CLIENT)
    public static void handleSpawnerSync(SpawnNetwork.SpawnerSyncPacket packet) {
        SpawnerConfig.SpawnerEditorSnapshot snapshot = SpawnerConfig.GSON.fromJson(
                packet.snapshotJson(),
                SpawnerConfig.SpawnerEditorSnapshot.class
        );
        if (snapshot == null) {
            snapshot = new SpawnerConfig.SpawnerEditorSnapshot();
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new SpawnerControlScreen(minecraft.screen, snapshot));
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSpawnSaveResult(SpawnNetwork.SpawnSaveResultPacket packet) {
        if (packet.success()) {
            KTConfigApi.notifySaved(SpawnConfigGui.PAGE_ID);
        } else {
            GuiOverlay.toast(Component.translatable("gui.kineticcore.config.save_failed"));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSpawnerSaveResult(SpawnNetwork.SpawnerSaveResultPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SpawnerControlScreen screen) {
            screen.handleSaveResult(packet.requestId(), packet.success());
        }
        if (packet.success()) {
            KTConfigApi.notifySaved(SpawnConfigGui.PAGE_ID);
        }
    }
}
