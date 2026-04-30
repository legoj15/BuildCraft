/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.entry;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.contents.PageLinkFluidStack;
import buildcraft.lib.client.guide.ref.GuideGroupManager;
import buildcraft.lib.gui.GuiFluid;
import buildcraft.lib.gui.ISimpleDrawable;

/** Guide page entry type for fluids. Mirrors {@link PageEntryItemStack} for the
 *  fluid side: lets group-membership data and (optionally) per-fluid markdown pages
 *  use a {@link FluidStackValueFilter} as the page value. */
public class PageEntryFluidStack extends PageValueType<FluidStackValueFilter> {

    public static final PageEntryFluidStack INSTANCE = new PageEntryFluidStack();

    @Override
    public Class<FluidStackValueFilter> getEntryClass() {
        return FluidStackValueFilter.class;
    }

    @Override
    protected boolean isValid(FluidStackValueFilter typed) {
        return !typed.stack.isEmpty();
    }

    /** Tag used to file vanilla / non-guide fluids under a search-only chapter,
     *  mirroring {@code PageEntryItemStack#OTHER_ITEMS_TAGS}. The chapter is invisible
     *  by default (every emitted link uses startVisible=false) and surfaces as
     *  "Other Fluids" only while a search query matches at least one entry. */
    private static final JsonTypeTags OTHER_FLUIDS_TAGS =
        new JsonTypeTags("buildcraft.guide.contents.other_fluids");

    @Override
    public void iterateAllDefault(IEntryLinkConsumer consumer, ProfilerFiller prof) {
        prof.push("iterate_all_fluids");
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY) continue;
            FluidStack stack = new FluidStack(fluid, 1);
            if (stack.isEmpty()) continue;
            // Dedup against guide-defined fluid entries (refinery fuels, coolants).
            if (!GuideManager.INSTANCE.objectsAdded.add(fluid)) continue;
            String displayName = fluid.getFluidType().getDescription(stack).getString();
            if (displayName == null || displayName.trim().isEmpty()) continue;
            consumer.addChild(OTHER_FLUIDS_TAGS, PageLinkFluidStack.create(false, stack, prof));
        }
        prof.pop();
    }

    @Override
    public OptionallyDisabled<PageEntry<FluidStackValueFilter>> deserialize(Identifier name, JsonObject json,
        JsonDeserializationContext ctx) {
        if (!json.has("fluid")) {
            throw new JsonSyntaxException("Expected a 'fluid' string for fluid_stack entry " + json);
        }
        String str = json.get("fluid").getAsString().trim();
        Identifier loc = Identifier.parse(str);
        Fluid fluid = BuiltInRegistries.FLUID.get(loc).map(ref -> ref.value()).orElse(null);
        if (fluid == null) {
            return new OptionallyDisabled<>("Unknown fluid '" + str + "'");
        }
        FluidStackValueFilter filter = new FluidStackValueFilter(new FluidStack(fluid, 1));
        return new OptionallyDisabled<>(new PageEntry<>(this, name, json, filter));
    }

    @Override
    public String getTitle(FluidStackValueFilter value) {
        FluidStack stack = value.stack;
        return stack.getFluid().getFluidType().getDescription(stack).getString();
    }

    @Override
    public List<String> getTooltip(FluidStackValueFilter value) {
        return Collections.singletonList(getTitle(value));
    }

    @Override
    public boolean matches(FluidStackValueFilter entry, Object obj) {
        Fluid target = null;
        if (obj instanceof FluidStackValueFilter f) {
            target = f.stack.getFluid();
        } else if (obj instanceof FluidStack fs) {
            target = fs.getFluid();
        } else if (obj instanceof Fluid f) {
            target = f;
        }
        return target != null && entry.stack.getFluid() == target;
    }

    @Override
    @Nullable
    public ISimpleDrawable createDrawable(FluidStackValueFilter value) {
        return new GuiFluid(value.stack);
    }

    @Override
    public Object getBasicValue(FluidStackValueFilter value) {
        return value.stack.getFluid();
    }

    @Override
    public void addPageEntries(FluidStackValueFilter value, GuiGuide gui, List<GuidePart> parts) {
        GuideGroupManager.appendLinkedChapters(INSTANCE.wrap(value), gui, parts);
    }
}
