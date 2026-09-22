package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class EntityModifierGuiCache {
    private static final long CACHE_EXPIRE_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long CLEANUP_CHECK_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30);

    private static final List<EntityModifierScreen.EntityGuiInfo> cachedEntities = new ArrayList<>();
    private static ClientLevel cachedLevel;
    private static boolean cacheReady;
    private static long lastAccessTime;
    private static long nextCleanupCheckTime;
    private static boolean registered;

    private EntityModifierGuiCache() {
    }

    public static synchronized void register() {
        if (registered) return;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, EntityModifierGuiCache::cleanupIfExpired);
        KineticClientEvents.onLogout(EntityModifierGuiCache::clear);
        registered = true;
    }

    public static synchronized List<EntityModifierScreen.EntityGuiInfo> getEntities() {
        ClientLevel level = KineticClientRuntime.currentLevel();
        if (level == null) {
            clear();
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        if (!cacheReady || cachedLevel != level || isExpired(now)) {
            rebuild(level, now);
        }

        lastAccessTime = now;
        return Collections.unmodifiableList(cachedEntities);
    }

    public static synchronized void clear() {
        cachedEntities.clear();
        cachedLevel = null;
        cacheReady = false;
        lastAccessTime = 0L;
        nextCleanupCheckTime = 0L;
    }

    public static synchronized void cleanupIfExpired() {
        long now = System.currentTimeMillis();
        if (now < nextCleanupCheckTime) return;
        nextCleanupCheckTime = now + CLEANUP_CHECK_INTERVAL_MS;

        ClientLevel level = KineticClientRuntime.currentLevel();
        if (level == null) {
            clear();
            return;
        }

        if (cacheReady && cachedLevel != null && cachedLevel != level) {
            clear();
            return;
        }

        if (cacheReady && isExpired(now)) {
            clear();
        }
    }

    private static boolean isExpired(long now) {
        return lastAccessTime > 0L && now - lastAccessTime >= CACHE_EXPIRE_MS;
    }

    private static void rebuild(ClientLevel level, long now) {
        cachedEntities.clear();
        cachedLevel = level;
        cacheReady = true;
        lastAccessTime = now;

        KineticRegistries.entityTypes().entries().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> addPreviewEntity(level, entry.getKey(), entry.getValue()));
    }

    private static void addPreviewEntity(ClientLevel level, ResourceLocation id, EntityType<?> type) {
        try {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity living) {
                cachedEntities.add(new EntityModifierScreen.EntityGuiInfo(
                        id.toString(),
                        type.getDescription().getString(),
                        living
                ));
            }
        } catch (Throwable ignored) {
        }
    }
}
