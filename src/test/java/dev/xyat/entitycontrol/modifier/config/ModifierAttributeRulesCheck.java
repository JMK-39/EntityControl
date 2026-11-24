package dev.xyat.entitycontrol.modifier.config;

import java.util.Set;

/** Rule precedence regression; no client or server startup is required. */
public final class ModifierAttributeRulesCheck {
    public static void main(String[] args) {
        var global = new EntityModifierConfig.EntityEditData();
        global.attributeRules.put("minecraft:generic.max_health", new EntityModifierConfig.AttributeRule("MULTIPLY", 2));
        global.attributes.put("minecraft:generic.armor", 3D);
        global.attributeRules.put("minecraft:generic.armor", new EntityModifierConfig.AttributeRule("ADD", 5));
        global.attributeRules.get("minecraft:generic.armor").targetEntities = Set.of("minecraft:pig");
        var individual = new EntityModifierConfig.EntityEditData();
        individual.attributes.put("irons_spellbooks:spell_resist", 0.8D);

        var resolved = ModifierAttributeRules.resolve(global, individual, "minecraft:pig");
        require(resolved.containsKey("minecraft:generic.max_health"), "unrelated individual rule must retain global max health");
        require(resolved.get("minecraft:generic.armor").value == 5, "modern global operation overrides legacy value for the same attribute");
        require(resolved.get("irons_spellbooks:spell_resist").value == 0.8D, "existing individual value is preserved");
        individual.attributes.put("minecraft:generic.armor", 7D);
        individual.attributeRules.put("minecraft:generic.armor", new EntityModifierConfig.AttributeRule("SET", 9));
        resolved = ModifierAttributeRules.resolve(global, individual, "minecraft:pig");
        require(resolved.get("minecraft:generic.armor").value == 9, "modern individual operation wins over global and legacy individual values");
        require(!ModifierAttributeRules.resolve(global, null, "minecraft:cow").containsKey("minecraft:generic.armor"), "global targets exclude non-target entities");
        global.attributeRules.get("minecraft:generic.armor").targetEntities = Set.of();
        require(!ModifierAttributeRules.resolve(global, null, "minecraft:pig").containsKey("minecraft:generic.armor"), "empty global targets match none");
        require(individual.attributes.get("irons_spellbooks:spell_resist") == 0.8D && global.attributes.get("minecraft:generic.armor") == 3D,
                "resolving rules does not mutate either source document");
        System.out.println("ENTITY_MODIFIER_RULES_PASS fallback=true individualPriority=true modernPriority=true targets=true sourcesPreserved=true");
    }

    private static void require(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
