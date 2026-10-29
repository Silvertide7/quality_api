package net.silvertide.quality_api.quality;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;

public final class QualityTooltipName {
    private static final ResourceLocation STAR_FONT = QualityAPI.id("stars");
    private static final String FULL_STAR = "";
    private static final String HALF_STAR = "";
    private static final int HALF_STARS_AT_LEVEL_ZERO = 2;
    private static final int HALF_STARS_PER_STAR = 2;

    private QualityTooltipName() {}

    public static Component of(Component tierName, Quality quality) {
        Config.TooltipStyle style = Config.CLIENT_SPEC.isLoaded() ? Config.QUALITY_TOOLTIP_STYLE.get() : Config.TooltipStyle.BOTH;
        int halfStars = quality.level() + HALF_STARS_AT_LEVEL_ZERO;
        if (style == Config.TooltipStyle.TEXT || halfStars <= 0) return tierName;
        MutableComponent stars = Component.literal(FULL_STAR.repeat(halfStars / HALF_STARS_PER_STAR) + (halfStars % HALF_STARS_PER_STAR == 1 ? HALF_STAR : ""))
                .withStyle(Style.EMPTY.withFont(STAR_FONT).withColor(quality.color()));
        if (style == Config.TooltipStyle.SYMBOLS) return stars;
        return tierName.copy().append(" ").append(stars);
    }
}
