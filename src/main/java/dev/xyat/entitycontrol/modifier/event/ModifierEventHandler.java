package dev.xyat.entitycontrol.modifier.event;

import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

@Mod.EventBusSubscriber(modid = ModifierModule.MODID)
public class ModifierEventHandler {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        EntityModifierConfig.loadAndClean(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) {
            CompoundTag persistentData = living.getPersistentData();
            if (persistentData.getBoolean("entitycontrolBlueprintApplied")) return;
            persistentData.putBoolean("entitycontrolBlueprintApplied", true);

            ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
            if (rl != null) {
                String id = rl.toString();
                EntityModifierConfig.EntityEditData data = EntityModifierConfig.ENTITY_DATA.get(id);
                if (data != null) {
                    // 1. 处理属性修改
                    if (!data.attributes.isEmpty()) {
                        for (Map.Entry<String, Double> attrEntry : data.attributes.entrySet()) {
                            ResourceLocation attributeId = ResourceLocation.tryParse(attrEntry.getKey());
                            if (attributeId == null) continue;
                            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
                            if (attr != null) {
                                AttributeInstance inst = living.getAttributes().getInstance(attr);
                                if (inst != null) {
                                    inst.setBaseValue(attrEntry.getValue());
                                    if (attr == Attributes.MAX_HEALTH) living.setHealth((float) (double) attrEntry.getValue());
                                }
                            }
                        }
                    }

                    // 2. 处理基础 Buff 添加
                    if (!data.buffs.isEmpty()) {
                        String currentDim = living.level().dimension().location().toString();

                        for (Map.Entry<String, EntityModifierConfig.PotionBuff> entry : data.buffs.entrySet()) {
                            EntityModifierConfig.PotionBuff buff = entry.getValue();

                            // 维度检查
                            if (buff.dimensions != null && !buff.dimensions.isEmpty()) {
                                boolean match = false;
                                for (String dim : buff.dimensions.split(",")) {
                                    if (dim.trim().equals(currentDim)) { match = true; break; }
                                }
                                if (!match) continue;
                            }

                            // 几率检查
                            if (buff.chance < 1.0 && living.level().random.nextDouble() > buff.chance) continue;

                            ResourceLocation effectId = ResourceLocation.tryParse(entry.getKey());
                            if (effectId == null) continue;
                            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                            if (effect != null) {
                                int min = Math.max(1, buff.minLevel);
                                int max = Math.max(min, buff.maxLevel);
                                int level = min + (max > min ? living.level().random.nextInt(max - min + 1) : 0);

                                // 添加 Buff
                                MobEffectInstance inst = new MobEffectInstance(effect, -1, level - 1, false, false, true);
                                living.addEffect(inst);
                            }
                        }
                    }
                }
            }
        }
    }
}
