package dev.xyat.entitycontrolvalidation;

import dev.xyat.entitycontrol.dummy.DummyInit;
import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Real server-side crafting, interaction and persistence checks in a disposable save. */
public final class DummyItemValidation {
    public static void run(MinecraftServer server) {
        var id = KineticResourceIds.of("entitycontrol", "dummy");
        var item = KineticRegistries.items().get(id);
        require(item != null && item != Items.AIR, "craftable dummy item registered");
        var player = server.getPlayerList().getPlayers().get(0);
        var level = player.serverLevel();
        var original = player.getMainHandItem().copy();
        var gameMode = player.gameMode.getGameModeForPlayer();
        var block = BlockPos.containing(player.getX() + 4, Math.max(80, player.getY()), player.getZ());
        var floor = level.getBlockState(block);
        var above = level.getBlockState(block.above());
        var above2 = level.getBlockState(block.above(2));
        DummyEntityTest restored = null;
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            //? if >=1.21 {
            /*var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 3,
                    List.of(new ItemStack(Items.HAY_BLOCK), new ItemStack(Items.ARMOR_STAND), new ItemStack(Items.HAY_BLOCK)));
            var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            *///?} else {
            var input = new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu, 1, 3);
            input.setItem(0, new ItemStack(Items.HAY_BLOCK));
            input.setItem(1, new ItemStack(Items.ARMOR_STAND));
            input.setItem(2, new ItemStack(Items.HAY_BLOCK));
            var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            //?}
            //? if >=26.1 {
            /*var crafted = recipe.value().assemble(input);
            *///?} else if >=1.21 {
            /*var crafted = recipe.value().assemble(input, level.registryAccess());
            *///?} else {
            var crafted = recipe.assemble(input, level.registryAccess());
            //?}
            require(crafted.is(item) && crafted.getCount() == 1, "vertical hay / armor stand / hay recipe");
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(block.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(block.above(2), Blocks.AIR.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, crafted);
            player.setShiftKeyDown(false); player.setPose(Pose.STANDING);
            var hit = new BlockHitResult(Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP, block, false);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
            require(crafted.useOn(context).consumesAction(), "dummy item places on block");
            var dummy = level.getEntitiesOfClass(DummyEntityTest.class,
                    new net.minecraft.world.phys.AABB(block.above()).inflate(0.4)).stream().findFirst().orElseThrow();
            require(player.getMainHandItem().isEmpty(), "survival placement consumes exactly one item");
            dummy.setCustomName(Component.literal("Portable dummy"));
            dummy.getInventory().setItem(4, new ItemStack(Items.DIAMOND, 3));
            //? if >=1.21 {
            /*dummy.setAttributeBaseValue(Attributes.ARMOR.value(), 12);
            *///?} else {
            dummy.setAttributeBaseValue(Attributes.ARMOR, 12);
            //?}
            dummy.setCustomMobType(2); dummy.setIFrames(false); dummy.setHealthDrop(false);
            boolean curios = CuriosCompat.isAvailable() && CuriosCompat.getSlotCount(dummy) > 0;
            require(curios, "installed Curios must expose dummy slots");
            if (curios) {
                require(CuriosCompat.setCurioItem(dummy, 0, new ItemStack(Items.DIAMOND)), "set accessory");
                require(CuriosCompat.getCurioItem(dummy, 0).is(Items.DIAMOND), "accessory present before recovery");
            }
            require(!dummy.skipAttackInteraction(player), "ordinary attack is not recovery");
            player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING);
            //? if >=26.1 {
            /*dummy.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            *///?} else {
            dummy.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
            //?}
            require(player.containerMenu instanceof dev.xyat.entitycontrol.dummy.DummyMenu && !dummy.isRemoved(),
                    "sneaking empty-hand right click still opens editor");
            player.closeContainer();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            require(!dummy.skipAttackInteraction(player) && !dummy.isRemoved(), "held item blocks recovery");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            long lastHit = dummy.lastHitTime;
            player.attack(dummy);
            var recovered = player.getMainHandItem().copy();
            require(dummy.isRemoved() && recovered.is(item) && recovered.getCount() == 1,
                    "sneaking empty-hand attack recovers directly to main hand");
            require(dummy.lastHitTime == lastHit, "recovery does not cause damage");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.attack(dummy);
            require(player.getMainHandItem().isEmpty(), "removed dummy cannot be recovered twice");
            player.setItemInHand(InteractionHand.MAIN_HAND, recovered);
            require(recovered.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "recovered dummy places again");
            restored = level.getEntitiesOfClass(DummyEntityTest.class,
                    new net.minecraft.world.phys.AABB(block.above()).inflate(0.4)).stream()
                    .filter(d -> !d.isRemoved()).findFirst().orElseThrow();
            require(!restored.getUUID().equals(dummy.getUUID()), "replacement gets fresh entity identity");
            require(restored.getInventory().getItem(4).is(Items.DIAMOND)
                    && restored.getInventory().getItem(4).getCount() == 3, "equipment survives recovery");
            require(restored.getAttributeBaseValue(Attributes.ARMOR) == 12 && restored.getCustomMobTypeId() == 2
                    && !restored.hasIFrames() && !restored.isHealthDropEnabled(), "editor settings survive recovery");
            require("Portable dummy".equals(restored.getCustomName().getString()), "custom name survives recovery");
            if (curios) require(CuriosCompat.getCurioItem(restored, 0).is(Items.DIAMOND), "accessory survives recovery: "
                    + CuriosCompat.savePreset(restored));
            require(player.getMainHandItem().isEmpty(), "stored dummy consumed without duplication");
            player.attack(restored);
            var creativeStack = player.getMainHandItem();
            require(creativeStack.is(item), "recover for creative placement");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            require(player.gameMode.useItemOn(player, level, creativeStack, InteractionHand.MAIN_HAND, hit).consumesAction(),
                    "creative placement through real game-mode wrapper");
            restored = level.getEntitiesOfClass(DummyEntityTest.class,
                    new net.minecraft.world.phys.AABB(block.above()).inflate(0.4)).stream()
                    .filter(d -> !d.isRemoved()).findFirst().orElseThrow();
            require(player.getMainHandItem().isEmpty(), "creative recovered placement must consume stored equipment");
            org.slf4j.LoggerFactory.getLogger(DummyItemValidation.class).info(
                    "ENTITY_DUMMY_ITEM_PASS recipe=true rightClickEditor=true leftClickRecovery=true equipment=true settings=true curios={} noDuplicate=true creativeNoDuplicate=true", curios);
        } finally {
            if (restored != null) { restored.allowKtRemoval = true; restored.discard(); }
            player.closeContainer(); player.setShiftKeyDown(false); player.setPose(Pose.STANDING);
            player.setGameMode(gameMode);
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
            level.setBlockAndUpdate(block, floor); level.setBlockAndUpdate(block.above(), above); level.setBlockAndUpdate(block.above(2), above2);
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
