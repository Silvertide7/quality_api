package net.silvertide.quality_api.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;

public class RollQualityModifier extends LootModifier {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, QualityAPI.MOD_ID);

    public static final MapCodec<RollQualityModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
            .and(Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(modifier -> modifier.chance))
            .apply(instance, RollQualityModifier::new));

    private final float chance;

    public RollQualityModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register("roll_quality", () -> CODEC);
        SERIALIZERS.register(modEventBus);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        RandomSource random = context.getRandom();
        for (ItemStack stack : generatedLoot) {
            if (!stack.isDamageableItem() || stack.has(QualityAPI.QUALITY.get()) || random.nextFloat() >= chance) continue;
            Qualities.roll(stack, random).ifPresent(id -> Qualities.apply(stack, id));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
