package com.jahirtrap.configlib;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.*;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static net.minecraft.client.Minecraft.ON_OSX;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("unchecked")
public class TXFConfigClient extends TXFConfig {
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
        Tab tab;

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

    public static void initClient(String modid, Field field, EntryInit init) {
        Entry e = field.getAnnotation(Entry.class);
        EntryInfo info = new EntryInfo();
        info.dataType = getUnderlyingType(field);
        info.width = e != null ? e.width() : 0;
        info.field = field; info.modid = modid;
        info.centered = init.centered; info.defaultValue = init.defaultValue;
        if (info.dataType == List.class) {
            Class<?> listType = (Class<?>) ((ParameterizedType) info.field.getGenericType()).getActualTypeArguments()[0];
            try { info.dataType = (Class<?>) listType.getField("TYPE").get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) { info.dataType = listType; }
        }

        if (e != null) {
            if (!e.name().isEmpty()) info.name = Component.translatable(e.name());
            if (info.dataType == int.class) textField(info, Integer::parseInt, INTEGER_ONLY, (int) e.min(), (int) e.max(), true);
            else if (info.dataType == float.class) textField(info, Float::parseFloat, DECIMAL_ONLY, (float) e.min(), (float) e.max(), false);
            else if (info.dataType == double.class) textField(info, Double::parseDouble, DECIMAL_ONLY, e.min(), e.max(), false);
            else if (info.dataType == String.class || info.dataType == List.class) textField(info, String::length, null, Math.min(e.min(), 0), Math.max(e.max(), 1), true);
            else if (info.dataType == boolean.class) {
                Function<Object, Component> func = value -> Component.translatable((Boolean) value ? "gui.yes" : "gui.no").withStyle((Boolean) value ? ChatFormatting.GREEN : ChatFormatting.RED);
                info.function = new AbstractMap.SimpleEntry<Button.OnPress, Function<Object, Component>>(button -> {
                    info.setValue(!(Boolean) info.value); button.setMessage(func.apply(info.value));
                }, func);
            } else if (info.dataType.isEnum()) {
                List<?> values = Arrays.asList(field.getType().getEnumConstants());
                Function<Object, Component> func = value -> Component.translatable(modid + ".config." + "enum." + info.dataType.getSimpleName() + "." + info.value.toString());
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
    public static Tooltip getTooltip(EntryInfo info) {
        String key = info.modid + ".config."+info.field.getName()+".tooltip";
        return Tooltip.create(info.error != null ? info.error : I18n.exists(key) ? Component.translatable(key) : Component.empty());
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
                info.error = inLimits? null : Component.literal(value.doubleValue() < min ?
                        "§cMinimum " + (isNumber? "value" : "length") + (cast? " is " + (int)min : " is " + min) :
                        "§cMaximum " + (isNumber? "value" : "length") + (cast? " is " + (int)max : " is " + max)).withStyle(ChatFormatting.RED);
                t.setTooltip(getTooltip(info));
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
                try { info.actionButton.setMessage(Component.literal("⬛").setStyle(Style.EMPTY.withColor(Color.decode(info.tempValue).getRGB())));
                } catch (Exception ignored) {}
            }
            return true;
        };
    }
    @OnlyIn(Dist.CLIENT)
    public static Screen getScreen(Screen parent, String modid) {
        return new ConfigScreen(parent, modid);
    }
    @OnlyIn(Dist.CLIENT)
    public static class ConfigScreen extends Screen {
        protected ConfigScreen(Screen parent, String modid) {
            super(Component.translatable(modid + ".config." + "title"));
            this.parent = parent; this.modid = modid;
            this.translationPrefix = modid + ".config.";
            loadValues();

            for (EntryInfo e : entries) {
                if (e.modid.equals(modid)) {
                    String tabId = e.field.isAnnotationPresent(Entry.class) ? e.field.getAnnotation(Entry.class).category() : e.field.getAnnotation(Comment.class).category();
                    String name = translationPrefix + "category." + tabId;
                    if (!I18n.exists(name) && tabId.equals("default"))
                        name = translationPrefix + "title";
                    if (!tabs.containsKey(name)) {
                        Tab tab = new GridLayoutTab(Component.translatable(name));
                        e.tab = tab;
                        tabs.put(name, tab);
                    } else e.tab = tabs.get(name);
                }
            }
            tabNavigation = TabNavigationBar.builder(tabManager, this.width).addTabs(tabs.values().toArray(new Tab[0])).build();
            tabNavigation.selectTab(0, false);
            tabNavigation.arrangeElements();
            prevTab = tabManager.getCurrentTab();
        }
        public final String translationPrefix, modid;
        public final Screen parent;
        public ConfigListWidget list;
        public TabManager tabManager = new TabManager(a -> {}, a -> {});
        public Map<String, Tab> tabs = new HashMap<>();
        public Tab prevTab;
        public TabNavigationBar tabNavigation;
        public Button done;
        public double scrollProgress = 0d;

        // Real Time config update //
        @Override
        public void tick() {
            super.tick();
            if (prevTab != null && prevTab != tabManager.getCurrentTab()) {
                prevTab = tabManager.getCurrentTab();
                this.list.clear(); fillList();
                list.setScrollAmount(0);
            }
            scrollProgress = list.getScrollAmount();
            for (EntryInfo info : entries) try {info.field.set(null, info.value);} catch (IllegalAccessException ignored) {}
            updateButtons();
        }
        public void updateButtons() {
            if (this.list != null) {
                for (ButtonEntry entry : this.list.children()) {
                    if (entry.buttons != null && entry.buttons.size() > 1) {
                        if (entry.buttons.getFirst() instanceof AbstractWidget widget)
                            if (widget.isFocused() || widget.isHovered()) widget.setTooltip(getTooltip(entry.info));
                        if (entry.buttons.get(1) instanceof Button button)
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
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.tabNavigation.keyPressed(keyCode)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        @Override
        public void onClose() {
            loadValues(); cleanup();
            Objects.requireNonNull(minecraft).setScreen(parent);
        }
        private void cleanup() {
            entries.forEach(info -> {
                info.error = null; info.value = null; info.tempValue = null; info.actionButton = null; info.listIndex = 0; info.tab = null; info.inLimits = true;
            });
        }
        @Override
        public void init() {
            super.init();
            tabNavigation.setWidth(this.width); tabNavigation.arrangeElements();
            if (!tabs.isEmpty()) this.addRenderableWidget(tabNavigation);

            this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).bounds(this.width / 2 - 154, this.height - 26, 150, 20).build());
            done = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
                for (EntryInfo info : entries) if (info.modid.equals(modid)) try { info.field.set(null, info.value); } catch (IllegalAccessException ignored) {}
                write(modid); cleanup();
                Objects.requireNonNull(minecraft).setScreen(parent);
            }).bounds(this.width / 2 + 4, this.height - 26, 150, 20).build());

            this.list = new ConfigListWidget(this.minecraft, this.width, this.height - 66, 33, 25);
            this.addWidget(this.list); fillList();
        }
        public void fillList() {
            for (EntryInfo info : entries) {
                if (info.modid.equals(modid) && (info.tab == null || info.tab == tabManager.getCurrentTab())) {
                    Component name = Objects.requireNonNullElseGet(info.name, () -> Component.translatable(translationPrefix + info.field.getName()));
                    Button resetButton = SpriteIconButton.builder(Component.translatable("controls.reset"), (button -> {
                        info.value = info.defaultValue; info.listIndex = 0;
                        info.tempValue = info.toTemporaryValue();
                        list.clear(); fillList();
                    }), true).sprite(ResourceLocation.fromNamespaceAndPath("configlibtxf","icon/reset"), 12, 12).size(20, 20).build();
                    resetButton.setPosition(width - 205 + 150 + 25, 0);

                    if (info.function != null) {
                        AbstractWidget widget;
                        Entry e = info.field.getAnnotation(Entry.class);

                        if (info.function instanceof Map.Entry) { // Enums & booleans
                            var values = (Map.Entry<Button.OnPress, Function<Object, Component>>) info.function;
                            if (info.dataType.isEnum())
                                values.setValue(value -> Component.translatable(translationPrefix + "enum." + info.field.getType().getSimpleName() + "." + info.value.toString()));
                            widget = Button.builder(values.getValue().apply(info.value), values.getKey()).bounds(width - 185, 0, 150, 20).tooltip(getTooltip(info)).build();
                        }
                        else if (e.isSlider())
                            widget = new SliderWidget(width - 185, 0, 150, 20, Component.literal(info.tempValue), (Double.parseDouble(info.tempValue) - e.min()) / (e.max() - e.min()), info);
                        else widget = new EditBox(font, width - 185, 0, 150, 20, Component.empty());

                        if (widget instanceof EditBox textField) {
                            textField.setMaxLength(info.width); textField.setValue(info.tempValue);
                            Predicate<String> processor = ((BiFunction<EditBox, Button, Predicate<String>>) info.function).apply(textField, done);
                            textField.setFilter(processor);
                        }
                        widget.setTooltip(getTooltip(info));

                        Button cycleButton = null;
                        if (info.field.getType() == List.class) {
                            cycleButton = Button.builder(Component.literal(String.valueOf(info.listIndex)).withStyle(ChatFormatting.GOLD), (button -> {
                                var values = (List<?>) info.value;
                                values.remove("");
                                info.listIndex = info.listIndex + 1;
                                if (info.listIndex > values.size()) info.listIndex = 0;
                                info.tempValue = info.toTemporaryValue();
                                if (info.listIndex == values.size()) info.tempValue = "";
                                list.clear(); fillList();
                            })).bounds(width - 185, 0, 20, 20).build();
                        }
                        if (e.isColor()) {
                            Button colorButton = Button.builder(Component.literal("⬛"),
                                    button -> new Thread(() -> {
                                        Color newColor = JColorChooser.showDialog(null, null, Color.decode(!Objects.equals(info.tempValue, "") ? info.tempValue : "#FFFFFF"));
                                        if (newColor != null) {
                                            info.setValue("#" + Integer.toHexString(newColor.getRGB()).substring(2));
                                            list.clear(); fillList();
                                        }
                                    }).start()
                            ).bounds(width - 185, 0, 20, 20).build();
                            try { colorButton.setMessage(Component.literal("⬛").setStyle(Style.EMPTY.withColor(Color.decode(info.tempValue).getRGB())));
                            } catch (Exception ignored) {}
                            info.actionButton = colorButton;
                        } else if (e.idMode() > -1) {
                            EditBox itemField = new EditBox(font, width - 185, 0, 20, 20, Component.empty());
                            itemField.active = false;
                            info.actionButton = itemField;
                        } else if (e.selectionMode() > -1) {
                            Button explorerButton = SpriteIconButton.builder(Component.empty(),
                                    button -> new Thread(() -> {
                                        JFileChooser fileChooser = new JFileChooser(info.tempValue);
                                        fileChooser.setFileSelectionMode(e.selectionMode()); fileChooser.setDialogType(e.fileChooserType());
                                        if ((e.selectionMode() == JFileChooser.FILES_ONLY || e.selectionMode() == JFileChooser.FILES_AND_DIRECTORIES) && Arrays.stream(e.fileExtensions()).noneMatch("*"::equals))
                                            fileChooser.setFileFilter(new FileNameExtensionFilter(
                                                    Component.translatable(translationPrefix + info.field.getName() + ".fileFilter").getString(), e.fileExtensions()));
                                        if (fileChooser.showDialog(null, null) == JFileChooser.APPROVE_OPTION) {
                                            info.setValue(fileChooser.getSelectedFile().getAbsolutePath());
                                            list.clear(); fillList();
                                        }
                                    }).start(), true
                            ).sprite(ResourceLocation.fromNamespaceAndPath("configlibtxf", "icon/explorer"), 12, 12).size(20, 20).build();
                            explorerButton.setPosition(width - 185, 0);
                            info.actionButton = explorerButton;
                        }
                        List<AbstractWidget> widgets = Lists.newArrayList(widget, resetButton);
                        if (info.actionButton != null) {
                            if (ON_OSX) info.actionButton.active = false;
                            widget.setWidth(widget.getWidth() - 22); widget.setX(widget.getX() + 22);
                            widgets.add(info.actionButton);
                        } if (cycleButton != null) {
                            if (info.actionButton != null) info.actionButton.setX(info.actionButton.getX() + 22);
                            widget.setWidth(widget.getWidth() - 22); widget.setX(widget.getX() + 22);
                            widgets.add(cycleButton);
                        }
                        this.list.addButton(widgets, name, info);
                    } else this.list.addButton(List.of(), name, info);
                } list.setScrollAmount(scrollProgress);
                updateButtons();
            }
        }
        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.render(context,mouseX,mouseY,delta);
            renderMenuBackgroundTexture(context, MENU_BACKGROUND, 0, 24, 0, 0, this.width, 7);
            this.list.render(context, mouseX, mouseY, delta);

            if (this.list != null) {
                for (ButtonEntry entry : this.list.children()) {
                    if (entry.buttons != null && entry.buttons.size() > 2) {
                        if (entry.buttons.get(2) instanceof AbstractWidget widget) {
                            int idMode = entry.info.field.getAnnotation(Entry.class).idMode();
                            if (idMode != -1) { ResourceLocation r = ResourceLocation.tryParse(entry.info.tempValue);
                                if (r != null) if (idMode == 0) BuiltInRegistries.ITEM.get(r).ifPresent(item -> context.renderItem(item.value().getDefaultInstance(), widget.getX() + 2, widget.getY() + 2)); else BuiltInRegistries.BLOCK.get(r).ifPresent(block -> context.renderItem(block.value().asItem().getDefaultInstance(), widget.getX() + 2, widget.getY() + 2));
                            }}}}}
        }
    }
    @OnlyIn(Dist.CLIENT)
    public static class ConfigListWidget extends ContainerObjectSelectionList<ButtonEntry> {
        public ConfigListWidget(Minecraft client, int width, int height, int y, int itemHeight) { super(client, width, height, y, itemHeight); }
        @Override public int getScrollbarPosition() { return this.width - 7; }

        public void addButton(List<AbstractWidget> buttons, Component text, EntryInfo info) { this.addEntry(new ButtonEntry(buttons, text, info)); }
        public void clear() { this.clearEntries(); }
        @Override public int getRowWidth() { return 10000; }
    }
    public static class ButtonEntry extends ContainerObjectSelectionList.Entry<ButtonEntry> {
        private static final Font textRenderer = Minecraft.getInstance().font;
        public final Component text;
        public final List<AbstractWidget> buttons;
        public final EntryInfo info;
        public boolean centered = false;

        public ButtonEntry(List<AbstractWidget> buttons, Component text, EntryInfo info) {
            this.buttons = buttons; this.text = text; this.info = info;
            if (info != null) this.centered = info.centered;
        }
        public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            buttons.forEach(b -> { b.setY(y); b.render(context, mouseX, mouseY, tickDelta); });
            if (text != null && (!text.getString().contains("spacer") || !buttons.isEmpty())) { int wrappedY = y;
                for (Iterator<FormattedCharSequence> iterator = textRenderer.split(text, (buttons.size() > 1 ? buttons.get(1).getX() - 24 : Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24)).iterator(); iterator.hasNext(); wrappedY += 9) {
                    context.drawString(textRenderer, iterator.next(), (centered) ? (Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - (textRenderer.width(text) / 2)) : 12, wrappedY + 5, 0xFFFFFF);
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
}