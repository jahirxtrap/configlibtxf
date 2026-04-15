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

Adds a label row in the config screen (no editable field):

```java
@Comment
public static String spacer = "Section Title";

@Comment(centered = true)
public static String header = "Centered Header";

@Comment(category = "combat")
public static String combatHeader = "Combat Settings";
```

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

If a translation key doesn't exist, the display name falls back to the `name` parameter or the field name converted to title case.

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
  // values: NORMAL, CREATIVE, ADVENTURE, default: NORMAL
  "mode": "NORMAL",

  // default: #FFFFFF
  "particleColor": "#FFFFFF",
  // min: 0.0, max: 1.0, default: 1.0
  "opacity": 1.0
}
```