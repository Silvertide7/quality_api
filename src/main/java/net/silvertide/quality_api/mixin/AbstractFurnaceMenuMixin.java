package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractFurnaceMenu.class)
public class AbstractFurnaceMenuMixin {
    @WrapOperation(
            method = "quickMoveStack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractFurnaceMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", ordinal = 0))
    private boolean quality_api$decideShiftTakenQuality(AbstractFurnaceMenu menu, ItemStack result, int start, int end, boolean reverse, Operation<Boolean> original,
                                                        @Local(argsOnly = true) Player player) {
        return StationQuality.hasAnEmptySlot(menu, start, end) && original.call(menu, StationQuality.taken(player, result), start, end, reverse);
    }
}
