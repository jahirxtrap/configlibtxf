<h2><strong>ConfigLib TXF mod</strong></h2>

Config library for minecraft mods

```java
// initialize config
TXFConfig.init(MODID, MyModConfig.class);

// Registering config screens (Neo/Forge)
ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
        new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> TXFConfig.getScreen(parent, MODID)));
```

forked from <a href="https://github.com/TeamMidnightDust/MidnightLib" target="_blank">TeamMidnightDust/MidnightLib</a>