package com.jahirtrap.configlib;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class AutoModMenu implements ModMenuApi {
    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        System.setProperty("java.awt.headless", "false");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        HashMap<String, ConfigScreenFactory<?>> map = new HashMap<>();
        TXFConfig.configClass.forEach((modid, cClass) -> map.put(modid, parent -> TXFConfig.getScreen(parent, modid)));
        return map;
    }
}
