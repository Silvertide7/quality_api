package net.silvertide.quality_api.compat;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.silvertide.quality_api.api.Qualities;
import squeek.appleskin.api.event.FoodValuesEvent;

public final class AppleSkinCompat {
    private AppleSkinCompat() {}

    public static void registerIfLoaded() {
        if (ModList.get().isLoaded("appleskin")) Bridge.register();
    }

    private static final class Bridge {
        private static void register() {
            NeoForge.EVENT_BUS.addListener(FoodValuesEvent.class, Bridge::applyQualityToFoodValues);
        }

        private static void applyQualityToFoodValues(FoodValuesEvent event) {
            event.modifiedFoodProperties = Qualities.applyFoodEffects(event.itemStack, event.modifiedFoodProperties);
        }
    }
}
