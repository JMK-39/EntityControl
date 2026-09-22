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
        return level.getDataStorage().computeIfAbsent(
                PlacedBlockTracker::load,
                PlacedBlockTracker::new,
                DATA_NAME
        );
    }

    public static PlacedBlockTracker load(CompoundTag tag) {
        PlacedBlockTracker data = new PlacedBlockTracker();
        for (long value : tag.getLongArray("positions")) {
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

    @Override
    public CompoundTag save(@Nonnull CompoundTag tag) {
        long[] values = new long[positions.size()];
        int index = 0;
        for (Long value : positions) {
            values[index++] = value;
        }
        tag.putLongArray("positions", values);
        return tag;
    }
}
