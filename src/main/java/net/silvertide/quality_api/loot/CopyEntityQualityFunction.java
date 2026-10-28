package net.silvertide.quality_api.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.api.QualityLootEvent;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

public class CopyEntityQualityFunction extends LootItemConditionalFunction {
    private static final DeferredRegister<LootItemFunctionType<?>> TYPES =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, QualityAPI.MOD_ID);

    public static final MapCodec<CopyEntityQualityFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(Codec.DOUBLE.optionalFieldOf("level_offset", 0.0).forGetter(function -> function.levelOffset))
            .apply(instance, CopyEntityQualityFunction::new));

    private static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<CopyEntityQualityFunction>> TYPE =
            TYPES.register("copy_entity_quality", () -> new LootItemFunctionType<>(CODEC));

    private final double levelOffset;

    public CopyEntityQualityFunction(List<LootItemCondition> conditions, double levelOffset) {
        super(conditions);
        this.levelOffset = levelOffset;
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity == null || stack.has(QualityAPI.QUALITY.get())) return stack;
        OptionalDouble entityLevel = Qualities.get(entity).map(quality -> OptionalDouble.of(quality.level())).orElse(OptionalDouble.empty());
        ResourceLocation proposed = entityLevel.isPresent()
                ? Qualities.forLevel(stack, entityLevel.getAsDouble() + levelOffset, context.getRandom()).orElse(null)
                : null;
        ServerPlayer player = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        QualityLootEvent event = NeoForge.EVENT_BUS.post(new QualityLootEvent(stack, context, player, entity, entityLevel, proposed));
        if (event.getQuality() != null) Qualities.apply(stack, event.getQuality());
        return stack;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public LootItemFunctionType<CopyEntityQualityFunction> getType() {
        return TYPE.get();
    }
}
