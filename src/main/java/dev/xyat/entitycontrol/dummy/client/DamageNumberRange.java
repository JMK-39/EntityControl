package dev.xyat.entitycontrol.dummy.client;

/** Client visibility policy shared by floating and cumulative damage numbers. */
public final class DamageNumberRange {
    public static final int DEFAULT_BLOCKS=64;
    private DamageNumberRange() { }
    public static boolean contains(double distanceSquared,int blocks) {
        return distanceSquared>=0 && distanceSquared<=(double)blocks*blocks;
    }
}
