package dev.xyat.entitycontrol.reset.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.entitycontrol.reset.client.gui.EntityResetRuleListScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EntityReseConfigGui {
    public static final String PAGE_ID = "entitycontrol:entityrese";

    private EntityReseConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.entitycontrol.reset.entity")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(Component.translatable("cfg.entitycontrol.reset.entity.description"))
                .booleanValue(
                        "enable_entity_reset",
                        Component.translatable("cfg.entitycontrol.reset.entity.enable"),
                        () -> EntityReseConfig.enableEntityReset,
                        value -> EntityReseConfig.enableEntityReset = value,
                        true,
                        Component.translatable("cfg.entitycontrol.reset.entity.enable.tooltip")
                )
                .intValue(
                        "check_radius",
                        Component.translatable("cfg.entitycontrol.reset.entity.radius"),
                        () -> EntityReseConfig.checkRadius,
                        value -> EntityReseConfig.checkRadius = value,
                        64,
                        0,
                        Integer.MAX_VALUE,
                        Component.translatable("cfg.entitycontrol.reset.entity.radius.tooltip")
                )
                .action(
                        "open_rules_editor",
                        Component.translatable("cfg.entitycontrol.reset.entity.rules"),
                        KTConfigApi.screenAction(EntityResetRuleListScreen::new),
                        Component.translatable("cfg.entitycontrol.reset.entity.rules.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "entitycontrol");
    }
}
