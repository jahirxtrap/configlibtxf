package com.jahirtrap.configlib;

import net.fabricmc.api.ModInitializer;

public class ConfigLibMod implements ModInitializer {

    public static final String MODID = "configlibtxf";

    @Override
    public void onInitialize() {
        TXFConfig.init(MODID, ExampleConfig.class, "example");
    }
}
