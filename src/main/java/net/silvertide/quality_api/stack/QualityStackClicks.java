package net.silvertide.quality_api.stack;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.silvertide.quality_api.QualityAPI;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class QualityStackClicks {
    private QualityStackClicks() {}

    @SubscribeEvent
    static void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        if (event.getClickAction() != ClickAction.SECONDARY) return;
        ItemStack carried = event.getCarriedItem();
        Slot slot = event.getSlot();
        Player player = event.getPlayer();
        boolean handled;
        if (QualityStacks.isQualityStack(carried)) handled = carriedQualityStackClicksSlot(carried, slot, player, event.getCarriedSlotAccess());
        else if (QualityStacks.isQualityStack(slot.getItem())) handled = slotQualityStackClicked(slot.getItem(), carried, slot, player, event.getCarriedSlotAccess());
        else handled = carriedPoolsWithSlot(carried, slot, player, event.getCarriedSlotAccess());
        if (handled) event.setCanceled(true);
    }

    private static boolean carriedQualityStackClicksSlot(ItemStack qualityStack, Slot slot, Player player, SlotAccess carriedAccess) {
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) {
            ItemStack best = QualityStacks.peekBest(qualityStack);
            int fits = slot.mayPlace(best) ? Math.min(best.getCount(), slot.getMaxStackSize(best)) : 0;
            if (fits <= 0) return true;
            slot.setByPlayer(QualityStacks.extractBest(qualityStack, fits));
            QualityStacks.collapseToSingleStack(qualityStack).ifPresent(carriedAccess::set);
            playSound(player, SoundEvents.BUNDLE_REMOVE_ONE);
            return true;
        }
        QualityStackContents contents = QualityStacks.contents(qualityStack);
        if (contents == null || !contents.accepts(inSlot)) return false;
        ItemStack taken = slot.safeTake(inSlot.getCount(), contents.capacity() - contents.total(), player);
        if (taken.isEmpty()) return true;
        QualityStacks.insert(qualityStack, taken);
        if (!taken.isEmpty()) player.getInventory().placeItemBackInInventory(taken);
        playSound(player, SoundEvents.BUNDLE_INSERT);
        return true;
    }

    private static boolean slotQualityStackClicked(ItemStack qualityStack, ItemStack carried, Slot slot, Player player, SlotAccess carriedAccess) {
        QualityStackContents contents = QualityStacks.contents(qualityStack);
        if (contents == null || !slot.allowModification(player)) return false;
        if (carried.isEmpty()) {
            carriedAccess.set(QualityStacks.extractBest(qualityStack, Integer.MAX_VALUE));
            writeBack(slot, qualityStack);
            playSound(player, SoundEvents.BUNDLE_REMOVE_ONE);
            return true;
        }
        if (!contents.accepts(carried) || slotHoldsLessThanAFullStackOf(slot, carried)) return false;
        if (QualityStacks.insert(qualityStack, carried) > 0) {
            writeBack(slot, qualityStack);
            playSound(player, SoundEvents.BUNDLE_INSERT);
        }
        return true;
    }

    private static void writeBack(Slot slot, ItemStack qualityStack) {
        ItemStack remaining = qualityStack.isEmpty() ? ItemStack.EMPTY : qualityStack;
        slot.set(QualityStacks.collapseToSingleStack(qualityStack).orElse(remaining));
    }

    private static boolean slotHoldsLessThanAFullStackOf(Slot slot, ItemStack stack) {
        return slot.getMaxStackSize(stack) < stack.getMaxStackSize();
    }

    private static boolean carriedPoolsWithSlot(ItemStack carried, Slot slot, Player player, SlotAccess carriedAccess) {
        ItemStack inSlot = slot.getItem();
        if (ItemStack.isSameItemSameComponents(carried, inSlot) || !QualityStacks.canPool(inSlot, carried) || !slot.allowModification(player) || slotHoldsLessThanAFullStackOf(slot, inSlot)) return false;
        ItemStack slotRemainder = inSlot.copy();
        ItemStack carriedRemainder = carried.copy();
        ItemStack qualityStack = QualityStacks.poolIntoNewQualityStack(slotRemainder, carriedRemainder);
        if (!slotRemainder.isEmpty() || !carriedRemainder.isEmpty() || !slot.mayPlace(qualityStack)) return false;
        slot.setByPlayer(qualityStack);
        carriedAccess.set(ItemStack.EMPTY);
        playSound(player, SoundEvents.BUNDLE_INSERT);
        return true;
    }

    private static void playSound(Player player, SoundEvent sound) {
        player.playSound(sound, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }
}
