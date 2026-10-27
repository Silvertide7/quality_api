package net.silvertide.quality_api.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.silvertide.quality_api.quality.EntityQualityModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void quality_api$applyQualityModifiersBeforeHealthLoads(CompoundTag tag, CallbackInfo ci) {
        EntityQualityModifiers.refresh((LivingEntity) (Object) this);
    }
}
