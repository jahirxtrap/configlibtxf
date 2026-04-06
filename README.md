<h2><strong>ConfigLib TXF mod</strong></h2>
<p><a href="https://jitpack.io/#jahirxtrap/configlibtxf"><img src="https://jitpack.io/v/jahirxtrap/configlibtxf.svg?style=flat" alt="JitPack version" /></a></p>

Config library for minecraft mods

### Dependency

Add JitPack repository and the dependency for your loader:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Fabric
    implementation 'com.github.jahirxtrap.configlibtxf:fabric:TAG'
    // Forge
    implementation 'com.github.jahirxtrap.configlibtxf:forge:TAG'
    // NeoForge
    implementation 'com.github.jahirxtrap.configlibtxf:neoforge:TAG'
}
```

Replace `TAG` with the version you want (e.g. `14.2.8`).

### Usage

```java
// initialize config
TXFConfig.init(MODID, ModConfig.class);
```

forked from <a href="https://github.com/TeamMidnightDust/MidnightLib" target="_blank">TeamMidnightDust/MidnightLib</a>