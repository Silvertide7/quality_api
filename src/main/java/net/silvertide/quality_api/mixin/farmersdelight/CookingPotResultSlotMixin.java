package net.silvertide.quality_api.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot;

@Mixin(CookingPotResultSlot.class)
public abstract class CookingPotResultSlotMixin extends SlotItemHandler {
    @Shadow
    @Final
    private Player player;

    private CookingPotResultSlotMixin(IItemHandler inventory, int slot, int x, int y) {
        super(inventory, slot, x, y);
    }

    @ModifyReturnValue(method = "remove", at = @At("RETURN"))
    private ItemStack quality_api$decideTakenMealQuality(ItemStack taken) {
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
