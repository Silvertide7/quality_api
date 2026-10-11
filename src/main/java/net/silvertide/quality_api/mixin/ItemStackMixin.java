package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.events.QualityBreakEvents;
import net.silvertide.quality_api.quality.Snapshot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void quality_api$rememberIfBrokenBeforeSilverLib(int damage, ServerLevel level, @Nullable LivingEntity entity, Consumer<Item> onBreak, CallbackInfo ci) {
        QualityBreakEvents.rememberIfBrokenBeforeSilverLib((ItemStack) (Object) this);
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void quality_api$forgetStackAfterBeingDamaged(int damage, ServerLevel level, @Nullable LivingEntity entity, Consumer<Item> onBreak, CallbackInfo ci) {
        QualityBreakEvents.forgetStackAfterBeingDamaged((ItemStack) (Object) this);
    }

    @ModifyReturnValue(method = "getMaxDamage", at = @At("RETURN"))
    private int quality_api$scaleMaxDamage(int original) {
        if (original <= 0 || !QualityAPI.QUALITY.isBound()) return original;
        ItemStack self = (ItemStack) (Object) this;
        ResourceLocation tier = self.get(QualityAPI.QUALITY.get());
        if (tier == null) return original;
        Snapshot.Resolved resolved = Snapshot.current().resolved(self.getItem(), tier);
        return resolved == null ? original : resolved.scaleMaxDamage(original);
    }
}
