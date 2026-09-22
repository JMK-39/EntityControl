package dev.xyat.entitycontrol.breakspawn.config;

import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetwork;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BreakSpawnConfigGui {
    public static final String PAGE_ID = "entitycontrol:break_spawn";

    private BreakSpawnConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.entitycontrol.breakspawn.break_spawn.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(Component.translatable("cfg.entitycontrol.breakspawn.break_spawn.apply_notice"))
                .pageDescription(Component.translatable("cfg.entitycontrol.breakspawn.break_spawn.description"))
                .action(
                        "open_editor",
                        Component.translatable("cfg.entitycontrol.breakspawn.break_spawn.open_editor"),
                        BreakSpawnNetwork::requestOpenEditor,
                        Component.translatable("cfg.entitycontrol.breakspawn.break_spawn.open_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "entitycontrol");
    }
}
