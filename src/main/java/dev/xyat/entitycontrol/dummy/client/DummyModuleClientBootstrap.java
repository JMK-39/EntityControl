package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.DummyInit;
import dev.xyat.entitycontrol.dummy.client.gui.DummyScreen;
import dev.xyat.entitycontrol.dummy.config.DummyClientConfig;
import dev.xyat.entitycontrol.dummy.config.DummyConfigGui;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.registry.KineticClientMenus;
import dev.xyat.kineticcore.api.client.registry.KineticClientRenderers;
import dev.xyat.kineticcore.api.runtime.KineticModLifecycle;

public final class DummyModuleClientBootstrap {
    private DummyModuleClientBootstrap() {
    }

    public static void register() {
        DummyClientConfig.register();
        DummyConfigGui.load();
        KineticClientRenderers.registerEntityRenderer(DummyInit.DUMMY, DummyRenderTest::new);
        KineticClientMenus.register(DummyInit.DUMMY_MENU, DummyScreen::new);
        KineticModLifecycle.onClientSetup(() -> {
            DummyTextManager.register();
        });
        KineticClientEvents.onHudRender(KineticClientEvents.HudStage.END, DeathSummaryOverlay::render);
    }
}
