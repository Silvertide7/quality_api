package net.silvertide.quality_api.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

public class QualityFoodEatenEvent extends Event {
    private final ServerPlayer player;
    private final ItemStack food;
    private final ResourceLocation quality;
    private final FoodProperties original;
    private final FoodProperties applied;

    public QualityFoodEatenEvent(ServerPlayer player, ItemStack food, ResourceLocation quality, FoodProperties original, FoodProperties applied) {
        this.player = player;
        this.food = food;
        this.quality = quality;
        this.original = original;
        this.applied = applied;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ItemStack getFood() {
        return food;
    }

    public ResourceLocation getQuality() {
        return quality;
    }

    public FoodProperties getOriginal() {
        return original;
    }

    public FoodProperties getApplied() {
        return applied;
    }
}
