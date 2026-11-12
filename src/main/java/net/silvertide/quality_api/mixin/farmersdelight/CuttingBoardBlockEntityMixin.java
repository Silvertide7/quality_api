package net.silvertide.quality_api.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.silvertide.quality_api.craft.StationQuality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.List;

@Mixin(CuttingBoardBlockEntity.class)
public class CuttingBoardBlockEntityMixin {
    @WrapOperation(
            method = "lambda$processStoredItemUsingTool$2",
            at = @At(value = "INVOKE", target = "Lvectorwing/farmersdelight/common/crafting/CuttingBoardRecipe;rollResults(Lnet/minecraft/util/RandomSource;ILnet/neoforged/neoforge/items/wrapper/RecipeWrapper;)Ljava/util/List;"))
    private List<ItemStack> quality_api$decideCutQuality(CuttingBoardRecipe recipe, RandomSource random, int fortuneLevel, RecipeWrapper input, Operation<List<ItemStack>> original,
                                                         @Local(argsOnly = true) Player player) {
        CuttingBoardBlockEntity cuttingBoard = (CuttingBoardBlockEntity) (Object) this;
        List<ItemStack> cutIngredients = List.of(cuttingBoard.getStoredItem());
        List<ItemStack> results = original.call(recipe, random, fortuneLevel, input);
        for (ItemStack result : results) StationQuality.taken(player, StationQuality.decide(cuttingBoard.getLevel(), cuttingBoard.getBlockPos(), cutIngredients, result));
        return results;
    }
}
