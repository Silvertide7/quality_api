package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntityMixin {
    @Shadow
    @Final
    protected static int SLOT_RESULT;

    @WrapOperation(
            method = {"canBurn", "burn"},
            require = 2,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack quality_api$decideSmeltedQuality(AbstractCookingRecipe recipe, SingleRecipeInput input, HolderLookup.Provider registries, Operation<ItemStack> original,
                                                             @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
        return StationQuality.decide(furnace.getLevel(), furnace.getBlockPos(), input, original.call(recipe, input, registries));
    }

    @WrapOperation(
            method = "canBurn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean quality_api$outputHasRoomForResultOfAnotherQuality(ItemStack output, ItemStack result, Operation<Boolean> original) {
        return original.call(output, result)
                || StationQuality.poolsWithOutput(output, result) && StationQuality.roomInPooledOutput(output) >= result.getCount();
    }

    @WrapOperation(
            method = "burn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean quality_api$poolResultOfAnotherQualityIntoOutput(ItemStack output, ItemStack result, Operation<Boolean> original,
                                                                            @Local(argsOnly = true) NonNullList<ItemStack> inventory) {
        if (original.call(output, result)) return true;
        if (StationQuality.poolsWithOutput(output, result)) inventory.set(SLOT_RESULT, StationQuality.pooledOutput(output, result, result.getCount()));
        return false;
    }
}
