package com.jahirtrap.configlib;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

import static net.minecraftforge.network.NetworkConstants.IGNORESERVERONLY;

@Mod(ConfigLibMod.MODID)
public class ConfigLibMod {

    public static final String MODID = "configlibtxf";

    public ConfigLibMod() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> IGNORESERVERONLY, (remote, server) -> true));
    }
}
