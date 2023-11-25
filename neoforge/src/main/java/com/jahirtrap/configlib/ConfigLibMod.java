package com.jahirtrap.configlib;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.ConfigScreenHandler;

import static net.neoforged.fml.IExtensionPoint.DisplayTest.IGNORESERVERONLY;

@Mod(ConfigLibMod.MODID)
@Mod.EventBusSubscriber(modid = ConfigLibMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ConfigLibMod {

    public static final String MODID = "configlibtxf";

    public ConfigLibMod() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> IGNORESERVERONLY, (remote, server) -> true));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().forEachModContainer((modid, modContainer) -> {
            if (TXFConfig.configClass.containsKey(modid)) {
                modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
                        new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> TXFConfig.getScreen(parent, modid)));
            }
        });
    }
}
