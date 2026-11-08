/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import buildcraft.platform.network.PacketContext;

/**
 * {@code oilIsSticky} is COMMON config a remote client never receives, and the client reads it to predict boats and
 * currents. This pins the client side of the sync: a value the server sent beats the client's own file, the client's
 * own file answers again once the world is left, and the server's own send is never contaminated by a synced copy.
 */
public class OilStickySyncTester {

    private boolean originalLocal;

    @AfterEach
    public void restore() {
        BCEnergyConfig.clearSyncedOilIsSticky();
        BCEnergyConfig.oilIsSticky.set(originalLocal);
    }

    private void setLocal(boolean value) {
        originalLocal = BCEnergyConfig.oilIsSticky.get();
        BCEnergyConfig.oilIsSticky.set(value);
    }

    /** The receiving side's main thread is the test thread, so work runs at once; nothing else is used. */
    private static final PacketContext INLINE = new PacketContext() {
        @Override
        public Player player() {
            return null;
        }

        @Override
        public void runOnMain(Runnable work) {
            work.run();
        }

        @Override
        public void reply(CustomPacketPayload payload) {
        }
    };

    @Test
    public void withNothingSyncedTheLocalConfigAnswers() {
        setLocal(true);
        Assertions.assertTrue(BCEnergyConfig.isOilSticky());
        BCEnergyConfig.oilIsSticky.set(false);
        Assertions.assertFalse(BCEnergyConfig.isOilSticky());
    }

    @Test
    public void aSyncedValueBeatsTheLocalConfigInBothDirections() {
        setLocal(false);
        OilStickySync.handle(new OilStickySync(true), INLINE);
        Assertions.assertTrue(BCEnergyConfig.isOilSticky(), "server says sticky, local file says not");

        BCEnergyConfig.oilIsSticky.set(true);
        OilStickySync.handle(new OilStickySync(false), INLINE);
        Assertions.assertFalse(BCEnergyConfig.isOilSticky(), "server says not sticky, local file says sticky");
    }

    @Test
    public void clearingFallsBackToTheLocalConfig() {
        setLocal(false);
        OilStickySync.handle(new OilStickySync(true), INLINE);
        BCEnergyConfig.clearSyncedOilIsSticky();
        Assertions.assertFalse(BCEnergyConfig.isOilSticky(), "left the world: the local file answers again");
    }

    @Test
    public void theLocalValueNeverSeesASyncedCopy() {
        setLocal(false);
        OilStickySync.handle(new OilStickySync(true), INLINE);
        Assertions.assertFalse(BCEnergyConfig.localOilIsSticky(),
                "what a server sends comes from the file, not from an earlier synced copy");
    }
}
