package net.silvertide.quality_api.events;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.command.QualityCommand;
import net.silvertide.quality_api.quality.Snapshot;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class QualityEvents {
    private QualityEvents() {}

    @SubscribeEvent
    static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) Snapshot.rebuild(event.getRegistryAccess());
    }

    @SubscribeEvent
    static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) Snapshot.rebuild(event.getRegistries());
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        QualityCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Snapshot.Resolved resolved = resolvedFor(stack);
        if (resolved == null) return;
        if (Qualities.isBroken(stack)) {
            event.clearModifiers();
            return;
        }
        if (resolved.attributes().length == 0) return;
        EquipmentSlotGroup fallbackSlot = equipmentSlotOf(stack);
        for (Snapshot.BakedAttribute attribute : resolved.attributes()) {
            EquipmentSlotGroup slot = attribute.slot() != null ? attribute.slot() : fallbackSlot;
            event.addModifier(attribute.attribute(), attribute.modifierFor(slot), slot);
        }
    }

    private static EquipmentSlotGroup equipmentSlotOf(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        return equipable == null ? EquipmentSlotGroup.MAINHAND : EquipmentSlotGroup.bySlot(equipable.getEquipmentSlot());
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        Snapshot.Resolved resolved = resolvedFor(stack);
        if (resolved == null) return;
        if (Qualities.isBroken(stack)) {
            event.setNewSpeed(0.0F);
            return;
        }
        if (resolved.miningSpeedMultiplier() == 1.0F) return;
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
            Holder<Enchantment> holder = event.getHolder(entry.getKey()).orElse(null);
            if (holder != null) enchantments.set(holder, enchantments.getLevel(holder) + entry.getIntValue());
        }
    }

    @Nullable
    private static Snapshot.Resolved resolvedFor(ItemStack stack) {
        ResourceLocation tier = stack.get(QualityAPI.QUALITY.get());
        return tier == null ? null : Snapshot.current().resolved(stack.getItem(), tier);
    }
}
