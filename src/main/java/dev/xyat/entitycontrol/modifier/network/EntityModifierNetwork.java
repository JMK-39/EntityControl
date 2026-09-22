package dev.xyat.entitycontrol.modifier.network;

import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.event.ModifierEventHandler;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class EntityModifierNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(ModifierModule.MODID, "entity_modifier"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.EXACT
    );
    private static boolean saveRegistered;
    private static boolean openRegistered;
    private static boolean requestRegistered;
    private static boolean resultRegistered;
    private static boolean attributesRegistered;

    private EntityModifierNetwork() {
    }

    public static synchronized void register() {
        PacketRegistrations.runIndependent(
                () -> {
                    if (!saveRegistered) {
                        CHANNEL.registerServerbound(
                                0,
                                SaveModifierPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SaveModifierPacket::new),
                                EntityModifierNetwork::handleSave
                        );
                        saveRegistered = true;
                    }
                },
                () -> {
                    if (!openRegistered) {
                        CHANNEL.registerClientbound(
                                1,
                                OpenModifierScreenPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), OpenModifierScreenPacket::new),
                                message -> EntityModifierNetworkClient.handleOpenScreen(message)
                        );
                        openRegistered = true;
                    }
                },
                () -> {
                    if (!requestRegistered) {
                        CHANNEL.registerServerbound(
                                2,
                                RequestOpenModifierScreenPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), RequestOpenModifierScreenPacket::new),
                                (message, context) -> handleOpenRequest(context.sender())
                        );
                        requestRegistered = true;
                    }
                },
                () -> {
                    if (!resultRegistered) {
                        CHANNEL.registerClientbound(
                                3,
                                SaveModifierResultPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SaveModifierResultPacket::new),
                                message -> EntityModifierNetworkClient.handleSaveResult(message.success)
                        );
                        resultRegistered = true;
                    }
                },
                () -> {
                    if (!attributesRegistered) {
                        CHANNEL.registerClientbound(4, DynamicAttributesPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), DynamicAttributesPacket::new),
                                message -> EntityModifierNetworkClient.handleDynamicAttributes(message));
                        attributesRegistered = true;
                    }
                }
        );
    }

    public static void broadcastAttributeChanges(LivingEntity living, Map<String, Double> added, Set<String> removed) {
        CHANNEL.sendToTrackingAndSelf(living, new DynamicAttributesPacket(living.getId(), added, removed));
    }

    public static void sendAttributeChanges(ServerPlayer player, LivingEntity living,
                                            Map<String, Double> added, Set<String> removed) {
        CHANNEL.sendToPlayer(player, new DynamicAttributesPacket(living.getId(), added, removed));
    }

    public static void requestOpenEditor() {
        CHANNEL.sendToServer(new RequestOpenModifierScreenPacket());
    }

    public static void saveConfig(String jsonConfig) {
        CHANNEL.sendToServer(new SaveModifierPacket(jsonConfig));
    }

    public static void sendEditorSnapshot(ServerPlayer player) {
        String json = EntityModifierConfig.GSON.toJson(EntityModifierConfig.ENTITY_DATA);
        CHANNEL.sendToPlayer(player, new OpenModifierScreenPacket(json));
    }

    private static void handleOpenRequest(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        if (!EntityModifierConfig.loadAndClean(player.getServer())) {
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.load_failed"));
            return;
        }
        sendEditorSnapshot(player);
    }

    private static void handleSave(SaveModifierPacket message, dev.xyat.kineticcore.api.network.ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) {
            CHANNEL.sendToPlayer(player, new SaveModifierResultPacket(false));
            return;
        }
        try {
            java.util.Map<String, EntityModifierConfig.EntityEditData> data =
                    EntityModifierConfig.parseConfigJson(message.jsonConfig);
            if (data == null || player.getServer() == null) {
                player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
                CHANNEL.sendToPlayer(player, new SaveModifierResultPacket(false));
                return;
            }
            java.util.Map<String, EntityModifierConfig.EntityEditData> validated =
                    EntityModifierConfig.validateForServer(data, player.getServer());
            if (!EntityModifierConfig.save(validated)) {
                player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
                CHANNEL.sendToPlayer(player, new SaveModifierResultPacket(false));
                return;
            }
            EntityModifierConfig.ENTITY_DATA = validated;
            ModifierEventHandler.applyToLoaded(player.getServer());
            CHANNEL.sendToPlayer(player, new SaveModifierResultPacket(true));
        } catch (Exception exception) {
            ModifierModule.LOGGER.error("Save config error", exception);
            player.sendSystemMessage(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
            CHANNEL.sendToPlayer(player, new SaveModifierResultPacket(false));
        }
    }

    public record RequestOpenModifierScreenPacket() {
        private RequestOpenModifierScreenPacket(NetworkBuffer buffer) {
            this();
        }

        private void encode(NetworkBuffer buffer) {
        }
    }

    public record SaveModifierPacket(String jsonConfig) {
        private SaveModifierPacket(NetworkBuffer buffer) {
            this(KineticCompression.decompressUtf8(buffer.readByteArray(), Integer.MAX_VALUE));
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeByteArray(KineticCompression.compressUtf8(jsonConfig, Integer.MAX_VALUE));
        }
    }

    public record SaveModifierResultPacket(boolean success) {
        private SaveModifierResultPacket(NetworkBuffer buffer) {
            this(buffer.readBoolean());
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(success);
        }
    }

    public record OpenModifierScreenPacket(String jsonConfig) {
        private OpenModifierScreenPacket(NetworkBuffer buffer) {
            this(KineticCompression.decompressUtf8(buffer.readByteArray(), Integer.MAX_VALUE));
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeByteArray(KineticCompression.compressUtf8(jsonConfig, Integer.MAX_VALUE));
        }
    }
    public record DynamicAttributesPacket(int entityId, Map<String, Double> added, Set<String> removed) {
        private DynamicAttributesPacket(NetworkBuffer buffer) {
            this(buffer.readVarInt(), readAdded(buffer), readRemoved(buffer));
        }

        private static Map<String, Double> readAdded(NetworkBuffer buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid attribute packet entry count");
            Map<String, Double> values = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) values.put(buffer.readUtf(), buffer.readDouble());
            return values;
        }

        private static Set<String> readRemoved(NetworkBuffer buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid attribute packet entry count");
            Set<String> values = new LinkedHashSet<>();
            for (int i = 0; i < size; i++) values.add(buffer.readUtf());
            return values;
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeVarInt(entityId);
            buffer.writeVarInt(added.size());
            added.forEach((id, value) -> { buffer.writeUtf(id); buffer.writeDouble(value); });
            buffer.writeVarInt(removed.size());
            removed.forEach(buffer::writeUtf);
        }
    }

}
