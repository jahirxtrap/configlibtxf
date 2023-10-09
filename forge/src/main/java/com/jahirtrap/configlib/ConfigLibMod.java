package com.jahirtrap.configlib;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import static net.minecraftforge.fml.IExtensionPoint.DisplayTest.IGNORESERVERONLY;

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
