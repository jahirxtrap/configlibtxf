package com.jahirtrap.configlib;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

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

public abstract class TXFConfig {
    public static final Map<String, Class<? extends TXFConfig>> configClass = new HashMap<>();
    public static Path path;
    private static final Map<String, Map<String, Object>> defaultValues = new HashMap<>();

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
        path = FabricLoader.getInstance().getConfigDir().resolve(modid + ".json5");
        Json5Helper.migrateLegacy(path);
        configClass.put(modid, config);
        cacheDefaults(modid, config);

        for (Field field : config.getFields()) {
            if ((field.isAnnotationPresent(Entry.class) || field.isAnnotationPresent(Comment.class)) && !field.isAnnotationPresent(Server.class) && !field.isAnnotationPresent(Hidden.class) && (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT))
                TXFConfigClient.initClient(modid, field);
        }
        try {
            String content = Files.readString(path);
            JsonObject json = Json5Helper.parse(content);
            gson.fromJson(json, config);
            write(modid);
        } catch (Exception e) {
            write(modid);
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

    public void writeChanges(String modid) {
        try {
            path = FabricLoader.getInstance().getConfigDir().resolve(modid + ".json5");
            if (!Files.exists(path)) Files.createFile(path);
            JsonObject original = gson.toJsonTree(getClass(modid)).getAsJsonObject();
            JsonObject json = orderByCategory(modid, original);
            Map<String, String> comments = buildComments(modid);
            Map<String, String> categories = buildCategories(modid);
            Files.writeString(path, Json5Helper.serialize(json, comments, categories));
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
            grouped.computeIfAbsent(category, _ -> new ArrayList<>()).add(field.getName());
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

        int selectionMode() default -1;        // -1 for none, 0 for file, 1 for directory, 2 for both

        int fileChooserType() default JFileChooser.OPEN_DIALOG;

        String[] fileExtensions() default {"*"};

        int idMode() default -1;               // -1 for none, 0 for item, 1 for block

        String itemDisplay() default "";

        boolean isColor() default false;

        boolean isSlider() default false;

        int precision() default 100;

        String category() default "default";
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
