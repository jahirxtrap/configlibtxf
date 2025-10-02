package com.jahirtrap.configlib;

import com.google.gson.*;
import com.google.gson.stream.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

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

    public static final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT).excludeFieldsWithModifiers(Modifier.PRIVATE)
            .addSerializationExclusionStrategy(new HiddenAnnotationExclusionStrategy())
            .registerTypeAdapter(ResourceLocation.class, new TypeAdapter<ResourceLocation>() {
                public void write(JsonWriter out, ResourceLocation id) throws IOException { out.value(id.toString()); }
                public ResourceLocation read(JsonReader in) throws IOException { return ResourceLocation.parse(in.nextString()); }
            }).setPrettyPrinting().create();

    public static void init(String modid, Class<? extends TXFConfig> config) {
        path = FMLPaths.CONFIGDIR.get().resolve(modid + ".json");
        configClass.put(modid, config);

        for (Field field : config.getFields()) {
            if ((field.isAnnotationPresent(Entry.class) || field.isAnnotationPresent(Comment.class)) && !field.isAnnotationPresent(Server.class) && !field.isAnnotationPresent(Hidden.class) && (FMLEnvironment.getDist().isClient()))
                TXFConfigClient.initClient(modid, field);
        }
        try { gson.fromJson(Files.newBufferedReader(path), config); }
        catch (Exception e) { write(modid); }
    }

    public static TXFConfig getClass(String modid) {
        try { return configClass.get(modid).getDeclaredConstructor().newInstance(); } catch (Exception e) {throw new RuntimeException(e);}
    }
    public static void write(String modid) {
        getClass(modid).writeChanges(modid);
    }

    public void writeChanges(String modid) {
        try { if (!Files.exists(path = FMLPaths.CONFIGDIR.get().resolve(modid + ".json"))) Files.createFile(path);
            Files.write(path, gson.toJson(getClass(modid)).getBytes());
        } catch (Exception e) { e.fillInStackTrace(); }
    }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface Entry {
        int width() default 400;
        double min() default Double.MIN_NORMAL;
        double max() default Double.MAX_VALUE;
        String name() default "";
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
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface Client {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface Server {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface Hidden {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface Comment {
        boolean centered() default false;
        String category() default "default";
    }

    public static class HiddenAnnotationExclusionStrategy implements ExclusionStrategy {
        public boolean shouldSkipClass(Class<?> clazz) { return false; }
        public boolean shouldSkipField(FieldAttributes fieldAttributes) { return fieldAttributes.getAnnotation(Entry.class) == null; }
    }
}
