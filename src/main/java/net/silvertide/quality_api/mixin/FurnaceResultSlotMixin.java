package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin extends Slot {
    @Shadow
    @Final
    private Player player;

    private FurnaceResultSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @ModifyReturnValue(method = "remove", at = @At("RETURN"))
    private ItemStack quality_api$decideTakenQuality(ItemStack taken) {
        return StationQuality.taken(player, taken);
    }

    @Override
    public boolean mayPickup(Player clicker) {
        return StationQuality.notMergingOntoCarried(clicker) && super.mayPickup(clicker);
    }

    @Override
    protected void onSwapCraft(int count) {
        StationQuality.taken(player, getItem());
        super.onSwapCraft(count);
    }
}
