package net.silvertide.quality_api.quality;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record Quality(int level, TextColor color, int weight, Optional<HolderSet<Item>> items, QualityEffects effects) {
    public static final Codec<Quality> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(Quality::level),
            TextColor.CODEC.optionalFieldOf("color", TextColor.fromLegacyFormat(ChatFormatting.WHITE)).forGetter(Quality::color),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("weight", 0).forGetter(Quality::weight),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(Quality::items),
            QualityEffects.CODEC.optionalFieldOf("effects", QualityEffects.EMPTY).forGetter(Quality::effects)
    ).apply(instance, Quality::new));

    public boolean appliesTo(ItemStack stack) {
        return items.map(set -> set.contains(stack.getItemHolder())).orElse(true);
    }
}
