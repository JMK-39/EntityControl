package dev.xyat.entitycontrol.modifier.event;

import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

@Mod.EventBusSubscriber(modid = ModifierModule.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModifierModEventHandler {

    @SubscribeEvent
        public static void onAttributeModification(EntityAttributeModificationEvent event) {
        EntityModifierConfig.load();
        for (Map.Entry<String, EntityModifierConfig.EntityEditData> entry : EntityModifierConfig.ENTITY_DATA.entrySet()) {
            ResourceLocation entityId = ResourceLocation.tryParse(entry.getKey());
            if (entityId == null) continue;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (type == null) continue;
            for (Map.Entry<String, Double> attrEntry : entry.getValue().attributes.entrySet()) {
                ResourceLocation attributeId = ResourceLocation.tryParse(attrEntry.getKey());
                if (attributeId == null) continue;
                Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
                if (attr != null && !event.has((EntityType<? extends LivingEntity>) type, attr)) {
                    event.add((EntityType<? extends LivingEntity>) type, attr);
                }
            }
        }
    }
}
