package net.silvertide.quality_api.stack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.silvertide.quality_api.QualityAPI;

public class QualityStackItem extends Item {
    public QualityStackItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        QualityStackContents contents = QualityStacks.contents(stack);
        return contents == null ? super.getName(stack) : contents.hoverName();
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
        QualityStackContents contents = itemEntity.getItem().remove(QualityAPI.QUALITY_STACK_CONTENTS.get());
        if (contents != null) ItemUtils.onContainerDestroyed(itemEntity, contents.asStacks());
    }
}
