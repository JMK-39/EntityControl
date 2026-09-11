package dev.xyat.entitycontrol.dummy;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import dev.xyat.entitycontrol.dummy.config.DummyConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.UUID;

public final class CuriosCompat {
    private static final UUID DUMMY_CURIO_UUID = UUID.fromString("d8a086f6-427c-4749-b00e-3d0d8b512345");

    private CuriosCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded("curios");
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

    private static final class LoadedCurios {
        private LoadedCurios() {
        }

        private static void initDummySlots(LivingEntity entity) {
            CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                int extraSlots = DummyConfig.dummyCurioExtraSlots.get();
                if (extraSlots <= 0) return;

                Multimap<String, AttributeModifier> map = LinkedHashMultimap.create();
                map.put("curio", new AttributeModifier(
                        DUMMY_CURIO_UUID,
                        "Dummy Curio Slots",
                        extraSlots,
                        AttributeModifier.Operation.ADDITION
                ));
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
