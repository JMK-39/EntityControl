package dev.xyat.entitycontrol;

import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.reset.ResetModule;
import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.spawn.SpawnModule;
import dev.xyat.entitycontrol.breakspawn.BreakSpawnModule;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EntityControl.MODID)
public final class EntityControl {
    public static final String MODID = "entitycontrol";

    public EntityControl(FMLJavaModLoadingContext context) {
        new ModifierModule();
        new ResetModule();
        new DummyModule(context);
        new SpawnModule(context);
        new BreakSpawnModule();
    }
}
