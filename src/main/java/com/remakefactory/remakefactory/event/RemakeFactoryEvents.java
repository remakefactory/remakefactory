package com.remakefactory.remakefactory.event;

import com.remakefactory.remakefactory.Remakefactory;
import com.remakefactory.remakefactory.command.RemakeFactoryCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles all game events for the RemakeFactory mod.
 * The @Mod.EventBusSubscriber annotation automatically registers this class.
 */
@Mod.EventBusSubscriber(modid = Remakefactory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RemakeFactoryEvents {

    private RemakeFactoryEvents() {}

    /**
     * This method is called when the game registers command.
     * @param event The event object containing the command dispatcher.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RemakeFactoryCommands.register(event.getDispatcher());
    }
}