package net.silvertide.quality_api.stack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.silvertide.quality_api.QualityAPI;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public record SelectQualityStackTierPayload(int containerId, int slotIndex, QualityStackSelection selection) implements CustomPacketPayload {
    public static final Type<SelectQualityStackTierPayload> TYPE = new Type<>(QualityAPI.id("select_quality_stack_tier"));

    private static final String PROTOCOL_VERSION = "1";

    private static final StreamCodec<RegistryFriendlyByteBuf, SelectQualityStackTierPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectQualityStackTierPayload::containerId,
            ByteBufCodecs.VAR_INT, SelectQualityStackTierPayload::slotIndex,
            QualityStackSelection.STREAM_CODEC, SelectQualityStackTierPayload::selection,
            SelectQualityStackTierPayload::new);

    @Override
    public Type<SelectQualityStackTierPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToServer(TYPE, STREAM_CODEC, SelectQualityStackTierPayload::handle);
    }

    private static void handle(SelectQualityStackTierPayload payload, IPayloadContext context) {
        Player player = context.player();
        AbstractContainerMenu menu = player.containerMenu;
        if (player.isSpectator() || menu.containerId != payload.containerId() || !menu.stillValid(player)) return;
        if (payload.slotIndex() < 0 || payload.slotIndex() >= menu.slots.size()) return;
        Slot slot = menu.getSlot(payload.slotIndex());
        ItemStack qualityStack = slot.getItem();
        if (!slot.allowModification(player) || !QualityStacks.select(qualityStack, payload.selection().tier())) return;
        slot.set(qualityStack);
    }
}
