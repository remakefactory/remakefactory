package com.remakefactory.remakefactory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.remakefactory.remakefactory.command.conflict.ConflictCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class RemakeFactoryCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 主指令 /ref
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ref")
                .requires(source -> source.hasPermission(2));

        // 注册子命令
        root.then(ConfigCommand.register());      // 注册 /ref config ...
        root.then(ConflictCommand.register());    // 注册 /ref conflict ...

        dispatcher.register(root);
    }
}