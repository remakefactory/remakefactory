package com.remakefactory.remakefactory.init;

import com.remakefactory.remakefactory.Remakefactory;
import com.remakefactory.remakefactory.item.MultiblockPlaceholderItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Items {
    // 1. 创建一个针对物品的 DeferredRegister
    // 它会收集所有要注册的物品，然后在合适的时机批量注册。
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Remakefactory.MODID); // 替换为您的 MOD_ID

    // 2. 注册我们的新物品
    // "multiblock_placeholder" 是物品的注册名 (ID)，必须是小写且唯一。
    public static final RegistryObject<Item> MULTIBLOCK_PLACEHOLDER = ITEMS.register("multiblock_placeholder",
            () -> new MultiblockPlaceholderItem(new Item.Properties()));

    // 3. 创建一个公共方法，用于在主 Mod 类中调用，将 DeferredRegister 附加到事件总线。
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
