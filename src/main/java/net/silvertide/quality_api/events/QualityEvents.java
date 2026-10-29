package net.silvertide.quality_api.events;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.silvertide.quality_api.Config;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.command.QualityCommand;
import net.silvertide.quality_api.quality.EntityQualityModifiers;
import net.silvertide.quality_api.quality.Snapshot;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class QualityEvents {
    private QualityEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) return;
        Snapshot.rebuild(event.getRegistryAccess());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD && server != null) {
            EntityQualityModifiers.refreshLoadedEntities(server);
        }
    }

    @SubscribeEvent
    static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        ResourceKey<?> registry = event.getRegistryKey();
        boolean qualityDataSyncedToClient = event.getCause() == DataMapsUpdatedEvent.UpdateCause.CLIENT_SYNC
                && (registry.equals(Registries.ITEM) || registry.equals(Registries.ENTITY_TYPE));
        if (qualityDataSyncedToClient) Snapshot.rebuild(event.getRegistries());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        if (!player.hasData(QualityAPI.ENTITY_QUALITY)) return;
        if (event.isWasDeath() && !Config.KEEP_PLAYER_QUALITY_ON_DEATH.get()) {
            Qualities.remove(player);
            return;
        }
        EntityQualityModifiers.refresh(player);
        if (!event.isWasDeath()) player.setHealth(event.getOriginal().getHealth());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onLivingConvert(LivingConversionEvent.Post event) {
        LivingEntity outcome = event.getOutcome();
        if (!outcome.hasData(QualityAPI.ENTITY_QUALITY)) return;
        outcome.syncData(QualityAPI.ENTITY_QUALITY);
        EntityQualityModifiers.refresh(outcome);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        QualityCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Snapshot.Resolved resolved = resolvedFor(stack);
        if (resolved == null || resolved.attributes().length == 0) return;
        EquipmentSlot ownSlot = equipmentSlotOf(stack);
        EquipmentSlotGroup ownSlotGroup = EquipmentSlotGroup.bySlot(ownSlot);
        for (Snapshot.BakedAttribute attribute : resolved.attributes()) {
            EquipmentSlotGroup slot = attribute.slot() != null ? attribute.slot() : ownSlotGroup;
            EquipmentSlotGroup slotNamingTheModifier = slot.test(ownSlot) ? ownSlotGroup : slot;
            event.addModifier(attribute.attribute(), attribute.modifierFor(slotNamingTheModifier), slot);
        }
    }

    private static EquipmentSlot equipmentSlotOf(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        return equipable == null ? EquipmentSlot.MAINHAND : equipable.getEquipmentSlot();
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        Snapshot.Resolved resolved = resolvedFor(stack);
        if (resolved == null || resolved.miningSpeedMultiplier() == 1.0F) return;
        event.setNewSpeed(event.getNewSpeed() * resolved.miningSpeedMultiplier());
    }

    @SubscribeEvent
    static void onEnchantmentLevel(GetEnchantmentLevelEvent event) {
        ItemStack stack = event.getStack();
        Snapshot.Resolved resolved = resolvedFor(stack);
        if (resolved == null || resolved.enchantmentLevels().isEmpty() || Qualities.isBroken(stack)) return;
        ItemEnchantments.Mutable enchantments = event.getEnchantments();
        Holder<Enchantment> target = event.getTargetEnchant();
        if (target != null) {
            int bonus = target instanceof Holder.Reference<Enchantment> reference ? resolved.enchantmentLevels().getInt(reference.key()) : 0;
            if (bonus > 0) enchantments.set(target, enchantments.getLevel(target) + bonus);
            return;
        }
        for (Object2IntMap.Entry<ResourceKey<Enchantment>> entry : resolved.enchantmentLevels().object2IntEntrySet()) {
            event.getHolder(entry.getKey()).ifPresent(holder -> enchantments.set(holder, enchantments.getLevel(holder) + entry.getIntValue()));
        }
    }

    @Nullable
    private static Snapshot.Resolved resolvedFor(ItemStack stack) {
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        return tier == null ? null : Snapshot.current().resolved(stack.getItem(), tier);
    }
}
