package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.config.DummyClientConfig;
import dev.xyat.entitycontrol.dummy.config.DummyConfigGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@OnlyIn(Dist.CLIENT)
public final class DummyModuleClientBootstrap {
    private DummyModuleClientBootstrap() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        DummyClientConfig.register(context);
        DummyConfigGui.load();
    }
}
