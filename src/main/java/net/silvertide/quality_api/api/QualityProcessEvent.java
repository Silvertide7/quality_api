package net.silvertide.quality_api.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalDouble;

public class QualityProcessEvent extends Event {
    private final Level level;
    @Nullable
    private final BlockPos stationPos;
    private final List<ItemStack> ingredients;
    private final ItemStack result;
    private final OptionalDouble ingredientMeanLevel;
    @Nullable
    private ResourceLocation quality;

    public QualityProcessEvent(Level level, @Nullable BlockPos stationPos, List<ItemStack> ingredients, ItemStack result, OptionalDouble ingredientMeanLevel, @Nullable ResourceLocation quality) {
        this.level = level;
        this.stationPos = stationPos;
        this.ingredients = ingredients;
        this.result = result;
        this.ingredientMeanLevel = ingredientMeanLevel;
        this.quality = quality;
    }

    public Level getLevel() {
        return level;
    }

    @Nullable
    public BlockPos getStationPos() {
        return stationPos;
    }

    public List<ItemStack> getIngredients() {
        return ingredients;
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
