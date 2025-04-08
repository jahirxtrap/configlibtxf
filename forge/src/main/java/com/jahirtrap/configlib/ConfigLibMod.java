package com.jahirtrap.configlib;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod("configlibtxf")
@Mod.EventBusSubscriber(modid = "configlibtxf", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ConfigLibMod {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        System.setProperty("java.awt.headless", "false");
        ModList.get().forEachModContainer((modid, container) -> {
            if (TXFConfig.configClass.containsKey(modid)) {
                container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
                        new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> TXFConfig.getScreen(parent, modid)));
            }
        });
    }
}
