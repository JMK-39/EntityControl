package dev.xyat.entitycontrol.dummy;

import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;

public class DummyMenu extends AbstractContainerMenu {
    public final DummyEntityTest entity;



    public DummyMenu(int id, Inventory playerInv, DummyEntityTest entity) {
        super(DummyInit.DUMMY_MENU.get(), id);
        this.entity = entity;

        for (int i = 0; i < 6; i++) {
            final int slotIdx = i;
            this.addSlot(new Slot(entity.getInventory(), i, 21 + i * 18, 8) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    if (DummyUtils.isBlacklisted(stack)) return false;
                    if (slotIdx < 4) {
                        EquipmentSlot target = getEquipmentSlot(slotIdx);
                        return target != null && stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == target;
                    }
                    return true;
                }
                @Override
                public void setChanged() {
                    super.setChanged();
                    entity.refreshSlotAttributes();
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 21 + col * 18, 115 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 21 + col * 18, 173));
        }
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        if (slotId >= 0 && slotId < 6) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty() && DummyUtils.isBlacklisted(carried)) {
                if (!player.level().isClientSide) {
                    DummyNetwork.sendToPlayer(new DummyNetwork.SyncNotify(Component.translatable("msg.entitycontrol.dummy.dummy.blacklisted")), (ServerPlayer) player);
                }
                return;
            }
            Slot targetSlot = this.slots.get(slotId);
            if (!carried.isEmpty()) {
                if (targetSlot.mayPlace(carried)) {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    targetSlot.set(copy);
                }
            } else {
                targetSlot.set(ItemStack.EMPTY);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (index < 6) {
                slot.set(ItemStack.EMPTY);
            } else {
                if (DummyUtils.isBlacklisted(stack)) {
                    if (!player.level().isClientSide) {
                        DummyNetwork.sendToPlayer(new DummyNetwork.SyncNotify(Component.translatable("msg.entitycontrol.dummy.dummy.blacklisted")), (ServerPlayer) player);
                    }
                    return ItemStack.EMPTY;
                }
                for (int i = 0; i < 6; i++) {
                    Slot dummySlot = this.slots.get(i);
                    if (!dummySlot.hasItem() && dummySlot.mayPlace(stack)) {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        dummySlot.set(copy);
                        break;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private EquipmentSlot getEquipmentSlot(int idx) {
        return switch (idx) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return !entity.isRemoved()
                && entity.isAlive()
                && entity.level() == p.level()
                && p.distanceToSqr(entity) <= 64.0D;
    }
}
