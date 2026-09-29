package dev.xyat.entitycontrol.spawn.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetworkClient;

public final class SpawnConfigGui {
    public static final String PAGE_ID = "entitycontrol:spawn";

    private SpawnConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.entitycontrol.spawn.spawn.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(KineticI18n.translatable("cfg.entitycontrol.spawn.spawn.apply_notice"))
                .pageDescription(KineticI18n.translatable("cfg.entitycontrol.spawn.spawn.description"))
                .action(
                        "open_spawn_editor",
                        KineticI18n.translatable("cfg.entitycontrol.spawn.spawn.open_spawn_editor"),
                        () -> SpawnNetworkClient.requestOpenEditor(SpawnNetwork.EDITOR_SPAWN),
                        KineticI18n.translatable("cfg.entitycontrol.spawn.spawn.open_spawn_editor.tooltip")
                )
                .action(
                        "open_spawner_editor",
                        KineticI18n.translatable("cfg.entitycontrol.spawn.spawn.open_spawner_editor"),
                        () -> SpawnNetworkClient.requestOpenEditor(SpawnNetwork.EDITOR_SPAWNER),
                        KineticI18n.translatable("cfg.entitycontrol.spawn.spawn.open_spawner_editor.tooltip")
                )
                .build());
    }

}
