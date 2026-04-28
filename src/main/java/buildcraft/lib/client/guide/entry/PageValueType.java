/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.entry;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.util.profiling.ProfilerFiller;

import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.gui.ISimpleDrawable;

/** Abstract base for guide page value types. Subclasses define how to deserialize, display, and match
 * entries of a specific type (item stacks, statements, external pages, etc.). */
public abstract class PageValueType<T> {

    public abstract OptionallyDisabled<PageEntry<T>> deserialize(Identifier name, JsonObject json,
        JsonDeserializationContext ctx);

    public abstract Class<T> getEntryClass();

    public boolean matches(T value, Object test) {
        return Objects.equals(test, value);
    }

    @Nullable
    public abstract ISimpleDrawable createDrawable(T value);

    public Object getBasicValue(T value) {
        return value;
    }

    public abstract String getTitle(T value);

    public abstract List<String> getTooltip(T value);

    public abstract void iterateAllDefault(IEntryLinkConsumer consumer, ProfilerFiller prof);

    /** @param to Something that identifies what this should link to.
     * @return Either success or the error reason. */
    public OptionallyDisabled<Object> createLink(String to, ProfilerFiller prof) {
        return new OptionallyDisabled<>(getClass().getSimpleName() + " doesn't support links");
    }

    @Nullable
    public final PageValue<T> wrap(Object value) {
        T typed = getEntryClass().cast(value);
        if (isValid(typed)) {
            return new PageValue<>(this, typed);
        } else {
            return null;
        }
    }

    protected boolean isValid(T typed) {
        return true;
    }

    /** Override to append type-specific guide parts when an entry of this type is opened.
     *  Used to auto-emit the "Linked From" / "Linked To" chapters from
     *  {@link buildcraft.lib.client.guide.ref.GuideGroupManager} group memberships, and
     *  potentially other type-specific page extensions. The default is a no-op.
     *
     *  <p>Called from {@link buildcraft.lib.client.guide.parts.GuidePage#GuidePage GuidePage}'s
     *  constructor after markdown-defined parts have been added but before the parts list is
     *  frozen, so implementations can both inspect existing parts (e.g. de-duplicate against
     *  manually-declared {@code <group>} tags) and append new ones. */
    public void addPageEntries(T value, GuiGuide gui, List<GuidePart> parts) {}
}
