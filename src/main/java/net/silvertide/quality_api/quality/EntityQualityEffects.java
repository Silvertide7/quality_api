package net.silvertide.quality_api.quality;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record EntityQualityEffects(Optional<List<EntityAttributeEffect>> attributes, Optional<TextColor> tint) {

    public static final Codec<EntityQualityEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityAttributeEffect.CODEC.listOf().optionalFieldOf("attributes").forGetter(EntityQualityEffects::attributes),
            TextColor.CODEC.optionalFieldOf("tint").forGetter(EntityQualityEffects::tint)
    ).apply(instance, EntityQualityEffects::new));

    public static final Codec<Map<ResourceLocation, EntityQualityEffects>> OVERRIDES_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, CODEC);

    public EntityQualityEffects patchedWith(EntityQualityEffects patch) {
        return new EntityQualityEffects(patch.attributes.or(() -> attributes), patch.tint.or(() -> tint));
    }

    public static Map<ResourceLocation, EntityQualityEffects> mergeOverrides(
            Registry<EntityType<?>> registry,
            Either<TagKey<EntityType<?>>, ResourceKey<EntityType<?>>> firstSource, Map<ResourceLocation, EntityQualityEffects> first,
            Either<TagKey<EntityType<?>>, ResourceKey<EntityType<?>>> secondSource, Map<ResourceLocation, EntityQualityEffects> second) {
        return QualityEffects.mergePatchesByTier(firstSource, first, secondSource, second, EntityQualityEffects::patchedWith);
    }

    public record EntityAttributeEffect(Holder<Attribute> type, AttributeModifier.Operation operation, double amount) {
        public static final Codec<EntityAttributeEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Attribute.CODEC.fieldOf("type").forGetter(EntityAttributeEffect::type),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(EntityAttributeEffect::operation),
                Codec.DOUBLE.fieldOf("amount").forGetter(EntityAttributeEffect::amount)
        ).apply(instance, EntityAttributeEffect::new));
    }
}
