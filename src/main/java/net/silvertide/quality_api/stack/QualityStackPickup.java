package net.silvertide.quality_api.stack;

import com.google.common.collect.Iterables;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;

import java.util.UUID;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class QualityStackPickup {
    private static final int PICKUP_POP_TICKS = 5;

    private QualityStackPickup() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity itemEntity = event.getItemEntity();
        ItemStack picked = itemEntity.getItem();
        if (!Config.QUALITY_STACKS.get() || !QualityStackContents.isPoolable(picked) || !mayPickUp(event, player, itemEntity)) return;
        ItemStack beforePickup = picked.copy();
        int moved = insertIntoQualityStacks(player.getInventory(), picked);
        if (moved == 0) return;
        player.getInventory().setChanged();
        EventHooks.fireItemPickupPost(itemEntity, player, beforePickup);
        player.take(itemEntity, moved);
        boolean usedUp = picked.isEmpty();
        if (usedUp) {
            itemEntity.discard();
            event.setCanPickup(TriState.FALSE);
        }
        player.awardStat(Stats.ITEM_PICKED_UP.get(beforePickup.getItem()), moved);
        triggerPickupCriteriaWithPickedCount(player, itemEntity, moved);
    }

    private static void triggerPickupCriteriaWithPickedCount(Player player, ItemEntity itemEntity, int pickedCount) {
        ItemStack left = itemEntity.getItem();
        int leftCount = left.getCount();
        left.setCount(pickedCount);
        player.onItemPickup(itemEntity);
        left.setCount(leftCount);
    }

    private static boolean mayPickUp(ItemEntityPickupEvent.Pre event, Player player, ItemEntity itemEntity) {
        TriState decision = event.canPickup();
        if (decision.isFalse()) return false;
        if (decision.isTrue()) return true;
        UUID owner = itemEntity.getTarget();
        return !itemEntity.hasPickUpDelay() && (owner == null || owner.equals(player.getUUID()));
    }

    private static int insertIntoQualityStacks(Inventory inventory, ItemStack picked) {
        int moved = 0;
        for (ItemStack held : Iterables.concat(inventory.items, inventory.offhand)) {
            if (picked.isEmpty()) break;
            int movedIntoHeld = QualityStacks.insert(held, picked);
            if (movedIntoHeld > 0) held.setPopTime(PICKUP_POP_TICKS);
            moved += movedIntoHeld;
        }
        return moved;
    }
}
