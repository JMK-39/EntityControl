package dev.xyat.entitycontrol.breakspawn.config;

import dev.xyat.entitycontrol.breakspawn.network.BreakSpawnNetworkClient;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.kineticcore.api.text.KineticI18n;

public final class BreakSpawnConfigGui {
    public static final String PAGE_ID = "entitycontrol:break_spawn";

    private BreakSpawnConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.entitycontrol.breakspawn.break_spawn.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(KineticI18n.translatable("cfg.entitycontrol.breakspawn.break_spawn.apply_notice"))
                .pageDescription(KineticI18n.translatable("cfg.entitycontrol.breakspawn.break_spawn.description"))
                .action(
                        "open_editor",
                        KineticI18n.translatable("cfg.entitycontrol.breakspawn.break_spawn.open_editor"),
                        BreakSpawnNetworkClient::requestOpenEditor,
                        KineticI18n.translatable("cfg.entitycontrol.breakspawn.break_spawn.open_editor.tooltip")
                )
                .build());
    }

}
