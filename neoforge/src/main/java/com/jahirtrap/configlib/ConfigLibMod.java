package com.jahirtrap.configlib;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.ConfigScreenHandler;

import javax.swing.*;

import static net.minecraft.client.Minecraft.ON_OSX;

@Mod("configlibtxf")
@Mod.EventBusSubscriber(modid = "configlibtxf", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ConfigLibMod {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        System.setProperty("java.awt.headless", "false");
        try { if (!ON_OSX) { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }} catch (Exception ignored) {}
        ModList.get().forEachModContainer((modid, container) -> {
            if (TXFConfig.configClass.containsKey(modid)) {
                container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
                        new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> TXFConfig.getScreen(parent, modid)));
            }
        });
    }
}
