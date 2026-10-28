package net.silvertide.quality_api.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;

public class QualityLootEvent extends Event {
    private final ItemStack stack;
    private final LootContext context;
    @Nullable
    private final ServerPlayer player;
    @Nullable
    private final Entity entity;
    private final OptionalDouble sourceLevel;
    @Nullable
    private ResourceLocation quality;

    public QualityLootEvent(ItemStack stack, LootContext context, @Nullable ServerPlayer player, @Nullable Entity entity, OptionalDouble sourceLevel, @Nullable ResourceLocation quality) {
        this.stack = stack;
        this.context = context;
        this.player = player;
        this.entity = entity;
        this.sourceLevel = sourceLevel;
        this.quality = quality;
    }

    public ItemStack getStack() {
        return stack;
    }

    public LootContext getContext() {
        return context;
    }

    @Nullable
    public ServerPlayer getPlayer() {
        return player;
    }

    @Nullable
    public Entity getEntity() {
        return entity;
    }

    public OptionalDouble getSourceLevel() {
        return sourceLevel;
    }

    @Nullable
    public ResourceLocation getQuality() {
        return quality;
    }

    public void setQuality(@Nullable ResourceLocation quality) {
        this.quality = quality;
    }
}
