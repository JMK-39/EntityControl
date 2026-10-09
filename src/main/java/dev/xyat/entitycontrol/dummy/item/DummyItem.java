package dev.xyat.entitycontrol.dummy.item;

import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyInit;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import dev.xyat.entitycontrol.util.Nbt;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

/** Portable dummy; stored equipment stays in one item until placement consumes it. */
public final class DummyItem extends Item {
    private static final String DATA = "EntityControlDummy";

    public DummyItem() { super(properties()); }

    private static Properties properties() {
        Properties properties = new Properties().stacksTo(1);
        //? if >=26.1 {
        /*properties.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
                KineticResourceIds.of("entitycontrol", "dummy")));
        *///?}
        return properties;
    }

    public static ItemStack capture(DummyEntityTest dummy) {
        ItemStack stack = new ItemStack(DummyInit.DUMMY_ITEM.get());
        CompoundTag data = Nbt.saveEntity(dummy);
        // A placed dummy must have a fresh identity and use the selected placement position.
        for (String key : List.of("UUID", "Pos", "Motion", "Rotation", "Passengers", "Leash")) data.remove(key);
        var curios = CuriosCompat.savePreset(dummy);
        if (curios != null) data.put("PortableCurios", curios);
        //? if >=1.21 {
        /*
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                stack, tag -> tag.put(DATA, data));
        *///?} else {
        stack.getOrCreateTag().put(DATA, data);
        //?}
        return stack;
    }

    private static CompoundTag storedData(ItemStack stack) {
        //? if >=1.21 {
        /*return Nbt.compound(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag(), DATA);
        *///?} else {
        return stack.hasTag() ? Nbt.compound(stack.getTag(), DATA).copy() : new CompoundTag();
        //?}
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockPos target = level.getBlockState(clicked).canBeReplaced()
                ? clicked : clicked.relative(context.getClickedFace());
        ItemStack stack = context.getItemInHand();
        if (!level.mayInteract(player, clicked) || !player.mayUseItemAt(target, context.getClickedFace(), stack))
            return InteractionResult.FAIL;
        var dummy = new DummyEntityTest(DummyInit.DUMMY.get(), level);
        CompoundTag data = storedData(stack);
        if (!data.isEmpty()) Nbt.loadEntity(dummy, data);
        dummy.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        dummy.setYRot(player.getYRot() + 180);
        dummy.setOwnerUUID(player.getUUID());
        if (!level.getWorldBorder().isWithinBounds(dummy.getBoundingBox()) || !level.noCollision(dummy))
            return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!level.addFreshEntity(dummy)) return InteractionResult.FAIL;
        if (data.contains("PortableCurios")) CuriosCompat.loadPreset(dummy, data.get("PortableCurios"));
        dummy.savePresetToOwner();
        // Creative block-use restores the original count after useOn. Replace the held
        // stack reference so a recovered item cannot duplicate its stored equipment.
        if (player.isCreative() && !data.isEmpty()) player.setItemInHand(context.getHand(), ItemStack.EMPTY);
        else if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    //? if >=26.1 {
    /*public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip,
            TooltipFlag flag) {
        tooltip.accept(KineticI18n.translatable("item.entitycontrol.dummy.place"));
        tooltip.accept(KineticI18n.translatable("item.entitycontrol.dummy.recover"));
        tooltip.accept(KineticI18n.translatable("item.entitycontrol.dummy.edit"));
    }
    *///?} else if >=1.21 {
    /*public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    *///?} else {
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
    //?}
    //? if <26.1 {
        tooltip.add(KineticI18n.translatable("item.entitycontrol.dummy.place"));
        tooltip.add(KineticI18n.translatable("item.entitycontrol.dummy.recover"));
        tooltip.add(KineticI18n.translatable("item.entitycontrol.dummy.edit"));
    }
    //?}
}
