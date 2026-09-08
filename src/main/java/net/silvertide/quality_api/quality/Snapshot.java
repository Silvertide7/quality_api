package net.silvertide.quality_api.quality;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.silvertide.quality_api.QualityAPI;
import org.jetbrains.annotations.Nullable;

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
        Map<Item, Map<ResourceLocation, Resolved>> overrides) {

    public static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), Map.of(), Map.of());

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

        Map<Item, Map<ResourceLocation, Resolved>> overrides = resolveItemOverrides(access.registryOrThrow(Registries.ITEM), tiers);

        current = new Snapshot(Map.copyOf(tiers), idsByLevel, Map.copyOf(base), overrides);
        QualityAPI.LOGGER.info("Loaded {} qualities and {} item overrides", tiers.size(), overrides.size());
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
            float breakDestroyChance) {

        public int scaleMaxDamage(int baseMaxDamage) {
            return durabilityMultiplier == 1.0F ? baseMaxDamage : Math.max(1, Math.round(baseMaxDamage * durabilityMultiplier));
        }

        static Resolved of(ResourceLocation tier, QualityEffects effects) {
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
                    effects.breakDestroyChance().orElse(0.0F));
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
