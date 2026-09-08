package net.silvertide.quality_api;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.AdvancedDataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.silvertide.quality_api.craft.MinQualityIngredient;
import net.silvertide.quality_api.loot.RollQualityModifier;
import net.silvertide.quality_api.quality.Quality;
import net.silvertide.quality_api.quality.QualityEffects;
import org.slf4j.Logger;

import java.util.Map;

@Mod(QualityAPI.MOD_ID)
public class QualityAPI {
    public static final String MOD_ID = "quality_api";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> QUALITY =
            DATA_COMPONENTS.registerComponentType("quality", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CRAFTER =
            DATA_COMPONENTS.registerComponentType("crafter", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final ResourceKey<Registry<Quality>> QUALITY_REGISTRY = ResourceKey.createRegistryKey(id("quality"));

    public static final DataMapType<Item, Map<ResourceLocation, QualityEffects>> ITEM_QUALITIES =
            AdvancedDataMapType.builder(id("item_qualities"), Registries.ITEM, QualityEffects.OVERRIDES_CODEC)
                    .synced(QualityEffects.OVERRIDES_CODEC, false)
                    .merger(QualityEffects::mergeOverrides)
                    .build();

    public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM, id("blacklist"));

    public QualityAPI(IEventBus modEventBus, ModContainer container) {
        DATA_COMPONENTS.register(modEventBus);
        RollQualityModifier.register(modEventBus);
        MinQualityIngredient.register(modEventBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        if (FMLEnvironment.dist.isClient()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
