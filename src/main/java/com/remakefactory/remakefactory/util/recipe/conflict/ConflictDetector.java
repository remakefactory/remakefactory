package com.remakefactory.remakefactory.util.recipe.conflict;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * 提供用于检测配方间冲突和寻找最大无冲突子集的方法。
 * 此类实现了我们理论分析得出的最终版、超优化的算法。
 */
public final class ConflictDetector {

    private ConflictDetector() {} // 静态工具类，无需实例化

    // =================================================================================
    // == 公共API：单线程 & 多线程版本                                                ==
    // =================================================================================

    /**
     * [单线程] 从给定的候选列表中，找到能与一个必须配方安全共存的最大子集。
     * 使用“从大到小”的回溯搜索策略，以尽快找到最优解。
     *
     * @param mandatoryRecipe 必须存在的配方。
     * @param candidateSet    待筛选的候选配方列表。
     * @param globalScope     用于检查外部冲突的全局配方范围。
     * @return 包含必须配方和候选列表最大子集的一个无冲突集合。
     */
    public static Set<IConflictRecipe> findLargestConflictFreeSubset(
            IConflictRecipe mandatoryRecipe,
            Collection<IConflictRecipe> candidateSet,
            Collection<IConflictRecipe> globalScope) {

        DescendingBacktrackingSolver solver = new DescendingBacktrackingSolver(mandatoryRecipe, new ArrayList<>(candidateSet), globalScope);
        return solver.solve();
    }

    /**
     * [多线程版] 从给定的候选列表中，找到能与一个必须配方安全共存的最大子集。
     * 使用 Fork/Join 框架并行搜索，以加速在大型候选集上的计算。
     *
     * @param mandatoryRecipe 必须存在的配方。
     * @param candidateSet    待筛选的候选配方列表。
     * @param globalScope     用于检查外部冲突的全局配方范围。
     * @return 包含必须配方和候选列表最大子集的一个无冲突集合。
     */
    public static Set<IConflictRecipe> findLargestConflictFreeSubset_multiThreaded(
            IConflictRecipe mandatoryRecipe,
            Collection<IConflictRecipe> candidateSet,
            Collection<IConflictRecipe> globalScope) {

        MultiThreadedSolver solver = new MultiThreadedSolver(mandatoryRecipe, new ArrayList<>(candidateSet), globalScope);
        return solver.solve();
    }

    /**
     * [贪心算法] 从一个全局搜索空间中，找到一个包含必须配方的、大的无冲突集合。
     * 速度快，但不保证找到的集合是绝对最大的。
     *
     * @param mandatoryRecipe 必须存在的配方。
     * @param searchSpace     用于搜索的全局空间 (例如，机器内的所有配方)。
     * @param globalScope     通常与 searchSpace 相同，用于检查外部冲突。
     * @return 一个大的无冲突集合。
     */
    public static Set<IConflictRecipe> findLargestConflictFreeSet_greedy(
            IConflictRecipe mandatoryRecipe,
            Collection<IConflictRecipe> searchSpace,
            Collection<IConflictRecipe> globalScope) {

        Set<IConflictRecipe> maxSafeSet = new HashSet<>(Set.of(mandatoryRecipe));

        // 初始检查
        if (!isSetSafelyCoexistent(maxSafeSet, globalScope)) {
            return maxSafeSet;
        }

        List<IConflictRecipe> candidates = new ArrayList<>(searchSpace);
        candidates.removeIf(r -> r.equals(mandatoryRecipe));
        // 启发式策略：优先尝试添加输入更“简单”的配方。
        candidates.sort(Comparator.comparingInt(r -> r.getInputsAsMultiset().size()));

        // 使用增量检查来优化性能
        for (IConflictRecipe candidate : candidates) {
            if (isAdditionSafe(maxSafeSet, candidate, globalScope)) {
                maxSafeSet.add(candidate); // 贪心地接受这个安全的添加
            }
        }
        return maxSafeSet;
    }

    // =================================================================================
    // == 核心验证逻辑 (包含增量检查优化)                                             ==
    // =================================================================================

    /**
     * [全量检查] 验证一个给定的配方集合是否是强无冲突的。
     * 这是最基础、最完整的验证，但性能开销较大。
     */
    public static boolean isSetSafelyCoexistent(Set<IConflictRecipe> recipeSet, Collection<IConflictRecipe> globalScope) {
        if (recipeSet.size() <= 1) return true;

        // 阶段一: 内部冲突检查
        for (IConflictRecipe recipeK : recipeSet) {
            Map<String, Integer> unionOfOthers = unionInputs(recipeSet, recipeK);
            if (isMultisetSubset(recipeK.getInputsAsMultiset(), unionOfOthers)) return false;
        }

        // 阶段二: 外部冲突检查
        Map<String, Integer> totalUnion = unionInputs(recipeSet, null);
        for (IConflictRecipe externalRecipe : globalScope) {
            if (!recipeSet.contains(externalRecipe)) {
                if (isMultisetSubset(externalRecipe.getInputsAsMultiset(), totalUnion)) return false;
            }
        }
        return true;
    }

    /**
     * [增量检查] 验证向一个已知的安全集合中添加一个新配方后，新集合是否仍然安全。
     * 比全量检查性能更高。
     */
    public static boolean isAdditionSafe(Set<IConflictRecipe> safeSet, IConflictRecipe newRecipe, Collection<IConflictRecipe> globalScope) {
        // 1. 新配方不能是任何旧配方的子集，反之亦然
        for (IConflictRecipe oldRecipe : safeSet) {
            if (isMultisetSubset(newRecipe.getInputsAsMultiset(), oldRecipe.getInputsAsMultiset()) ||
                    isMultisetSubset(oldRecipe.getInputsAsMultiset(), newRecipe.getInputsAsMultiset())) {
                return false;
            }
        }

        Set<IConflictRecipe> newSet = new HashSet<>(safeSet);
        newSet.add(newRecipe);

        // 2. 检查新集合的内部冲突 (只需检查新配方和旧配方组合的情况)
        Map<String, Integer> unionOfOld = unionInputs(safeSet, null);
        // 新配方是否会被旧配方组合而成
        if (isMultisetSubset(newRecipe.getInputsAsMultiset(), unionOfOld)) return false;

        // 旧配方是否会被“其他旧配方+新配方”组合而成
        for (IConflictRecipe oldRecipe : safeSet) {
            Map<String, Integer> unionOfOthers = unionInputs(safeSet, oldRecipe);
            // 增量更新并集，而不是重新计算
            newRecipe.getInputsAsMultiset().forEach((item, count) ->
                    unionOfOthers.put(item, unionOfOthers.getOrDefault(item, 0) + count));

            if (isMultisetSubset(oldRecipe.getInputsAsMultiset(), unionOfOthers)) return false;
        }

        // 3. 检查新集合的外部冲突
        Map<String, Integer> newTotalUnion = new HashMap<>(unionOfOld);
        newRecipe.getInputsAsMultiset().forEach((item, count) ->
                newTotalUnion.put(item, newTotalUnion.getOrDefault(item, 0) + count));

        for (IConflictRecipe externalRecipe : globalScope) {
            if (!newSet.contains(externalRecipe)) {
                if (isMultisetSubset(externalRecipe.getInputsAsMultiset(), newTotalUnion)) return false;
            }
        }

        return true;
    }

    // =================================================================================
    // == 单线程回溯求解器 (最终优化版：使用增量检查)                                 ==
    // =================================================================================
    private static class DescendingBacktrackingSolver {
        private final IConflictRecipe mandatoryRecipe;
        private final List<IConflictRecipe> candidates;
        private final Collection<IConflictRecipe> globalScope;

        DescendingBacktrackingSolver(IConflictRecipe m, List<IConflictRecipe> c, Collection<IConflictRecipe> s) {
            mandatoryRecipe = m;
            candidates = c;
            globalScope = s;
        }

        public Set<IConflictRecipe> solve() {
            // 必须配方自己必须是安全的
            Set<IConflictRecipe> initialSet = Set.of(mandatoryRecipe);
            if (!isSetSafelyCoexistent(initialSet, globalScope)) {
                return initialSet;
            }

            // 从大到小寻找第一个解
            for (int k = candidates.size(); k >= 0; k--) {
                Optional<Set<IConflictRecipe>> result = findFirstSafeCombination(k);
                if (result.isPresent()) {
                    Set<IConflictRecipe> finalSet = result.get();
                    finalSet.add(mandatoryRecipe);
                    return finalSet;
                }
            }
            return initialSet;
        }

        private Optional<Set<IConflictRecipe>> findFirstSafeCombination(int k) {
            // 初始组合是空的，但我们知道它要和 mandatoryRecipe 组合
            // 所以我们的基础安全集就是 mandatoryRecipe
            return findRecursive(0, k, new HashSet<>(), Set.of(mandatoryRecipe));
        }

        /**
         * 递归地构建并测试大小为k的子集，并使用增量检查进行剪枝。
         *
         * @param startIdx             当前搜索的起始索引
         * @param k                    目标子集大小 (不含 mandatoryRecipe)
         * @param currentCombination   正在构建的候选者子集
         * @param knownSafeBaseSet     已知的、总是存在的安全基础集合 (包含上一层的组合和mandatoryRecipe)
         * @return 如果找到一个安全的子集，则返回它，否则返回空的Optional。
         */
        private Optional<Set<IConflictRecipe>> findRecursive(int startIdx, int k,
                                                             Set<IConflictRecipe> currentCombination,
                                                             Set<IConflictRecipe> knownSafeBaseSet) {
            // 1. 成功构建了一个大小为 k 的组合
            if (currentCombination.size() == k) {
                // 因为每一步都是增量检查通过的，所以这个组合一定是安全的
                return Optional.of(currentCombination);
            }

            // 2. 剪枝
            if (candidates.size() - startIdx < k - currentCombination.size()) {
                return Optional.empty();
            }

            // 3. 递归探索
            for (int i = startIdx; i < candidates.size(); i++) {
                IConflictRecipe candidate = candidates.get(i);

                // **核心优化**: 使用增量检查来提前剪枝
                if (isAdditionSafe(knownSafeBaseSet, candidate, globalScope)) {
                    // 如果添加 candidate 是安全的，则以此为基础继续探索
                    currentCombination.add(candidate);

                    // 创建新的基础集合，用于下一层递归的增量检查
                    Set<IConflictRecipe> nextBaseSet = new HashSet<>(knownSafeBaseSet);
                    nextBaseSet.add(candidate);

                    Optional<Set<IConflictRecipe>> result = findRecursive(i + 1, k, currentCombination, nextBaseSet);
                    if (result.isPresent()) {
                        return result; // 找到了！立即向上传递结果
                    }

                    // 回溯
                    currentCombination.remove(candidate);
                }
            }

            return Optional.empty(); // 此分支无解
        }
    }

    // =================================================================================
    // == 多线程回溯求解器 (Fork/Join)                                                ==
    // =================================================================================
    private static class MultiThreadedSolver {
        private final IConflictRecipe mandatoryRecipe; private final List<IConflictRecipe> candidates;
        private final Collection<IConflictRecipe> globalScope;
        private final ForkJoinPool pool = ForkJoinPool.commonPool();
        MultiThreadedSolver(IConflictRecipe m, List<IConflictRecipe> c, Collection<IConflictRecipe> s) {
            mandatoryRecipe = m; candidates = c; globalScope = s;
        }

        public Set<IConflictRecipe> solve() {
            if (!isSetSafelyCoexistent(Set.of(mandatoryRecipe), globalScope)) return Set.of(mandatoryRecipe);
            for (int k = candidates.size(); k >= 0; k--) {
                CombinationFinderTask mainTask = new CombinationFinderTask(mandatoryRecipe, candidates, globalScope, 0, k, new HashSet<>());
                Optional<Set<IConflictRecipe>> result = pool.invoke(mainTask);
                if (result.isPresent()) {
                    Set<IConflictRecipe> finalSet = result.get();
                    finalSet.add(mandatoryRecipe);
                    // 不再shutdownNow，因为commonPool是共享的。invoke会处理任务的生命周期。
                    return finalSet;
                }
            }
            return Set.of(mandatoryRecipe);
        }
    }

    private static class CombinationFinderTask extends RecursiveTask<Optional<Set<IConflictRecipe>>> {
        private final IConflictRecipe mandatoryRecipe; private final List<IConflictRecipe> candidates;
        private final Collection<IConflictRecipe> globalScope; private final int startIdx;
        private final int k; private final Set<IConflictRecipe> currentCombination;
        // 阈值：当任务规模小于此值时，不再分解，直接单线程计算，避免过度分解的开销
        private static final int THRESHOLD = 5;

        CombinationFinderTask(IConflictRecipe m, List<IConflictRecipe> c, Collection<IConflictRecipe> s, int start, int k, Set<IConflictRecipe> current) {
            mandatoryRecipe = m; candidates = c; globalScope = s; startIdx = start; this.k = k; currentCombination = current;
        }

        @Override
        protected Optional<Set<IConflictRecipe>> compute() {
            if (currentCombination.size() == k) {
                Set<IConflictRecipe> testSet = new HashSet<>(currentCombination);
                testSet.add(mandatoryRecipe);
                return isSetSafelyCoexistent(testSet, globalScope) ? Optional.of(currentCombination) : Optional.empty();
            }
            if (candidates.size() - startIdx < k - currentCombination.size()) return Optional.empty();

            // 当任务规模足够小时，转为单线程循环，避免创建过多小任务的开销
            if (candidates.size() - startIdx <= THRESHOLD) {
                return computeSequentially();
            }

            List<CombinationFinderTask> subTasks = new ArrayList<>();
            for (int i = startIdx; i < candidates.size(); i++) {
                Set<IConflictRecipe> nextCombination = new HashSet<>(currentCombination);
                nextCombination.add(candidates.get(i));
                CombinationFinderTask task = new CombinationFinderTask(mandatoryRecipe, candidates, globalScope, i + 1, k, nextCombination);
                task.fork();
                subTasks.add(task);
            }
            for (CombinationFinderTask task : subTasks) {
                if (task.isCancelled()) continue;
                Optional<Set<IConflictRecipe>> result = task.join();
                if (result.isPresent()) {
                    for (CombinationFinderTask otherTask : subTasks) otherTask.cancel(true);
                    return result;
                }
            }
            return Optional.empty();
        }

        private Optional<Set<IConflictRecipe>> computeSequentially() {
            for (int i = startIdx; i < candidates.size(); i++) {
                currentCombination.add(candidates.get(i));
                Optional<Set<IConflictRecipe>> result = compute(); // 递归调用自身，但不再fork
                if (result.isPresent()) return result;
                currentCombination.remove(candidates.get(i));
            }
            return Optional.empty();
        }
    }

    // =================================================================================
    // == 辅助方法 (多重集操作)                                                       ==
    // =================================================================================

    private static boolean isMultisetSubset(Map<String, Integer> sub, Map<String, Integer> sup) {
        if (sub.isEmpty()) return true;
        for (Map.Entry<String, Integer> entry : sub.entrySet()) {
            if (sup.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    private static Map<String, Integer> unionInputs(Set<IConflictRecipe> set, IConflictRecipe exclude) {
        Map<String, Integer> union = new HashMap<>();
        for (IConflictRecipe recipe : set) {
            if (recipe.equals(exclude)) continue;
            recipe.getInputsAsMultiset().forEach((item, count) -> union.put(item, union.getOrDefault(item, 0) + count));
        }
        return union;
    }
}