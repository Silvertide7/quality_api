package net.silvertide.quality_api.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.Snapshot;

import java.util.List;

@EventBusSubscriber(modid = QualityAPI.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        if (tier == null) return;
        Quality quality = Snapshot.current().tiers().get(tier);
        if (quality == null) return;
        List<Component> lines = event.getToolTip();
        boolean showLines = Config.SHOW_QUALITY_TOOLTIP.get();
        int insertAt = 1;
        if (quality.level() != 0) {
            if (Config.COLOR_ITEM_NAME.get() && !stack.has(DataComponents.CUSTOM_NAME) && !lines.isEmpty()) {
                lines.set(0, lines.getFirst().copy().withStyle(style -> style.withColor(quality.color())));
            }
            if (showLines) insertAt = insert(lines, insertAt, Qualities.displayName(tier));
        }
        String crafter = stack.get(QualityAPI.CRAFTER.get());
        if (showLines && crafter != null) {
            insertAt = insert(lines, insertAt, Component.translatable("quality_api.tooltip.crafter", crafter).withStyle(ChatFormatting.GRAY));
        }
        if (Qualities.isBroken(stack)) {
            insert(lines, insertAt, Component.translatable("quality_api.tooltip.broken").withStyle(ChatFormatting.RED));
        }
    }

    private static int insert(List<Component> lines, int index, Component line) {
        int at = Math.min(index, lines.size());
        lines.add(at, line);
        return at + 1;
    }
}
