package dev.xyat.entitycontrol.modifier.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves attribute operations without changing the persisted rules. */
public final class ModifierAttributeRules {
    private ModifierAttributeRules() {}

    public static Map<String, EntityModifierConfig.AttributeRule> resolve(
            EntityModifierConfig.EntityEditData global, EntityModifierConfig.EntityEditData individual, String entityId) {
        var operations = new LinkedHashMap<String, EntityModifierConfig.AttributeRule>();
        append(operations, global);
        operations.entrySet().removeIf(entry -> !entry.getValue().appliesTo(entityId));
        // A specific entity overrides only the attributes it actually changes.
        append(operations, individual);
        return operations;
    }

    private static void append(Map<String, EntityModifierConfig.AttributeRule> operations,
                               EntityModifierConfig.EntityEditData source) {
        if (source == null) return;
        source.attributes.forEach((id, value) -> operations.put(id, new EntityModifierConfig.AttributeRule("SET", value)));
        // Modern operations retain their existing precedence over legacy fixed values.
        operations.putAll(source.attributeRules);
    }
}
