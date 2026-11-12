package net.silvertide.quality_api.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

@Mixin(AbstractStoveBlockEntity.class)
public class AbstractStoveBlockEntityMixin {
    @WrapOperation(
            method = "cookAndOutputItems",
            at = @At(value = "INVOKE", target = "Lvectorwing/farmersdelight/common/utility/ItemUtils;spawnItemEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;DDDDDD)V"))
    private void quality_api$decideStoveQuality(Level level, ItemStack result, double x, double y, double z, double xMotion, double yMotion, double zMotion,
                                                Operation<Void> original, @Local SingleRecipeInput input) {
        boolean uncooked = ItemStack.isSameItemSameComponents(result, input.item());
        original.call(level, uncooked ? result : StationQuality.decide(level, ((AbstractStoveBlockEntity) (Object) this).getBlockPos(), input, result), x, y, z, xMotion, yMotion, zMotion);
    }
}
