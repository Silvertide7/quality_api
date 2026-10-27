package net.silvertide.quality_api;

import com.mojang.logging.LogUtils;
import com.mojang.datafixers.util.Either;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.AdvancedDataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.silvertide.quality_api.compat.AppleSkinCompat;
import net.silvertide.quality_api.craft.MinQualityIngredient;
import net.silvertide.quality_api.loot.CopyEntityQualityFunction;
import net.silvertide.quality_api.loot.EntityMinQualityCondition;
import net.silvertide.quality_api.loot.RollQualityModifier;
import net.silvertide.quality_api.quality.EntityQualityEffects;
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

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResourceLocation>> ENTITY_QUALITY =
            ATTACHMENT_TYPES.register("quality", () -> AttachmentType.builder(() -> id("standard"))
                    .serialize(ResourceLocation.CODEC)
                    .copyOnDeath()
                    .sync(ResourceLocation.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Multimap<Holder<Attribute>, AttributeModifier>>> APPLIED_ENTITY_MODIFIERS =
            ATTACHMENT_TYPES.register("applied_entity_modifiers", () -> AttachmentType.<Multimap<Holder<Attribute>, AttributeModifier>>builder(ImmutableMultimap::of).build());

    public static final ResourceKey<Registry<Quality>> QUALITY_REGISTRY = ResourceKey.createRegistryKey(id("quality"));

    public static final DataMapType<Item, Map<ResourceLocation, QualityEffects>> ITEM_QUALITIES =
            AdvancedDataMapType.builder(id("item_qualities"), Registries.ITEM, QualityEffects.OVERRIDES_CODEC)
                    .synced(QualityEffects.OVERRIDES_CODEC, false)
                    .merger(QualityEffects::mergeOverrides)
                    .build();

    private static final Codec<String> NAME_SET_CODEC = Codec.STRING.fieldOf("name_set").codec();

    public static final DataMapType<Item, String> NAME_SET =
            AdvancedDataMapType.builder(id("name_set"), Registries.ITEM, NAME_SET_CODEC)
                    .synced(NAME_SET_CODEC, false)
                    .merger(QualityAPI::elementEntryBeatsTagEntry)
                    .build();

    public static final DataMapType<EntityType<?>, String> ENTITY_NAME_SET =
            AdvancedDataMapType.builder(id("name_set"), Registries.ENTITY_TYPE, NAME_SET_CODEC)
                    .synced(NAME_SET_CODEC, false)
                    .merger(QualityAPI::elementEntryBeatsTagEntry)
                    .build();

    public static final DataMapType<EntityType<?>, Map<ResourceLocation, EntityQualityEffects>> ENTITY_QUALITIES =
            AdvancedDataMapType.builder(id("entity_qualities"), Registries.ENTITY_TYPE, EntityQualityEffects.OVERRIDES_CODEC)
                    .merger(EntityQualityEffects::mergeOverrides)
                    .build();

    public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM, id("blacklist"));

    public QualityAPI(IEventBus modEventBus, ModContainer container) {
        DATA_COMPONENTS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        RollQualityModifier.register(modEventBus);
        CopyEntityQualityFunction.register(modEventBus);
        EntityMinQualityCondition.register(modEventBus);
        MinQualityIngredient.register(modEventBus);
        AppleSkinCompat.registerIfLoaded();
        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        if (FMLEnvironment.dist.isClient()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
    }

    private static <T> String elementEntryBeatsTagEntry(Registry<T> registry,
                                                        Either<TagKey<T>, ResourceKey<T>> firstSource, String first,
                                                        Either<TagKey<T>, ResourceKey<T>> secondSource, String second) {
        return firstSource.right().isPresent() && secondSource.left().isPresent() ? first : second;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
