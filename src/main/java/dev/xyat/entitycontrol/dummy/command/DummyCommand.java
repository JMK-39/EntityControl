package dev.xyat.entitycontrol.dummy.command;

import dev.xyat.entitycontrol.dummy.util.ColorText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xyat.kineticcore.api.command.CommandText;
import dev.xyat.entitycontrol.dummy.DummyInit;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DummyCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> dummy = Commands.literal("dummy");

        dummy.then(Commands.literal("help").executes(ctx -> sendHelp(ctx.getSource())));
        dummy.then(Commands.literal("spawn").executes(DummyCommand::spawnDummy));
        dummy.then(Commands.literal("clear").executes(DummyCommand::clearDummies));
        dummy.executes(ctx -> sendHelp(ctx.getSource()));
        root.then(dummy);
    }

    private static int sendHelp(CommandSourceStack source) {
        MutableComponent msg = CommandText.header("msg.entitycontrol.dummy.dummy.help.unified_header").append("\n");

        msg.append(createCmd("/kt dummy spawn", "/kt dummy spawn", "cmd.entitycontrol.dummy.dummy.spawn.desc")).append("\n");
        msg.append(createCmd("/kt dummy clear", "/kt dummy clear", "cmd.entitycontrol.dummy.dummy.clear.desc"));

        source.sendSuccess(() -> msg, false);
        return 1;
    }

    private static MutableComponent createCmd(String shown, String clickValue, String descKey) {
        return ColorText.translatable("cmd.entitycontrol.dummy.dummy.help.entry", shown).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickValue))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ColorText.translatable(descKey).withStyle(ChatFormatting.GOLD)))
        );
    }

    private static int spawnDummy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            HitResult trace = player.pick(10.0D, 0.0F, false);
            Vec3 targetVec;
            if (trace.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) trace).getBlockPos();
                targetVec = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            } else {
                targetVec = player.getEyePosition().add(player.getLookAngle().scale(3));
            }
            DummyEntityTest dummy = new DummyEntityTest(DummyInit.DUMMY.get(), player.serverLevel());
            dummy.setPos(targetVec.x, targetVec.y, targetVec.z);
            dummy.setYRot(player.getYRot() + 180f);

            dummy.setOwnerUUID(player.getUUID());
            dummy.loadFromPlayerPreset(player);

            player.serverLevel().addFreshEntity(dummy);
            context.getSource().sendSuccess(() -> ColorText.translatable("cmd.entitycontrol.dummy.dummy.spawned"), true);
        } catch (Exception e) {
            context.getSource().sendFailure(ColorText.translatable("cmd.entitycontrol.dummy.dummy.error", e.getMessage()));
        }
        return 1;
    }

    private static int clearDummies(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        int count = 0;
        for (Entity e : context.getSource().getLevel().getAllEntities()) {
            if (e instanceof DummyEntityTest dummy) {
                // 仅赋予通行证才能使其被代码清理
                dummy.allowKtRemoval = true;
                dummy.discard();
                count++;
            }
        }
        int finalCount = count;
        context.getSource().sendSuccess(() -> ColorText.translatable("cmd.entitycontrol.dummy.dummy.cleared", finalCount), true);
        return 1;
    }
}
