package net.silvertide.quality_api.stack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;

import java.util.List;
import java.util.Optional;

public class QualityStackItem extends Item {
    public QualityStackItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        QualityStackContents contents = QualityStacks.contents(stack);
        return contents == null ? super.getName(stack) : contents.hoverName();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        QualityStackContents contents = QualityStacks.contents(stack);
        return contents != null && contents.template().hasFoil();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        QualityStackContents contents = QualityStacks.contents(stack);
        if (contents == null) return;
        for (QualityStackContents.Entry entry : contents.entriesBestFirst()) {
            lines.add(Component.translatable("quality_api.tooltip.quality_stack.entry", tierName(contents, entry.tier()), entry.count()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("quality_api.tooltip.quality_stack.total", contents.total(), contents.capacity()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component tierName(QualityStackContents contents, Optional<ResourceLocation> tier) {
        if (tier.isEmpty()) return Component.translatable("quality_api.tooltip.quality_stack.plain");
        ResourceLocation id = tier.get();
        return Qualities.get(id).isPresent() ? Qualities.displayName(contents.template(), id) : Component.literal(id.toString());
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
        QualityStackContents contents = itemEntity.getItem().remove(QualityAPI.QUALITY_STACK_CONTENTS.get());
        if (contents != null) ItemUtils.onContainerDestroyed(itemEntity, contents.asStacks());
    }
}
