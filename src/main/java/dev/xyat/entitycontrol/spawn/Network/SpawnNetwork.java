package dev.xyat.entitycontrol.spawn.Network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.entitycontrol.spawn.SpawnModule;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class SpawnNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SpawnModule.MODID, "spawn_control"),
            () -> PROTOCOL_VERSION,
            KTNetworkProtocol::acceptsAnyVersion,
            KTNetworkProtocol::acceptsAnyVersion
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
        CHANNEL.registerMessage(id++, SavePacket.class, SavePacket::encode, SavePacket::decode, SavePacket::handle);
        CHANNEL.registerMessage(id++, SwitchProfilePacket.class, SwitchProfilePacket::encode, SwitchProfilePacket::decode, SwitchProfilePacket::handle);
        CHANNEL.registerMessage(id++, SpawnerSyncPacket.class, SpawnerSyncPacket::encode, SpawnerSyncPacket::decode, SpawnerSyncPacket::handle);
        CHANNEL.registerMessage(id++, SpawnerSavePacket.class, SpawnerSavePacket::encode, SpawnerSavePacket::decode, SpawnerSavePacket::handle);
        CHANNEL.registerMessage(id++, SpawnerSaveResultPacket.class, SpawnerSaveResultPacket::encode, SpawnerSaveResultPacket::decode, SpawnerSaveResultPacket::handle);
        CHANNEL.registerMessage(id++, RefreshSpawnBackupPacket.class, RefreshSpawnBackupPacket::encode, RefreshSpawnBackupPacket::decode, RefreshSpawnBackupPacket::handle);
        CHANNEL.registerMessage(id++, SpawnBackupSyncPacket.class, SpawnBackupSyncPacket::encode, SpawnBackupSyncPacket::decode, SpawnBackupSyncPacket::handle);
        CHANNEL.registerMessage(id++, UpdateProfileCountPacket.class, UpdateProfileCountPacket::encode, UpdateProfileCountPacket::decode, UpdateProfileCountPacket::handle);
        CHANNEL.registerMessage(id++, RequestOpenEditorPacket.class, RequestOpenEditorPacket::encode, RequestOpenEditorPacket::decode, RequestOpenEditorPacket::handle);
        CHANNEL.registerMessage(id, SpawnSaveResultPacket.class, SpawnSaveResultPacket::encode, SpawnSaveResultPacket::decode, SpawnSaveResultPacket::handle);
    }

    public static final int EDITOR_SPAWN = 0;
    public static final int EDITOR_SPAWNER = 1;

    public static void requestOpenEditor(int editorType) {
        CHANNEL.sendToServer(new RequestOpenEditorPacket(editorType));
    }

    public record UpdateProfileCountPacket(int count) {
        public static UpdateProfileCountPacket decode(FriendlyByteBuf buf) {
            return new UpdateProfileCountPacket(buf.readVarInt());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(count);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                BiomeSpawnConfig.loadGlobals();
                if (!BiomeSpawnConfig.isValidProfileCount(count) || count < BiomeSpawnConfig.globals.current_index) {
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.profile_count_invalid"));
                    return;
                }
                BiomeSpawnConfig.globals.config_amount = count;
                if (!BiomeSpawnConfig.saveGlobals()) {
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_failed"));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestOpenEditorPacket(int editorType) {
        public static RequestOpenEditorPacket decode(FriendlyByteBuf buf) {
            return new RequestOpenEditorPacket(buf.readVarInt());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(editorType);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                if (editorType == EDITOR_SPAWN) {
                    BiomeSpawnConfig.loadGlobals();
                    sendSyncToPlayer(player, BiomeSpawnConfig.globals.current_index);
                } else if (editorType == EDITOR_SPAWNER) {
                    SpawnerConfig.load();
                    sendSpawnerSyncToPlayer(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
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

        String profileJson = BiomeSpawnConfig.GSON.toJson(profile);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncPacket(
                        BiomeSpawnConfig.globals.enable_rule_override,
                        BiomeSpawnConfig.globals.enable_biome_override,
                        BiomeSpawnConfig.globals.auto_scan,
                        BiomeSpawnConfig.globals.config_amount,
                        BiomeSpawnConfig.globals.current_index,
                        profileJson,
                        editIndex
                )
        );
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SpawnBackupSyncPacket(
                        editIndex,
                        BiomeSpawnConfig.GSON.toJson(backupProfile)
                )
        );
    }

    public static void sendSpawnerSyncToPlayer(ServerPlayer player) {
        SpawnerConfig.cleanupInvalidEntityRules();
        SpawnerConfig.SpawnerEditorSnapshot snapshot = SpawnerConfig.createEditorSnapshot(player.server);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SpawnerSyncPacket(SpawnerConfig.GSON.toJson(snapshot))
        );
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
        public static SyncPacket decode(FriendlyByteBuf buf) {
            return new SyncPacket(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    NetworkCompressUtil.decompress(buf.readByteArray()),
                    buf.readInt()
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(ruleOverride);
            buf.writeBoolean(biomeOverride);
            buf.writeBoolean(autoScan);
            buf.writeInt(amount);
            buf.writeInt(currentIndex);
            buf.writeByteArray(NetworkCompressUtil.compress(profileJson));
            buf.writeInt(editIndex);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SpawnNetworkClient.handleSync(this)
            ));
            ctx.get().setPacketHandled(true);
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
        public static SavePacket decode(FriendlyByteBuf buf) {
            return new SavePacket(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    NetworkCompressUtil.decompress(buf.readByteArray()),
                    buf.readInt()
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(ruleOverride);
            buf.writeBoolean(biomeOverride);
            buf.writeBoolean(autoScan);
            buf.writeInt(amount);
            buf.writeInt(currentIndex);
            buf.writeByteArray(NetworkCompressUtil.compress(profileJson));
            buf.writeInt(editIndex);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) {
                    sendSpawnSaveResult(player, false);
                    return;
                }

                if (!BiomeSpawnConfig.isValidProfileCount(amount)
                        || currentIndex < 1 || currentIndex > amount
                        || editIndex < 1 || editIndex > amount) {
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_invalid"));
                    sendSpawnSaveResult(player, false);
                    return;
                }

                try {
                    BiomeSpawnConfig.ConfigProfile profile = BiomeSpawnConfig.GSON.fromJson(
                            profileJson,
                            BiomeSpawnConfig.ConfigProfile.class
                    );
                    if (profile == null) {
                        player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_invalid"));
                        sendSpawnSaveResult(player, false);
                        return;
                    }

                    if (!BiomeSpawnConfig.saveEditorState(
                            player.server,
                            ruleOverride,
                            biomeOverride,
                            autoScan,
                            amount,
                            currentIndex,
                            editIndex,
                            profile
                    )) {
                        player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_failed"));
                        sendSpawnSaveResult(player, false);
                        return;
                    }

                    sendSpawnSaveResult(player, true);
                } catch (RuntimeException exception) {
                    SpawnModule.LOGGER.warn("Rejected invalid spawn-control config from {}", player.getGameProfile().getName(), exception);
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawn.save_invalid"));
                    sendSpawnSaveResult(player, false);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static void sendSpawnSaveResult(ServerPlayer player, boolean success) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SpawnSaveResultPacket(success)
        );
    }

    public record SpawnSaveResultPacket(boolean success) {
        public static SpawnSaveResultPacket decode(FriendlyByteBuf buf) {
            return new SpawnSaveResultPacket(buf.readBoolean());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SpawnNetworkClient.handleSpawnSaveResult(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SwitchProfilePacket(int requestIndex) {
        public static SwitchProfilePacket decode(FriendlyByteBuf buf) {
            return new SwitchProfilePacket(buf.readInt());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(requestIndex);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    BiomeSpawnConfig.loadGlobals();
                    if (BiomeSpawnConfig.isValidProfileIndex(requestIndex)) {
                        sendSyncToPlayer(player, requestIndex);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record RefreshSpawnBackupPacket(int editIndex) {
        public static RefreshSpawnBackupPacket decode(FriendlyByteBuf buf) {
            return new RefreshSpawnBackupPacket(buf.readInt());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(editIndex);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) {
                    return;
                }

                BiomeSpawnConfig.loadGlobals();
                if (editIndex < 1 || editIndex > BiomeSpawnConfig.globals.config_amount) {
                    return;
                }

                BiomeSpawnConfig.ConfigProfile backup =
                        BiomeSpawnConfig.refreshAutoScanBackup(player.server);

                CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new SpawnBackupSyncPacket(
                                editIndex,
                                BiomeSpawnConfig.GSON.toJson(backup)
                        )
                );
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SpawnBackupSyncPacket(int editIndex, String backupJson) {
        public static SpawnBackupSyncPacket decode(FriendlyByteBuf buf) {
            return new SpawnBackupSyncPacket(
                    buf.readInt(),
                    NetworkCompressUtil.decompress(buf.readByteArray())
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(editIndex);
            buf.writeByteArray(NetworkCompressUtil.compress(backupJson));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SpawnNetworkClient.handleSpawnBackupSync(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SpawnerSyncPacket(String snapshotJson) {
        public static SpawnerSyncPacket decode(FriendlyByteBuf buf) {
            return new SpawnerSyncPacket(
                    NetworkCompressUtil.decompress(buf.readByteArray())
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(snapshotJson));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SpawnNetworkClient.handleSpawnerSync(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SpawnerSavePacket(String dataJson, long requestId) {
        public static SpawnerSavePacket decode(FriendlyByteBuf buf) {
            return new SpawnerSavePacket(
                    NetworkCompressUtil.decompress(buf.readByteArray()),
                    buf.readVarLong()
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(dataJson));
            buf.writeVarLong(requestId);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                if (!player.hasPermissions(2)) {
                    sendSpawnerSaveResult(player, requestId, false);
                    return;
                }

                try {
                    SpawnerConfig.SpawnerData incoming = SpawnerConfig.GSON.fromJson(
                            dataJson,
                            SpawnerConfig.SpawnerData.class
                    );
                    if (incoming == null) {
                        player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawner.save_invalid"));
                        sendSpawnerSaveResult(player, requestId, false);
                        return;
                    }
                    if (!SpawnerConfig.applyEditorSnapshot(incoming)) {
                        player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawner.save_failed"));
                        sendSpawnerSaveResult(player, requestId, false);
                        return;
                    }
                    sendSpawnerSaveResult(player, requestId, true);
                } catch (RuntimeException exception) {
                    SpawnModule.LOGGER.warn("Rejected invalid spawner-control config from {}", player.getGameProfile().getName(), exception);
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.spawn.spawner.save_invalid"));
                    sendSpawnerSaveResult(player, requestId, false);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static void sendSpawnerSaveResult(ServerPlayer player, long requestId, boolean success) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SpawnerSaveResultPacket(requestId, success)
        );
    }

    public record SpawnerSaveResultPacket(long requestId, boolean success) {
        public static SpawnerSaveResultPacket decode(FriendlyByteBuf buf) {
            return new SpawnerSaveResultPacket(buf.readVarLong(), buf.readBoolean());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(requestId);
            buf.writeBoolean(success);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SpawnNetworkClient.handleSpawnerSaveResult(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }
}
