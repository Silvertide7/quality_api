package net.silvertide.quality_api.events;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.quality.Snapshot;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class BrokenItems {
    private static final float BREAK_SOUND_VOLUME = 0.8F;
    private static final float BREAK_SOUND_MIN_PITCH = 0.8F;
    private static final float BREAK_SOUND_PITCH_RANGE = 0.4F;

    private BrokenItems() {}

    public static boolean isProtectedBrokenItem(ItemStack stack) {
        return Config.BROKEN_INSTEAD_OF_DESTROYED.get() && Qualities.isBroken(stack);
    }

    public static boolean breakInsteadOfDestroy(ItemStack stack, ServerLevel level, @Nullable LivingEntity entity) {
        if (!Config.BROKEN_INSTEAD_OF_DESTROYED.get()) return false;
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        if (tier == null) return false;
        Snapshot.Resolved resolved = Snapshot.current().resolved(stack.getItem(), tier);
        if (resolved == null) return false;

        if (level.getRandom().nextFloat() < resolved.breakDestroyChance()) return false;
        float downgradeChance = resolved.breakDowngradeChance().orElseGet(() -> Config.BREAK_DOWNGRADE_CHANCE.get().floatValue());
        boolean mayDowngrade = Config.HIGHEST_TIER_CAN_DOWNGRADE.get() || !Qualities.isHighestLevel(stack, tier);
        if (mayDowngrade && level.getRandom().nextFloat() < downgradeChance) {
            Qualities.shift(stack, tier, -1).ifPresent(lower -> Qualities.apply(stack, lower));
        }
        stack.set(DataComponents.DAMAGE, stack.getMaxDamage());
        if (entity != null) playBreakSound(level, entity);
        return true;
    }

    private static void playBreakSound(ServerLevel level, LivingEntity entity) {
        float pitch = BREAK_SOUND_MIN_PITCH + level.getRandom().nextFloat() * BREAK_SOUND_PITCH_RANGE;
        level.playSound(null, entity.blockPosition(), SoundEvents.ITEM_BREAK, entity.getSoundSource(), BREAK_SOUND_VOLUME, pitch);
    }

    private static boolean blocksUse(Player player, ItemStack stack) {
        return !player.hasInfiniteMaterials() && isProtectedBrokenItem(stack);
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (blocksUse(event.getEntity(), event.getEntity().getMainHandItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocksUse(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocksUse(event.getEntity(), event.getItemStack())) event.setUseItem(TriState.FALSE);
    }

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (blocksUse(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (blocksUse(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (blocksUse(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }
}
