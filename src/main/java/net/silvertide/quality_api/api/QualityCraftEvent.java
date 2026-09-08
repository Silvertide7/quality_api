package net.silvertide.quality_api.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;

public class QualityCraftEvent extends Event {
    private final ServerPlayer player;
    @Nullable
    private final RecipeHolder<?> recipe;
    private final CraftingInput input;
    private final ItemStack result;
    private final OptionalDouble ingredientMeanLevel;
    @Nullable
    private ResourceLocation quality;

    public QualityCraftEvent(ServerPlayer player, @Nullable RecipeHolder<?> recipe, CraftingInput input, ItemStack result, OptionalDouble ingredientMeanLevel, @Nullable ResourceLocation quality) {
        this.player = player;
        this.recipe = recipe;
        this.input = input;
        this.result = result;
        this.ingredientMeanLevel = ingredientMeanLevel;
        this.quality = quality;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    @Nullable
    public RecipeHolder<?> getRecipe() {
        return recipe;
    }

    public CraftingInput getInput() {
        return input;
    }

    public ItemStack getResult() {
        return result;
    }

    public OptionalDouble getIngredientMeanLevel() {
        return ingredientMeanLevel;
    }

    @Nullable
    public ResourceLocation getQuality() {
        return quality;
    }

    public void setQuality(@Nullable ResourceLocation quality) {
        this.quality = quality;
    }
}
