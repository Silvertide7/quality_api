package net.silvertide.quality_api.events;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.Snapshot;

@EventBusSubscriber(modid = QualityAPI.MOD_ID, value = Dist.CLIENT)
public final class QualityMarkerClient {
    private static final ResourceLocation MARKER_SPRITE = QualityAPI.id("quality_marker");
    private static final int MARKER_SIZE = 5;
    private static final int MARKER_LEFT = 0;
    private static final int MARKER_TOP = 0;
    private static final float OVERLAY_LAYER = 200.0F;

    private QualityMarkerClient() {}

    @SubscribeEvent
    static void markEveryItemWithItsTier(RegisterItemDecorationsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != QualityAPI.QUALITY_STACK.get()) event.register(item, QualityMarkerClient::drawTierStar);
        }
    }

    private static boolean drawTierStar(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        if (tier == null || !Config.SHOW_QUALITY_MARKER.get()) return false;
        Quality quality = Snapshot.current().tiers().get(tier);
        if (quality == null || Config.HIDDEN_TOOLTIP_TIERS.get().contains(tier.toString())) return false;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_LAYER);
        drawMarker(graphics, x + MARKER_LEFT, y + MARKER_TOP, quality.color().getValue());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
        return false;
    }

    private static void drawMarker(GuiGraphics graphics, int left, int top, int rgb) {
        graphics.setColor(FastColor.ARGB32.red(rgb) / 255.0F, FastColor.ARGB32.green(rgb) / 255.0F, FastColor.ARGB32.blue(rgb) / 255.0F, 1.0F);
        graphics.blitSprite(MARKER_SPRITE, left, top, MARKER_SIZE, MARKER_SIZE);
    }
}
