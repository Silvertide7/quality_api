package net.silvertide.quality_api.stack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class QualityStacks {
    private QualityStacks() {}

    public static boolean isQualityStack(ItemStack stack) {
        return stack.is(QualityAPI.QUALITY_STACK.get());
    }

    @Nullable
    public static QualityStackContents contents(ItemStack stack) {
        return isQualityStack(stack) && stack.getCount() == 1 ? stack.get(QualityAPI.QUALITY_STACK_CONTENTS.get()) : null;
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

    public static boolean holdSameItem(ItemStack firstQualityStack, ItemStack secondQualityStack) {
        QualityStackContents first = contents(firstQualityStack);
        QualityStackContents second = contents(secondQualityStack);
        return first != null && second != null && second.accepts(first.stackOf(Optional.empty(), 1));
    }

    public static int moveContents(ItemStack fromQualityStack, ItemStack intoQualityStack) {
        if (fromQualityStack == intoQualityStack || !holdSameItem(fromQualityStack, intoQualityStack)) return 0;
        QualityStackContents source = contents(fromQualityStack);
        QualityStackContents target = contents(intoQualityStack);
        Set<Optional<ResourceLocation>> tiersAlreadyHeld = target.entries().stream().map(QualityStackContents.Entry::tier).collect(Collectors.toSet());
        List<QualityStackContents.Entry> heldTiersFirst = source.entriesBestFirst().stream()
                .sorted(Comparator.comparing((QualityStackContents.Entry entry) -> !tiersAlreadyHeld.contains(entry.tier())))
                .toList();
        int moved = 0;
        for (QualityStackContents.Entry entry : heldTiersFirst) {
            int count = Math.min(entry.count(), target.capacity() - target.total());
            if (count <= 0) break;
            target = target.plus(entry.tier(), count);
            source = source.minus(entry.tier(), count);
            moved += count;
        }
        if (moved == 0) return 0;
        intoQualityStack.set(QualityAPI.QUALITY_STACK_CONTENTS.get(), target);
        if (source.isEmpty()) fromQualityStack.setCount(0);
        else fromQualityStack.set(QualityAPI.QUALITY_STACK_CONTENTS.get(), source);
        return moved;
    }

    @Nullable
    public static QualityStackContents.Entry nextOut(ItemStack qualityStack) {
        QualityStackContents contents = contents(qualityStack);
        if (contents == null || contents.isEmpty()) return null;
        QualityStackSelection selection = qualityStack.get(QualityAPI.QUALITY_STACK_SELECTION.get());
        if (selection != null) {
            for (QualityStackContents.Entry entry : contents.entries()) {
                if (entry.tier().equals(selection.tier())) return entry;
            }
        }
        return contents.entriesBestFirst().getFirst();
    }

    public static boolean select(ItemStack qualityStack, Optional<ResourceLocation> tier) {
        QualityStackContents contents = contents(qualityStack);
        if (contents == null || contents.entries().stream().noneMatch(entry -> entry.tier().equals(tier))) return false;
        qualityStack.set(QualityAPI.QUALITY_STACK_SELECTION.get(), new QualityStackSelection(tier));
        return true;
    }

    @Nullable
    public static QualityStackContents.Entry selectNeighbour(ItemStack qualityStack, int stepsTowardWorse) {
        QualityStackContents contents = contents(qualityStack);
        QualityStackContents.Entry current = nextOut(qualityStack);
        if (contents == null || current == null || contents.entries().size() < 2) return null;
        List<QualityStackContents.Entry> entriesBestFirst = contents.entriesBestFirst();
        int neighbourIndex = Math.floorMod(entriesBestFirst.indexOf(current) + stepsTowardWorse, entriesBestFirst.size());
        QualityStackContents.Entry neighbour = entriesBestFirst.get(neighbourIndex);
        select(qualityStack, neighbour.tier());
        return neighbour;
    }

    public static ItemStack peekNext(ItemStack qualityStack) {
        QualityStackContents contents = contents(qualityStack);
        QualityStackContents.Entry next = nextOut(qualityStack);
        if (contents == null || next == null) return ItemStack.EMPTY;
        return contents.stackOf(next.tier(), Math.min(next.count(), contents.maxStackSize()));
    }

    public static ItemStack extractNext(ItemStack qualityStack, int maxCount) {
        QualityStackContents contents = contents(qualityStack);
        QualityStackContents.Entry next = nextOut(qualityStack);
        if (contents == null || next == null || maxCount <= 0) return ItemStack.EMPTY;
        int taken = Math.min(Math.min(next.count(), contents.maxStackSize()), maxCount);
        QualityStackContents remaining = contents.minus(next.tier(), taken);
        if (remaining.isEmpty()) qualityStack.setCount(0);
        else qualityStack.set(QualityAPI.QUALITY_STACK_CONTENTS.get(), remaining);
        if (taken == next.count()) qualityStack.remove(QualityAPI.QUALITY_STACK_SELECTION.get());
        return contents.stackOf(next.tier(), taken);
    }

    public static Optional<ItemStack> collapseToSingleStack(ItemStack qualityStack, int maxCount) {
        QualityStackContents contents = contents(qualityStack);
        if (contents == null || contents.entries().size() != 1) return Optional.empty();
        QualityStackContents.Entry only = contents.entries().getFirst();
        if (only.count() > Math.min(contents.maxStackSize(), maxCount)) return Optional.empty();
        qualityStack.setCount(0);
        return Optional.of(contents.stackOf(only.tier(), only.count()));
    }

    static Optional<ResourceLocation> tierOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(QualityAPI.QUALITY.get()));
    }
}
