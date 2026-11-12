package net.silvertide.quality_api.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.ArrayList;
import java.util.List;

@Mixin(CookingPotBlockEntity.class)
public class CookingPotBlockEntityMixin {
    @Shadow
    @Final
    private ItemStackHandler inventory;

    @WrapOperation(
            method = {"canCook", "processCooking"},
            require = 2,
            at = @At(value = "INVOKE", target = "Lvectorwing/farmersdelight/common/crafting/CookingPotRecipe;assemble(Lnet/neoforged/neoforge/items/wrapper/RecipeWrapper;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack quality_api$decideMealQuality(CookingPotRecipe recipe, RecipeWrapper input, HolderLookup.Provider registries, Operation<ItemStack> original) {
        return StationQuality.decide(quality_api$cookingPot().getLevel(), quality_api$cookingPot().getBlockPos(), quality_api$ingredientSlots(input), original.call(recipe, input, registries));
    }

    @Inject(method = "moveMealToOutput", at = @At("HEAD"), cancellable = true)
    private void quality_api$poolMealIntoOutputOfAnotherQuality(CallbackInfo callback) {
        ItemStack meal = inventory.getStackInSlot(CookingPotBlockEntity.MEAL_DISPLAY_SLOT);
        if (quality_api$poolIntoOutput(meal, meal.getCount())) callback.cancel();
    }

    @Inject(method = "useStoredContainersOnMeal", at = @At("HEAD"), cancellable = true)
    private void quality_api$poolServedMealIntoOutputOfAnotherQuality(CallbackInfo callback) {
        ItemStack meal = inventory.getStackInSlot(CookingPotBlockEntity.MEAL_DISPLAY_SLOT);
        ItemStack containers = inventory.getStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT);
        int mealsBeforeServing = meal.getCount();
        int servable = quality_api$cookingPot().isContainerValid(containers) ? Math.min(mealsBeforeServing, containers.getCount()) : 0;
        if (!quality_api$poolIntoOutput(meal, servable)) return;
        containers.shrink(mealsBeforeServing - meal.getCount());
        callback.cancel();
    }

    @Unique
    private boolean quality_api$poolIntoOutput(ItemStack meal, int maxCount) {
        ItemStack output = inventory.getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT);
        if (meal.isEmpty() || !StationQuality.poolsWithOutput(output, meal)) return false;
        inventory.setStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT, StationQuality.pooledOutput(output, meal, maxCount));
        return true;
    }

    @Unique
    private CookingPotBlockEntity quality_api$cookingPot() {
        return (CookingPotBlockEntity) (Object) this;
    }

    @Unique
    private static List<ItemStack> quality_api$ingredientSlots(RecipeWrapper input) {
        List<ItemStack> ingredients = new ArrayList<>(CookingPotBlockEntity.MEAL_DISPLAY_SLOT);
        for (int slot = 0; slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; slot++) ingredients.add(input.getItem(slot));
        return ingredients;
    }
}
