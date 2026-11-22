/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && >=1.21.10 {
/*import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.electronwill.nightconfig.core.UnmodifiableConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import net.minecraftforge.common.ForgeConfigSpec;

/^*
 * The config screen MinecraftForge's mod list opens for BuildCraft, built by hand because Forge generates none from a
 * spec (NeoForge has {@code ConfigurationScreen}, which Fabric borrows from Forge Config API Port). It walks every spec
 * {@code ForgeConfigRegistrar} registered: each section is a button opening a screen of its own, and each value is a row
 * of a label and an editor, picked by the value's type (an on/off button for a boolean, a cycling button over the
 * accepted constants for an enum, a text box for a number, a string or a list of strings, written as comma-separated
 * text). A typed value reaches the config only once the spec's own validator accepts it; until then the text turns red.
 *
 * <p>Labels and tooltips use NeoForge's key convention, so the same lang file serves both:
 * {@code <modid>.configuration.<key>} and {@code <modid>.configuration.<key>.tooltip}, falling back to the spec's own
 * translation key, then to the comment written in the spec, then to the bare key.
 *
 * <p>A change is set on the config at once (the game reads its values live) and written to disk when the screen goes
 * away; Forge's config watcher then sends the usual reload event. A spec that is not loaded yet is left out.
 *^/
public final class ForgeConfigScreen extends OptionsSubScreen {
    private static final int COLUMN = 150;
    private static final int ROW = 20;
    private static final int INVALID_TEXT_COLOR = 0xFFFF5555;
    private static final int MAX_TEXT = 4096;

    /^* One section or value of a spec. ^/
    private sealed interface Node permits Section, Value {
    }

    private record Section(ForgeConfigSpec spec, UnmodifiableConfig values, List<String> path) implements Node {
    }

    private record Value(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<?> value, ForgeConfigSpec.ValueSpec valueSpec,
            List<String> path) implements Node {
    }

    private final String modId;
    private final String langPrefix;
    private final List<Node> nodes;
    /^* The specs changed since the last write, shared by every screen of one opening. ^/
    private final Set<ForgeConfigSpec> dirty;

    private ForgeConfigScreen(Screen parent, Component title, String modId, List<Node> nodes, Set<ForgeConfigSpec> dirty) {
        super(parent, Minecraft.getInstance().options, title);
        this.modId = modId;
        this.langPrefix = modId + ".configuration.";
        this.nodes = nodes;
        this.dirty = dirty;
    }

    /^* The top screen: the top-level entries of every loaded spec, in registration order. ^/
    public static Screen root(Screen parent, String modId, List<ForgeConfigSpec> specs) {
        List<Node> nodes = new ArrayList<>();
        for (ForgeConfigSpec spec : specs) {
            if (spec.isLoaded()) {
                collect(spec, spec.getValues(), List.of(), nodes);
            }
        }
        Component title = Component.translatableWithFallback(modId + ".configuration.title", "BuildCraft Configuration");
        return new ForgeConfigScreen(parent, title, modId, nodes, new LinkedHashSet<>());
    }

    private static void collect(ForgeConfigSpec spec, UnmodifiableConfig values, List<String> parentPath, List<Node> out) {
        for (UnmodifiableConfig.Entry entry : values.entrySet()) {
            List<String> path = new ArrayList<>(parentPath);
            path.add(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                Object valueSpec = spec.getSpec().get(path);
                if (valueSpec instanceof ForgeConfigSpec.ValueSpec vs) {
                    out.add(new Value(spec, configValue, vs, List.copyOf(path)));
                }
            } else if (value instanceof UnmodifiableConfig section) {
                out.add(new Section(spec, section, List.copyOf(path)));
            }
        }
    }

    @Override
    protected void addOptions() {
        if (list == null) {
            return;
        }
        // Consecutive sections share rows two to a row; a value takes a row of its own (label, editor).
        List<AbstractWidget> pendingSections = new ArrayList<>();
        for (Node node : nodes) {
            if (node instanceof Section section) {
                pendingSections.add(sectionButton(section));
                continue;
            }
            if (!pendingSections.isEmpty()) {
                list.addSmall(pendingSections);
                pendingSections.clear();
            }
            Value value = (Value) node;
            Component label = valueLabel(value);
            Tooltip tooltip = valueTooltip(value);
            StringWidget labelWidget = new StringWidget(COLUMN, ROW, label, font)
                .setMaxWidth(COLUMN, StringWidget.TextOverflow.SCROLLING);
            AbstractWidget editor = editor(value, label);
            if (tooltip != null) {
                labelWidget.setTooltip(tooltip);
                editor.setTooltip(tooltip);
            }
            list.addSmall(labelWidget, editor);
        }
        if (!pendingSections.isEmpty()) {
            list.addSmall(pendingSections);
        }
    }

    private AbstractWidget sectionButton(Section section) {
        String key = section.path().getLast();
        Component label = translated(section.spec().getLevelTranslationKey(section.path()), key);
        Button button = Button.builder(label, b -> {
            List<Node> children = new ArrayList<>();
            collect(section.spec(), section.values(), section.path(), children);
            show(new ForgeConfigScreen(this, label, modId, children, dirty));
        }).width(COLUMN).build();
        String comment = section.spec().getLevelComment(section.path());
        if (comment != null && !comment.isBlank()) {
            button.setTooltip(Tooltip.create(Component.literal(comment)));
        }
        return button;
    }

    private Component valueLabel(Value value) {
        return translated(value.valueSpec().getTranslationKey(), value.path().getLast());
    }

    /^* The spec's own key if it has a translation, else {@code <modid>.configuration.<key>}, else the bare key. ^/
    private Component translated(String specKey, String key) {
        Language language = Language.getInstance();
        if (specKey != null && language.has(specKey)) {
            return Component.translatable(specKey);
        }
        if (language.has(langPrefix + key)) {
            return Component.translatable(langPrefix + key);
        }
        return Component.literal(key);
    }

    private Tooltip valueTooltip(Value value) {
        String tooltipKey = langPrefix + value.path().getLast() + ".tooltip";
        if (Language.getInstance().has(tooltipKey)) {
            return Tooltip.create(Component.translatable(tooltipKey));
        }
        String comment = value.valueSpec().getComment();
        return comment == null || comment.isBlank() ? null : Tooltip.create(Component.literal(comment));
    }

    private AbstractWidget editor(Value value, Component label) {
        Object current = value.value().get();
        if (current instanceof Boolean flag) {
            return CycleButton.onOffBuilder(flag).displayOnlyValue()
                .create(0, 0, COLUMN, ROW, label, (button, chosen) -> set(value, chosen));
        }
        if (current instanceof Enum<?> constant) {
            return enumButton(value, label, constant);
        }
        if (current instanceof Number number) {
            Function<String, Object> parser = numberParser(number);
            if (parser != null) {
                return textBox(value, label, String.valueOf(number), parser);
            }
        }
        if (current instanceof String text) {
            return textBox(value, label, text, s -> s);
        }
        if (current instanceof List<?> elements) {
            return listBox(value, label, elements);
        }
        // A type this screen has no editor for: shown, not editable (edit the file).
        return new StringWidget(COLUMN, ROW, Component.literal(String.valueOf(current)), font)
            .setMaxWidth(COLUMN, StringWidget.TextOverflow.SCROLLING);
    }

    private <E extends Enum<E>> AbstractWidget enumButton(Value value, Component label, Enum<?> current) {
        @SuppressWarnings("unchecked") // current is an E: it came out of this value
        E start = (E) current;
        List<E> accepted = new ArrayList<>();
        for (E constant : start.getDeclaringClass().getEnumConstants()) {
            if (value.valueSpec().test(constant)) {
                accepted.add(constant);
            }
        }
        if (!accepted.contains(start)) {
            accepted.add(start);
        }
        return CycleButton.<E>builder(constant -> Component.literal(constant.name()), start)
            .withValues(accepted)
            .displayOnlyValue()
            .create(0, 0, COLUMN, ROW, label, (button, chosen) -> set(value, chosen));
    }

    /^* Parses text into the number type the value already holds; a parse failure throws. ^/
    private static Function<String, Object> numberParser(Number current) {
        return switch (current) {
            case Integer i -> s -> Integer.valueOf(s.trim());
            case Long l -> s -> Long.valueOf(s.trim());
            case Double d -> s -> Double.valueOf(s.trim());
            case Float f -> s -> Float.valueOf(s.trim());
            case Short sh -> s -> Short.valueOf(s.trim());
            case Byte b -> s -> Byte.valueOf(s.trim());
            default -> null;
        };
    }

    private EditBox textBox(Value value, Component label, String text, Function<String, Object> parser) {
        EditBox box = new EditBox(font, COLUMN, ROW, label);
        box.setMaxLength(MAX_TEXT);
        box.setValue(text);
        box.setResponder(typed -> {
            Object parsed;
            try {
                parsed = parser.apply(typed);
            } catch (RuntimeException e) {
                parsed = null;
            }
            boolean valid = parsed != null && value.valueSpec().test(parsed);
            box.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : INVALID_TEXT_COLOR);
            if (valid) {
                set(value, parsed);
            }
        });
        return box;
    }

    /^* A list of strings as comma-separated text; read-only when an element is not a string or holds a comma itself. ^/
    private EditBox listBox(Value value, Component label, List<?> elements) {
        boolean editable = true;
        List<String> strings = new ArrayList<>();
        for (Object element : elements) {
            if (!(element instanceof String string) || string.indexOf(',') >= 0) {
                editable = false;
                strings.add(String.valueOf(element));
            } else {
                strings.add(string);
            }
        }
        EditBox box = textBox(value, label, String.join(", ", strings), typed -> Arrays.stream(typed.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList());
        box.setEditable(editable);
        return box;
    }

    @SuppressWarnings("unchecked") // the editor hands back a value of the type this config value holds
    private void set(Value value, Object newValue) {
        ((ForgeConfigSpec.ConfigValue<Object>) value.value()).set(newValue);
        dirty.add(value.spec());
    }

    private void show(Screen screen) {
        //? if >=26.2 {
        minecraft.setScreenAndShow(screen);
        //?} else {
        /^minecraft.setScreen(screen);
        ^///?}
    }

    // Writes what changed. Not super.removed(): that saves the vanilla options, which this screen never touches.
    @Override
    public void removed() {
        for (ForgeConfigSpec spec : dirty) {
            if (spec.isLoaded()) {
                spec.save();
            }
        }
        dirty.clear();
    }
}
*///?}
