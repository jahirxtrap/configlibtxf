package com.jahirtrap.configlib;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(ConfigLibMod.MODID)
@EventBusSubscriber(modid = ConfigLibMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ConfigLibMod {

    public static final String MODID = "configlibtxf";

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().forEachModContainer((modid, container) -> {
            if (TXFConfig.configClass.containsKey(modid)) {
                container.registerExtensionPoint(IConfigScreenFactory.class, (client, parent) -> TXFConfig.getScreen(parent, modid));
            }
        });
    }
}
