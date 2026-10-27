package net.silvertide.quality_api.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;

import java.util.Set;

public record EntityMinQualityCondition(int minLevel) implements LootItemCondition {
    private static final DeferredRegister<LootItemConditionType> TYPES =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, QualityAPI.MOD_ID);

    public static final MapCodec<EntityMinQualityCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("min_level").forGetter(EntityMinQualityCondition::minLevel)
    ).apply(instance, EntityMinQualityCondition::new));

    private static final DeferredHolder<LootItemConditionType, LootItemConditionType> TYPE =
            TYPES.register("entity_min_quality", () -> new LootItemConditionType(CODEC));

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        return entity != null && Qualities.level(entity) >= minLevel;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public LootItemConditionType getType() {
        return TYPE.get();
    }
}
