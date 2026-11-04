package net.silvertide.quality_api.quality;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record Trait(TextColor color) {
    public static final int MAX_TRAITS_PER_HOLDER = 16;

    public static final Codec<Trait> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TextColor.CODEC.optionalFieldOf("color", TextColor.fromLegacyFormat(ChatFormatting.GRAY)).forGetter(Trait::color)
    ).apply(instance, Trait::new));

    public static final Codec<List<ResourceLocation>> IDS_CODEC =
            ResourceLocation.CODEC.listOf().xmap(Trait::canonical, Trait::canonical);

    public static final StreamCodec<ByteBuf, List<ResourceLocation>> IDS_STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TRAITS_PER_HOLDER)).map(Trait::canonical, Trait::canonical);

    public static List<ResourceLocation> canonical(List<ResourceLocation> ids) {
        return ids.stream().distinct().sorted().limit(MAX_TRAITS_PER_HOLDER).toList();
    }
}
