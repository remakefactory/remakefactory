package com.remakefactory.remakefactory;

import com.mojang.logging.LogUtils;
import com.remakefactory.remakefactory.init.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.remakefactory.remakefactory.config.Config;
/**
 * Mod的主类。Forge通过 @Mod 注解来识别它。
 * 这个类负责协调Mod的初始化过程。
 */
@SuppressWarnings("removal")
@Mod(Remakefactory.MODID) // @Mod注解，声明这是一个Mod，并指定其唯一ID
public class Remakefactory {

    // 将Mod ID定义为公开、静态、最终的常量，方便在整个项目中引用
    public static final String MODID = "remakefactory";
    // 创建一个专用的日志记录器，用于输出信息到控制台和日志文件
    private static final Logger LOGGER = LogUtils.getLogger();

    public Remakefactory() {
        // 获取Mod专用的事件总线，用于处理注册等加载时期的事件
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // --- 注册内容 ---

        // 调用ModItems类的注册方法，将所有物品注册到游戏中
        Items.register(modEventBus);
        // 你可以在这里添加其他注册，例如方块、实体等
        // ModBlocks.register(modEventBus);

        // --- 注册配置文件 ---

        // 注册客户端配置文件。Forge会自动生成名为 "remakefactory-client.toml" 的文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);



        // 游戏运行时的事件处理已经被分离到专门的事件类中（例如 ModEvents.java），
        // 使用 @Mod.EventBusSubscriber 注解自动注册，所以主类不再需要手动注册到MinecraftForge.EVENT_BUS。

        LOGGER.info("RemakeFactory Mod has been loaded!");
    }
}