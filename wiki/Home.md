# ConfigLib TXF

A lightweight config library for Minecraft mods supporting **Fabric**, **Forge**, and **NeoForge**. It provides JSON5 config files with auto-generated comments, an automatic config screen, and server-to-client config synchronization.

## Setup

### Dependency

The library is published on [Maven Central](https://central.sonatype.com/artifact/io.github.jahirxtrap/configlibtxf).

Add the dependency to your `build.gradle`:

**Fabric:**
```gradle
dependencies {
    include(implementation("io.github.jahirxtrap:configlibtxf:${configlibtxf_version}-fabric"))
}
```

**Forge:**
```gradle
dependencies {
    implementation(jarJar("io.github.jahirxtrap:configlibtxf:${configlibtxf_version}-forge"))
}
```

**NeoForge:**
```gradle
dependencies {
    implementation(jarJar("io.github.jahirxtrap:configlibtxf:${configlibtxf_version}-neoforge"))
}
```

In `gradle.properties`:
```properties
configlibtxf_version=14.3.2
```

### Creating a Config Class

Create a class extending `TXFConfig` with static fields annotated with `@Entry`:

```java
import com.jahirtrap.configlib.TXFConfig;

public class ModConfig extends TXFConfig {
    @Entry(name = "Enable Feature")
    public static boolean enableFeature = true;

    @Entry(name = "Max Count", min = 1, max = 100)
    public static int maxCount = 10;

    @Entry(name = "Speed Multiplier", min = 0.0, max = 5.0)
    public static double speedMultiplier = 1.0;
}
```

### Initialization

Call `TXFConfig.init()` during mod initialization:

**Fabric:**
```java
public class MyMod implements ModInitializer {
    public static final String MODID = "mymod";

    @Override
    public void onInitialize() {
        TXFConfig.init(MODID, ModConfig.class);
    }
}
```

**Forge:**
```java
@Mod("mymod")
public class MyMod {
    public static final String MODID = "mymod";

    public MyMod() {
        TXFConfig.init(MODID, ModConfig.class);
    }
}
```

**NeoForge:**
```java
@Mod("mymod")
public class MyMod {
    public static final String MODID = "mymod";

    public MyMod(IEventBus bus) {
        TXFConfig.init(MODID, ModConfig.class);
    }
}
```

### Reading Config Values

Access values directly from the static fields:

```java
if (ModConfig.enableFeature) {
    // ...
}
```

### Generated JSON5 File

The config file is generated at `config/mymod.json5`:

```json5
{
  // default: true
  "enableFeature": true,
  // min: 1, max: 100, default: 10
  "maxCount": 10,
  // min: 0.0, max: 5.0, default: 1.0
  "speedMultiplier": 1.0
}
```

Comments are auto-generated based on field type, min/max values, enum options, and defaults.