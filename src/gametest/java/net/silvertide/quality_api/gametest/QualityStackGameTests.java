package net.silvertide.quality_api.gametest;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.stack.QualityStackContents;
import net.silvertide.quality_api.stack.QualityStacks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@GameTestHolder(QualityAPI.MOD_ID)
@PrefixGameTestTemplate(false)
public class QualityStackGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation STANDARD = QualityAPI.id("standard");
    private static final ResourceLocation FINE = QualityAPI.id("fine");
    private static final ResourceLocation SUPERIOR = QualityAPI.id("superior");
    private static final ResourceLocation MASTERWORK = QualityAPI.id("masterwork");
    private static final ResourceLocation REMOVED_TIER = QualityAPI.id("removed_tier");
    private static final int FIRST_INVENTORY_SLOT = 9;
    private static final int SECOND_INVENTORY_SLOT = 10;
    private static final int CRAFTING_RESULT_SLOT = 0;
    private static final int PRIMARY_BUTTON = 0;
    private static final int SECONDARY_BUTTON = 1;
    private static final GameProfile CLICKING_PLAYER_PROFILE = new GameProfile(UUID.fromString("5f0c2a7e-3b1d-4c55-9a43-2b8f6e0d1c11"), "quality-stack-clicks");
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
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 30), carrots(SUPERIOR, 34));
        helper.assertTrue(total(qualityStack) == CARROT_CAPACITY, "one stack's worth across tiers should fill a quality stack");
        ItemStack secondFullStack = carrots(SUPERIOR, 64);
        helper.assertTrue(total(QualityStacks.poolIntoNewQualityStack(carrots(FINE, 64), secondFullStack)) == CARROT_CAPACITY && secondFullStack.getCount() == 64,
                "two full stacks cannot both fit, so the second is left whole");
        ItemStack overflow = carrots(MASTERWORK, 10);
        helper.assertTrue(QualityStacks.insert(qualityStack, overflow) == 0 && overflow.getCount() == 10, "a full quality stack should take nothing and leave the stack whole");
        ItemStack potato = stack(Items.POTATO, FINE, 3);
        helper.assertTrue(QualityStacks.insert(QualityStacks.poolIntoNewQualityStack(carrots(FINE, 1), carrots(SUPERIOR, 1)), potato) == 0 && potato.getCount() == 3, "a carrot quality stack should refuse potatoes");
        ItemStack nearlyFull = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 30), carrots(SUPERIOR, 30));
        ItemStack partial = carrots(MASTERWORK, 10);
        helper.assertTrue(QualityStacks.insert(nearlyFull, partial) == 4 && partial.getCount() == 6, "an almost full quality stack should take only what fits");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void extractTakesTheBestTierFirstOneStackAtATime(GameTestHelper helper) {
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 5), carrots(null, 3));
        QualityStacks.insert(qualityStack, carrots(MASTERWORK, 40));
        QualityStacks.insert(qualityStack, carrots(STANDARD, 4));
        QualityStacks.insert(qualityStack, carrots(REMOVED_TIER, 2));
        assertExtracts(helper, qualityStack, MASTERWORK, 40);
        assertExtracts(helper, qualityStack, FINE, 5);
        assertExtracts(helper, qualityStack, STANDARD, 4);
        assertExtracts(helper, qualityStack, null, 3);
        assertExtracts(helper, qualityStack, REMOVED_TIER, 2);
        helper.assertTrue(qualityStack.isEmpty(), "the quality stack should be gone once its last item is taken");
        helper.assertTrue(QualityStacks.extractNext(qualityStack, Integer.MAX_VALUE).isEmpty(), "an empty quality stack should give nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aSingleTierThatFitsOneStackCollapsesBack(GameTestHelper helper) {
        ItemStack oneTier = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 30), carrots(FINE, 10));
        ItemStack collapsed = QualityStacks.collapseToSingleStack(oneTier).orElseThrow();
        helper.assertTrue(collapsed.is(Items.CARROT) && collapsed.getCount() == 40 && FINE.equals(collapsed.get(QualityAPI.QUALITY.get())), "one tier of 40 should collapse to 40 fine carrots");
        helper.assertTrue(oneTier.isEmpty(), "collapsing should use up the quality stack so the carrots exist only once");
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
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 30), carrots(FINE, 20));
        QualityStacks.insert(qualityStack, carrots(SUPERIOR, 10));
        Vec3 spot = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        ItemEntity dropped = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, qualityStack);
        helper.getLevel().addFreshEntity(dropped);
        dropped.hurt(helper.getLevel().damageSources().inFire(), 100.0F);
        List<ItemEntity> spilled = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(spot, spot).inflate(2.0), entity -> entity.isAlive() && entity.getItem().is(Items.CARROT));
        try {
            List<String> spilledStacks = spilled.stream().map(entity -> entity.getItem().getCount() + " " + entity.getItem().get(QualityAPI.QUALITY.get())).sorted().toList();
            helper.assertTrue(!dropped.isAlive() && spilledStacks.equals(List.of("10 " + SUPERIOR, "50 " + FINE)),
                    "a destroyed quality stack should spill 50 fine and 10 superior carrots, spilled " + spilledStacks);
        } finally {
            spilled.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rightClickingCarrotsOntoAnotherQualityMakesAQualityStack(GameTestHelper helper) {
        ServerPlayer player = clickingPlayer(helper);
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, carrots(FINE, 10));
        player.inventoryMenu.setCarried(carrots(SUPERIOR, 20));
        Map<String, Integer> before = carrotsByTier(player);
        rightClick(player, FIRST_INVENTORY_SLOT);
        ItemStack inSlot = player.getInventory().getItem(FIRST_INVENTORY_SLOT);
        helper.assertTrue(QualityStacks.isQualityStack(inSlot) && player.inventoryMenu.getCarried().isEmpty(), "right-clicking superior carrots onto fine ones should leave one quality stack and an empty cursor");
        helper.assertTrue(carrotsByTier(player).equals(before), "pooling should keep every carrot and its tier, had " + before + " now " + carrotsByTier(player));
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, carrots(FINE, 10));
        player.inventoryMenu.setCarried(carrots(FINE, 5));
        rightClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(player.getInventory().getItem(FIRST_INVENTORY_SLOT).getCount() == 11 && player.inventoryMenu.getCarried().getCount() == 4, "carrots of the same tier should keep vanilla's place-one");
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, carrots(FINE, 10));
        player.inventoryMenu.setCarried(carrots(SUPERIOR, 20));
        leftClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(SUPERIOR.equals(player.getInventory().getItem(FIRST_INVENTORY_SLOT).get(QualityAPI.QUALITY.get())) && FINE.equals(player.inventoryMenu.getCarried().get(QualityAPI.QUALITY.get())),
                "left-click should keep vanilla's swap");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rightClickingAQualityStackWithAnEmptyCursorTakesTheBestOut(GameTestHelper helper) {
        ServerPlayer player = clickingPlayer(helper);
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 10), carrots(MASTERWORK, 40));
        QualityStacks.insert(qualityStack, carrots(SUPERIOR, 6));
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, qualityStack);
        Map<String, Integer> before = carrotsByTier(player);
        rightClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(isCarrots(player.inventoryMenu.getCarried(), MASTERWORK, 40), "the first click should take all 40 masterwork carrots");
        helper.assertTrue(carrotsByTier(player).equals(before), "taking out should keep every carrot, had " + before + " now " + carrotsByTier(player));
        player.getInventory().setItem(SECOND_INVENTORY_SLOT, player.inventoryMenu.getCarried());
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        rightClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(isCarrots(player.inventoryMenu.getCarried(), SUPERIOR, 6), "the second click should take the 6 superior carrots");
        helper.assertTrue(isCarrots(player.getInventory().getItem(FIRST_INVENTORY_SLOT), FINE, 10), "a quality stack left with only 10 fine carrots should turn back into them");
        helper.assertTrue(carrotsByTier(player).equals(before), "collapsing should keep every carrot, had " + before + " now " + carrotsByTier(player));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rightClickingCarrotsOntoAQualityStackPutsThemIn(GameTestHelper helper) {
        ServerPlayer player = clickingPlayer(helper);
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, QualityStacks.poolIntoNewQualityStack(carrots(FINE, 1), carrots(SUPERIOR, 1)));
        player.inventoryMenu.setCarried(carrots(MASTERWORK, 30));
        Map<String, Integer> before = carrotsByTier(player);
        rightClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty() && total(player.getInventory().getItem(FIRST_INVENTORY_SLOT)) == 32, "30 masterwork carrots should go into the quality stack");
        helper.assertTrue(carrotsByTier(player).equals(before), "putting in should keep every carrot, had " + before + " now " + carrotsByTier(player));
        player.inventoryMenu.setCarried(stack(Items.POTATO, FINE, 3));
        rightClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(player.inventoryMenu.getCarried().is(QualityAPI.QUALITY_STACK.get()) && player.getInventory().getItem(FIRST_INVENTORY_SLOT).is(Items.POTATO),
                "a potato should not go into a carrot quality stack; right-click falls back to vanilla's swap");
        helper.assertTrue(carrotsByTier(player).equals(before), "a refused potato should not change the carrots");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void carryingAQualityStackPicksUpAndPutsDownItems(GameTestHelper helper) {
        ServerPlayer player = clickingPlayer(helper);
        player.inventoryMenu.setCarried(QualityStacks.poolIntoNewQualityStack(carrots(FINE, 5), carrots(SUPERIOR, 5)));
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, carrots(MASTERWORK, 20));
        Map<String, Integer> before = carrotsByTier(player);
        rightClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(player.getInventory().getItem(FIRST_INVENTORY_SLOT).isEmpty() && total(player.inventoryMenu.getCarried()) == 30, "a carried quality stack should take in the clicked carrots");
        rightClick(player, FIRST_INVENTORY_SLOT);
        rightClick(player, SECOND_INVENTORY_SLOT);
        helper.assertTrue(isCarrots(player.getInventory().getItem(FIRST_INVENTORY_SLOT), MASTERWORK, 20) && isCarrots(player.getInventory().getItem(SECOND_INVENTORY_SLOT), SUPERIOR, 5),
                "a carried quality stack should put down its best tier into each empty slot clicked");
        helper.assertTrue(isCarrots(player.inventoryMenu.getCarried(), FINE, 5), "a carried quality stack left with one small tier should turn back into it");
        helper.assertTrue(carrotsByTier(player).equals(before), "carrying should keep every carrot, had " + before + " now " + carrotsByTier(player));
        player.inventoryMenu.setCarried(QualityStacks.poolIntoNewQualityStack(carrots(FINE, 5), carrots(SUPERIOR, 5)));
        rightClick(player, CRAFTING_RESULT_SLOT);
        helper.assertTrue(player.inventoryMenu.getSlot(CRAFTING_RESULT_SLOT).getItem().isEmpty() && total(player.inventoryMenu.getCarried()) == 10, "the crafting result slot should take nothing out of a quality stack");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void slotsThatCopyOrLimitTheirStackAreRespected(GameTestHelper helper) {
        ServerPlayer player = clickingPlayer(helper);
        SimpleContainer copyingContainer = new SimpleContainer(1);
        Slot copyingSlot = new Slot(copyingContainer, 0, 0, 0) {
            @Override
            public ItemStack getItem() {
                return super.getItem().copy();
            }
        };
        copyingContainer.setItem(0, QualityStacks.poolIntoNewQualityStack(carrots(FINE, 10), carrots(MASTERWORK, 20)));
        SimpleContainer cursor = new SimpleContainer(1);
        rightClickWithEvent(player, copyingSlot, cursor);
        helper.assertTrue(isCarrots(cursor.getItem(0), MASTERWORK, 20) && isCarrots(copyingContainer.getItem(0), FINE, 10),
                "taking out of a slot that hands out copies should write the change back, not duplicate");
        copyingContainer.setItem(0, QualityStacks.poolIntoNewQualityStack(carrots(FINE, 10), carrots(MASTERWORK, 20)));
        cursor.setItem(0, carrots(SUPERIOR, 7));
        rightClickWithEvent(player, copyingSlot, cursor);
        helper.assertTrue(cursor.getItem(0).isEmpty() && total(copyingContainer.getItem(0)) == 37, "putting into a slot that hands out copies should write the change back, not delete");

        SimpleContainer singleItemContainer = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        Slot singleItemSlot = new Slot(singleItemContainer, 0, 0, 0);
        singleItemContainer.setItem(0, carrots(FINE, 1));
        cursor.setItem(0, carrots(SUPERIOR, 20));
        helper.assertFalse(rightClickWithEvent(player, singleItemSlot, cursor), "a slot that holds one carrot should not take a quality stack of 21");
        singleItemContainer.setItem(0, QualityStacks.poolIntoNewQualityStack(carrots(FINE, 1), carrots(SUPERIOR, 1)));
        helper.assertFalse(rightClickWithEvent(player, singleItemSlot, cursor), "a slot that holds one carrot should not let its quality stack grow");

        copyingContainer.setItem(0, new ItemStack(QualityAPI.QUALITY_STACK.get()));
        cursor.setItem(0, ItemStack.EMPTY);
        helper.assertFalse(rightClickWithEvent(player, copyingSlot, cursor), "a quality stack with no contents should fall through to vanilla");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tooltipListsEachTierBestFirstWithTheTotal(GameTestHelper helper) {
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 10), carrots(null, 3));
        QualityStacks.insert(qualityStack, carrots(MASTERWORK, 20));
        QualityStacks.insert(qualityStack, carrots(REMOVED_TIER, 2));
        List<String> lines = qualityStack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        String masterwork = Qualities.displayName(carrots(MASTERWORK, 1), MASTERWORK).getString();
        String fine = Qualities.displayName(carrots(FINE, 1), FINE).getString();
        List<String> expected = List.of("Carrot", masterwork + " ×20", fine + " ×10", "Plain ×3", REMOVED_TIER + " ×2", "35 / 64", "Scroll to Select - Right Click to Remove");
        helper.assertTrue(lines.equals(expected), "the tooltip should list tiers best first then the total, expected " + expected + " got " + lines);
        ItemStack goldenApples = QualityStacks.poolIntoNewQualityStack(stack(Items.ENCHANTED_GOLDEN_APPLE, FINE, 1), stack(Items.ENCHANTED_GOLDEN_APPLE, SUPERIOR, 1));
        helper.assertTrue(Objects.equals(goldenApples.getHoverName().getStyle().getColor(), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).getRarity().getStyleModifier().apply(Style.EMPTY).getColor()) && goldenApples.hasFoil(),
                "a quality stack should take its item's rarity colour and glint");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pickingUpCarrotsFillsACarriedQualityStackFirst(GameTestHelper helper) {
        ServerPlayer player = clickingPlayer(helper);
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, QualityStacks.poolIntoNewQualityStack(carrots(FINE, 10), carrots(SUPERIOR, 10)));
        ItemEntity masterwork = dropAt(helper, carrots(MASTERWORK, 20));
        masterwork.playerTouch(player);
        helper.assertTrue(!masterwork.isAlive() && total(player.getInventory().getItem(FIRST_INVENTORY_SLOT)) == 40 && looseCarrots(player) == 0,
                "20 picked-up masterwork carrots should all go into the quality stack");
        helper.assertTrue(masterwork.getItem().isEmpty(), "a used-up item entity should hold nothing, so a later pickup listener cannot hand its carrots out again");
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, QualityStacks.poolIntoNewQualityStack(carrots(FINE, 32), carrots(SUPERIOR, 30)));
        ItemEntity overflow = dropAt(helper, carrots(MASTERWORK, 10));
        overflow.playerTouch(player);
        helper.assertTrue(!overflow.isAlive() && total(player.getInventory().getItem(FIRST_INVENTORY_SLOT)) == CARROT_CAPACITY && looseCarrots(player) == 8,
                "what does not fit in the quality stack should be picked up normally");
        ItemEntity potatoes = dropAt(helper, stack(Items.POTATO, FINE, 4));
        potatoes.playerTouch(player);
        helper.assertTrue(!potatoes.isAlive() && QualityStacks.contents(player.getInventory().getItem(FIRST_INVENTORY_SLOT)).accepts(carrots(FINE, 1)), "potatoes should be picked up normally and never enter a carrot quality stack");
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, QualityStacks.poolIntoNewQualityStack(carrots(FINE, 1), carrots(SUPERIOR, 1)));
        ItemEntity justThrown = dropAt(helper, carrots(MASTERWORK, 5));
        justThrown.setDefaultPickUpDelay();
        justThrown.playerTouch(player);
        helper.assertTrue(justThrown.isAlive() && total(player.getInventory().getItem(FIRST_INVENTORY_SLOT)) == 2, "an item with a pickup delay should not be taken in");
        justThrown.discard();
        helper.succeed();
    }

    private static ItemEntity dropAt(GameTestHelper helper, ItemStack stack) {
        Vec3 spot = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        ItemEntity itemEntity = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, stack);
        helper.getLevel().addFreshEntity(itemEntity);
        return itemEntity;
    }

    private static int looseCarrots(ServerPlayer player) {
        return player.getInventory().items.stream().filter(stack -> stack.is(Items.CARROT)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = EMPTY)
    public static void aChosenTierComesOutFirstUntilItRunsOut(GameTestHelper helper) {
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 10), carrots(MASTERWORK, 20));
        QualityStacks.insert(qualityStack, carrots(null, 3));
        helper.assertTrue(QualityStacks.nextOut(qualityStack).tier().equals(Optional.of(MASTERWORK)), "with nothing chosen the best tier should come out");
        helper.assertTrue(QualityStacks.select(qualityStack, Optional.of(FINE)), "a tier the quality stack holds should be choosable");
        helper.assertFalse(QualityStacks.select(qualityStack, Optional.of(SUPERIOR)), "a tier the quality stack does not hold should be refused");
        assertExtracts(helper, qualityStack, FINE, 10);
        helper.assertTrue(QualityStacks.nextOut(qualityStack).tier().equals(Optional.of(MASTERWORK)) && !qualityStack.has(QualityAPI.QUALITY_STACK_SELECTION.get()),
                "once the chosen tier runs out the choice should clear and the best tier come out again");
        helper.assertTrue(QualityStacks.selectNeighbour(qualityStack, 1).tier().isEmpty(), "scrolling toward worse from masterwork should choose plain");
        helper.assertTrue(QualityStacks.selectNeighbour(qualityStack, 1).tier().equals(Optional.of(MASTERWORK)), "scrolling past the worst should wrap to the best");
        helper.assertTrue(QualityStacks.selectNeighbour(qualityStack, -1).tier().isEmpty(), "scrolling toward better from the best should wrap to the worst");

        RegistryAccess registries = helper.getLevel().registryAccess();
        Tag saved = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), qualityStack).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), saved).getOrThrow();
        helper.assertTrue(!loaded.has(QualityAPI.QUALITY_STACK_SELECTION.get()) && total(loaded) == total(qualityStack), "the choice should not be saved, only the contents");

        ServerPlayer player = clickingPlayer(helper);
        ItemStack inSlot = QualityStacks.poolIntoNewQualityStack(carrots(FINE, 10), carrots(MASTERWORK, 20));
        QualityStacks.select(inSlot, Optional.of(FINE));
        player.getInventory().setItem(FIRST_INVENTORY_SLOT, inSlot);
        rightClick(player, FIRST_INVENTORY_SLOT);
        helper.assertTrue(isCarrots(player.inventoryMenu.getCarried(), FINE, 10) && isCarrots(player.getInventory().getItem(FIRST_INVENTORY_SLOT), MASTERWORK, 20),
                "right-clicking should take out the chosen tier, then the last tier left should turn back into normal carrots");
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
        ItemStack extracted = QualityStacks.extractNext(qualityStack, Integer.MAX_VALUE);
        helper.assertTrue(extracted.is(Items.CARROT) && extracted.getCount() == count && Objects.equals(tier, extracted.get(QualityAPI.QUALITY.get())),
                "expected " + count + " carrots of " + tier + ", got " + extracted.getCount() + " of " + extracted.get(QualityAPI.QUALITY.get()));
        helper.assertTrue(total(qualityStack) == totalBefore - count, "extracting should remove exactly what came out");
    }

    private static ServerPlayer clickingPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), CLICKING_PLAYER_PROFILE);
        player.getInventory().clearContent();
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static boolean rightClickWithEvent(ServerPlayer player, Slot slot, SimpleContainer cursor) {
        return CommonHooks.onItemStackedOn(cursor.getItem(0), slot.getItem(), slot, ClickAction.SECONDARY, player, SlotAccess.forContainer(cursor, 0));
    }

    private static void rightClick(ServerPlayer player, int slot) {
        player.inventoryMenu.clicked(slot, SECONDARY_BUTTON, ClickType.PICKUP, player);
    }

    private static void leftClick(ServerPlayer player, int slot) {
        player.inventoryMenu.clicked(slot, PRIMARY_BUTTON, ClickType.PICKUP, player);
    }

    private static boolean isCarrots(ItemStack stack, ResourceLocation tier, int count) {
        return stack.is(Items.CARROT) && stack.getCount() == count && tier.equals(stack.get(QualityAPI.QUALITY.get()));
    }

    private static Map<String, Integer> carrotsByTier(ServerPlayer player) {
        Map<String, Integer> counts = new TreeMap<>();
        List<ItemStack> held = new ArrayList<>(player.getInventory().items);
        held.add(player.inventoryMenu.getCarried());
        for (ItemStack stack : held) {
            QualityStackContents contents = QualityStacks.contents(stack);
            if (contents != null) {
                for (QualityStackContents.Entry entry : contents.entries()) counts.merge(entry.tier().map(ResourceLocation::toString).orElse("plain"), entry.count(), Integer::sum);
            } else if (stack.is(Items.CARROT)) {
                counts.merge(Objects.toString(stack.get(QualityAPI.QUALITY.get()), "plain"), stack.getCount(), Integer::sum);
            }
        }
        return counts;
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
