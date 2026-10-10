package net.silvertide.quality_api.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

public class QualityBreakDowngradeEvent extends Event implements ICancellableEvent {
    private final ItemStack stack;
    @Nullable
    private final LivingEntity entity;
    private final ResourceLocation currentQuality;
    private final ResourceLocation lowerQuality;

    public QualityBreakDowngradeEvent(ItemStack stack, @Nullable LivingEntity entity, ResourceLocation currentQuality, ResourceLocation lowerQuality) {
        this.stack = stack;
        this.entity = entity;
        this.currentQuality = currentQuality;
        this.lowerQuality = lowerQuality;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Nullable
    public LivingEntity getEntity() {
        return entity;
    }

    public ResourceLocation getCurrentQuality() {
        return currentQuality;
    }

    public ResourceLocation getLowerQuality() {
        return lowerQuality;
    }
}
