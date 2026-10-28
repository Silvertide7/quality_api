package net.silvertide.quality_api.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.stack.QualityStackContents;
import net.silvertide.quality_api.stack.QualityStacks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@GameTestHolder(QualityAPI.MOD_ID)
@PrefixGameTestTemplate(false)
public class QualityStackGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation CRUDE = QualityAPI.id("crude");
    private static final ResourceLocation STANDARD = QualityAPI.id("standard");
    private static final ResourceLocation FINE = QualityAPI.id("fine");
    private static final ResourceLocation SUPERIOR = QualityAPI.id("superior");
    private static final ResourceLocation MASTERWORK = QualityAPI.id("masterwork");
    private static final ResourceLocation REMOVED_TIER = QualityAPI.id("removed_tier");
    private static final int CARROT_CAPACITY = 64 * QualityStackContents.CAPACITY_IN_STACKS;

    @GameTest(template = EMPTY)
    public static void onlyTheSameItemPoolsAndOnlyQualityMayDiffer(GameTestHelper helper) {
        ItemStack renamedCarrot = carrots(FINE, 1);
        renamedCarrot.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky"));
        helper.assertTrue(QualityStacks.canPool(carrots(FINE, 1), carrots(SUPERIOR, 1)), "carrots of two tiers should pool");
        helper.assertTrue(QualityStacks.canPool(carrots(null, 1), carrots(FINE, 1)), "a plain carrot should pool with a fine one");
        helper.assertTrue(QualityStacks.canPool(carrots(FINE, 1), carrots(FINE, 1)), "carrots of one tier should pool");
        helper.assertFalse(QualityStacks.canPool(carrots(FINE, 1), stack(Items.POTATO, FINE, 1)), "different items should not pool");
        helper.assertFalse(QualityStacks.canPool(renamedCarrot, carrots(FINE, 1)), "a renamed carrot should not pool with a plain-named one");
        helper.assertFalse(QualityStacks.canPool(stack(Items.IRON_SWORD, FINE, 1), stack(Items.IRON_SWORD, SUPERIOR, 1)), "damageable items should not pool");
        helper.assertFalse(QualityStacks.canPool(stack(Items.MUSHROOM_STEW, FINE, 1), stack(Items.MUSHROOM_STEW, SUPERIOR, 1)), "unstackable items should not pool");
        helper.assertFalse(QualityStacks.canPool(QualityStacks.poolIntoNewQualityStack(carrots(FINE, 1), carrots(SUPERIOR, 1)), carrots(FINE, 1)), "a quality stack should not pool as an item");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void poolingKeepsEveryItemAndItsTier(GameTestHelper helper) {
        ItemStack fine = carrots(FINE, 10);
        ItemStack superior = carrots(SUPERIOR, 20);
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(fine, superior);
        helper.assertTrue(fine.isEmpty() && superior.isEmpty(), "both pooled stacks should be used up");
        helper.assertTrue(counts(qualityStack).equals(List.of(new QualityStackContents.Entry(Optional.of(FINE), 10), new QualityStackContents.Entry(Optional.of(SUPERIOR), 20))),
                "the quality stack should hold 10 fine and 20 superior, held " + counts(qualityStack));
        helper.assertTrue(qualityStack.getHoverName().getString().equals(new ItemStack(Items.CARROT).getHoverName().getString()), "a quality stack should be named after its item");
        ItemStack potato = stack(Items.POTATO, FINE, 5);
        helper.assertTrue(QualityStacks.poolIntoNewQualityStack(potato, carrots(FINE, 5)).isEmpty() && potato.getCount() == 5, "refusing to pool should leave both stacks untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void insertStopsAtCapacityAndLeavesTheRest(GameTestHelper helper) {
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 64), carrots(SUPERIOR, 64));
        helper.assertTrue(QualityStacks.insert(qualityStack, carrots(CRUDE, 64)) == 64, "a quality stack with room should take a whole stack");
        ItemStack masterwork = carrots(MASTERWORK, 64);
        helper.assertTrue(QualityStacks.insert(qualityStack, masterwork) == 64 && total(qualityStack) == CARROT_CAPACITY, "the fourth stack should fill it to capacity");
        ItemStack overflow = carrots(MASTERWORK, 10);
        helper.assertTrue(QualityStacks.insert(qualityStack, overflow) == 0 && overflow.getCount() == 10, "a full quality stack should take nothing and leave the stack whole");
        ItemStack potato = stack(Items.POTATO, FINE, 3);
        helper.assertTrue(QualityStacks.insert(QualityStacks.poolIntoNewQualityStack(carrots(FINE, 1), carrots(SUPERIOR, 1)), potato) == 0 && potato.getCount() == 3, "a carrot quality stack should refuse potatoes");
        ItemStack nearlyFull = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 64), carrots(SUPERIOR, 64));
        QualityStacks.insert(nearlyFull, carrots(CRUDE, 64));
        QualityStacks.insert(nearlyFull, carrots(STANDARD, 60));
        ItemStack partial = carrots(MASTERWORK, 10);
        helper.assertTrue(QualityStacks.insert(nearlyFull, partial) == 4 && partial.getCount() == 6, "an almost full quality stack should take only what fits");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void extractTakesTheBestTierFirstOneStackAtATime(GameTestHelper helper) {
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 5), carrots(null, 3));
        QualityStacks.insert(qualityStack, carrots(MASTERWORK, 64));
        QualityStacks.insert(qualityStack, carrots(MASTERWORK, 6));
        QualityStacks.insert(qualityStack, carrots(STANDARD, 4));
        QualityStacks.insert(qualityStack, carrots(REMOVED_TIER, 2));
        assertExtracts(helper, qualityStack, MASTERWORK, 64);
        assertExtracts(helper, qualityStack, MASTERWORK, 6);
        assertExtracts(helper, qualityStack, FINE, 5);
        assertExtracts(helper, qualityStack, STANDARD, 4);
        assertExtracts(helper, qualityStack, null, 3);
        assertExtracts(helper, qualityStack, REMOVED_TIER, 2);
        helper.assertTrue(qualityStack.isEmpty(), "the quality stack should be gone once its last item is taken");
        helper.assertTrue(QualityStacks.extractBest(qualityStack).isEmpty(), "an empty quality stack should give nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aSingleTierThatFitsOneStackCollapsesBack(GameTestHelper helper) {
        ItemStack oneTier = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 30), carrots(FINE, 10));
        ItemStack collapsed = QualityStacks.collapseToSingleStack(oneTier).orElseThrow();
        helper.assertTrue(collapsed.is(Items.CARROT) && collapsed.getCount() == 40 && FINE.equals(collapsed.get(QualityAPI.QUALITY.get())), "one tier of 40 should collapse to 40 fine carrots");
        helper.assertTrue(oneTier.isEmpty(), "collapsing should use up the quality stack so the carrots exist only once");
        ItemStack oneTierOverAStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 64), carrots(FINE, 6));
        helper.assertTrue(QualityStacks.collapseToSingleStack(oneTierOverAStack).isEmpty() && total(oneTierOverAStack) == 70, "one tier of 70 should stay a quality stack");
        ItemStack twoTiers = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 1), carrots(SUPERIOR, 1));
        helper.assertTrue(QualityStacks.collapseToSingleStack(twoTiers).isEmpty() && total(twoTiers) == 2, "two tiers should stay a quality stack");
        ItemStack plainOnly = QualityStacks.collapseToSingleStack(QualityStacks.poolIntoNewQualityStack(carrots(null, 2), carrots(null, 3))).orElseThrow();
        helper.assertTrue(plainOnly.is(Items.CARROT) && plainOnly.getCount() == 5 && !plainOnly.has(QualityAPI.QUALITY.get()), "plain carrots should collapse to plain carrots");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void invalidContentsAreRejectedWhenLoadedOrReceived(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        assertRejected(helper, "{item:{id:\"minecraft:carrot\"},entries:[{tier:\"quality_api:fine\",count:2147483647},{tier:\"quality_api:fine\",count:1}]}", "overflowing counts");
        assertRejected(helper, "{item:{id:\"minecraft:carrot\"},entries:[{tier:\"quality_api:fine\",count:300}]}", "more than the capacity");
        assertRejected(helper, "{item:{id:\"minecraft:carrot\"},entries:[]}", "no entries");
        assertRejected(helper, "{item:{id:\"minecraft:iron_sword\"},entries:[{count:1}]}", "a damageable item");
        assertRejected(helper, "{item:{id:\"quality_api:quality_stack\"},entries:[{count:1}]}", "a quality stack inside a quality stack");
        QualityStackContents withQualityOnTheItem = parse(helper, "{item:{id:\"minecraft:carrot\",components:{\"quality_api:quality\":\"quality_api:fine\"}},entries:[{tier:\"quality_api:superior\",count:3}]}").getOrThrow();
        helper.assertTrue(!withQualityOnTheItem.item().has(QualityAPI.QUALITY.get()), "a quality on the stored item should be stripped when loaded");
        RegistryFriendlyByteBuf overCapacity = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ItemStack.STREAM_CODEC.encode(overCapacity, new ItemStack(Items.CARROT));
        overCapacity.writeVarInt(1);
        overCapacity.writeBoolean(true);
        overCapacity.writeResourceLocation(FINE);
        overCapacity.writeVarInt(1000);
        boolean rejected = false;
        try {
            QualityStackContents.STREAM_CODEC.decode(overCapacity);
        } catch (DecoderException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a received quality stack over capacity should be rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void contentsSaveSyncAndCompareByValue(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ItemStack fineFirst = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 7), carrots(SUPERIOR, 9));
        QualityStacks.insert(fineFirst, carrots(REMOVED_TIER, 2));
        ItemStack superiorFirst = QualityStacks.poolIntoNewQualityStack(carrots(REMOVED_TIER, 2), carrots(SUPERIOR, 9));
        QualityStacks.insert(superiorFirst, carrots(FINE, 7));
        QualityStackContents contents = QualityStacks.contents(fineFirst);
        helper.assertTrue(contents.equals(QualityStacks.contents(superiorFirst)) && contents.hashCode() == QualityStacks.contents(superiorFirst).hashCode(),
                "the same items pooled in a different order should be equal");
        helper.assertTrue(ItemStack.isSameItemSameComponents(fineFirst, superiorFirst), "two quality stacks with the same contents should match");
        Tag saved = QualityStackContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents).getOrThrow();
        helper.assertTrue(contents.equals(QualityStackContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), saved).getOrThrow()), "contents should survive saving");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        QualityStackContents.STREAM_CODEC.encode(buffer, contents);
        helper.assertTrue(contents.equals(QualityStackContents.STREAM_CODEC.decode(buffer)), "contents should survive syncing");
        ItemStack reloaded = ItemStack.parse(registries, fineFirst.save(registries)).orElseThrow();
        helper.assertTrue(ItemStack.matches(reloaded, fineFirst), "a saved quality stack should load back identical");
        assertExtracts(helper, reloaded, SUPERIOR, 9);
        assertExtracts(helper, reloaded, FINE, 7);
        assertExtracts(helper, reloaded, REMOVED_TIER, 2);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aDestroyedQualityStackSpillsItsContents(GameTestHelper helper) {
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 64), carrots(FINE, 64));
        QualityStacks.insert(qualityStack, carrots(SUPERIOR, 10));
        Vec3 spot = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        ItemEntity dropped = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, qualityStack);
        helper.getLevel().addFreshEntity(dropped);
        dropped.hurt(helper.getLevel().damageSources().inFire(), 100.0F);
        List<ItemEntity> spilled = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(spot, spot).inflate(2.0), entity -> entity.isAlive() && entity.getItem().is(Items.CARROT));
        try {
            List<String> spilledStacks = spilled.stream().map(entity -> entity.getItem().getCount() + " " + entity.getItem().get(QualityAPI.QUALITY.get())).sorted().toList();
            helper.assertTrue(!dropped.isAlive() && spilledStacks.equals(List.of("10 " + SUPERIOR, "64 " + FINE, "64 " + FINE)),
                    "a destroyed quality stack should spill 64 + 64 fine and 10 superior carrots, spilled " + spilledStacks);
        } finally {
            spilled.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, String snbt, String reason) {
        helper.assertTrue(parse(helper, snbt).isError(), "contents with " + reason + " should be rejected");
    }

    private static DataResult<QualityStackContents> parse(GameTestHelper helper, String snbt) {
        try {
            return QualityStackContents.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), TagParser.parseTag(snbt));
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void assertExtracts(GameTestHelper helper, ItemStack qualityStack, @Nullable ResourceLocation tier, int count) {
        int totalBefore = total(qualityStack);
        ItemStack extracted = QualityStacks.extractBest(qualityStack);
        helper.assertTrue(extracted.is(Items.CARROT) && extracted.getCount() == count && Objects.equals(tier, extracted.get(QualityAPI.QUALITY.get())),
                "expected " + count + " carrots of " + tier + ", got " + extracted.getCount() + " of " + extracted.get(QualityAPI.QUALITY.get()));
        helper.assertTrue(total(qualityStack) == totalBefore - count, "extracting should remove exactly what came out");
    }

    private static List<QualityStackContents.Entry> counts(ItemStack qualityStack) {
        return QualityStacks.contents(qualityStack).entries();
    }

    private static int total(ItemStack qualityStack) {
        QualityStackContents contents = QualityStacks.contents(qualityStack);
        return contents == null ? 0 : contents.total();
    }

    private static ItemStack carrots(@Nullable ResourceLocation tier, int count) {
        return stack(Items.CARROT, tier, count);
    }

    private static ItemStack stack(Item item, @Nullable ResourceLocation tier, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (tier != null) stack.set(QualityAPI.QUALITY.get(), tier);
        return stack;
    }
}
