/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Regression guard for the multiplayer-join disconnect where naming a bare facade item NPEd.
 *
 * <p>On a multiplayer client {@link FacadeStateManager#init()} is deferred to
 * {@code ClientPlayerNetworkEvent.LoggingIn} (no integrated server runs it earlier via
 * {@code ServerAboutToStartEvent}). The guide book's "index every registered item" warm fires on the
 * same event and, if it runs first, calls {@code getHoverName()} on a bare facade while
 * {@link FacadeStateManager#defaultState} is still null — which used to dereference a null
 * {@link FacadePhasedState#stateInfo} and kill the login packet handler, disconnecting the player.
 *
 * <p>The ordering is now pinned (the facade-init login listener runs at HIGH priority), but naming is
 * also null-guarded as a backstop. This test pins the backstop directly: force
 * {@code defaultState = null} to recreate the pre-init window and assert that naming a bare facade
 * neither throws nor yields an empty name. The {@code static} mutation is bracketed in try/finally so
 * it doesn't leak into other game tests.
 */
public final class FacadeNamingTester {

    private FacadeNamingTester() {}

    public static void testBareFacadeNameSurvivesNullDefaultState(GameTestHelper helper) {
        FacadeStateManager.ensureInitialized();
        FacadeBlockStateInfo priorDefault = FacadeStateManager.defaultState;
        try {
            // Recreate the multiplayer pre-init window: defaultState not yet populated on the client.
            FacadeStateManager.defaultState = null;

            // getHoverName -> ItemPluggableFacade.getName -> getFacadeStateDisplayName. getStates on a
            // bare stack resolves to createSingle(defaultState=null) -> phasedStates[0].stateInfo=null.
            // Before the fix this NPEd; now it must fall back to the bare item name.
            ItemStack bareFacade = new ItemStack(BCSiliconItems.PLUG_FACADE.get());
            Component name;
            try {
                name = bareFacade.getHoverName();
            } catch (Exception e) {
                helper.fail("Naming a bare facade with defaultState=null threw " + e
                    + " — the login-disconnect regression is back.");
                return;
            }
            if (name == null || name.getString().isBlank()) {
                helper.fail("Bare facade name was empty/null with defaultState=null.");
                return;
            }

            // The static helper must be null-safe directly too (appendHoverText calls it as well).
            String suffix = ItemPluggableFacade.getFacadeStateDisplayName(new FacadePhasedState(null, null));
            if (!suffix.isEmpty()) {
                helper.fail("getFacadeStateDisplayName(null stateInfo) returned \"" + suffix
                    + "\" instead of an empty string.");
                return;
            }
            helper.succeed();
        } finally {
            FacadeStateManager.defaultState = priorDefault;
        }
    }
}
