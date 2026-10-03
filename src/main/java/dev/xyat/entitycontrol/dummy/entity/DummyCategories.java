//? if >=1.21 {
/*package dev.xyat.entitycontrol.dummy.entity;

import net.minecraft.advancements.critereon.EntityTypePredicate;
import net.minecraft.tags.EntityTypeTags;

/^** Preserves per-dummy category selection for the native enchantment entity predicates. *^/
public final class DummyCategories {
    private DummyCategories() {}
    public static Boolean matches(EntityTypePredicate predicate, DummyEntityTest dummy) {
        var key = predicate.types().unwrapKey();
        if (key.isEmpty()) return null;
        var tag = key.get();
        int category = dummy.getCustomMobTypeId();
        if (tag.equals(EntityTypeTags.SENSITIVE_TO_SMITE) || tag.equals(EntityTypeTags.UNDEAD)) return category == 1;
        if (tag.equals(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) return category == 2;
        if (tag.equals(EntityTypeTags.SENSITIVE_TO_IMPALING)) return category == 4;
        return null;
    }
}
*///?}
