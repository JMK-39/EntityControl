package dev.xyat.entitycontrol.dummy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xyat.kineticcore.command.CommandUtils;
import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.entitycontrol.dummy.DummyModule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class DummyCommandExtension implements KTCommandExtension {
    private DummyCommandExtension() {
    }

    public static void install() {
        KTCommandApi.register(DummyModule.MODID, new DummyCommandExtension());
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        DummyCommand.register(root);
    }

    @Override
    public void appendHelpItems(CommandSourceStack source, List<MutableComponent> items) {
        items.add(CommandUtils.createExecutableCommand(
                "/kt dummy",
                "cmd.entitycontrol.dummy.dummy.desc"
        ));
    }

    @Override
    public void reload(CommandSourceStack source) {
    }
}
