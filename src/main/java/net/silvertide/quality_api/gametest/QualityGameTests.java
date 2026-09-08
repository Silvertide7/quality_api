package net.silvertide.quality_api.gametest;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityCraftEvent;
import net.silvertide.quality_api.craft.MinQualityIngredient;
import net.silvertide.quality_api.quality.QualityEffects;
import net.silvertide.quality_api.quality.Snapshot;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

@GameTestHolder(QualityAPI.MOD_ID)
@PrefixGameTestTemplate(false)
public class QualityGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation CRUDE = QualityAPI.id("crude");
    private static final ResourceLocation FINE = QualityAPI.id("fine");
    private static final ResourceLocation SUPERIOR = QualityAPI.id("superior");
    private static final ResourceLocation EXQUISITE = QualityAPI.id("exquisite");
    private static final ResourceLocation MASTERWORK = QualityAPI.id("masterwork");
    private static final int DIAMOND_PICKAXE_MAX_DAMAGE = Items.DIAMOND_PICKAXE.components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
    private static final int DAMAGE_FAR_BEYOND_REMAINING = 100;
    private static final int PARTIAL_DAMAGE = 1000;
    private static final int HEAVY_DAMAGE = 2000;

    @GameTest(template = EMPTY)
    public static void durabilityScalesLiveAndSkipsStackables(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        helper.assertTrue(Qualities.apply(pickaxe, MASTERWORK), "apply masterwork failed");
        helper.assertTrue(pickaxe.getMaxDamage() == Math.round(DIAMOND_PICKAXE_MAX_DAMAGE * 1.6F), "masterwork max damage was " + pickaxe.getMaxDamage());
        Qualities.remove(pickaxe);
        helper.assertTrue(pickaxe.getMaxDamage() == DIAMOND_PICKAXE_MAX_DAMAGE, "removed max damage was " + pickaxe.getMaxDamage());

        ItemStack arrow = new ItemStack(Items.ARROW, 16);
        helper.assertTrue(Qualities.apply(arrow, MASTERWORK), "apply to arrow failed");
        helper.assertTrue(!arrow.isDamageableItem() && arrow.getMaxDamage() == 0, "arrow became damageable");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void damageRescalesWhenTierChanges(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        Qualities.apply(pickaxe, MASTERWORK);
        int masterworkMax = pickaxe.getMaxDamage();
        pickaxe.setDamageValue(HEAVY_DAMAGE);
        Qualities.remove(pickaxe);
        int expected = Math.round((float) HEAVY_DAMAGE * DIAMOND_PICKAXE_MAX_DAMAGE / masterworkMax);
        helper.assertTrue(pickaxe.getDamageValue() == expected, "rescaled damage was " + pickaxe.getDamageValue() + " expected " + expected);
        helper.assertTrue(!Qualities.isBroken(pickaxe), "item should not be broken after rescale");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void attributesComeFromDataMap(GameTestHelper helper) {
        ItemStack sword = withQuality(Items.IRON_SWORD, MASTERWORK);
        ItemAttributeModifiers.Entry swordBonus = findModifier(sword, QualityAPI.id("quality_api/masterwork/0/mainhand"));
        helper.assertTrue(swordBonus != null && swordBonus.modifier().amount() == 2.0 && swordBonus.slot() == EquipmentSlotGroup.MAINHAND, "sword bonus missing or wrong");

        ItemStack chestplate = withQuality(Items.IRON_CHESTPLATE, FINE);
        ItemAttributeModifiers.Entry armorBonus = findModifier(chestplate, QualityAPI.id("quality_api/fine/0/chest"));
        helper.assertTrue(armorBonus != null && armorBonus.modifier().amount() == 0.5 && armorBonus.slot() == EquipmentSlotGroup.CHEST, "armor bonus missing or wrong");

        ItemStack stick = withQuality(Items.STICK, MASTERWORK);
        helper.assertTrue(stick.getAttributeModifiers().modifiers().isEmpty(), "stick should have no attribute bonus");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void specificEntryPatchesGlobal(GameTestHelper helper) {
        Registry<Item> items = helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM);
        QualityEffects.AttributeEffect attack = new QualityEffects.AttributeEffect(Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 1.0, Optional.empty());
        QualityEffects tagPatch = new QualityEffects(Optional.of(List.of(attack)), Optional.of(1.5F), Optional.empty(), Optional.of(1.6F), Optional.empty(), Optional.empty());
        QualityEffects itemPatch = new QualityEffects(Optional.empty(), Optional.of(2.0F), Optional.of(Map.of()), Optional.empty(), Optional.empty(), Optional.empty());
        Map<ResourceLocation, QualityEffects> merged = QualityEffects.mergeOverrides(items,
                Either.right(ResourceKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("iron_sword"))), Map.of(MASTERWORK, itemPatch),
                Either.left(TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("swords"))), Map.of(MASTERWORK, tagPatch, FINE, tagPatch));
        QualityEffects masterwork = merged.get(MASTERWORK);
        helper.assertTrue(masterwork.miningSpeedMultiplier().orElse(0F) == 2.0F, "item patch should win the field it names");
        helper.assertTrue(masterwork.attributes().equals(Optional.of(List.of(attack))) && masterwork.durabilityMultiplier().orElse(0F) == 1.6F, "fields the item patch does not name should come from the tag");
        helper.assertTrue(masterwork.enchantmentLevels().equals(Optional.of(Map.of())), "an empty map should be kept as an explicit removal");
        helper.assertTrue(tagPatch.equals(merged.get(FINE)), "tiers the item entry does not mention keep the tag entry");
        Map<ResourceLocation, QualityEffects> mergedTagFirst = QualityEffects.mergeOverrides(items,
                Either.left(TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("swords"))), Map.of(MASTERWORK, tagPatch, FINE, tagPatch),
                Either.right(ResourceKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("iron_sword"))), Map.of(MASTERWORK, itemPatch));
        helper.assertTrue(merged.equals(mergedTagFirst), "item entry should win regardless of which entry came first");
        QualityEffects laterTagPatch = new QualityEffects(Optional.empty(), Optional.of(3.0F), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Map<ResourceLocation, QualityEffects> mergedTags = QualityEffects.mergeOverrides(items,
                Either.left(TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("swords"))), Map.of(MASTERWORK, tagPatch),
                Either.left(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools"))), Map.of(MASTERWORK, laterTagPatch));
        QualityEffects tagOverTag = mergedTags.get(MASTERWORK);
        helper.assertTrue(tagOverTag.miningSpeedMultiplier().orElse(0F) == 3.0F && tagOverTag.durabilityMultiplier().orElse(0F) == 1.6F, "later tag entry should win only the fields it names");

        Snapshot.Resolved sword = Snapshot.current().resolved(Items.IRON_SWORD, MASTERWORK);
        helper.assertTrue(sword != null && sword.durabilityMultiplier() == 1.6F && sword.miningSpeedMultiplier() == 1.5F && sword.attributes().length == 1, "shipped sword entry should patch attributes onto the tier defaults");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void armorBonusesStackAcrossSlots(GameTestHelper helper) {
        ItemAttributeModifiers.Entry helmetBonus = findModifier(withQuality(Items.IRON_HELMET, FINE), QualityAPI.id("quality_api/fine/0/head"));
        ItemAttributeModifiers.Entry chestBonus = findModifier(withQuality(Items.IRON_CHESTPLATE, FINE), QualityAPI.id("quality_api/fine/0/chest"));
        helper.assertTrue(helmetBonus != null && chestBonus != null, "armor bonuses missing");
        AttributeInstance armor = new AttributeInstance(Attributes.ARMOR, instance -> {});
        armor.addTransientModifier(helmetBonus.modifier());
        armor.addTransientModifier(chestBonus.modifier());
        helper.assertTrue(armor.getValue() == 1.0, "two fine armor pieces should give +1.0 armor, gave " + armor.getValue());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bonusEnchantmentLevelsAreLiveAndClearedWhenBroken(GameTestHelper helper) {
        Holder<Enchantment> unbreaking = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.set(QualityAPI.QUALITY.get(), MASTERWORK);
        helper.assertTrue(pickaxe.getEnchantmentLevel(unbreaking) == 2, "masterwork should grant Unbreaking II, got " + pickaxe.getEnchantmentLevel(unbreaking));

        pickaxe.setDamageValue(pickaxe.getMaxDamage());
        helper.assertTrue(pickaxe.getEnchantmentLevel(unbreaking) == 0, "broken item should lose bonus enchantments");

        ItemStack sword = withQuality(Items.IRON_SWORD, MASTERWORK);
        sword.setDamageValue(sword.getMaxDamage());
        helper.assertTrue(sword.getAttributeModifiers().modifiers().isEmpty(), "broken item should have no attribute modifiers");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void unknownTierIsInert(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.set(QualityAPI.QUALITY.get(), QualityAPI.id("nope"));
        helper.assertTrue(pickaxe.getMaxDamage() == DIAMOND_PICKAXE_MAX_DAMAGE, "unknown tier scaled durability");
        helper.assertTrue(Qualities.getId(pickaxe).isEmpty() && Qualities.level(pickaxe) == 0, "unknown tier should read as no quality");
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        pickaxe.hurtAndBreak(1, helper.getLevel(), (LivingEntity) null, item -> {});
        helper.assertTrue(pickaxe.isEmpty(), "unknown tier should be destroyed like vanilla");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void breakingMakesBrokenInsteadOfDestroying(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        Qualities.apply(pickaxe, CRUDE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        pickaxe.hurtAndBreak(1, helper.getLevel(), (LivingEntity) null, item -> {});
        helper.assertTrue(!pickaxe.isEmpty(), "crude pickaxe was destroyed");
        helper.assertTrue(Qualities.isBroken(pickaxe), "crude pickaxe is not broken");
        helper.assertTrue(CRUDE.equals(Qualities.getId(pickaxe).orElse(null)), "lowest tier should not change");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void breakingDowngradesUnlessHighestIsProtected(GameTestHelper helper) {
        double previousChance = Config.BREAK_DOWNGRADE_CHANCE.get();
        boolean previousHighest = Config.HIGHEST_TIER_CAN_DOWNGRADE.get();
        Config.BREAK_DOWNGRADE_CHANCE.set(1.0);
        try {
            ItemStack pickaxe = breakFresh(helper, MASTERWORK);
            helper.assertTrue(EXQUISITE.equals(Qualities.getId(pickaxe).orElse(null)), "masterwork should downgrade to exquisite");
            helper.assertTrue(Qualities.isBroken(pickaxe) && pickaxe.getDamageValue() == pickaxe.getMaxDamage(), "downgraded item should be pinned at max damage");

            Config.HIGHEST_TIER_CAN_DOWNGRADE.set(false);
            ItemStack protectedPickaxe = breakFresh(helper, MASTERWORK);
            helper.assertTrue(MASTERWORK.equals(Qualities.getId(protectedPickaxe).orElse(null)), "protected masterwork should keep its tier");
            helper.assertTrue(Qualities.isBroken(protectedPickaxe), "protected masterwork should still be broken");
        } finally {
            Config.BREAK_DOWNGRADE_CHANCE.set(previousChance);
            Config.HIGHEST_TIER_CAN_DOWNGRADE.set(previousHighest);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void brokenItemTakesNoFurtherDamageOrDowngrade(GameTestHelper helper) {
        double previousChance = Config.BREAK_DOWNGRADE_CHANCE.get();
        Config.BREAK_DOWNGRADE_CHANCE.set(1.0);
        try {
            ItemStack pickaxe = breakFresh(helper, MASTERWORK);
            helper.assertTrue(EXQUISITE.equals(Qualities.getId(pickaxe).orElse(null)), "first break should downgrade once");
            pickaxe.hurtAndBreak(DAMAGE_FAR_BEYOND_REMAINING, helper.getLevel(), (LivingEntity) null, item -> {});
            pickaxe.hurtAndBreak(DAMAGE_FAR_BEYOND_REMAINING, helper.getLevel(), (LivingEntity) null, item -> {});
            helper.assertTrue(!pickaxe.isEmpty() && Qualities.isBroken(pickaxe), "broken item should survive further hits");
            helper.assertTrue(EXQUISITE.equals(Qualities.getId(pickaxe).orElse(null)), "further hits on a broken item must not downgrade again");
        } finally {
            Config.BREAK_DOWNGRADE_CHANCE.set(previousChance);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftingInheritsHighestAndSurvivesShiftClick(GameTestHelper helper) {
        ServerPlayer player = mockServerPlayer(helper);
        CraftingMenu menu = openCraftingMenu(helper, player);
        placeSwordRecipe(menu, FINE, MASTERWORK);

        ItemStack preview = menu.getSlot(0).getItem();
        helper.assertTrue(preview.is(Items.IRON_SWORD), "preview is not a sword");
        helper.assertTrue(MASTERWORK.equals(Qualities.getId(preview).orElse(null)), "preview should inherit masterwork");
        helper.assertTrue(player.getGameProfile().getName().equals(preview.get(QualityAPI.CRAFTER.get())), "crafter not recorded");

        menu.quickMoveStack(player, 0);
        ItemStack crafted = findInInventory(player, Items.IRON_SWORD);
        helper.assertTrue(crafted != null && MASTERWORK.equals(Qualities.getId(crafted).orElse(null)), "shift-clicked sword lost its quality");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftingAverageSkipsPlainIngredientsAndRollsTheFraction(GameTestHelper helper) {
        Config.Inheritance previous = Config.CRAFT_INHERITANCE.get();
        Config.CRAFT_INHERITANCE.set(Config.Inheritance.AVERAGE);
        try {
            ServerPlayer player = mockServerPlayer(helper);
            CraftingMenu menu = openCraftingMenu(helper, player);
            placeSwordRecipe(menu, SUPERIOR, MASTERWORK);
            ItemStack preview = menu.getSlot(0).getItem();
            helper.assertTrue(EXQUISITE.equals(Qualities.getId(preview).orElse(null)), "average of superior and masterwork should be exquisite, was " + Qualities.getId(preview));

            placeSwordRecipe(menu, FINE, MASTERWORK);
            ResourceLocation halfway = Qualities.getId(menu.getSlot(0).getItem()).orElse(null);
            helper.assertTrue(SUPERIOR.equals(halfway) || EXQUISITE.equals(halfway), "a 2.5 mean should roll superior or exquisite, was " + halfway);
            menu.getSlot(5).set(new ItemStack(Items.IRON_INGOT));
            ResourceLocation again = Qualities.getId(menu.getSlot(0).getItem()).orElse(null);
            helper.assertTrue(halfway.equals(again), "the preview roll must be stable until a craft happens");
        } finally {
            Config.CRAFT_INHERITANCE.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void inventoryGridInheritsIntoDamageableResultsOnly(GameTestHelper helper) {
        ServerPlayer player = mockServerPlayer(helper);
        InventoryMenu menu = new InventoryMenu(player.getInventory(), false, player);
        menu.getSlot(1).set(withQuality(Items.IRON_INGOT, MASTERWORK));
        menu.getSlot(2).set(new ItemStack(Items.FLINT));
        ItemStack flintAndSteel = menu.getSlot(0).getItem();
        helper.assertTrue(flintAndSteel.is(Items.FLINT_AND_STEEL) && MASTERWORK.equals(Qualities.getId(flintAndSteel).orElse(null)), "2x2 grid should inherit into flint and steel");

        menu.getSlot(1).set(withQuality(Items.OAK_PLANKS, MASTERWORK));
        menu.getSlot(2).set(ItemStack.EMPTY);
        menu.getSlot(3).set(new ItemStack(Items.OAK_PLANKS));
        ItemStack sticks = menu.getSlot(0).getItem();
        helper.assertTrue(sticks.is(Items.STICK) && Qualities.getId(sticks).isEmpty() && !sticks.has(QualityAPI.CRAFTER.get()), "stackable results should not inherit");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftEventCanStripQuality(GameTestHelper helper) {
        double[] observedMean = new double[1];
        Consumer<QualityCraftEvent> stripQuality = event -> {
            observedMean[0] = event.getIngredientMeanLevel().orElse(Double.NaN);
            event.setQuality(null);
        };
        NeoForge.EVENT_BUS.addListener(stripQuality);
        try {
            ServerPlayer player = mockServerPlayer(helper);
            CraftingMenu menu = openCraftingMenu(helper, player);
            placeSwordRecipe(menu, FINE, MASTERWORK);
            ItemStack preview = menu.getSlot(0).getItem();
            helper.assertTrue(preview.is(Items.IRON_SWORD) && !preview.has(QualityAPI.QUALITY.get()) && !preview.has(QualityAPI.CRAFTER.get()), "listener should leave the result plain");
            helper.assertTrue(observedMean[0] == 2.5, "event should expose the ingredient mean level, was " + observedMean[0]);
        } finally {
            NeoForge.EVENT_BUS.unregister(stripQuality);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void repairRecipeKeepsRemainingDurabilityAndTier(GameTestHelper helper) {
        ServerPlayer player = mockServerPlayer(helper);
        CraftingMenu menu = openCraftingMenu(helper, player);
        ItemStack first = withQuality(Items.DIAMOND_PICKAXE, FINE);
        ItemStack second = withQuality(Items.DIAMOND_PICKAXE, FINE);
        first.setDamageValue(PARTIAL_DAMAGE);
        second.setDamageValue(PARTIAL_DAMAGE);
        int fineMax = first.getMaxDamage();
        int expectedDamage = Math.max(fineMax - ((fineMax - PARTIAL_DAMAGE) * 2 + fineMax * 5 / 100), 0);
        menu.getSlot(1).set(first);
        menu.getSlot(2).set(second);

        ItemStack repaired = menu.getSlot(0).getItem();
        helper.assertTrue(repaired.is(Items.DIAMOND_PICKAXE), "repair result missing");
        helper.assertTrue(FINE.equals(Qualities.getId(repaired).orElse(null)), "repair result should inherit fine");
        helper.assertTrue(repaired.getComponentsPatch().get(DataComponents.MAX_DAMAGE) == null, "repair result should not carry an explicit max damage");
        helper.assertTrue(repaired.getMaxDamage() == fineMax, "repair result max damage should be the fine max");
        helper.assertTrue(repaired.getDamageValue() == expectedDamage, "repair damage was " + repaired.getDamageValue() + " expected " + expectedDamage);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ingredientAndLookupHelpers(GameTestHelper helper) {
        MinQualityIngredient atLeastSuperior = new MinQualityIngredient(HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(Items.STICK)), 2);
        helper.assertTrue(atLeastSuperior.test(withQuality(Items.STICK, SUPERIOR)), "superior stick should match");
        helper.assertTrue(atLeastSuperior.test(withQuality(Items.STICK, MASTERWORK)), "masterwork stick should match");
        helper.assertTrue(!atLeastSuperior.test(withQuality(Items.STICK, FINE)), "fine stick should not match");
        helper.assertTrue(!atLeastSuperior.test(new ItemStack(Items.STICK)), "plain stick should not match");
        Optional<ItemStack> displayed = atLeastSuperior.getItems().findFirst();
        helper.assertTrue(displayed.isPresent() && SUPERIOR.equals(Qualities.getId(displayed.get()).orElse(null)), "displayed ingredient should be superior");

        ItemStack stick = new ItemStack(Items.STICK);
        helper.assertTrue(Qualities.level(stick) == 0, "plain stick level should be 0");
        helper.assertTrue(SUPERIOR.equals(Qualities.forLevel(stick, 2).orElse(null)), "forLevel 2 should be superior");
        helper.assertTrue(Qualities.shift(stick, CRUDE, -1).isEmpty(), "nothing below crude");
        helper.assertTrue(SUPERIOR.equals(Qualities.shift(stick, FINE, 1).orElse(null)), "fine + 1 should be superior");
        helper.assertTrue(MASTERWORK.equals(Qualities.shift(stick, FINE, 3).orElse(null)), "fine + 3 should be masterwork");
        helper.assertTrue(Qualities.shift(stick, FINE, 4).isEmpty(), "fine + 4 is above the ladder");
        helper.assertTrue(Qualities.isHighestLevel(stick, MASTERWORK) && !Qualities.isHighestLevel(stick, FINE), "masterwork should be the highest applicable tier");
        helper.assertTrue(Qualities.shift(stick, QualityAPI.id("nope"), 1).isEmpty(), "unknown tier cannot shift");
        helper.assertTrue(Qualities.roll(stick, RandomSource.create(1)).isPresent(), "roll should pick a tier");
        OptionalDouble mean = Qualities.meanLevel(List.of(withQuality(Items.STICK, MASTERWORK), new ItemStack(Items.STICK), withQuality(Items.STICK, FINE)));
        helper.assertTrue(mean.isPresent() && mean.getAsDouble() == 2.5, "mean should skip plain stacks, was " + mean);
        helper.assertTrue(Qualities.meanLevel(List.of(new ItemStack(Items.STICK))).isEmpty(), "mean of plain stacks should be empty");
        helper.assertTrue(SUPERIOR.equals(Qualities.forLevel(stick, 2.0, RandomSource.create(1)).orElse(null)), "integer level should not roll");
        ResourceLocation rolled = Qualities.forLevel(stick, 2.5, RandomSource.create(7)).orElse(null);
        helper.assertTrue(SUPERIOR.equals(rolled) || EXQUISITE.equals(rolled), "fractional level should roll between neighbours, was " + rolled);
        helper.assertTrue(Qualities.idsByLevel().size() == 6, "expected six shipped tiers");
        helper.succeed();
    }

    private static ItemStack breakFresh(GameTestHelper helper, ResourceLocation tier) {
        ItemStack pickaxe = withQuality(Items.DIAMOND_PICKAXE, tier);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        pickaxe.hurtAndBreak(DAMAGE_FAR_BEYOND_REMAINING, helper.getLevel(), (LivingEntity) null, item -> {});
        return pickaxe;
    }

    private static ItemStack withQuality(Item item, ResourceLocation tier) {
        ItemStack stack = new ItemStack(item);
        Qualities.apply(stack, tier);
        return stack;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer mockServerPlayer(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    private static CraftingMenu openCraftingMenu(GameTestHelper helper, ServerPlayer player) {
        return new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
    }

    private static void placeSwordRecipe(AbstractContainerMenu menu, ResourceLocation ingotTier, ResourceLocation stickTier) {
        menu.getSlot(2).set(withQuality(Items.IRON_INGOT, ingotTier));
        menu.getSlot(5).set(new ItemStack(Items.IRON_INGOT));
        menu.getSlot(8).set(withQuality(Items.STICK, stickTier));
    }

    private static ItemAttributeModifiers.Entry findModifier(ItemStack stack, ResourceLocation id) {
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.modifier().id().equals(id)) return entry;
        }
        return null;
    }

    private static ItemStack findInInventory(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) return stack;
        }
        return null;
    }
}
