package net.silvertide.quality_api.stack;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record QualityStackSelection(Optional<ResourceLocation> tier) {
    public static final StreamCodec<ByteBuf, QualityStackSelection> STREAM_CODEC =
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).map(QualityStackSelection::new, QualityStackSelection::tier);
}
