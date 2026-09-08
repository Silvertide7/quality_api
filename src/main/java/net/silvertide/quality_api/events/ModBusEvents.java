package net.silvertide.quality_api.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.silvertide.quality_api.QualityAPI;
import net.silvertide.quality_api.quality.Quality;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class ModBusEvents {
    private ModBusEvents() {}

    @SubscribeEvent
    static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(QualityAPI.QUALITY_REGISTRY, Quality.CODEC, Quality.CODEC);
    }

    @SubscribeEvent
    static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(QualityAPI.ITEM_QUALITIES);
    }
}
