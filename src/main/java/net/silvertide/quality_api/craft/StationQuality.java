package net.silvertide.quality_api.craft;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityProcessEvent;
import net.silvertide.quality_api.api.QualityResultTakenEvent;
import net.silvertide.quality_api.stack.QualityStackContents;
import net.silvertide.quality_api.stack.QualityStacks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public final class StationQuality {
    private StationQuality() {}

    public static ItemStack decide(Level level, @Nullable BlockPos stationPos, RecipeInput input, ItemStack result) {
        List<ItemStack> ingredients = new ArrayList<>(input.size());
        for (int slot = 0; slot < input.size(); slot++) ingredients.add(input.getItem(slot));
        return decide(level, stationPos, ingredients, result);
    }

    public static ItemStack decide(Level level, @Nullable BlockPos stationPos, List<ItemStack> ingredients, ItemStack result) {
        if (result.isEmpty() || level.isClientSide()) return result;
        OptionalDouble meanLevel = Qualities.meanLevel(ingredients);
        ResourceLocation proposed = Qualities.getId(result).orElseGet(() -> inherit(ingredients, meanLevel, result));
        QualityProcessEvent event = new QualityProcessEvent(level, stationPos, List.copyOf(ingredients), result, meanLevel, proposed);
        NeoForge.EVENT_BUS.post(event);
        stamp(result, event.getQuality());
        return result;
    }

    public static ItemStack taken(@Nullable Player player, ItemStack result) {
        if (result.isEmpty() || !(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) return result;
        QualityStackContents pooled = QualityStacks.contents(result);
        if (pooled == null) {
            if (decideTakenQuality(serverPlayer, result)) recordCrafter(serverPlayer, result);
            return result;
        }
        List<ItemStack> tiers = pooled.asStacks();
        boolean everyTierHasQuality = true;
        for (ItemStack tier : tiers) everyTierHasQuality &= decideTakenQuality(serverPlayer, tier);
        if (everyTierHasQuality) tiers.forEach(tier -> recordCrafter(serverPlayer, tier));
        QualityStacks.replaceContents(result, tiers);
        return result;
    }

    private static boolean decideTakenQuality(ServerPlayer taker, ItemStack result) {
        QualityResultTakenEvent event = new QualityResultTakenEvent(taker, result, Qualities.getId(result).orElse(null));
        NeoForge.EVENT_BUS.post(event);
        return stamp(result, event.getQuality());
    }

    private static void recordCrafter(ServerPlayer taker, ItemStack result) {
        if (Config.RECORD_CRAFTER.get() && !result.has(QualityAPI.CRAFTER.get())) {
            result.set(QualityAPI.CRAFTER.get(), taker.getGameProfile().getName());
        }
    }

    public static boolean poolsWithOutput(ItemStack output, ItemStack result) {
        if (!Config.QUALITY_STACKS.get() || ItemStack.isSameItemSameComponents(output, result)) return false;
        QualityStackContents pooled = QualityStacks.contents(output);
        return pooled != null ? pooled.accepts(result) : QualityStacks.canPool(output, result);
    }

    public static int roomInPooledOutput(ItemStack output) {
        QualityStackContents pooled = QualityStacks.contents(output);
        return pooled != null ? pooled.capacity() - pooled.total() : output.getMaxStackSize() - output.getCount();
    }

    public static ItemStack pooledOutput(ItemStack output, ItemStack moving, int maxCount) {
        int moved = Math.min(Math.min(moving.getCount(), maxCount), roomInPooledOutput(output));
        if (moved <= 0) return output;
        ItemStack movedStack = moving.split(moved);
        if (!QualityStacks.isQualityStack(output)) return QualityStacks.poolIntoNewQualityStack(output, movedStack);
        QualityStacks.insert(output, movedStack);
        return output;
    }

    @Nullable
    private static ResourceLocation inherit(List<ItemStack> ingredients, OptionalDouble meanLevel, ItemStack result) {
        if (meanLevel.isEmpty()) return null;
        if (Config.STATION_INHERITANCE_DAMAGEABLE_ONLY.get() && !result.isDamageableItem()) return null;
        ResourceLocation inherited = switch (Config.CRAFT_INHERITANCE.get()) {
            case HIGHEST -> CraftInheritance.IngredientTiers.of(ingredients).highest();
            case LOWEST -> CraftInheritance.IngredientTiers.of(ingredients).lowest();
            case AVERAGE -> Qualities.forLevel(result, (int) Math.round(meanLevel.getAsDouble())).orElse(null);
            case NONE -> null;
        };
        return inherited == null ? null : CraftInheritance.nearestTierTheResultCanTake(result, inherited);
    }

    private static boolean stamp(ItemStack result, @Nullable ResourceLocation chosen) {
        if (chosen == null) {
            if (result.has(QualityAPI.QUALITY.get())) Qualities.remove(result);
            return false;
        }
        return Qualities.apply(result, chosen);
    }

    public static boolean notMergingOntoCarried(Player clicker) {
        return clicker.containerMenu.getCarried().isEmpty();
    }

    public static boolean hasAnEmptySlot(AbstractContainerMenu menu, int start, int end) {
        return menu.slots.subList(start, end).stream().anyMatch(slot -> !slot.hasItem());
    }
}
