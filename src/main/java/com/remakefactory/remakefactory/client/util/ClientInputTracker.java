package com.remakefactory.remakefactory.client.util;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * A client-side utility class to track global input states, like whether the shift key is currently held down.
 * This state is updated by a Mixin on the KeyboardHandler.
 */
@OnlyIn(Dist.CLIENT)
public class ClientInputTracker {

    /**
     * A global flag indicating if the Shift key is currently pressed.
     * Updated by {@link com.remakefactory.remakefactory.mixin.client.KeyboardHandlerMixin}.
     */
    public static boolean isShiftKeyDown = false;

    /**
     * The key code for the left shift key.
     */
    public static final int SHIFT_KEY_CODE = GLFW.GLFW_KEY_LEFT_SHIFT;
}