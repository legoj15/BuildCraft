/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.profiling.ProfilerFiller;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;
import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;

import buildcraft.lib.client.guide.entry.PageEntry;
import buildcraft.lib.client.guide.parts.GuidePageFactory;

public enum MarkdownPageLoader implements IPageLoaderText {
    INSTANCE;

    public static final boolean DEBUG = BCDebugging.shouldDebugLog("lib.markdown");

    public static ItemStack loadComplexItemStack(String line) {
        OptionallyDisabled<ItemStack> stackq = parseItemStack(line);
        if (stackq.isPresent()) {
            return stackq.get();
        }
        BCLog.logger.warn("[lib.guide.loader.markdown] " + stackq.getDisabledReason());
        return ItemStack.EMPTY;
    }

    public static OptionallyDisabled<ItemStack> parseItemStack(String line) {
        String[] args = line.split(",");
        if (args.length == 0) {
            return new OptionallyDisabled<>(line + " was not a valid complex item string!");
        }
        Identifier itemId = Identifier.tryParse(args[0].trim());
        if (itemId == null) {
            return new OptionallyDisabled<>(args[0] + " was not a valid item identifier!");
        }
        Item item = BuiltInRegistries.ITEM.get(itemId)
            .map(ref -> ref.value())
            .orElse(null);
        if (item == null) {
            return new OptionallyDisabled<>(args[0] + " was not a valid item!");
        }
        ItemStack stack = new ItemStack(item);

        if (args.length == 1) {
            return new OptionallyDisabled<>(stack);
        }

        int stackSize;
        try {
            stackSize = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException nfe) {
            return new OptionallyDisabled<>(args[1] + " was not a valid number: " + nfe.getLocalizedMessage());
        }
        stack.setCount(stackSize);

        // Metadata (args[2]) and NBT (args[3]) are legacy 1.12 — ignored in 1.21

        return new OptionallyDisabled<>(stack);
    }

    @Override
    public GuidePageFactory loadPage(BufferedReader reader, Identifier name, PageEntry<?> entry, ProfilerFiller prof)
        throws IOException {
        StringBuilder replaced = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            replaced.append(replaceSpecialForXml(line));
            replaced.append('\n');
        }
        BufferedReader nReader = new BufferedReader(new StringReader(replaced.toString()));
        return XmlPageLoader.INSTANCE.loadPage(nReader, name, entry, prof);
    }

    /**
     * Markdown-style preprocessing applied to each input line before XML parsing.
     * Lines starting with one or more {@code #} characters are converted to a
     * {@code <chapter>} tag (number of {@code #}s minus 1 = level), matching
     * 1.12.2 behavior. Without this, {@code ## Foo} renders literally and never
     * appears in the chapter TOC.
     */
    private static String replaceSpecialForXml(String line) {
        if (line.startsWith("#")) {
            int level = -1;
            while (line.startsWith("#")) {
                line = line.substring(1);
                level++;
            }
            line = line.trim();
            if (level == 0) {
                return "<chapter name=\"" + line + "\"/>";
            } else {
                return "<chapter name=\"" + line + "\" level=\"" + level + "\"/>";
            }
        }
        return line;
    }
}
