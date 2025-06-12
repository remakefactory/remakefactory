package com.remakefactory.remakefactory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.remakefactory.remakefactory.config.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Registers and handles the in-game configuration commands for the RemakeFactory mod.
 * This version uses a hardcoded command tree for maximum stability and the best possible tab-completion.
 */
public final class ConfigCommands {

    private ConfigCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 主命令节点 /remakefactoryconfig
        LiteralArgumentBuilder<CommandSourceStack> rootCommand = Commands.literal("remakefactoryconfig")
                .requires(source -> source.hasPermission(2));

        // 构建 recipe_hijacker 子命令树
        rootCommand.then(
                Commands.literal("recipe_hijacker")
                        // 1. /rfc recipe_hijacker enable [true/false]
                        .then(buildCommandForBoolean("enable", Config.COMMON.recipeHijacker.enable))
                        // 2. /rfc recipe_hijacker gtceu ...
                        .then(
                                Commands.literal("gtceu")
                                        .then(buildCommandForBoolean("enable", Config.COMMON.recipeHijacker.gtceu.enable))
                                        .then(buildCommandForBoolean("reorder", Config.COMMON.recipeHijacker.gtceu.reorder))
                                        .then(buildCommandForBoolean("multiBlock", Config.COMMON.recipeHijacker.gtceu.multiBlock))
                                        .then(buildCommandForBoolean("filter", Config.COMMON.recipeHijacker.gtceu.filter))
                                        .then(buildCommandForInt("scalingMultiplier", Config.COMMON.recipeHijacker.gtceu.scalingMultiplier, 1, 100000))
                        )
        );

        dispatcher.register(rootCommand);
        dispatcher.register(Commands.literal("rfc").redirect(rootCommand.build()));
    }

    /**
     * 为一个布尔类型的配置项构建一个完整的命令节点 (get 和 set)
     * @param name         命令中的名字 (e.g., "enable")
     * @param configValue  对应的配置对象
     * @return 一个 LiteralArgumentBuilder 节点
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandForBoolean(String name, ForgeConfigSpec.BooleanValue configValue) {
        return Commands.literal(name)
                .executes(context -> {
                    boolean currentValue = configValue.get();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.get.success", name, String.valueOf(currentValue)), true);
                    return 1;
                })
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean newValue = BoolArgumentType.getBool(context, "value");
                            configValue.set(newValue);
                            Config.COMMON_SPEC.save();
                            context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.set.success", name, String.valueOf(newValue)), true);
                            return 1;
                        })
                );
    }

    /**
     * 为一个整数类型的配置项构建一个完整的命令节点 (get 和 set)
     * @param name         命令中的名字 (e.g., "scalingMultiplier")
     * @param configValue  对应的配置对象
     * @param min          允许的最小值
     * @param max          允许的最大值
     * @return 一个 LiteralArgumentBuilder 节点
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandForInt(String name, ForgeConfigSpec.IntValue configValue, int min, int max) {
        return Commands.literal(name)
                .executes(context -> {
                    int currentValue = configValue.get();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.get.success", name, String.valueOf(currentValue)), true);
                    return 1;
                })
                .then(Commands.argument("value", IntegerArgumentType.integer(min, max))
                        .executes(context -> {
                            int newValue = IntegerArgumentType.getInteger(context, "value");
                            configValue.set(newValue);
                            Config.COMMON_SPEC.save();
                            context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.set.success", name, String.valueOf(newValue)), true);
                            return 1;
                        })
                );
    }
}