package net.silvertide.quality_api.quality;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.silvertide.quality_api.QualityAPI;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record Snapshot(
        Map<ResourceLocation, Quality> tiers,
        List<ResourceLocation> idsByLevel,
        Map<ResourceLocation, Resolved> base,
        Map<Item, Map<ResourceLocation, Resolved>> overrides,
        Map<Item, String> nameSets,
        Map<EntityType<?>, String> entityNameSets,
        Map<EntityType<?>, Map<ResourceLocation, Multimap<Holder<Attribute>, AttributeModifier>>> entityModifiers,
        Map<EntityType<?>, Map<ResourceLocation, Integer>> entityTints) {

    public static final int UNTINTED = 0xFFFFFFFF;
    public static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    private static volatile Snapshot current = EMPTY;

    public static Snapshot current() {
        return current;
    }

    public static void rebuild(RegistryAccess access) {
        Optional<Registry<Quality>> registry = access.registry(QualityAPI.QUALITY_REGISTRY);
        if (registry.isEmpty()) {
            current = EMPTY;
            return;
        }
        Map<ResourceLocation, Quality> tiers = new HashMap<>();
        registry.get().entrySet().forEach(entry -> tiers.put(entry.getKey().location(), entry.getValue()));

        List<ResourceLocation> idsByLevel = tiers.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, Quality> entry) -> entry.getValue().level())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();

        Map<ResourceLocation, Resolved> base = new HashMap<>();
        tiers.forEach((id, quality) -> base.put(id, Resolved.of(id, quality.effects())));

        Registry<Item> items = access.registryOrThrow(Registries.ITEM);
        Map<Item, Map<ResourceLocation, Resolved>> overrides = resolveItemOverrides(items, tiers);

        Registry<EntityType<?>> entityTypes = access.registryOrThrow(Registries.ENTITY_TYPE);
        current = new Snapshot(Map.copyOf(tiers), idsByLevel, Map.copyOf(base), overrides,
                resolveNameSets(items, QualityAPI.NAME_SET),
                resolveNameSets(entityTypes, QualityAPI.ENTITY_NAME_SET),
                resolveEntityModifiers(entityTypes, tiers),
                resolveEntityTints(entityTypes, tiers));
        QualityAPI.LOGGER.info("Loaded {} qualities and {} item overrides", tiers.size(), overrides.size());
        if (!current.entityModifiers().isEmpty()) QualityAPI.LOGGER.info("Loaded entity attribute overrides for {} entity types", current.entityModifiers().size());
    }

    private static Map<Item, Map<ResourceLocation, Resolved>> resolveItemOverrides(Registry<Item> items, Map<ResourceLocation, Quality> tiers) {
        Map<Item, Map<ResourceLocation, Resolved>> overrides = new HashMap<>();
        Set<ResourceLocation> unknownTiers = new HashSet<>();
        items.getDataMap(QualityAPI.ITEM_QUALITIES).forEach((itemKey, patchesByTier) -> {
            Item item = items.get(itemKey);
            if (item == null) return;
            Map<ResourceLocation, Resolved> resolvedByTier = new HashMap<>();
            patchesByTier.forEach((tierId, patch) -> {
                Quality tier = tiers.get(tierId);
                if (tier == null) {
                    unknownTiers.add(tierId);
                    return;
                }
                resolvedByTier.put(tierId, Resolved.of(tierId, tier.effects().patchedWith(patch)));
            });
            overrides.put(item, Map.copyOf(resolvedByTier));
        });
        unknownTiers.forEach(tierId -> QualityAPI.LOGGER.warn("Item quality overrides reference unknown quality {}", tierId));
        return Map.copyOf(overrides);
    }

    private static <T> Map<T, String> resolveNameSets(Registry<T> registry, DataMapType<T, String> nameSetMap) {
        Map<T, String> nameSets = new HashMap<>();
        registry.getDataMap(nameSetMap).forEach((key, nameSet) -> {
            T element = registry.get(key);
            if (element != null) nameSets.put(element, nameSet);
        });
        return Map.copyOf(nameSets);
    }

    private static Map<EntityType<?>, Map<ResourceLocation, Multimap<Holder<Attribute>, AttributeModifier>>> resolveEntityModifiers(
            Registry<EntityType<?>> entityTypes, Map<ResourceLocation, Quality> tiers) {
        Map<EntityType<?>, Map<ResourceLocation, Multimap<Holder<Attribute>, AttributeModifier>>> modifiers = new HashMap<>();
        Set<ResourceLocation> unknownTiers = new HashSet<>();
        entityTypes.getDataMap(QualityAPI.ENTITY_QUALITIES).forEach((typeKey, effectsByTier) -> {
            EntityType<?> type = entityTypes.get(typeKey);
            if (type == null) return;
            Map<ResourceLocation, Multimap<Holder<Attribute>, AttributeModifier>> modifiersByTier = new HashMap<>();
            effectsByTier.forEach((tierId, effects) -> {
                if (!tiers.containsKey(tierId)) {
                    unknownTiers.add(tierId);
                    return;
                }
                Multimap<Holder<Attribute>, AttributeModifier> tierModifiers = entityModifiersOf(tierId, effects);
                if (!tierModifiers.isEmpty()) modifiersByTier.put(tierId, tierModifiers);
            });
            if (!modifiersByTier.isEmpty()) modifiers.put(type, Map.copyOf(modifiersByTier));
        });
        unknownTiers.forEach(tierId -> QualityAPI.LOGGER.warn("Entity quality overrides reference unknown quality {}", tierId));
        return Map.copyOf(modifiers);
    }

    private static Map<EntityType<?>, Map<ResourceLocation, Integer>> resolveEntityTints(Registry<EntityType<?>> entityTypes, Map<ResourceLocation, Quality> tiers) {
        Map<EntityType<?>, Map<ResourceLocation, Integer>> tints = new HashMap<>();
        entityTypes.getDataMap(QualityAPI.ENTITY_QUALITIES).forEach((typeKey, effectsByTier) -> {
            EntityType<?> type = entityTypes.get(typeKey);
            if (type == null) return;
            Map<ResourceLocation, Integer> tintsByTier = new HashMap<>();
            effectsByTier.forEach((tierId, effects) -> effects.tint()
                    .map(tint -> FastColor.ARGB32.opaque(tint.getValue()))
                    .filter(tint -> tint != UNTINTED && tiers.containsKey(tierId))
                    .ifPresent(tint -> tintsByTier.put(tierId, tint)));
            if (!tintsByTier.isEmpty()) tints.put(type, Map.copyOf(tintsByTier));
        });
        return Map.copyOf(tints);
    }

    public static Multimap<Holder<Attribute>, AttributeModifier> entityModifiersOf(ResourceLocation tier, EntityQualityEffects effects) {
        List<EntityQualityEffects.EntityAttributeEffect> attributeEffects = effects.attributes().orElse(List.of());
        ImmutableMultimap.Builder<Holder<Attribute>, AttributeModifier> modifiers = ImmutableMultimap.builder();
        for (int i = 0; i < attributeEffects.size(); i++) {
            EntityQualityEffects.EntityAttributeEffect effect = attributeEffects.get(i);
            ResourceLocation id = QualityAPI.id("entity/" + tier.getNamespace() + "/" + tier.getPath() + "/" + i);
            modifiers.put(effect.type(), new AttributeModifier(id, effect.amount(), effect.operation()));
        }
        return modifiers.build();
    }

    public Multimap<Holder<Attribute>, AttributeModifier> entityModifiers(EntityType<?> type, @Nullable ResourceLocation tier) {
        if (tier == null) return ImmutableMultimap.of();
        Map<ResourceLocation, Multimap<Holder<Attribute>, AttributeModifier>> perType = entityModifiers.get(type);
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = perType == null ? null : perType.get(tier);
        return modifiers == null ? ImmutableMultimap.of() : modifiers;
    }

    public int entityTint(EntityType<?> type, @Nullable ResourceLocation tier) {
        if (tier == null) return UNTINTED;
        Map<ResourceLocation, Integer> perType = entityTints.get(type);
        Integer tint = perType == null ? null : perType.get(tier);
        return tint == null ? UNTINTED : tint;
    }

    @Nullable
    public Resolved resolved(Item item, ResourceLocation tier) {
        Map<ResourceLocation, Resolved> perItem = overrides.get(item);
        Resolved resolved = perItem == null ? null : perItem.get(tier);
        return resolved != null ? resolved : base.get(tier);
    }

    public record Resolved(
            BakedAttribute[] attributes,
            Object2IntMap<ResourceKey<Enchantment>> enchantmentLevels,
            float miningSpeedMultiplier,
            float durabilityMultiplier,
            Optional<Float> breakDowngradeChance,
            float breakDestroyChance,
            float nutritionMultiplier,
            float saturationMultiplier,
            float eatEffectDurationMultiplier,
            List<FoodProperties.PossibleEffect> eatEffects) {

        public int scaleMaxDamage(int baseMaxDamage) {
            return durabilityMultiplier == 1.0F ? baseMaxDamage : Math.max(1, Math.round(baseMaxDamage * durabilityMultiplier));
        }

        public boolean changesFood(FoodProperties food) {
            return changesNutrition(food) || changesSaturation(food) || changesEffectDurations(food) || !eatEffects.isEmpty();
        }

        public boolean changesNutrition(FoodProperties food) {
            return scaledNutrition(food) != food.nutrition();
        }

        public boolean changesSaturation(FoodProperties food) {
            return saturationMultiplier != 1.0F && food.saturation() != 0.0F;
        }

        public boolean changesEffectDurations(FoodProperties food) {
            return eatEffectDurationMultiplier != 1.0F && !food.effects().isEmpty();
        }

        private int scaledNutrition(FoodProperties food) {
            return Math.round(food.nutrition() * nutritionMultiplier);
        }

        public FoodProperties applyTo(FoodProperties food) {
            if (!changesFood(food)) return food;
            List<FoodProperties.PossibleEffect> effects = new ArrayList<>(food.effects().size() + eatEffects.size());
            for (FoodProperties.PossibleEffect existing : food.effects()) {
                MobEffectInstance scaled = scaleDuration(existing.effect());
                effects.add(new FoodProperties.PossibleEffect(() -> scaled, existing.probability()));
            }
            effects.addAll(eatEffects);
            return new FoodProperties(
                    scaledNutrition(food),
                    food.saturation() * saturationMultiplier,
                    food.canAlwaysEat(),
                    food.eatSeconds(),
                    food.usingConvertsTo(),
                    List.copyOf(effects));
        }

        private MobEffectInstance scaleDuration(MobEffectInstance effect) {
            if (eatEffectDurationMultiplier == 1.0F || effect.isInfiniteDuration()) return effect;
            int duration = Math.max(1, Math.round(effect.getDuration() * eatEffectDurationMultiplier));
            MobEffectInstance scaled = new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
            scaled.getCures().clear();
            scaled.getCures().addAll(effect.getCures());
            return scaled;
        }

        public static Resolved of(ResourceLocation tier, QualityEffects effects) {
            List<QualityEffects.AttributeEffect> attributeEffects = effects.attributes().orElse(List.of());
            BakedAttribute[] attributes = new BakedAttribute[attributeEffects.size()];
            for (int i = 0; i < attributes.length; i++) {
                attributes[i] = BakedAttribute.of(tier, i, attributeEffects.get(i));
            }
            Object2IntOpenHashMap<ResourceKey<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
            effects.enchantmentLevels().ifPresent(enchantments::putAll);
            return new Resolved(attributes,
                    Object2IntMaps.unmodifiable(enchantments),
                    effects.miningSpeedMultiplier().orElse(1.0F),
                    effects.durabilityMultiplier().orElse(1.0F),
                    effects.breakDowngradeChance(),
                    effects.breakDestroyChance().orElse(0.0F),
                    effects.nutritionMultiplier().orElse(1.0F),
                    effects.saturationMultiplier().orElse(1.0F),
                    effects.eatEffectDurationMultiplier().orElse(1.0F),
                    effects.eatEffects().orElse(List.of()).stream().map(Resolved::possibleEffect).toList());
        }

        private static FoodProperties.PossibleEffect possibleEffect(QualityEffects.EatEffect eatEffect) {
            return new FoodProperties.PossibleEffect(
                    () -> new MobEffectInstance(eatEffect.effect(), eatEffect.duration(), eatEffect.amplifier()),
                    eatEffect.probability());
        }
    }

    public record BakedAttribute(Holder<Attribute> attribute, @Nullable EquipmentSlotGroup slot, AttributeModifier[] modifiersBySlotOrdinal) {
        static BakedAttribute of(ResourceLocation tier, int index, QualityEffects.AttributeEffect effect) {
            EquipmentSlotGroup[] groups = EquipmentSlotGroup.values();
            AttributeModifier[] modifiers = new AttributeModifier[groups.length];
            for (EquipmentSlotGroup group : groups) {
                ResourceLocation id = QualityAPI.id(tier.getNamespace() + "/" + tier.getPath() + "/" + index + "/" + group.getSerializedName());
                modifiers[group.ordinal()] = new AttributeModifier(id, effect.amount(), effect.operation());
            }
            return new BakedAttribute(effect.type(), effect.slot().orElse(null), modifiers);
        }

        public AttributeModifier modifierFor(EquipmentSlotGroup group) {
            return modifiersBySlotOrdinal[group.ordinal()];
        }
    }
}
