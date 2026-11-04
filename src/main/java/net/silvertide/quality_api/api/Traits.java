package net.silvertide.quality_api.api;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.quality.Snapshot;
import net.silvertide.quality_api.quality.Trait;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Traits {
    private static final int TRAITS_PER_TOOLTIP_LINE = 4;
    private static final String TOOLTIP_CONTINUATION_INDENT = "  ";

    private Traits() {}

    public static List<ResourceLocation> getIds(ItemStack stack) {
        return stack.getOrDefault(QualityAPI.TRAITS.get(), List.of());
    }

    public static List<ResourceLocation> getIds(Entity entity) {
        List<ResourceLocation> ids = entity.getExistingDataOrNull(QualityAPI.ENTITY_TRAITS);
        return ids == null ? List.of() : ids;
    }

    public static boolean has(ItemStack stack, ResourceLocation id) {
        return getIds(stack).contains(id);
    }

    public static boolean has(Entity entity, ResourceLocation id) {
        return getIds(entity).contains(id);
    }

    public static Optional<Trait> get(ResourceLocation id) {
        return Optional.ofNullable(Snapshot.current().traits().get(id));
    }

    public static boolean canAdd(ItemStack stack, ResourceLocation id) {
        return !stack.isEmpty() && !stack.is(QualityAPI.BLACKLIST) && canAdd(getIds(stack), id);
    }

    public static boolean add(ItemStack stack, ResourceLocation id) {
        if (!canAdd(stack, id)) return false;
        stack.set(QualityAPI.TRAITS.get(), with(getIds(stack), id));
        return true;
    }

    public static boolean add(Entity entity, ResourceLocation id) {
        List<ResourceLocation> ids = getIds(entity);
        if (!canAdd(ids, id)) return false;
        entity.setData(QualityAPI.ENTITY_TRAITS, with(ids, id));
        return true;
    }

    public static void remove(ItemStack stack, ResourceLocation id) {
        if (!has(stack, id)) return;
        List<ResourceLocation> remaining = without(getIds(stack), id);
        if (remaining.isEmpty()) stack.remove(QualityAPI.TRAITS.get());
        else stack.set(QualityAPI.TRAITS.get(), remaining);
    }

    public static void remove(Entity entity, ResourceLocation id) {
        if (!has(entity, id)) return;
        List<ResourceLocation> remaining = without(getIds(entity), id);
        if (remaining.isEmpty()) entity.removeData(QualityAPI.ENTITY_TRAITS);
        else entity.setData(QualityAPI.ENTITY_TRAITS, remaining);
    }

    public static Component displayName(ResourceLocation id) {
        MutableComponent name = Component.translatable(Util.makeDescriptionId("trait", id));
        return get(id).map(trait -> name.withStyle(style -> style.withColor(trait.color()))).orElse(name);
    }

    public static List<Component> displayNames(ItemStack stack) {
        return getIds(stack).stream().filter(id -> get(id).isPresent()).map(Traits::displayName).toList();
    }

    public static List<Component> tooltipLines(ItemStack stack) {
        List<Component> names = displayNames(stack);
        List<Component> lines = new ArrayList<>();
        Component separator = Component.literal(", ").withStyle(ChatFormatting.GRAY);
        for (int start = 0; start < names.size(); start += TRAITS_PER_TOOLTIP_LINE) {
            Component row = ComponentUtils.formatList(names.subList(start, Math.min(names.size(), start + TRAITS_PER_TOOLTIP_LINE)), separator);
            lines.add(start == 0
                    ? Component.translatable("quality_api.tooltip.traits", row).withStyle(ChatFormatting.GRAY)
                    : Component.literal(TOOLTIP_CONTINUATION_INDENT).append(row));
        }
        return lines;
    }

    private static boolean canAdd(List<ResourceLocation> ids, ResourceLocation id) {
        return get(id).isPresent() && !ids.contains(id) && ids.size() < Trait.MAX_TRAITS_PER_HOLDER;
    }

    private static List<ResourceLocation> with(List<ResourceLocation> ids, ResourceLocation id) {
        List<ResourceLocation> added = new ArrayList<>(ids);
        added.add(id);
        return Trait.canonical(added);
    }

    private static List<ResourceLocation> without(List<ResourceLocation> ids, ResourceLocation id) {
        return ids.stream().filter(held -> !held.equals(id)).toList();
    }
}
