package dev.xyat.entitycontrol.modifier.event;

import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.config.ModifierMath;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.player.event.KineticPlayerEvents;
import dev.xyat.kineticcore.api.registry.KineticEntityAttributes;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ModifierEventHandler {
    private static final String BACKUP_KEY = "entitycontrolModifierOriginalBases";
    private static final String LEGACY_APPLIED = "entitycontrolBlueprintApplied";
    private static final int ENTITIES_PER_TICK = 128;
    private static final Deque<LivingEntity> PENDING = new ArrayDeque<>();
    private static boolean registered;

    private ModifierEventHandler() {}

    public static synchronized void register() {
        if (registered) return;
        KineticServerEvents.onStarting(KineticEventPriority.NORMAL, server -> {
            PENDING.clear();
            EntityModifierConfig.loadAndClean(server);
        });
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END,
                server -> processPending());
        KineticWorldEvents.onEntityJoin(KineticEventPriority.NORMAL, context -> {
            if (!context.level().isClientSide() && context.entity() instanceof LivingEntity living
                    && !(living instanceof ServerPlayer)) applyModifier(living);
        });
        KineticPlayerEvents.onStartTracking(KineticEventPriority.NORMAL, (player, target) -> {
            if (player instanceof ServerPlayer serverPlayer && target instanceof LivingEntity living)
                synchronizeRuntime(serverPlayer, living);
        });
        registered = true;
    }

    /** Called only on the server thread, after the validated config has been saved. */
    public static void applyToLoaded(MinecraftServer server) {
        PENDING.clear();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && !(entity instanceof ServerPlayer)) PENDING.add(living);
            }
        }
        processPending();
    }

    private static void processPending() {
        for (int i = 0; i < ENTITIES_PER_TICK && !PENDING.isEmpty(); i++) {
            LivingEntity living = PENDING.removeFirst();
            if (!living.isRemoved() && !living.level().isClientSide()) applyModifier(living);
        }
    }

    private static EntityModifierConfig.EntityEditData findRule(LivingEntity living, ResourceLocation entityId) {
        EntityModifierConfig.EntityEditData individual = EntityModifierConfig.ENTITY_DATA.get(entityId.toString());
        // An individual rule replaces the complete global rule; never merge partial attributes.
        if (individual != null && individual.hasRules()) return individual;
        EntityModifierConfig.EntityEditData global = EntityModifierConfig.ENTITY_DATA.get(EntityModifierConfig.GLOBAL_KEY);
        return global != null && global.hasRules() ? global : null;
    }

    private static void applyModifier(LivingEntity living) {
        if (living instanceof ServerPlayer) return;
        ResourceLocation entityId = KineticRegistries.entityTypes().id(living.getType());
        if (entityId == null) return;

        float healthBefore = living.getHealth();
        double maxHealthBefore = living.getMaxHealth();
        CompoundTag persistent = living.getPersistentData();
        Set<String> removed = new LinkedHashSet<>();
        restoreOriginal(living, removed);
        // Old builds used a one-time marker without storing original bases; never retain the marker.
        persistent.remove(LEGACY_APPLIED);
        EntityModifierConfig.EntityEditData rule = findRule(living, entityId);
        Map<String, Double> dynamic = new LinkedHashMap<>();
        if (rule == null) {
            updateHealth(living, healthBefore, maxHealthBefore);
            if (!removed.isEmpty()) EntityModifierNetwork.broadcastAttributeChanges(living, dynamic, removed);
            return;
        }

        CompoundTag originals = new CompoundTag();
        Map<String, EntityModifierConfig.AttributeRule> operations = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : rule.attributes.entrySet()) {
            operations.put(entry.getKey(), new EntityModifierConfig.AttributeRule("SET", entry.getValue()));
        }
        operations.putAll(rule.attributeRules);
        for (Map.Entry<String, EntityModifierConfig.AttributeRule> entry : operations.entrySet()) {
            if (rule == EntityModifierConfig.ENTITY_DATA.get(EntityModifierConfig.GLOBAL_KEY)
                    && !entry.getValue().appliesTo(entityId.toString())) continue;
            applyAttribute(living, entry.getKey(), entry.getValue().mode, entry.getValue().value,
                    originals, dynamic);
        }
        if (!originals.isEmpty()) persistent.put(BACKUP_KEY, originals);
        updateHealth(living, healthBefore, maxHealthBefore);
        applyBuffs(living, rule);
        if (!dynamic.isEmpty() || !removed.isEmpty()) EntityModifierNetwork.broadcastAttributeChanges(living, dynamic, removed);
    }

    private static void applyAttribute(LivingEntity living, String id, String mode, double parameter,
                                       CompoundTag originals, Map<String, Double> dynamic) {
        ResourceLocation attributeId = KineticResourceIds.tryParse(id);
        if (attributeId == null) return;
        Attribute attribute = KineticRegistries.attributes().get(attributeId);
        if (attribute == null) return;
        AttributeInstance originalInstance = living.getAttributes().getInstance(attribute);
        boolean inserted = originalInstance == null;
        double base = inserted ? attribute.getDefaultValue() : originalInstance.getBaseValue();
        double result = ModifierMath.calculate(base, mode, parameter);
        if (!Double.isFinite(result)) return;
        CompoundTag backup = new CompoundTag();
        backup.putBoolean("added", inserted);
        backup.putDouble("base", base);
        originals.put(id, backup);
        AttributeInstance instance = inserted ? KineticEntityAttributes.ensureInstance(living, attribute) : originalInstance;
        instance.setBaseValue(attribute.sanitizeValue(result));
        if (inserted) dynamic.put(id, instance.getBaseValue());
        // Maximum-health changes are reconciled once after all rules have been applied.
    }

    public static void restoreOriginal(LivingEntity living, Set<String> removed) {
        CompoundTag persistent = living.getPersistentData();
        if (!persistent.contains(BACKUP_KEY, 10)) return;
        CompoundTag originals = persistent.getCompound(BACKUP_KEY);
        for (String id : originals.getAllKeys()) {
            if (restoreOne(living, id, originals.getCompound(id))) removed.add(id);
        }
        persistent.remove(BACKUP_KEY);
    }

    private static boolean restoreOne(LivingEntity living, String id, CompoundTag original) {
        ResourceLocation attributeId = KineticResourceIds.tryParse(id);
        if (attributeId == null) return false;
        Attribute attribute = KineticRegistries.attributes().get(attributeId);
        if (attribute == null) return false;
        if (original.getBoolean("added")) return KineticEntityAttributes.removeRuntimeInstance(living, attribute);
        AttributeInstance instance = living.getAttributes().getInstance(attribute);
        if (instance != null) instance.setBaseValue(original.getDouble("base"));
        // Maximum-health changes are reconciled once after all rules have been applied.
        return false;
    }

    private static void updateHealth(LivingEntity living, float originalHealth, double previousMax) {
        if (previousMax <= 0.0D || living.isDeadOrDying()) return;
        double newMax = living.getMaxHealth();
        if (Math.abs(newMax - previousMax) < 1.0E-7D) return;
        living.setHealth((float) Math.min(newMax, originalHealth * newMax / previousMax));
    }

    private static void synchronizeRuntime(ServerPlayer player, LivingEntity living) {
        CompoundTag persistent = living.getPersistentData();
        if (!persistent.contains(BACKUP_KEY, 10)) return;
        CompoundTag originals = persistent.getCompound(BACKUP_KEY);
        Map<String, Double> dynamic = new LinkedHashMap<>();
        for (String id : originals.getAllKeys()) {
            if (!originals.getCompound(id).getBoolean("added")) continue;
            ResourceLocation key = KineticResourceIds.tryParse(id);
            if (key == null) continue;
            Attribute attr = KineticRegistries.attributes().get(key);
            AttributeInstance instance = attr == null ? null : living.getAttributes().getInstance(attr);
            if (instance != null) dynamic.put(id, instance.getBaseValue());
        }
        if (!dynamic.isEmpty()) EntityModifierNetwork.sendAttributeChanges(player, living, dynamic, Set.of());
    }

    private static void applyBuffs(LivingEntity living, EntityModifierConfig.EntityEditData rule) {
        String currentDimension = living.level().dimension().location().toString();
        for (Map.Entry<String, EntityModifierConfig.PotionBuff> entry : rule.buffs.entrySet()) {
            EntityModifierConfig.PotionBuff buff = entry.getValue();
            if (buff.dimensions != null && !buff.dimensions.isEmpty()) {
                boolean matches = false;
                for (String dimension : buff.dimensions.split(",")) {
                    if (dimension.trim().equals(currentDimension)) { matches = true; break; }
                }
                if (!matches) continue;
            }
            if (buff.chance < 1.0D && living.level().random.nextDouble() > buff.chance) continue;
            ResourceLocation effectId = KineticResourceIds.tryParse(entry.getKey());
            if (effectId == null) continue;
            MobEffect effect = KineticRegistries.mobEffects().get(effectId);
            if (effect == null) continue;
            int min = Math.max(1, buff.minLevel);
            int max = Math.max(min, buff.maxLevel);
            int level = min + (max > min ? living.level().random.nextInt(max - min + 1) : 0);
            living.addEffect(new MobEffectInstance(effect, -1, level - 1, false, false, true));
        }
    }
}
