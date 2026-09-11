package dev.xyat.entitycontrol.breakspawn.network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.event.BreakSpawnEventHandler;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
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

public final class BreakSpawnNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BreakSpawnModule.MODID, "break_spawn"),
            () -> PROTOCOL_VERSION,
            KTNetworkProtocol::acceptsAnyVersion,
            KTNetworkProtocol::acceptsAnyVersion
    );

    private BreakSpawnNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SaveConfigPacket.class, SaveConfigPacket::encode, SaveConfigPacket::decode, SaveConfigPacket::handle);
        CHANNEL.registerMessage(id++, OpenEditorPacket.class, OpenEditorPacket::encode, OpenEditorPacket::decode, OpenEditorPacket::handle);
        CHANNEL.registerMessage(id++, RequestOpenEditorPacket.class, RequestOpenEditorPacket::encode, RequestOpenEditorPacket::decode, RequestOpenEditorPacket::handle);
        CHANNEL.registerMessage(id, SaveConfigResultPacket.class, SaveConfigResultPacket::encode, SaveConfigResultPacket::decode, SaveConfigResultPacket::handle);
    }

    public static void requestOpenEditor() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> BreakSpawnNetworkClient::captureReturnScreen);
        CHANNEL.sendToServer(new RequestOpenEditorPacket());
    }

    public static void sendEditorSnapshot(ServerPlayer player) {
        String json = BreakSpawnConfig.GSON.toJson(BreakSpawnConfig.CURRENT);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenEditorPacket(json));
    }

    public record RequestOpenEditorPacket() {
        public static RequestOpenEditorPacket decode(FriendlyByteBuf buf) {
            return new RequestOpenEditorPacket();
        }

        public static void encode(RequestOpenEditorPacket packet, FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) {
                    return;
                }
                if (!BreakSpawnConfig.loadAndClean(player.getServer())) {
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.breakspawn.load_failed"));
                    return;
                }
                sendEditorSnapshot(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveConfigPacket(String jsonConfig) {
        public static SaveConfigPacket decode(FriendlyByteBuf buf) {
            return new SaveConfigPacket(NetworkCompressUtil.decompress(buf.readByteArray()));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(jsonConfig));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (!player.hasPermissions(2) || player.getServer() == null) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveConfigResultPacket(false));
                    return;
                }
                BreakSpawnConfig.ConfigRoot parsed = BreakSpawnConfig.parseConfigJson(jsonConfig);
                BreakSpawnConfig.ConfigRoot validated = BreakSpawnConfig.validateForServer(parsed, player.getServer());
                if (validated == null) {
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.breakspawn.save_invalid"));
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveConfigResultPacket(false));
                    return;
                }
                if (!BreakSpawnConfig.save(validated)) {
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.breakspawn.save_failed"));
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveConfigResultPacket(false));
                    return;
                }
                BreakSpawnConfig.CURRENT = validated;
                BreakSpawnEventHandler.resetRuntimeState();
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveConfigResultPacket(true));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveConfigResultPacket(boolean success) {
        public static SaveConfigResultPacket decode(FriendlyByteBuf buf) {
            return new SaveConfigResultPacket(buf.readBoolean());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> BreakSpawnNetworkClient.handleSaveResult(success)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public record OpenEditorPacket(String jsonConfig) {
        public static OpenEditorPacket decode(FriendlyByteBuf buf) {
            return new OpenEditorPacket(NetworkCompressUtil.decompress(buf.readByteArray()));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(jsonConfig));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> BreakSpawnNetworkClient.handleOpenScreen(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }
}
