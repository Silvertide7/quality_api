package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.craft.CraftInheritance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {
    @WrapOperation(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private static void quality_api$applyQuality(ResultContainer container, int slot, ItemStack stack, Operation<Void> original,
                                                 @Local(argsOnly = true) Player player, @Local(argsOnly = true) CraftingContainer craftSlots) {
        if (!stack.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            CraftInheritance.applyToResult(serverPlayer, craftSlots, container, stack);
        }
        original.call(container, slot, stack);
    }
}
