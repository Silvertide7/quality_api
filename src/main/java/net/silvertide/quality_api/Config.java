package net.silvertide.quality_api;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class Config {
    public enum Inheritance { HIGHEST, LOWEST, AVERAGE, NONE }

    public enum TooltipStyle { BOTH, SYMBOLS, TEXT }

    public enum EntityQualityDisplay { NAMETAG, INSPECT, OFF }

    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Inheritance> CRAFT_INHERITANCE = COMMON
            .comment("How a crafted item's quality is derived from ingredients that carry quality.",
                    "HIGHEST/LOWEST pick that ingredient's tier, AVERAGE takes the mean level and rolls the fractional part up or down, NONE ignores ingredients.")
            .defineEnum("craftInheritance", Inheritance.AVERAGE);

    public static final ModConfigSpec.BooleanValue CRAFT_INHERITANCE_DAMAGEABLE_ONLY = COMMON
            .comment("Only crafted results with durability inherit quality from ingredients. Off lets planks, sticks and other stackables carry quality too.")
            .define("craftInheritanceDamageableOnly", true);

    public static final ModConfigSpec.DoubleValue DOWNGRADE_CHANCE = COMMON
            .comment("Chance that an inherited or rolled crafting quality drops one tier.")
            .defineInRange("downgradeChance", 0.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue ROLL_ON_CRAFT = COMMON
            .comment("Roll a weighted random quality for results crafted from ingredients without quality (damageable results only while craftInheritanceDamageableOnly is on).")
            .define("rollOnCraft", false);

    public static final ModConfigSpec.BooleanValue RECORD_CRAFTER = COMMON
            .comment("Record the crafting player's name on items that receive a quality when crafted.")
            .define("recordCrafter", true);

    public static final ModConfigSpec.EnumValue<Inheritance> BREED_INHERITANCE = COMMON
            .comment("How a bred animal's quality is derived from its parents when at least one has quality. A parent without quality counts as level 0.",
                    "HIGHEST/LOWEST take that parent's level, AVERAGE takes the mean level and rolls the fractional part up or down, NONE leaves the baby plain.",
                    "QualityBreedEvent is posted either way, so another mod can set NONE here and decide the baby's quality itself.")
            .defineEnum("breedInheritance", Inheritance.AVERAGE);

    public static final ModConfigSpec.BooleanValue BROKEN_INSTEAD_OF_DESTROYED = COMMON
            .comment("Quality items that reach zero durability become broken and unusable until repaired instead of being destroyed. Turning this off only affects future breaks: items already broken stay broken until repaired.")
            .define("brokenInsteadOfDestroyed", true);

    public static final ModConfigSpec.DoubleValue BREAK_DOWNGRADE_CHANCE = COMMON
            .comment("Chance that breaking drops the item one quality tier. A tier or item can override this with break_downgrade_chance.")
            .defineInRange("breakDowngradeChance", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue HIGHEST_TIER_CAN_DOWNGRADE = COMMON
            .comment("Whether items of the highest quality tier can lose their tier when broken.")
            .define("highestTierCanDowngrade", true);

    public static final ModConfigSpec.BooleanValue KEEP_PLAYER_QUALITY_ON_DEATH = COMMON
            .comment("Players keep their entity quality when they die and respawn. Off clears it on death. Mob conversions keep quality either way.")
            .define("keepPlayerQualityOnDeath", true);

    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue QUALITY_STACKS = SERVER
            .comment("Right-clicking an item onto the same item of another quality makes a quality stack, and picked-up items go into quality stacks you carry.",
                    "Off stops both; quality stacks that already exist can still be emptied and filled by hand, so nothing is trapped.")
            .define("qualityStacks", true);

    public static final ModConfigSpec SERVER_SPEC = SERVER.build();

    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_QUALITY_TOOLTIP = CLIENT
            .comment("Show the quality tier, crafter and food bonus lines in item tooltips, and the tier on mobs and players (see entityQualityDisplay).")
            .define("showQualityTooltip", true);

    public static final ModConfigSpec.BooleanValue SHOW_QUALITY_MARKER = CLIENT
            .comment("Draw a small star in the tier's colour in the top-left corner of every item with a quality, in inventories and the hotbar. Tiers in hiddenTooltipTiers get none.")
            .define("showQualityMarker", true);

    public static final ModConfigSpec.EnumValue<TooltipStyle> QUALITY_TOOLTIP_STYLE = CLIENT
            .comment("How an item's quality shows in tooltips: BOTH the tier name followed by stars, SYMBOLS the stars alone, or TEXT the name alone.",
                    "Stars are half a star per level above -2, at most five: crude is half a star, standard one star, masterwork three. Tiers at level -2 or below always show their name.")
            .defineEnum("qualityTooltipStyle", TooltipStyle.BOTH);

    public static final ModConfigSpec.EnumValue<EntityQualityDisplay> ENTITY_QUALITY_DISPLAY = CLIENT
            .comment("How a mob or player's quality shows, in the qualityTooltipStyle: NAMETAG looking at it shows its name and tier above its head,",
                    "INSPECT sneaking and right-clicking it with an empty main hand shows them above the hotbar, OFF neither.")
            .defineEnum("entityQualityDisplay", EntityQualityDisplay.NAMETAG);

    public static final ModConfigSpec.BooleanValue SHOW_ENTITY_QUALITY_PARTICLES = CLIENT
            .comment("Mobs and players whose tier sets a particle_color give off an occasional sparkle in that colour.")
            .define("showEntityQualityParticles", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_TOOLTIP_TIERS = CLIENT
            .comment("Quality tier ids whose name is left out of item tooltips, slot markers and mob and player labels, e.g. [\"quality_api:standard\"].")
            .defineListAllowEmpty("hiddenTooltipTiers", List.of(), () -> "", entry -> entry instanceof String id && id.indexOf(':') > 0 && ResourceLocation.tryParse(id) != null);

    private record HiddenTiers(List<? extends String> configured, Set<ResourceLocation> ids) {}

    private static volatile HiddenTiers hiddenTiers = new HiddenTiers(List.of(), Set.of());

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    public static boolean isTierHidden(ResourceLocation tier) {
        List<? extends String> configured = HIDDEN_TOOLTIP_TIERS.get();
        HiddenTiers cached = hiddenTiers;
        if (cached.configured() != configured) {
            cached = new HiddenTiers(configured, configured.stream().map(ResourceLocation::tryParse).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet()));
            hiddenTiers = cached;
        }
        return cached.ids().contains(tier);
    }

    private Config() {}
}
