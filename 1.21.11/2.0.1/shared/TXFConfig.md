# Class `TXFConfig`

**Package:** `com.jahirtrap.configlib`

```java
public abstract class TXFConfig
```

Base class for ConfigLib TXF configuration holders.

A configuration is declared by extending `TXFConfig` and adding
`public static` fields annotated with `Entry` (regular values) or
`Comment` (text-only entries shown in the GUI). The mod registers the
class at startup with one of the `init` overloads; from that point on
ConfigLib TXF takes care of loading the values from disk, writing them back
when the in-game screen is closed, and synchronising server-locked entries
to connected clients.

On-disk format is JSON5: the file is named `<modid>.json5` (or
`<modid>-<suffix>.json5` when a suffix is supplied) and lives under
the loader's standard config directory — or under
`config/<modid>/` when `useModFolder` is enabled. Legacy
`.json` files from older versions are migrated automatically the
first time `init` runs.

Multiple configs per mod are supported by calling `init` more than
once with distinct `suffix` values: each call adds a registry entry
under the key `<modid>:<suffix>`, and the client screen presents a
hub page when more than one entry exists for a given mod id.

Field-level behaviour is driven entirely by annotations:

- `Entry` — the field is exposed in the config file and the
GUI; the annotation's attributes control the widget type (numeric
slider, color picker, item/block id field, file chooser, regex-
validated string, …).

- `Comment` — the field is rendered as decorative text in
the GUI (no value is persisted). Useful for section headers or
in-line notes.

- `Client` / `Server` — environment hints used by
loader-specific filters; `@Server` fields are skipped on the
client GUI side, `@Client` fields on the dedicated server.

- `Hidden` — field stays in code but is not exposed via
the GUI nor written to the file.

> **See also**
> 
> - `Entry`
> - `Comment`
> - `EntryMeta`
> - `TXFConfigClient.getScreen(net.minecraft.client.gui.screens.Screen, String)`

---

## Fields

| Field | Description |
|---|---|
| [`configClass`](#configclass) |  |
| [`path`](#path) |  |
| [`gson`](#gson) |  |

## Methods

| Method | Summary |
|---|---|
| [`init(String, Class<?extends TXFConfig>)`](#init-string-class-?extends-txfconfig) | Registers a configuration class under the given mod id. |
| [`init(String, Class<?extends TXFConfig>, String)`](#init-string-class-?extends-txfconfig-string) | Registers a configuration class under `modid` with the given file suffix. |
| [`init(String, Class<?extends TXFConfig>, boolean)`](#init-string-class-?extends-txfconfig-boolean) | Registers a configuration class with the option to put the file inside a mod-specific subdirectory. |
| [`init(String, Class<?extends TXFConfig>, String, boolean)`](#init-string-class-?extends-txfconfig-string-boolean) | Registers a configuration class with full control over both the file suffix and the subfolder layout. |
| [`getClass(String)`](#getclass-string) |  |
| [`write(String)`](#write-string) |  |
| [`get(Class<?extends TXFConfig>, String)`](#get-class-?extends-txfconfig-string) | Returns metadata for `field` declared on the given configuration class, or `null` if no entry by that name exists. |
| [`get(String, String)`](#get-string-string) | Returns a metadata snapshot for `field` on the configuration registered under `modid`, or `null` if no such entry exists. |
| [`writeChanges(String)`](#writechanges-string) |  |

---

### `configClass`

```java
public static final Map<String, Class<?extends TXFConfig>> configClass
```

---

### `path`

```java
public static Path path
```

---

### `gson`

```java
public static final Gson gson
```

---

### `init(String, Class<?extends TXFConfig>)`

```java
public static void init(String modid, Class<?extends TXFConfig> config)
```

Registers a configuration class under the given mod id.

Shorthand for [`init(modid,
config, "", false)`](#init-string-class-string-boolean): the file is written as `<modid>.json5`
directly under the loader's config directory and the registry key is
`modid`. Use this overload for mods with a single configuration
file.

Call once at startup — from the Fabric `ModInitializer`,
the Forge/NeoForge mod constructor, or any equivalent — before
the first time the player can open the config screen. Calling
`init` multiple times with the same key replaces the previous
registration.

**Parameters:**

| Name | Description |
|---|---|
| `modid` | the mod id used as both the registry key and the file name; must not be `null` |
| `config` | the configuration class; must extend [`TXFConfig`](TXFConfig.md) and expose its entries as `public static` fields |

> **See also:** [`init(String, Class, String, boolean)`](#init-string-class-string-boolean)

---

### `init(String, Class<?extends TXFConfig>, String)`

```java
public static void init(String modid, Class<?extends TXFConfig> config, String suffix)
```

Registers a configuration class under `modid` with the given
file suffix.

Equivalent to [`init(modid,
config, suffix, false)`](#init-string-class-string-boolean). The registry key becomes
`modid + ":" + suffix` and the on-disk file
`modid + "-" + suffix + ".json5"`. Use this overload when the
mod ships more than one configuration file (for example, a separate
"client" and "common" file); registering several suffixes against the
same `modid` causes the GUI to display a hub screen that lets
the player pick which sub-config to open.

**Parameters:**

| Name | Description |
|---|---|
| `modid` | the mod id (registry-key prefix) |
| `config` | the configuration class |
| `suffix` | sub-config identifier; must not be `null` but may be empty, in which case the call behaves like the two-argument overload |

> **See also:** [`init(String, Class, String, boolean)`](#init-string-class-string-boolean)
> 
> **Since** 2.0.0

---

### `init(String, Class<?extends TXFConfig>, boolean)`

```java
public static void init(String modid, Class<?extends TXFConfig> config, boolean useModFolder)
```

Registers a configuration class with the option to put the file inside
a mod-specific subdirectory.

Equivalent to [`init(modid,
config, "", useModFolder)`](#init-string-class-string-boolean). When `useModFolder` is `true`
the file is written to `config/<modid>/<modid>.json5`; otherwise
it lives directly at `config/<modid>.json5`.

**Parameters:**

| Name | Description |
|---|---|
| `modid` | the mod id |
| `config` | the configuration class |
| `useModFolder` | if `true`, place the file inside a `config/<modid>/` subfolder; useful for mods that ship several files and want to keep the global config directory tidy |

> **See also:** [`init(String, Class, String, boolean)`](#init-string-class-string-boolean)
> 
> **Since** 2.0.0

---

### `init(String, Class<?extends TXFConfig>, String, boolean)`

```java
public static void init(String modid, Class<?extends TXFConfig> config, String suffix, boolean useModFolder)
```

Registers a configuration class with full control over both the file
suffix and the subfolder layout.

The runtime effects of one call are:

1. the registry key `modid[:suffix]` is bound to
`config`;

2. any legacy `.json` file is migrated to `.json5` in
place;

3. default values are captured from the `Entry`-annotated
static fields so they can later be restored from the GUI's
reset button or used as fallback during validation;

4. client-side widget builders are initialised for every entry
that is not annotated with `Server` or `Hidden`
(skipped on dedicated servers);

5. the file is parsed into the static fields; invalid values are
silently replaced with the captured defaults;

6. the file is rewritten to disk with up-to-date defaults,
comments and category ordering;

7. networking is wired up if the class declares at least one
`syncServer = true` `Entry`.

Should be called once per configuration during mod initialisation; the
call must run on the main thread because it touches the
loader's filesystem APIs.

**Parameters:**

| Name | Description |
|---|---|
| `modid` | the mod id used as the registry-key prefix |
| `config` | the configuration class to register |
| `suffix` | sub-config identifier; an empty string registers the configuration as the mod's primary file |
| `useModFolder` | when `true`, the file lives under `config/<modid>/`; otherwise directly in the config directory |

> **Since** 2.0.0

---

### `getClass(String)`

```java
public static TXFConfig getClass(String modid)
```

**Parameters:**

| Name | Description |
|---|---|
| `modid` |  |

---

### `write(String)`

```java
public static void write(String modid)
```

**Parameters:**

| Name | Description |
|---|---|
| `modid` |  |

---

### `get(Class<?extends TXFConfig>, String)`

```java
public static EntryMeta get(Class<?extends TXFConfig> config, String field)
```

Returns metadata for `field` declared on the given configuration
class, or `null` if no entry by that name exists.

Convenience overload that resolves the registry key from `config`
before delegating to [`get(String, String)`](#get-string-string); equivalent to
looking up the same field via `get(classToModid.get(config), field)`.

**Parameters:**

| Name | Description |
|---|---|
| `config` | the configuration class previously passed to [`init(String, Class)`](#init-string-class) |
| `field` | the `Entry`-annotated field name to inspect |

**Returns:** the metadata snapshot, or `null` if `config` is
not registered or `field` does not exist

> **See also:** [`get(String, String)`](#get-string-string)
> 
> **Since** 2.0.0

---

### `get(String, String)`

```java
public static EntryMeta get(String modid, String field)
```

Returns a metadata snapshot for `field` on the configuration
registered under `modid`, or `null` if no such entry
exists.

The returned `EntryMeta` bundles the current value, the
captured default, the bounds and validation hints declared on the
`Entry` annotation, and the live values of every other
annotation attribute — suitable for building tooltips, custom
UIs or audit log entries.

When the supplied `modid` does not include a suffix and no
direct match is found, every sub-config registered under
`modid:*` is searched in registration order; the first matching
entry is returned. This makes it easy to query a field by mod id only
when the actual key uses a suffix.

**Parameters:**

| Name | Description |
|---|---|
| `modid` | the registry key, with or without the `:suffix` part |
| `field` | the `Entry`-annotated field name to inspect |

**Returns:** the metadata snapshot, or `null` when nothing matches

> **Since** 2.0.0

---

### `writeChanges(String)`

```java
public void writeChanges(String key)
```

**Parameters:**

| Name | Description |
|---|---|
| `key` |  |

---

## Nested Record `EntryMeta`

```java
public record EntryMeta(Object value, Object defaultValue, double min, double max, String name, String comment, String regex, String regexMessage, String category, int idMode, String itemDisplay, boolean isColor, boolean isSlider, int precision, boolean syncServer)
```

Immutable snapshot of an `Entry` field's runtime state plus its
declared annotation attributes.

Returned by [`TXFConfig.get(String, String)`](TXFConfig.md#get-string-string) and its sibling
overload, this record is the recommended way to inspect a config
entry from outside the GUI — for example to render a custom
tooltip, build a `/config` command, or write a regression test
that asserts on min/max bounds.

**Components:**

| Name | Type |
|---|---|
| `value` | `Object` |
| `defaultValue` | `Object` |
| `min` | `double` |
| `max` | `double` |
| `name` | `String` |
| `comment` | `String` |
| `regex` | `String` |
| `regexMessage` | `String` |
| `category` | `String` |
| `idMode` | `int` |
| `itemDisplay` | `String` |
| `isColor` | `boolean` |
| `isSlider` | `boolean` |
| `precision` | `int` |
| `syncServer` | `boolean` |

> **See also**
> 
> - [`TXFConfig.get(String, String)`](TXFConfig.md#get-string-string)
> - `Entry`
> 
> **Since** 2.0.0

---

## Methods

| Method | Summary |
|---|---|
| [`validate(String)`](#validate-string) | Tests whether `val` satisfies the declared [`regex`](#regex) pattern. |

---

### `validate(String)`

```java
public boolean validate(String val)
```

Tests whether `val` satisfies the declared [`regex`](#regex)
pattern.

Returns `true` unconditionally when no regex is set or when
`val` is empty — empty input is considered valid so
that the GUI can clear a field while the user is editing.

**Parameters:**

| Name | Description |
|---|---|
| `val` | the candidate string |

**Returns:** `true` when no regex applies or when `val`
matches the regex

> **Since** 2.0.0

---

## Nested Class `HiddenAnnotationExclusionStrategy`

```java
public static class HiddenAnnotationExclusionStrategy
```

---

## Methods

| Method | Summary |
|---|---|
| [`shouldSkipClass(Class<?>)`](#shouldskipclass-class-?) |  |
| [`shouldSkipField(FieldAttributes)`](#shouldskipfield-fieldattributes) |  |

---

### `shouldSkipClass(Class<?>)`

```java
public boolean shouldSkipClass(Class<?> clazz)
```

**Parameters:**

| Name | Description |
|---|---|
| `clazz` |  |

---

### `shouldSkipField(FieldAttributes)`

```java
public boolean shouldSkipField(FieldAttributes fieldAttributes)
```

**Parameters:**

| Name | Description |
|---|---|
| `fieldAttributes` |  |
