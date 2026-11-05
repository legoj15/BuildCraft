/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.silicon.client.model;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

//? if >=1.21.10 {
import net.minecraft.world.item.ItemDisplayContext;
//?}
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * The crash window of issue #127: a bare facade stack (no NBT) resolved by the item model before
 * {@link FacadeStateManager#init()} has ever run. {@code defaultState} is only assigned at
 * {@code ServerAboutToStart} / {@code LoggingIn}, so on the title screen — no world joined — the
 * stack's {@link FacadePhasedState#stateInfo} is null and the model arms must render nothing rather
 * than dereference it. Found in the wild via Moonlight's config-screen item carousel, which feeds
 * {@code getDefaultInstance()} stacks of every mod item through {@code ItemRenderer.getModel}.
 */
public class FacadeItemModelBareStackTester extends VanillaSetupBaseTester {

    @Test
    public void bareStackRendersNothingBeforeFacadeStateInit() {
        // Force the pre-init window deterministically: whatever earlier tests did to the static
        // facade state, this is the exact state a fresh client's title screen sees.
        FacadeBlockStateInfo savedDefault = FacadeStateManager.defaultState;
        FacadeStateManager.defaultState = null;
        try {
            ItemStack bare = new ItemStack(BCSiliconItems.PLUG_FACADE.get());
            FacadePhasedState state = ItemPluggableFacade.getStates(bare).getCurrentStateForStack();
            assertNull(state.stateInfo, "precondition: a bare stack carries no facade state pre-init");

            //? if <1.21.10 {
            /*// 1.21.1: the per-stack ItemOverrides arm (FacadeItemModel.java:157 in the #127 report).
            // Resolve with the registry-level model as ItemRenderer does; it used to NPE here.
            FacadeItemModel registry = new FacadeItemModel();
            registry.getOverrides().resolve(registry, bare, null, null, 0);
            *///?}
            //? if >=1.21.10 {
            // The ItemModel arm shares the same unguarded stateInfo dereference; GUI context is
            // what a config-screen carousel uses.
            new FacadeItemModel().update(new net.minecraft.client.renderer.item.ItemStackRenderState(),
                bare, null, ItemDisplayContext.GUI, null, null, 0);
            //?}
            // Reaching here without a NullPointerException is the whole assertion.
        } finally {
            FacadeStateManager.defaultState = savedDefault;
        }
    }

    /** Positive control: once facade state init has run, the same bare stack resolves to the air
     *  fallback instead of null — the guards above must never fire for a normal client again. */
    @Test
    public void bareStackResolvesToAirAfterFacadeStateInit() {
        FacadeStateManager.ensureInitialized();
        ItemStack bare = new ItemStack(BCSiliconItems.PLUG_FACADE.get());
        assertNotNull(ItemPluggableFacade.getStates(bare).getCurrentStateForStack().stateInfo,
            "post-init a bare stack must resolve to the air default state, not null");
    }
}
