<h2><strong>ConfigLib TXF mod</strong></h2>
<p><a href="https://www.curseforge.com/minecraft/mc-mods/configlib-txf"><img src="https://cf.way2muchnoise.eu/full_1515959_downloads.svg?badge_style=flat" alt="CurseForge downloads" /></a> <a href="https://modrinth.com/mod/configlib-txf"><img src="https://img.shields.io/badge/dynamic/json?color=2d2d2d&amp;colorA=17b85a&amp;style=flat-square&amp;label=&amp;suffix= downloads&amp;query=downloads&amp;url=https://api.modrinth.com/v2/project/HtQQHY1F&amp;logo=modrinth&amp;logoColor=2d2d2d" alt="Modrinth downloads" /></a> <a href="https://central.sonatype.com/artifact/io.github.jahirxtrap/configlibtxf"><img src="https://img.shields.io/maven-central/v/io.github.jahirxtrap/configlibtxf?style=flat" alt="Maven Central" /></a> <a href="https://deepwiki.com/jahirxtrap/configlibtxf"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki" /></a></p>

A powerful & lightweight config library for Minecraft mods

<strong>Main features:</strong>

<strong>Automatic Config Screen:</strong> A ready-to-use GUI with tabs, categories, and a reset button on every field

<strong>JSON5 Config Files:</strong> Human-friendly config files with auto-generated comments showing defaults, ranges, and allowed values

<strong>Rich Widgets:</strong> Toggles, sliders, cycle buttons, color picker, file/directory chooser, and item/block icon preview

<strong>Real-Time Editing:</strong> Changes apply instantly while editing, with per-field validation (min/max, regex) and visual feedback

<strong>Server Sync:</strong> Seamlessly syncs selected fields from server to clients in multiplayer, keeping gameplay consistent

<strong>Multiple Configs:</strong> Split a mod's settings across several files with an automatic hub screen for navigation

<strong>Full Translation Support:</strong> Every label, tooltip, tab, and enum value can be translated through the standard language files

<img src="https://cdn.modrinth.com/data/HtQQHY1F/images/3f3582ad22420ceb7c4f9cc13dcff52533fa10bf.png">

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

Replace `TAG` with the version you want (e.g. `26.1.2-2.0.0`).

### Usage

```java
// initialize config
TXFConfig.init(MODID, ModConfig.class);
```