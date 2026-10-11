package net.silvertide.quality_api.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityBreakDowngradeEvent;
import net.silvertide.quality_api.api.QualityItemBrokenEvent;
import net.silvertide.quality_api.quality.Snapshot;
import net.silvertide.silverlib.feature.breakage.BrokenItems;
import net.silvertide.silverlib.feature.breakage.ItemBreakingEvent;
import net.silvertide.silverlib.feature.breakage.ItemKeptBrokenEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class QualityBreakEvents {

    private static final ThreadLocal<Set<ItemStack>> STACKS_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED = ThreadLocal.withInitial(QualityBreakEvents::newIdentitySet);
    private static final ThreadLocal<Set<ItemStack>> STACKS_CONVERTED_FROM_BEFORE_SILVERLIB = ThreadLocal.withInitial(QualityBreakEvents::newIdentitySet);

    private QualityBreakEvents() {}

    private static Set<ItemStack> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public static void rememberIfBrokenBeforeSilverLib(ItemStack stack) {
        STACKS_CONVERTED_FROM_BEFORE_SILVERLIB.get().remove(stack);
        if (isBrokenBeforeSilverLib(stack)) STACKS_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED.get().add(stack);
        else STACKS_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED.get().remove(stack);
    }

    private static boolean isBrokenBeforeSilverLib(ItemStack stack) {
        return Qualities.getId(stack).isPresent()
                && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage()
                && !BrokenItems.isBroken(stack);
    }

    private static void convertIfBrokenBeforeSilverLib(ItemStack stack, ServerLevel level) {
        if (Config.BROKEN_INSTEAD_OF_DESTROYED.get() && isBrokenBeforeSilverLib(stack)) {
            stack.hurtAndBreak(0, level, (LivingEntity) null, item -> {});
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            convertIfBrokenBeforeSilverLib(inventory.getItem(slot), player.serverLevel());
        }
    }

    @SubscribeEvent
    static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) convertIfBrokenBeforeSilverLib(event.getTo(), level);
    }

    @SubscribeEvent
    static void onItemBreaking(ItemBreakingEvent event) {
        ItemStack stack = event.getStack();
        boolean wasBrokenBeforeSilverLib = STACKS_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED.get().remove(stack);
        if (!Config.BROKEN_INSTEAD_OF_DESTROYED.get()) return;
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        if (tier == null) return;
        Snapshot.Resolved resolved = Snapshot.current().resolved(stack.getItem(), tier);
        if (resolved == null) return;
        if (wasBrokenBeforeSilverLib) {
            STACKS_CONVERTED_FROM_BEFORE_SILVERLIB.get().add(stack);
            event.keepBroken();
            return;
        }

        if (event.getLevel().getRandom().nextFloat() < resolved.breakDestroyChance()) return;
        float downgradeChance = resolved.breakDowngradeChance().orElseGet(() -> Config.BREAK_DOWNGRADE_CHANCE.get().floatValue());
        boolean mayDowngrade = Config.HIGHEST_TIER_CAN_DOWNGRADE.get() || !Qualities.isHighestLevel(stack, tier);
        if (mayDowngrade && event.getLevel().getRandom().nextFloat() < downgradeChance) {
            Qualities.shift(stack, tier, -1)
                    .filter(lower -> !NeoForge.EVENT_BUS.post(new QualityBreakDowngradeEvent(stack, event.getEntity(), tier, lower)).isCanceled())
                    .ifPresent(lower -> Qualities.apply(stack, lower));
        }
        event.keepBroken();
    }

    @SubscribeEvent
    static void onItemKeptBroken(ItemKeptBrokenEvent event) {
        ItemStack stack = event.getStack();
        if (STACKS_CONVERTED_FROM_BEFORE_SILVERLIB.get().remove(stack)) return;
        Qualities.getId(stack).ifPresent(tier -> NeoForge.EVENT_BUS.post(new QualityItemBrokenEvent(stack, event.getEntity(), tier)));
    }
}
