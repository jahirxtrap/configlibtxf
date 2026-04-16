package com.jahirtrap.configlib;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
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
import java.util.regex.Pattern;

public abstract class TXFConfig {
    public static final Map<String, Class<? extends TXFConfig>> configClass = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> classToModid = new ConcurrentHashMap<>();
    static final Map<String, Path> configPaths = new ConcurrentHashMap<>();
    public static Path path;
    private static final Map<String, Map<String, Object>> defaultValues = new ConcurrentHashMap<>();
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]+$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    public static final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT).excludeFieldsWithModifiers(Modifier.PRIVATE)
            .addSerializationExclusionStrategy(new HiddenAnnotationExclusionStrategy())
            .registerTypeAdapter(Identifier.class, new TypeAdapter<Identifier>() {
                public void write(JsonWriter out, Identifier id) throws IOException {
                    out.value(id.toString());
                }

                public Identifier read(JsonReader in) throws IOException {
                    return Identifier.parse(in.nextString());
                }
            }).setPrettyPrinting().create();

    public static void init(String modid, Class<? extends TXFConfig> config) {
        init(modid, config, "", false);
    }

    public static void init(String modid, Class<? extends TXFConfig> config, String suffix) {
        init(modid, config, suffix, false);
    }

    public static void init(String modid, Class<? extends TXFConfig> config, boolean useModFolder) {
        init(modid, config, "", useModFolder);
    }

    public static void init(String modid, Class<? extends TXFConfig> config, String suffix, boolean useModFolder) {
        String key = suffix.isEmpty() ? modid : modid + ":" + suffix;
        String fileName = suffix.isEmpty() ? modid : modid + "-" + suffix;
        Path configDir = FabricLoader.getInstance().getConfigDir();
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
        cacheDefaults(key, config);

        for (Field field : config.getFields()) {
            if ((field.isAnnotationPresent(Entry.class) || field.isAnnotationPresent(Comment.class)) && !field.isAnnotationPresent(Server.class) && !field.isAnnotationPresent(Hidden.class) && (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT))
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
            TXFConfigServer.register();
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
                } else if (type == List.class && !e.regex().isEmpty() && val instanceof List<?> list) {
                    var filtered = list.stream().filter(item -> item instanceof String s && s.matches(e.regex())).toList();
                    if (filtered.size() != list.size()) {
                        field.set(null, new ArrayList<>(filtered));
                        continue;
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

    @Nullable
    public static EntryMeta get(Class<? extends TXFConfig> config, String field) {
        String modid = classToModid.get(config);
        return modid != null ? get(modid, field) : null;
    }

    @Nullable
    public static EntryMeta get(String modid, String field) {
        Class<? extends TXFConfig> config = configClass.get(modid);
        if (config == null) return null;
        try {
            Field f = config.getField(field);
            if (!f.isAnnotationPresent(Entry.class)) return null;
            Entry e = f.getAnnotation(Entry.class);
            var defaults = defaultValues.get(modid);
            Object def = defaults != null ? defaults.get(field) : null;
            Object val = f.get(null);
            return new EntryMeta(val, def, e.min(), e.max(), e.name(), e.comment(), e.regex(), e.regexMessage(), e.category(), e.idMode(), e.itemDisplay(), e.isColor(), e.isSlider(), e.precision(), e.syncServer());
        } catch (Exception ex) {
            return null;
        }
    }

    public record EntryMeta(Object value, Object defaultValue, double min, double max, String name, String comment,
                            String regex, String regexMessage, String category, int idMode, String itemDisplay,
                            boolean isColor, boolean isSlider, int precision, boolean syncServer) {
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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Entry {
        int width() default 400;

        double min() default Double.MIN_NORMAL;

        double max() default Double.MAX_VALUE;

        String name() default "";

        String comment() default "";

        int selectionMode() default -1;

        int fileChooserType() default JFileChooser.OPEN_DIALOG;

        String[] fileExtensions() default {"*"};

        int idMode() default -1;

        String itemDisplay() default "";

        boolean isColor() default false;

        boolean isSlider() default false;

        int precision() default 100;

        String category() default "default";

        boolean syncServer() default false;

        String regex() default "";

        String regexMessage() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Client {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Server {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Hidden {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Comment {
        boolean centered() default false;

        boolean spacer() default false;

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
