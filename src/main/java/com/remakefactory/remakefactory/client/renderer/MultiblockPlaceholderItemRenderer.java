package com.remakefactory.remakefactory.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.remakefactory.remakefactory.client.util.ClientInputTracker;
import com.remakefactory.remakefactory.item.MultiblockPlaceholderItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class MultiblockPlaceholderItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static MultiblockPlaceholderItemRenderer INSTANCE;

    private boolean lastRenderWasShift = false;

    private MultiblockPlaceholderItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static MultiblockPlaceholderItemRenderer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MultiblockPlaceholderItemRenderer();}
        return INSTANCE;
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext,
                             @NotNull PoseStack poseStack,
                             @NotNull MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LivingEntity entity = mc.player;
        Level level = mc.level;

        boolean shouldRenderShifted = ClientInputTracker.isShiftKeyDown;

        if (shouldRenderShifted != this.lastRenderWasShift) {
            this.lastRenderWasShift = shouldRenderShifted;
        }

        if (shouldRenderShifted) {
            ItemStack targetStack = MultiblockPlaceholderItem.getTargetStack(stack);

            if (targetStack != null) {
                BakedModel targetModel = itemRenderer.getModel(targetStack, level, entity, 0);
                itemRenderer.render(targetStack, displayContext, false, poseStack, buffer, combinedLight, combinedOverlay, targetModel);
                return;
            }
        }

        BakedModel defaultModel = itemRenderer.getModel(stack, level, entity, 0);
        itemRenderer.render(stack, displayContext, false, poseStack, buffer, combinedLight, combinedOverlay, defaultModel);
    }
}