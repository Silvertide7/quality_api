package net.silvertide.quality_api.gametest;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.Traits;
import net.silvertide.quality_api.quality.Trait;
import net.silvertide.quality_api.stack.QualityStacks;

import java.util.List;
import java.util.stream.IntStream;

@GameTestHolder(QualityAPI.MOD_ID)
@PrefixGameTestTemplate(false)
public class TraitGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GOLDEN = QualityAPI.id("test_golden");
    private static final ResourceLocation STURDY = QualityAPI.id("test_sturdy");
    private static final ResourceLocation UNKNOWN_TRAIT = QualityAPI.id("unknown_trait");
    private static final ResourceLocation FINE = QualityAPI.id("fine");
    private static final ResourceLocation SUPERIOR = QualityAPI.id("superior");

    @GameTest(template = EMPTY)
    public static void traitsAreStoredSortedWithoutDuplicatesAndRemovedWhenEmpty(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        helper.assertTrue(Traits.add(sword, STURDY), "a loaded trait should be added");
        helper.assertTrue(Traits.add(sword, GOLDEN), "a second loaded trait should be added");
        helper.assertFalse(Traits.add(sword, GOLDEN), "a trait already held should not be added twice");
        helper.assertFalse(Traits.add(sword, UNKNOWN_TRAIT), "an unknown trait should be refused");
        helper.assertTrue(Traits.getIds(sword).equals(List.of(GOLDEN, STURDY)), "traits should be stored sorted");
        helper.assertTrue(Traits.tooltipLines(sword).size() == 1, "two traits should share one tooltip line");
        Traits.remove(sword, GOLDEN);
        Traits.remove(sword, STURDY);
        helper.assertFalse(sword.has(QualityAPI.TRAITS.get()), "removing the last trait should remove the component");
        helper.assertFalse(Traits.add(new ItemStack(QualityAPI.QUALITY_STACK.get()), GOLDEN), "blacklisted items should take no traits");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void decodedTraitsAreCanonicalSoOrderDoesNotSplitStacks(GameTestHelper helper) {
        List<ResourceLocation> decoded = Trait.IDS_CODEC.parse(JsonOps.INSTANCE, Trait.IDS_CODEC.encodeStart(JsonOps.INSTANCE, List.of(STURDY, GOLDEN, STURDY)).getOrThrow()).getOrThrow();
        helper.assertTrue(decoded.equals(List.of(GOLDEN, STURDY)), "decoding should sort and deduplicate");
        List<ResourceLocation> tooMany = IntStream.range(0, Trait.MAX_TRAITS_PER_HOLDER + 4).mapToObj(index -> QualityAPI.id("trait_" + index)).toList();
        List<ResourceLocation> capped = Trait.IDS_CODEC.parse(JsonOps.INSTANCE, Trait.IDS_CODEC.encodeStart(JsonOps.INSTANCE, tooMany).getOrThrow()).getOrThrow();
        helper.assertTrue(capped.size() == Trait.MAX_TRAITS_PER_HOLDER, "a list over the limit should be cut to the limit instead of failing to save");
        ItemStack first = new ItemStack(Items.CARROT);
        Traits.add(first, STURDY);
        Traits.add(first, GOLDEN);
        ItemStack second = new ItemStack(Items.CARROT);
        Traits.add(second, GOLDEN);
        Traits.add(second, STURDY);
        helper.assertTrue(ItemStack.isSameItemSameComponents(first, second), "the same traits added in any order should stack");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void qualityStacksPoolOnlyItemsWithTheSameTraits(GameTestHelper helper) {
        ItemStack goldenFine = carrot(FINE, GOLDEN);
        ItemStack goldenSuperior = carrot(SUPERIOR, GOLDEN);
        helper.assertTrue(QualityStacks.canPool(goldenFine, goldenSuperior), "carrots with the same traits should pool across tiers");
        helper.assertFalse(QualityStacks.canPool(goldenFine, carrot(SUPERIOR, null)), "a golden carrot should not pool with a plain one");
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(goldenFine, goldenSuperior);
        ItemStack extracted = QualityStacks.extractNext(qualityStack, 1);
        helper.assertTrue(Traits.has(extracted, GOLDEN), "an extracted carrot should keep its trait");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityTraitsAreAddedAndRemoved(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        helper.assertTrue(Traits.add(cow, GOLDEN), "a loaded trait should be added to an entity");
        helper.assertTrue(Traits.has(cow, GOLDEN), "the entity should hold the trait");
        Traits.remove(cow, GOLDEN);
        helper.assertFalse(cow.hasData(QualityAPI.ENTITY_TRAITS), "removing the last trait should remove the attachment");
        helper.succeed();
    }

    private static ItemStack carrot(ResourceLocation tier, ResourceLocation trait) {
        ItemStack carrot = new ItemStack(Items.CARROT);
        Qualities.apply(carrot, tier);
        if (trait != null) Traits.add(carrot, trait);
        return carrot;
    }
}
