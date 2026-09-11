package dev.xyat.entitycontrol.modifier.network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
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

public class EntityModifierNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModifierModule.MODID, "entity_modifier"),
            () -> PROTOCOL_VERSION,
            KTNetworkProtocol::acceptsAnyVersion,
            KTNetworkProtocol::acceptsAnyVersion
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SaveModifierPacket.class, SaveModifierPacket::encode, SaveModifierPacket::decode, SaveModifierPacket::handle);
        CHANNEL.registerMessage(id++, OpenModifierScreenPacket.class, OpenModifierScreenPacket::encode, OpenModifierScreenPacket::decode, OpenModifierScreenPacket::handle);
        CHANNEL.registerMessage(id++, RequestOpenModifierScreenPacket.class, RequestOpenModifierScreenPacket::encode, RequestOpenModifierScreenPacket::decode, RequestOpenModifierScreenPacket::handle);
        CHANNEL.registerMessage(id, SaveModifierResultPacket.class, SaveModifierResultPacket::encode, SaveModifierResultPacket::decode, SaveModifierResultPacket::handle);
    }

    public static void requestOpenEditor() {
        CHANNEL.sendToServer(new RequestOpenModifierScreenPacket());
    }

    public static void sendEditorSnapshot(ServerPlayer player) {
        String json = EntityModifierConfig.GSON.toJson(EntityModifierConfig.ENTITY_DATA);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenModifierScreenPacket(json));
    }

    public record RequestOpenModifierScreenPacket() {
        public static RequestOpenModifierScreenPacket decode(FriendlyByteBuf buf) {
            return new RequestOpenModifierScreenPacket();
        }

        public static void encode(RequestOpenModifierScreenPacket packet, FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    if (!EntityModifierConfig.loadAndClean(player.getServer())) {
                        player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.load_failed"));
                        return;
                    }
                    sendEditorSnapshot(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveModifierPacket(String jsonConfig) {
        public static SaveModifierPacket decode(FriendlyByteBuf buf) {
            return new SaveModifierPacket(NetworkCompressUtil.decompress(buf.readByteArray()));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(jsonConfig));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveModifierResultPacket(false));
                    return;
                }
                try {
                    java.util.Map<String, EntityModifierConfig.EntityEditData> data = EntityModifierConfig.parseConfigJson(jsonConfig);
                    if (data == null || player.getServer() == null) {
                        player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveModifierResultPacket(false));
                        return;
                    }
                    java.util.Map<String, EntityModifierConfig.EntityEditData> validated =
                            EntityModifierConfig.validateForServer(data, player.getServer());
                    if (!EntityModifierConfig.save(validated)) {
                        player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveModifierResultPacket(false));
                        return;
                    }
                    EntityModifierConfig.ENTITY_DATA = validated;
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveModifierResultPacket(true));
                } catch (Exception e) {
                    ModifierModule.LOGGER.error("Save config error", e);
                    player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveModifierResultPacket(false));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveModifierResultPacket(boolean success) {
        public static SaveModifierResultPacket decode(FriendlyByteBuf buf) {
            return new SaveModifierResultPacket(buf.readBoolean());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> EntityModifierNetworkClient.handleSaveResult(success)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public record OpenModifierScreenPacket(String jsonConfig) {
        public static OpenModifierScreenPacket decode(FriendlyByteBuf buf) {
            return new OpenModifierScreenPacket(NetworkCompressUtil.decompress(buf.readByteArray()));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(jsonConfig));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> EntityModifierNetworkClient.handleOpenScreen(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }
}
