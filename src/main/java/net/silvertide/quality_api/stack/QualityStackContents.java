package net.silvertide.quality_api.stack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.Snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class QualityStackContents {
    public static final int CAPACITY_IN_STACKS = 1;
    private static final int LARGEST_CAPACITY_IN_STACKS_EVER_RELEASED = 4;
    private static final int LOADABLE_TOTAL_LIMIT = Item.ABSOLUTE_MAX_STACK_SIZE * LARGEST_CAPACITY_IN_STACKS_EVER_RELEASED;

    public record Entry(Optional<ResourceLocation> tier, int count) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("tier").forGetter(Entry::tier),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Entry::count)
        ).apply(instance, Entry::new));

        private static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Entry::tier,
                ByteBufCodecs.VAR_INT, Entry::count,
                Entry::new);
    }

    public static final Codec<QualityStackContents> CODEC = RecordCodecBuilder.<QualityStackContents>create(instance -> instance.group(
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(contents -> contents.item),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(contents -> contents.entries)
    ).apply(instance, QualityStackContents::new)).validate(QualityStackContents::validated);

    public static final StreamCodec<RegistryFriendlyByteBuf, QualityStackContents> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, contents -> contents.item,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), contents -> contents.entries,
            QualityStackContents::new).map(QualityStackContents::validatedOrThrow, contents -> contents);

    private static final Comparator<Optional<ResourceLocation>> PLAIN_FIRST_THEN_BY_ID =
            Comparator.comparing(tier -> tier.map(ResourceLocation::toString).orElse(""));

    private final ItemStack item;
    private final List<Entry> entries;
    private final long total;
    private volatile BestFirstOrder bestFirstOrder;

    private record BestFirstOrder(Snapshot snapshot, List<Entry> entries) {}

    private QualityStackContents(ItemStack item, List<Entry> entries) {
        this.item = item.copyWithCount(1);
        this.item.remove(QualityAPI.QUALITY.get());
        this.entries = mergedAndSorted(entries);
        this.total = sumOf(this.entries);
    }

    static QualityStackContents emptyFor(ItemStack stack) {
        if (!isPoolable(stack)) throw new IllegalArgumentException("Cannot pool " + stack);
        return new QualityStackContents(stack, List.of());
    }

    public static boolean isPoolable(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() > 1 && !stack.isDamageableItem() && !stack.is(QualityAPI.QUALITY_STACK.get());
    }

    private static DataResult<QualityStackContents> validated(QualityStackContents contents) {
        if (contents.item.isEmpty() || contents.item.is(QualityAPI.QUALITY_STACK.get())) return DataResult.error(() -> "Quality stack cannot hold " + contents.item);
        if (contents.entries.isEmpty()) return DataResult.error(() -> "Quality stack has no entries");
        if (contents.total > LOADABLE_TOTAL_LIMIT) return DataResult.error(() -> "Quality stack holds " + contents.total + ", over the most any item could ever fit (" + LOADABLE_TOTAL_LIMIT + ")");
        return DataResult.success(contents);
    }

    private static QualityStackContents validatedOrThrow(QualityStackContents contents) {
        return validated(contents).getOrThrow(DecoderException::new);
    }

    private static List<Entry> mergedAndSorted(List<Entry> entries) {
        Map<Optional<ResourceLocation>, Long> countsByTier = new TreeMap<>(PLAIN_FIRST_THEN_BY_ID);
        for (Entry entry : entries) {
            if (entry.count() > 0) countsByTier.merge(entry.tier(), (long) entry.count(), Long::sum);
        }
        List<Entry> merged = new ArrayList<>(countsByTier.size());
        countsByTier.forEach((tier, count) -> merged.add(new Entry(tier, (int) Math.min(count, Integer.MAX_VALUE))));
        return List.copyOf(merged);
    }

    private static long sumOf(List<Entry> entries) {
        long total = 0;
        for (Entry entry : entries) total += entry.count();
        return total;
    }

    public Component hoverName() {
        MutableComponent name = Component.empty().append(item.getHoverName()).withStyle(item.getRarity().getStyleModifier());
        return item.has(DataComponents.CUSTOM_NAME) ? name.withStyle(ChatFormatting.ITALIC) : name;
    }

    ItemStack template() {
        return item;
    }

    public int maxStackSize() {
        return item.getMaxStackSize();
    }

    public int capacity() {
        return maxStackSize() * CAPACITY_IN_STACKS;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int total() {
        return (int) total;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean accepts(ItemStack stack) {
        return isPoolable(stack) && isSameApartFromQuality(item, stack);
    }

    public List<Entry> entriesBestFirst() {
        Snapshot snapshot = Snapshot.current();
        BestFirstOrder cached = bestFirstOrder;
        if (cached != null && cached.snapshot() == snapshot) return cached.entries();
        List<Entry> entriesBestFirst = sortedBestFirst(snapshot.tiers());
        bestFirstOrder = new BestFirstOrder(snapshot, entriesBestFirst);
        return entriesBestFirst;
    }

    private List<Entry> sortedBestFirst(Map<ResourceLocation, Quality> tiers) {
        Comparator<Entry> bestFirst = Comparator
                .comparing((Entry entry) -> entry.tier().isPresent() && !tiers.containsKey(entry.tier().get()))
                .thenComparing(entry -> -entry.tier().map(tiers::get).map(Quality::level).orElse(0))
                .thenComparing(entry -> entry.tier().isEmpty())
                .thenComparing(Entry::tier, PLAIN_FIRST_THEN_BY_ID);
        return entries.stream().sorted(bestFirst).toList();
    }

    public List<ItemStack> asStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Entry entry : entries) {
            for (int remaining = entry.count(); remaining > 0; remaining -= maxStackSize()) {
                stacks.add(stackOf(entry.tier(), Math.min(remaining, maxStackSize())));
            }
        }
        return stacks;
    }

    static boolean isSameApartFromQuality(ItemStack first, ItemStack second) {
        if (!first.is(second.getItem())) return false;
        if (!first.has(QualityAPI.QUALITY.get()) && !second.has(QualityAPI.QUALITY.get())) return ItemStack.isSameItemSameComponents(first, second);
        return ItemStack.isSameItemSameComponents(withoutQuality(first), withoutQuality(second));
    }

    private static ItemStack withoutQuality(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        copy.remove(QualityAPI.QUALITY.get());
        return copy;
    }

    QualityStackContents plus(Optional<ResourceLocation> tier, int count) {
        if (count <= 0 || total + count > capacity()) throw new IllegalArgumentException("Cannot add " + count + " to " + this);
        List<Entry> added = new ArrayList<>(entries);
        added.add(new Entry(tier, count));
        return new QualityStackContents(item, added);
    }

    QualityStackContents minus(Optional<ResourceLocation> tier, int count) {
        int held = entries.stream().filter(entry -> entry.tier().equals(tier)).mapToInt(Entry::count).sum();
        if (count <= 0 || count > held) throw new IllegalArgumentException("Cannot remove " + count + " of " + tier + " from " + this);
        List<Entry> reduced = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            reduced.add(entry.tier().equals(tier) ? new Entry(tier, entry.count() - count) : entry);
        }
        return new QualityStackContents(item, reduced);
    }

    ItemStack stackOf(Optional<ResourceLocation> tier, int count) {
        ItemStack stack = item.copyWithCount(count);
        tier.ifPresent(id -> stack.set(QualityAPI.QUALITY.get(), id));
        return stack;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof QualityStackContents contents
                && ItemStack.isSameItemSameComponents(item, contents.item)
                && entries.equals(contents.entries);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(item) + entries.hashCode();
    }

    @Override
    public String toString() {
        return "QualityStackContents[" + item.getItem() + ", " + entries + "]";
    }
}
