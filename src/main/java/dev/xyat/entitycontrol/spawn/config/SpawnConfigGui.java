package dev.xyat.entitycontrol.spawn.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SpawnConfigGui {
    public static final String PAGE_ID = "entitycontrol:spawn";

    private SpawnConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.entitycontrol.spawn.spawn.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(Component.translatable("cfg.entitycontrol.spawn.spawn.apply_notice"))
                .pageDescription(Component.translatable("cfg.entitycontrol.spawn.spawn.description"))
                .action(
                        "open_spawn_editor",
                        Component.translatable("cfg.entitycontrol.spawn.spawn.open_spawn_editor"),
                        () -> SpawnNetwork.requestOpenEditor(SpawnNetwork.EDITOR_SPAWN),
                        Component.translatable("cfg.entitycontrol.spawn.spawn.open_spawn_editor.tooltip")
                )
                .action(
                        "open_spawner_editor",
                        Component.translatable("cfg.entitycontrol.spawn.spawn.open_spawner_editor"),
                        () -> SpawnNetwork.requestOpenEditor(SpawnNetwork.EDITOR_SPAWNER),
                        Component.translatable("cfg.entitycontrol.spawn.spawn.open_spawner_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "entitycontrol");
    }
}
