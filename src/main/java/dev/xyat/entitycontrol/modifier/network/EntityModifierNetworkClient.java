package dev.xyat.entitycontrol.modifier.network;

import dev.xyat.entitycontrol.modifier.ModifierModule;
import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import dev.xyat.entitycontrol.modifier.config.ModifierConfigGui;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import dev.xyat.kineticcore.api.registry.KineticEntityAttributes;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;

public final class EntityModifierNetworkClient {
    private static final Deque<Pending> PENDING = new ArrayDeque<>();
    private static boolean registered;
    private record Pending(ClientLevel level, EntityModifierNetwork.DynamicAttributesPacket packet, int ttl) {}

    public static synchronized void register() {
        if (registered) return;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, EntityModifierNetworkClient::flushPending);
        KineticClientEvents.onLogout(PENDING::clear);
        registered = true;
    }

    private static void flushPending() {
        ClientLevel current = KineticClientRuntime.currentLevel();
        int count = PENDING.size();
        for (int i = 0; i < count; i++) {
            Pending pending = PENDING.removeFirst();
            if (pending.level() != current || pending.ttl() <= 0) continue;
            if (current == null || !(current.getEntity(pending.packet().entityId()) instanceof LivingEntity living)) {
                PENDING.addLast(new Pending(current, pending.packet(), pending.ttl() - 1));
            } else applyDynamicAttributes(living, pending.packet());
        }
    }

    private EntityModifierNetworkClient() {
    }

    public static void handleSaveResult(boolean success) {
        if (KineticClientRuntime.currentScreen() instanceof EntityModifierScreen screen) {
            screen.handleSaveResult(success);
        }
        if (success) {
            KTConfigApi.notifySaved(ModifierConfigGui.PAGE_ID);
        } else {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.modifier.modifier.save_failed"));
        }
    }

    public static void handleOpenScreen(EntityModifierNetwork.OpenModifierScreenPacket packet) {
        try {
            KineticClientRuntime.openScreen(new EntityModifierScreen(KineticClientRuntime.currentScreen(), packet.jsonConfig()));
        } catch (RuntimeException exception) {
            ModifierModule.LOGGER.error("Rejected invalid server entity-modifier snapshot", exception);
        }
    }
    public static void handleDynamicAttributes(EntityModifierNetwork.DynamicAttributesPacket packet) {
        var level = KineticClientRuntime.currentLevel();
        if (level == null) return;
        if (!(level.getEntity(packet.entityId()) instanceof LivingEntity living)) {
            if (PENDING.size() >= 2048) PENDING.removeFirst();
            PENDING.addLast(new Pending(level, packet, 100));
            return;
        }
        applyDynamicAttributes(living, packet);
    }

    private static void applyDynamicAttributes(LivingEntity living, EntityModifierNetwork.DynamicAttributesPacket packet) {
        for (String id : packet.removed()) {
            ResourceLocation key = KineticResourceIds.tryParse(id);
            Attribute attr = key == null ? null : KineticRegistries.attributes().get(key);
            if (attr != null) KineticEntityAttributes.removeRuntimeInstance(living, attr);
        }
        packet.added().forEach((id, value) -> {
            ResourceLocation key = KineticResourceIds.tryParse(id);
            Attribute attr = key == null ? null : KineticRegistries.attributes().get(key);
            if (attr != null && Double.isFinite(value))
                KineticEntityAttributes.ensureInstance(living, attr).setBaseValue(attr.sanitizeValue(value));
        });
    }

}
