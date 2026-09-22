package dev.xyat.entitycontrol.reset.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.xyat.entitycontrol.reset.ResetModule;
import dev.xyat.entitycontrol.reset.client.EntityReseRuleClient;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class EntityReseRuleNetwork {
    public static final byte RESULT_SAVE_SUCCESS = 1;
    public static final byte RESULT_REMOVE_SUCCESS = 2;
    public static final byte RESULT_PERMISSION_DENIED = 3;
    public static final byte RESULT_INVALID_RULE = 4;
    public static final byte RESULT_SAVE_FAILED = 5;

    private static final String PROTOCOL = "3";
    private static final int MAX_RULES = 4096;
    private static final int MAX_RULE_LENGTH = 512;
    private static final int MAX_COMPRESSED_RULE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_RULE_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private static final Type RULE_LIST_TYPE = new TypeToken<List<String>>() { }.getType();
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(ResetModule.MODID, "entity_rese_rules"),
            PROTOCOL,
            NetworkVersionPolicy.ANY
    );
    private static boolean requestRegistered;
    private static boolean saveRegistered;
    private static boolean removeRegistered;
    private static boolean snapshotRegistered;
    private static boolean resultRegistered;

    private EntityReseRuleNetwork() {
    }

    public static synchronized void register() {
        PacketRegistrations.runIndependent(
                () -> {
                    if (!requestRegistered) {
                        CHANNEL.registerServerbound(0, RequestRulesPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), RequestRulesPacket::new),
                                (message, context) -> sendSnapshot(context.sender()));
                        requestRegistered = true;
                    }
                },
                () -> {
                    if (!saveRegistered) {
                        CHANNEL.registerServerbound(1, SaveRulePacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), SaveRulePacket::new),
                                EntityReseRuleNetwork::handleSaveRule);
                        saveRegistered = true;
                    }
                },
                () -> {
                    if (!removeRegistered) {
                        CHANNEL.registerServerbound(2, RemoveRulePacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), RemoveRulePacket::new),
                                EntityReseRuleNetwork::handleRemoveRule);
                        removeRegistered = true;
                    }
                },
                () -> {
                    if (!snapshotRegistered) {
                        CHANNEL.registerClientbound(3, RulesSnapshotPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), RulesSnapshotPacket::new),
                                message -> EntityReseRuleClient.handleSnapshot(new ArrayList<>(message.rules)));
                        snapshotRegistered = true;
                    }
                },
                () -> {
                    if (!resultRegistered) {
                        CHANNEL.registerClientbound(4, OperationResultPacket.class,
                                NetworkCodec.of((buffer, message) -> message.encode(buffer), OperationResultPacket::new),
                                message -> EntityReseRuleClient.handleOperationResult(message.result, new ArrayList<>(message.rules)));
                        resultRegistered = true;
                    }
                }
        );
    }

    public static void requestRules() {
        CHANNEL.sendToServer(new RequestRulesPacket());
    }

    public static void saveRule(
            String entityId,
            int threshold,
            boolean countRealDeath,
            boolean countPreventedDeath,
            boolean countCancelledDeath
    ) {
        CHANNEL.sendToServer(new SaveRulePacket(
                entityId,
                threshold,
                countRealDeath,
                countPreventedDeath,
                countCancelledDeath
        ));
    }

    public static void removeRule(String entityId) {
        CHANNEL.sendToServer(new RemoveRulePacket(entityId));
    }

    private static void sendSnapshot(ServerPlayer player) {
        CHANNEL.sendToPlayer(player, new RulesSnapshotPacket(EntityReseConfig.snapshotRules()));
    }

    private static void broadcastSnapshot() {
        CHANNEL.broadcast(new RulesSnapshotPacket(EntityReseConfig.snapshotRules()));
    }

    private static void sendResult(ServerPlayer player, byte result) {
        CHANNEL.sendToPlayer(player, new OperationResultPacket(result, EntityReseConfig.snapshotRules()));
    }

    private static boolean cannotEdit(ServerPlayer player) {
        return player == null || !player.hasPermissions(2);
    }

    private static boolean invalidEntityId(String entityId) {
        ResourceLocation id = KineticResourceIds.tryParse(entityId == null ? "" : entityId.trim());
        return id == null || KineticRegistries.entityTypes().get(id) == null;
    }

    public static final class RequestRulesPacket {
        public RequestRulesPacket() {
        }

        private RequestRulesPacket(NetworkBuffer buffer) {
        }

        private void encode(NetworkBuffer buffer) {
        }

    }

    public static final class SaveRulePacket {
        private final String entityId;
        private final int threshold;
        private final boolean countRealDeath;
        private final boolean countPreventedDeath;
        private final boolean countCancelledDeath;

        public SaveRulePacket(
                String entityId,
                int threshold,
                boolean countRealDeath,
                boolean countPreventedDeath,
                boolean countCancelledDeath
        ) {
            this.entityId = entityId == null ? "" : entityId;
            this.threshold = threshold;
            this.countRealDeath = countRealDeath;
            this.countPreventedDeath = countPreventedDeath;
            this.countCancelledDeath = countCancelledDeath;
        }

        private SaveRulePacket(NetworkBuffer buffer) {
            this(
                    buffer.readUtf(MAX_RULE_LENGTH),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeUtf(entityId, MAX_RULE_LENGTH);
            buffer.writeVarInt(threshold);
            buffer.writeBoolean(countRealDeath);
            buffer.writeBoolean(countPreventedDeath);
            buffer.writeBoolean(countCancelledDeath);
        }

    }

    public static final class RemoveRulePacket {
        private final String entityId;

        public RemoveRulePacket(String entityId) {
            this.entityId = entityId == null ? "" : entityId;
        }

        private RemoveRulePacket(NetworkBuffer buffer) {
            this(buffer.readUtf(MAX_RULE_LENGTH));
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeUtf(entityId, MAX_RULE_LENGTH);
        }

    }

    public static final class RulesSnapshotPacket {
        private final List<String> rules;

        public RulesSnapshotPacket(List<String> rules) {
            this.rules = sanitizeRules(rules);
        }

        private RulesSnapshotPacket(NetworkBuffer buffer) {
            this.rules = readRules(buffer);
        }

        private void encode(NetworkBuffer buffer) {
            writeRules(buffer, rules);
        }

    }

    public static final class OperationResultPacket {
        private final byte result;
        private final List<String> rules;

        public OperationResultPacket(byte result, List<String> rules) {
            this.result = result;
            this.rules = sanitizeRules(rules);
        }

        private OperationResultPacket(NetworkBuffer buffer) {
            this.result = buffer.readByte();
            this.rules = readRules(buffer);
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeByte(result);
            writeRules(buffer, rules);
        }

    }

    private static void handleSaveRule(SaveRulePacket message, dev.xyat.kineticcore.api.network.ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (cannotEdit(player)) {
            sendResult(player, RESULT_PERMISSION_DENIED);
            return;
        }
        if (message.threshold < 1 || invalidEntityId(message.entityId)) {
            sendResult(player, RESULT_INVALID_RULE);
            return;
        }

        boolean saved = EntityReseConfig.saveRuleAuthoritative(
                message.entityId,
                message.threshold,
                message.countRealDeath,
                message.countPreventedDeath,
                message.countCancelledDeath
        );
        if (saved) {
            broadcastSnapshot();
            sendResult(player, RESULT_SAVE_SUCCESS);
        } else {
            sendResult(player, RESULT_SAVE_FAILED);
        }
    }

    private static void handleRemoveRule(RemoveRulePacket message, dev.xyat.kineticcore.api.network.ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (cannotEdit(player)) {
            sendResult(player, RESULT_PERMISSION_DENIED);
            return;
        }
        if (invalidEntityId(message.entityId)) {
            sendResult(player, RESULT_INVALID_RULE);
            return;
        }

        boolean saved = EntityReseConfig.removeRuleAuthoritative(message.entityId);
        if (saved) {
            broadcastSnapshot();
            sendResult(player, RESULT_REMOVE_SUCCESS);
        } else {
            sendResult(player, RESULT_SAVE_FAILED);
        }
    }

    private static List<String> sanitizeRules(List<String> rules) {
        List<String> result = new ArrayList<>();
        if (rules == null) return result;
        int limit = Math.min(rules.size(), MAX_RULES);
        for (int i = 0; i < limit; i++) {
            String rule = rules.get(i);
            if (rule != null && rule.length() <= MAX_RULE_LENGTH) result.add(rule);
        }
        return result;
    }

    private static void writeRules(NetworkBuffer buffer, List<String> rules) {
        List<String> safeRules = sanitizeRules(rules);
        byte[] compressed = KineticCompression.compressUtf8(GSON.toJson(safeRules, RULE_LIST_TYPE), MAX_COMPRESSED_RULE_BYTES, MAX_DECOMPRESSED_RULE_BYTES);
        if (compressed.length > MAX_COMPRESSED_RULE_BYTES) {
            throw new IllegalArgumentException("compressed entity rule payload is too large");
        }
        buffer.writeByteArray(compressed);
    }

    private static List<String> readRules(NetworkBuffer buffer) {
        byte[] compressed = buffer.readByteArray(MAX_COMPRESSED_RULE_BYTES);
        String json = KineticCompression.decompressUtf8(compressed, MAX_DECOMPRESSED_RULE_BYTES);
        List<String> decoded = GSON.fromJson(json, RULE_LIST_TYPE);
        if (decoded == null) return new ArrayList<>();
        if (decoded.size() > MAX_RULES) {
            throw new IllegalArgumentException("invalid entity rule count: " + decoded.size());
        }
        for (String rule : decoded) {
            if (rule == null || rule.length() > MAX_RULE_LENGTH) {
                throw new IllegalArgumentException("invalid entity rule payload");
            }
        }
        return new ArrayList<>(decoded);
    }
}
