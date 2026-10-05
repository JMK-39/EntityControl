package dev.xyat.entitycontrol.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Set;
import java.util.UUID;

/**
 * NBT access that reads the same on every Minecraft version. 26.1 getters return Optional, UUIDs go through a codec
 * and item stacks are written with the item stack codec; the stored data keeps its layout. Missing values read as
 * 0, false or an empty compound.
 */
public final class Nbt {
    private Nbt() {
    }

    //? if >=26.1 {
    /*public static Set<String> keys(CompoundTag tag) { return tag.keySet(); }
    public static CompoundTag compound(CompoundTag tag, String key) { return tag.getCompoundOrEmpty(key); }
    public static ListTag compoundList(CompoundTag tag, String key) { return tag.getListOrEmpty(key); }
    public static int intValue(CompoundTag tag, String key) { return tag.getIntOr(key, 0); }
    public static byte byteValue(CompoundTag tag, String key) { return tag.getByteOr(key, (byte) 0); }
    public static double doubleValue(CompoundTag tag, String key) { return tag.getDoubleOr(key, 0D); }
    public static boolean bool(CompoundTag tag, String key) { return tag.getBooleanOr(key, false); }
    public static boolean isNumeric(CompoundTag tag, String key) { return tag.get(key) instanceof net.minecraft.nbt.NumericTag; }
    public static boolean hasUuid(CompoundTag tag, String key) { return tag.read(key, net.minecraft.core.UUIDUtil.CODEC).isPresent(); }
    public static UUID uuid(CompoundTag tag, String key) { return tag.read(key, net.minecraft.core.UUIDUtil.CODEC).orElse(null); }
    public static void putUuid(CompoundTag tag, String key, UUID value) { tag.store(key, net.minecraft.core.UUIDUtil.CODEC, value); }
    *///?} else {
    public static Set<String> keys(CompoundTag tag) { return tag.getAllKeys(); }
    public static CompoundTag compound(CompoundTag tag, String key) { return tag.getCompound(key); }
    public static ListTag compoundList(CompoundTag tag, String key) { return tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND); }
    public static int intValue(CompoundTag tag, String key) { return tag.getInt(key); }
    public static byte byteValue(CompoundTag tag, String key) { return tag.getByte(key); }
    public static double doubleValue(CompoundTag tag, String key) { return tag.getDouble(key); }
    public static boolean bool(CompoundTag tag, String key) { return tag.getBoolean(key); }
    public static boolean isNumeric(CompoundTag tag, String key) { return tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC); }
    public static boolean hasUuid(CompoundTag tag, String key) { return tag.hasUUID(key); }
    public static UUID uuid(CompoundTag tag, String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }
    public static void putUuid(CompoundTag tag, String key, UUID value) { tag.putUUID(key, value); }
    //?}

    // Whole entities as NBT; 26.1 entities save and load through value outputs and inputs on the same tag layout.
    //? if >=26.1 {
    /*public static CompoundTag saveEntity(net.minecraft.world.entity.Entity entity) {
        net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    public static void loadEntity(net.minecraft.world.entity.Entity entity, CompoundTag tag) {
        entity.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess(), tag));
    }
    *///?} else {
    public static CompoundTag saveEntity(net.minecraft.world.entity.Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        return tag;
    }

    public static void loadEntity(net.minecraft.world.entity.Entity entity, CompoundTag tag) {
        entity.load(tag);
    }
    //?}

    //? if >=26.1 {
    /*public static CompoundTag saveItem(net.minecraft.world.item.ItemStack stack, net.minecraft.core.HolderLookup.Provider lookup) {
        return (CompoundTag) net.minecraft.world.item.ItemStack.CODEC.encodeStart(lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static net.minecraft.world.item.ItemStack loadItem(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
        return net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result().orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }
    *///?} else if >=1.21 {
    /*public static CompoundTag saveItem(net.minecraft.world.item.ItemStack stack, net.minecraft.core.HolderLookup.Provider lookup) {
        return (CompoundTag) stack.save(lookup);
    }

    public static net.minecraft.world.item.ItemStack loadItem(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
        return net.minecraft.world.item.ItemStack.parseOptional(lookup, tag);
    }
    *///?}
}
