package net.silvertide.quality_api.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
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

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class QualityBreakEvents {

    private static final ThreadLocal<ItemStack> STACK_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED = new ThreadLocal<>();

    private QualityBreakEvents() {}

    public static void rememberIfBrokenBeforeSilverLib(ItemStack stack) {
        STACK_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED.set(isBrokenBeforeSilverLib(stack) ? stack : null);
    }

    private static boolean isBrokenBeforeSilverLib(ItemStack stack) {
        return stack.has(QualityAPI.QUALITY.get())
                && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage()
                && !BrokenItems.isBroken(stack);
    }

    private static boolean wasBrokenBeforeSilverLib(ItemStack stack) {
        boolean wasBroken = STACK_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED.get() == stack;
        STACK_BROKEN_BEFORE_SILVERLIB_BEING_DAMAGED.remove();
        return wasBroken;
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Config.BROKEN_INSTEAD_OF_DESTROYED.get() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isBrokenBeforeSilverLib(stack)) stack.hurtAndBreak(0, player.serverLevel(), (LivingEntity) null, item -> {});
        }
    }

    @SubscribeEvent
    static void onItemBreaking(ItemBreakingEvent event) {
        ItemStack stack = event.getStack();
        boolean wasBrokenBeforeSilverLib = wasBrokenBeforeSilverLib(stack);
        if (!Config.BROKEN_INSTEAD_OF_DESTROYED.get()) return;
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        if (tier == null) return;
        Snapshot.Resolved resolved = Snapshot.current().resolved(stack.getItem(), tier);
        if (resolved == null) return;
        if (wasBrokenBeforeSilverLib) {
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
        Qualities.getId(stack).ifPresent(tier -> NeoForge.EVENT_BUS.post(new QualityItemBrokenEvent(stack, event.getEntity(), tier)));
    }
}
