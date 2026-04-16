# Screen & Server Sync

## Server Config Sync

Fields marked with `syncServer = true` are automatically sent from the server to connecting clients. This ensures all players use the server's config values for gameplay-critical settings.

### How It Works

1. A mod calls `TXFConfig.init()` with a config that has `syncServer` fields
2. ConfigLib registers network packets and player join/disconnect events automatically
3. When a player joins the server, all `syncServer` fields are serialized and sent to the client
4. The client applies the server values to the config fields in memory
5. When the player disconnects, the values are restored to the client's local config

**Local config files are never modified by server sync.** Server values only exist in memory while connected.

### Usage

```java
@Entry(name = "Allow Flight", syncServer = true)
public static boolean allowFlight = false;

@Entry(name = "Max Health", min = 1, max = 100, syncServer = true)
public static int maxHealth = 20;

// Client-only setting, no sync needed
@Entry(name = "Show Particles")
public static boolean showParticles = true;
```

### Supported Types

`boolean`, `int`, `float`, `double`, `String`, enums, `List<String>`

### Reading Synced Values

No special code needed. Read the static field as usual:

```java
if (ModConfig.allowFlight) {
    // On a server: uses the server's value
    // In singleplayer: uses the local value
}
```

### Zero Overhead

If no mod registers `syncServer` fields, no network packets or events are registered. The sync system is fully lazy.

---

## Config Screen

ConfigLib automatically generates a config screen with widgets for each field type.

### Fabric

Requires [Mod Menu](https://modrinth.com/mod/modmenu). The config screen is registered automatically via the `modmenu` entrypoint. No additional code needed.

### Forge & NeoForge

The config screen is registered natively through the mod list screen. No additional dependencies required. The registration happens automatically in `ConfigLibMod`.

### Widget Types

| Field Type | Widget |
|-----------|--------|
| `boolean` | Toggle button (Yes/No) |
| `int`, `float`, `double` | Text field with validation |
| `int`, `float`, `double` + `isSlider` | Slider |
| `String` | Text field |
| `Enum` | Cycle button |
| `List<String>` | Expandable list with add/remove |
| `String` + `isColor` | Text field + color picker button |
| `String` + `selectionMode` | Text field + file explorer button |
| Field + `idMode` | Text field + dynamic item/block icon |
| Field + `itemDisplay` | Static item icon (decorative) |

### Categories as Tabs

Fields grouped by `category` are displayed in separate tabs. The first category is selected by default.

### Real-Time Preview

Changes are applied to config fields in real-time while editing. Clicking **Done** saves to the file. Clicking **Cancel** reverts all changes.

### Sync-Locked Fields in Multiplayer

When connected to a server, fields with `syncServer = true` are:

- Displayed with server values (read-only)
- Text fields show the `NOT_ALLOWED` cursor
- Toggle buttons, sliders, and reset buttons are disabled
- Lists can be expanded to view values, but add/remove/edit is disabled
- Clicking **Done** only saves non-synced fields to the local file
- Server values are never written to the local config file

### Hub Screen (Multiple Configs)

When a mod registers multiple config files, a hub screen is shown with a button for each config. Each button shows the display name and file name in gray. An editor icon button opens the file directly.

Navigation:
- **Done** in config → saves and returns to the hub
- **Done** in hub → closes everything
- **Cancel** in config → closes everything without saving
- **Back** in config → returns to the hub without saving

### Editor Button

The config screen includes an editor button that opens the JSON5 file directly in the system's default text editor.