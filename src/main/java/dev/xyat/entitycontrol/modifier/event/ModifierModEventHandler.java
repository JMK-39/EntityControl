package dev.xyat.entitycontrol.modifier.event;

import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.kineticcore.api.registry.KineticEntityAttributes;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.Map;

public final class ModifierModEventHandler {
    private static boolean registered;

    private ModifierModEventHandler() {
    }

    public static synchronized void register() {
        if (registered) return;
        KineticEntityAttributes.onModify(ModifierModEventHandler::applyAttributeModifications);
        registered = true;
    }

    @SuppressWarnings("unchecked")
    private static void applyAttributeModifications(KineticEntityAttributes.ModificationContext context) {
        EntityModifierConfig.load();
        for (Map.Entry<String, EntityModifierConfig.EntityEditData> entry : EntityModifierConfig.ENTITY_DATA.entrySet()) {
            ResourceLocation entityId = KineticResourceIds.tryParse(entry.getKey());
            if (entityId == null) continue;
            EntityType<?> rawType = KineticRegistries.entityTypes().get(entityId);
            if (rawType == null) continue;

            EntityType<? extends LivingEntity> type;
            try {
                type = (EntityType<? extends LivingEntity>) rawType;
            } catch (ClassCastException ignored) {
                continue;
            }

            for (Map.Entry<String, Double> attrEntry : entry.getValue().attributes.entrySet()) {
                ResourceLocation attributeId = KineticResourceIds.tryParse(attrEntry.getKey());
                if (attributeId == null) continue;
                Attribute attribute = KineticRegistries.attributes().get(attributeId);
                if (attribute != null && !context.has(type, attribute)) {
                    context.add(type, attribute);
                }
            }
        }
    }
}
