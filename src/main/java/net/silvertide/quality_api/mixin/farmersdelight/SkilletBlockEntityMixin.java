package net.silvertide.quality_api.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;

@Mixin(SkilletBlockEntity.class)
public class SkilletBlockEntityMixin {
    @WrapOperation(
            method = "cookAndOutputItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CampfireCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack quality_api$decideSkilletQuality(CampfireCookingRecipe recipe, SingleRecipeInput input, HolderLookup.Provider registries, Operation<ItemStack> original,
                                                     @Local(argsOnly = true) Level level) {
        return StationQuality.decide(level, ((SkilletBlockEntity) (Object) this).getBlockPos(), input, original.call(recipe, input, registries));
    }
}
