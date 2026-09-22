package dev.xyat.entitycontrol.dummy.event;

import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.config.DummyConfig;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import dev.xyat.kineticcore.api.entity.event.KineticLivingEvents;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.kineticcore.api.world.event.KineticWorldEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class GlobalDamageHandler {

    private static final Map<ServerPlayer, CombatSession> sessions = new WeakHashMap<>();
    private static final Map<Integer, DummyCombatStats> dummyStats = new HashMap<>();
    private static final Map<DummySyncKey, PendingDummySync> pendingDummySyncs = new HashMap<>();
    private static final Map<DirectSyncKey, PendingDirectSync> pendingDirectSyncs = new HashMap<>();
    private static int ticksSinceSyncFlush;
    private static boolean registered;

    public static synchronized void register() {
        if (registered) {
            return;
        }
        KineticLivingEvents.onDamage(KineticEventPriority.LOWEST, GlobalDamageHandler::onLivingDamage);
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, server -> onServerTick());
        KineticServerEvents.onStopped(KineticEventPriority.NORMAL, server -> onServerStopped());
        KineticLivingEvents.onDeath(KineticEventPriority.NORMAL, false, GlobalDamageHandler::onLivingDeathSummary);
        KineticLivingEvents.onDrops(KineticEventPriority.HIGHEST, GlobalDamageHandler::onLivingDrops);
        KineticLivingEvents.onExperienceDrop(KineticEventPriority.HIGHEST, GlobalDamageHandler::onLivingExperienceDrop);
        KineticWorldEvents.onEntityJoin(KineticEventPriority.HIGHEST, GlobalDamageHandler::onEntityJoinLevel);
        KineticWorldEvents.onEntityLeave(KineticEventPriority.NORMAL, GlobalDamageHandler::onEntityLeaveLevel);
        registered = true;
    }

    private static class CombatSession {
        int targetId;
        float total;
        long start;
        long last;
        int hits;

        CombatSession(int id, long now) {
            targetId = id;
            start = now;
            last = now;
            total = 0;
            hits = 0;
        }
    }

    private record HitSample(long tick, float amount) {
    }

    private record DummySyncKey(int dummyId, int attackerId, int minionOwnerId) {
    }

    private record DirectSyncKey(int viewerId, int targetId, int attackerId, int minionOwnerId) {
    }

    private static class DummyCombatStats {
        final Deque<HitSample> oneSecondWindow = new ArrayDeque<>();
        float windowDamage;
        float totalDamage;
        int hitCount;
        long sessionStart;
        long lastHit;

        void reset(long now) {
            oneSecondWindow.clear();
            windowDamage = 0f;
            totalDamage = 0f;
            hitCount = 0;
            sessionStart = now;
        }

        void addHit(long now, float amount) {
            if (hitCount == 0 || now - lastHit > 100) {
                reset(now);
            }

            totalDamage += amount;
            hitCount++;
            lastHit = now;

            oneSecondWindow.addLast(new HitSample(now, amount));
            windowDamage += amount;

            while (!oneSecondWindow.isEmpty() && now - oneSecondWindow.peekFirst().tick >= 20) {
                windowDamage -= oneSecondWindow.removeFirst().amount;
            }
        }

        float currentDps(float fallback) {
            return windowDamage > 0f ? windowDamage : fallback;
        }

        float averageDps(long now) {
            float duration = (now - sessionStart) / 20.0f;
            return totalDamage / Math.max(0.05f, duration);
        }
    }

    private static class PendingDummySync {
        DummyEntityTest dummy;
        Entity attacker;
        ServerPlayer minionOwner;
        Component sourceName;
        Component typeName;
        float total;
        float dps;
        float avgDps;
        float currentDamage;
        int hits;
        int attackerId;
        int minionOwnerId;
        long tick;

        PendingDummySync(DummyEntityTest dummy, Entity attacker, ServerPlayer minionOwner, Component sourceName, Component typeName, int attackerId, int minionOwnerId) {
            this.dummy = dummy;
            this.attacker = attacker;
            this.minionOwner = minionOwner;
            this.sourceName = sourceName;
            this.typeName = typeName;
            this.attackerId = attackerId;
            this.minionOwnerId = minionOwnerId;
            this.tick = Long.MIN_VALUE;
        }

        void add(long now, DummyEntityTest dummy, Entity attacker, ServerPlayer minionOwner, Component sourceName, Component typeName, float total, float dps, float avgDps, int hits, float amount, int attackerId, int minionOwnerId) {
            if (this.tick != now) {
                this.tick = now;
                this.currentDamage = 0f;
            }

            this.dummy = dummy;
            this.attacker = attacker;
            this.minionOwner = minionOwner;
            this.sourceName = sourceName;
            this.typeName = typeName;
            this.total = total;
            this.dps = dps;
            this.avgDps = avgDps;
            this.hits = hits;
            this.attackerId = attackerId;
            this.minionOwnerId = minionOwnerId;
            this.currentDamage += amount;
        }
    }

    private static class PendingDirectSync {
        ServerPlayer viewer;
        Component sourceName;
        Component typeName;
        float total;
        float dps;
        float avgDps;
        float currentDamage;
        int entityId;
        int hits;
        int attackerId;
        int minionOwnerId;
        long tick;

        PendingDirectSync(ServerPlayer viewer, int entityId, Component sourceName, Component typeName, int attackerId, int minionOwnerId) {
            this.viewer = viewer;
            this.entityId = entityId;
            this.sourceName = sourceName;
            this.typeName = typeName;
            this.attackerId = attackerId;
            this.minionOwnerId = minionOwnerId;
            this.tick = Long.MIN_VALUE;
        }

        void add(long now, ServerPlayer viewer, int entityId, Component sourceName, Component typeName, float total, float dps, float avgDps, int hits, float amount, int attackerId) {
            if (this.tick != now) {
                this.tick = now;
                this.currentDamage = 0f;
            }

            this.viewer = viewer;
            this.entityId = entityId;
            this.sourceName = sourceName;
            this.typeName = typeName;
            this.total = total;
            this.dps = dps;
            this.avgDps = avgDps;
            this.hits = hits;
            this.attackerId = attackerId;
            this.minionOwnerId = -1;
            this.currentDamage += amount;
        }

        void addMinion(long now, ServerPlayer viewer, int entityId, Component sourceName, Component typeName, float amount, int attackerId, int minionOwnerId) {
            if (this.tick != now) {
                this.tick = now;
                this.currentDamage = 0f;
                this.hits = 0;
            }

            this.viewer = viewer;
            this.entityId = entityId;
            this.sourceName = sourceName;
            this.typeName = typeName;
            this.attackerId = attackerId;
            this.minionOwnerId = minionOwnerId;
            this.currentDamage += amount;
            this.total = this.currentDamage;
            this.dps = this.currentDamage;
            this.avgDps = this.currentDamage;
            this.hits++;
        }
    }

    public static void onLivingDamage(KineticLivingEvents.DamageContext event) {
        try {
            if (event.entity().level().isClientSide) return;

            LivingEntity target = event.entity();
            DamageSource source = event.source();
            float originalAmount = event.amount();
            if (originalAmount <= 0f) return;

            Entity attacker = source.getEntity();

            ServerPlayer minionOwner = null;
            if (attacker instanceof OwnableEntity ownable) {
                if (ownable.getOwner() instanceof ServerPlayer ownerPlayer) {
                    minionOwner = ownerPlayer;
                }
            }

            if (target instanceof DummyEntityTest dummy) {
                handleDummyDamage(dummy, source, originalAmount, attacker, minionOwner);
                return;
            }

            if (minionOwner != null) {
                handleMinionDamage(minionOwner, target, source, originalAmount, attacker);
                return;
            }

            if (attacker instanceof ServerPlayer player) {
                handlePlayerDamage(player, target, source, originalAmount);
            }
        } catch (Exception e) {
            DummyModule.LOGGER.error("Dummy/Damage SafeCatch: 处理伤害事件时出错。", e);
        }
    }

    public static void onServerTick() {
        try {
            int interval = Math.max(1, DummyConfig.dummySyncIntervalTicks.get());
            if (++ticksSinceSyncFlush < interval) {
                return;
            }
            // Bounded by the configured interval, so the counter cannot overflow.
            // Pending maps keep coalescing hits until this shared tick boundary.
            ticksSinceSyncFlush = 0;
            flushPendingSyncs();
        } catch (Exception e) {
            DummyModule.LOGGER.error("Dummy/Damage SafeCatch: 同步累计伤害数字时出错。", e);
        }
    }

    public static void onServerStopped() {
        sessions.clear();
        dummyStats.clear();
        pendingDummySyncs.clear();
        pendingDirectSyncs.clear();
        ticksSinceSyncFlush = 0;
    }

    private static void handleDummyDamage(DummyEntityTest dummy, DamageSource source, float amount, Entity attacker, ServerPlayer minionOwner) {
        long now = dummy.level().getGameTime();
        int dummyId = dummy.getId();

        DummyCombatStats stats = dummyStats.computeIfAbsent(dummyId, k -> new DummyCombatStats());
        stats.addHit(now, amount);
        dummy.lastHitTime = now;

        Component typeName = getDamageTypeName(source);
        Component sourceName = attacker != null ? attacker.getDisplayName() : typeName;
        int attackerId = attacker != null ? attacker.getId() : -1;
        int minionOwnerId = minionOwner != null ? minionOwner.getId() : -1;

        DummySyncKey key = new DummySyncKey(dummyId, attackerId, minionOwnerId);
        PendingDummySync pending = pendingDummySyncs.computeIfAbsent(key, k -> new PendingDummySync(dummy, attacker, minionOwner, sourceName, typeName, attackerId, minionOwnerId));

        pending.add(
                now,
                dummy,
                attacker,
                minionOwner,
                sourceName,
                typeName,
                stats.totalDamage,
                stats.currentDps(amount),
                stats.averageDps(now),
                stats.hitCount,
                amount,
                attackerId,
                minionOwnerId
        );
    }

    private static void handleMinionDamage(ServerPlayer minionOwner, LivingEntity target, DamageSource source, float amount, Entity attacker) {
        long now = target.level().getGameTime();
        int targetId = target.getId();
        int attackerId = attacker != null ? attacker.getId() : -1;
        int minionOwnerId = minionOwner.getId();

        Component typeName = getDamageTypeName(source);
        Component sourceName = minionOwner.getDisplayName();

        DirectSyncKey key = new DirectSyncKey(minionOwnerId, targetId, attackerId, minionOwnerId);
        PendingDirectSync pending = pendingDirectSyncs.computeIfAbsent(key, k -> new PendingDirectSync(minionOwner, targetId, sourceName, typeName, attackerId, minionOwnerId));
        pending.addMinion(now, minionOwner, targetId, sourceName, typeName, amount, attackerId, minionOwnerId);
    }

    private static void handlePlayerDamage(ServerPlayer player, LivingEntity target, DamageSource source, float amount) {
        long now = player.level().getGameTime();
        CombatSession s = sessions.computeIfAbsent(player, k -> new CombatSession(target.getId(), now));

        if (s.targetId != target.getId() || now - s.last > 100) {
            s = new CombatSession(target.getId(), now);
            sessions.put(player, s);
        }

        s.total += amount;
        s.last = now;
        s.hits++;

        float duration = (now - s.start) / 20.0f;
        float avgDps = s.total / Math.max(0.05f, duration);

        int targetId = target.getId();
        int playerId = player.getId();
        Component typeName = getDamageTypeName(source);
        Component sourceName = player.getDisplayName();

        DirectSyncKey key = new DirectSyncKey(playerId, targetId, playerId, -1);
        PendingDirectSync pending = pendingDirectSyncs.computeIfAbsent(key, k -> new PendingDirectSync(player, targetId, sourceName, typeName, playerId, -1));

        pending.add(
                now,
                player,
                targetId,
                sourceName,
                typeName,
                s.total,
                avgDps,
                avgDps,
                s.hits,
                amount,
                playerId
        );
    }

    private static void flushPendingSyncs() {
        if (!pendingDummySyncs.isEmpty()) {
            List<PendingDummySync> list = new ArrayList<>(pendingDummySyncs.values());
            pendingDummySyncs.clear();

            for (PendingDummySync pending : list) {
                if (pending.currentDamage <= 0f) continue;
                if (pending.dummy == null || pending.dummy.isRemoved()) continue;

                DummyNetwork.Sync packet = DummyNetwork.Sync.realtime(
                        pending.dummy.getId(),
                        pending.sourceName,
                        pending.typeName,
                        pending.total,
                        pending.dps,
                        pending.avgDps,
                        pending.hits,
                        pending.currentDamage,
                        true,
                        pending.attackerId,
                        pending.minionOwnerId
                );

                sendDummyRealtime(pending.dummy, packet, pending.attacker, pending.minionOwner);
            }
        }

        if (!pendingDirectSyncs.isEmpty()) {
            List<PendingDirectSync> list = new ArrayList<>(pendingDirectSyncs.values());
            pendingDirectSyncs.clear();

            for (PendingDirectSync pending : list) {
                if (pending.currentDamage <= 0f) continue;
                if (pending.viewer == null || pending.viewer.isRemoved()) continue;

                DummyNetwork.sendToPlayer(DummyNetwork.Sync.realtime(
                        pending.entityId,
                        pending.sourceName,
                        pending.typeName,
                        pending.total,
                        pending.dps,
                        pending.avgDps,
                        pending.hits,
                        pending.currentDamage,
                        false,
                        pending.attackerId,
                        pending.minionOwnerId
                ), pending.viewer);
            }
        }
    }

    public static void onLivingDeathSummary(KineticLivingEvents.DeathContext event) {
        try {
            if (event.entity().level().isClientSide || event.cancelled()) return;

            LivingEntity target = event.entity();
            long now = target.level().getGameTime();

            for (Map.Entry<ServerPlayer, CombatSession> entry : sessions.entrySet()) {
                if (entry.getValue().targetId == target.getId()) {
                    CombatSession s = entry.getValue();
                    if (now - s.last <= 100) {
                        float duration = (float) Math.max(1, s.last - s.start) / 20.0f;
                        float dps = s.total / Math.max(0.05f, duration);

                        DummyNetwork.sendToPlayer(DummyNetwork.Sync.summary(
                                target.getDisplayName(), s.total, dps, duration, s.hits
                        ), entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            DummyModule.LOGGER.error("Dummy/Damage SafeCatch: 处理实体死亡结算时出错。", e);
        }
    }

    public static void onLivingDrops(KineticLivingEvents.DropsContext event) {
        if (event.entity() instanceof DummyEntityTest) {
            event.cancel();
            event.drops().clear();
        }
    }

    public static void onLivingExperienceDrop(KineticLivingEvents.ExperienceDropContext event) {
        if (event.entity() instanceof DummyEntityTest) {
            event.cancel();
            event.droppedExperience(0);
        }
    }

    public static void onEntityJoinLevel(KineticWorldEvents.EntityJoinContext event) {
        if (event.level().isClientSide()) return;

        if (event.entity() instanceof ItemEntity itemEntity) {
            ItemStack droppedStack = itemEntity.getItem();
            if (!droppedStack.isEmpty() && droppedStack.hasTag() && droppedStack.getTag() != null && droppedStack.getTag().getBoolean("KTDummyItem")) {
                event.cancel();
                itemEntity.discard();
            }
        }
    }

    public static void onEntityLeaveLevel(Entity entity, net.minecraft.world.level.LevelAccessor level) {
        if (!level.isClientSide() && entity instanceof DummyEntityTest) {
            int entityId = entity.getId();
            dummyStats.remove(entityId);
            pendingDummySyncs.entrySet().removeIf(entry -> entry.getKey().dummyId() == entityId);
        }
    }

    private static void sendDummyRealtime(DummyEntityTest dummy, DummyNetwork.Sync packet, Entity attacker, ServerPlayer minionOwner) {
        int range = DummyConfig.dummyBroadcastRange.get();

        if (range <= 0) {
            if (attacker instanceof ServerPlayer player) {
                DummyNetwork.sendToPlayer(packet, player);
                return;
            }
            if (minionOwner != null) {
                DummyNetwork.sendToPlayer(packet, minionOwner);
            }
            return;
        }

        if (!(dummy.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        double rangeSqr = (double) range * (double) range;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(dummy) <= rangeSqr) {
                DummyNetwork.sendToPlayer(packet, player);
            }
        }
    }

    public static Component getDamageTypeName(DamageSource source) {
        String msgId = source.getMsgId();
        return Component.translatableWithFallback("dmg." + msgId, msgId);
    }
}
