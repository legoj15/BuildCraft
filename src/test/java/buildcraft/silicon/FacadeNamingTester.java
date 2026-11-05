/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Regression guard for the multiplayer-join disconnect where naming a bare facade item NPEd.
 *
 * <p>On a multiplayer client {@link FacadeStateManager#init()} is deferred to
 * {@code ClientPlayerNetworkEvent.LoggingIn} (no integrated server runs it earlier via
 * {@code ServerAboutToStartEvent}). The guide book's "index every registered item" warm fires on the
 * same event and, if it ran first, called {@code getHoverName()} on a bare facade while
 * {@link FacadeStateManager#defaultState} was still null — which dereferenced a null
 * {@link FacadePhasedState#stateInfo} and killed the login packet handler, disconnecting the player.
 *
 * <p>The ordering is now pinned (the facade-init login listener runs at HIGH priority), and
 * {@code defaultState} is a class-load constant (issue #127), so a bare facade is named before init the
 * same as after it. This pins the naming fallback: a bare facade's name is never empty, and the display
 * helper stays null-safe for a hand-built state with no info.
 */
public final class FacadeNamingTester {

    private FacadeNamingTester() {}

    public static void testBareFacadeNameUsesAirDefault(GameTestHelper helper) {
        // getHoverName -> ItemPluggableFacade.getName -> getFacadeStateDisplayName. getStates on a
        // bare stack resolves to createSingle(defaultState), the air facade, which must name as the bare item.
        ItemStack bareFacade = new ItemStack(BCSiliconItems.PLUG_FACADE.get());
        Component name;
        try {
            name = bareFacade.getHoverName();
        } catch (Exception e) {
            helper.fail("Naming a bare facade before facade init threw " + e
                + " — the login-disconnect regression is back.");
            return;
        }
        if (name == null || name.getString().isBlank()) {
            helper.fail("Bare facade name was empty/null before facade init.");
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
    }
}
