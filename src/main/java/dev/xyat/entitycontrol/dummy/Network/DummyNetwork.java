package dev.xyat.entitycontrol.dummy.Network;

import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.DummyUtils;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class DummyNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int ID_SYNC = 0;
    private static final int ID_UPDATE_LEGACY = 1;
    private static final int ID_SYNC_NOTIFY = 2;
    private static final int ID_UPDATE_CURIO_LEGACY = 3;
    private static final int ID_UPDATE_V2 = 4;
    private static final int ID_UPDATE_CURIO_V2 = 5;

    private static final Set<Attribute> EDITABLE_ATTRIBUTES = Set.of(
            Attributes.MAX_HEALTH,
            Attributes.KNOCKBACK_RESISTANCE,
            Attributes.MOVEMENT_SPEED,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS
    );

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(DummyModule.MODID, "dummy"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(KTNetworkProtocol::acceptsAnyVersion)
            .serverAcceptedVersions(KTNetworkProtocol::acceptsAnyVersion)
            .simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(Sync.class, ID_SYNC, NetworkDirection.PLAY_TO_CLIENT).decoder(Sync::new).encoder(Sync::toBytes).consumerMainThread(Sync::handle).add();
        CHANNEL.messageBuilder(Update.class, ID_UPDATE_LEGACY, NetworkDirection.PLAY_TO_SERVER).decoder(Update::new).encoder(Update::toBytes).consumerMainThread(Update::handle).add();
        CHANNEL.messageBuilder(SyncNotify.class, ID_SYNC_NOTIFY, NetworkDirection.PLAY_TO_CLIENT).decoder(SyncNotify::new).encoder(SyncNotify::toBytes).consumerMainThread(SyncNotify::handle).add();
        CHANNEL.messageBuilder(UpdateCurio.class, ID_UPDATE_CURIO_LEGACY, NetworkDirection.PLAY_TO_SERVER).decoder(UpdateCurio::new).encoder(UpdateCurio::toBytes).consumerMainThread(UpdateCurio::handle).add();
        CHANNEL.messageBuilder(UpdateV2.class, ID_UPDATE_V2, NetworkDirection.PLAY_TO_SERVER).decoder(UpdateV2::new).encoder(UpdateV2::toBytes).consumerMainThread(UpdateV2::handle).add();
        CHANNEL.messageBuilder(UpdateCurioV2.class, ID_UPDATE_CURIO_V2, NetworkDirection.PLAY_TO_SERVER).decoder(UpdateCurioV2::new).encoder(UpdateCurioV2::toBytes).consumerMainThread(UpdateCurioV2::handle).add();
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

        public Sync(FriendlyByteBuf buf) {
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

        public void toBytes(FriendlyByteBuf buf) {
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

        public void handle(Supplier<NetworkEvent.Context> supplier) {
            supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DummyNetworkClient.handleSync(this)));
            supplier.get().setPacketHandled(true);
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

        public Update(FriendlyByteBuf buf) {
            this.entityId = buf.readInt();
            this.mobTypeId = buf.readInt();
            this.attributeData = buf.readNbt();
            this.iFrames = buf.readBoolean();
            this.healthDrop = buf.readBoolean();
            this.environmentDamage = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeInt(mobTypeId);
            buf.writeNbt(attributeData);
            buf.writeBoolean(iFrames);
            buf.writeBoolean(healthDrop);
            buf.writeBoolean(environmentDamage);
        }

        public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context context = ctxSupplier.get();
            context.enqueueWork(() -> applyUpdate(context.getSender(), -1, entityId, mobTypeId,
                    attributeData, iFrames, healthDrop, environmentDamage));
            context.setPacketHandled(true);
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

        public UpdateV2(FriendlyByteBuf buf) {
            this.containerId = buf.readVarInt();
            this.entityId = buf.readInt();
            this.mobTypeId = buf.readInt();
            this.attributeData = buf.readNbt();
            this.iFrames = buf.readBoolean();
            this.healthDrop = buf.readBoolean();
            this.environmentDamage = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeInt(entityId);
            buf.writeInt(mobTypeId);
            buf.writeNbt(attributeData);
            buf.writeBoolean(iFrames);
            buf.writeBoolean(healthDrop);
            buf.writeBoolean(environmentDamage);
        }

        public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context context = ctxSupplier.get();
            context.enqueueWork(() -> applyUpdate(context.getSender(), containerId, entityId, mobTypeId,
                    attributeData, iFrames, healthDrop, environmentDamage));
            context.setPacketHandled(true);
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

        public UpdateCurio(FriendlyByteBuf buf) {
            this.entityId = buf.readInt();
            this.slotIndex = buf.readInt();
            this.stack = buf.readItem();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeInt(slotIndex);
            buf.writeItem(stack);
        }

        public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context context = ctxSupplier.get();
            context.enqueueWork(() -> {
                // Legacy peers sent a complete ItemStack. Keep the codec, but discard
                // all client-controlled count/NBT and use only its registered item ID.
                ItemStack template = stack.isEmpty()
                        ? ItemStack.EMPTY
                        : defaultStack(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                applyCurioUpdate(context.getSender(), -1, entityId, slotIndex, template);
            });
            context.setPacketHandled(true);
        }
    }

    public static class UpdateCurioV2 {
        public enum Source {
            CLEAR,
            CARRIED,
            INVENTORY,
            DEFAULT_ITEM
        }

        private static final ResourceLocation AIR_ID = new ResourceLocation("minecraft", "air");

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

        public UpdateCurioV2(FriendlyByteBuf buf) {
            this.containerId = buf.readVarInt();
            this.entityId = buf.readInt();
            this.slotIndex = buf.readVarInt();
            this.source = buf.readEnum(Source.class);
            this.inventoryIndex = buf.readInt();
            this.itemId = buf.readResourceLocation();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeInt(entityId);
            buf.writeVarInt(slotIndex);
            buf.writeEnum(source);
            buf.writeInt(inventoryIndex);
            buf.writeResourceLocation(itemId);
        }

        public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context context = ctxSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                ItemStack template = resolveTemplate(player);
                applyCurioUpdate(player, containerId, entityId, slotIndex, template);
            });
            context.setPacketHandled(true);
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
            ResourceLocation id = ResourceLocation.tryParse(key);
            Attribute attribute = id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
            double value = data.getDouble(key);
            if (attribute == null
                    || !EDITABLE_ATTRIBUTES.contains(attribute)
                    || dummy.getAttribute(attribute) == null
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
                && attribute instanceof RangedAttribute ranged
                && value >= ranged.getMinValue()
                && value <= ranged.getMaxValue()
                && value == attribute.sanitizeValue(value);
    }

    private static ItemStack emptyToNull(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack;
    }

    private static ItemStack defaultStack(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        ResourceLocation registeredId = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        if (item == null || item == Items.AIR || !id.equals(registeredId)) {
            return null;
        }
        return new ItemStack(item);
    }

    public record SyncNotify(Component msg) {
        public SyncNotify() {
            this(Component.translatable("msg.entitycontrol.dummy.dummy.updated"));
        }

        public SyncNotify(FriendlyByteBuf buf) {
            this(buf.readComponent());
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeComponent(this.msg);
        }

        public void handle(Supplier<NetworkEvent.Context> supplier) {
            supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DummyNetworkClient.handleNotify(this)));
            supplier.get().setPacketHandled(true);
        }
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), msg);
    }
}
