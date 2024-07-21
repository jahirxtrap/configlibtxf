<h2><strong>ConfigLib TXF mod</strong></h2>

Config library for minecraft mods

```java
// initialize config
TXFConfig.init(MODID, MyModConfig.class);

// Registering config screens (Forge)
ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
        new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> TXFConfigClient.getScreen(parent, MODID)));

// Registering config screens (NeoForge)
ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () ->
                (client, parent) -> TXFConfig.getScreen(parent, MODID));
```

forked from <a href="https://github.com/TeamMidnightDust/MidnightLib" target="_blank">TeamMidnightDust/MidnightLib</a>