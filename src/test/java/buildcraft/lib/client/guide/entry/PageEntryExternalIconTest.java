/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.entry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;
import buildcraft.lib.gui.GuiStack;
import buildcraft.lib.gui.ISimpleDrawable;

/** An {@code external} guide entry has no object in the game to take an icon from, so its contents-page line used to
 *  be text only. The optional {@code icon} field gives it one: a one-member object whose key names a kind of icon and
 *  whose value is that kind's argument. {@code item} is built in; a subsystem adds kinds with
 *  {@link PageEntryExternal#registerIconKind} (robotics adds {@code board}) so lib needs no knowledge of them. */
class PageEntryExternalIconTest extends VanillaSetupBaseTester {

    private static final Identifier NAME = Identifier.parse("buildcraftunofficial:board/test_entry");

    private static PageEntry<String> entry(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        OptionallyDisabled<PageEntry<String>> entry = PageEntryExternal.INSTANCE.deserialize(NAME, object, null);
        assertTrue(entry.isPresent(), "entry should be accepted, but: " + (entry.isPresent() ? "" : entry.getDisabledReason()));
        return entry.get();
    }

    private static ItemStack drawnStack(PageEntry<String> entry) {
        ISimpleDrawable drawable = entry.createDrawable();
        assertNotNull(drawable, "the entry should draw an icon");
        return assertInstanceOf(GuiStack.class, drawable, "an item icon is drawn as a stack").getStack();
    }

    @Test
    void anEntryWithoutAnIconStillDrawsNothing() {
        PageEntry<String> entry = entry("{\"title_raw\": \"Plain\"}");

        assertNull(entry.createDrawable());
        assertEquals("Plain", entry.title);
    }

    @Test
    void theBuiltInItemKindDrawsThatItem() {
        PageEntry<String> entry = entry("{\"title_raw\": \"With Icon\", \"icon\": {\"item\": \"minecraft:diamond\"}}");

        assertTrue(ItemStack.isSameItem(new ItemStack(Items.DIAMOND), drawnStack(entry)));
        assertEquals("With Icon", entry.title, "the icon does not disturb the title");
    }

    @Test
    void aRegisteredKindReceivesItsArgumentAndItsStackIsDrawn() {
        List<String> asked = new ArrayList<>();
        ItemStack answer = new ItemStack(Items.EMERALD);
        PageEntryExternal.registerIconKind("test_kind_resolver", arg -> {
            asked.add(arg);
            return answer;
        });

        PageEntry<String> entry = entry("{\"title_raw\": \"Custom\", \"icon\": {\"test_kind_resolver\": \"some arg\"}}");

        assertSame(answer, drawnStack(entry));
        assertEquals(List.of("some arg"), asked, "resolved on demand, with the entry's argument");
    }

    @Test
    void anIconThatCannotBeResolvedDrawsNothingAndKeepsTheEntry() {
        PageEntry<String> unknownKind = entry("{\"title_raw\": \"A\", \"icon\": {\"no_such_kind\": \"x\"}}");
        PageEntry<String> unknownItem = entry("{\"title_raw\": \"B\", \"icon\": {\"item\": \"minecraft:no_such_item\"}}");
        PageEntryExternal.registerIconKind("test_kind_empty", arg -> ItemStack.EMPTY);
        PageEntry<String> emptyAnswer = entry("{\"title_raw\": \"C\", \"icon\": {\"test_kind_empty\": \"x\"}}");

        assertNull(unknownKind.createDrawable());
        assertNull(unknownItem.createDrawable());
        assertNull(emptyAnswer.createDrawable());
    }

    @Test
    void aMalformedIconFieldDisablesTheEntryWithAReason() {
        for (String icon : List.of("\"diamond\"", "{}", "{\"item\": 3}", "{\"item\": \"a\", \"board\": \"b\"}", "[]")) {
            JsonObject object = JsonParser.parseString("{\"title_raw\": \"X\", \"icon\": " + icon + "}").getAsJsonObject();

            OptionallyDisabled<PageEntry<String>> entry = PageEntryExternal.INSTANCE.deserialize(NAME, object, null);

            assertFalse(entry.isPresent(), "icon " + icon + " is not a one-member {kind: argument} object");
            assertTrue(entry.getDisabledReason().contains("icon"), entry.getDisabledReason());
        }
    }
}
