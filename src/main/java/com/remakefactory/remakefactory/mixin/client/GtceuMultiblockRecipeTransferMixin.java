package com.remakefactory.remakefactory.mixin.client;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.gregtechceu.gtceu.api.gui.widget.PatternPreviewWidget;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.remakefactory.remakefactory.config.Config;
import com.remakefactory.remakefactory.init.Items;
import com.remakefactory.remakefactory.item.MultiblockPlaceholderItem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Mixin dedicated to handling GTCEu Multiblock Structure "recipe" transfers from JEI to an AE2 Pattern Encoding Terminal.
 * It correctly creates a blueprint by counting blocks from the currently viewed multiblock shape
 * and encodes it into a pattern with a custom Multiblock Placeholder item as the output.
 */
@Mixin(value = appeng.integration.modules.jei.transfer.EncodePatternTransferHandler.class, remap = false, priority = 1009)
public abstract class GtceuMultiblockRecipeTransferMixin {

    @Unique
    private static final Field remakefactory$widgetField; // Cached reflection Field for ModularWrapper's 'widget'
    @Unique
    private static final Field remakefactory$indexField;  // Cached reflection Field for PatternPreviewWidget's 'index'

    /*
      Static initializer to safely get private fields via reflection once at class load time.
      This is safer and more performant than doing it repeatedly inside the transfer method.
     */
    static {
        Field widgetF = null;
        Field indexF = null;
        try {
            widgetF = ModularWrapper.class.getDeclaredField("widget");
            widgetF.setAccessible(true);
            indexF = PatternPreviewWidget.class.getDeclaredField("index");
            indexF.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            // If fields are not found (e.g., due to a library update), they remain null,
            // and the logic will gracefully fall back to default behavior.
        }
        remakefactory$widgetField = widgetF;
        remakefactory$indexField = indexF;
    }

    @Inject(
            method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onTransferMultiblockRecipe(
            PatternEncodingTermMenu menu,
            Object recipe,
            IRecipeSlotsView slotsView,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            CallbackInfoReturnable<IRecipeTransferError> cir) {

        if (!(recipe instanceof MultiblockInfoWrapper wrapper)) {
            return; // This mixin only handles GTCEu multiblock info pages.
        }

        if (!remakefactory$isMultiblockTransferEnabled()) {
            return;
        }

        // For the pre-check (when JEI shows the '+' button), always allow the transfer.
        if (!doTransfer) {
            cir.setReturnValue(null);
            return;
        }

        try {
            // Attempt to process the multiblock blueprint and encode it.
            if (remakefactory$encodeMultiblockBlueprint(wrapper, menu)) {
                cir.setReturnValue(null); // Success, cancel the original method.
            }
        } catch (Exception ignored) {
            // Silently fail to prevent crashing the game on unexpected errors.
        }
    }

    /**
     * Checks if the multiblock blueprint transfer feature is enabled in the configuration.
     */
    @Unique
    private boolean remakefactory$isMultiblockTransferEnabled() {
        return Config.COMMON.recipeHijacker.enable.get() &&
                Config.COMMON.recipeHijacker.gtceu.enable.get() &&
                Config.COMMON.recipeHijacker.gtceu.multiBlock.get();
    }

    /**
     * Core logic to process a multiblock structure, convert it into a recipe pattern, and encode it.
     * @return true if the encoding was successful, false otherwise.
     */
    @Unique
    private boolean remakefactory$encodeMultiblockBlueprint(MultiblockInfoWrapper wrapper, PatternEncodingTermMenu menu) throws ReflectiveOperationException {
        MultiblockMachineDefinition definition = wrapper.definition;
        if (definition == null) {
            return false;
        }

        // 1. Get the correct shape based on the currently viewed page in JEI.
        List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
        if (shapes.isEmpty()) {
            return false;
        }

        int currentPageIndex = remakefactory$getCurrentPageIndex(wrapper, shapes);

        BlockInfo[][][] blockArray = shapes.get(currentPageIndex).getBlocks();

        // 2. Count, filter, and sort all blocks required for the structure.
        Object2IntMap<Block> structureBlocks = new Object2IntOpenHashMap<>();
        for (BlockInfo[][] ySlice : blockArray) {
            for (BlockInfo[] xSlice : ySlice) {
                for (BlockInfo blockInfo : xSlice) {
                    if (blockInfo != null && blockInfo != BlockInfo.EMPTY && blockInfo.getBlockState() != null) {
                        Block block = blockInfo.getBlockState().getBlock();
                        if (!block.equals(Blocks.AIR)) {
                            structureBlocks.mergeInt(block, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        if (structureBlocks.isEmpty()) {
            return false;
        }

        // Separate the controller block from the rest of the structure blocks.
        Item controllerItem = ForgeRegistries.ITEMS.getValue(definition.getId());
        ItemStack controllerStack = null;
        if (controllerItem instanceof net.minecraft.world.item.BlockItem blockItem) {
            Block controllerBlock = blockItem.getBlock();
            int controllerCount = structureBlocks.removeInt(controllerBlock);
            if (controllerCount > 0) {
                controllerStack = new ItemStack(controllerItem, controllerCount);
            }
        }

        // Filter out common "functional" blocks like input/output buses/hatches if they are few.
        structureBlocks.object2IntEntrySet().removeIf(entry -> {
            if (entry.getIntValue() <= 2) { // Only filter if there are 1 or 2 of them.
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(entry.getKey());
                if (blockId != null) {
                    String path = blockId.getPath();
                    return path.contains("input") || path.contains("output");
                }
            }
            return false;
        });

        // Build the sorted list of input items for the pattern.
        List<ItemStack> sortedStacks = structureBlocks.object2IntEntrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(entry -> new ItemStack(entry.getKey().asItem(), entry.getIntValue()))
                .filter(stack -> !stack.isEmpty())
                .toList();

        List<List<GenericStack>> inputs = new ArrayList<>();
        if (controllerStack != null) {
            inputs.add(List.of(Objects.requireNonNull(GenericStack.fromItemStack(controllerStack))));
        }
        for (ItemStack stack : sortedStacks) {
            inputs.add(List.of(Objects.requireNonNull(GenericStack.fromItemStack(stack))));
        }

        // 3. Prepare the placeholder item as the recipe output.
        ItemStack placeholder = new ItemStack(Items.MULTIBLOCK_PLACEHOLDER.get());
        CompoundTag rootTag = new CompoundTag();
        CompoundTag multiblockTag = new CompoundTag();

        multiblockTag.putString(MultiblockPlaceholderItem.NBT_KEY_ID, definition.getId().toString());

        // Only add the 'index' NBT tag if it's a non-default shape (index > 0).
        if (currentPageIndex > 0) {
            multiblockTag.putInt("index", currentPageIndex);
        }

        rootTag.put(MultiblockPlaceholderItem.NBT_KEY_MULTIBLOCK, multiblockTag);
        placeholder.setTag(rootTag);

        GenericStack outputStack = GenericStack.fromItemStack(placeholder);
        if (outputStack == null) {
            return false;
        }
        List<GenericStack> outputs = List.of(outputStack);

        // 4. Encode the final recipe into the AE2 terminal.
        EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
        return true;
    }

    @Unique
    private static int remakefactory$getCurrentPageIndex(MultiblockInfoWrapper wrapper, List<MultiblockShapeInfo> shapes) throws IllegalAccessException {
        int currentPageIndex = 0;
        // Use reflection to get the current page index from the JEI widget.
        if (remakefactory$widgetField != null && remakefactory$indexField != null) {
            Object widgetObject = remakefactory$widgetField.get(wrapper);
            if (widgetObject instanceof PatternPreviewWidget widget) {
                currentPageIndex = remakefactory$indexField.getInt(widget);
            }
        }

        // Sanity check the index.
        if (currentPageIndex < 0 || currentPageIndex >= shapes.size()) {
            currentPageIndex = 0;
        }
        return currentPageIndex;
    }
}