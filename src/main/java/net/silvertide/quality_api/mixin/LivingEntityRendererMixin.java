package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.quality.Snapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4,
            require = 0)
    private int quality_api$tintByQuality(int color, @Local(argsOnly = true) LivingEntity entity) {
        int tint = Snapshot.current().entityTint(entity.getType(), entity.getExistingDataOrNull(QualityAPI.ENTITY_QUALITY));
        return tint == Snapshot.UNTINTED ? color : FastColor.ARGB32.multiply(color, tint);
    }
}
