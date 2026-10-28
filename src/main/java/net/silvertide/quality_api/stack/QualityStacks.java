package net.silvertide.quality_api.stack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class QualityStacks {
    private QualityStacks() {}

    public static boolean isQualityStack(ItemStack stack) {
        return stack.is(QualityAPI.QUALITY_STACK.get());
    }

    @Nullable
    public static QualityStackContents contents(ItemStack stack) {
        return isQualityStack(stack) ? stack.get(QualityAPI.QUALITY_STACK_CONTENTS.get()) : null;
    }

    public static boolean canPool(ItemStack first, ItemStack second) {
        return QualityStackContents.isPoolable(first) && QualityStackContents.isPoolable(second) && QualityStackContents.isSameApartFromQuality(first, second);
    }

    public static ItemStack poolIntoNewQualityStack(ItemStack first, ItemStack second) {
        if (!canPool(first, second)) return ItemStack.EMPTY;
        ItemStack qualityStack = new ItemStack(QualityAPI.QUALITY_STACK.get());
        qualityStack.set(QualityAPI.QUALITY_STACK_CONTENTS.get(), QualityStackContents.emptyFor(first));
        insert(qualityStack, first);
        insert(qualityStack, second);
        return qualityStack;
    }

    public static int insert(ItemStack qualityStack, ItemStack stack) {
        QualityStackContents contents = contents(qualityStack);
        if (contents == null || !contents.accepts(stack)) return 0;
        int moved = Math.min(stack.getCount(), contents.capacity() - contents.total());
        if (moved <= 0) return 0;
        qualityStack.set(QualityAPI.QUALITY_STACK_CONTENTS.get(), contents.plus(tierOf(stack), moved));
        stack.shrink(moved);
        return moved;
    }

    public static ItemStack peekBest(ItemStack qualityStack) {
        QualityStackContents contents = contents(qualityStack);
        if (contents == null || contents.isEmpty()) return ItemStack.EMPTY;
        QualityStackContents.Entry best = contents.entriesBestFirst().getFirst();
        return contents.stackOf(best.tier(), Math.min(best.count(), contents.maxStackSize()));
    }

    public static ItemStack extractBest(ItemStack qualityStack, int maxCount) {
        QualityStackContents contents = contents(qualityStack);
        if (contents == null || contents.isEmpty() || maxCount <= 0) return ItemStack.EMPTY;
        QualityStackContents.Entry best = contents.entriesBestFirst().getFirst();
        int taken = Math.min(Math.min(best.count(), contents.maxStackSize()), maxCount);
        QualityStackContents remaining = contents.minus(best.tier(), taken);
        if (remaining.isEmpty()) qualityStack.setCount(0);
        else qualityStack.set(QualityAPI.QUALITY_STACK_CONTENTS.get(), remaining);
        return contents.stackOf(best.tier(), taken);
    }

    public static Optional<ItemStack> collapseToSingleStack(ItemStack qualityStack) {
        QualityStackContents contents = contents(qualityStack);
        if (contents == null || contents.entries().size() != 1) return Optional.empty();
        QualityStackContents.Entry only = contents.entries().getFirst();
        if (only.count() > contents.maxStackSize()) return Optional.empty();
        qualityStack.setCount(0);
        return Optional.of(contents.stackOf(only.tier(), only.count()));
    }

    private static Optional<ResourceLocation> tierOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(QualityAPI.QUALITY.get()));
    }
}
