package net.silvertide.quality_api.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Traits;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AddTraitFunction extends LootItemConditionalFunction {
    private static final DeferredRegister<LootItemFunctionType<?>> TYPES =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, QualityAPI.MOD_ID);

    public static final MapCodec<AddTraitFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(ResourceLocation.CODEC.fieldOf("trait").forGetter(function -> function.trait))
            .apply(instance, AddTraitFunction::new));

    private static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<AddTraitFunction>> TYPE =
            TYPES.register("add_trait", () -> new LootItemFunctionType<>(CODEC));

    private static final Set<ResourceLocation> TRAITS_ALREADY_WARNED_ABOUT = ConcurrentHashMap.newKeySet();

    private final ResourceLocation trait;

    public AddTraitFunction(List<LootItemCondition> conditions, ResourceLocation trait) {
        super(conditions);
        this.trait = trait;
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (Traits.get(trait).isEmpty()) warnOnceAboutUnknownTrait();
        else Traits.add(stack, trait);
        return stack;
    }

    private void warnOnceAboutUnknownTrait() {
        if (TRAITS_ALREADY_WARNED_ABOUT.add(trait)) QualityAPI.LOGGER.warn("Loot function quality_api:add_trait names unknown trait {}", trait);
    }

    @Override
    public LootItemFunctionType<AddTraitFunction> getType() {
        return TYPE.get();
    }
}
