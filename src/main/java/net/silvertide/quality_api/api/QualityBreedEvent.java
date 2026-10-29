package net.silvertide.quality_api.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;

public class QualityBreedEvent extends Event {
    private final Mob parentA;
    private final Mob parentB;
    @Nullable
    private final ServerPlayer player;
    private final AgeableMob child;
    private final OptionalDouble parentMeanLevel;
    @Nullable
    private ResourceLocation quality;

    public QualityBreedEvent(Mob parentA, Mob parentB, @Nullable ServerPlayer player, AgeableMob child, OptionalDouble parentMeanLevel, @Nullable ResourceLocation quality) {
        this.parentA = parentA;
        this.parentB = parentB;
        this.player = player;
        this.child = child;
        this.parentMeanLevel = parentMeanLevel;
        this.quality = quality;
    }

    public Mob getParentA() {
        return parentA;
    }

    public Mob getParentB() {
        return parentB;
    }

    @Nullable
    public ServerPlayer getPlayer() {
        return player;
    }

    public AgeableMob getChild() {
        return child;
    }

    public OptionalDouble getParentMeanLevel() {
        return parentMeanLevel;
    }

    @Nullable
    public ResourceLocation getQuality() {
        return quality;
    }

    public void setQuality(@Nullable ResourceLocation quality) {
        this.quality = quality;
    }
}
