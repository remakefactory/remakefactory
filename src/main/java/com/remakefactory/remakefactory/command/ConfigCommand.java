package com.remakefactory.remakefactory.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.remakefactory.remakefactory.config.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ConfigCommand {

    private ConfigCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("config")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.usage"), false);
                    return 1;
                });

        LiteralArgumentBuilder<CommandSourceStack> hijackerCmd = Commands.literal("recipe_hijacker")
                .then(buildCommandForBoolean("enable", Config.COMMON.recipeHijacker.enable));

        LiteralArgumentBuilder<CommandSourceStack> gtceuCmd = Commands.literal("gtceu")
                .then(buildCommandForBoolean("enable", Config.COMMON.recipeHijacker.gtceu.enable))
                .then(buildCommandForBoolean("reorder", Config.COMMON.recipeHijacker.gtceu.reorder))
                .then(buildCommandForBoolean("multiBlock", Config.COMMON.recipeHijacker.gtceu.multiBlock))
                .then(buildCommandForBoolean("filter", Config.COMMON.recipeHijacker.gtceu.filter))
                .then(buildCommandForInt("scalingMultiplier", Config.COMMON.recipeHijacker.gtceu.scalingMultiplier, 1, 100000));

        hijackerCmd.then(gtceuCmd);
        cmd.then(hijackerCmd);
        return cmd;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandForBoolean(String name, ForgeConfigSpec.BooleanValue configValue) {
        return Commands.literal(name)
                .executes(context -> {
                    boolean currentValue = configValue.get();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.get.success", name, currentValue), true);
                    return 1;
                })
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean newValue = BoolArgumentType.getBool(context, "value");
                            configValue.set(newValue);
                            Config.COMMON_SPEC.save();
                            context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.set.success", name, newValue), true);
                            return 1;
                        })
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandForInt(String name, ForgeConfigSpec.IntValue configValue, int min, int max) {
        return Commands.literal(name)
                .executes(context -> {
                    int currentValue = configValue.get();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.get.success", name, currentValue), true);
                    return 1;
                })
                .then(Commands.argument("value", IntegerArgumentType.integer(min, max))
                        .executes(context -> {
                            int newValue = IntegerArgumentType.getInteger(context, "value");
                            configValue.set(newValue);
                            Config.COMMON_SPEC.save();
                            context.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.config.set.success", name, newValue), true);
                            return 1;
                        })
                );
    }
}