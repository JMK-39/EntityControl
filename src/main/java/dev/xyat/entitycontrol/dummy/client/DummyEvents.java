package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.dummy.client.gui.DummyScreen;
import dev.xyat.entitycontrol.dummy.DummyInit;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DummyModule.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DummyEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DummyInit.DUMMY.get(), DummyRenderTest::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("death_summary", DeathSummaryOverlay.INSTANCE);
    }

    /**
     * 使用 FMLClientSetupEvent 注册 DummyScreen
     * 这种方式兼容性最好，能解决 RegisterMenuScreensEvent 无法解析的问题
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            DummyTextManager.register();
            // 绑定 DUMMY_MENU 和 DummyScreen
            MenuScreens.register(DummyInit.DUMMY_MENU.get(), DummyScreen::new);
        });
    }
}
