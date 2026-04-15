package com.jahirtrap.configlib;

import com.google.gson.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Json5Helper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void migrateLegacy(Path json5Path) {
        Path jsonPath = Path.of(json5Path.toString().replace(".json5", ".json"));
        try {
            if (!Files.exists(json5Path) && Files.exists(jsonPath))
                Files.copy(jsonPath, json5Path);
        } catch (Exception ignored) {
        }
    }

    public static JsonObject parse(String json5) {
        String json = stripComments(json5);
        json = json.replaceAll(",\\s*([}\\]])", "$1");
        return JsonParser.parseString(json).getAsJsonObject();
    }

    public static String serialize(JsonObject json, Map<String, String> comments, Map<String, String> categories) {
        StringBuilder sb = new StringBuilder("{\n");
        var entries = json.entrySet().stream().toList();
        String lastCategory = null;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            String key = entry.getKey();
            String category = categories != null ? categories.get(key) : null;
            if (lastCategory != null && category != null && !category.equals(lastCategory))
                sb.append("\n");
            lastCategory = category;
            String comment = comments.get(key);
            if (comment != null)
                for (String line : comment.split("\n")) sb.append("  // ").append(line).append("\n");
            sb.append("  ").append(GSON.toJson(key)).append(": ").append(formatValue(entry.getValue()));
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        return sb.append("}\n").toString();
    }

    private static String formatValue(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < arr.size(); i++) {
                sb.append("    ").append(GSON.toJson(arr.get(i)));
                if (i < arr.size() - 1) sb.append(",");
                sb.append("\n");
            }
            return sb.append("  ]").toString();
        }
        return GSON.toJson(element);
    }

    private static String stripComments(String input) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inString) {
                result.append(c);
                if (c == '\\' && i + 1 < input.length()) result.append(input.charAt(++i));
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
                result.append(c);
            } else if (c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                while (i < input.length() && input.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < input.length() && !(input.charAt(i) == '*' && input.charAt(i + 1) == '/')) i++;
                i++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
