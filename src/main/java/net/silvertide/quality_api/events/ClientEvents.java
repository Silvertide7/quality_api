package net.silvertide.quality_api.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.QualityTooltipName;
import net.silvertide.quality_api.quality.Snapshot;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = QualityAPI.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        if (tier == null) return;
        Quality quality = Snapshot.current().tiers().get(tier);
        if (quality == null) return;
        List<Component> lines = event.getToolTip();
        boolean broken = Qualities.isBroken(stack);
        int insertAt = 1;
        if (Config.SHOW_QUALITY_TOOLTIP.get()) {
            if (!Config.HIDDEN_TOOLTIP_TIERS.get().contains(tier.toString())) {
                insertAt = insert(lines, insertAt, QualityTooltipName.of(Qualities.displayName(stack, tier), quality));
            }
            String crafter = stack.get(QualityAPI.CRAFTER.get());
            if (crafter != null) {
                insertAt = insert(lines, insertAt, Component.translatable("quality_api.tooltip.crafter", crafter).withStyle(ChatFormatting.GRAY));
            }
            FoodProperties food = stack.getFoodProperties(event.getEntity());
            Snapshot.Resolved resolved = food == null ? null : Snapshot.current().resolved(stack.getItem(), tier);
            if (resolved != null && !broken && resolved.changesFood(food)) {
                for (Component foodLine : foodLines(resolved, food, event.getContext().tickRate())) {
                    insertAt = insert(lines, insertAt, foodLine);
                }
            }
        }
        if (broken) {
            insert(lines, insertAt, Component.translatable("quality_api.tooltip.broken").withStyle(ChatFormatting.RED));
        }
    }

    private static List<Component> foodLines(Snapshot.Resolved resolved, FoodProperties food, float ticksPerSecond) {
        List<Component> foodLines = new ArrayList<>();
        if (resolved.changesNutrition(food)) {
            foodLines.add(gray(Component.translatable("quality_api.tooltip.food.nutrition", signedPercentChange(resolved.nutritionMultiplier()))));
        }
        if (resolved.changesSaturation(food)) {
            foodLines.add(gray(Component.translatable("quality_api.tooltip.food.saturation", signedPercentChange(resolved.saturationMultiplier()))));
        }
        float durationMultiplier = resolved.eatEffectDurationMultiplier();
        if (resolved.changesEffectDurations(food)) {
            String durationKey = durationMultiplier > 1.0F ? "quality_api.tooltip.food.duration.longer" : "quality_api.tooltip.food.duration.shorter";
            foodLines.add(gray(Component.translatable(durationKey, Math.abs(percentChange(durationMultiplier)))));
        }
        for (FoodProperties.PossibleEffect eatEffect : resolved.eatEffects()) {
            foodLines.add(gray(eatEffectLine(eatEffect, ticksPerSecond)));
        }
        return foodLines;
    }

    private static MutableComponent eatEffectLine(FoodProperties.PossibleEffect eatEffect, float ticksPerSecond) {
        MobEffectInstance effect = eatEffect.effect();
        MutableComponent name = Component.translatable(effect.getDescriptionId());
        if (effect.getAmplifier() > 0) {
            name = Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + effect.getAmplifier()));
        }
        name = Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(effect, 1.0F, ticksPerSecond));
        if (eatEffect.probability() >= 1.0F) return name;
        return Component.translatable("quality_api.tooltip.food.chance", name, Math.round(eatEffect.probability() * 100.0F));
    }

    private static int percentChange(float multiplier) {
        return Math.round((multiplier - 1.0F) * 100.0F);
    }

    private static String signedPercentChange(float multiplier) {
        int percent = percentChange(multiplier);
        return percent > 0 ? "+" + percent : String.valueOf(percent);
    }

    private static MutableComponent gray(MutableComponent line) {
        return line.withStyle(ChatFormatting.GRAY);
    }

    private static int insert(List<Component> lines, int index, Component line) {
        int at = Math.min(index, lines.size());
        lines.add(at, line);
        return at + 1;
    }
}
