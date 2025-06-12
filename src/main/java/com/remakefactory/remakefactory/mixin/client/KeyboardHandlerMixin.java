package com.remakefactory.remakefactory.mixin.client;

import com.remakefactory.remakefactory.client.util.ClientInputTracker;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into the KeyboardHandler to listen for key presses and releases globally.
 * This updates our central ClientInputTracker for easy state checking elsewhere.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void remakefactory$onKeyPress(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        // We only care about the Left Shift key for this tracker.
        if (key == ClientInputTracker.SHIFT_KEY_CODE) {
            if (action == GLFW.GLFW_PRESS && !ClientInputTracker.isShiftKeyDown) {
                // Key was pressed, and our state was previously 'up'
                ClientInputTracker.isShiftKeyDown = true;
            } else if (action == GLFW.GLFW_RELEASE && ClientInputTracker.isShiftKeyDown) {
                // Key was released, and our state was previously 'down'
                ClientInputTracker.isShiftKeyDown = false;
            }
        }
    }
}