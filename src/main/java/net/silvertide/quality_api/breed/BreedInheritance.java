package net.silvertide.quality_api.breed;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityBreedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class BreedInheritance {
    private BreedInheritance() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBabySpawn(BabyEntitySpawnEvent event) {
        AgeableMob child = event.getChild();
        if (child == null) return;
        Mob parentA = event.getParentA();
        Mob parentB = event.getParentB();
        OptionalDouble meanLevel = parentMeanLevel(parentA, parentB);
        ResourceLocation proposed = Qualities.getId(child).orElse(null);
        if (proposed == null) proposed = inherit(parentA, parentB, meanLevel, child.getRandom());
        ServerPlayer player = event.getCausedByPlayer() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        QualityBreedEvent decision = new QualityBreedEvent(parentA, parentB, player, child, meanLevel, proposed);
        NeoForge.EVENT_BUS.post(decision);
        stamp(child, decision.getQuality());
    }

    private static OptionalDouble parentMeanLevel(Mob parentA, Mob parentB) {
        if (Qualities.getId(parentA).isEmpty() && Qualities.getId(parentB).isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of((Qualities.level(parentA) + Qualities.level(parentB)) / 2.0);
    }

    @Nullable
    private static ResourceLocation inherit(Mob parentA, Mob parentB, OptionalDouble meanLevel, RandomSource random) {
        if (meanLevel.isEmpty()) return null;
        boolean parentAIsHigher = Qualities.level(parentA) >= Qualities.level(parentB);
        return switch (Config.BREED_INHERITANCE.get()) {
            case HIGHEST -> tierOf(parentAIsHigher ? parentA : parentB);
            case LOWEST -> tierOf(parentAIsHigher ? parentB : parentA);
            case AVERAGE -> nearestTier(rollFraction(meanLevel.getAsDouble(), random));
            case NONE -> null;
        };
    }

    @Nullable
    private static ResourceLocation tierOf(Mob parent) {
        return Qualities.getId(parent).orElseGet(() -> nearestTier(0));
    }

    private static int rollFraction(double level, RandomSource random) {
        int lower = (int) Math.floor(level);
        return random.nextDouble() < level - lower ? lower + 1 : lower;
    }

    @Nullable
    private static ResourceLocation nearestTier(int level) {
        ResourceLocation nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (ResourceLocation id : Qualities.idsByLevel()) {
            int distance = Math.abs(Qualities.levelOf(id) - level);
            if (distance < nearestDistance) {
                nearest = id;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static void stamp(AgeableMob child, @Nullable ResourceLocation chosen) {
        if (chosen != null) Qualities.apply(child, chosen);
        else if (child.hasData(QualityAPI.ENTITY_QUALITY)) Qualities.remove(child);
    }
}
