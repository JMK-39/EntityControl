package dev.xyat.entitycontrol.dummy.Network;

import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.dummy.DummyUtils;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.registry.KineticEntityAttributes;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class DummyNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int ID_SYNC = 0;
    private static final int ID_UPDATE_LEGACY = 1;
    private static final int ID_SYNC_NOTIFY = 2;
    private static final int ID_UPDATE_CURIO_LEGACY = 3;
    private static final int ID_UPDATE_V2 = 4;
    private static final int ID_UPDATE_CURIO_V2 = 5;

    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(DummyModule.MODID, "dummy"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.ANY
    );
    private static boolean syncRegistered;
    private static boolean updateLegacyRegistered;
    private static boolean syncNotifyRegistered;
    private static boolean updateCurioLegacyRegistered;
    private static boolean updateV2Registered;
    private static boolean updateCurioV2Registered;

    public static synchronized void register() {
        PacketRegistrations.runIndependent(
                () -> {
                    if (!syncRegistered) {
                        CHANNEL.registerClientbound(
                                ID_SYNC,
                                Sync.class,
                                NetworkCodec.of((buffer, message) -> message.toBytes(buffer), Sync::new),
                                message -> DummyNetworkClient.handleSync(message)
                        );
                        syncRegistered = true;
                    }
                },
                () -> {
                    if (!updateLegacyRegistered) {
                        CHANNEL.registerServerbound(
                                ID_UPDATE_LEGACY,
                                Update.class,
                                NetworkCodec.of((buffer, message) -> message.toBytes(buffer), Update::new),
                                (message, context) -> applyUpdate(
                                        context.sender(),
                                        -1,
                                        message.entityId,
                                        message.mobTypeId,
                                        message.attributeData,
                                        message.iFrames,
                                        message.healthDrop,
                                        message.environmentDamage
                                )
                        );
                        updateLegacyRegistered = true;
                    }
                },
                () -> {
                    if (!syncNotifyRegistered) {
                        CHANNEL.registerClientbound(
                                ID_SYNC_NOTIFY,
                                SyncNotify.class,
                                NetworkCodec.of((buffer, message) -> message.toBytes(buffer), SyncNotify::new),
                                message -> DummyNetworkClient.handleNotify(message)
                        );
                        syncNotifyRegistered = true;
                    }
                },
                () -> {
                    if (!updateCurioLegacyRegistered) {
                        CHANNEL.registerServerbound(
                                ID_UPDATE_CURIO_LEGACY,
                                UpdateCurio.class,
                                NetworkCodec.of((buffer, message) -> message.toBytes(buffer), UpdateCurio::new),
                                (message, context) -> {
                                    ItemStack template = message.stack.isEmpty()
                                            ? ItemStack.EMPTY
                                            : defaultStack(KineticRegistries.items().id(message.stack.getItem()));
                                    applyCurioUpdate(context.sender(), -1, message.entityId, message.slotIndex, template);
                                }
                        );
                        updateCurioLegacyRegistered = true;
                    }
                },
                () -> {
                    if (!updateV2Registered) {
                        CHANNEL.registerServerbound(
                                ID_UPDATE_V2,
                                UpdateV2.class,
                                NetworkCodec.of((buffer, message) -> message.toBytes(buffer), UpdateV2::new),
                                (message, context) -> applyUpdate(
                                        context.sender(),
                                        message.containerId,
                                        message.entityId,
                                        message.mobTypeId,
                                        message.attributeData,
                                        message.iFrames,
                                        message.healthDrop,
                                        message.environmentDamage
                                )
                        );
                        updateV2Registered = true;
                    }
                },
                () -> {
                    if (!updateCurioV2Registered) {
                        CHANNEL.registerServerbound(
                                ID_UPDATE_CURIO_V2,
                                UpdateCurioV2.class,
                                NetworkCodec.of((buffer, message) -> message.toBytes(buffer), UpdateCurioV2::new),
                                (message, context) -> {
                                    ServerPlayer player = context.sender();
                                    ItemStack template = message.resolveTemplate(player);
                                    applyCurioUpdate(player, message.containerId, message.entityId, message.slotIndex, template);
                                }
                        );
                        updateCurioV2Registered = true;
                    }
                }
        );
    }

    public static class Sync {
        public enum Type { REALTIME, SUMMARY }

        public final Type type;
        public final Component name;
        public final float total;
        public final float dps;
        public float avgDps;
        public final int hits;
        public int entityId;
        public Component typeName;
        public float currentDamage;
        public boolean isDummy;
        public float time;
        public int attackerId;
        public int minionOwnerId;

        public static Sync realtime(int entityId, Component sourceName, Component typeName, float total, float dps, float avgDps, int hits, float current, boolean isDummy, int attackerId, int minionOwnerId) {
            Sync p = new Sync(Type.REALTIME, sourceName, total, dps, hits);
            p.avgDps = avgDps;
            p.entityId = entityId;
            p.typeName = typeName;
            p.currentDamage = current;
            p.isDummy = isDummy;
            p.attackerId = attackerId;
            p.minionOwnerId = minionOwnerId;
            return p;
        }

        public static Sync summary(Component name, float total, float dps, float time, int hits) {
            Sync p = new Sync(Type.SUMMARY, name, total, dps, hits);
            p.time = time;
            return p;
        }

        private Sync(Type type, Component name, float total, float dps, int hits) {
            this.type = type;
            this.name = name;
            this.total = total;
            this.dps = dps;
            this.hits = hits;
            this.minionOwnerId = -1;
        }

        public Sync(NetworkBuffer buf) {
            this.type = buf.readEnum(Type.class);
            this.name = buf.readComponent();
            this.total = buf.readFloat();
            this.dps = buf.readFloat();
            this.hits = buf.readInt();
            if (this.type == Type.REALTIME) {
                this.avgDps = buf.readFloat();
                this.entityId = buf.readInt();
                this.typeName = buf.readComponent();
                this.currentDamage = buf.readFloat();
                this.isDummy = buf.readBoolean();
                this.attackerId = buf.readInt();
                this.minionOwnerId = buf.readInt();
            } else {
                this.time = buf.readFloat();
            }
        }

        public void toBytes(NetworkBuffer buf) {
            buf.writeEnum(type);
            buf.writeComponent(name);
            buf.writeFloat(total);
            buf.writeFloat(dps);
            buf.writeInt(hits);
            if (this.type == Type.REALTIME) {
                buf.writeFloat(avgDps);
                buf.writeInt(entityId);
                buf.writeComponent(typeName);
                buf.writeFloat(currentDamage);
                buf.writeBoolean(isDummy);
                buf.writeInt(attackerId);
                buf.writeInt(minionOwnerId);
            } else {
                buf.writeFloat(time);
            }
        }

    }

    public static class Update {
        private final int entityId;
        private final int mobTypeId;
        private final CompoundTag attributeData;
        private final boolean iFrames;
        private final boolean healthDrop;
        private final boolean environmentDamage;

        public Update(int entityId, int mobTypeId, CompoundTag attributeData,
                      boolean iFrames, boolean healthDrop, boolean environmentDamage) {
            this.entityId = entityId;
            this.mobTypeId = mobTypeId;
            this.attributeData = attributeData;
            this.iFrames = iFrames;
            this.healthDrop = healthDrop;
            this.environmentDamage = environmentDamage;
        }

        public Update(NetworkBuffer buf) {
            this.entityId = buf.readInt();
            this.mobTypeId = buf.readInt();
            this.attributeData = buf.readNbt();
            this.iFrames = buf.readBoolean();
            this.healthDrop = buf.readBoolean();
            this.environmentDamage = buf.readBoolean();
        }

        public void toBytes(NetworkBuffer buf) {
            buf.writeInt(entityId);
            buf.writeInt(mobTypeId);
            buf.writeNbt(attributeData);
            buf.writeBoolean(iFrames);
            buf.writeBoolean(healthDrop);
            buf.writeBoolean(environmentDamage);
        }

    }

    public static class UpdateV2 {
        private final int containerId;
        private final int entityId;
        private final int mobTypeId;
        private final CompoundTag attributeData;
        private final boolean iFrames;
        private final boolean healthDrop;
        private final boolean environmentDamage;

        public UpdateV2(int containerId, int entityId, int mobTypeId, CompoundTag attributeData,
                        boolean iFrames, boolean healthDrop, boolean environmentDamage) {
            this.containerId = containerId;
            this.entityId = entityId;
            this.mobTypeId = mobTypeId;
            this.attributeData = attributeData;
            this.iFrames = iFrames;
            this.healthDrop = healthDrop;
            this.environmentDamage = environmentDamage;
        }

        public UpdateV2(NetworkBuffer buf) {
            this.containerId = buf.readVarInt();
            this.entityId = buf.readInt();
            this.mobTypeId = buf.readInt();
            this.attributeData = buf.readNbt();
            this.iFrames = buf.readBoolean();
            this.healthDrop = buf.readBoolean();
            this.environmentDamage = buf.readBoolean();
        }

        public void toBytes(NetworkBuffer buf) {
            buf.writeVarInt(containerId);
            buf.writeInt(entityId);
            buf.writeInt(mobTypeId);
            buf.writeNbt(attributeData);
            buf.writeBoolean(iFrames);
            buf.writeBoolean(healthDrop);
            buf.writeBoolean(environmentDamage);
        }

    }

    public static class UpdateCurio {
        private final int entityId;
        private final int slotIndex;
        private final ItemStack stack;

        public UpdateCurio(int entityId, int slotIndex, ItemStack stack) {
            this.entityId = entityId;
            this.slotIndex = slotIndex;
            this.stack = stack;
        }

        public UpdateCurio(NetworkBuffer buf) {
            this.entityId = buf.readInt();
            this.slotIndex = buf.readInt();
            this.stack = buf.readItemStack();
        }

        public void toBytes(NetworkBuffer buf) {
            buf.writeInt(entityId);
            buf.writeInt(slotIndex);
            buf.writeItemStack(stack);
        }

    }

    public static class UpdateCurioV2 {
        public enum Source {
            CLEAR,
            CARRIED,
            INVENTORY,
            DEFAULT_ITEM
        }

        private static final ResourceLocation AIR_ID = KineticResourceIds.of("minecraft", "air");

        private final int containerId;
        private final int entityId;
        private final int slotIndex;
        private final Source source;
        private final int inventoryIndex;
        private final ResourceLocation itemId;

        private UpdateCurioV2(int containerId, int entityId, int slotIndex, Source source,
                              int inventoryIndex, ResourceLocation itemId) {
            this.containerId = containerId;
            this.entityId = entityId;
            this.slotIndex = slotIndex;
            this.source = source;
            this.inventoryIndex = inventoryIndex;
            this.itemId = itemId == null ? AIR_ID : itemId;
        }

        public static UpdateCurioV2 clear(int containerId, int entityId, int slotIndex) {
            return new UpdateCurioV2(containerId, entityId, slotIndex, Source.CLEAR, -1, AIR_ID);
        }

        public static UpdateCurioV2 fromCarried(int containerId, int entityId, int slotIndex) {
            return new UpdateCurioV2(containerId, entityId, slotIndex, Source.CARRIED, -1, AIR_ID);
        }

        public static UpdateCurioV2 fromInventory(int containerId, int entityId, int slotIndex, int inventoryIndex) {
            return new UpdateCurioV2(containerId, entityId, slotIndex, Source.INVENTORY, inventoryIndex, AIR_ID);
        }

        public static UpdateCurioV2 defaultItem(int containerId, int entityId, int slotIndex, ResourceLocation itemId) {
            return new UpdateCurioV2(containerId, entityId, slotIndex, Source.DEFAULT_ITEM, -1, itemId);
        }

        public UpdateCurioV2(NetworkBuffer buf) {
            this.containerId = buf.readVarInt();
            this.entityId = buf.readInt();
            this.slotIndex = buf.readVarInt();
            this.source = buf.readEnum(Source.class);
            this.inventoryIndex = buf.readInt();
            this.itemId = buf.readResourceLocation();
        }

        public void toBytes(NetworkBuffer buf) {
            buf.writeVarInt(containerId);
            buf.writeInt(entityId);
            buf.writeVarInt(slotIndex);
            buf.writeEnum(source);
            buf.writeInt(inventoryIndex);
            buf.writeResourceLocation(itemId);
        }


        private ItemStack resolveTemplate(ServerPlayer player) {
            if (player == null || !(player.containerMenu instanceof DummyMenu menu)) {
                return null;
            }
            return switch (source) {
                case CLEAR -> ItemStack.EMPTY;
                case CARRIED -> menu.getCarried().isEmpty() ? null : menu.getCarried();
                case INVENTORY -> inventoryIndex >= 0 && inventoryIndex < 36
                        ? emptyToNull(player.getInventory().getItem(inventoryIndex))
                        : null;
                case DEFAULT_ITEM -> defaultStack(itemId);
            };
        }

    }

    private static void applyUpdate(ServerPlayer player, int containerId, int entityId, int mobTypeId,
                                    CompoundTag attributeData, boolean iFrames, boolean healthDrop,
                                    boolean environmentDamage) {
        DummyEntityTest dummy = getOpenDummy(player, containerId, entityId);
        if (dummy == null || mobTypeId < 0 || mobTypeId > 4) {
            return;
        }

        List<AttributeWrite> writes = validateAttributeWrites(dummy, attributeData);
        if (writes == null) {
            return;
        }

        dummy.setCustomMobType(mobTypeId);
        dummy.setIFrames(iFrames);
        dummy.setHealthDrop(healthDrop);
        dummy.setEnvironmentDamage(environmentDamage);
        for (AttributeWrite write : writes) {
            KineticEntityAttributes.ensureInstance(dummy, write.attribute());
            dummy.setAttributeBaseValue(write.attribute(), write.value());
        }

        dummy.savePresetToOwner();
        sendToPlayer(new SyncNotify(), player);
    }

    private static void applyCurioUpdate(ServerPlayer player, int containerId, int entityId,
                                         int slotIndex, ItemStack template) {
        DummyEntityTest dummy = getOpenDummy(player, containerId, entityId);
        if (dummy == null
                || slotIndex < 0
                || slotIndex >= CuriosCompat.getSlotCount(dummy)
                || template == null) {
            return;
        }

        ItemStack stack = ItemStack.EMPTY;
        if (!template.isEmpty()) {
            if (template.getCount() <= 0 || template.getCount() > template.getMaxStackSize()) {
                return;
            }
            if (DummyUtils.isBlacklisted(template)) {
                sendToPlayer(new SyncNotify(Component.translatable("msg.entitycontrol.dummy.dummy.blacklisted")), player);
                return;
            }
            if (template.getTags().noneMatch(tag -> tag.location().getNamespace().equals("curios"))) {
                sendToPlayer(new SyncNotify(Component.translatable("msg.entitycontrol.dummy.dummy.not_a_curio")), player);
                return;
            }

            stack = template.copy();
            stack.setCount(1);
            stack.getOrCreateTag().putBoolean("KTDummyItem", true);
        }

        if (CuriosCompat.setCurioItem(dummy, slotIndex, stack)) {
            dummy.savePresetToOwner();
            sendToPlayer(new SyncNotify(), player);
        }
    }

    private static DummyEntityTest getOpenDummy(ServerPlayer player, int containerId, int entityId) {
        if (player == null || !player.isAlive() || !(player.containerMenu instanceof DummyMenu menu)) {
            return null;
        }
        DummyEntityTest dummy = menu.entity;
        if ((containerId >= 0 && menu.containerId != containerId)
                || dummy == null
                || dummy.getId() != entityId
                || dummy.isRemoved()
                || dummy.level() != player.level()
                || player.level().getEntity(entityId) != dummy
                || !menu.stillValid(player)) {
            return null;
        }
        return dummy;
    }

    private static List<AttributeWrite> validateAttributeWrites(DummyEntityTest dummy, CompoundTag data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        if (data.getAllKeys().size() > 1) {
            return null;
        }

        List<AttributeWrite> writes = new ArrayList<>(1);
        for (String key : data.getAllKeys()) {
            if (key.length() > 128 || !data.contains(key, Tag.TAG_ANY_NUMERIC)) {
                return null;
            }
            ResourceLocation id = KineticResourceIds.tryParse(key);
            Attribute attribute = id == null ? null : KineticRegistries.attributes().get(id);
            double value = data.getDouble(key);
            if (attribute == null
                    || !id.equals(KineticRegistries.attributes().id(attribute))
                    || !isValidAttributeValue(attribute, value)) {
                return null;
            }
            writes.add(new AttributeWrite(attribute, value));
        }
        return writes;
    }

    private record AttributeWrite(Attribute attribute, double value) {
    }

    private static boolean isValidAttributeValue(Attribute attribute, double value) {
        return Double.isFinite(value)
                && value == attribute.sanitizeValue(value);
    }

    private static ItemStack emptyToNull(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack;
    }

    private static ItemStack defaultStack(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Item item = KineticRegistries.items().get(id);
        ResourceLocation registeredId = item == null ? null : KineticRegistries.items().id(item);
        if (item == null || item == Items.AIR || !id.equals(registeredId)) {
            return null;
        }
        return new ItemStack(item);
    }

    public record SyncNotify(Component msg) {
        public SyncNotify() {
            this(Component.translatable("msg.entitycontrol.dummy.dummy.updated"));
        }

        public SyncNotify(NetworkBuffer buf) {
            this(buf.readComponent());
        }

        public void toBytes(NetworkBuffer buf) {
            buf.writeComponent(this.msg);
        }

    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        CHANNEL.sendToPlayer(player, msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
