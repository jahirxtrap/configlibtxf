package com.jahirtrap.configlib;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Base class for ConfigLib TXF configuration holders.
 * <p>
 * A configuration is declared by extending {@code TXFConfig} and adding
 * {@code public static} fields annotated with {@link Entry} (regular values) or
 * {@link Comment} (text-only entries shown in the GUI). The mod registers the
 * class at startup with one of the {@code init} overloads; from that point on
 * ConfigLib TXF takes care of loading the values from disk, writing them back
 * when the in-game screen is closed, and synchronising server-locked entries
 * to connected clients.
 * <p>
 * On-disk format is JSON5: the file is named {@code <modid>.json5} (or
 * {@code <modid>-<suffix>.json5} when a suffix is supplied) and lives under
 * the loader's standard config directory &mdash; or under
 * {@code config/<modid>/} when {@code useModFolder} is enabled. Legacy
 * {@code .json} files from older versions are migrated automatically the
 * first time {@code init} runs.
 * <p>
 * Multiple configs per mod are supported by calling {@code init} more than
 * once with distinct {@code suffix} values: each call adds a registry entry
 * under the key {@code <modid>:<suffix>}, and the client screen presents a
 * hub page when more than one entry exists for a given mod id.
 * <p>
 * Field-level behaviour is driven entirely by annotations:
 * <ul>
 *   <li>{@link Entry} &mdash; the field is exposed in the config file and the
 *       GUI; the annotation's attributes control the widget type (numeric
 *       slider, color picker, item/block id field, file chooser, regex-
 *       validated string, &hellip;).</li>
 *   <li>{@link Comment} &mdash; the field is rendered as decorative text in
 *       the GUI (no value is persisted). Useful for section headers or
 *       in-line notes.</li>
 *   <li>{@link Client} / {@link Server} &mdash; environment hints used by
 *       loader-specific filters; {@code @Server} fields are skipped on the
 *       client GUI side, {@code @Client} fields on the dedicated server.</li>
 *   <li>{@link Hidden} &mdash; field stays in code but is not exposed via
 *       the GUI nor written to the file.</li>
 * </ul>
 *
 * @see Entry
 * @see Comment
 * @see EntryMeta
 * @see TXFConfigClient#getScreen(net.minecraft.client.gui.screens.Screen, String)
 */
public abstract class TXFConfig {
    public static final Map<String, Class<? extends TXFConfig>> configClass = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> classToModid = new ConcurrentHashMap<>();
    private static final List<String> registrationOrder = new CopyOnWriteArrayList<>();
    static final Map<String, Path> configPaths = new ConcurrentHashMap<>();
    public static Path path;
    private static final Map<String, Map<String, Object>> defaultValues = new ConcurrentHashMap<>();
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]+$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    public static final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT).excludeFieldsWithModifiers(Modifier.PRIVATE)
            .addSerializationExclusionStrategy(new HiddenAnnotationExclusionStrategy())
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
            .setPrettyPrinting().create();

    /**
     * Registers a configuration class under the given mod id.
     * <p>
     * Shorthand for {@link #init(String, Class, String, boolean) init(modid,
     * config, "", false)}: the file is written as {@code <modid>.json5}
     * directly under the loader's config directory and the registry key is
     * {@code modid}. Use this overload for mods with a single configuration
     * file.
     * <p>
     * Call once at startup &mdash; from the Fabric {@code ModInitializer},
     * the Forge/NeoForge mod constructor, or any equivalent &mdash; before
     * the first time the player can open the config screen. Calling
     * {@code init} multiple times with the same key replaces the previous
     * registration.
     *
     * @param modid  the mod id used as both the registry key and the file
     *               name; must not be {@code null}
     * @param config the configuration class; must extend {@link TXFConfig}
     *               and expose its entries as {@code public static} fields
     * @see #init(String, Class, String, boolean)
     */
    public static void init(String modid, Class<? extends TXFConfig> config) {
        init(modid, config, "", false);
    }

    /**
     * Registers a configuration class under {@code modid} with the given
     * file suffix.
     * <p>
     * Equivalent to {@link #init(String, Class, String, boolean) init(modid,
     * config, suffix, false)}. The registry key becomes
     * {@code modid + ":" + suffix} and the on-disk file
     * {@code modid + "-" + suffix + ".json5"}. Use this overload when the
     * mod ships more than one configuration file (for example, a separate
     * "client" and "common" file); registering several suffixes against the
     * same {@code modid} causes the GUI to display a hub screen that lets
     * the player pick which sub-config to open.
     *
     * @param modid  the mod id (registry-key prefix)
     * @param config the configuration class
     * @param suffix sub-config identifier; must not be {@code null} but may
     *               be empty, in which case the call behaves like the
     *               two-argument overload
     * @see #init(String, Class, String, boolean)
     * @since 2.0.0
     */
    public static void init(String modid, Class<? extends TXFConfig> config, String suffix) {
        init(modid, config, suffix, false);
    }

    /**
     * Registers a configuration class with the option to put the file inside
     * a mod-specific subdirectory.
     * <p>
     * Equivalent to {@link #init(String, Class, String, boolean) init(modid,
     * config, "", useModFolder)}. When {@code useModFolder} is {@code true}
     * the file is written to {@code config/<modid>/<modid>.json5}; otherwise
     * it lives directly at {@code config/<modid>.json5}.
     *
     * @param modid        the mod id
     * @param config       the configuration class
     * @param useModFolder if {@code true}, place the file inside a
     *                     {@code config/<modid>/} subfolder; useful for
     *                     mods that ship several files and want to keep
     *                     the global config directory tidy
     * @see #init(String, Class, String, boolean)
     * @since 2.0.0
     */
    public static void init(String modid, Class<? extends TXFConfig> config, boolean useModFolder) {
        init(modid, config, "", useModFolder);
    }

    /**
     * Registers a configuration class with full control over both the file
     * suffix and the subfolder layout.
     * <p>
     * The runtime effects of one call are:
     * <ol>
     *   <li>the registry key {@code modid[:suffix]} is bound to
     *       {@code config};</li>
     *   <li>any legacy {@code .json} file is migrated to {@code .json5} in
     *       place;</li>
     *   <li>default values are captured from the {@link Entry}-annotated
     *       static fields so they can later be restored from the GUI's
     *       reset button or used as fallback during validation;</li>
     *   <li>client-side widget builders are initialised for every entry
     *       that is not annotated with {@link Server} or {@link Hidden}
     *       (skipped on dedicated servers);</li>
     *   <li>the file is parsed into the static fields; invalid values are
     *       silently replaced with the captured defaults;</li>
     *   <li>the file is rewritten to disk with up-to-date defaults,
     *       comments and category ordering;</li>
     *   <li>networking is wired up if the class declares at least one
     *       {@code syncServer = true} {@link Entry}.</li>
     * </ol>
     * <p>
     * Should be called once per configuration during mod initialisation; the
     * call must run on the main thread because it touches the
     * loader's filesystem APIs.
     *
     * @param modid        the mod id used as the registry-key prefix
     * @param config       the configuration class to register
     * @param suffix       sub-config identifier; an empty string registers
     *                     the configuration as the mod's primary file
     * @param useModFolder when {@code true}, the file lives under
     *                     {@code config/<modid>/}; otherwise directly in
     *                     the config directory
     * @since 2.0.0
     */
    public static void init(String modid, Class<? extends TXFConfig> config, String suffix, boolean useModFolder) {
        String key = suffix.isEmpty() ? modid : modid + ":" + suffix;
        String fileName = suffix.isEmpty() ? modid : modid + "-" + suffix;
        Path configDir = FMLPaths.CONFIGDIR.get();
        if (useModFolder) {
            configDir = configDir.resolve(modid);
            try {
                Files.createDirectories(configDir);
            } catch (Exception ignored) {
            }
        }
        path = configDir.resolve(fileName + ".json5");
        Path configPath = path;
        configPaths.put(key, configPath);
        Json5Helper.migrateLegacy(configPath);
        configClass.put(key, config);
        classToModid.put(config, key);
        if (!registrationOrder.contains(key)) registrationOrder.add(key);
        cacheDefaults(key, config);

        for (Field field : config.getFields()) {
            if ((field.isAnnotationPresent(Entry.class) || field.isAnnotationPresent(Comment.class)) && !field.isAnnotationPresent(Server.class) && !field.isAnnotationPresent(Hidden.class) && (FMLEnvironment.dist.isClient()))
                TXFConfigClient.initClient(key, field);
        }
        try {
            String content = Files.readString(configPath);
            JsonObject json = Json5Helper.parse(content);
            gson.fromJson(json, config);
            validateFields(key, config);
            write(key);
        } catch (Exception e) {
            write(key);
        }

        if (TXFConfigServer.hasSyncFields(key))
            TXFConfigServer.registerEvents();
    }

    private static void validateFields(String modid, Class<? extends TXFConfig> config) {
        var defaults = defaultValues.get(modid);
        if (defaults == null) return;
        try {
            for (Field field : config.getFields()) {
                if (!field.isAnnotationPresent(Entry.class)) continue;
                Entry e = field.getAnnotation(Entry.class);
                Object val = field.get(null), def = defaults.get(field.getName());
                if (def == null) continue;
                boolean invalid = false;
                Class<?> type = field.getType();
                if (type == int.class || type == float.class || type == double.class)
                    invalid = ((Number) val).doubleValue() < e.min() || ((Number) val).doubleValue() > e.max();
                else if (type.isEnum()) invalid = val == null;
                else if (val instanceof String s && !s.isEmpty()) {
                    if (!e.regex().isEmpty()) invalid = !s.matches(e.regex());
                    if (!invalid && e.isColor())
                        invalid = !COLOR_PATTERN.matcher(s.startsWith("#") ? s : "#" + s).matches();
                    if (!invalid && e.idMode() >= 0) invalid = !IDENTIFIER_PATTERN.matcher(s).matches();
                } else if (type == List.class && val instanceof List<?> list) {
                    int required = e.labels().length;
                    boolean badCount = required > 0 ? list.size() != required
                            : (e.minItems() >= 0 && list.size() < e.minItems()) || (e.maxItems() >= 0 && list.size() > e.maxItems());
                    if (badCount) {
                        field.set(null, def);
                        continue;
                    }
                    if (!e.regex().isEmpty() || e.isColor() || e.idMode() >= 0) {
                        var filtered = list.stream().filter(item -> {
                            if (!(item instanceof String s)) return false;
                            if (s.isEmpty()) return true;
                            if (!e.regex().isEmpty() && !s.matches(e.regex())) return false;
                            if (e.isColor() && !COLOR_PATTERN.matcher(s.startsWith("#") ? s : "#" + s).matches())
                                return false;
                            return e.idMode() < 0 || IDENTIFIER_PATTERN.matcher(s).matches();
                        }).toList();
                        if (filtered.size() != list.size()) {
                            if (required > 0 || (e.minItems() >= 0 && filtered.size() < e.minItems()))
                                field.set(null, def);
                            else
                                field.set(null, new ArrayList<>(filtered));
                            continue;
                        }
                    }
                }
                if (invalid) field.set(null, def);
            }
        } catch (Exception ignored) {
        }
    }

    private static void cacheDefaults(String modid, Class<? extends TXFConfig> config) {
        if (defaultValues.containsKey(modid)) return;
        Map<String, Object> defaults = new LinkedHashMap<>();
        try {
            for (Field field : config.getFields())
                if (field.isAnnotationPresent(Entry.class)) defaults.put(field.getName(), field.get(null));
        } catch (Exception ignored) {
        }
        defaultValues.put(modid, defaults);
    }

    public static TXFConfig getClass(String modid) {
        try {
            return configClass.get(modid).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void write(String modid) {
        getClass(modid).writeChanges(modid);
    }

    /**
     * Returns metadata for {@code field} declared on the given configuration
     * class, or {@code null} if no entry by that name exists.
     * <p>
     * Convenience overload that resolves the registry key from {@code config}
     * before delegating to {@link #get(String, String)}; equivalent to
     * looking up the same field via {@code get(classToModid.get(config),
     * field)}.
     *
     * @param config the configuration class previously passed to
     *               {@link #init(String, Class)}
     * @param field  the {@link Entry}-annotated field name to inspect
     * @return the metadata snapshot, or {@code null} if {@code config} is
     * not registered or {@code field} does not exist
     * @see #get(String, String)
     * @since 2.0.0
     */
    @Nullable
    public static EntryMeta get(Class<? extends TXFConfig> config, String field) {
        String modid = classToModid.get(config);
        return modid != null ? get(modid, field) : null;
    }

    /**
     * Returns a metadata snapshot for {@code field} on the configuration
     * registered under {@code modid}, or {@code null} if no such entry
     * exists.
     * <p>
     * The returned {@link EntryMeta} bundles the current value, the
     * captured default, the bounds and validation hints declared on the
     * {@link Entry} annotation, and the live values of every other
     * annotation attribute &mdash; suitable for building tooltips, custom
     * UIs or audit log entries.
     * <p>
     * When the supplied {@code modid} does not include a suffix and no
     * direct match is found, every sub-config registered under
     * {@code modid:*} is searched in registration order; the first matching
     * entry is returned. This makes it easy to query a field by mod id only
     * when the actual key uses a suffix.
     *
     * @param modid the registry key, with or without the {@code :suffix}
     *              part
     * @param field the {@link Entry}-annotated field name to inspect
     * @return the metadata snapshot, or {@code null} when nothing matches
     * @since 2.0.0
     */
    @Nullable
    public static EntryMeta get(String modid, String field) {
        EntryMeta meta = buildMeta(modid, field);
        if (meta != null) return meta;
        // When only a base modid is passed, also search sub-configs in registration order
        if (!modid.contains(":")) {
            for (String key : registrationOrder) {
                if (key.startsWith(modid + ":")) {
                    meta = buildMeta(key, field);
                    if (meta != null) return meta;
                }
            }
        }
        return null;
    }

    private static EntryMeta buildMeta(String key, String field) {
        Class<? extends TXFConfig> config = configClass.get(key);
        if (config == null) return null;
        try {
            Field f = config.getField(field);
            if (!f.isAnnotationPresent(Entry.class)) return null;
            Entry e = f.getAnnotation(Entry.class);
            var defaults = defaultValues.get(key);
            Object def = defaults != null ? defaults.get(field) : null;
            Object val = f.get(null);
            return new EntryMeta(val, def, e.min(), e.max(), e.name(), e.comment(), e.regex(), e.regexMessage(), e.category(), e.idMode(), e.itemDisplay(), e.isColor(), e.isSlider(), e.precision(), e.syncServer());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Immutable snapshot of an {@link Entry} field's runtime state plus its
     * declared annotation attributes.
     * <p>
     * Returned by {@link TXFConfig#get(String, String)} and its sibling
     * overload, this record is the recommended way to inspect a config
     * entry from outside the GUI &mdash; for example to render a custom
     * tooltip, build a {@code /config} command, or write a regression test
     * that asserts on min/max bounds.
     *
     * @param value        the current value of the field
     * @param defaultValue the default captured at registration time
     * @param min          {@link Entry#min()} (or {@link Double#MIN_NORMAL}
     *                     if unset)
     * @param max          {@link Entry#max()} (or {@link Double#MAX_VALUE}
     *                     if unset)
     * @param name         {@link Entry#name()}; empty when the GUI should
     *                     auto-format the field name
     * @param comment      {@link Entry#comment()}; tooltip / file comment
     *                     text
     * @param regex        {@link Entry#regex()}; empty when no pattern is
     *                     enforced
     * @param regexMessage {@link Entry#regexMessage()}; the error shown when
     *                     the regex fails
     * @param category     {@link Entry#category()}; defaults to
     *                     {@code "default"}
     * @param idMode       {@link Entry#idMode()}: {@code -1} none,
     *                     {@code 0} item id, {@code 1} block id
     * @param itemDisplay  {@link Entry#itemDisplay()}: fixed item id shown
     *                     next to the field, or empty
     * @param isColor      {@code true} when the field is rendered as a
     *                     color picker
     * @param isSlider     {@code true} when the field is rendered as a
     *                     slider rather than a text input
     * @param precision    {@link Entry#precision()}; slider rounding factor
     *                     (default {@code 100})
     * @param syncServer   {@link Entry#syncServer()}; whether the value is
     *                     overridden by the server while connected
     * @see TXFConfig#get(String, String)
     * @see Entry
     * @since 2.0.0
     */
    public record EntryMeta(Object value, Object defaultValue, double min, double max, String name, String comment,
                            String regex, String regexMessage, String category, int idMode, String itemDisplay,
                            boolean isColor, boolean isSlider, int precision, boolean syncServer) {
        /**
         * Tests whether {@code val} satisfies the declared {@link #regex}
         * pattern.
         * <p>
         * Returns {@code true} unconditionally when no regex is set or when
         * {@code val} is empty &mdash; empty input is considered valid so
         * that the GUI can clear a field while the user is editing.
         *
         * @param val the candidate string
         * @return {@code true} when no regex applies or when {@code val}
         * matches the regex
         * @since 2.0.0
         */
        public boolean validate(String val) {
            return regex.isEmpty() || val.isEmpty() || val.matches(regex);
        }
    }

    public void writeChanges(String key) {
        try {
            Path configPath = configPaths.get(key);
            if (configPath == null) return;
            if (!Files.exists(configPath)) Files.createFile(configPath);
            JsonObject original = gson.toJsonTree(getClass(key)).getAsJsonObject();
            JsonObject json = orderByCategory(key, original);
            Map<String, String> comments = buildComments(key);
            Map<String, String> categories = buildCategories(key);
            Files.writeString(configPath, Json5Helper.serialize(json, comments, categories));
        } catch (Exception e) {
            e.fillInStackTrace();
        }
    }

    private static Map<String, String> buildComments(String modid) {
        Map<String, String> comments = new LinkedHashMap<>();
        Class<? extends TXFConfig> config = configClass.get(modid);
        var defaults = defaultValues.get(modid);
        for (Field field : config.getFields()) {
            if (!field.isAnnotationPresent(Entry.class)) continue;
            Entry e = field.getAnnotation(Entry.class);
            List<String> parts = new ArrayList<>();
            if (!e.comment().isEmpty()) parts.add(e.comment());
            List<String> meta = new ArrayList<>();
            Class<?> type = field.getType();
            if (type == int.class || type == float.class || type == double.class) {
                if (e.min() != Double.MIN_NORMAL) meta.add("min: " + formatNum(e.min(), type));
                if (e.max() != Double.MAX_VALUE) meta.add("max: " + formatNum(e.max(), type));
            }
            if (type == String.class || type == List.class) {
                if (e.min() != Double.MIN_NORMAL) meta.add("min length: " + (int) e.min());
                if (e.max() != Double.MAX_VALUE) meta.add("max length: " + (int) e.max());
                if (e.width() != 400) meta.add("max chars: " + e.width());
            }
            if (type == List.class) {
                if (e.labels().length > 0) parts.add("Order: " + String.join(", ", e.labels()));
                else {
                    if (e.minItems() >= 0) meta.add("min items: " + e.minItems());
                    if (e.maxItems() >= 0) meta.add("max items: " + e.maxItems());
                }
            }
            if (type.isEnum())
                meta.add("values: " + String.join(", ", Arrays.stream(type.getEnumConstants()).map(Object::toString).toArray(String[]::new)));
            if (defaults != null && defaults.containsKey(field.getName()))
                meta.add("default: " + defaults.get(field.getName()));
            if (!meta.isEmpty()) parts.add(String.join(", ", meta));
            if (!parts.isEmpty()) comments.put(field.getName(), String.join("\n", parts));
        }
        return comments;
    }

    private static JsonObject orderByCategory(String modid, JsonObject original) {
        Class<? extends TXFConfig> config = configClass.get(modid);
        LinkedHashMap<String, List<String>> grouped = new LinkedHashMap<>();
        for (Field field : config.getFields()) {
            if (!field.isAnnotationPresent(Entry.class)) continue;
            String category = field.getAnnotation(Entry.class).category();
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(field.getName());
        }
        JsonObject ordered = new JsonObject();
        for (List<String> fields : grouped.values())
            for (String name : fields)
                if (original.has(name)) ordered.add(name, original.get(name));
        return ordered;
    }

    private static Map<String, String> buildCategories(String modid) {
        Map<String, String> categories = new LinkedHashMap<>();
        Class<? extends TXFConfig> config = configClass.get(modid);
        for (Field field : config.getFields()) {
            if (!field.isAnnotationPresent(Entry.class)) continue;
            categories.put(field.getName(), field.getAnnotation(Entry.class).category());
        }
        return categories;
    }

    private static String formatNum(double val, Class<?> type) {
        if (type == int.class) return String.valueOf((int) val);
        if (type == float.class) return String.valueOf((float) val);
        return String.valueOf(val);
    }

    /**
     * Marks a {@code public static} field as a configurable entry.
     * <p>
     * Every annotated field is persisted to the JSON5 file, exposed in the
     * GUI and (optionally) validated against {@code min}/{@code max} bounds
     * or a {@code regex}. The widget type is inferred from the field's Java
     * type:
     * <ul>
     *   <li>{@code int}, {@code float}, {@code double} &rarr; text input
     *       (or slider when {@link #isSlider()} is {@code true});</li>
     *   <li>{@code boolean} &rarr; toggle button;</li>
     *   <li>{@link Enum} &rarr; cycling button;</li>
     *   <li>{@link String} &rarr; text input, optionally with color picker
     *       ({@link #isColor()}), item/block id helper
     *       ({@link #idMode()}) or file chooser
     *       ({@link #selectionMode()});</li>
     *   <li>{@link List} of {@link String} &rarr; expandable list
     *       editor; each element offers the same color picker, id helper or
     *       file chooser as a single {@link String}.</li>
     * </ul>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Entry {
        /**
         * Maximum length of the text typed into the widget, in characters.
         * <p>
         * Applies to {@link String} and {@link List} fields and
         * controls the underlying {@code EditBox.setMaxLength}; long values
         * are truncated when the user types past the limit. Defaults to
         * {@code 400}.
         *
         * @return the max character length for the input widget
         */
        int width() default 400;

        /**
         * Lower bound for the field's value.
         * <p>
         * Applied to numeric fields directly and to {@link String} /
         * {@link List} fields as the minimum length. Defaults to
         * {@link Double#MIN_NORMAL}, which the library treats as "unset".
         *
         * @return the lower bound, or {@link Double#MIN_NORMAL} when none
         */
        double min() default Double.MIN_NORMAL;

        /**
         * Upper bound for the field's value.
         * <p>
         * Same semantics as {@link #min()} but for the upper limit.
         * Defaults to {@link Double#MAX_VALUE} ("unset").
         *
         * @return the upper bound, or {@link Double#MAX_VALUE} when none
         */
        double max() default Double.MAX_VALUE;

        /**
         * Fixed labels for the elements of a {@link List} entry.
         * <p>
         * When non-empty, the list has a fixed size equal to the number of
         * labels: the GUI shows each label in place of the {@code #1, #2, …}
         * index and disables the add/remove buttons, and a list whose size
         * differs from the label count is reset to its default on load. The
         * labels are also emitted as an {@code Order: …} comment above the
         * entry in the JSON5 file. Takes priority over {@link #minItems()} and
         * {@link #maxItems()}. Has no effect on non-list fields.
         *
         * @return the per-element labels, or an empty array for an unlabeled
         * variable-length list
         * @since 2.0.2
         */
        String[] labels() default {};

        /**
         * Minimum number of elements required in a {@link List} entry.
         * <p>
         * Default {@code -1} (no minimum). On load, a list with fewer than
         * {@code minItems} elements is reset to its default; in the GUI the
         * remove ({@code ✕}) button is disabled once the minimum is reached.
         * Ignored when {@link #labels()} is set. Has no effect on non-list
         * fields.
         *
         * @return the minimum element count, or {@code -1} for no minimum
         * @since 2.0.2
         */
        int minItems() default -1;

        /**
         * Maximum number of elements allowed in a {@link List} entry.
         * <p>
         * Default {@code -1} (no limit). On load, a list with more than
         * {@code maxItems} elements is reset to its default; in the GUI the
         * add ({@code +}) button is disabled once the limit is reached.
         * Ignored when {@link #labels()} is set. Has no effect on non-list
         * fields.
         *
         * @return the maximum element count, or {@code -1} for no limit
         * @since 2.0.2
         */
        int maxItems() default -1;

        /**
         * Override for the entry's display name in the GUI.
         * <p>
         * When empty, the library falls back to the translation key
         * {@code <modid>.config.<fieldName>} and, failing that, to the
         * auto-formatted field name (camelCase split with capitalised
         * words).
         *
         * @return the literal display name, or an empty string to use the
         * translation/auto-formatted name
         */
        String name() default "";

        /**
         * Tooltip and inline JSON5 comment for the entry.
         * <p>
         * The text appears in the GUI tooltip and, prefixed by {@code //},
         * above the corresponding key in the generated file. Bounds,
         * defaults and enum values are appended automatically; this string
         * only needs to convey the field's purpose.
         *
         * @return the comment text
         */
        String comment() default "";

        /**
         * Mode for the optional file/directory chooser button.
         * <p>
         * Values: {@code -1} disables the chooser, {@code 0} restricts the
         * dialog to files ({@link JFileChooser#FILES_ONLY}), {@code 1} to
         * directories ({@link JFileChooser#DIRECTORIES_ONLY}), {@code 2}
         * accepts both ({@link JFileChooser#FILES_AND_DIRECTORIES}).
         * Defaults to {@code -1}.
         *
         * @return the file-selection mode
         */
        int selectionMode() default -1;

        /**
         * Dialog type for the file chooser, when one is shown.
         * <p>
         * Accepts {@link JFileChooser#OPEN_DIALOG} or
         * {@link JFileChooser#SAVE_DIALOG}. Only meaningful when
         * {@link #selectionMode()} &ge; {@code 0}.
         *
         * @return the dialog type
         */
        int fileChooserType() default JFileChooser.OPEN_DIALOG;

        /**
         * Whitelist of file extensions accepted by the chooser dialog.
         * <p>
         * The default value {@code {"*"}} accepts every file. Use values
         * without leading dot ({@code "png"}, {@code "json"}). Only used
         * when {@link #selectionMode()} restricts to files.
         *
         * @return the accepted extensions
         */
        String[] fileExtensions() default {"*"};

        /**
         * Mode for the optional item-id helper.
         * <p>
         * Values: {@code -1} disables the helper, {@code 0} treats the
         * field as a {@code minecraft:item} id and shows the item icon next
         * to the field, {@code 1} treats it as a {@code minecraft:block} id
         * and shows the block icon. Defaults to {@code -1}.
         * <p>
         * On {@link List} fields with {@code idMode >= 0}, every
         * expanded entry also gets the icon helper.
         *
         * @return the id-mode
         */
        int idMode() default -1;

        /**
         * Fixed item id to render next to the field, independent of the
         * value.
         * <p>
         * Useful for decorative entries that always represent the same
         * item (e.g. a "Golden Apple" toggle that always shows the apple
         * icon). Ignored when empty or blank. Mutually exclusive with
         * {@link #idMode()}.
         *
         * @return the item id to display, or an empty string
         */
        String itemDisplay() default "";

        /**
         * Renders the field as a hex color picker.
         * <p>
         * The widget validates input as a {@code #RRGGBB} string and
         * opens a {@link javax.swing.JColorChooser} dialog when the swatch
         * button is clicked. Applies to {@link String} fields and to each
         * element of a {@link List} of {@link String}.
         *
         * @return {@code true} to use the color-picker widget
         */
        boolean isColor() default false;

        /**
         * Renders the field as a slider rather than a text input.
         * <p>
         * Requires {@link #min()} and {@link #max()} to be set. Floating
         * point sliders round to a multiple of {@code 1 / precision} (see
         * {@link #precision()}).
         *
         * @return {@code true} to use a slider widget
         */
        boolean isSlider() default false;

        /**
         * Rounding factor for slider fields.
         * <p>
         * The slider snaps to multiples of {@code 1 / precision} of the
         * range. Defaults to {@code 100}, i.e. two decimal places when
         * {@link #min()} and {@link #max()} span a unit interval. Ignored
         * for integer sliders.
         *
         * @return the rounding factor
         */
        int precision() default 100;

        /**
         * Tab/category under which the entry is grouped in the GUI and the
         * file.
         * <p>
         * Each distinct value creates a tab in the config screen and a
         * blank-line break in the JSON5 file. Defaults to
         * {@code "default"}, which keeps everything on a single tab.
         *
         * @return the category name
         */
        String category() default "default";

        /**
         * Marks the field as server-synced.
         * <p>
         * While the client is connected to a server that also runs
         * ConfigLib TXF, the field's value is overridden by whatever the
         * server has configured; the GUI displays the field as read-only
         * until disconnection. Useful for gameplay-affecting values that
         * the server operator must control authoritatively.
         *
         * @return {@code true} to enable server-side sync
         */
        boolean syncServer() default false;

        /**
         * Regular expression the value must match.
         * <p>
         * Validation is performed both at load time (invalid stored values
         * are replaced with the default) and live in the GUI (the field
         * turns red and the {@code Done} button is disabled). Defaults to
         * an empty string, i.e. no pattern check. For {@link String}
         * fields the regex is applied to the value; for
         * {@link List} fields it is applied to each element and
         * non-matching ones are dropped silently.
         *
         * @return the regex pattern, or an empty string for no validation
         */
        String regex() default "";

        /**
         * Error message shown when {@link #regex()} fails.
         * <p>
         * Rendered in red in the tooltip; defaults to a generic
         * "Invalid format" message when left empty.
         *
         * @return the regex error message
         */
        String regexMessage() default "";
    }

    /**
     * Marks a field as client-only.
     * <p>
     * Informational only &mdash; the annotation has no functional effect
     * on persistence or screen rendering. Use it to document intent at the
     * declaration site (e.g. visual-only options) so readers can tell a
     * client-only field apart from a gameplay one at a glance.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Client {
    }

    /**
     * Hides a field from the config screen.
     * <p>
     * Server-only entries that should not be visible to players; the value
     * is still loaded from and written to the JSON5 file, only the in-game
     * GUI skips it. Combine with {@link Entry} on the same field.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Server {
    }

    /**
     * Hides a field from both the config screen and JSON serialisation.
     * <p>
     * Use for internal state that lives on an {@link Entry}-annotated field
     * but should not be exposed to the user or persisted across runs &mdash;
     * for instance a derived cache or a compatibility shim.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Hidden {
    }

    /**
     * Marks a field as decorative text in the GUI.
     * <p>
     * The annotated field is rendered as a label (no widget) under the
     * category specified by {@link #category()}. When the field is a
     * non-empty {@link String}, that value is shown verbatim; otherwise the
     * label falls back to the translation key
     * {@code <modid>.config.<fieldName>} or the auto-formatted field name.
     * Comments are not persisted to the JSON5 file.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Comment {
        /**
         * Renders the comment horizontally centred on its row.
         * <p>
         * Useful for section headers; defaults to {@code false}, i.e. the
         * text is left-aligned like a normal entry label.
         *
         * @return {@code true} to centre the text
         */
        boolean centered() default false;

        /**
         * Treats the field as a blank vertical spacer rather than a label.
         * <p>
         * When {@code true}, the GUI inserts an empty row to add visual
         * breathing room between entries. Other attributes are ignored in
         * this mode.
         *
         * @return {@code true} to render an empty spacer row
         */
        boolean spacer() default false;

        /**
         * Tab/category the comment belongs to.
         * <p>
         * Mirrors {@link Entry#category()}; defaults to {@code "default"}.
         *
         * @return the category name
         */
        String category() default "default";
    }

    public static class HiddenAnnotationExclusionStrategy implements ExclusionStrategy {
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }

        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            return fieldAttributes.getAnnotation(Entry.class) == null;
        }
    }
}
