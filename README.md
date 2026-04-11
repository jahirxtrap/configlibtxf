<h2><strong>ConfigLib TXF mod</strong></h2>
<p><a href="https://central.sonatype.com/artifact/io.github.jahirxtrap/configlibtxf"><img src="https://img.shields.io/maven-central/v/io.github.jahirxtrap/configlibtxf?style=flat" alt="Maven Central" /></a></p>

Config library for minecraft mods

### Dependency (Maven Central)

```groovy
repositories {
    mavenCentral()
}

dependencies {
    // Fabric
    implementation 'io.github.jahirxtrap:configlibtxf:TAG-fabric'
    // Forge
    implementation 'io.github.jahirxtrap:configlibtxf:TAG-forge'
    // NeoForge
    implementation 'io.github.jahirxtrap:configlibtxf:TAG-neoforge'
}
```

Replace `TAG` with the version you want (e.g. `14.3.2`).

### Usage

```java
// initialize config
TXFConfig.init(MODID, ModConfig.class);
```