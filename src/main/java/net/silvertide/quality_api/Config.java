package net.silvertide.quality_api;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public enum Inheritance { HIGHEST, LOWEST, AVERAGE, NONE }

    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Inheritance> CRAFT_INHERITANCE = COMMON
            .comment("How a crafted item's quality is derived from ingredients that carry quality.",
                    "HIGHEST/LOWEST pick that ingredient's tier, AVERAGE takes the mean level and rolls the fractional part up or down, NONE ignores ingredients.")
            .defineEnum("craftInheritance", Inheritance.HIGHEST);

    public static final ModConfigSpec.BooleanValue CRAFT_INHERITANCE_DAMAGEABLE_ONLY = COMMON
            .comment("Only crafted results with durability inherit quality from ingredients. Off lets planks, sticks and other stackables carry quality too.")
            .define("craftInheritanceDamageableOnly", true);

    public static final ModConfigSpec.DoubleValue DOWNGRADE_CHANCE = COMMON
            .comment("Chance that an inherited or rolled crafting quality drops one tier.")
            .defineInRange("downgradeChance", 0.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue ROLL_ON_CRAFT = COMMON
            .comment("Roll a weighted random quality for damageable items crafted from ingredients without quality.")
            .define("rollOnCraft", false);

    public static final ModConfigSpec.BooleanValue RECORD_CRAFTER = COMMON
            .comment("Record the crafting player's name on items that receive a quality when crafted.")
            .define("recordCrafter", true);

    public static final ModConfigSpec.BooleanValue BROKEN_INSTEAD_OF_DESTROYED = COMMON
            .comment("Quality items that reach zero durability become broken and unusable until repaired instead of being destroyed.")
            .define("brokenInsteadOfDestroyed", true);

    public static final ModConfigSpec.DoubleValue BREAK_DOWNGRADE_CHANCE = COMMON
            .comment("Chance that breaking drops the item one quality tier. A tier or item can override this with break_downgrade_chance.")
            .defineInRange("breakDowngradeChance", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue HIGHEST_TIER_CAN_DOWNGRADE = COMMON
            .comment("Whether items of the highest quality tier can lose their tier when broken.")
            .define("highestTierCanDowngrade", true);

    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_QUALITY_TOOLTIP = CLIENT
            .comment("Show the quality tier and crafter lines in item tooltips.")
            .define("showQualityTooltip", true);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    private Config() {}
}
