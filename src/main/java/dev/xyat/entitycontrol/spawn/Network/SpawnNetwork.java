package dev.xyat.entitycontrol.spawn.Network;

import dev.xyat.entitycontrol.spawn.SpawnModule;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SpawnNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(SpawnModule.MODID, "spawn_control"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.ANY
    );

    private static boolean syncRegistered;
    private static boolean saveRegistered;
    private static boolean switchRegistered;
    private static boolean spawnerSyncRegistered;
    private static boolean spawnerSaveRegistered;
    private static boolean spawnerSaveResultRegistered;
    private static boolean refreshBackupRegistered;
    private static boolean backupSyncRegistered;
    private static boolean updateProfileCountRegistered;
    private static boolean requestOpenRegistered;
    private static boolean spawnSaveResultRegistered;

    public static final int EDITOR_SPAWN = 0;
    public static final int EDITOR_SPAWNER = 1;

    private SpawnNetwork() {
    }

    public static synchronized void register() {
        PacketRegistrations.runIndependent(
                () -> {
                    if (!syncRegistered) {
                        CHANNEL.registerClientbound(
                                0,
                                SyncPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SyncPacket::new),
                                message -> SpawnNetworkClient.handleSync(message)
                        );
                        syncRegistered = true;
                    }
                },
                () -> {
                    if (!saveRegistered) {
                        CHANNEL.registerServerbound(
                                1,
                                SavePacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SavePacket::new),
                                SpawnNetwork::handleSave
                        );
                        saveRegistered = true;
                    }
                },
                () -> {
                    if (!switchRegistered) {
                        CHANNEL.registerServerbound(
                                2,
                                SwitchProfilePacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SwitchProfilePacket::new),
                                SpawnNetwork::handleSwitchProfile
                        );
                        switchRegistered = true;
                    }
                },
                () -> {
                    if (!spawnerSyncRegistered) {
                        CHANNEL.registerClientbound(
                                3,
                                SpawnerSyncPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SpawnerSyncPacket::new),
                                message -> SpawnNetworkClient.handleSpawnerSync(message)
                        );
                        spawnerSyncRegistered = true;
                    }
                },
                () -> {
                    if (!spawnerSaveRegistered) {
                        CHANNEL.registerServerbound(
                                4,
                                SpawnerSavePacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SpawnerSavePacket::new),
                                SpawnNetwork::handleSpawnerSave
                        );
                        spawnerSaveRegistered = true;
                    }
                },
                () -> {
                    if (!spawnerSaveResultRegistered) {
                        CHANNEL.registerClientbound(
                                5,
                                SpawnerSaveResultPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SpawnerSaveResultPacket::new),
                                message -> SpawnNetworkClient.handleSpawnerSaveResult(message)
                        );
                        spawnerSaveResultRegistered = true;
                    }
                },
                () -> {
                    if (!refreshBackupRegistered) {
                        CHANNEL.registerServerbound(
                                6,
                                RefreshSpawnBackupPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), RefreshSpawnBackupPacket::new),
                                SpawnNetwork::handleRefreshBackup
                        );
                        refreshBackupRegistered = true;
                    }
                },
                () -> {
                    if (!backupSyncRegistered) {
                        CHANNEL.registerClientbound(
                                7,
                                SpawnBackupSyncPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SpawnBackupSyncPacket::new),
                                message -> SpawnNetworkClient.handleSpawnBackupSync(message)
                        );
                        backupSyncRegistered = true;
                    }
                },
                () -> {
                    if (!updateProfileCountRegistered) {
                        CHANNEL.registerServerbound(
                                8,
                                UpdateProfileCountPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), UpdateProfileCountPacket::new),
                                SpawnNetwork::handleUpdateProfileCount
                        );
                        updateProfileCountRegistered = true;
                    }
                },
                () -> {
                    if (!requestOpenRegistered) {
                        CHANNEL.registerServerbound(
                                9,
                                RequestOpenEditorPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), RequestOpenEditorPacket::new),
                                SpawnNetwork::handleRequestOpenEditor
                        );
                        requestOpenRegistered = true;
                    }
                },
                () -> {
                    if (!spawnSaveResultRegistered) {
                        CHANNEL.registerClientbound(
                                10,
                                SpawnSaveResultPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SpawnSaveResultPacket::new),
                                message -> SpawnNetworkClient.handleSpawnSaveResult(message)
                        );
                        spawnSaveResultRegistered = true;
                    }
                }
        );
    }

    public static void requestOpenEditor(int editorType) {
        CHANNEL.sendToServer(new RequestOpenEditorPacket(editorType));
    }

    public static void updateProfileCount(int count) {
        CHANNEL.sendToServer(new UpdateProfileCountPacket(count));
    }

    public static void refreshSpawnBackup(int editIndex) {
        CHANNEL.sendToServer(new RefreshSpawnBackupPacket(editIndex));
    }

    public static void saveSpawnSettings(
            boolean ruleOverride,
            boolean biomeOverride,
            boolean autoScan,
            int amount,
            int currentIndex,
            String profileJson,
            int editIndex
    ) {
        CHANNEL.sendToServer(new SavePacket(
                ruleOverride,
                biomeOverride,
                autoScan,
                amount,
                currentIndex,
                profileJson,
                editIndex
        ));
    }

    public static void switchProfile(int requestIndex) {
        CHANNEL.sendToServer(new SwitchProfilePacket(requestIndex));
    }

    public static void saveSpawner(String dataJson, long requestId) {
        CHANNEL.sendToServer(new SpawnerSavePacket(dataJson, requestId));
    }

    public static void sendSyncToPlayer(ServerPlayer player, int editIndex) {
        BiomeSpawnConfig.sanitizeGlobals();
        if (!BiomeSpawnConfig.isValidProfileIndex(editIndex)) return;
        BiomeSpawnConfig.ConfigProfile profile = BiomeSpawnConfig.getProfileData(editIndex);

        boolean changed = false;
        BiomeSpawnConfig.ConfigProfile backupProfile = BiomeSpawnConfig.getBackupProfileData();
        if (BiomeSpawnConfig.globals.auto_scan) {
            changed = BiomeSpawnConfig.performAutoScan(player.server, profile);
            backupProfile = BiomeSpawnConfig.refreshAutoScanBackup(player.server);
        }
        if (changed) {
            BiomeSpawnConfig.saveProfileData(editIndex, profile);
        }

        CHANNEL.sendToPlayer(
                player,
                new SyncPacket(
                        BiomeSpawnConfig.globals.enable_rule_override,
                        BiomeSpawnConfig.globals.enable_biome_override,
                        BiomeSpawnConfig.globals.auto_scan,
                        BiomeSpawnConfig.globals.config_amount,
                        BiomeSpawnConfig.globals.current_index,
                        BiomeSpawnConfig.GSON.toJson(profile),
                        editIndex
                )
        );
        CHANNEL.sendToPlayer(
                player,
                new SpawnBackupSyncPacket(editIndex, BiomeSpawnConfig.GSON.toJson(backupProfile))
        );
    }

    public static void sendSpawnerSyncToPlayer(ServerPlayer player) {
        SpawnerConfig.cleanupInvalidEntityRules();
        SpawnerConfig.SpawnerEditorSnapshot snapshot = SpawnerConfig.createEditorSnapshot(player.server);
        CHANNEL.sendToPlayer(player, new SpawnerSyncPacket(SpawnerConfig.GSON.toJson(snapshot)));
    }

    private static void handleUpdateProfileCount(UpdateProfileCountPacket message, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        BiomeSpawnConfig.loadGlobals();
        if (!BiomeSpawnConfig.isValidProfileCount(message.count)
                || message.count < BiomeSpawnConfig.globals.current_index) {
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.profile_count_invalid"));
            return;
        }
        BiomeSpawnConfig.globals.config_amount = message.count;
        if (!BiomeSpawnConfig.saveGlobals()) {
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_failed"));
        }
    }

    private static void handleRequestOpenEditor(RequestOpenEditorPacket message, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        if (message.editorType == EDITOR_SPAWN) {
            BiomeSpawnConfig.loadGlobals();
            sendSyncToPlayer(player, BiomeSpawnConfig.globals.current_index);
        } else if (message.editorType == EDITOR_SPAWNER) {
            SpawnerConfig.load();
            sendSpawnerSyncToPlayer(player);
        }
    }

    private static void handleSave(SavePacket message, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) {
            sendSpawnSaveResult(player, false);
            return;
        }

        if (!BiomeSpawnConfig.isValidProfileCount(message.amount)
                || message.currentIndex < 1 || message.currentIndex > message.amount
                || message.editIndex < 1 || message.editIndex > message.amount) {
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_invalid"));
            sendSpawnSaveResult(player, false);
            return;
        }

        try {
            BiomeSpawnConfig.ConfigProfile profile = BiomeSpawnConfig.GSON.fromJson(
                    message.profileJson,
                    BiomeSpawnConfig.ConfigProfile.class
            );
            if (profile == null) {
                player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_invalid"));
                sendSpawnSaveResult(player, false);
                return;
            }

            if (!BiomeSpawnConfig.saveEditorState(
                    player.server,
                    message.ruleOverride,
                    message.biomeOverride,
                    message.autoScan,
                    message.amount,
                    message.currentIndex,
                    message.editIndex,
                    profile
            )) {
                player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_failed"));
                sendSpawnSaveResult(player, false);
                return;
            }

            sendSpawnSaveResult(player, true);
        } catch (RuntimeException exception) {
            SpawnModule.LOGGER.warn(
                    "Rejected invalid spawn-control config from {}",
                    player.getGameProfile().getName(),
                    exception
            );
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_invalid"));
            sendSpawnSaveResult(player, false);
        }
    }

    private static void handleSwitchProfile(SwitchProfilePacket message, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        BiomeSpawnConfig.loadGlobals();
        if (BiomeSpawnConfig.isValidProfileIndex(message.requestIndex)) {
            sendSyncToPlayer(player, message.requestIndex);
        }
    }

    private static void handleRefreshBackup(RefreshSpawnBackupPacket message, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;

        BiomeSpawnConfig.loadGlobals();
        if (message.editIndex < 1 || message.editIndex > BiomeSpawnConfig.globals.config_amount) {
            return;
        }

        BiomeSpawnConfig.ConfigProfile backup = BiomeSpawnConfig.refreshAutoScanBackup(player.server);
        CHANNEL.sendToPlayer(
                player,
                new SpawnBackupSyncPacket(message.editIndex, BiomeSpawnConfig.GSON.toJson(backup))
        );
    }

    private static void handleSpawnerSave(SpawnerSavePacket message, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) {
            sendSpawnerSaveResult(player, message.requestId, false);
            return;
        }

        try {
            SpawnerConfig.SpawnerData incoming = SpawnerConfig.GSON.fromJson(
                    message.dataJson,
                    SpawnerConfig.SpawnerData.class
            );
            if (incoming == null) {
                player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawner.save_invalid"));
                sendSpawnerSaveResult(player, message.requestId, false);
                return;
            }
            if (!SpawnerConfig.applyEditorSnapshot(incoming)) {
                player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawner.save_failed"));
                sendSpawnerSaveResult(player, message.requestId, false);
                return;
            }
            sendSpawnerSaveResult(player, message.requestId, true);
        } catch (RuntimeException exception) {
            SpawnModule.LOGGER.warn(
                    "Rejected invalid spawner-control config from {}",
                    player.getGameProfile().getName(),
                    exception
            );
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawner.save_invalid"));
            sendSpawnerSaveResult(player, message.requestId, false);
        }
    }

    private static void sendSpawnSaveResult(ServerPlayer player, boolean success) {
        CHANNEL.sendToPlayer(player, new SpawnSaveResultPacket(success));
    }

    private static void sendSpawnerSaveResult(ServerPlayer player, long requestId, boolean success) {
        CHANNEL.sendToPlayer(player, new SpawnerSaveResultPacket(requestId, success));
    }

    public record SyncPacket(
            boolean ruleOverride,
            boolean biomeOverride,
            boolean autoScan,
            int amount,
            int currentIndex,
            String profileJson,
            int editIndex
    ) {
        private SyncPacket(NetworkBuffer buffer) {
            this(
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readInt(),
                    readCompressedString(buffer),
                    buffer.readInt()
            );
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(ruleOverride);
            buffer.writeBoolean(biomeOverride);
            buffer.writeBoolean(autoScan);
            buffer.writeInt(amount);
            buffer.writeInt(currentIndex);
            writeCompressedString(buffer, profileJson);
            buffer.writeInt(editIndex);
        }
    }

    public record SavePacket(
            boolean ruleOverride,
            boolean biomeOverride,
            boolean autoScan,
            int amount,
            int currentIndex,
            String profileJson,
            int editIndex
    ) {
        private SavePacket(NetworkBuffer buffer) {
            this(
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readInt(),
                    readCompressedString(buffer),
                    buffer.readInt()
            );
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(ruleOverride);
            buffer.writeBoolean(biomeOverride);
            buffer.writeBoolean(autoScan);
            buffer.writeInt(amount);
            buffer.writeInt(currentIndex);
            writeCompressedString(buffer, profileJson);
            buffer.writeInt(editIndex);
        }
    }

    public record SwitchProfilePacket(int requestIndex) {
        private SwitchProfilePacket(NetworkBuffer buffer) {
            this(buffer.readInt());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeInt(requestIndex);
        }
    }

    public record SpawnerSyncPacket(String snapshotJson) {
        private SpawnerSyncPacket(NetworkBuffer buffer) {
            this(readCompressedString(buffer));
        }

        private void encode(NetworkBuffer buffer) {
            writeCompressedString(buffer, snapshotJson);
        }
    }

    public record SpawnerSavePacket(String dataJson, long requestId) {
        private SpawnerSavePacket(NetworkBuffer buffer) {
            this(readCompressedString(buffer), buffer.readVarLong());
        }

        private void encode(NetworkBuffer buffer) {
            writeCompressedString(buffer, dataJson);
            buffer.writeVarLong(requestId);
        }
    }

    public record SpawnerSaveResultPacket(long requestId, boolean success) {
        private SpawnerSaveResultPacket(NetworkBuffer buffer) {
            this(buffer.readVarLong(), buffer.readBoolean());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeVarLong(requestId);
            buffer.writeBoolean(success);
        }
    }

    public record RefreshSpawnBackupPacket(int editIndex) {
        private RefreshSpawnBackupPacket(NetworkBuffer buffer) {
            this(buffer.readInt());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeInt(editIndex);
        }
    }

    public record SpawnBackupSyncPacket(int editIndex, String backupJson) {
        private SpawnBackupSyncPacket(NetworkBuffer buffer) {
            this(buffer.readInt(), readCompressedString(buffer));
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeInt(editIndex);
            writeCompressedString(buffer, backupJson);
        }
    }

    public record UpdateProfileCountPacket(int count) {
        private UpdateProfileCountPacket(NetworkBuffer buffer) {
            this(buffer.readVarInt());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeVarInt(count);
        }
    }

    public record RequestOpenEditorPacket(int editorType) {
        private RequestOpenEditorPacket(NetworkBuffer buffer) {
            this(buffer.readVarInt());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeVarInt(editorType);
        }
    }

    public record SpawnSaveResultPacket(boolean success) {
        private SpawnSaveResultPacket(NetworkBuffer buffer) {
            this(buffer.readBoolean());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(success);
        }
    }

    private static String readCompressedString(NetworkBuffer buffer) {
        return KineticCompression.decompressUtf8(buffer.readByteArray(), Integer.MAX_VALUE);
    }

    private static void writeCompressedString(NetworkBuffer buffer, String value) {
        buffer.writeByteArray(KineticCompression.compressUtf8(value, Integer.MAX_VALUE));
    }
}
