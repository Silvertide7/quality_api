package net.silvertide.quality_api.gametest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.silvertide.quality_api.QualityAPI;

@EventBusSubscriber(modid = QualityAPI.MOD_ID)
public final class EntityQualityTestPack {
    private EntityQualityTestPack() {}

    @SubscribeEvent
    static void addEntityQualityTestPack(AddPackFindersEvent event) {
        event.addPackFinders(QualityAPI.id("builtin_data_packs/quality_entity_test"), PackType.SERVER_DATA,
                Component.literal("Quality API Entity Test"), PackSource.DEFAULT, false, Pack.Position.TOP);
    }
}
