package dev.xyat.entitycontrol.dummy.client;

/** Boundary regression, runnable without starting or emulating a Minecraft client. */
public final class DamageNumberRangeCheck {
    public static void main(String[] args)throws Exception {
        var type=Class.forName("dev.xyat.entitycontrol.dummy.client.DamageNumberRange");
        int defaultDistance=type.getField("DEFAULT_BLOCKS").getInt(null);
        var contains=type.getMethod("contains",double.class,int.class);
        require(defaultDistance==64,"Default must be 64 blocks");
        require((boolean)contains.invoke(null,64.0*64,defaultDistance),"64-block boundary must remain visible");
        require(!(boolean)contains.invoke(null,64.01*64.01,defaultDistance),"Beyond configured range must be hidden");
        require(!(boolean)contains.invoke(null,32.0*32,16),"Lowering distance must immediately hide distant damage");
        require((boolean)contains.invoke(null,96.0*96,128),"Increasing distance must immediately allow distant damage");
        require(!(boolean)contains.invoke(null,Double.NaN,64),"Invalid positions must not render");
        System.out.println("DAMAGE_RANGE_PASS default=64 boundary=true runtimeDecrease=true runtimeIncrease=true");
    }
    private static void require(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
