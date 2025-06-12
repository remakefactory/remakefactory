package com.remakefactory.remakefactory.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Manages and holds all configuration values for the RemakeFactory mod.
 * This class uses ForgeConfigSpec to create and manage the common configuration file,
 * which is synchronized between the server and clients. The settings are organized
 * into logical nested classes for clarity and ease of use.
 */
public final class Config {

    // Common config is used for settings that need to be the same on both the
    // logical server and all connected clients, such as recipe behavior.
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;

    static {
        // ForgeConfigSpec.Builder is used to construct the configuration file's layout and settings.
        // We use a Pair to hold both the config class instance and the spec itself.
        final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    /**
     * Container for all common configuration options. The settings here will be
     * located at the root of the configuration file.
     */
    public static final class CommonConfig {
        public final RecipeHijacker recipeHijacker;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            this.recipeHijacker = new RecipeHijacker(builder);
        }
    }

    /**
     * Configuration for recipe modification features.
     * This section allows for fine-tuned control over how recipes are handled and displayed.
     */
    public static final class RecipeHijacker {
        public final ForgeConfigSpec.BooleanValue enable;
        public final Gtceu gtceu;

        public RecipeHijacker(ForgeConfigSpec.Builder builder) {
            builder.comment("Core settings for the recipe modification system.")
                    .translation("config.remakefactory.recipe_hijacker")
                    .push("recipe_hijacker");

            this.enable = builder
                    .comment("Master switch for all recipe modification features. If false, no recipes will be altered by this mod.")
                    .translation("config.remakefactory.recipe_hijacker.enable")
                    .define("enable", true);

            this.gtceu = new Gtceu(builder);

            builder.pop();
        }
    }

    /**
     * Specific settings for GregTech Community Edition Unofficial (GTCEu) recipes.
     */
    public static final class Gtceu {
        public final ForgeConfigSpec.BooleanValue enable;
        public final ForgeConfigSpec.BooleanValue reorder;
        public final ForgeConfigSpec.BooleanValue multiBlock;
        public final ForgeConfigSpec.IntValue scalingMultiplier;
        public final ForgeConfigSpec.BooleanValue filter;

        public Gtceu(ForgeConfigSpec.Builder builder) {
            builder.comment("Settings specifically for GregTech CEu recipes.")
                    .translation("config.remakefactory.gtceu")
                    .push("gtceu");

            this.enable = builder
                    .comment("Enable or disable all modifications for GTCEu recipes.")
                    .translation("config.remakefactory.gtceu.enable")
                    .define("enable", true);

            this.reorder = builder
                    .comment("Enable reordering of recipe inputs/outputs to improve compatibility with other mods.")
                    .translation("config.remakefactory.gtceu.reorder.enable")
                    .define("reorder", true);

            this.multiBlock = builder
                    .comment("Enable special handling and adjustments for GTCEu multi-block machine recipes.")
                    .translation("config.remakefactory.gtceu.multi_block.enable")
                    .define("multiBlock", true);

            this.scalingMultiplier = builder
                    .comment("A universal multiplier for certain recipe parameters (e.g., duration or energy cost). Used for balancing.")
                    .translation("config.remakefactory.gtceu.scaling_multiplier")
                    .defineInRange("scalingMultiplier", 1, 1, 100000);

            this.filter = builder
                    .comment("If true, enables recipe filtering logic. The specifics of what is filtered are handled in the mod's code.")
                    .translation("config.remakefactory.gtceu.filter.enable")
                    .define("enable", true);
            builder.pop();
        }
    }
    private Config() {}
}