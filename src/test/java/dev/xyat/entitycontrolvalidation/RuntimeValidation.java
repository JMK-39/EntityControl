//? if >=1.21 {
/*package dev.xyat.entitycontrolvalidation;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.data.EquipmentData;
import dev.xyat.entitycontrol.breakspawn.data.PlacedBlockTracker;
import dev.xyat.entitycontrol.dummy.DummyInit;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.common.Mod;

@Mod("entitycontrol_validation")
public final class RuntimeValidation {
    private static int failures;
    public RuntimeValidation() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::started);
    }
    private void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        var level = event.getServer().overworld();
        run("component-equipment", () -> {
            String json = new com.google.gson.Gson().toJson(new BreakSpawnConfig.EquipmentSpec());
            require(json.contains("\"components\"") && !json.contains("\"nbt\""), "native equipment schema: " + json);
            var stack = EquipmentData.compile("minecraft:diamond_sword", "[damage=5,custom_data={CaseKey:1},enchantments={levels:{\"minecraft:sharpness\":2}}]");
            require(stack.getDamageValue() == 5, "equipment damage component");
            require(EquipmentData.compile("minecraft:diamond_sword", EquipmentData.format(stack)).getComponentsPatch().equals(stack.getComponentsPatch()), "component text round trip");
            try { EquipmentData.compile("minecraft:diamond_sword", "{Damage:5}"); throw new AssertionError("accepted NBT"); }
            catch (IllegalArgumentException expected) {}
            try { EquipmentData.compile("minecraft:diamond_sword", "[damage=5]trailing"); throw new AssertionError("accepted trailing text"); }
            catch (IllegalArgumentException expected) {}
            DummyEntityTest dummy = DummyInit.DUMMY.get().create(level);
            require(dummy != null, "dummy registered");
            dummy.getInventory().setItem(4, stack);
            dummy.setAttributeBaseValue(Attributes.ARMOR.value(), 12);
            var saved = new CompoundTag(); dummy.addAdditionalSaveData(saved);
            DummyEntityTest restored = DummyInit.DUMMY.get().create(level);
            restored.readAdditionalSaveData(saved);
            require(restored.getInventory().getItem(4).getComponentsPatch().equals(stack.getComponentsPatch()), "dummy inventory retains components and marker");
            require(restored.getAttributeBaseValue(Attributes.ARMOR) == 12, "dummy attribute persistence");
            require(restored.getInventory().getItem(4).getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean("KTDummyItem"), "dummy item marker");
        });
        run("curios-preset", () -> {
            var dummy = DummyInit.DUMMY.get().create(level);
            dummy.onAddedToLevel();
            require(dev.xyat.entitycontrol.dummy.CuriosCompat.getSlotCount(dummy) > 0, "dummy Curios slots registered");
            var stack = EquipmentData.compile("minecraft:diamond", "[custom_data={PresetKey:7}]");
            require(dev.xyat.entitycontrol.dummy.CuriosCompat.setCurioItem(dummy, 0, stack), "set dummy Curio");
            var saved = dev.xyat.entitycontrol.dummy.CuriosCompat.savePreset(dummy);
            var restored = DummyInit.DUMMY.get().create(level);
            dev.xyat.entitycontrol.dummy.CuriosCompat.loadPreset(restored, saved);
            restored.onAddedToLevel();
            require(dev.xyat.entitycontrol.dummy.CuriosCompat.getCurioItem(restored, 0).getComponentsPatch().equals(stack.getComponentsPatch()), "Curios preset retains components expected=" + stack + "/" + stack.getComponentsPatch() + " actual=" + dev.xyat.entitycontrol.dummy.CuriosCompat.getCurioItem(restored, 0) + "/" + dev.xyat.entitycontrol.dummy.CuriosCompat.getCurioItem(restored, 0).getComponentsPatch() + " saved=" + saved);
        });
        run("placed-blocks", () -> {
            var tracker = new PlacedBlockTracker(); tracker.mark(12345L);
            var saved = tracker.save(new CompoundTag(), level.registryAccess());
            var restored = PlacedBlockTracker.load(saved);
            require(restored.consumeIfPlaced(12345L) && !restored.consumeIfPlaced(12345L), "placed block consumed once after persistence");
        });
        run("dummy-category", () -> {
            var dummy = DummyInit.DUMMY.get().create(level);
            dummy.setCustomMobType(1);
            require(dummy.isInvertedHealAndHarm(), "undead healing behavior");
            var smite = net.minecraft.advancements.critereon.EntityPredicate.Builder.entity().of(EntityTypeTags.SENSITIVE_TO_SMITE).build();
            require(smite.matches(level, net.minecraft.world.phys.Vec3.ZERO, dummy), "undead dummy sensitive to Smite");
            dummy.setCustomMobType(2);
            var bane = net.minecraft.advancements.critereon.EntityPredicate.Builder.entity().of(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS).build();
            require(bane.matches(level, net.minecraft.world.phys.Vec3.ZERO, dummy) && !smite.matches(level, net.minecraft.world.phys.Vec3.ZERO, dummy), "arthropod category switching");
            dummy.setCustomMobType(4);
            var impaling = net.minecraft.advancements.critereon.EntityPredicate.Builder.entity().of(EntityTypeTags.SENSITIVE_TO_IMPALING).build();
            require(impaling.matches(level, net.minecraft.world.phys.Vec3.ZERO, dummy), "aquatic category impaling");
            dummy.setCustomMobType(0);
            require(!impaling.matches(level, net.minecraft.world.phys.Vec3.ZERO, dummy), "normal category clears aquatic sensitivity");
        });
        run("undead-effects", () -> {
            var setting = dev.xyat.entitycontrol.dummy.config.DummyConfig.dummyStandbyRange;
            int before = setting.get();
            try {
                setting.set(0);
                var dummy = DummyInit.DUMMY.get().create(level);
                dummy.setCustomMobType(1);
                require(!dummy.canBeAffected(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100)), "undead dummy rejects poison");
                require(!dummy.canBeAffected(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100)), "undead dummy rejects regeneration");
                dummy.setCustomMobType(0);
                require(dummy.canBeAffected(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100)), "normal dummy accepts poison");
            } finally { setting.set(before); }
        });
        org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).info("ENTITYCONTROL_VALIDATION_{} failures={}", failures == 0 ? "PASS" : "FAIL", failures);
    }
    private interface Checked { void run() throws Exception; }
    private static void run(String name, Checked test) {
        try { test.run(); org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).info("ENTITYCONTROL_CHECK_PASS {}", name); }
        catch (Throwable error) { failures++; org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class).error("ENTITYCONTROL_CHECK_FAIL " + name, error); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
*///?}
