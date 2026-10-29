package net.silvertide.quality_api.gametest;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.datamaps.AdvancedDataMapType;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityBreedEvent;
import net.silvertide.quality_api.api.QualityCraftEvent;
import net.silvertide.quality_api.api.QualityFoodEatenEvent;
import net.silvertide.quality_api.api.QualityItemBrokenEvent;
import net.silvertide.quality_api.api.QualityLootEvent;
import net.silvertide.quality_api.craft.MinQualityIngredient;
import net.silvertide.quality_api.quality.EntityQualityEffects;
import net.silvertide.quality_api.quality.EntityQualityModifiers;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.QualityTooltipName;
import net.silvertide.quality_api.quality.QualityEffects;
import net.silvertide.quality_api.quality.Snapshot;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
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
    private static final int OVER_MAX_DAMAGE = 50;
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
        QualityEffects tagPatch = new QualityEffects(Optional.of(List.of(attack)), Optional.of(1.5F), Optional.empty(), Optional.of(1.6F), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        QualityEffects itemPatch = new QualityEffects(Optional.empty(), Optional.of(2.0F), Optional.of(Map.of()), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
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
        QualityEffects laterTagPatch = new QualityEffects(Optional.empty(), Optional.of(3.0F), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
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

        double previousChance = Config.BREAK_DOWNGRADE_CHANCE.get();
        Config.BREAK_DOWNGRADE_CHANCE.set(0.0);
        try {
            pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
            pickaxe.hurtAndBreak(DAMAGE_FAR_BEYOND_REMAINING, helper.getLevel(), (LivingEntity) null, item -> {});
            helper.assertTrue(MASTERWORK.equals(Qualities.getId(pickaxe).orElse(null)), "masterwork should keep its tier with downgrade chance 0");
            helper.assertTrue(pickaxe.getEnchantmentLevel(unbreaking) == 0, "broken item should lose bonus enchantments");

            ItemStack sword = withQuality(Items.IRON_SWORD, MASTERWORK);
            sword.setDamageValue(sword.getMaxDamage() - 1);
            sword.hurtAndBreak(DAMAGE_FAR_BEYOND_REMAINING, helper.getLevel(), (LivingEntity) null, item -> {});
            helper.assertTrue(sword.getAttributeModifiers().modifiers().isEmpty(), "broken item should have no attribute modifiers");
        } finally {
            Config.BREAK_DOWNGRADE_CHANCE.set(previousChance);
        }
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
    public static void breakingPostsQualityItemBrokenEvent(GameTestHelper helper) {
        List<ResourceLocation> brokenTiers = new ArrayList<>();
        Consumer<QualityItemBrokenEvent> recordBrokenTier = event -> brokenTiers.add(event.getQuality());
        NeoForge.EVENT_BUS.addListener(recordBrokenTier);
        try {
            breakFresh(helper, CRUDE);
        } finally {
            NeoForge.EVENT_BUS.unregister(recordBrokenTier);
        }
        helper.assertTrue(brokenTiers.equals(List.of(CRUDE)), "expected one QualityItemBrokenEvent for crude, got " + brokenTiers);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemBrokenBeforeSilverLibIsKeptWithoutRerolling(GameTestHelper helper) {
        double previousChance = Config.BREAK_DOWNGRADE_CHANCE.get();
        boolean previousHighest = Config.HIGHEST_TIER_CAN_DOWNGRADE.get();
        Config.BREAK_DOWNGRADE_CHANCE.set(1.0);
        Config.HIGHEST_TIER_CAN_DOWNGRADE.set(true);
        List<ResourceLocation> brokenTiers = new ArrayList<>();
        Consumer<QualityItemBrokenEvent> recordBrokenTier = event -> brokenTiers.add(event.getQuality());
        NeoForge.EVENT_BUS.addListener(recordBrokenTier);
        try {
            ItemStack pickaxe = withQuality(Items.DIAMOND_PICKAXE, MASTERWORK);
            pickaxe.setDamageValue(pickaxe.getMaxDamage());
            helper.assertTrue(!Qualities.isBroken(pickaxe), "an unmarked item at max damage is not yet SilverLib-broken");
            pickaxe.hurtAndBreak(DAMAGE_FAR_BEYOND_REMAINING, helper.getLevel(), (LivingEntity) null, item -> {});
            helper.assertTrue(!pickaxe.isEmpty() && Qualities.isBroken(pickaxe), "legacy broken item should be kept broken");
            helper.assertTrue(MASTERWORK.equals(Qualities.getId(pickaxe).orElse(null)), "legacy broken item must not roll a second downgrade");
            helper.assertTrue(brokenTiers.isEmpty(), "converting a legacy broken item must not post QualityItemBrokenEvent");
        } finally {
            NeoForge.EVENT_BUS.unregister(recordBrokenTier);
            Config.BREAK_DOWNGRADE_CHANCE.set(previousChance);
            Config.HIGHEST_TIER_CAN_DOWNGRADE.set(previousHighest);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void loginAndEquipConvertItemsBrokenBeforeSilverLib(GameTestHelper helper) {
        double previousChance = Config.BREAK_DOWNGRADE_CHANCE.get();
        boolean previousHighest = Config.HIGHEST_TIER_CAN_DOWNGRADE.get();
        boolean previousBrokenInstead = Config.BROKEN_INSTEAD_OF_DESTROYED.get();
        Config.BREAK_DOWNGRADE_CHANCE.set(1.0);
        Config.HIGHEST_TIER_CAN_DOWNGRADE.set(true);
        Config.BROKEN_INSTEAD_OF_DESTROYED.set(true);
        List<ResourceLocation> brokenTiers = new ArrayList<>();
        Consumer<QualityItemBrokenEvent> recordBrokenTier = event -> brokenTiers.add(event.getQuality());
        NeoForge.EVENT_BUS.addListener(recordBrokenTier);
        try {
            ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "legacy-broken-test"));
            ItemStack pickaxe = withQuality(Items.DIAMOND_PICKAXE, MASTERWORK);
            pickaxe.setDamageValue(pickaxe.getMaxDamage());
            ItemStack unknownTierPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            unknownTierPickaxe.set(QualityAPI.QUALITY.get(), QualityAPI.id("tier_that_does_not_exist"));
            unknownTierPickaxe.setDamageValue(unknownTierPickaxe.getMaxDamage());
            player.getInventory().setItem(0, pickaxe);
            player.getInventory().setItem(1, unknownTierPickaxe);

            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(Qualities.isBroken(pickaxe) && MASTERWORK.equals(Qualities.getId(pickaxe).orElse(null)), "login should convert a legacy broken item without a downgrade");
            helper.assertTrue(player.getInventory().getItem(1) == unknownTierPickaxe && !unknownTierPickaxe.isEmpty(), "login must not destroy an item whose tier is unknown");

            ItemStack chestplate = withQuality(Items.DIAMOND_CHESTPLATE, MASTERWORK);
            chestplate.setDamageValue(chestplate.getMaxDamage());
            NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.CHEST, ItemStack.EMPTY, chestplate));
            helper.assertTrue(Qualities.isBroken(chestplate) && MASTERWORK.equals(Qualities.getId(chestplate).orElse(null)), "equipping should convert a legacy broken item without a downgrade");

            ItemStack overMaxSword = withQuality(Items.DIAMOND_SWORD, MASTERWORK);
            overMaxSword.set(DataComponents.DAMAGE, overMaxSword.getMaxDamage() + OVER_MAX_DAMAGE);
            NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY, overMaxSword));
            helper.assertTrue(!Qualities.isBroken(overMaxSword), "an item pushed over max by a durability change is not a pre-SilverLib break and must not be converted");

            helper.assertTrue(brokenTiers.isEmpty(), "conversions must not post QualityItemBrokenEvent, got " + brokenTiers);
        } finally {
            NeoForge.EVENT_BUS.unregister(recordBrokenTier);
            Config.BREAK_DOWNGRADE_CHANCE.set(previousChance);
            Config.HIGHEST_TIER_CAN_DOWNGRADE.set(previousHighest);
            Config.BROKEN_INSTEAD_OF_DESTROYED.set(previousBrokenInstead);
        }
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

    @GameTest(template = EMPTY)
    public static void nameSetsPickWordsByItemAndFallBack(GameTestHelper helper) {
        helper.assertTrue(Qualities.nameSet(new ItemStack(Items.IRON_SWORD)).isEmpty(), "a sword should have no name set");
        helper.assertTrue(Qualities.nameSet(new ItemStack(Items.CARROT)).equals(Optional.of("food")), "a carrot should use the food name set");
        helper.assertTrue(Qualities.nameSet(new ItemStack(Items.WHEAT)).equals(Optional.of("food")), "wheat should use the food name set");
        helper.assertTrue(translationKey(Qualities.displayName(SUPERIOR)).equals("quality.quality_api.superior"), "default name should use the base key");
        helper.assertTrue(translationKey(Qualities.displayName(SUPERIOR, "food")).equals("quality.quality_api.superior.food"), "food set should use its own key");
        Component hardy = Qualities.displayName(SUPERIOR, "animal");
        helper.assertTrue(translationKey(hardy).equals("quality.quality_api.superior.animal") && hardy.getString().equals("Hardy"), "animal set should read Hardy, was " + hardy.getString());
        helper.assertTrue(hardy.getStyle().getColor() != null && hardy.getStyle().getColor().getValue() == 0xFCEE4B, "name set should keep the tier colour");
        AdvancedDataMapType<Item, String, ?> nameSetType = (AdvancedDataMapType<Item, String, ?>) QualityAPI.NAME_SET;
        Registry<Item> items = helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM);
        Either<TagKey<Item>, ResourceKey<Item>> carrotEntry = Either.right(ResourceKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("carrot")));
        Either<TagKey<Item>, ResourceKey<Item>> cropsEntry = Either.left(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "crops")));
        helper.assertTrue(nameSetType.merger().merge(items, carrotEntry, "veg", cropsEntry, "food").equals("veg"), "an item entry should beat a later tag entry");
        helper.assertTrue(nameSetType.merger().merge(items, cropsEntry, "food", carrotEntry, "veg").equals("veg"), "an item entry should beat an earlier tag entry");
        Component unknownSet = Qualities.displayName(SUPERIOR, "foo");
        helper.assertTrue(translationKey(unknownSet).equals("quality.quality_api.superior") && unknownSet.getString().equals("Superior"), "unknown set should fall back to the default name");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void forLevelWithoutStackSkipsFilteredTiers(GameTestHelper helper) {
        helper.assertTrue(Qualities.forLevel(2).equals(Optional.of(SUPERIOR)), "level 2 should be superior");
        helper.assertTrue(Qualities.forLevel(-1).equals(Optional.of(CRUDE)), "level -1 should be crude");
        helper.assertTrue(Qualities.forLevel(99).isEmpty(), "level 99 should match nothing");
        helper.assertTrue(Qualities.forLevel(2.0, RandomSource.create()).equals(Optional.of(SUPERIOR)), "a whole level should not roll up");
        helper.assertTrue(Qualities.forLevel(1.9999999, RandomSource.create(1)).equals(Optional.of(SUPERIOR)), "a fraction near 1 should roll up");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityQualityPersistsAndUsesEntityNameSets(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(Qualities.getId(cow).isEmpty() && Qualities.level(cow) == 0, "a new cow should have no quality");
        helper.assertFalse(Qualities.apply(cow, ResourceLocation.fromNamespaceAndPath("quality_api", "missing")), "an unknown tier should not apply to an entity");
        helper.assertTrue(Qualities.apply(cow, SUPERIOR), "superior should apply to a cow");
        helper.assertTrue(Qualities.getId(cow).equals(Optional.of(SUPERIOR)) && Qualities.level(cow) == 2, "the cow should be superior");
        Cow reloaded = EntityType.COW.create(helper.getLevel());
        reloaded.load(cow.saveWithoutId(new CompoundTag()));
        helper.assertTrue(Qualities.getId(reloaded).equals(Optional.of(SUPERIOR)), "entity quality should survive save and load");
        Component cowName = Qualities.displayName(cow, SUPERIOR);
        helper.assertTrue(cowName.getString().equals("Hardy"), "a cow should use the animal name set, was " + cowName.getString());
        helper.assertTrue(Qualities.nameSet(EntityType.TRADER_LLAMA.create(helper.getLevel())).equals(Optional.of("animal")), "a trader llama should use the animal name set through the animals tag");
        Component zombieName = Qualities.displayName(EntityType.ZOMBIE.create(helper.getLevel()), SUPERIOR);
        helper.assertTrue(zombieName.getString().equals("Fierce"), "a zombie should use the monster name set, was " + zombieName.getString());
        Component playerName = Qualities.displayName(FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "name-set-test")), SUPERIOR);
        helper.assertTrue(playerName.getString().equals("Stalwart"), "a player should use the player name set, was " + playerName.getString());
        Component golemName = Qualities.displayName(EntityType.IRON_GOLEM.create(helper.getLevel()), SUPERIOR);
        helper.assertTrue(golemName.getString().equals("Skilled"), "an iron golem should use the townsfolk name set, was " + golemName.getString());
        Component armorStandName = Qualities.displayName(EntityType.ARMOR_STAND.create(helper.getLevel()), SUPERIOR);
        helper.assertTrue(armorStandName.getString().equals("Superior"), "an entity without a name set should use the default name, was " + armorStandName.getString());
        Qualities.remove(cow);
        helper.assertTrue(Qualities.getId(cow).isEmpty(), "removing should clear the cow's quality");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void copyEntityQualityFunctionFollowsTheEntity(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        LootItemFunction copy = lootFunction(helper, "{\"function\": \"quality_api:copy_entity_quality\"}");
        LootItemFunction copyOneLower = lootFunction(helper, "{\"function\": \"quality_api:copy_entity_quality\", \"level_offset\": -1}");
        helper.assertTrue(copy.apply(new ItemStack(Items.BEEF), lootContext(helper, cow)).get(QualityAPI.QUALITY.get()) == null, "a cow without quality should drop plain beef");
        Qualities.apply(cow, SUPERIOR);
        helper.assertTrue(SUPERIOR.equals(copy.apply(new ItemStack(Items.BEEF), lootContext(helper, cow)).get(QualityAPI.QUALITY.get())), "a superior cow should drop superior beef");
        helper.assertTrue(FINE.equals(copyOneLower.apply(new ItemStack(Items.BEEF), lootContext(helper, cow)).get(QualityAPI.QUALITY.get())), "an offset of -1 should drop fine beef");
        helper.assertTrue(MASTERWORK.equals(copy.apply(withQuality(Items.BEEF, MASTERWORK), lootContext(helper, cow)).get(QualityAPI.QUALITY.get())), "an item that already has quality should keep it");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lootEventSeesTheKillAndCanChangeTheTier(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        ServerPlayer killer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "loot-event-test"));
        LootItemFunction copy = lootFunction(helper, "{\"function\": \"quality_api:copy_entity_quality\"}");
        List<QualityLootEvent> seen = new ArrayList<>();
        List<ResourceLocation> proposed = new ArrayList<>();
        Consumer<QualityLootEvent> promotePlainDropsAndClearSuperiorOnes = event -> {
            seen.add(event);
            proposed.add(event.getQuality());
            if (event.getSourceLevel().isEmpty()) event.setQuality(FINE);
            else if (event.getSourceLevel().getAsDouble() >= 2) event.setQuality(null);
        };
        NeoForge.EVENT_BUS.addListener(promotePlainDropsAndClearSuperiorOnes);
        try {
            ItemStack plainCowBeef = copy.apply(new ItemStack(Items.BEEF), lootContextKilledBy(helper, cow, killer));
            helper.assertTrue(FINE.equals(plainCowBeef.get(QualityAPI.QUALITY.get())), "a listener should be able to give a plain cow's beef a quality");
            helper.assertTrue(seen.size() == 1 && seen.getFirst().getPlayer() == killer && seen.getFirst().getEntity() == cow && proposed.getFirst() == null,
                    "the event for a plain cow should carry the killer, the cow and no proposed tier");
            Qualities.apply(cow, SUPERIOR);
            ItemStack superiorCowBeef = copy.apply(new ItemStack(Items.BEEF), lootContextKilledBy(helper, cow, killer));
            helper.assertTrue(superiorCowBeef.get(QualityAPI.QUALITY.get()) == null, "a listener clearing the tier should leave the beef plain");
            helper.assertTrue(seen.size() == 2 && seen.get(1).getSourceLevel().getAsDouble() == 2.0 && SUPERIOR.equals(proposed.get(1)),
                    "the event for a superior cow should carry level 2 and propose superior");
            copy.apply(withQuality(Items.BEEF, MASTERWORK), lootContextKilledBy(helper, cow, killer));
            helper.assertTrue(seen.size() == 2, "an item that already has quality should not post the event");
        } finally {
            NeoForge.EVENT_BUS.unregister(promotePlainDropsAndClearSuperiorOnes);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityMinQualityConditionComparesLevels(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        LootItemCondition atLeastSuperior = lootCondition(helper, "{\"condition\": \"quality_api:entity_min_quality\", \"min_level\": 2}");
        LootItemCondition atLeastStandard = lootCondition(helper, "{\"condition\": \"quality_api:entity_min_quality\", \"min_level\": 0}");
        helper.assertTrue(atLeastStandard.test(lootContext(helper, cow)), "an entity without quality should count as level 0");
        helper.assertFalse(atLeastSuperior.test(lootContext(helper, cow)), "an entity without quality should not pass min_level 2");
        Qualities.apply(cow, FINE);
        helper.assertFalse(atLeastSuperior.test(lootContext(helper, cow)), "a fine cow should not pass min_level 2");
        Qualities.apply(cow, SUPERIOR);
        helper.assertTrue(atLeastSuperior.test(lootContext(helper, cow)), "a superior cow should pass min_level 2");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityWithRemovedTierHasNoQualityAndCanBeCleared(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        try {
            cow.setData(QualityAPI.ENTITY_QUALITY, QualityAPI.id("removed_tier"));
            helper.assertTrue(Qualities.getId(cow).isEmpty() && Qualities.level(cow) == 0, "a removed tier should read as no quality");
            runCommand(helper, "quality entity " + cow.getStringUUID() + " remove");
            helper.assertFalse(cow.hasData(QualityAPI.ENTITY_QUALITY), "the command should clear a removed tier id");
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityQualitiesJsonDecodesAndTypeEntriesBeatTags(GameTestHelper helper) {
        Map<ResourceLocation, EntityQualityEffects> tagEntry = EntityQualityEffects.OVERRIDES_CODEC.parse(
                helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString("{\"quality_api:superior\": {\"attributes\": [{\"type\": \"minecraft:generic.max_health\", \"operation\": \"add_value\", \"amount\": 4.0}]}}")).getOrThrow();
        Map<ResourceLocation, EntityQualityEffects> typeEntry = Map.of(SUPERIOR, new EntityQualityEffects(Optional.of(List.of()), Optional.empty()));
        Map<ResourceLocation, EntityQualityEffects> emptyPatch = Map.of(SUPERIOR, new EntityQualityEffects(Optional.empty(), Optional.empty()));
        Either<TagKey<EntityType<?>>, ResourceKey<EntityType<?>>> animals = Either.left(TagKey.create(Registries.ENTITY_TYPE, QualityAPI.id("animals")));
        Either<TagKey<EntityType<?>>, ResourceKey<EntityType<?>>> cowKey = Either.right(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("cow")));
        Registry<EntityType<?>> entityTypes = helper.getLevel().registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        helper.assertTrue(tagEntry.get(SUPERIOR).attributes().orElseThrow().getFirst().amount() == 4.0, "the attribute amount should decode");
        helper.assertTrue(EntityQualityEffects.mergeOverrides(entityTypes, cowKey, typeEntry, animals, tagEntry).get(SUPERIOR).attributes().orElseThrow().isEmpty(), "a type entry should beat a later tag entry");
        helper.assertTrue(EntityQualityEffects.mergeOverrides(entityTypes, animals, tagEntry, cowKey, typeEntry).get(SUPERIOR).attributes().orElseThrow().isEmpty(), "a type entry should beat an earlier tag entry");
        helper.assertTrue(EntityQualityEffects.mergeOverrides(entityTypes, animals, tagEntry, cowKey, emptyPatch).get(SUPERIOR).attributes().orElseThrow().size() == 1, "a type entry that names no field should keep the tag's attributes");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityTintsBakePerTypeAndTierAndPatchOnTheirOwn(GameTestHelper helper) {
        Snapshot snapshot = Snapshot.current();
        helper.assertTrue(snapshot.entityTint(EntityType.COW, CRUDE) == 0xFF9CCC65, "the test pack tints crude cows green");
        helper.assertTrue(snapshot.entityTint(EntityType.PLAYER, MASTERWORK) == 0xFFFFD54F, "the test pack tints masterwork players gold");
        helper.assertTrue(snapshot.entityTint(EntityType.COW, MASTERWORK) == Snapshot.UNTINTED, "a tier entry without a tint is untinted");
        helper.assertTrue(snapshot.entityTint(EntityType.ZOMBIE, CRUDE) == Snapshot.UNTINTED, "a white tint bakes to untinted");
        helper.assertTrue(snapshot.entityTint(EntityType.PIG, CRUDE) == Snapshot.UNTINTED, "a type without an entry is untinted");
        helper.assertTrue(snapshot.entityTint(EntityType.COW, null) == Snapshot.UNTINTED, "no quality is untinted");
        Map<ResourceLocation, EntityQualityEffects> tagEntry = EntityQualityEffects.OVERRIDES_CODEC.parse(
                helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString("{\"quality_api:crude\": {\"tint\": \"#9CCC65\", \"attributes\": [{\"type\": \"minecraft:generic.max_health\", \"operation\": \"add_value\", \"amount\": -2.0}]}}")).getOrThrow();
        Map<ResourceLocation, EntityQualityEffects> whiteTintPatch = EntityQualityEffects.OVERRIDES_CODEC.parse(
                helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString("{\"quality_api:crude\": {\"tint\": \"#FFFFFF\"}}")).getOrThrow();
        Either<TagKey<EntityType<?>>, ResourceKey<EntityType<?>>> animals = Either.left(TagKey.create(Registries.ENTITY_TYPE, QualityAPI.id("animals")));
        Either<TagKey<EntityType<?>>, ResourceKey<EntityType<?>>> cowKey = Either.right(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("cow")));
        Registry<EntityType<?>> entityTypes = helper.getLevel().registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        EntityQualityEffects merged = EntityQualityEffects.mergeOverrides(entityTypes, animals, tagEntry, cowKey, whiteTintPatch).get(CRUDE);
        helper.assertTrue(merged.tint().orElseThrow().getValue() == 0xFFFFFF, "the type entry's tint should win");
        helper.assertTrue(merged.attributes().orElseThrow().size() == 1, "a tint-only patch should keep the tag's attributes");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityCommandSetsAndRemovesQuality(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        try {
            runCommand(helper, "quality entity " + cow.getStringUUID() + " set quality_api:superior");
            helper.assertTrue(Qualities.getId(cow).equals(Optional.of(SUPERIOR)), "the command should make the cow superior");
            runCommand(helper, "quality entity " + cow.getStringUUID() + " remove");
            helper.assertTrue(Qualities.getId(cow).isEmpty(), "the command should remove the cow's quality");
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void entityQualityAttributesApplyAndKeepHealthPercentage(GameTestHelper helper) {
        Snapshot loaded = Snapshot.current();
        installSnapshot(withEntityAttributes(loaded, EntityType.COW, SUPERIOR, List.of(
                new EntityQualityEffects.EntityAttributeEffect(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 10.0),
                new EntityQualityEffects.EntityAttributeEffect(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.5))));
        try {
            Cow cow = EntityType.COW.create(helper.getLevel());
            double baseSpeed = cow.getAttributeValue(Attributes.MOVEMENT_SPEED);
            cow.setHealth(5.0F);
            Qualities.apply(cow, SUPERIOR);
            helper.assertTrue(cow.getMaxHealth() == 20.0F && cow.getHealth() == 10.0F, "a superior cow should have 10/20 health, had " + cow.getHealth() + "/" + cow.getMaxHealth());
            helper.assertTrue(Math.abs(cow.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed * 1.5) < 1.0E-6, "a superior cow should be 50% faster");
            Qualities.apply(cow, FINE);
            helper.assertTrue(cow.getMaxHealth() == 10.0F && cow.getHealth() == 5.0F, "a tier without entity attributes should restore 5/10 health, had " + cow.getHealth() + "/" + cow.getMaxHealth());
            Qualities.apply(cow, SUPERIOR);
            Qualities.remove(cow);
            helper.assertTrue(cow.getMaxHealth() == 10.0F && Math.abs(cow.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 1.0E-6, "removing quality should remove its modifiers");
            Qualities.apply(cow, SUPERIOR);
            cow.setHealth(18.0F);
            Cow reloaded = EntityType.COW.create(helper.getLevel());
            reloaded.load(cow.saveWithoutId(new CompoundTag()));
            helper.assertTrue(reloaded.getMaxHealth() == 20.0F && reloaded.getHealth() == 18.0F, "a loaded cow should keep its saved 18/20 health, had " + reloaded.getHealth() + "/" + reloaded.getMaxHealth());
        } finally {
            installSnapshot(loaded);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void conversionKeepsQualityAndUsesTheNewTypesAttributes(GameTestHelper helper) {
        Snapshot loaded = Snapshot.current();
        Snapshot pigOnly = withEntityAttributes(loaded, EntityType.PIG, SUPERIOR, List.of(
                new EntityQualityEffects.EntityAttributeEffect(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.5)));
        installSnapshot(withEntityAttributes(pigOnly, EntityType.ZOMBIFIED_PIGLIN, SUPERIOR, List.of(
                new EntityQualityEffects.EntityAttributeEffect(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 10.0))));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        ZombifiedPiglin piglin = null;
        try {
            Qualities.apply(pig, SUPERIOR);
            piglin = pig.convertTo(EntityType.ZOMBIFIED_PIGLIN, false);
            helper.assertTrue(piglin != null, "the pig should convert");
            EventHooks.onLivingConvert(pig, piglin);
            double basePiglinSpeed = piglin.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Qualities.getId(piglin).equals(Optional.of(SUPERIOR)), "the zombified piglin should keep the pig's quality");
            helper.assertTrue(piglin.getMaxHealth() == 30.0F && piglin.getHealth() == 30.0F, "the zombified piglin should use its own attributes, had " + piglin.getHealth() + "/" + piglin.getMaxHealth());
            helper.assertTrue(piglin.getAttributeValue(Attributes.MOVEMENT_SPEED) == basePiglinSpeed, "the pig's speed bonus should not carry over");
        } finally {
            installSnapshot(loaded);
            if (piglin != null) piglin.discard();
            pig.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void playerQualitySurvivesDeathAndLeavingTheEnd(GameTestHelper helper) {
        Snapshot loaded = Snapshot.current();
        installSnapshot(withEntityAttributes(loaded, EntityType.PLAYER, SUPERIOR, List.of(
                new EntityQualityEffects.EntityAttributeEffect(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 10.0))));
        boolean configuredKeepOnDeath = Config.KEEP_PLAYER_QUALITY_ON_DEATH.get();
        Config.KEEP_PLAYER_QUALITY_ON_DEATH.set(true);
        try {
            ServerPlayer original = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "original-test"));
            Qualities.apply(original, SUPERIOR);
            original.setHealth(25.0F);
            ServerPlayer leftTheEnd = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "left-the-end-test"));
            leftTheEnd.restoreFrom(original, true);
            helper.assertTrue(leftTheEnd.getMaxHealth() == 30.0F && leftTheEnd.getHealth() == 25.0F, "leaving the End should keep 25/30 health, had " + leftTheEnd.getHealth() + "/" + leftTheEnd.getMaxHealth());
            original.setHealth(0.0F);
            ServerPlayer respawned = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "respawned-test"));
            respawned.restoreFrom(original, false);
            helper.assertTrue(Qualities.getId(respawned).equals(Optional.of(SUPERIOR)), "the quality should survive death");
            helper.assertTrue(respawned.getMaxHealth() == 30.0F && respawned.getHealth() == 30.0F, "a respawned player should have full 30/30 health, had " + respawned.getHealth() + "/" + respawned.getMaxHealth());
            Config.KEEP_PLAYER_QUALITY_ON_DEATH.set(false);
            ServerPlayer clearedOnDeath = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "cleared-on-death-test"));
            clearedOnDeath.restoreFrom(original, false);
            helper.assertTrue(Qualities.getId(clearedOnDeath).isEmpty(), "with keepPlayerQualityOnDeath off the quality should be cleared on death");
            helper.assertTrue(clearedOnDeath.getMaxHealth() == 20.0F && clearedOnDeath.getHealth() == 20.0F, "a player who lost quality should respawn at 20/20, had " + clearedOnDeath.getHealth() + "/" + clearedOnDeath.getMaxHealth());
            ServerPlayer leftTheEndWithoutDying = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "kept-without-death-test"));
            original.setHealth(25.0F);
            leftTheEndWithoutDying.restoreFrom(original, true);
            helper.assertTrue(Qualities.getId(leftTheEndWithoutDying).equals(Optional.of(SUPERIOR)), "leaving the End should keep quality even with keepPlayerQualityOnDeath off");
        } finally {
            Config.KEEP_PLAYER_QUALITY_ON_DEATH.set(configuredKeepOnDeath);
            installSnapshot(loaded);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void reloadRefreshesLoadedEntityAttributes(GameTestHelper helper) {
        Snapshot loaded = Snapshot.current();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        try {
            installSnapshot(withEntityAttributes(loaded, EntityType.COW, SUPERIOR, List.of(
                    new EntityQualityEffects.EntityAttributeEffect(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 10.0))));
            Qualities.apply(cow, SUPERIOR);
            helper.assertTrue(cow.getMaxHealth() == 20.0F && cow.getHealth() == 20.0F, "a superior cow should have 20/20 health");
            installSnapshot(withEntityAttributes(loaded, EntityType.COW, SUPERIOR, List.of(
                    new EntityQualityEffects.EntityAttributeEffect(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 30.0))));
            EntityQualityModifiers.refreshLoadedEntities(helper.getLevel().getServer());
            helper.assertTrue(cow.getMaxHealth() == 40.0F && cow.getHealth() == 40.0F, "a reload should give the cow 40/40 health, had " + cow.getHealth() + "/" + cow.getMaxHealth());
        } finally {
            installSnapshot(loaded);
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void qualityFoodRestoresScaledNutritionAndPostsEvent(GameTestHelper helper) {
        QualityEffects patch = foodPatch(Optional.of(1.4F), Optional.of(0.5F), Optional.empty(), Optional.empty());
        withFoodOverride(Items.COOKED_BEEF, MASTERWORK, patch, () -> {
            ServerPlayer player = mockServerPlayer(helper);
            player.getFoodData().setFoodLevel(0);
            player.getFoodData().setSaturation(0.0F);
            float halvedBeefSaturation = Items.COOKED_BEEF.components().get(DataComponents.FOOD).saturation() * 0.5F;
            List<QualityFoodEatenEvent> eaten = new ArrayList<>();
            Consumer<QualityFoodEatenEvent> recordEaten = event -> eaten.add(event);
            NeoForge.EVENT_BUS.addListener(recordEaten);
            try {
                eat(helper, player, withQuality(Items.COOKED_BEEF, MASTERWORK));
            } finally {
                NeoForge.EVENT_BUS.unregister(recordEaten);
            }
            helper.assertTrue(player.getFoodData().getFoodLevel() == 11, "masterwork beef should restore 11 hunger, restored " + player.getFoodData().getFoodLevel());
            helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - halvedBeefSaturation) < 0.001F, "masterwork beef should restore half saturation, restored " + player.getFoodData().getSaturationLevel());
            helper.assertTrue(eaten.size() == 1 && MASTERWORK.equals(eaten.getFirst().getQuality())
                    && eaten.getFirst().getOriginal().nutrition() == 8 && eaten.getFirst().getApplied().nutrition() == 11
                    && eaten.getFirst().getFood().is(Items.COOKED_BEEF), "expected one QualityFoodEatenEvent with original and applied values");
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void qualityFoodAppliesExtraEatEffects(GameTestHelper helper) {
        QualityEffects.EatEffect absorption = new QualityEffects.EatEffect(MobEffects.ABSORPTION, 1200, 1, 1.0F);
        QualityEffects patch = foodPatch(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(List.of(absorption)));
        withFoodOverride(Items.COOKED_BEEF, MASTERWORK, patch, () -> {
            ServerPlayer player = mockServerPlayer(helper);
            eat(helper, player, withQuality(Items.COOKED_BEEF, MASTERWORK));
            MobEffectInstance applied = player.getEffect(MobEffects.ABSORPTION);
            helper.assertTrue(applied != null && applied.getAmplifier() == 1 && applied.getDuration() == 1200, "eating should apply Absorption II for 1200 ticks, got " + applied);
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void qualityFoodScalesExistingEffectDurations(GameTestHelper helper) {
        QualityEffects patch = foodPatch(Optional.empty(), Optional.empty(), Optional.of(2.0F), Optional.empty());
        withFoodOverride(Items.GOLDEN_APPLE, MASTERWORK, patch, () -> {
            ServerPlayer player = mockServerPlayer(helper);
            eat(helper, player, withQuality(Items.GOLDEN_APPLE, MASTERWORK));
            MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
            helper.assertTrue(regeneration != null && regeneration.getDuration() == 200, "golden apple regeneration should last 200 ticks, got " + regeneration);
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void neutralQualityKeepsTheSameFoodProperties(GameTestHelper helper) {
        ItemStack beef = withQuality(Items.COOKED_BEEF, MASTERWORK);
        FoodProperties original = beef.get(DataComponents.FOOD);
        helper.assertTrue(Qualities.applyFoodEffects(beef, original) == original, "a tier with neutral food values should return the same instance");
        helper.assertTrue(Qualities.applyFoodEffects(new ItemStack(Items.COOKED_BEEF), original) == original, "plain food should return the same instance");
        FoodProperties carrot = Items.CARROT.components().get(DataComponents.FOOD);
        Snapshot.Resolved roundsAway = Snapshot.Resolved.of(MASTERWORK, foodPatch(Optional.of(1.1F), Optional.empty(), Optional.of(2.0F), Optional.empty()));
        helper.assertTrue(!roundsAway.changesFood(carrot) && roundsAway.applyTo(carrot) == carrot, "a nutrition change that rounds away and a duration change on an effect-free food should change nothing");
        helper.succeed();
    }

    private static QualityEffects foodPatch(Optional<Float> nutritionMultiplier, Optional<Float> saturationMultiplier, Optional<Float> eatEffectDurationMultiplier, Optional<List<QualityEffects.EatEffect>> eatEffects) {
        return new QualityEffects(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                nutritionMultiplier, saturationMultiplier, eatEffectDurationMultiplier, eatEffects);
    }

    private static void withFoodOverride(Item item, ResourceLocation tier, QualityEffects patch, Runnable body) {
        Snapshot loaded = Snapshot.current();
        Map<Item, Map<ResourceLocation, Snapshot.Resolved>> overrides = new HashMap<>(loaded.overrides());
        overrides.put(item, Map.of(tier, Snapshot.Resolved.of(tier, loaded.tiers().get(tier).effects().patchedWith(patch))));
        installSnapshot(new Snapshot(loaded.tiers(), loaded.idsByLevel(), loaded.base(), Map.copyOf(overrides), loaded.nameSets(), loaded.entityNameSets(), loaded.entityModifiers(), loaded.entityTints()));
        try {
            body.run();
        } finally {
            installSnapshot(loaded);
        }
    }

    private static Snapshot withEntityAttributes(Snapshot loaded, EntityType<?> type, ResourceLocation tier, List<EntityQualityEffects.EntityAttributeEffect> attributes) {
        Map<EntityType<?>, Map<ResourceLocation, Multimap<Holder<Attribute>, AttributeModifier>>> entityModifiers = new HashMap<>(loaded.entityModifiers());
        entityModifiers.put(type, Map.of(tier, Snapshot.entityModifiersOf(tier, new EntityQualityEffects(Optional.of(attributes), Optional.empty()))));
        return new Snapshot(loaded.tiers(), loaded.idsByLevel(), loaded.base(), loaded.overrides(), loaded.nameSets(), loaded.entityNameSets(), Map.copyOf(entityModifiers), loaded.entityTints());
    }

    private static void runCommand(GameTestHelper helper, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static LootItemFunction lootFunction(GameTestHelper helper, String json) {
        return LootItemFunctions.ROOT_CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
    }

    private static LootItemCondition lootCondition(GameTestHelper helper, String json) {
        return LootItemCondition.DIRECT_CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
    }

    private static LootContext lootContext(GameTestHelper helper, Entity entity) {
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.ADVANCEMENT_ENTITY);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    private static LootContext lootContextKilledBy(GameTestHelper helper, Entity entity, ServerPlayer killer) {
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().playerAttack(killer))
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    private static void installSnapshot(Snapshot snapshot) {
        try {
            Field current = Snapshot.class.getDeclaredField("current");
            current.setAccessible(true);
            current.set(null, snapshot);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void eat(GameTestHelper helper, ServerPlayer player, ItemStack food) {
        player.eat(helper.getLevel(), food, food.getFoodProperties(player));
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
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

    @GameTest(template = EMPTY)
    public static void tooltipStarsFollowTheStyleAndLevel(GameTestHelper helper) {
        Quality masterwork = Qualities.get(QualityAPI.id("masterwork")).orElseThrow();
        Component name = Component.literal("Masterwork");
        helper.assertTrue(QualityTooltipName.of(name, masterwork, Config.TooltipStyle.BOTH).getString().equals("Masterwork \uE000\uE000\uE000"), "BOTH should show the name then three stars for masterwork");
        helper.assertTrue(QualityTooltipName.of(name, masterwork, Config.TooltipStyle.TEXT).getString().equals("Masterwork"), "TEXT should show only the name");
        Component symbols = QualityTooltipName.of(name, masterwork, Config.TooltipStyle.SYMBOLS);
        Component withCrafter = symbols.copy().append(" - Crafted by Steve");
        helper.assertTrue(symbols.getString().equals("\uE000\uE000\uE000") && withCrafter.getStyle().getFont().equals(Style.DEFAULT_FONT),
                "SYMBOLS should show only stars, and text appended after them must stay in the normal font");
        helper.assertTrue(QualityTooltipName.of(name, tierAtLevel(-1), Config.TooltipStyle.SYMBOLS).getString().equals("\uE001"), "level -1 should be half a star");
        helper.assertTrue(QualityTooltipName.of(name, tierAtLevel(0), Config.TooltipStyle.SYMBOLS).getString().equals("\uE000"), "level 0 should be one star");
        helper.assertTrue(QualityTooltipName.of(name, tierAtLevel(-2), Config.TooltipStyle.SYMBOLS).getString().equals("Masterwork"), "a tier with no stars should fall back to its name");
        helper.assertTrue(QualityTooltipName.of(name, tierAtLevel(Integer.MAX_VALUE), Config.TooltipStyle.SYMBOLS).getString().equals("\uE000".repeat(5)), "very high levels should stop at five stars");
        helper.succeed();
    }

    private static Quality tierAtLevel(int level) {
        Quality masterwork = Qualities.get(QualityAPI.id("masterwork")).orElseThrow();
        return new Quality(level, masterwork.color(), masterwork.weight(), masterwork.items(), masterwork.effects(), masterwork.particleColor());
    }

    @GameTest(template = EMPTY)
    public static void bredAnimalsInheritTheirParentsQualityByConfig(GameTestHelper helper) {
        Config.Inheritance configuredInheritance = Config.BREED_INHERITANCE.get();
        try {
            Config.BREED_INHERITANCE.set(Config.Inheritance.AVERAGE);
            Cow superiorCalf = breedCalf(helper, FINE, EXQUISITE);
            helper.assertTrue(Qualities.getId(superiorCalf).equals(Optional.of(SUPERIOR)), "fine and exquisite parents should average to a superior baby");
            helper.assertTrue(superiorCalf.getMaxHealth() == EntityType.COW.create(helper.getLevel()).getMaxHealth() + 10.0F && superiorCalf.getHealth() == superiorCalf.getMaxHealth(),
                    "a superior calf should get the superior cow bonus and start at full health, had " + superiorCalf.getHealth() + "/" + superiorCalf.getMaxHealth());
            superiorCalf.discard();
            Set<ResourceLocation> halfwayCalves = new HashSet<>();
            for (int calf = 0; calf < 24; calf++) babyQuality(helper, FINE, SUPERIOR).ifPresent(halfwayCalves::add);
            helper.assertTrue(halfwayCalves.equals(Set.of(FINE, SUPERIOR)), "fine and superior parents should roll fine or superior, got " + halfwayCalves);
            helper.assertTrue(babyQuality(helper, null, null).isEmpty(), "parents without quality should have a plain baby");
            Config.BREED_INHERITANCE.set(Config.Inheritance.HIGHEST);
            helper.assertTrue(babyQuality(helper, MASTERWORK, null).equals(Optional.of(MASTERWORK)), "HIGHEST should take the masterwork parent");
            Config.BREED_INHERITANCE.set(Config.Inheritance.LOWEST);
            helper.assertTrue(babyQuality(helper, MASTERWORK, null).equals(Qualities.forLevel(0)), "LOWEST should count a plain parent as level 0");
            Config.BREED_INHERITANCE.set(Config.Inheritance.NONE);
            helper.assertTrue(babyQuality(helper, MASTERWORK, MASTERWORK).isEmpty(), "NONE should leave the baby plain");
            Object exquisiteBreeder = new Object() {
                @SubscribeEvent
                public void decide(QualityBreedEvent event) {
                    event.setQuality(EXQUISITE);
                }
            };
            NeoForge.EVENT_BUS.register(exquisiteBreeder);
            try {
                helper.assertTrue(babyQuality(helper, null, null).equals(Optional.of(EXQUISITE)), "QualityBreedEvent should fire under NONE with plain parents and its tier should win");
            } finally {
                NeoForge.EVENT_BUS.unregister(exquisiteBreeder);
            }
        } finally {
            Config.BREED_INHERITANCE.set(configuredInheritance);
        }
        helper.succeed();
    }

    private static Optional<ResourceLocation> babyQuality(GameTestHelper helper, @Nullable ResourceLocation tierA, @Nullable ResourceLocation tierB) {
        Cow calf = breedCalf(helper, tierA, tierB);
        Optional<ResourceLocation> quality = Qualities.getId(calf);
        calf.discard();
        return quality;
    }

    private static Cow breedCalf(GameTestHelper helper, @Nullable ResourceLocation tierA, @Nullable ResourceLocation tierB) {
        Cow parentA = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        Cow parentB = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        if (tierA != null) Qualities.apply(parentA, tierA);
        if (tierB != null) Qualities.apply(parentB, tierB);
        parentA.spawnChildFromBreeding(helper.getLevel(), parentB);
        List<Cow> babies = helper.getLevel().getEntitiesOfClass(Cow.class, parentA.getBoundingBox().inflate(2), Cow::isBaby);
        helper.assertTrue(babies.size() == 1, "breeding should spawn exactly one calf, found " + babies.size());
        parentA.discard();
        parentB.discard();
        return babies.get(0);
    }
}
