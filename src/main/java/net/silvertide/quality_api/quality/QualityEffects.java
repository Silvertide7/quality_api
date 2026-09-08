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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record QualityEffects(
        Optional<List<AttributeEffect>> attributes,
        Optional<Float> miningSpeedMultiplier,
        Optional<Map<ResourceKey<Enchantment>, Integer>> enchantmentLevels,
        Optional<Float> durabilityMultiplier,
        Optional<Float> breakDowngradeChance,
        Optional<Float> breakDestroyChance) {

    public static final QualityEffects EMPTY = new QualityEffects(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<QualityEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AttributeEffect.CODEC.listOf().optionalFieldOf("attributes").forGetter(QualityEffects::attributes),
            Codec.FLOAT.optionalFieldOf("mining_speed_multiplier").forGetter(QualityEffects::miningSpeedMultiplier),
            Codec.unboundedMap(ResourceKey.codec(Registries.ENCHANTMENT), Codec.intRange(1, 255)).optionalFieldOf("enchantment_levels").forGetter(QualityEffects::enchantmentLevels),
            Codec.FLOAT.optionalFieldOf("durability_multiplier").forGetter(QualityEffects::durabilityMultiplier),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("break_downgrade_chance").forGetter(QualityEffects::breakDowngradeChance),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("break_destroy_chance").forGetter(QualityEffects::breakDestroyChance)
    ).apply(instance, QualityEffects::new));

    public static final Codec<Map<ResourceLocation, QualityEffects>> OVERRIDES_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, CODEC);

    public QualityEffects patchedWith(QualityEffects patch) {
        return new QualityEffects(
                patch.attributes.or(() -> attributes),
                patch.miningSpeedMultiplier.or(() -> miningSpeedMultiplier),
                patch.enchantmentLevels.or(() -> enchantmentLevels),
                patch.durabilityMultiplier.or(() -> durabilityMultiplier),
                patch.breakDowngradeChance.or(() -> breakDowngradeChance),
                patch.breakDestroyChance.or(() -> breakDestroyChance));
    }

    public static Map<ResourceLocation, QualityEffects> mergeOverrides(
            Registry<Item> registry,
            Either<TagKey<Item>, ResourceKey<Item>> firstSource, Map<ResourceLocation, QualityEffects> first,
            Either<TagKey<Item>, ResourceKey<Item>> secondSource, Map<ResourceLocation, QualityEffects> second) {
        boolean tagEntryArrivedAfterItemEntry = firstSource.right().isPresent() && secondSource.left().isPresent();
        Map<ResourceLocation, QualityEffects> base = tagEntryArrivedAfterItemEntry ? second : first;
        Map<ResourceLocation, QualityEffects> patches = tagEntryArrivedAfterItemEntry ? first : second;
        Map<ResourceLocation, QualityEffects> merged = new HashMap<>(base);
        patches.forEach((tier, patch) -> merged.merge(tier, patch, QualityEffects::patchedWith));
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
}
