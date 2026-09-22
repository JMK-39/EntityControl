package dev.xyat.entitycontrol.reset.client;

import dev.xyat.entitycontrol.reset.client.gui.EntityResetRuleEditScreen;
import dev.xyat.entitycontrol.reset.client.gui.EntityResetRuleListScreen;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public final class EntityReseRuleClient {
    private EntityReseRuleClient() {
    }

    public static void handleSnapshot(List<String> rules) {
        EntityReseConfig.applyRemoteRules(rules);
        Screen screen = KineticClientRuntime.currentScreen();
        if (screen instanceof EntityResetRuleListScreen listScreen) {
            listScreen.onRemoteRulesUpdated();
        }
    }

    public static void handleOperationResult(byte result, List<String> rules) {
        EntityReseConfig.applyRemoteRules(rules);
        Screen screen = KineticClientRuntime.currentScreen();
        if (screen instanceof EntityResetRuleEditScreen editScreen) {
            editScreen.onServerOperationResult(result);
        } else if (screen instanceof EntityResetRuleListScreen listScreen) {
            listScreen.onServerOperationResult(result);
        }
    }
}
