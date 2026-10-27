package net.silvertide.quality_api.quality;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;

public record QualityEffects(
        Optional<List<AttributeEffect>> attributes,
        Optional<Float> miningSpeedMultiplier,
        Optional<Map<ResourceKey<Enchantment>, Integer>> enchantmentLevels,
        Optional<Float> durabilityMultiplier,
        Optional<Float> breakDowngradeChance,
        Optional<Float> breakDestroyChance,
        Optional<Float> nutritionMultiplier,
        Optional<Float> saturationMultiplier,
        Optional<Float> eatEffectDurationMultiplier,
        Optional<List<EatEffect>> eatEffects) {

    public static final QualityEffects EMPTY = new QualityEffects(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static final Codec<Float> NON_NEGATIVE_MULTIPLIER = Codec.floatRange(0.0F, Float.MAX_VALUE);

    public static final Codec<QualityEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AttributeEffect.CODEC.listOf().optionalFieldOf("attributes").forGetter(QualityEffects::attributes),
            Codec.FLOAT.optionalFieldOf("mining_speed_multiplier").forGetter(QualityEffects::miningSpeedMultiplier),
            Codec.unboundedMap(ResourceKey.codec(Registries.ENCHANTMENT), Codec.intRange(1, 255)).optionalFieldOf("enchantment_levels").forGetter(QualityEffects::enchantmentLevels),
            Codec.FLOAT.optionalFieldOf("durability_multiplier").forGetter(QualityEffects::durabilityMultiplier),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("break_downgrade_chance").forGetter(QualityEffects::breakDowngradeChance),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("break_destroy_chance").forGetter(QualityEffects::breakDestroyChance),
            NON_NEGATIVE_MULTIPLIER.optionalFieldOf("nutrition_multiplier").forGetter(QualityEffects::nutritionMultiplier),
            NON_NEGATIVE_MULTIPLIER.optionalFieldOf("saturation_multiplier").forGetter(QualityEffects::saturationMultiplier),
            NON_NEGATIVE_MULTIPLIER.optionalFieldOf("eat_effect_duration_multiplier").forGetter(QualityEffects::eatEffectDurationMultiplier),
            EatEffect.CODEC.listOf().optionalFieldOf("eat_effects").forGetter(QualityEffects::eatEffects)
    ).apply(instance, QualityEffects::new));

    public static final Codec<Map<ResourceLocation, QualityEffects>> OVERRIDES_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, CODEC);

    public QualityEffects patchedWith(QualityEffects patch) {
        return new QualityEffects(
                patch.attributes.or(() -> attributes),
                patch.miningSpeedMultiplier.or(() -> miningSpeedMultiplier),
                patch.enchantmentLevels.or(() -> enchantmentLevels),
                patch.durabilityMultiplier.or(() -> durabilityMultiplier),
                patch.breakDowngradeChance.or(() -> breakDowngradeChance),
                patch.breakDestroyChance.or(() -> breakDestroyChance),
                patch.nutritionMultiplier.or(() -> nutritionMultiplier),
                patch.saturationMultiplier.or(() -> saturationMultiplier),
                patch.eatEffectDurationMultiplier.or(() -> eatEffectDurationMultiplier),
                patch.eatEffects.or(() -> eatEffects));
    }

    public static Map<ResourceLocation, QualityEffects> mergeOverrides(
            Registry<Item> registry,
            Either<TagKey<Item>, ResourceKey<Item>> firstSource, Map<ResourceLocation, QualityEffects> first,
            Either<TagKey<Item>, ResourceKey<Item>> secondSource, Map<ResourceLocation, QualityEffects> second) {
        return mergePatchesByTier(firstSource, first, secondSource, second, QualityEffects::patchedWith);
    }

    public static <T, E> Map<ResourceLocation, E> mergePatchesByTier(
            Either<TagKey<T>, ResourceKey<T>> firstSource, Map<ResourceLocation, E> first,
            Either<TagKey<T>, ResourceKey<T>> secondSource, Map<ResourceLocation, E> second,
            BinaryOperator<E> patchedWith) {
        boolean tagEntryArrivedAfterElementEntry = firstSource.right().isPresent() && secondSource.left().isPresent();
        Map<ResourceLocation, E> base = tagEntryArrivedAfterElementEntry ? second : first;
        Map<ResourceLocation, E> patches = tagEntryArrivedAfterElementEntry ? first : second;
        Map<ResourceLocation, E> merged = new HashMap<>(base);
        patches.forEach((tier, patch) -> merged.merge(tier, patch, patchedWith));
        return Map.copyOf(merged);
    }

    public record AttributeEffect(Holder<Attribute> type, AttributeModifier.Operation operation, double amount, Optional<EquipmentSlotGroup> slot) {
        public static final Codec<AttributeEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Attribute.CODEC.fieldOf("type").forGetter(AttributeEffect::type),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(AttributeEffect::operation),
                Codec.DOUBLE.fieldOf("amount").forGetter(AttributeEffect::amount),
                EquipmentSlotGroup.CODEC.optionalFieldOf("slot").forGetter(AttributeEffect::slot)
        ).apply(instance, AttributeEffect::new));
    }

    public record EatEffect(Holder<MobEffect> effect, int duration, int amplifier, float probability) {
        public static final Codec<EatEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(EatEffect::effect),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(EatEffect::duration),
                Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(EatEffect::amplifier),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("probability", 1.0F).forGetter(EatEffect::probability)
        ).apply(instance, EatEffect::new));
    }
}
