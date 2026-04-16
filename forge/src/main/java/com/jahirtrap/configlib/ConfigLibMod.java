package com.jahirtrap.configlib;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod("configlibtxf")
@Mod.EventBusSubscriber(modid = "configlibtxf", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ConfigLibMod {
    public ConfigLibMod() {
        TXFConfig.init("configlibtxf", ExampleConfig.class, "example");
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        System.setProperty("java.awt.headless", "false");
        ModList.forEachModContainer((modid, container) -> {
            if (TXFConfig.configClass.keySet().stream().anyMatch(k -> k.equals(modid) || k.startsWith(modid + ":"))) {
                container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
                        new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> TXFConfigClient.getScreen(parent, modid)));
            }
        });
    }
}
