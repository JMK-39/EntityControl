package dev.xyat.entitycontrol.reset.config;

import dev.xyat.entitycontrol.reset.client.gui.EntityResetRuleListScreen;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.kineticcore.api.text.KineticI18n;

public final class EntityReseConfigGui {
    public static final String PAGE_ID = "entitycontrol:entityrese";

    private EntityReseConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.entitycontrol.reset.entity")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(KineticI18n.translatable("cfg.entitycontrol.reset.entity.description"))
                .booleanValue(
                        "enable_entity_reset",
                        KineticI18n.translatable("cfg.entitycontrol.reset.entity.enable"),
                        () -> EntityReseConfig.enableEntityReset,
                        value -> EntityReseConfig.enableEntityReset = value,
                        true,
                        KineticI18n.translatable("cfg.entitycontrol.reset.entity.enable.tooltip")
                )
                .intValue(
                        "check_radius",
                        KineticI18n.translatable("cfg.entitycontrol.reset.entity.radius"),
                        () -> EntityReseConfig.checkRadius,
                        value -> EntityReseConfig.checkRadius = value,
                        64,
                        0,
                        Integer.MAX_VALUE,
                        KineticI18n.translatable("cfg.entitycontrol.reset.entity.radius.tooltip")
                )
                .action(
                        "open_rules_editor",
                        KineticI18n.translatable("cfg.entitycontrol.reset.entity.rules"),
                        KTConfigApi.pageAction(EntityResetRuleListScreen::new),
                        KineticI18n.translatable("cfg.entitycontrol.reset.entity.rules.tooltip")
                )
                .build());
    }
}
