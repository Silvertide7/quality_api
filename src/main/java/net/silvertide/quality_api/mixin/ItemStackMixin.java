package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.events.BrokenItems;
import net.silvertide.quality_api.quality.Snapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    private static final String HURT_AND_BREAK = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V";

    @ModifyReturnValue(method = "getMaxDamage", at = @At("RETURN"))
    private int quality_api$scaleMaxDamage(int original) {
        if (original <= 0 || !QualityAPI.QUALITY.isBound()) return original;
        ItemStack self = (ItemStack) (Object) this;
        ResourceLocation tier = self.get(QualityAPI.QUALITY.get());
        if (tier == null) return original;
        Snapshot.Resolved resolved = Snapshot.current().resolved(self.getItem(), tier);
        return resolved == null ? original : resolved.scaleMaxDamage(original);
    }

    @Inject(method = HURT_AND_BREAK, at = @At("HEAD"), cancellable = true)
    private void quality_api$brokenItemsTakeNoDamage(int damage, ServerLevel level, LivingEntity entity, Consumer<Item> onBreak, CallbackInfo ci) {
        if (BrokenItems.isProtectedBrokenItem((ItemStack) (Object) this)) ci.cancel();
    }

    @Inject(method = HURT_AND_BREAK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), cancellable = true)
    private void quality_api$breakInsteadOfDestroy(int damage, ServerLevel level, LivingEntity entity, Consumer<Item> onBreak, CallbackInfo ci) {
        if (BrokenItems.breakInsteadOfDestroy((ItemStack) (Object) this, level, entity)) ci.cancel();
    }
}
