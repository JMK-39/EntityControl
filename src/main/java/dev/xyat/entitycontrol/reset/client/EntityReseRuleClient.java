package dev.xyat.entitycontrol.reset.client;

import dev.xyat.entitycontrol.reset.client.gui.EntityResetRuleEditScreen;
import dev.xyat.entitycontrol.reset.client.gui.EntityResetRuleListScreen;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.kineticcore.api.client.gui.KineticGui;

import java.util.List;

public final class EntityReseRuleClient {
    private EntityReseRuleClient() {
    }

    public static void handleSnapshot(List<String> rules) {
        EntityReseConfig.applyRemoteRules(rules);
        EntityResetRuleListScreen listPage = KineticGui.currentPage(EntityResetRuleListScreen.class);
        if (listPage != null) listPage.onRemoteRulesUpdated();
    }

    public static void handleOperationResult(byte result, List<String> rules) {
        EntityReseConfig.applyRemoteRules(rules);
        EntityResetRuleEditScreen editPage = KineticGui.currentPage(EntityResetRuleEditScreen.class);
        if (editPage != null) {
            editPage.onServerOperationResult(result);
            return;
        }
        EntityResetRuleListScreen listPage = KineticGui.currentPage(EntityResetRuleListScreen.class);
        if (listPage != null) listPage.onServerOperationResult(result);
    }
}
