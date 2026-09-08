package net.silvertide.quality_api.craft;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityCraftEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

public final class CraftInheritance {
    private CraftInheritance() {}

    public static void applyToResult(ServerPlayer player, CraftingContainer grid, ResultContainer resultSlots, ItemStack result) {
        RecipeHolder<?> recipe = resultSlots.getRecipeUsed();
        boolean repairedByCombining = recipe != null && recipe.value() instanceof RepairItemRecipe;
        int remainingDurability = result.getMaxDamage() - result.getDamageValue();
        if (repairedByCombining) resetMaxDamageCopiedFromIngredients(result);

        CraftingInput input = grid.asCraftInput();
        OptionalDouble meanLevel = Qualities.meanLevel(input.items());
        ResourceLocation proposed = result.has(QualityAPI.QUALITY.get()) ? result.get(QualityAPI.QUALITY.get()) : inherit(player, input, meanLevel, result);
        ResourceLocation chosen = letListenersDecide(player, recipe, input, result, meanLevel, proposed);
        stamp(player, result, chosen);

        if (repairedByCombining) result.set(DataComponents.DAMAGE, Math.max(0, result.getMaxDamage() - remainingDurability));
    }

    @Nullable
    private static ResourceLocation inherit(ServerPlayer player, CraftingInput input, OptionalDouble meanLevel, ItemStack result) {
        if (Config.CRAFT_INHERITANCE_DAMAGEABLE_ONLY.get() && !result.isDamageableItem()) return null;
        RandomSource random = seededFor(player, result);
        ResourceLocation base = meanLevel.isEmpty()
                ? rollIfConfigured(result, random)
                : chooseFromIngredients(input, meanLevel.getAsDouble(), result, random);
        if (base == null) return null;
        if (!Qualities.canApply(result, base)) base = Qualities.forLevel(result, Qualities.levelOf(base)).orElse(null);
        if (base != null && random.nextDouble() < Config.DOWNGRADE_CHANCE.get()) base = Qualities.shift(result, base, -1).orElse(base);
        return base;
    }

    @Nullable
    private static ResourceLocation rollIfConfigured(ItemStack result, RandomSource random) {
        if (!Config.ROLL_ON_CRAFT.get() || !result.isDamageableItem()) return null;
        return Qualities.roll(result, random).orElse(null);
    }

    @Nullable
    private static ResourceLocation chooseFromIngredients(CraftingInput input, double meanLevel, ItemStack result, RandomSource random) {
        IngredientTiers tiers = IngredientTiers.of(input);
        return switch (Config.CRAFT_INHERITANCE.get()) {
            case HIGHEST -> tiers.highest();
            case LOWEST -> tiers.lowest();
            case AVERAGE -> Qualities.forLevel(result, meanLevel, random).orElse(null);
            case NONE -> null;
        };
    }

    private record IngredientTiers(@Nullable ResourceLocation highest, @Nullable ResourceLocation lowest) {
        static IngredientTiers of(CraftingInput input) {
            ResourceLocation highest = null;
            ResourceLocation lowest = null;
            for (int slot = 0; slot < input.size(); slot++) {
                Optional<ResourceLocation> tier = Qualities.getId(input.getItem(slot));
                if (tier.isEmpty()) continue;
                int level = Qualities.levelOf(tier.get());
                if (highest == null || level > Qualities.levelOf(highest)) highest = tier.get();
                if (lowest == null || level < Qualities.levelOf(lowest)) lowest = tier.get();
            }
            return new IngredientTiers(highest, lowest);
        }
    }

    @Nullable
    private static ResourceLocation letListenersDecide(ServerPlayer player, @Nullable RecipeHolder<?> recipe, CraftingInput input,
                                                       ItemStack result, OptionalDouble meanLevel, @Nullable ResourceLocation proposed) {
        QualityCraftEvent event = new QualityCraftEvent(player, recipe, input, result, meanLevel, proposed);
        NeoForge.EVENT_BUS.post(event);
        return event.getQuality();
    }

    private static void stamp(ServerPlayer player, ItemStack result, @Nullable ResourceLocation chosen) {
        if (chosen == null) {
            if (result.has(QualityAPI.QUALITY.get())) Qualities.remove(result);
            return;
        }
        if (Qualities.apply(result, chosen) && shouldRecordCrafter(player, result)) {
            result.set(QualityAPI.CRAFTER.get(), player.getGameProfile().getName());
        }
    }

    private static boolean shouldRecordCrafter(ServerPlayer player, ItemStack result) {
        return Config.RECORD_CRAFTER.get() && !result.has(QualityAPI.CRAFTER.get()) && !(player instanceof FakePlayer);
    }

    private static RandomSource seededFor(ServerPlayer player, ItemStack result) {
        if (player instanceof FakePlayer) return player.serverLevel().getRandom();
        int timesCrafted = player.getStats().getValue(Stats.ITEM_CRAFTED.get(result.getItem()));
        long seed = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
        seed = seed * 31 + BuiltInRegistries.ITEM.getKey(result.getItem()).hashCode();
        seed = seed * 31 + timesCrafted;
        return RandomSource.create(seed);
    }

    private static void resetMaxDamageCopiedFromIngredients(ItemStack result) {
        Integer prototypeMax = result.getItem().components().get(DataComponents.MAX_DAMAGE);
        if (prototypeMax != null) result.set(DataComponents.MAX_DAMAGE, prototypeMax);
    }
}
