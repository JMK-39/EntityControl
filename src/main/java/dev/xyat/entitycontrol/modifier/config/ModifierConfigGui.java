package dev.xyat.entitycontrol.modifier.config;

import dev.xyat.entitycontrol.modifier.network.EntityModifierNetworkClient;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.kineticcore.api.text.KineticI18n;

public final class ModifierConfigGui {
    public static final String PAGE_ID = "entitycontrol:modifier";

    private ModifierConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.entitycontrol.modifier.modifier.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(KineticI18n.translatable("cfg.entitycontrol.modifier.modifier.apply_notice"))
                .pageDescription(KineticI18n.translatable("cfg.entitycontrol.modifier.modifier.description"))
                .action(
                        "open_editor",
                        KineticI18n.translatable("cfg.entitycontrol.modifier.modifier.open_editor"),
                        EntityModifierNetworkClient::requestOpenEditor,
                        KineticI18n.translatable("cfg.entitycontrol.modifier.modifier.open_editor.tooltip")
                )
                .build());
    }
}
