package com.remakefactory.remakefactory.command.conflict;

import com.google.common.base.Stopwatch;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.remakefactory.remakefactory.util.recipe.conflict.ConflictDetector;
import com.remakefactory.remakefactory.util.recipe.conflict.GTCEuConflictRecipe;
import com.remakefactory.remakefactory.util.recipe.conflict.IConflictRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("removal")
public final class ConflictCommand {

    private static final Logger LOGGER = LogManager.getLogger("ConflictOptimizer");

    // --- 本地化的异常类型 ---
    private static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.not_player"));
    private static final SimpleCommandExceptionType ERROR_RECIPE_NOT_FOUND = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.recipe_not_found"));
    private static final SimpleCommandExceptionType ERROR_NOT_GT_RECIPE = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.not_gt_recipe"));
    private static final SimpleCommandExceptionType ERROR_NO_RECIPES_GIVEN = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.no_recipes_given"));
    private static final SimpleCommandExceptionType ERROR_NO_VALID_RECIPES = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.no_valid_recipes"));
    private static final SimpleCommandExceptionType ERROR_CANNOT_CREATE_DIR = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.cannot_create_dir"));
    private static final SimpleCommandExceptionType ERROR_CANNOT_WRITE_FILE = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.cannot_write_file"));
    private static final SimpleCommandExceptionType ERROR_CANNOT_READ_FILE = new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.cannot_read_file"));

    private static final String DEFAULT_NAMESPACE = "gtceu"; // 定义默认命名空间
    private static final boolean DEFAULT_USE_MULTITHREADING = false; // 定义默认线程使用

    private ConflictCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("conflict")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.remakefactory.conflict.usage"), false);
                    return 1;
                });

        // --- /ref conflict bookmarks 的新结构 ---
        cmd.then(Commands.literal("bookmarks")
                .then(Commands.argument("bookmarks_file", StringArgumentType.string())
                        .suggests(ConflictCommand::suggestBookmarkFiles)
                        // 版本1: 只提供文件名就执行 (使用两个默认值)
                        .executes(context -> runBookmarkOptimizer(
                                context,
                                StringArgumentType.getString(context, "bookmarks_file"),
                                DEFAULT_NAMESPACE,
                                DEFAULT_USE_MULTITHREADING
                        ))
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .suggests((ctx, builder) -> { builder.suggest("gtceu"); return builder.buildFuture(); })
                                // 版本2: 提供文件名和命名空间 (使用一个默认值)
                                .executes(context -> runBookmarkOptimizer(
                                        context,
                                        StringArgumentType.getString(context, "bookmarks_file"),
                                        StringArgumentType.getString(context, "namespace"),
                                        DEFAULT_USE_MULTITHREADING
                                ))
                                .then(Commands.argument("use_multithreading", BoolArgumentType.bool())
                                        // 版本3: 提供所有参数
                                        .executes(context -> runBookmarkOptimizer(
                                                context,
                                                StringArgumentType.getString(context, "bookmarks_file"),
                                                StringArgumentType.getString(context, "namespace"),
                                                BoolArgumentType.getBool(context, "use_multithreading")
                                        ))
                                )
                        )
                )
        );

        // --- /ref conflict test <use_multithreading> <recipe_ids...> ---
        cmd.then(Commands.literal("test")
                .then(Commands.argument("use_multithreading", BoolArgumentType.bool())
                        .then(Commands.argument("recipe_ids", StringArgumentType.greedyString())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(
                                                context.getSource().getServer().getRecipeManager().getRecipeIds().map(ResourceLocation::toString),
                                                builder
                                        )
                                )
                                .executes(ConflictCommand::runTest)
                        )
                )
        );

        return cmd;
    }

    // --- 指令执行逻辑 ---

    private static int runBookmarkOptimizer(CommandContext<CommandSourceStack> context, String bookmarkFileStr, String namespace, boolean useMultiThreading) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!source.isPlayer()) throw ERROR_NOT_PLAYER.create();

        Stopwatch stopwatch = Stopwatch.createStarted();
        RecipeManager recipeManager = source.getServer().getRecipeManager();

        Set<IConflictRecipe> initialSet = getRecipesFromBookmarks(recipeManager, bookmarkFileStr, namespace);
        if (initialSet.isEmpty()) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.no_recipes_in_bookmark", namespace, bookmarkFileStr)).create();
        }

        source.sendSuccess(() -> Component.translatable("commands.remakefactory.conflict.bookmarks.start", initialSet.size(), namespace), true);

        runAnalysis(source, initialSet, useMultiThreading, stopwatch);

        return 1;
    }

    private static int runTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        boolean useMultiThreading = BoolArgumentType.getBool(context, "use_multithreading");
        String recipeIdsStr = StringArgumentType.getString(context, "recipe_ids");

        String[] recipeIdArray = recipeIdsStr.split("\\s+");
        if (recipeIdArray.length == 0 || (recipeIdArray.length == 1 && recipeIdArray[0].isEmpty())) {
            throw ERROR_NO_RECIPES_GIVEN.create();
        }

        RecipeManager recipeManager = source.getServer().getRecipeManager();
        Set<IConflictRecipe> recipeSet = new HashSet<>();
        List<String> invalidIds = new ArrayList<>();

        for (String idStr : recipeIdArray) {
            if (idStr.trim().isEmpty()) continue;
            try {
                recipeSet.add(getRecipeById(recipeManager, new ResourceLocation(idStr)));
            } catch (Exception e) {
                invalidIds.add(idStr);
            }
        }

        if (!invalidIds.isEmpty()) {
            source.sendFailure(Component.translatable("commands.remakefactory.error.invalid_ids", String.join(", ", invalidIds)));
        }
        if (recipeSet.isEmpty()) {
            throw ERROR_NO_VALID_RECIPES.create();
        }

        source.sendSuccess(() -> Component.translatable("commands.remakefactory.conflict.test.start", recipeSet.size()), true);
        runAnalysis(source, recipeSet, useMultiThreading, Stopwatch.createStarted());
        return 1;
    }

    // --- 通用分析与输出逻辑 ---
    private static void runAnalysis(CommandSourceStack source, Set<IConflictRecipe> initialSet, boolean useMultiThreading, Stopwatch stopwatch) throws CommandSyntaxException {
        Set<IConflictRecipe> finalSet;
        RecipeManager recipeManager = source.getServer().getRecipeManager();
        IConflictRecipe mandatoryRecipe = initialSet.iterator().next();
        String namespace = getNamespace(mandatoryRecipe);

        if (initialSet.size() == 1) {
            // 单个配方: 探索模式 (贪心)
            GTRecipeType recipeType = getRecipeType(mandatoryRecipe);
            Set<IConflictRecipe> searchSpace = getRecipesFromMachine(recipeManager, recipeType);
            source.sendSuccess(() -> Component.translatable("commands.remakefactory.conflict.single_mode", recipeType.registryName, searchSpace.size()), true);
            finalSet = ConflictDetector.findLargestConflictFreeSet_greedy(mandatoryRecipe, searchSpace, searchSpace);
        } else {
            // 多个配方: 优化模式 (回溯)
            source.sendSuccess(() -> Component.translatable("commands.remakefactory.conflict.multi_mode", initialSet.size(), useMultiThreading ? "multi-threaded" : "single-threaded"), true);
            Set<IConflictRecipe> candidateSet = new HashSet<>(initialSet);
            candidateSet.remove(mandatoryRecipe);
            if (useMultiThreading) {
                finalSet = ConflictDetector.findLargestConflictFreeSubset_multiThreaded(mandatoryRecipe, candidateSet, initialSet);
            } else {
                finalSet = ConflictDetector.findLargestConflictFreeSubset(mandatoryRecipe, candidateSet, initialSet);
            }
        }

        stopwatch.stop();

        Component summary = Component.translatable("commands.remakefactory.conflict.summary", stopwatch.elapsed(TimeUnit.MILLISECONDS), initialSet.size(), finalSet.size());

        Optional<Component> filePathComponentOpt = writeOptimizedBookmarks(source, finalSet, summary.getString(), namespace);

        source.sendSuccess(() -> summary, true); // 发送摘要
        filePathComponentOpt.ifPresent(filePathComponent ->
                source.sendSuccess(() -> filePathComponent, false) // 如果有，则发送可点击链接
        );
    }

    // --- 动态建议提供者 ---
    private static CompletableFuture<Suggestions> suggestBookmarkFiles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {

        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path jeiConfigDir = gameDir.resolve("config").resolve("jei");

            // --- 扫描 config/jei/world/ ---
            Path worldDir = jeiConfigDir.resolve("world");
            if (Files.isDirectory(worldDir)) {
                // 使用 Files.walk 来递归地查找所有 bookmarks.ini 文件
                try (Stream<Path> pathStream = Files.walk(worldDir)) {
                    pathStream
                            .filter(path -> path.getFileName().toString().equals("bookmarks.ini") && Files.isRegularFile(path))
                            .forEach(path -> {
                                // 构建相对于游戏根目录的相对路径
                                String relativePath = gameDir.relativize(path).toString().replace('\\', '/');
                                builder.suggest("\"" + relativePath + "\""); // 用引号包裹，防止路径中有特殊字符
                            });
                } catch (IOException e) {
                    LOGGER.error(Component.translatable("log.remakefactory.conflict.error.list_saves_dir").getString(), e.getMessage());
                }
            }


        } catch (Exception e) {
            // 捕获所有可能的异常，防止建议提供者崩溃
            LOGGER.error(Component.translatable("log.remakefactory.conflict.error.list_saves_dir").getString(), e.getMessage());
        }

        // 如果在扫描后，builder里没有任何建议，可以给一个提示
        if (builder.getRemaining().isEmpty() && builder.build().getList().isEmpty()) {
            builder.suggest("\"<" + Component.translatable("commands.remakefactory.error.no_bookmarks_found_suggest").getString() + ">\"");
        }

        return builder.buildFuture();
    }

    // --- 核心逻辑与辅助方法 ---
    private static IConflictRecipe getRecipeById(RecipeManager recipeManager, ResourceLocation recipeId) throws CommandSyntaxException {
        Recipe<?> rawRecipe = recipeManager.byKey(recipeId).orElseThrow(ERROR_RECIPE_NOT_FOUND::create);
        if (!(rawRecipe instanceof GTRecipe)) throw ERROR_NOT_GT_RECIPE.create();
        return new GTCEuConflictRecipe((GTRecipe) rawRecipe);
    }

    private static Set<IConflictRecipe> getRecipesFromBookmarks(RecipeManager recipeManager, String bookmarkFileStr, String requiredNamespace) throws CommandSyntaxException {
        Set<IConflictRecipe> recipes = new HashSet<>();
        Path bookmarkPath = Minecraft.getInstance().gameDirectory.toPath().resolve(bookmarkFileStr.replace("\"", ""));
        if (!Files.exists(bookmarkPath)) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.remakefactory.error.file_not_found", bookmarkFileStr)).create();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(bookmarkPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("R:")) {
                    try {
                        String[] mainParts = line.substring(2).split("#", 2);
                        if (mainParts.length < 2) continue;
                        String[] machineParts = mainParts[0].split(":", 2);
                        if (machineParts.length == 2 && machineParts[0].equals(requiredNamespace)) {
                            ResourceLocation recipeId = new ResourceLocation(mainParts[1].split("#")[0]);
                            if (recipeId.getNamespace().equals(requiredNamespace)) {
                                recipeManager.byKey(recipeId).ifPresent(recipe -> {
                                    if (recipe instanceof GTRecipe) {
                                        recipes.add(new GTCEuConflictRecipe((GTRecipe) recipe));
                                    }
                                });
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            LOGGER.error(Component.translatable("log.remakefactory.conflict.error.read_bookmark_io", bookmarkPath).getString(), e);
            throw ERROR_CANNOT_READ_FILE.create();
        }
        return recipes;
    }

    private static Set<IConflictRecipe> getRecipesFromMachine(RecipeManager recipeManager, GTRecipeType recipeType) {
        return recipeManager.getAllRecipesFor(recipeType).stream()
                .filter(Objects::nonNull)
                .map(GTCEuConflictRecipe::new)
                .collect(Collectors.toSet());
    }

    private static Optional<Component> writeOptimizedBookmarks(CommandSourceStack source, Set<IConflictRecipe> recipeSet, String summary, String namespace) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Path outputPath = server.getFile("config/remakefactory").toPath();
        File outputDir = outputPath.toFile();
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                LOGGER.error(Component.translatable("log.remakefactory.conflict.error.create_dir_fail", outputPath).getString());
                throw ERROR_CANNOT_CREATE_DIR.create();
            }
        }
        String fileName = String.format("bookmarks_%s_optimized.ini", namespace);
        Path filePath = outputPath.resolve(fileName);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        Path backupFilePath = outputPath.resolve(String.format("bookmarks_%s_%s.bak", namespace, timestamp));
        try { if (Files.exists(filePath)) { Files.move(filePath, backupFilePath, StandardCopyOption.REPLACE_EXISTING); } } catch (IOException e) { LOGGER.warn(Component.translatable("log.remakefactory.conflict.warn.backup_fail", e.getMessage()).getString()); }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
            writer.write(String.format("# Optimized Recipe Bookmarks for namespace '%s' - Generated by RemakeFactory", namespace));
            writer.newLine(); writer.write("# " + summary);
            writer.newLine(); writer.write("# Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.newLine(); writer.newLine();
            for (IConflictRecipe conflictRecipe : recipeSet) {
                if (conflictRecipe.getUnderlyingRecipe() instanceof GTRecipe gtRecipe) {
                    formatRecipeToBookmarkString(gtRecipe).ifPresent(line -> {
                        try { writer.write(line); writer.newLine(); } catch (IOException ignored) {}
                    });
                }
            }
            LOGGER.info(Component.translatable("log.remakefactory.conflict.info.write_success", filePath).getString());
            String relativePath = server.getFile("").toPath().relativize(filePath).toString().replace('\\', '/');
            Component filePathComponent = Component.translatable("commands.remakefactory.conflict.write_success",
                    Component.literal(relativePath)
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.GREEN)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, filePath.toAbsolutePath().toString()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
                            )
            );
            return Optional.of(filePathComponent);
        } catch (IOException e) {
            LOGGER.error(Component.translatable("log.remakefactory.conflict.error.write_file_io", filePath).getString(), e);
            throw ERROR_CANNOT_WRITE_FILE.create();
        }
    }

    private static Optional<String> formatRecipeToBookmarkString(GTRecipe recipe) {
        String machineId = recipe.recipeType.registryName.toString();
        String recipeId = recipe.getId().toString();
        List<Content> itemOutputs = recipe.outputs.get(ItemRecipeCapability.CAP);
        if (itemOutputs != null && !itemOutputs.isEmpty()) {
            Object contentObj = itemOutputs.get(0).content;
            if (contentObj instanceof SizedIngredient si && si.getItems().length > 0) {
                return Optional.of(String.format("R:%s#%s#item_stack&%s", machineId, recipeId, BuiltInRegistries.ITEM.getKey(si.getItems()[0].getItem())));
            }
        }
        List<Content> fluidOutputs = recipe.outputs.get(FluidRecipeCapability.CAP);
        if (fluidOutputs != null && !fluidOutputs.isEmpty()) {
            Object contentObj = fluidOutputs.get(0).content;
            if (contentObj instanceof FluidIngredient fi && fi.getStacks().length > 0) {
                return Optional.of(String.format("R:%s#%s#fluid_stack&fluid:%s", machineId, recipeId, BuiltInRegistries.FLUID.getKey(fi.getStacks()[0].getFluid())));
            }
        }
        return Optional.empty();
    }

    private static GTRecipeType getRecipeType(IConflictRecipe recipe) throws CommandSyntaxException {
        if (recipe.getUnderlyingRecipe() instanceof GTRecipe gtRecipe) return gtRecipe.recipeType;
        throw ERROR_NOT_GT_RECIPE.create();
    }

    private static String getNamespace(IConflictRecipe recipe) {
        if (recipe.getUnderlyingRecipe() instanceof GTRecipe gtRecipe) return gtRecipe.getId().getNamespace();
        return "unknown";
    }
}