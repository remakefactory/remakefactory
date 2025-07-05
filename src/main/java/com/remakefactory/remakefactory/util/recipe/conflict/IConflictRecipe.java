package com.remakefactory.remakefactory.util.recipe.conflict;

import java.util.Map;

/**
 * 一个抽象层，用于任何希望被 {@link ConflictDetector} 处理的配方类型。
 * 这使得本系统未来可以轻松扩展到GTCEu之外的其他Mod配方。
 */
public interface IConflictRecipe {

    /**
     * 获取底层的、原始的配方对象。用于等价性检查和最终结果的获取。
     * @return 原始配方对象 (例如一个 GTRecipe 实例)。
     */
    Object getUnderlyingRecipe();

    /**
     * 核心方法。将配方的输入解析为一个规范的、可比较的格式。
     * 这个Map代表一个多重集(multiset)，其中键是原料的规范化字符串表示
     * (例如 "item:minecraft:iron_ingot", "fluid:minecraft:water", "tag:forge:ingots/iron")，
     * 值是所需的数量。
     *
     * @return 一个代表输入多重集的Map。
     */
    Map<String, Integer> getInputsAsMultiset();

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();
}