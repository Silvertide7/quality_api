package net.silvertide.quality_api.craft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.api.Qualities;
import net.silvertide.quality_api.quality.Quality;

import java.util.stream.Stream;

public record MinQualityIngredient(HolderSet<Item> items, int minLevel) implements ICustomIngredient {
    private static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, QualityAPI.MOD_ID);

    public static final MapCodec<MinQualityIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(MinQualityIngredient::items),
            Codec.INT.fieldOf("min_level").forGetter(MinQualityIngredient::minLevel)
    ).apply(instance, MinQualityIngredient::new));

    private static final DeferredHolder<IngredientType<?>, IngredientType<MinQualityIngredient>> TYPE =
            TYPES.register("min_quality", () -> new IngredientType<>(CODEC));

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    @Override
    public boolean test(ItemStack stack) {
        return items.contains(stack.getItemHolder()) && Qualities.level(stack) >= minLevel;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return items.stream().map(holder -> {
            ItemStack stack = new ItemStack(holder);
            for (ResourceLocation id : Qualities.idsByLevel()) {
                if (Qualities.get(id).map(Quality::level).orElse(0) >= minLevel && Qualities.apply(stack, id)) break;
            }
            return stack;
        });
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }
}
