package net.silvertide.quality_api.stack;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.silvertide.quality_api.QualityAPI;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@EventBusSubscriber(modid = QualityAPI.MOD_ID, value = Dist.CLIENT)
public final class QualityStackRendering {
    private static final ModelResourceLocation QUALITY_STACK_MODEL = ModelResourceLocation.inventory(QualityAPI.id("quality_stack"));
    private static final int NO_TINT = -1;
    private static final int COUNT_RIGHT_EDGE = 17;
    private static final int COUNT_TOP = 9;
    private static final float COUNT_LAYER = 200.0F;
    private static final int COUNT_COLOR = 0xFFFFFF;
    private static final Supplier<BlockEntityWithoutLevelRenderer> HELD_ITEM_RENDERER = Suppliers.memoize(HeldItemRenderer::new);

    private QualityStackRendering() {}

    @SubscribeEvent
    static void drawAsHeldItem(ModelEvent.ModifyBakingResult event) {
        event.getModels().computeIfPresent(QUALITY_STACK_MODEL, (location, model) -> new HeldItemModel(model));
    }

    @SubscribeEvent
    static void tintAsHeldItem(RegisterColorHandlersEvent.Item event) {
        ItemColors itemColors = event.getItemColors();
        event.register((stack, tintIndex) -> {
            QualityStackContents contents = QualityStacks.contents(stack);
            return contents == null ? NO_TINT : itemColors.getColor(contents.template(), tintIndex);
        }, QualityAPI.QUALITY_STACK.get());
    }

    @SubscribeEvent
    static void drawCustomRenderedHeldItems(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return HELD_ITEM_RENDERER.get();
            }
        }, QualityAPI.QUALITY_STACK.get());
    }

    @SubscribeEvent
    static void drawTotalCount(RegisterItemDecorationsEvent event) {
        event.register(QualityAPI.QUALITY_STACK.get(), QualityStackRendering::drawTotalCount);
    }

    private static boolean drawTotalCount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        QualityStackContents contents = QualityStacks.contents(stack);
        if (contents == null) return false;
        String total = String.valueOf(contents.total());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, COUNT_LAYER);
        graphics.drawString(font, total, x + COUNT_RIGHT_EDGE - font.width(total), y + COUNT_TOP, COUNT_COLOR, true);
        graphics.pose().popPose();
        return false;
    }

    private static final class HeldItemModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides heldItemOverrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                QualityStackContents contents = QualityStacks.contents(stack);
                if (contents == null) return originalModel;
                return Minecraft.getInstance().getItemRenderer().getModel(contents.template(), level, entity, seed);
            }
        };

        private HeldItemModel(BakedModel originalModel) {
            super(originalModel);
        }

        @Override
        public ItemOverrides getOverrides() {
            return heldItemOverrides;
        }
    }

    private static final class HeldItemRenderer extends BlockEntityWithoutLevelRenderer {
        private HeldItemRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
            QualityStackContents contents = QualityStacks.contents(stack);
            if (contents == null) return;
            ItemStack heldItem = contents.template();
            IClientItemExtensions.of(heldItem).getCustomRenderer().renderByItem(heldItem, displayContext, poseStack, buffer, packedLight, packedOverlay);
        }
    }
}
