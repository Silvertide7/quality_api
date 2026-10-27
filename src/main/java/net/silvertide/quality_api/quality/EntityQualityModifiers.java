package net.silvertide.quality_api.quality;

import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.silvertide.quality_api.QualityAPI;

public final class EntityQualityModifiers {
    private EntityQualityModifiers() {}

    public static void refresh(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.level().isClientSide()) return;
        Multimap<Holder<Attribute>, AttributeModifier> applied = living.getExistingDataOrNull(QualityAPI.APPLIED_ENTITY_MODIFIERS);
        Multimap<Holder<Attribute>, AttributeModifier> wanted = Snapshot.current().entityModifiers(living.getType(), living.getExistingDataOrNull(QualityAPI.ENTITY_QUALITY));
        if (wanted.equals(applied) || applied == null && wanted.isEmpty()) return;
        float maxHealthBefore = living.getMaxHealth();
        float healthFraction = living.getHealth() / maxHealthBefore;
        if (applied != null) living.getAttributes().removeAttributeModifiers(applied);
        living.getAttributes().addTransientAttributeModifiers(wanted);
        if (wanted.isEmpty()) {
            living.removeData(QualityAPI.APPLIED_ENTITY_MODIFIERS);
        } else {
            living.setData(QualityAPI.APPLIED_ENTITY_MODIFIERS, wanted);
        }
        if (living.getMaxHealth() != maxHealthBefore) living.setHealth(healthFraction * living.getMaxHealth());
    }

    public static void refreshLoadedEntities(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) refresh(entity);
        }
    }
}
