package net.silvertide.quality_api.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

public class QualityResultTakenEvent extends Event {
    private final ServerPlayer player;
    private final ItemStack result;
    @Nullable
    private ResourceLocation quality;

    public QualityResultTakenEvent(ServerPlayer player, ItemStack result, @Nullable ResourceLocation quality) {
        this.player = player;
        this.result = result;
        this.quality = quality;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ItemStack getResult() {
        return result;
    }

    @Nullable
    public ResourceLocation getQuality() {
        return quality;
    }

    public void setQuality(@Nullable ResourceLocation quality) {
        this.quality = quality;
    }
}
