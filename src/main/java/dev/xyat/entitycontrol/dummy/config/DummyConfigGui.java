package dev.xyat.entitycontrol.dummy.config;

import dev.xyat.kineticcore.api.config.client.KTClientConfigAdapter;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.entitycontrol.dummy.client.DeathSummaryOverlay;
import dev.xyat.entitycontrol.dummy.client.DummyTextManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;

public final class DummyConfigGui {
    /** Kept for add-ons that already open the original client display page directly. */
    public static final String PAGE_ID = "entitycontrol:dummy";
    public static final String SERVER_PAGE_ID = "entitycontrol:server";

    private DummyConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(buildClientPage());
        KTConfigApi.register(buildServerPage());
    }

    private static KTConfigPage buildClientPage() {
        return KTClientConfigAdapter.filteredPageBuilder(
                        PAGE_ID,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.category"),
                        DummyClientConfig.SPEC,
                        DummyConfigGui::includeAutomaticClientField
                )
                .pageDescription(Component.translatable("cfg.entitycontrol.dummy.dummy.client.description"))
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .divider()
                .tickSecondsValue("summary_duration", Component.translatable("cfg.entitycontrol.dummy.dummy.summaryDuration"),
                        DummyClientConfig.summaryDuration::get, DummyClientConfig.summaryDuration::set,
                        100, 20, 600, Component.translatable("cfg.entitycontrol.dummy.dummy.summaryDuration.tooltip"))
                .color("color_normal", Component.translatable("cfg.entitycontrol.dummy.dummy.colorNormal"),
                        DummyClientConfig.colorNormal::get, DummyClientConfig.colorNormal::set, 0xFF69B4,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorNormal.tooltip"))
                .color("color_crit", Component.translatable("cfg.entitycontrol.dummy.dummy.colorCrit"),
                        DummyClientConfig.colorCrit::get, DummyClientConfig.colorCrit::set, 0xFF5555,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorCrit.tooltip"))
                .color("color_minion", Component.translatable("cfg.entitycontrol.dummy.dummy.colorMinion"),
                        DummyClientConfig.colorMinion::get, DummyClientConfig.colorMinion::set, 0x55FF55,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorMinion.tooltip"))
                .color("color_overhead_source", Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverSource"),
                        DummyClientConfig.colorOverheadSource::get, DummyClientConfig.colorOverheadSource::set, 0xBBFFFF,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverSource.tooltip"))
                .color("color_overhead_type", Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverType"),
                        DummyClientConfig.colorOverheadType::get, DummyClientConfig.colorOverheadType::set, 0xFFFF55,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverType.tooltip"))
                .color("color_overhead_stats", Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverStats"),
                        DummyClientConfig.colorOverheadStats::get, DummyClientConfig.colorOverheadStats::set, 0x55FF55,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverStats.tooltip"))
                .color("color_overhead_dps", Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverDps"),
                        DummyClientConfig.colorOverheadDps::get, DummyClientConfig.colorOverheadDps::set, 0x00F6F6,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorOverDps.tooltip"))
                .color("color_summary_title", Component.translatable("cfg.entitycontrol.dummy.dummy.colorSumTitle"),
                        DummyClientConfig.colorSummaryTitle::get, DummyClientConfig.colorSummaryTitle::set, 0xFFAA00,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorSumTitle.tooltip"))
                .color("color_summary_stats", Component.translatable("cfg.entitycontrol.dummy.dummy.colorSumStats"),
                        DummyClientConfig.colorSummaryStats::get, DummyClientConfig.colorSummaryStats::set, 0x55FF55,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorSumStats.tooltip"))
                .color("color_summary_time", Component.translatable("cfg.entitycontrol.dummy.dummy.colorSumTime"),
                        DummyClientConfig.colorSummaryTime::get, DummyClientConfig.colorSummaryTime::set, 0x55FFFF,
                        Component.translatable("cfg.entitycontrol.dummy.dummy.colorSumTime.tooltip"))
                .onSave(DummyConfigGui::saveClientConfig)
                .build();
    }

    private static boolean includeAutomaticClientField(String path) {
        int separator = path.lastIndexOf('.');
        String leaf = separator < 0 ? path : path.substring(separator + 1);
        return !leaf.startsWith("color") && !"DeathSummaryHUD.durationTicks".equals(path);
    }

    private static void saveClientConfig() {
        DummyClientConfig.SPEC.save();
        if (!DummyClientConfig.showDamageParticles.get()) DummyTextManager.clearDamageParticles();
        if (!DummyClientConfig.showMinionDamage.get()) DummyTextManager.clearMinionDamageParticles();
        if (!DummyClientConfig.showDeathSummary.get()) DeathSummaryOverlay.clear();
        DummyTextManager.clearAllDamageNumbers();
    }

    private static KTConfigPage buildServerPage() {
        return KTConfigPage.builder(SERVER_PAGE_ID, Component.translatable("cfg.entitycontrol.dummy.dummy.server.category"))
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.MIXED)
                .applyNotice(Component.translatable("cfg.entitycontrol.dummy.dummy.apply_notice"))
                .pageDescription(Component.translatable("cfg.entitycontrol.dummy.dummy.server.description"))
                .itemRuleList("equipment_blacklist", Component.translatable("cfg.entitycontrol.dummy.dummy.blacklist"),
                        () -> new ArrayList<>(DummyConfig.equipmentBlacklist.get()),
                        values -> DummyConfig.equipmentBlacklist.set(new ArrayList<>(values)),
                        Arrays.asList("kineticcore:levitation_backpack", "somerandomitem:infinite_potion"),
                        Component.translatable("cfg.entitycontrol.dummy.dummy.blacklist.tooltip"))
                .intValue("standby_range", Component.translatable("cfg.entitycontrol.dummy.dummy.standbyRange"),
                        DummyConfig.dummyStandbyRange::get, DummyConfig.dummyStandbyRange::set,
                        16, 0, 64, Component.translatable("cfg.entitycontrol.dummy.dummy.standbyRange.tooltip"))
                .intValue("broadcast_range", Component.translatable("cfg.entitycontrol.dummy.dummy.broadcastRange"),
                        DummyConfig.dummyBroadcastRange::get, DummyConfig.dummyBroadcastRange::set,
                        32, 0, 256, Component.translatable("cfg.entitycontrol.dummy.dummy.broadcastRange.tooltip"))
                .tickSecondsValue("standby_check_interval", Component.translatable("cfg.entitycontrol.dummy.dummy.standbyCheckInterval"),
                        DummyConfig.dummyStandbyCheckIntervalTicks::get, DummyConfig.dummyStandbyCheckIntervalTicks::set,
                        20, 1, 200, Component.translatable("cfg.entitycontrol.dummy.dummy.standbyCheckInterval.tooltip"))
                .tickSecondsValue("sync_interval", Component.translatable("cfg.entitycontrol.dummy.dummy.syncInterval"),
                        DummyConfig.dummySyncIntervalTicks::get, DummyConfig.dummySyncIntervalTicks::set,
                        2, 1, 20, Component.translatable("cfg.entitycontrol.dummy.dummy.syncInterval.tooltip"))
                .intValue("curio_extra_slots", Component.translatable("cfg.entitycontrol.dummy.dummy.curioExtraSlots"),
                        DummyConfig.dummyCurioExtraSlots::get, DummyConfig.dummyCurioExtraSlots::set,
                        53, 0, 53, Component.translatable("cfg.entitycontrol.dummy.dummy.curioExtraSlots.tooltip"))
                .build();
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreen(parent, PAGE_ID);
    }
}
