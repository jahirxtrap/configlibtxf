package com.jahirtrap.configlib;

import com.google.common.collect.Lists;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static net.minecraft.client.Minecraft.ON_OSX;

@SuppressWarnings("unchecked")
public abstract class TXFConfig {
    private static final Pattern INTEGER_ONLY = Pattern.compile("(-?[0-9]*)");
    private static final Pattern DECIMAL_ONLY = Pattern.compile("-?(\\d+\\.?\\d*|\\d*\\.?\\d+|\\.)");
    private static final Pattern HEXADECIMAL_ONLY = Pattern.compile("(-?[#0-9a-fA-F]*)");

    private static final List<EntryInfo> entries = new CopyOnWriteArrayList<>();

    public static class EntryInfo {
        Field field;
        Class<?> dataType;
        int width, listIndex;
        boolean centered;
        Object defaultValue, value, function;
        String modid, tempValue;   // The value visible in the config screen
        boolean inLimits = true;
        Component name, error;
        AbstractWidget actionButton; // color picker button / explorer button

        public void setValue(Object value) {
            if (this.field.getType() != List.class) { this.value = value;
                this.tempValue = value.toString();
            } else { writeList(this.listIndex, value);
                this.tempValue = toTemporaryValue(); }
        }
        public String toTemporaryValue() {
            if (this.field.getType() != List.class) return this.value.toString();
            else try { return ((List<?>) this.value).get(this.listIndex).toString(); } catch (Exception ignored) {return "";}
        }
        public <T> void writeList(int index, T value) {
            var list = new ArrayList<>((List<T>) this.value);
            if (index >= list.size()) list.add(value);
            else list.set(index, value);
            this.value = list;
        }
    }

    public static final Map<String, Class<? extends TXFConfig>> configClass = new HashMap<>();
    private static Path path;

    private static final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT).excludeFieldsWithModifiers(Modifier.PRIVATE)
            .addSerializationExclusionStrategy(new HiddenAnnotationExclusionStrategy())
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
            .setPrettyPrinting().create();

    public static void init(String modid, Class<? extends TXFConfig> config) {
        path = FabricLoader.getInstance().getConfigDir().resolve(modid + ".json");
        configClass.put(modid, config);

        for (Field field : config.getFields()) {
            EntryInfo info = new EntryInfo();
            if ((field.isAnnotationPresent(Entry.class) || field.isAnnotationPresent(Comment.class)) && !field.isAnnotationPresent(Server.class) && !field.isAnnotationPresent(Hidden.class) && (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT))
                initClient(modid, field, info);
            if (field.isAnnotationPresent(Comment.class)) info.centered = field.getAnnotation(Comment.class).centered();
            if (field.isAnnotationPresent(Entry.class))
                try { info.defaultValue = field.get(null);
                } catch (IllegalAccessException ignored) {}
        }
        try { gson.fromJson(Files.newBufferedReader(path), config); }
        catch (Exception e) { write(modid); }

        for (EntryInfo info : entries) {
            if (info.field.isAnnotationPresent(Entry.class)) try {
                info.value = info.field.get(null);
                info.tempValue = info.toTemporaryValue();
            } catch (IllegalAccessException ignored) {}
        }
    }
    @Environment(EnvType.CLIENT)
    private static void initClient(String modid, Field field, EntryInfo info) {
        Entry e = field.getAnnotation(Entry.class);
        info.dataType = getUnderlyingType(field);
        info.width = e != null ? e.width() : 0;
        info.field = field; info.modid = modid;
        if (info.dataType == List.class) {
            Class<?> listType = (Class<?>) ((ParameterizedType) info.field.getGenericType()).getActualTypeArguments()[0];
            try { info.dataType = (Class<?>) listType.getField("TYPE").get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) { info.dataType = listType; }
        }

        if (e != null) {
            if (!e.name().isEmpty()) info.name = new TranslatableComponent(e.name());
            if (info.dataType == int.class) textField(info, Integer::parseInt, INTEGER_ONLY, (int) e.min(), (int) e.max(), true);
            else if (info.dataType == float.class) textField(info, Float::parseFloat, DECIMAL_ONLY, (float) e.min(), (float) e.max(), false);
            else if (info.dataType == double.class) textField(info, Double::parseDouble, DECIMAL_ONLY, e.min(), e.max(), false);
            else if (info.dataType == String.class || info.dataType == List.class) textField(info, String::length, null, Math.min(e.min(), 0), Math.max(e.max(), 1), true);
            else if (info.dataType == boolean.class) {
                Function<Object, Component> func = value -> new TranslatableComponent((Boolean) value ? "gui.yes" : "gui.no").withStyle((Boolean) value ? ChatFormatting.GREEN : ChatFormatting.RED);
                info.function = new AbstractMap.SimpleEntry<Button.OnPress, Function<Object, Component>>(button -> {
                    info.setValue(!(Boolean) info.value); button.setMessage(func.apply(info.value));
                }, func);
            } else if (info.dataType.isEnum()) {
                List<?> values = Arrays.asList(field.getType().getEnumConstants());
                Function<Object, Component> func = value -> new TranslatableComponent(modid + ".config." + "enum." + info.dataType.getSimpleName() + "." + info.value.toString());
                info.function = new AbstractMap.SimpleEntry<Button.OnPress, Function<Object, Component>>(button -> {
                    int index = values.indexOf(info.value) + 1;
                    info.value = values.get(index >= values.size() ? 0 : index); button.setMessage(func.apply(info.value));
                }, func);
            }
        }
        entries.add(info);
    }
    public static Class<?> getUnderlyingType(Field field) {
        if (field.getType() == List.class) {
            Class<?> listType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            try { return (Class<?>) listType.getField("TYPE").get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) { return listType; }
        } else return field.getType();
    }

    private static void textField(EntryInfo info, Function<String,Number> f, Pattern pattern, double min, double max, boolean cast) {
        boolean isNumber = pattern != null;
        info.function = (BiFunction<EditBox, Button, Predicate<String>>) (t, b) -> s -> {
            s = s.trim();
            if (!(s.isEmpty() || !isNumber || pattern.matcher(s).matches())) return false;

            Number value = 0; boolean inLimits = false; info.error = null;
            if (!(isNumber && s.isEmpty()) && !s.equals("-") && !s.equals(".")) {
                try { value = f.apply(s); } catch(NumberFormatException e){ return false; }
                inLimits = value.doubleValue() >= min && value.doubleValue() <= max;
                info.error = inLimits? null : new TranslatableComponent(value.doubleValue() < min ?
                        "§cMinimum " + (isNumber? "value" : "length") + (cast? " is " + (int)min : " is " + min) :
                        "§cMaximum " + (isNumber? "value" : "length") + (cast? " is " + (int)max : " is " + max)).withStyle(ChatFormatting.RED);
            }

            info.tempValue = s;
            t.setTextColor(inLimits? 0xFFFFFFFF : 0xFFFF7777);
            info.inLimits = inLimits;
            b.active = entries.stream().allMatch(e -> e.inLimits);

            if (inLimits) {
                if (info.dataType == ResourceLocation.class) info.setValue(ResourceLocation.tryParse(s));
                else info.setValue(isNumber ? value : s);
            }

            if (info.field.getAnnotation(Entry.class).isColor()) {
                if (!s.contains("#")) s = '#' + s;
                if (!HEXADECIMAL_ONLY.matcher(s).matches()) return false;
                try { info.actionButton.setMessage(new TranslatableComponent("⬛").setStyle(Style.EMPTY.withColor(Color.decode(info.tempValue).getRGB())));
                } catch (Exception ignored) {}
            }
            return true;
        };
    }
    public static TXFConfig getClass(String modid) {
        try { return configClass.get(modid).getDeclaredConstructor().newInstance(); } catch (Exception e) {throw new RuntimeException(e);}
    }
    public static void write(String modid) {
        getClass(modid).writeChanges(modid);
    }

    public void writeChanges(String modid) {
        try { if (!Files.exists(path = FabricLoader.getInstance().getConfigDir().resolve(modid + ".json"))) Files.createFile(path);
            Files.write(path, gson.toJson(getClass(modid)).getBytes());
        } catch (Exception e) { e.fillInStackTrace(); }
    }
    @Environment(EnvType.CLIENT)
    public static Screen getScreen(Screen parent, String modid) {
        return new ConfigScreen(parent, modid);
    }
    @Environment(EnvType.CLIENT)
    public static class ConfigScreen extends Screen {
        protected ConfigScreen(Screen parent, String modid) {
            super(new TranslatableComponent(modid + ".config." + "title"));
            this.parent = parent; this.modid = modid;
            this.translationPrefix = modid + ".config.";
            loadValues();
        }
        public final String translationPrefix, modid;
        public final Screen parent;
        public ConfigListWidget list;
        public Button done;
        public double scrollProgress = 0d;

        // Real Time config update //
        @Override
        public void tick() {
            super.tick();
            scrollProgress = list.getScrollAmount();
            for (EntryInfo info : entries) try {info.field.set(null, info.value);} catch (IllegalAccessException ignored) {}
            updateButtons();
        }
        public void updateButtons() {
            if (this.list != null) {
                for (ButtonEntry entry : this.list.children()) {
                    if (entry.buttons != null && entry.buttons.size() > 1 && entry.buttons.get(1) instanceof Button button) {
                        button.active = !Objects.equals(entry.info.value.toString(), entry.info.defaultValue.toString());
                    }}}
        }
        public void loadValues() {
            try { gson.fromJson(Files.newBufferedReader(path), configClass.get(modid)); }
            catch (Exception e) { write(modid); }

            for (EntryInfo info : entries) {
                if (info.field.isAnnotationPresent(Entry.class))
                    try { info.value = info.field.get(null); info.tempValue = info.toTemporaryValue();
                    } catch (IllegalAccessException ignored) {}
            }
        }
        @Override
        public void onClose() {
            loadValues(); cleanup();
            Objects.requireNonNull(minecraft).setScreen(parent);
        }
        private void cleanup() {
            entries.forEach(info -> {
                info.error = null; info.value = null; info.tempValue = null; info.actionButton = null; info.listIndex = 0; info.inLimits = true;
            });
        }
        @Override
        public void init() {
            super.init();
            this.addRenderableWidget(new Button(this.width / 2 - 155, this.height - 27, 150, 20, CommonComponents.GUI_CANCEL, button -> this.onClose()));
            done = this.addRenderableWidget(new Button(this.width / 2 + 5, this.height - 27, 150, 20, CommonComponents.GUI_DONE, (button) -> {
                for (EntryInfo info : entries) if (info.modid.equals(modid)) try { info.field.set(null, info.value); } catch (IllegalAccessException ignored) {}
                write(modid); cleanup();
                Objects.requireNonNull(minecraft).setScreen(parent);
            }));
            this.addRenderableWidget(new SpriteIconButton(this.width / 2 - 182, this.height - 27, 20, 20, TextComponent.EMPTY, button -> Util.getPlatform().openFile(FabricLoader.getInstance().getConfigDir().resolve(modid + ".json").toFile()), new ResourceLocation("configlibtxf","textures/gui/sprites/icon/editor.png"), 12, 12));

            this.list = new ConfigListWidget(this.minecraft, this.width, this.height, 32, this.height - 32, 25);
            if (this.minecraft != null && this.minecraft.level != null) this.list.setRenderBackground(false);
            this.addWidget(this.list); fillList();
        }
        public void fillList() {
            for (EntryInfo info : entries) {
                if (info.modid.equals(modid)) {
                    Component name = Objects.requireNonNullElseGet(info.name, () -> new TranslatableComponent(translationPrefix + info.field.getName()));
                    Button resetButton = new SpriteIconButton(width - 205 + 150 + 25, 0, 20, 20, TextComponent.EMPTY, (button -> {
                        info.value = info.defaultValue; info.listIndex = 0;
                        info.tempValue = info.toTemporaryValue();
                        list.clear(); fillList();
                    }), new ResourceLocation("configlibtxf", "textures/gui/sprites/icon/reset.png"), 12, 12);

                    if (info.function != null) {
                        AbstractWidget widget;
                        Entry e = info.field.getAnnotation(Entry.class);

                        if (info.function instanceof Map.Entry) { // Enums & booleans
                            var values = (Map.Entry<Button.OnPress, Function<Object, Component>>) info.function;
                            if (info.dataType.isEnum())
                                values.setValue(value -> new TranslatableComponent(translationPrefix + "enum." + info.field.getType().getSimpleName() + "." + info.value.toString()));
                            widget = new Button(width - 185, 0, 150, 20, values.getValue().apply(info.value), values.getKey());
                        }
                        else if (e.isSlider())
                            widget = new SliderWidget(width - 185, 0, 150, 20, new TranslatableComponent(info.tempValue), (Double.parseDouble(info.tempValue) - e.min()) / (e.max() - e.min()), info);
                        else widget = new TextField(font, width - 185, 0, 150, 20, TextComponent.EMPTY);

                        if (widget instanceof EditBox textField) {
                            textField.setMaxLength(info.width); textField.setValue(info.tempValue);
                            Predicate<String> processor = ((BiFunction<EditBox, Button, Predicate<String>>) info.function).apply(textField, done);
                            textField.setFilter(processor);
                        }

                        Button cycleButton = null;
                        if (info.field.getType() == List.class) {
                            cycleButton = new Button(width - 185, 0, 20, 20, new TranslatableComponent(String.valueOf(info.listIndex)).withStyle(ChatFormatting.GOLD), (button -> {
                                var values = (List<?>) info.value;
                                values.remove("");
                                info.listIndex = info.listIndex + 1;
                                if (info.listIndex > values.size()) info.listIndex = 0;
                                info.tempValue = info.toTemporaryValue();
                                if (info.listIndex == values.size()) info.tempValue = "";
                                list.clear(); fillList();
                            }));
                        }
                        if (e.isColor()) {
                            Button colorButton = new Button(width - 185, 0, 20, 20, new TranslatableComponent("⬛"),
                                    button -> new Thread(() -> {
                                        Color newColor = JColorChooser.showDialog(null, null, Color.decode(!Objects.equals(info.tempValue, "") ? info.tempValue : "#FFFFFF"));
                                        if (newColor != null) {
                                            info.setValue("#" + Integer.toHexString(newColor.getRGB()).substring(2));
                                            list.clear(); fillList();
                                        }
                                    }).start()
                            );
                            try { colorButton.setMessage(new TranslatableComponent("⬛").setStyle(Style.EMPTY.withColor(Color.decode(info.tempValue).getRGB())));
                            } catch (Exception ignored) {}
                            info.actionButton = colorButton;
                        } else if (e.idMode() == 0 || e.idMode() == 1) {
                            info.actionButton = new ItemField(font, width - 185, 0, 20, 20, e.idMode());
                        } else if (!e.itemDisplay().isBlank()) {
                            info.actionButton = new ItemField(font, width - 185, 0, 20, 20, e.itemDisplay());
                        } else if (e.selectionMode() > -1) {
                            Button explorerButton = new SpriteIconButton(width - 185, 0, 20, 20, TextComponent.EMPTY,
                                    button -> new Thread(() -> {
                                        JFileChooser fileChooser = new JFileChooser(info.tempValue);
                                        fileChooser.setFileSelectionMode(e.selectionMode()); fileChooser.setDialogType(e.fileChooserType());
                                        if ((e.selectionMode() == JFileChooser.FILES_ONLY || e.selectionMode() == JFileChooser.FILES_AND_DIRECTORIES) && Arrays.stream(e.fileExtensions()).noneMatch("*"::equals))
                                            fileChooser.setFileFilter(new FileNameExtensionFilter(
                                                    new TranslatableComponent(translationPrefix + info.field.getName() + ".fileFilter").getString(), e.fileExtensions()));
                                        if (fileChooser.showDialog(null, null) == JFileChooser.APPROVE_OPTION) {
                                            info.setValue(fileChooser.getSelectedFile().getAbsolutePath());
                                            list.clear(); fillList();
                                        }
                                    }).start(),
                                    new ResourceLocation("configlibtxf", "textures/gui/sprites/icon/explorer.png"), 12, 12);
                            info.actionButton = explorerButton;
                        }
                        List<AbstractWidget> widgets = Lists.newArrayList(widget, resetButton);
                        if (info.actionButton != null) {
                            if (ON_OSX) info.actionButton.active = false;
                            widget.setWidth(widget.getWidth() - 22); widget.x += 22;
                            widgets.add(info.actionButton);
                        } if (cycleButton != null) {
                            if (info.actionButton != null) info.actionButton.x += 22;
                            widget.setWidth(widget.getWidth() - 22); widget.x += 22;
                            widgets.add(cycleButton);
                        }
                        this.list.addButton(widgets, name, info);
                    } else this.list.addButton(List.of(), name, info);
                } list.setScrollAmount(scrollProgress);
                updateButtons();
            }
        }
        @Override
        public void render(PoseStack context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            this.list.render(context, mouseX, mouseY, delta);
            drawCenteredString(context, font, title, width / 2, 15, 0xFFFFFF);

            for (EntryInfo info : entries) {
                if (info.modid.equals(modid)) {
                    if (list.getHoveredButton(mouseX,mouseY).isPresent()) {
                        AbstractWidget buttonWidget = list.getHoveredButton(mouseX,mouseY).get();
                        Component text = ButtonEntry.buttonsWithText.get(buttonWidget);
                        Component name = new TranslatableComponent(this.translationPrefix + info.field.getName());
                        String key = translationPrefix + info.field.getName() + ".tooltip";

                        if (info.error != null && text.equals(name)) renderTooltip(context, info.error, mouseX, mouseY);
                        else if (I18n.exists(key) && text.equals(name)) {
                            List<Component> list = new ArrayList<>();
                            for (String str : I18n.get(key).split("\n"))
                                list.add(new TranslatableComponent(str));
                            renderTooltip(context, list, null, mouseX, mouseY);
                        }
                    }
                }
            }
            if (this.list != null) for (ButtonEntry entry : this.list.children()) if (entry.buttons != null && entry.buttons.size() > 2) if (entry.buttons.get(2) instanceof ItemField widget && widget.dynamic) widget.setItem(entry.info.tempValue);
            super.render(context,mouseX,mouseY,delta);
        }
    }
    @Environment(EnvType.CLIENT)
    public static class ConfigListWidget extends ContainerObjectSelectionList<ButtonEntry> {
        public ConfigListWidget(Minecraft client, int i, int j, int k, int l, int m) { super(client, i, j, k, l, m); }
        @Override public int getScrollbarPosition() { return this.width - 7; }

        public void addButton(List<AbstractWidget> buttons, Component text, EntryInfo info) { this.addEntry(new ButtonEntry(buttons, text, info)); }
        public void clear() { this.clearEntries(); }
        @Override public int getRowWidth() { return 10000; }
        public Optional<AbstractWidget> getHoveredButton(double mouseX, double mouseY) {
            for (ButtonEntry buttonEntry : this.children()) { for (int i = 0; i <= 1; i++) { if (!buttonEntry.buttons.isEmpty() && buttonEntry.buttons.get(i).isMouseOver(mouseX, mouseY)) return Optional.of(buttonEntry.buttons.get(i)); }}
            return Optional.empty();
        }
    }
    public static class ButtonEntry extends ContainerObjectSelectionList.Entry<ButtonEntry> {
        private static final Font textRenderer = Minecraft.getInstance().font;
        public final Component text;
        public final List<AbstractWidget> buttons;
        public final EntryInfo info;
        public boolean centered = false;
        public static final Map<AbstractWidget, Component> buttonsWithText = new HashMap<>();

        public ButtonEntry(List<AbstractWidget> buttons, Component text, EntryInfo info) {
            if (!buttons.isEmpty()) buttonsWithText.put(buttons.get(0),text);
            this.buttons = buttons; this.text = text; this.info = info;
            if (info != null) this.centered = info.centered;
        }
        public void render(PoseStack context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            buttons.forEach(b -> { b.y = y; b.render(context, mouseX, mouseY, tickDelta); });
            if (text != null && (!text.getString().contains("spacer") || !buttons.isEmpty())) { int wrappedY = y;
                for (Iterator<FormattedCharSequence> iterator = textRenderer.split(text, (buttons.size() > 1 ? buttons.get(1).x - 24 : Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24)).iterator(); iterator.hasNext(); wrappedY += 9) {
                    GuiComponent.drawString(context, textRenderer, iterator.next(), (centered) ? (Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - (textRenderer.width(text) / 2)) : 12, wrappedY + 5, 0xFFFFFF);
                }
            }
        }
        public List<? extends GuiEventListener> children() {return Lists.newArrayList(buttons);}
        public List<? extends NarratableEntry> narratables() {return Lists.newArrayList(buttons);}
    }
    public static class SliderWidget extends AbstractSliderButton {
        private final EntryInfo info; private final Entry e;
        public SliderWidget(int x, int y, int width, int height, Component text, double value, EntryInfo info) {
            super(x, y, width, height, text, value);
            this.e = info.field.getAnnotation(Entry.class);
            this.info = info;
        }

        @Override
        protected void updateMessage() { this.setMessage(Component.nullToEmpty(info.tempValue)); }

        @Override
        public void applyValue() {
            if (info.dataType == int.class) info.setValue(((Number) (e.min() + value * (e.max() - e.min()))).intValue());
            else if (info.field.getType() == double.class) info.setValue(Math.round((e.min() + value * (e.max() - e.min())) * (double) e.precision()) / (double) e.precision());
            else if (info.field.getType() == float.class) info.setValue(Math.round((e.min() + value * (e.max() - e.min())) * (float) e.precision()) / (float) e.precision());
        }
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

    private static class SpriteIconButton extends Button {
        private final ResourceLocation iconTexture;
        private final int iconWidth;
        private final int iconHeight;

        public SpriteIconButton(int x, int y, int width, int height, Component message, OnPress onPress, ResourceLocation iconTexture, int iconWidth, int iconHeight) {
            super(x, y, width, height, message, onPress);
            this.iconTexture = iconTexture;
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
        }

        @Override
        public void renderButton(PoseStack context, int mouseX, int mouseY, float delta) {
            super.renderButton(context, mouseX, mouseY, delta);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, iconTexture);
            RenderSystem.enableDepthTest();
            blit(context, this.x + ((this.width - iconWidth) / 2), this.y + ((this.height - iconHeight) / 2), 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
        }
    }

    private static class TextField extends EditBox {
        public TextField(Font font, int x, int y, int width, int height, Component message) {
            super(font, x + 1, y, width - 2, height - 2, message);
        }

        @Override
        public void render(PoseStack context, int mouseX, int mouseY, float delta) {
            this.y = y + 1;
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double d, double e, int i) {
            if (!active) {
                this.setEditable(false);
                return false;
            }
            return super.mouseClicked(d, e, i);
        }
    }

    private static class ItemField extends TextField {
        private final int idMode;
        private boolean dynamic;
        private String item;

        public ItemField(Font font, int x, int y, int width, int height, int idMode) {
            super(font, x, y, width, height, TextComponent.EMPTY);
            this.active = false;
            this.idMode = idMode;
            this.dynamic = true;
        }

        public ItemField(Font font, int x, int y, int width, int height, String item) {
            this(font, x, y, width, height, 0);
            this.dynamic = false;
            this.item = item;
        }

        @Override
        public void renderButton(PoseStack context, int mouseX, int mouseY, float delta) {
            super.renderButton(context, mouseX, mouseY, delta);
            if (item != null) {
                ResourceLocation r = ResourceLocation.tryParse(item);
                if (r != null) {
                    var optStack = (idMode == 0) ? Registry.ITEM.getOptional(r).map(item -> item.getDefaultInstance()) : Registry.BLOCK.getOptional(r).map(block -> block.asItem().getDefaultInstance());
                    optStack.ifPresent(stack -> renderItem(context, stack, this.x + (this.width - 16) / 2, this.y + (this.height - 16) / 2));
                }
            }
        }

        public void setItem(String item) {
            if (this.dynamic) this.item = item;
        }
    }

    @Environment(EnvType.CLIENT)
    private static void renderItem(PoseStack context, ItemStack stack, int i, int j) {
        if (!stack.isEmpty()) {
            var minecraft = Minecraft.getInstance();
            var bufferSource = minecraft.renderBuffers().bufferSource();
            var bakedModel = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
            var pose = RenderSystem.getModelViewStack();

            try {
                pose.pushPose();
                pose.translate(i + 8, j + 8, 150);
                pose.scale(16, -16, 16);
                RenderSystem.applyModelViewMatrix();
                boolean bl = !bakedModel.usesBlockLight();
                if (bl) Lighting.setupForFlatItems();
                minecraft.getItemRenderer().render(stack, ItemTransforms.TransformType.GUI, false, context, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, bakedModel);
                RenderSystem.disableDepthTest();
                bufferSource.endBatch();
                RenderSystem.enableDepthTest();
                if (bl) Lighting.setupFor3DItems();
                pose.popPose();
                RenderSystem.applyModelViewMatrix();
            } catch (Exception ignored) {}
        }
    }
}
