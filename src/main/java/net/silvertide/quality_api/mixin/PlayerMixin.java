package net.silvertide.quality_api.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityFoodEatenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @ModifyVariable(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), argsOnly = true)
    private FoodProperties quality_api$applyQualityToFood(FoodProperties original, @Local(argsOnly = true) ItemStack food) {
        FoodProperties applied = Qualities.applyFoodEffects(food, original);
        if (applied != original && (Object) this instanceof ServerPlayer player) {
            NeoForge.EVENT_BUS.post(new QualityFoodEatenEvent(player, food.copy(), food.get(QualityAPI.QUALITY.get()), original, applied));
        }
        return applied;
    }
}
