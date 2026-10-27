package net.silvertide.quality_api.api;

import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.quality.EntityQualityModifiers;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.Snapshot;
import net.silvertide.silverlib.feature.breakage.BrokenItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class Qualities {
    private Qualities() {}

    public static Optional<ResourceLocation> getId(ItemStack stack) {
        return Optional.ofNullable(stack.get(QualityAPI.QUALITY.get())).filter(Snapshot.current().tiers()::containsKey);
    }

    public static Optional<Quality> get(ItemStack stack) {
        ResourceLocation id = stack.get(QualityAPI.QUALITY.get());
        return id == null ? Optional.empty() : Optional.ofNullable(Snapshot.current().tiers().get(id));
    }

    public static Optional<ResourceLocation> getId(Entity entity) {
        return entity.getExistingData(QualityAPI.ENTITY_QUALITY).filter(Snapshot.current().tiers()::containsKey);
    }

    public static Optional<Quality> get(Entity entity) {
        return entity.getExistingData(QualityAPI.ENTITY_QUALITY).map(Snapshot.current().tiers()::get);
    }

    public static Optional<Quality> get(ResourceLocation id) {
        return Optional.ofNullable(Snapshot.current().tiers().get(id));
    }

    public static int level(ItemStack stack) {
        return get(stack).map(Quality::level).orElse(0);
    }

    public static int level(Entity entity) {
        return get(entity).map(Quality::level).orElse(0);
    }

    public static int levelOf(ResourceLocation id) {
        return get(id).map(Quality::level).orElse(0);
    }

    public static boolean canApply(ItemStack stack, ResourceLocation id) {
        Quality quality = Snapshot.current().tiers().get(id);
        return quality != null && !stack.isEmpty() && !stack.is(QualityAPI.BLACKLIST) && quality.appliesTo(stack);
    }

    public static boolean apply(ItemStack stack, ResourceLocation id) {
        if (!canApply(stack, id)) return false;
        int previousMax = stack.getMaxDamage();
        stack.set(QualityAPI.QUALITY.get(), id);
        rescaleDamage(stack, previousMax);
        return true;
    }

    public static void remove(ItemStack stack) {
        int previousMax = stack.getMaxDamage();
        stack.remove(QualityAPI.QUALITY.get());
        rescaleDamage(stack, previousMax);
    }

    public static boolean apply(Entity entity, ResourceLocation id) {
        if (get(id).isEmpty()) return false;
        entity.setData(QualityAPI.ENTITY_QUALITY, id);
        EntityQualityModifiers.refresh(entity);
        return true;
    }

    public static void remove(Entity entity) {
        entity.removeData(QualityAPI.ENTITY_QUALITY);
        EntityQualityModifiers.refresh(entity);
    }

    private static void rescaleDamage(ItemStack stack, int previousMax) {
        int newMax = stack.getMaxDamage();
        int damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        if (previousMax <= 0 || newMax <= 0 || previousMax == newMax || damage <= 0) return;
        boolean broken = damage >= previousMax;
        int scaled = Math.round((float) damage * newMax / previousMax);
        stack.set(DataComponents.DAMAGE, broken ? newMax : Math.min(newMax - 1, scaled));
    }

    public static boolean isBroken(ItemStack stack) {
        return getId(stack).isPresent() && BrokenItems.isBroken(stack);
    }

    public static FoodProperties applyFoodEffects(ItemStack food, FoodProperties original) {
        ResourceLocation tier = food.get(QualityAPI.QUALITY.get());
        if (tier == null) return original;
        Snapshot.Resolved resolved = Snapshot.current().resolved(food.getItem(), tier);
        if (resolved == null || !resolved.changesFood(original) || isBroken(food)) return original;
        return resolved.applyTo(original);
    }

    public static boolean isHighestLevel(ItemStack stack, ResourceLocation id) {
        return get(id).isPresent() && applicableLevels(stack).stream().noneMatch(level -> level > levelOf(id));
    }

    public static Optional<ResourceLocation> roll(ItemStack stack, RandomSource random) {
        List<ResourceLocation> applicable = applicable(stack);
        int totalWeight = 0;
        for (ResourceLocation id : applicable) totalWeight += weightOf(id);
        if (totalWeight <= 0) return Optional.empty();
        int roll = random.nextInt(totalWeight);
        for (ResourceLocation id : applicable) {
            roll -= weightOf(id);
            if (roll < 0) return Optional.of(id);
        }
        return Optional.empty();
    }

    public static Optional<ResourceLocation> forLevel(ItemStack stack, int level) {
        ResourceLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ResourceLocation id : applicable(stack)) {
            int distance = Math.abs(levelOf(id) - level);
            if (distance < bestDistance) {
                best = id;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<ResourceLocation> forLevel(int level) {
        Snapshot snapshot = Snapshot.current();
        for (ResourceLocation id : snapshot.idsByLevel()) {
            Quality quality = snapshot.tiers().get(id);
            if (quality.level() == level && quality.items().isEmpty()) return Optional.of(id);
        }
        return Optional.empty();
    }

    public static Optional<ResourceLocation> forLevel(ItemStack stack, double level, RandomSource random) {
        return forLevel(stack, rollFraction(level, random));
    }

    public static Optional<ResourceLocation> forLevel(double level, RandomSource random) {
        return forLevel(rollFraction(level, random));
    }

    private static int rollFraction(double level, RandomSource random) {
        int lower = (int) Math.floor(level);
        return random.nextDouble() < level - lower ? lower + 1 : lower;
    }

    public static OptionalDouble meanLevel(Iterable<ItemStack> stacks) {
        int sum = 0;
        int count = 0;
        for (ItemStack stack : stacks) {
            Quality quality = get(stack).orElse(null);
            if (quality == null) continue;
            sum += quality.level();
            count++;
        }
        return count == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) sum / count);
    }

    public static Optional<ResourceLocation> shift(ItemStack stack, ResourceLocation id, int steps) {
        if (get(id).isEmpty() || steps == 0) return Optional.empty();
        int current = levelOf(id);
        List<Integer> levelsInDirection = steps > 0
                ? applicableLevels(stack).stream().filter(level -> level > current).toList()
                : applicableLevels(stack).stream().filter(level -> level < current).toList().reversed();
        int stepsAway = Math.abs(steps) - 1;
        if (stepsAway >= levelsInDirection.size()) return Optional.empty();
        return firstApplicableAtLevel(stack, levelsInDirection.get(stepsAway));
    }

    public static List<ResourceLocation> idsByLevel() {
        return Snapshot.current().idsByLevel();
    }

    public static Component displayName(ResourceLocation id) {
        return styled(id, Component.translatable(Util.makeDescriptionId("quality", id)));
    }

    public static Component displayName(ResourceLocation id, String nameSet) {
        String defaultKey = Util.makeDescriptionId("quality", id);
        String nameSetKey = defaultKey + "." + nameSet;
        return styled(id, Component.translatable(Language.getInstance().has(nameSetKey) ? nameSetKey : defaultKey));
    }

    public static Component displayName(ItemStack stack, ResourceLocation id) {
        return nameSet(stack).map(nameSet -> displayName(id, nameSet)).orElseGet(() -> displayName(id));
    }

    public static Component displayName(Entity entity, ResourceLocation id) {
        return nameSet(entity).map(nameSet -> displayName(id, nameSet)).orElseGet(() -> displayName(id));
    }

    public static Optional<String> nameSet(ItemStack stack) {
        return Optional.ofNullable(Snapshot.current().nameSets().get(stack.getItem()));
    }

    public static Optional<String> nameSet(Entity entity) {
        return Optional.ofNullable(Snapshot.current().entityNameSets().get(entity.getType()));
    }

    private static Component styled(ResourceLocation id, MutableComponent name) {
        return get(id).map(quality -> name.withStyle(style -> style.withColor(quality.color()))).orElse(name);
    }

    private static int weightOf(ResourceLocation id) {
        return get(id).map(Quality::weight).orElse(0);
    }

    private static List<ResourceLocation> applicable(ItemStack stack) {
        List<ResourceLocation> applicable = new ArrayList<>();
        for (ResourceLocation id : Snapshot.current().idsByLevel()) {
            if (canApply(stack, id)) applicable.add(id);
        }
        return applicable;
    }

    private static List<Integer> applicableLevels(ItemStack stack) {
        return applicable(stack).stream().map(Qualities::levelOf).distinct().toList();
    }

    private static Optional<ResourceLocation> firstApplicableAtLevel(ItemStack stack, int level) {
        return applicable(stack).stream().filter(id -> levelOf(id) == level).findFirst();
    }
}
