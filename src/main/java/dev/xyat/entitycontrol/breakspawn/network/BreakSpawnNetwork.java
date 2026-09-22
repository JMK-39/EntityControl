package dev.xyat.entitycontrol.breakspawn.network;

import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.event.BreakSpawnEventHandler;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BreakSpawnNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(BreakSpawnModule.MODID, "break_spawn"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.ANY
    );
    private static boolean saveRegistered;
    private static boolean openRegistered;
    private static boolean requestRegistered;
    private static boolean resultRegistered;

    private BreakSpawnNetwork() {
    }

    public static synchronized void register() {
        PacketRegistrations.runIndependent(
                () -> {
                    if (!saveRegistered) {
                        CHANNEL.registerServerbound(
                                0,
                                SaveConfigPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SaveConfigPacket::new),
                                BreakSpawnNetwork::handleSave
                        );
                        saveRegistered = true;
                    }
                },
                () -> {
                    if (!openRegistered) {
                        CHANNEL.registerClientbound(
                                1,
                                OpenEditorPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), OpenEditorPacket::new),
                                message -> BreakSpawnNetworkClient.handleOpenScreen(message)
                        );
                        openRegistered = true;
                    }
                },
                () -> {
                    if (!requestRegistered) {
                        CHANNEL.registerServerbound(
                                2,
                                RequestOpenEditorPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), RequestOpenEditorPacket::new),
                                (message, context) -> handleOpenRequest(context.sender())
                        );
                        requestRegistered = true;
                    }
                },
                () -> {
                    if (!resultRegistered) {
                        CHANNEL.registerClientbound(
                                3,
                                SaveConfigResultPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SaveConfigResultPacket::new),
                                message -> BreakSpawnNetworkClient.handleSaveResult(message.success)
                        );
                        resultRegistered = true;
                    }
                }
        );
    }

    public static void requestOpenEditor() {
        KineticPlatform.runOnClient(() -> BreakSpawnNetworkClient::captureReturnScreen);
        CHANNEL.sendToServer(new RequestOpenEditorPacket());
    }

    public static void saveConfig(String jsonConfig) {
        CHANNEL.sendToServer(new SaveConfigPacket(jsonConfig));
    }

    public static void sendEditorSnapshot(ServerPlayer player) {
        String json = BreakSpawnConfig.GSON.toJson(BreakSpawnConfig.CURRENT);
        CHANNEL.sendToPlayer(player, new OpenEditorPacket(json));
    }

    private static void handleOpenRequest(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }
        if (!BreakSpawnConfig.loadAndClean(player.getServer())) {
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.breakspawn.load_failed"));
            return;
        }
        sendEditorSnapshot(player);
    }

    private static void handleSave(SaveConfigPacket message, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (player == null || !player.hasPermissions(2) || player.getServer() == null) {
            if (player != null) {
                CHANNEL.sendToPlayer(player, new SaveConfigResultPacket(false));
            }
            return;
        }

        BreakSpawnConfig.ConfigRoot parsed = BreakSpawnConfig.parseConfigJson(message.jsonConfig);
        BreakSpawnConfig.ConfigRoot validated = BreakSpawnConfig.validateForServer(parsed, player.getServer());
        if (validated == null) {
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.breakspawn.save_invalid"));
            CHANNEL.sendToPlayer(player, new SaveConfigResultPacket(false));
            return;
        }
        if (!BreakSpawnConfig.save(validated)) {
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.breakspawn.save_failed"));
            CHANNEL.sendToPlayer(player, new SaveConfigResultPacket(false));
            return;
        }

        BreakSpawnConfig.CURRENT = validated;
        BreakSpawnEventHandler.resetRuntimeState();
        CHANNEL.sendToPlayer(player, new SaveConfigResultPacket(true));
    }

    public record RequestOpenEditorPacket() {
        private RequestOpenEditorPacket(NetworkBuffer buffer) {
            this();
        }

        private void encode(NetworkBuffer buffer) {
        }
    }

    public record SaveConfigPacket(String jsonConfig) {
        private SaveConfigPacket(NetworkBuffer buffer) {
            this(KineticCompression.decompressUtf8(buffer.readByteArray(), Integer.MAX_VALUE));
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeByteArray(KineticCompression.compressUtf8(jsonConfig, Integer.MAX_VALUE));
        }
    }

    public record SaveConfigResultPacket(boolean success) {
        private SaveConfigResultPacket(NetworkBuffer buffer) {
            this(buffer.readBoolean());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(success);
        }
    }

    public record OpenEditorPacket(String jsonConfig) {
        private OpenEditorPacket(NetworkBuffer buffer) {
            this(KineticCompression.decompressUtf8(buffer.readByteArray(), Integer.MAX_VALUE));
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeByteArray(KineticCompression.compressUtf8(jsonConfig, Integer.MAX_VALUE));
        }
    }
}
