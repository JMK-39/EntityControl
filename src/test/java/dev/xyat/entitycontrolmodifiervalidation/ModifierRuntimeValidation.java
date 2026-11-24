package dev.xyat.entitycontrolmodifiervalidation;

import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.event.ModifierEventHandler;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/** Explicit real-entity fixture. Run only in an owned/copied world; restores the exact original document. */
@Mod("entitycontrol_modifier_validation")
public final class ModifierRuntimeValidation {
    private static final Logger LOG = LoggerFactory.getLogger(ModifierRuntimeValidation.class);
    private static final String FILE = "kineticcore/entitycontrol/entity_modifiers.json";
    private static final String HEALTH = "minecraft:generic.max_health", ARMOR = "minecraft:generic.armor";
    private Map<String, EntityModifierConfig.EntityEditData> original;
    private String originalDisk;
    private String originalHash;
    private Mob loaded, fresh;
    private double healthBase, armorBase;
    private int phase, ticks;
    private boolean active;
    private boolean started;

    public ModifierRuntimeValidation() {
        if (!Boolean.getBoolean("entitycontrol.modifierValidation")) return;
        KineticServerEvents.onTick(KineticEventPriority.LOWEST, KineticServerEvents.TickPhase.END, this::tick);
    }

    private void start(MinecraftServer server) {
        try {
            original = EntityModifierConfig.ENTITY_DATA;
            originalDisk = KineticPaths.readConfigText(FILE);
            originalHash = sha256(originalDisk);
            LOG.info("ENTITY_MODIFIER_ORIGINAL sha256={}", originalHash);
            var validationLevel = server.overworld();
            var type = EntityType.PIG;
            loaded = type.create(validationLevel);
            require(loaded != null, "create existing sample");
            healthBase = value(loaded, HEALTH);
            armorBase = value(loaded, ARMOR);
            loaded.setNoAi(true);
            loaded.setInvulnerable(true);
            var player = server.getPlayerList().getPlayers().get(0);
            //? if >=26.1 {
            /*loaded.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
            *///?} else {
            loaded.moveTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
            //?}
            validationLevel.getChunkAt(loaded.blockPosition());
            require(validationLevel.addFreshEntity(loaded), "load existing sample before editing rules");
            require(validationLevel.getEntity(loaded.getUUID()) == loaded, "sample is tracked in a loaded chunk");

            var data = EntityModifierConfig.parseConfigJson(EntityModifierConfig.GSON.toJson(original));
            var global = data.computeIfAbsent(EntityModifierConfig.GLOBAL_KEY, key -> new EntityModifierConfig.EntityEditData());
            var hp = new EntityModifierConfig.AttributeRule("MULTIPLY", 2);
            hp.targetEntities = Set.of("minecraft:pig");
            var armor = new EntityModifierConfig.AttributeRule("ADD", 5);
            armor.targetEntities = Set.of("minecraft:pig");
            global.attributeRules.put(HEALTH, hp);
            global.attributeRules.put(ARMOR, armor);
            var pig = new EntityModifierConfig.EntityEditData();
            if (KineticRegistries.attributes().contains(KineticResourceIds.tryParse("irons_spellbooks:spell_resist")))
                pig.attributes.put("irons_spellbooks:spell_resist", 0.8D);
            else pig.attributes.put("minecraft:generic.follow_range", 24D);
            data.put("minecraft:pig", pig);
            require(EntityModifierConfig.save(data), "persist combined rule document");
            EntityModifierConfig.ENTITY_DATA = data;
            ModifierEventHandler.applyToLoaded(server);
            active = true;
            LOG.info("ENTITY_MODIFIER_RUNTIME_READY existing={} originalHealth={} originalArmor={}", loaded.getUUID(), healthBase, armorBase);
        } catch (Throwable error) { fail(server, error); }
    }

    private void tick(MinecraftServer server) {
        if (!started) {
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            started = true;
            start(server);
            return;
        }
        if (!active) return;
        try {
            if (++ticks > 200) throw new AssertionError("loaded-entity refresh did not finish within 200 ticks: health="
                    + value(loaded, HEALTH) + " armor=" + value(loaded, ARMOR) + " removed=" + loaded.isRemoved()
                    + " tracked=" + (server.overworld().getEntity(loaded.getUUID()) == loaded));
            if (phase == 0) {
                if (!near(value(loaded, HEALTH), healthBase * 2) || !near(value(loaded, ARMOR), armorBase + 5)) return;
                verify(loaded, armorBase + 5);
                var validationLevel = server.overworld();
                var type = EntityType.PIG;
                fresh = type.create(validationLevel);
                require(fresh != null, "create new sample after saving rules");
                fresh.setNoAi(true);
                fresh.setInvulnerable(true);
                //? if >=26.1 {
                /*fresh.snapTo(loaded.getX(), loaded.getY(), loaded.getZ() + 2, 0, 0);
                *///?} else {
                fresh.moveTo(loaded.getX(), loaded.getY(), loaded.getZ() + 2, 0, 0);
                //?}
                require(validationLevel.addFreshEntity(fresh), "join new sample through normal entity event");
                verify(fresh, armorBase + 5);
                EntityModifierConfig.ENTITY_DATA.get("minecraft:pig").attributeRules.put(ARMOR,
                        new EntityModifierConfig.AttributeRule("SET", 7));
                ModifierEventHandler.applyToLoaded(server);
                phase = 1;
            } else {
                if (!near(value(loaded, ARMOR), 7) || !near(value(fresh, ARMOR), 7)) return;
                verify(loaded, 7);
                verify(fresh, 7);
                EntityModifierConfig.load();
                require(EntityModifierConfig.ENTITY_DATA.get("minecraft:pig").attributeRules.get(HEALTH) == null,
                        "global health remains in global document rather than copied into individual data");
                restore(server);
                LOG.info("ENTITY_MODIFIER_RUNTIME_PASS loaded=true new=true globalHealth=true globalArmor=true individualPriority=true sourcesRestored=true");
                stopIfRequested();
            }
        } catch (Throwable error) { fail(server, error); }
    }

    private void verify(LivingEntity entity, double armor) {
        require(near(value(entity, HEALTH), healthBase * 2), "global max health coexists with unrelated individual rule");
        require(near(value(entity, ARMOR), armor), "global armor fallback and same-name individual priority");
        var individual = EntityModifierConfig.ENTITY_DATA.get("minecraft:pig");
        for (var entry : individual.attributes.entrySet())
            require(near(value(entity, entry.getKey()), entry.getValue()), "individual attribute is retained: " + entry.getKey());
        LOG.info("ENTITY_MODIFIER_SAMPLE entity={} health={} armor={} individual={}", entity.getUUID(),
                value(entity, HEALTH), value(entity, ARMOR), individual.attributes);
    }

    private void fail(MinecraftServer server, Throwable error) {
        LOG.error("ENTITY_MODIFIER_RUNTIME_FAIL phase=" + phase, error);
        try {
            restore(server);
            stopIfRequested();
        } catch (Throwable restoreError) { LOG.error("ENTITY_MODIFIER_RESTORE_FAIL", restoreError); }
    }

    private void restore(MinecraftServer server) throws Exception {
        active = false;
        if (originalDisk != null) KineticPaths.writeConfigTextsAtomic(Map.of(FILE, originalDisk));
        if (original != null) {
            EntityModifierConfig.ENTITY_DATA = original;
            ModifierEventHandler.applyToLoaded(server);
        }
        if (loaded != null) loaded.discard();
        if (fresh != null) fresh.discard();
        require(originalDisk == null || originalDisk.equals(KineticPaths.readConfigText(FILE)), "restore exact original disk text");
        require(originalDisk == null || originalHash.equals(sha256(KineticPaths.readConfigText(FILE))), "restore original SHA256");
        LOG.info("ENTITY_MODIFIER_RESTORED diskExact=true originalRules=true samplesRemoved=true sha256={}", originalHash);
    }

    private static void stopIfRequested() {
        if (Boolean.getBoolean("entitycontrol.modifierValidation.stopAfter"))
            KineticClientRuntime.execute(KineticClientRuntime::stopClient);
    }

    private static String sha256(String text) throws java.security.NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static double value(LivingEntity entity, String id) {
        Attribute attribute = KineticRegistries.attributes().get(KineticResourceIds.tryParse(id));
        //? if >=1.21 {
        /*var instance = entity.getAttributes().getInstance(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
        *///?} else {
        var instance = entity.getAttribute(attribute);
        //?}
        return instance == null ? attribute.getDefaultValue() : instance.getBaseValue();
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 1.0E-6D; }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
