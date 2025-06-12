package com.remakefactory.remakefactory.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.remakefactory.remakefactory.client.renderer.MultiblockPlaceholderItemRenderer;
import com.remakefactory.remakefactory.item.MultiblockPlaceholderItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ItemRenderer to provide custom rendering for MultiblockPlaceholderItem.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    /**
     * A recursion guard to prevent a StackOverflowError.
     * <p>
     * Our custom renderer might internally call ItemRenderer.render() again for subcomponents.
     * This ThreadLocal flag ensures that our injection logic only runs for the top-level call
     * and not for any recursive calls, allowing the original method to handle them.
     */
    @Unique
    private static final ThreadLocal<Boolean> remakefactory$isRenderingMultiblockPlaceholder = ThreadLocal.withInitial(() -> false);

    /**
     * Injects at the head of the render method to intercept rendering for our specific item.
     * If the item is a MultiblockPlaceholderItem, it redirects rendering to our custom
     * BlockEntityWithoutLevelRenderer and cancels the original method.
     */
    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderItem(
            ItemStack stack, ItemDisplayContext displayContext, boolean leftHand,
            PoseStack poseStack, MultiBufferSource buffer,
            int combinedLight, int combinedOverlay, BakedModel model,
            CallbackInfo ci) {

        // Guard against recursive calls from our own custom renderer.
        if (remakefactory$isRenderingMultiblockPlaceholder.get()) {
            return;
        }

        // Only handle our specific item type.
        if (!(stack.getItem() instanceof MultiblockPlaceholderItem)) {
            return;
        }

        try {
            // Set the recursion guard.
            remakefactory$isRenderingMultiblockPlaceholder.set(true);

            // Delegate rendering to our custom renderer.
            BlockEntityWithoutLevelRenderer renderer = MultiblockPlaceholderItemRenderer.getInstance();
            renderer.renderByItem(stack, displayContext, poseStack, buffer, combinedLight, combinedOverlay);

            // Cancel the original render call to prevent it from running.
            ci.cancel();

        } finally {
            // Always release the recursion guard, even if rendering fails.
            remakefactory$isRenderingMultiblockPlaceholder.set(false);
        }
    }
}