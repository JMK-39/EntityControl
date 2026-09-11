package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.entitycontrol.modifier.ModifierModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = ModifierModule.MODID, value = Dist.CLIENT)
public class EntityModifierGuiCache {
    private static final long CACHE_EXPIRE_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long CLEANUP_CHECK_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30);

    private static final List<EntityModifierScreen.EntityGuiInfo> cachedEntities = new ArrayList<>();
    private static ClientLevel cachedLevel = null;
    private static boolean cacheReady = false;
    private static long lastAccessTime = 0L;
    private static long nextCleanupCheckTime = 0L;

    public static synchronized List<EntityModifierScreen.EntityGuiInfo> getEntities() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;

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

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }

        if (cacheReady && cachedLevel != null && cachedLevel != minecraft.level) {
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

        ForgeRegistries.ENTITY_TYPES.getEntries().stream()
                .sorted(Comparator.comparing(e -> e.getKey().location().toString()))
                .forEach(e -> {
                    try {
                        Entity entity = e.getValue().create(level);
                        if (entity instanceof LivingEntity living) {
                            cachedEntities.add(new EntityModifierScreen.EntityGuiInfo(e.getKey().location().toString(), e.getValue().getDescription().getString(), living));
                        }
                    } catch (Throwable ignored) {}
                });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            cleanupIfExpired();
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
