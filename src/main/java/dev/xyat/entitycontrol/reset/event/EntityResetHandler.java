package dev.xyat.entitycontrol.reset.event;

import dev.xyat.entitycontrol.reset.ResetModule;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.kineticcore.api.entity.event.KineticLivingEvents;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityResetHandler {
    private static final String NBT_SNAPSHOT = "entitycontrol_snapshot";
    private static final String NBT_DEATH_COUNT = "entitycontrol_death_count";
    private static final String NBT_IS_TRACKING = "entitycontrol_tracking";
    private static final long COOLDOWN_MS = 1000L;

    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, List<LivingEntity>> deathSnapshot = new ConcurrentHashMap<>();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) {
            return;
        }
        KineticLivingEvents.onHurt(KineticEventPriority.NORMAL, EntityResetHandler::onLivingHurt);
        KineticLivingEvents.onDeath(KineticEventPriority.HIGHEST, false, EntityResetHandler::onPlayerDeathPre);
        KineticLivingEvents.onDeath(KineticEventPriority.LOWEST, true, EntityResetHandler::onPlayerDeathPost);
        registered = true;
    }

    private EntityResetHandler() {
    }

    private enum DeathTrigger {
        REAL_DEATH,
        PREVENTED_DEATH,
        CANCELLED_DEATH
    }

    public static void onLivingHurt(KineticLivingEvents.HurtContext event) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (event.entity().level().isClientSide) return;
        if (event.amount() <= 0) return;

        LivingEntity target = event.entity();
        ResourceLocation targetId = KineticRegistries.entityTypes().id(target.getType());
        if (targetId == null || !EntityReseConfig.ENTITY_RULES_CACHE.containsKey(targetId.toString())) return;
        if (isNotPlayerOrMinion(event.source())) return;

        CompoundTag data = target.getPersistentData();
        if (!data.contains(NBT_IS_TRACKING)) {
            saveSnapshot(target);
        }
    }

    public static void onPlayerDeathPre(KineticLivingEvents.DeathContext event) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (!(event.entity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        List<LivingEntity> trackedBosses = getNearbyTrackedBosses(player);
        if (!trackedBosses.isEmpty()) {
            deathSnapshot.put(player.getUUID(), trackedBosses);
        }
    }

    public static void onPlayerDeathPost(KineticLivingEvents.DeathContext event) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (!(event.entity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        List<LivingEntity> bosses = deathSnapshot.remove(player.getUUID());
        if (bosses == null || bosses.isEmpty()) return;

        processDeathLogic(
                player,
                bosses,
                event.cancelled() ? DeathTrigger.CANCELLED_DEATH : DeathTrigger.REAL_DEATH
        );
    }

    public static void onTotemProtectionTriggered(Player player) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (player.level().isClientSide) return;

        List<LivingEntity> trackedBosses = getNearbyTrackedBosses(player);
        if (trackedBosses.isEmpty()) return;

        processDeathLogic(player, trackedBosses, DeathTrigger.PREVENTED_DEATH);
    }

    private static List<LivingEntity> getNearbyTrackedBosses(Player player) {
        double radius = EntityReseConfig.checkRadius;
        AABB area = player.getBoundingBox().inflate(radius);
        List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(LivingEntity.class, area);
        List<LivingEntity> trackedBosses = new ArrayList<>();

        for (LivingEntity entity : nearbyEntities) {
            if (entity != player && entity.getPersistentData().contains(NBT_IS_TRACKING)) {
                trackedBosses.add(entity);
            }
        }
        return trackedBosses;
    }

    private static void processDeathLogic(Player player, List<LivingEntity> bosses, DeathTrigger trigger) {
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUUID(), 0L);
        if (now - last < COOLDOWN_MS) return;

        boolean countedAny = false;
        for (LivingEntity boss : bosses) {
            if (boss == null || !boss.isAlive() || boss.isDeadOrDying()) continue;

            ResourceLocation bossId = KineticRegistries.entityTypes().id(boss.getType());
            if (bossId == null) continue;

            EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(bossId.toString());
            if (rule == null || shouldSkipCount(rule, trigger)) continue;

            CompoundTag data = boss.getPersistentData();
            int currentCount = data.getInt(NBT_DEATH_COUNT) + 1;
            data.putInt(NBT_DEATH_COUNT, currentCount);
            countedAny = true;

            if (currentCount >= rule.threshold) {
                resetBossState(boss);
                data.putInt(NBT_DEATH_COUNT, 0);
            }
        }

        if (countedAny) {
            cooldowns.put(player.getUUID(), now);
        }
    }

    private static boolean shouldSkipCount(EntityReseConfig.EntityRule rule, DeathTrigger trigger) {
        return switch (trigger) {
            case REAL_DEATH -> !rule.countRealDeath;
            case PREVENTED_DEATH -> !rule.countPreventedDeath;
            case CANCELLED_DEATH -> !rule.countCancelledDeath;
        };
    }

    private static void saveSnapshot(LivingEntity entity) {
        CompoundTag snapshot = new CompoundTag();
        entity.saveWithoutId(snapshot);

        CompoundTag data = entity.getPersistentData();
        data.put(NBT_SNAPSHOT, snapshot);
        data.putInt(NBT_DEATH_COUNT, 0);
        data.putBoolean(NBT_IS_TRACKING, true);
    }

    private static void resetBossState(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBT_SNAPSHOT)) return;

        CompoundTag snapshot = data.getCompound(NBT_SNAPSHOT);
        ListTag currentPos = createDoubleList(entity.getX(), entity.getY(), entity.getZ());
        ListTag currentRot = createFloatList(entity.getYRot(), entity.getXRot());
        ListTag currentMotion = createDoubleList(
                entity.getDeltaMovement().x,
                entity.getDeltaMovement().y,
                entity.getDeltaMovement().z
        );

        CompoundTag applyTag = snapshot.copy();
        applyTag.put("Pos", currentPos);
        applyTag.put("Rotation", currentRot);
        applyTag.put("Motion", currentMotion);
        applyTag.putFloat("FallDistance", entity.fallDistance);
        applyTag.putUUID("UUID", entity.getUUID());

        entity.load(applyTag);
        entity.setHealth(entity.getMaxHealth());

        if (entity.level() instanceof ServerLevel serverLevel) {
            Component message = Component.translatable(
                    "msg.entitycontrol.reset.entity_reset.broadcast",
                    entity.getDisplayName()
            );
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static boolean isNotPlayerOrMinion(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player) return false;
        if (attacker instanceof OwnableEntity ownable) {
            return !(ownable.getOwner() instanceof Player);
        }
        return true;
    }

    private static ListTag createDoubleList(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    private static ListTag createFloatList(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }
}
