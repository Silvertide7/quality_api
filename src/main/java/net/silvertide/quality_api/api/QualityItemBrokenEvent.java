package net.silvertide.quality_api.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

public class QualityItemBrokenEvent extends Event {
    private final ItemStack stack;
    @Nullable
    private final LivingEntity entity;
    private final ResourceLocation quality;

    public QualityItemBrokenEvent(ItemStack stack, @Nullable LivingEntity entity, ResourceLocation quality) {
        this.stack = stack;
        this.entity = entity;
        this.quality = quality;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Nullable
    public LivingEntity getEntity() {
        return entity;
    }

    public ResourceLocation getQuality() {
        return quality;
    }
}
