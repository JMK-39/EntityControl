package dev.xyat.entitycontrol.dummy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xyat.kineticcore.api.command.CommandText;
import dev.xyat.kineticcore.api.command.KineticCommands;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.entitycontrol.dummy.DummyModule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class DummyCommandExtension implements CommandExtension {
    private DummyCommandExtension() {
    }

    public static void install() {
        KineticCommands.registerExtension(DummyModule.MODID, new DummyCommandExtension());
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        DummyCommand.register(root);
    }

    @Override
    public void appendHelpItems(CommandSourceStack source, List<MutableComponent> items) {
        items.add(CommandText.executable(
                "/kt dummy",
                "cmd.entitycontrol.dummy.dummy.desc"
        ));
    }

    @Override
    public void reload(CommandSourceStack source) {
    }
}
