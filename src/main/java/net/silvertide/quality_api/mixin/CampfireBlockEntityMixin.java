package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CampfireBlockEntity.class)
public class CampfireBlockEntityMixin {
    @WrapOperation(
            method = "cookTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
    private static void quality_api$decideCookedQuality(Level level, double x, double y, double z, ItemStack result, Operation<Void> original,
                                                        @Local SingleRecipeInput input, @Local(argsOnly = true) BlockPos pos) {
        original.call(level, x, y, z, result == input.item() ? result : StationQuality.decide(level, pos, input, result));
    }
}
