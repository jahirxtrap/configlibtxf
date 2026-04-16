# Configuration

## Field Types

### Boolean

```java
@Entry(name = "Enable Feature")
public static boolean enableFeature = true;
```

Rendered as a toggle button (Yes/No).

### Integer

```java
@Entry(name = "Max Count", min = 1, max = 100)
public static int maxCount = 10;
```

### Float / Double

```java
@Entry(name = "Speed", min = 0.0, max = 5.0)
public static double speed = 1.0;
```

### String

```java
@Entry(name = "Player Name")
public static String playerName = "Steve";
```

### Enum

```java
@Entry(name = "Difficulty")
public static Difficulty difficulty = Difficulty.NORMAL;

public enum Difficulty { EASY, NORMAL, HARD }
```

Rendered as a cycle button. Supports translation keys: `modid.config.enum.EnumName.VALUE`

### List\<String\>

```java
@Entry(name = "Block List", width = 400)
public static List<String> blockList = Lists.newArrayList();
```

Rendered as an expandable list with add/remove buttons and per-item editing.

---

## @Entry Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | `String` | `""` | Display name. If empty, derived from field name |
| `min` | `double` | `Double.MIN_NORMAL` | Minimum value (numeric types) or min length (strings) |
| `max` | `double` | `Double.MAX_VALUE` | Maximum value (numeric types) or max length (strings) |
| `width` | `int` | `400` | Max character length for text fields |
| `comment` | `String` | `""` | Custom comment in the JSON5 file |
| `category` | `String` | `"default"` | Tab category for grouping fields |
| `isSlider` | `boolean` | `false` | Render as slider instead of text field (numeric types) |
| `precision` | `int` | `100` | Decimal precision for sliders |
| `isColor` | `boolean` | `false` | Enable color picker (hex string fields) |
| `idMode` | `int` | `-1` | Item/block icon: `-1` none, `0` item, `1` block |
| `itemDisplay` | `String` | `""` | Static item icon by registry id (e.g. `"minecraft:diamond"`) |
| `selectionMode` | `int` | `-1` | File chooser: `-1` none, `0` file, `1` directory, `2` both |
| `fileChooserType` | `int` | `OPEN_DIALOG` | `JFileChooser.OPEN_DIALOG` or `SAVE_DIALOG` |
| `fileExtensions` | `String[]` | `{"*"}` | Allowed file extensions for file chooser |
| `syncServer` | `boolean` | `false` | Sync this field from server to client. See [Screen & Server Sync](Screen-&-Server-Sync) |
| `regex` | `String` | `""` | Regex pattern for string validation. Field turns red if invalid |
| `regexMessage` | `String` | `""` | Error message shown when regex fails. Default: "Invalid format" |

---

## Slider Example

```java
@Entry(name = "Volume", min = 0.0, max = 1.0, precision = 100, isSlider = true)
public static double volume = 0.5;
```

`precision` controls rounding: `100` = 2 decimal places, `10` = 1 decimal place, `1000` = 3 decimal places.

## Color Picker Example

```java
@Entry(name = "Highlight Color", isColor = true)
public static String highlightColor = "#FF0000";
```

Shows a color preview button that opens a color picker dialog.

## Item/Block Icon Example

```java
// Dynamic icon from field value (field contains a registry id)
@Entry(name = "Target Block", idMode = 1)
public static String targetBlock = "minecraft:stone";

// Static icon (decorative, doesn't affect the value)
@Entry(name = "Enable Gold", itemDisplay = "minecraft:gold_ingot")
public static boolean enableGold = true;
```

## File/Directory Chooser Example

```java
@Entry(name = "Export Path", selectionMode = 1)
public static String exportPath = "";
```

## Regex Validation

```java
@Entry(name = "Username", regex = "^[a-zA-Z0-9_]{3,16}$", regexMessage = "Must be 3-16 alphanumeric characters")
public static String username = "Steve";
```

The field turns red and the Done button is disabled when the value doesn't match the regex. The error message is shown as a tooltip on hover. If `regexMessage` is omitted, "Invalid format" is shown by default. Regex also applies to `List<String>` fields — invalid items are filtered on load.

## Custom Comment

```java
@Entry(name = "Render Distance", min = 2, max = 32, comment = "Higher values may impact performance")
public static int renderDistance = 12;
```

Generated JSON5:
```json5
{
  // Higher values may impact performance
  // min: 2, max: 32, default: 12
  "renderDistance": 12
}
```

---

## Categories

Group fields into tabs using `category`:

```java
public static final String GENERAL = "general", COMBAT = "combat";

@Entry(category = GENERAL, name = "Enable Particles")
public static boolean enableParticles = true;

@Entry(category = COMBAT, name = "Attack Speed", min = 0.5, max = 3.0)
public static double attackSpeed = 1.0;
```

Each category becomes a tab in the config screen. The category name can be translated with `modid.config.category.categoryid`. Fields in different categories are separated by a blank line in the JSON5 file.

---

## Other Annotations

### @Comment

Adds a label row in the config screen (no editable field). The display text is resolved as: translation key > field value > formatted field name.

```java
// Displays the field value "Section Title"
@Comment
public static String sectionTitle = "Section Title";

// Centered text
@Comment(centered = true)
public static String header = "Centered Header";

// With category
@Comment(category = "combat")
public static String combatHeader = "Combat Settings";

// Blank spacer row
@Comment(spacer = true)
public static String s1;
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `centered` | `boolean` | `false` | Center the text horizontally |
| `spacer` | `boolean` | `false` | Render as a blank row (no text) |
| `category` | `String` | `"default"` | Tab category |

### @Server

Hides the field from the config screen (server-only, not visible to players):

```java
@Server
@Entry(name = "Admin Setting")
public static boolean adminSetting = false;
```

### @Hidden

Hides the field from both the config screen and JSON serialization:

```java
@Hidden
@Entry
public static String internalState = "";
```

### @Client

Marks a field as client-only (informational, no functional effect):

```java
@Client
@Entry(name = "Show HUD")
public static boolean showHud = true;
```

---

## Translation Keys

| Key | Usage |
|-----|-------|
| `modid.config.title` | Config screen title |
| `modid.config.fieldName` | Field display name |
| `modid.config.fieldName.tooltip` | Tooltip on hover |
| `modid.config.category.categoryid` | Tab name |
| `modid.config.enum.EnumName.VALUE` | Enum value display |
| `modid.config.hub.suffix` | Hub button name (multiple configs) |

All keys are optional. Fallbacks when a key doesn't exist:

| Key | Fallback |
|-----|----------|
| `modid.config.title` | Mod name from mod loader |
| `modid.config.fieldName` | `@Entry(name="...")` or field name as title case |
| `modid.config.fieldName.tooltip` | No tooltip shown |
| `modid.config.category.categoryid` | Screen title (if `"default"`) or categoryid capitalized |
| `modid.config.enum.EnumName.VALUE` | Enum name formatted (`HARD_MODE` → `"Hard Mode"`) |
| `modid.config.hub.suffix` | Suffix capitalized (`"client"` → `"Client"`) |

For `@Comment` fields: translation key > field value (`= "text"`) > formatted field name.

---

## Multiple Config Files

Split configs into separate files using the `suffix` parameter:

```java
TXFConfig.init(MODID, ClientConfig.class, "client");   // → config/mymod-client.json5
TXFConfig.init(MODID, ServerConfig.class, "server");   // → config/mymod-server.json5
```

When a mod has multiple config files, a **hub screen** is shown with buttons for each config. Single-config mods go directly to the config screen as usual.

Translation key for hub buttons: `modid.config.hub.suffix` (fallback: capitalized suffix name).

### Mod Folder

Organize configs in a subfolder:

```java
TXFConfig.init(MODID, Config.class, true);               // → config/mymod/mymod.json5
TXFConfig.init(MODID, ClientConfig.class, "client", true); // → config/mymod/mymod-client.json5
```

---

## EntryMeta API

Read config metadata from any mod at runtime:

```java
// From your own mod (using class reference)
var meta = TXFConfig.get(ModConfig.class, "maxHealth");

// From another mod (using modid)
var meta = TXFConfig.get("othermod", "maxHealth");

if (meta != null) {
    Object current = meta.value();        // current runtime value
    Object def = meta.defaultValue();     // default value
    double min = meta.min();              // min constraint
    boolean valid = meta.validate("10");  // test regex
}
```

Returns `@Nullable EntryMeta` with all annotation parameters + current value + default value. Returns `null` if the mod/field doesn't exist.

---

## JSON5 Validation

Invalid values in JSON5 files are automatically corrected on load:

- **int/float/double**: out of min/max range → reset to default
- **enum**: invalid constant → reset to default
- **String + regex**: doesn't match → reset to default
- **String + isColor**: not valid hex format → reset to default
- **String + idMode**: not a valid identifier (modid:name) → reset to default
- **List\<String\> + regex**: invalid items are filtered out

---

## Full Example

```java
public class ModConfig extends TXFConfig {
    public static final String GENERAL = "general", VISUALS = "visuals";

    @Entry(category = GENERAL, name = "Enabled", syncServer = true)
    public static boolean enabled = true;

    @Entry(category = GENERAL, name = "Max Level", min = 1, max = 50, syncServer = true)
    public static int maxLevel = 10;

    @Entry(category = GENERAL, name = "Allowed Items", idMode = 0, syncServer = true)
    public static List<String> allowedItems = Lists.newArrayList();

    @Entry(category = GENERAL, name = "Server Name", min = 3, max = 30)
    public static String serverName = "My Server";

    @Entry(category = GENERAL, name = "Mode", syncServer = true)
    public static Mode mode = Mode.NORMAL;

    @Entry(category = VISUALS, name = "Particle Color", isColor = true)
    public static String particleColor = "#FFFFFF";

    @Entry(category = VISUALS, name = "Opacity", min = 0.0, max = 1.0, precision = 100, isSlider = true)
    public static double opacity = 1.0;

    public enum Mode { NORMAL, CREATIVE, ADVENTURE }
}
```

Generated `mymod.json5`:
```json5
{
  // default: true
  "enabled": true,
  // min: 1, max: 50, default: 10
  "maxLevel": 10,
  // default: []
  "allowedItems": [],
  // min: 3, max: 30, default: My Server
  "serverName": "My Server",
  // values: NORMAL, CREATIVE, ADVENTURE, default: NORMAL
  "mode": "NORMAL",

  // default: #FFFFFF
  "particleColor": "#FFFFFF",
  // min: 0.0, max: 1.0, default: 1.0
  "opacity": 1.0
}
```