package com.jahirtrap.configlib;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(ConfigLibMod.MODID)
@EventBusSubscriber(modid = ConfigLibMod.MODID, value = Dist.CLIENT)
public class ConfigLibMod {

    public static final String MODID = "configlibtxf";

    public ConfigLibMod(IEventBus bus) {
        TXFConfigServer.register(bus);
        TXFConfig.init(MODID, ExampleConfig.class, "example");
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        System.setProperty("java.awt.headless", "false");
        ModList.get().forEachModContainer((modid, container) -> {
            if (TXFConfig.configClass.keySet().stream().anyMatch(k -> k.equals(modid) || k.startsWith(modid + ":"))) {
                container.registerExtensionPoint(IConfigScreenFactory.class, (client, parent) -> TXFConfigClient.getScreen(parent, modid));
            }
        });
    }
}
