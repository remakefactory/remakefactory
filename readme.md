<div >

**English** | [中文简体](./readme_zh_cn.md) 

</div>

# RemakeFactory

RemakeFactory is a powerful utility and quality-of-life mod designed for advanced players, automation experts, and modpack creators, primarily focused on enhancing the experience with **GregTech CEu (GTCEu)**. It provides a suite of tools to fix common recipe issues, test complex automation setups, and optimize production lines.

## Features

The mod's functionalities are divided into two main categories: **Recipe Hijacker** for real-time recipe modifications and the **Conflict Optimizer** for in-depth recipe analysis.

### 1. Recipe Hijacker

This feature allows for on-the-fly modifications to GTCEu recipes as they are used in systems like Applied Energistics 2 (AE2) pattern encoding. It helps fix common inconveniences and scale recipes without needing complex datapacks. All options are configurable in-game via the `/ref config` command.

*   **Remove Zero-Chance Inputs**: Automatically filters out input ingredients that have a 0% chance of being consumed (e.g., catalysts shown in JEI but not actually used). This cleans up patterns and prevents automation systems from requesting unnecessary items.
*   **Reverse Input Order**: Reverses the order of item inputs in a recipe. This is particularly useful for working around AE2's pattern encoding behavior.
*   **Scale Recipe IO**: Multiplies the inputs and outputs of a recipe by a configurable factor. Want to craft 64x of a component at once? Simply set the multiplier and encode the pattern to create large-batch crafting recipes instantly.
*   **Multiblock Placeholder Item**: A special creative-only item that acts as a placeholder for any GTCEu multiblock structure.

### 2. Recipe Conflict Optimizer

This is an advanced in-game analysis tool designed to solve the "Recipe Ambiguity" problem in complex automation. It provides a powerful command, `/ref conflict`, to statically analyze recipe sets and identify the largest possible group of recipes that can safely coexist in a single machine or system.

*   **Bookmark-based Workflow (`/ref conflict bookmarks`)**:
    *   Use your JEI recipe bookmarks as a "draft" for a production line.
    *   The command reads your bookmarked recipes and filters them by a specified mod (e.g., `gtceu`).
    *   It intelligently analyzes the set for all types of conflicts, including subtle combinatorial ones.
    *   **Single-Recipe Mode**: If one recipe is bookmarked, it explores all other recipes in the same machine to find the largest compatible set.
    *   **Multi-Recipe Mode**: If multiple recipes are bookmarked, it optimizes that specific list to find the largest conflict-free subset.
    *   The final, safe recipe list is exported to a new `bookmarks_optimized.ini` file, providing a "safe production manifest".

*   **On-the-fly Testing (`/ref conflict test`)**:
    *   A powerful utility for developers and pack makers to quickly test any combination of recipes by providing their full IDs.
    *   It reports back whether the given set is conflict-free or what the optimized result would be.

*   **Advanced Features**: The optimizer supports both a fast, greedy algorithm for large-scale exploration and a thorough, multithreaded backtracking algorithm to guarantee optimal results for complex sets.

## Usage


*   **Configuration**: `/ref config recipe_hijacker gtceu <option> [value]`
    *   Example: `/ref config recipe_hijacker gtceu enable true`
*   **Conflict Analysis**: `/ref conflict <bookmarks|test> ...`
    *   Example: `/ref conflict bookmarks "config/jei/world/local/dev/bookmarks.ini" gtceu false`

For detailed command syntax, use the in-game auto-completion.