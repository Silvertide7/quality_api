package net.silvertide.quality_api.events;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.QualityTooltipName;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = QualityAPI.MOD_ID, value = Dist.CLIENT)
public final class EntityQualityClient {
    private static final int TICKS_BETWEEN_SPARKLES_ON_AVERAGE = 60;
    private static final float SPARKLE_SIZE = 0.8F;
    private static final double SPARKLE_SPREAD_OF_WIDTH = 0.6;

    private EntityQualityClient() {}

    @SubscribeEvent
    static void showQualityOnLookedAtNameTag(RenderNameTagEvent event) {
        if (Config.ENTITY_QUALITY_DISPLAY.get() != Config.EntityQualityDisplay.NAMETAG || !Minecraft.renderNames() || event.canRender().isFalse()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = event.getEntity();
        if (entity != minecraft.crosshairPickEntity || minecraft.player == null || entity.isInvisibleTo(minecraft.player)) return;
        Component label = qualityLabel(entity, event.getContent());
        if (label == null) return;
        event.setContent(label);
        if (nameTagVisibleToEveryone(entity)) event.setCanRender(TriState.TRUE);
    }

    private static boolean nameTagVisibleToEveryone(Entity entity) {
        Team team = entity.getTeam();
        return !entity.isVehicle() && (team == null || team.getNameTagVisibility() == Team.Visibility.ALWAYS);
    }

    @SubscribeEvent
    static void showQualityWhenInspected(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || Config.ENTITY_QUALITY_DISPLAY.get() != Config.EntityQualityDisplay.INSPECT) return;
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return;
        Component label = qualityLabel(event.getTarget(), event.getTarget().getDisplayName());
        if (label != null) player.displayClientMessage(label, true);
    }

    @SubscribeEvent
    static void sparkleInTheTiersParticleColor(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide() || entity.getRandom().nextInt(TICKS_BETWEEN_SPARKLES_ON_AVERAGE) != 0) return;
        if (!Config.SHOW_ENTITY_QUALITY_PARTICLES.get() || entity.isInvisible() || isSeenFromInside(entity)) return;
        TextColor color = Qualities.get(entity).flatMap(Quality::particleColor).orElse(null);
        if (color == null) return;
        DustParticleOptions sparkle = new DustParticleOptions(Vec3.fromRGB24(color.getValue()).toVector3f(), SPARKLE_SIZE);
        entity.level().addParticle(sparkle, entity.getRandomX(SPARKLE_SPREAD_OF_WIDTH), entity.getRandomY(), entity.getRandomZ(SPARKLE_SPREAD_OF_WIDTH), 0, 0, 0);
    }

    private static boolean isSeenFromInside(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
    }

    @Nullable
    private static Component qualityLabel(Entity entity, Component name) {
        ResourceLocation tier = Qualities.getId(entity).orElse(null);
        if (tier == null || !Config.SHOW_QUALITY_TOOLTIP.get() || Config.isTierHidden(tier)) return null;
        Quality quality = Qualities.get(tier).orElse(null);
        if (quality == null) return null;
        return Component.empty().append(name).append(" - ").append(QualityTooltipName.of(Qualities.displayName(entity, tier), quality));
    }
}
