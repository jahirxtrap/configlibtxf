package com.jahirtrap.configlib;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(ConfigLibMod.MODID)
@Mod.EventBusSubscriber(modid = ConfigLibMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ConfigLibMod {

    public static final String MODID = "configlibtxf";

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().forEachModContainer((modid, container) -> {
            if (TXFConfig.configClass.containsKey(modid)) {
                container.registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class, () ->
                        new ConfigGuiHandler.ConfigGuiFactory((client, parent) -> TXFConfig.getScreen(parent, modid)));
            }
        });
    }
}
