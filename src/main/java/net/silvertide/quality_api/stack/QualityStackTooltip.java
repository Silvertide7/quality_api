package net.silvertide.quality_api.stack;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record QualityStackTooltip(List<ItemStack> tierStacksBestFirst, int nextOutIndex) implements TooltipComponent {}
