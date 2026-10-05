package dev.xyat.entitycontrol.breakspawn.data;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class PlacedBlockTracker extends SavedData {
    private static final String DATA_NAME = "entitycontrol_placed_blocks";
    private final Set<Long> positions = new HashSet<>();

    public static PlacedBlockTracker get(ServerLevel level) {
//? if >=26.1 {
/*        return level.getDataStorage().computeIfAbsent(TYPE);
*///?} else if >=1.21 {
/*        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlacedBlockTracker::new, (tag, registries) -> load(tag), null), DATA_NAME);
*///?} else {
        return level.getDataStorage().computeIfAbsent(
                PlacedBlockTracker::load,
                PlacedBlockTracker::new,
                DATA_NAME
        );
//?}
    }

    // 26.1 saved data is typed by id and codec; the stored compound keeps its layout.
    //? if >=26.1 {
    /*private static final net.minecraft.world.level.saveddata.SavedDataType<PlacedBlockTracker> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
            dev.xyat.kineticcore.api.resource.KineticResourceIds.of("entitycontrol", DATA_NAME), PlacedBlockTracker::new,
            CompoundTag.CODEC.xmap(PlacedBlockTracker::load, data -> data.save(new CompoundTag(), null)));
    *///?}

    public static PlacedBlockTracker load(CompoundTag tag) {
        PlacedBlockTracker data = new PlacedBlockTracker();
        //? if >=26.1 {
        /*long[] values = tag.getLongArray("positions").orElse(new long[0]);
        *///?} else {
        long[] values = tag.getLongArray("positions");
        //?}
        for (long value : values) {
            data.positions.add(value);
        }
        return data;
    }

    public void mark(long position) {
        if (positions.add(position)) {
            setDirty();
        }
    }

    public boolean consumeIfPlaced(long position) {
        if (positions.remove(position)) {
            setDirty();
            return true;
        }
        return false;
    }

//? if >=26.1 {
/*    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
*///?} else if >=1.21 {
/*    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
*///?} else {
    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) {
//?}
        long[] values = new long[positions.size()];
        int index = 0;
        for (Long value : positions) {
            values[index++] = value;
        }
        tag.putLongArray("positions", values);
        return tag;
    }
}
