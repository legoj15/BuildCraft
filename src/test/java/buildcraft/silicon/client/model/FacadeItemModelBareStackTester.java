/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.silicon.client.model;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

//? if >=1.21.10 {
import net.minecraft.world.item.ItemDisplayContext;
//?}
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Issue #127: a bare facade stack (no NBT) reaches the item model before {@link FacadeStateManager#init()}
 * has run, on the title screen with no world joined. Moonlight's config-screen item carousel feeds
 * {@code getDefaultInstance()} stacks of every mod item through {@code ItemRenderer.getModel}, and the
 * model used to dereference a null {@link FacadePhasedState#stateInfo}. {@code defaultState} is now a
 * class-load constant, so the bare stack resolves to the air facade before init exactly as after it.
 */
public class FacadeItemModelBareStackTester extends VanillaSetupBaseTester {

    @Test
    public void bareStackResolvesToAirAndRendersNothing() {
        ItemStack bare = new ItemStack(BCSiliconItems.PLUG_FACADE.get());
        FacadePhasedState state = ItemPluggableFacade.getStates(bare).getCurrentStateForStack();
        assertSame(FacadeStateManager.defaultState, state.stateInfo,
            "a bare stack must resolve to the non-null air default state, before or after facade init");

        //? if <1.21.10 {
        /*// 1.21.1: the per-stack ItemOverrides arm (FacadeItemModel.java:157 in the #127 report),
        // resolved with the registry-level model as ItemRenderer does.
        FacadeItemModel registry = new FacadeItemModel();
        registry.getOverrides().resolve(registry, bare, null, null, 0);
        *///?}
        //? if >=1.21.10 {
        // The ItemModel arm, in the GUI context a config-screen carousel uses. The air facade bakes no
        // quads (no block model to draw), so update() must return before touching the client.
        net.minecraft.client.renderer.item.ItemStackRenderState renderState =
            new net.minecraft.client.renderer.item.ItemStackRenderState();
        new FacadeItemModel().update(renderState, bare, null, ItemDisplayContext.GUI, null, null, 0);
        //?}
    }
}
