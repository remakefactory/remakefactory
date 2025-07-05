package com.remakefactory.remakefactory.util.recipe.conflict;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 针对 GTCEu 的 GTRecipe 的 IConflictRecipe 具体实现。
 */
public class GTCEuConflictRecipe implements IConflictRecipe {

    private final GTRecipe recipe;
    private Map<String, Integer> memoizedInputs;

    public GTCEuConflictRecipe(GTRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public Object getUnderlyingRecipe() {
        return this.recipe;
    }

    @Override
    public Map<String, Integer> getInputsAsMultiset() {
        if (this.memoizedInputs != null) {
            return this.memoizedInputs;
        }

        Map<String, Integer> inputs = new HashMap<>();
        parseContentMap(inputs, this.recipe.inputs);

        this.memoizedInputs = inputs;
        return this.memoizedInputs;
    }

    private void parseContentMap(Map<String, Integer> inputs, Map<?, List<Content>> contentMap) {
        // --- 处理物品输入 ---
        List<Content> itemContents = contentMap.get(ItemRecipeCapability.CAP);
        if (itemContents != null) {
            for (Content content : itemContents) {
                try {
                    Object ingredientObj = content.content;
                    String key = null;
                    int amount = 0;

                    if (ingredientObj instanceof SizedIngredient si) {
                        amount = si.getAmount();
                        JsonElement json = si.toJson();
                        if (json.isJsonObject()) {
                            JsonObject ingredientJson = json.getAsJsonObject().getAsJsonObject("ingredient");
                            if (ingredientJson != null) {
                                if (ingredientJson.has("tag")) {
                                    key = "tag:" + ingredientJson.get("tag").getAsString();
                                } else if (ingredientJson.has("item")) {
                                    key = "item:" + ingredientJson.get("item").getAsString();
                                }
                            }
                        }
                    } else if (ingredientObj instanceof IntCircuitIngredient ici) {
                        amount = 1;
                        JsonElement json = ici.toJson();
                        if (json.isJsonObject()) {
                            int configValue = json.getAsJsonObject().get("configuration").getAsInt();
                            key = "circuit:" + configValue;
                        }
                    }

                    if (key != null) {
                        inputs.put(key, inputs.getOrDefault(key, 0) + amount);
                    }
                } catch (Exception e) {
                    // 忽略解析失败的 content
                }
            }
        }

        // --- 处理流体输入 ---
        List<Content> fluidContents = contentMap.get(FluidRecipeCapability.CAP);
        if (fluidContents != null) {
            for (Content content : fluidContents) {
                Object ingredientObj = content.content;
                if (ingredientObj instanceof FluidIngredient fi) {
                    long fluidAmount = fi.getAmount();
                    if (fluidAmount > 0 && fi.getStacks().length > 0 && !fi.getStacks()[0].isEmpty()) {
                        com.lowdragmc.lowdraglib.side.fluid.FluidStack ldlFs = fi.getStacks()[0];
                        String key = "fluid:" + BuiltInRegistries.FLUID.getKey(ldlFs.getFluid());
                        inputs.put(key, inputs.getOrDefault(key, 0) + (int) fluidAmount);
                    }
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GTCEuConflictRecipe that = (GTCEuConflictRecipe) o;
        return Objects.equals(recipe.getId(), that.recipe.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipe.getId());
    }
}