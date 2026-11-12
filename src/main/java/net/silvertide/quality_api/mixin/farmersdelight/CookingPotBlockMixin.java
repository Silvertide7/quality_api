package net.silvertide.quality_api.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.CookingPotBlock;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

@Mixin(CookingPotBlock.class)
public class CookingPotBlockMixin {
    @WrapOperation(
            method = "useItemOn",
            at = @At(value = "INVOKE", target = "Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;useHeldItemOnMeal(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack quality_api$decideServedMealQuality(CookingPotBlockEntity cookingPot, ItemStack container, Operation<ItemStack> original,
                                                          @Local(argsOnly = true) Player player) {
        return StationQuality.taken(player, original.call(cookingPot, container));
    }
}
