package com.jahirtrap.configlib;

import java.util.List;

public class ExampleConfig extends TXFConfig {
    public static final String CATEGORY_1 = "Category 1", CATEGORY_2 = "Category 2";

    // Category 1
    @Comment(centered = true, category = CATEGORY_1)
    public static String header = "Example Config";

    @Entry(category = CATEGORY_1, comment = "A boolean field")
    public static boolean booleanField = true;

    @Entry(category = CATEGORY_1, comment = "A string field")
    public static String stringField = "Hello";

    @Entry(category = CATEGORY_1, comment = "String with regex", regex = "^[a-zA-Z0-9_]{3,16}$")
    public static String stringRegex = "Steve";

    @Entry(category = CATEGORY_1)
    public static Difficulty enumField = Difficulty.NORMAL;

    @Entry(category = CATEGORY_1, min = 0, max = 100, comment = "An integer field")
    public static int intField = 50;

    @Entry(category = CATEGORY_1, min = 0, max = 100, isSlider = true, comment = "Integer slider")
    public static int intSlider = 75;

    @Entry(category = CATEGORY_1, min = 0.0, max = 1.0, isSlider = true, precision = 100, comment = "Float slider")
    public static float floatSlider = 0.5f;

    @Entry(category = CATEGORY_1, min = 0.0, max = 1000.0, comment = "A double field")
    public static double doubleField = 42.0;

    @Entry(category = CATEGORY_1, idMode = 0, comment = "String with item icon")
    public static String stringItemId = "minecraft:diamond";

    @Entry(category = CATEGORY_1, idMode = 1, comment = "String with block icon")
    public static String stringBlockId = "minecraft:grass_block";

    @Entry(category = CATEGORY_1, itemDisplay = "minecraft:golden_apple", comment = "String with fixed item display")
    public static String stringItemDisplay = "Golden Apple";

    @Server
    @Entry(category = CATEGORY_1, min = 1, max = 20)
    public static int serverOnlyField = 10;

    @Hidden
    @Entry
    public static String hiddenField = "not visible";

    // Category 2
    @Entry(category = CATEGORY_2, isColor = true, width = 7, min = 7, comment = "Color picker")
    public static String colorField = "#FF5555";

    @Entry(category = CATEGORY_2, selectionMode = 0, comment = "File chooser")
    public static String fileChooser = "";

    @Entry(category = CATEGORY_2, selectionMode = 1, comment = "Directory chooser")
    public static String directoryChooser = "";

    @Entry(category = CATEGORY_2, comment = "String list")
    public static List<String> stringList = List.of("alpha", "beta", "gamma");

    @Entry(category = CATEGORY_2, idMode = 0, comment = "String list with item icons")
    public static List<String> stringListItems = List.of("minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot");

    public enum Difficulty {EASY, NORMAL, HARD, EXTREME}
}
