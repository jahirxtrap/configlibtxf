package com.jahirtrap.configlib;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import java.util.HashMap;
import java.util.Map;

public class AutoModMenu implements ModMenuApi {
    static {
        TXFConfig.init("configlibtxf", ExampleConfig.class, "example");
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        System.setProperty("java.awt.headless", "false");
        HashMap<String, ConfigScreenFactory<?>> map = new HashMap<>();
        TXFConfig.configClass.forEach((key, cClass) -> {
            String modid = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
            map.putIfAbsent(modid, parent -> TXFConfigClient.getScreen(parent, modid));
        });
        return map;
    }
}
