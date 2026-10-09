package dev.xyat.entitycontrol.dummy;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import dev.xyat.entitycontrol.dummy.config.DummyConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.UUID;

public final class CuriosCompat {
    private static final UUID DUMMY_CURIO_UUID = UUID.fromString("d8a086f6-427c-4749-b00e-3d0d8b512345");

    private CuriosCompat() {
    }

    public static boolean isAvailable() {
        return KineticPlatform.isModLoaded("curios");
    }

    public static void initDummySlots(LivingEntity entity) {
        if (!isAvailable()) return;
        LoadedCurios.initDummySlots(entity);
    }

    public static int getSlotCount(LivingEntity entity) {
        if (!isAvailable()) return 0;
        return LoadedCurios.getSlotCount(entity);
    }

    public static ItemStack getCurioItem(LivingEntity entity, int index) {
        if (!isAvailable()) return ItemStack.EMPTY;
        return LoadedCurios.getCurioItem(entity, index);
    }

    public static boolean setCurioItem(LivingEntity entity, int index, ItemStack stack) {
        if (!isAvailable()) return false;
        return LoadedCurios.setCurioItem(entity, index, stack);
    }

//? if >=26.1 {
/*    public static net.minecraft.nbt.Tag savePreset(LivingEntity entity) {
        if (!isAvailable()) return null;
        return CuriosApi.getCuriosInventory(entity).map(ICuriosItemHandler::writeTag).orElse(null);
    }

    // Curios 15 resets to an empty default inventory, which would drop the preset just read.
    public static void loadPreset(LivingEntity entity, net.minecraft.nbt.Tag saved) {
        if (isAvailable() && saved != null) CuriosApi.getCuriosInventory(entity).ifPresent(handler -> handler.readTag(saved));
    }
*///?} else if >=1.21 {
/*    public static net.minecraft.nbt.Tag savePreset(LivingEntity entity) {
        if (!isAvailable()) return null;
        return CuriosApi.getCuriosInventory(entity).map(ICuriosItemHandler::writeTag).orElse(null);
    }

    public static void loadPreset(LivingEntity entity, net.minecraft.nbt.Tag saved) {
        if (isAvailable() && saved != null) CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            handler.readTag(saved);
            handler.reset();
        });
    }
    *///?} else {
    public static net.minecraft.nbt.Tag savePreset(LivingEntity entity) {
        if (!isAvailable()) return null;
        return CuriosApi.getCuriosInventory(entity).map(ICuriosItemHandler::writeTag).orElse(null);
    }

    public static void loadPreset(LivingEntity entity, net.minecraft.nbt.Tag saved) {
        // Curios 5 reset() clears both saved stacks and the dummy's extra slots.
        if (isAvailable() && saved != null) CuriosApi.getCuriosInventory(entity).ifPresent(handler -> handler.readTag(saved));
    }
    //?}
    private static final class LoadedCurios {
        private LoadedCurios() {
        }

        private static void initDummySlots(LivingEntity entity) {
            CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                int extraSlots = DummyConfig.dummyCurioExtraSlots.get();
                if (extraSlots <= 0) return;

                Multimap<String, AttributeModifier> map = LinkedHashMultimap.create();
                //? if >=1.21 {
/*map.put("curio", new AttributeModifier(dev.xyat.kineticcore.api.resource.KineticResourceIds.of("entitycontrol", "dummy_curio_slots"), extraSlots, AttributeModifier.Operation.ADDITION));
*///?} else {
map.put("curio", new AttributeModifier(
                        DUMMY_CURIO_UUID,
                        "Dummy Curio Slots",
                        extraSlots,
                        AttributeModifier.Operation.ADDITION
                ));
//?}
                handler.addPermanentSlotModifiers(map);
            });
        }

        private static int getSlotCount(LivingEntity entity) {
            return CuriosApi.getCuriosInventory(entity)
                    .map(ICuriosItemHandler::getSlots)
                    .orElse(0);
        }

        private static ItemStack getCurioItem(LivingEntity entity, int index) {
            return CuriosApi.getCuriosInventory(entity)
                    .filter(handler -> index >= 0 && index < handler.getSlots())
                    .map(handler -> handler.getEquippedCurios().getStackInSlot(index))
                    .orElse(ItemStack.EMPTY);
        }

        private static boolean setCurioItem(LivingEntity entity, int index, ItemStack stack) {
            return CuriosApi.getCuriosInventory(entity).map(handler -> {
                if (index < 0 || index >= handler.getSlots()) return false;
                handler.getEquippedCurios().setStackInSlot(index, stack);
                return true;
            }).orElse(false);
        }
    }
}
