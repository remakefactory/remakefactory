package com.remakefactory.remakefactory.mixin.client;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.remakefactory.remakefactory.config.Config;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.*;

@Mixin(value = appeng.integration.modules.jei.transfer.EncodePatternTransferHandler.class, remap = false, priority = 1010)
public abstract class GtceuRecipeTransferReviseMixin  {

    @Unique
    private static final Field remakefactory$contentField;

    static {
        Field field = null;
        try {
            field = Content.class.getDeclaredField("content");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Error is handled by null-checking the field later.
        }
        remakefactory$contentField = field;
    }

    @Inject(
            method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void remakefactory$onTransferGtceuRecipe(
            PatternEncodingTermMenu menu,
            Object recipe,
            IRecipeSlotsView slotsView,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            CallbackInfoReturnable<IRecipeTransferError> cir) {

        if (!(recipe instanceof GTRecipeWrapper gtRecipeWrapper)) {
            return;
        }

        if (!remakefactory$isGtceuModificationEnabled()) {
            return;
        }

        if (!doTransfer) {
            cir.setReturnValue(null);
            return;
        }

        try {
            // FIX 1: Changed from gtRecipeWrapper.getRecipe() to direct field access
            GTRecipe originalRecipe = gtRecipeWrapper.recipe;
            if (originalRecipe == null) {
                return;
            }

            GTRecipe processedRecipe = originalRecipe;
            if (Config.COMMON.recipeHijacker.gtceu.filter.get()) {
                processedRecipe = remakefactory$applyFiltering(processedRecipe);
            }
            if (Config.COMMON.recipeHijacker.gtceu.reorder.get()) {
                processedRecipe = remakefactory$applyReordering(processedRecipe);
            }

            int multiplier = Config.COMMON.recipeHijacker.gtceu.scalingMultiplier.get();
            if (multiplier > 1) {
                processedRecipe = remakefactory$applyScaling(processedRecipe, multiplier);
            }

            List<List<GenericStack>> modifiedInputs = remakefactory$buildInputSlotList(processedRecipe);
            List<GenericStack> modifiedOutputs = remakefactory$buildOutputFlatList(processedRecipe);

            EncodingHelper.encodeProcessingRecipe(menu, modifiedInputs, modifiedOutputs);
            cir.setReturnValue(null);
        } catch (Exception e) {
            // Silently fail.
        }
    }

    @Unique
    private boolean remakefactory$isGtceuModificationEnabled() {
        return Config.COMMON.recipeHijacker.enable.get() &&
                Config.COMMON.recipeHijacker.gtceu.enable.get();
    }

    @Unique
    private GTRecipe remakefactory$applyFiltering(GTRecipe recipe) {
        GTRecipe newRecipe = recipe.copy();
        List<Content> itemInputs = newRecipe.inputs.get(ItemRecipeCapability.CAP);

        if (itemInputs != null && !itemInputs.isEmpty()) {
            if (itemInputs.removeIf(content -> content.chance == 0)) {
                if (itemInputs.isEmpty()) {
                    newRecipe.inputs.remove(ItemRecipeCapability.CAP);
                }
            }
        }

        List<Content> fluidInputs = newRecipe.inputs.get(FluidRecipeCapability.CAP);

        if (fluidInputs != null && !fluidInputs.isEmpty()) {
            if (fluidInputs.removeIf(content -> content.chance == 0)) {
                if (fluidInputs.isEmpty()) {
                    newRecipe.inputs.remove(FluidRecipeCapability.CAP);
                }
            }
        }

        return newRecipe;
    }

    @Unique
    private GTRecipe remakefactory$applyReordering(GTRecipe recipe) {
        GTRecipe newRecipe = recipe.copy();
        List<Content> itemInputs = newRecipe.inputs.get(ItemRecipeCapability.CAP);

        if (itemInputs != null && itemInputs.size() > 1) {
            Collections.reverse(itemInputs);
        }
        return newRecipe;
    }

    @Unique
    private GTRecipe remakefactory$applyScaling(GTRecipe recipe, int multiplier) {
        GTRecipe newRecipe = recipe.copy();
        remakefactory$scaleRecipeContents(newRecipe.inputs, multiplier);
        remakefactory$scaleRecipeContents(newRecipe.outputs, multiplier);
        remakefactory$scaleRecipeContents(newRecipe.tickInputs, multiplier);
        remakefactory$scaleRecipeContents(newRecipe.tickOutputs, multiplier);
        return newRecipe;
    }

    @Unique
    private void remakefactory$scaleRecipeContents(Map<RecipeCapability<?>, List<Content>> contents, int multiplier) {
        if (remakefactory$contentField == null) return;

        for (List<Content> contentList : contents.values()) {
            for (Content content : contentList) {
                try {
                    Object ingredientObj = remakefactory$contentField.get(content);
                    if (ingredientObj instanceof SizedIngredient si) {
                        for (ItemStack stack : si.getItems()) {
                            if (!stack.isEmpty()) {
                                long newCount = (long) stack.getCount() * multiplier;
                                stack.setCount((int) Math.min(newCount, Integer.MAX_VALUE));
                            }
                        }
                    } else if (ingredientObj instanceof FluidIngredient fi) {
                        for (com.lowdragmc.lowdraglib.side.fluid.FluidStack stack : fi.getStacks()) {
                            if (!stack.isEmpty()) {
                                stack.setAmount(stack.getAmount() * multiplier);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Unique
    private List<List<GenericStack>> remakefactory$buildInputSlotList(GTRecipe recipe) {
        List<List<GenericStack>> allSlotsList = new ArrayList<>();
        remakefactory$addContentsToSlotList(recipe.inputs, allSlotsList);
        return allSlotsList;
    }

    @Unique
    private List<GenericStack> remakefactory$buildOutputFlatList(GTRecipe recipe) {
        List<GenericStack> flatOutputList = new ArrayList<>();
        List<List<GenericStack>> nestedOutputs = new ArrayList<>();
        remakefactory$addContentsToSlotList(recipe.outputs, nestedOutputs);
        nestedOutputs.forEach(flatOutputList::addAll);
        return flatOutputList;
    }

    @Unique
    private void remakefactory$addContentsToSlotList(Map<RecipeCapability<?>, List<Content>> contentMap, List<List<GenericStack>> allSlotsList) {
        if (remakefactory$contentField == null) return;

        List<Content> itemContents = contentMap.get(ItemRecipeCapability.CAP);
        if (itemContents != null) {
            for (Content content : itemContents) {
                try {
                    Object ingredientObj = remakefactory$contentField.get(content);
                    List<GenericStack> stacksForThisSlot = new ArrayList<>();
                    if (ingredientObj instanceof SizedIngredient si) {
                        Arrays.stream(si.getItems()).map(GenericStack::fromItemStack).filter(Objects::nonNull).forEach(stacksForThisSlot::add);
                    } else if (ingredientObj instanceof IntCircuitIngredient ici) {
                        Arrays.stream(ici.getItems()).map(GenericStack::fromItemStack).filter(Objects::nonNull).forEach(stacksForThisSlot::add);
                    }
                    if (!stacksForThisSlot.isEmpty()) {
                        allSlotsList.add(stacksForThisSlot);
                    }
                } catch (Exception ignored) {}
            }
        }

        List<Content> fluidContents = contentMap.get(FluidRecipeCapability.CAP);
        if (fluidContents != null) {
            for (Content content : fluidContents) {
                try {
                    Object ingredientObj = remakefactory$contentField.get(content);
                    List<GenericStack> stacksForThisSlot = new ArrayList<>();
                    if (ingredientObj instanceof FluidIngredient fi) {
                        for (com.lowdragmc.lowdraglib.side.fluid.FluidStack ldlFs : fi.getStacks()) {
                            net.minecraftforge.fluids.FluidStack forgeFs = new net.minecraftforge.fluids.FluidStack(ldlFs.getFluid(), (int) ldlFs.getAmount(), ldlFs.getTag());

                            // FIX 2 & 3: Reverted to the "original" way of handling a potentially null return value.
                            GenericStack genericStack = GenericStack.fromFluidStack(forgeFs);
                            if (genericStack != null) {
                                stacksForThisSlot.add(genericStack);
                            }
                        }
                    }
                    if (!stacksForThisSlot.isEmpty()) {
                        allSlotsList.add(stacksForThisSlot);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}