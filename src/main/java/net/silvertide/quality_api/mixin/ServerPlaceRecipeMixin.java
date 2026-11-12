package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlaceRecipe.class)
public class ServerPlaceRecipeMixin {
    @Shadow
    protected RecipeBookMenu<?, ?> menu;

    @WrapOperation(
            method = "clearGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V"))
    private void quality_api$decideRecipeBookTakenQuality(Inventory inventory, ItemStack moved, boolean sendPacket, Operation<Void> original,
                                                         @Local int slotIndex) {
        boolean takenFromFurnaceResult = menu.getSlot(slotIndex) instanceof FurnaceResultSlot;
        original.call(inventory, takenFromFurnaceResult ? StationQuality.taken(inventory.player, moved) : moved, sendPacket);
    }
}
