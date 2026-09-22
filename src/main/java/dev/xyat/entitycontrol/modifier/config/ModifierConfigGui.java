package dev.xyat.entitycontrol.modifier.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ModifierConfigGui {
    public static final String PAGE_ID = "entitycontrol:modifier";

    private ModifierConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.entitycontrol.modifier.modifier.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(Component.translatable("cfg.entitycontrol.modifier.modifier.apply_notice"))
                .pageDescription(Component.translatable("cfg.entitycontrol.modifier.modifier.description"))
                .action(
                        "open_editor",
                        Component.translatable("cfg.entitycontrol.modifier.modifier.open_editor"),
                        EntityModifierNetwork::requestOpenEditor,
                        Component.translatable("cfg.entitycontrol.modifier.modifier.open_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "entitycontrol");
    }
}
