package net.silvertide.quality_api.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import org.jetbrains.annotations.Nullable;

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
        int insertAt = 1;
        if (Config.SHOW_QUALITY_TOOLTIP.get()) {
            Component tierName = quality.level() == 0 ? null : Qualities.displayName(tier);
            Component qualityLine = qualityLine(tierName, stack.get(QualityAPI.CRAFTER.get()));
            if (qualityLine != null) insertAt = insert(lines, insertAt, qualityLine);
        }
        if (Qualities.isBroken(stack)) {
            insert(lines, insertAt, Component.translatable("quality_api.tooltip.broken").withStyle(ChatFormatting.RED));
        }
    }

    @Nullable
    private static Component qualityLine(@Nullable Component tierName, @Nullable String crafter) {
        if (crafter == null) return tierName;
        MutableComponent craftedBy = Component.translatable("quality_api.tooltip.crafter", crafter).withStyle(ChatFormatting.GRAY);
        if (tierName == null) return craftedBy;
        return tierName.copy().append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(craftedBy);
    }

    private static int insert(List<Component> lines, int index, Component line) {
        int at = Math.min(index, lines.size());
        lines.add(at, line);
        return at + 1;
    }
}
