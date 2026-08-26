/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.core.BCUnifiedConfig;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Covers issue #29 — the {@code general.disableFacadeGeneration} config toggle must kill ALL automatic
 * facade derivation, not merely hide it: with the toggle set, no block-registry enumeration scan runs
 * (the derivation maps stay empty), no facade item can be derived for any block, and yet worlds that
 * already contain facades stay safe — the facade item and plug remain registered, and a saved facade
 * keeps its block identity on read-back instead of collapsing to the air default (which would be
 * persisted on the next chunk save and permanently destroy it).
 *
 * <p>Single-method by design: the whole check manipulates JVM-global state (the config value and
 * {@link FacadeStateManager}'s once-per-JVM snapshots), so it runs as one linear sequence with a
 * {@code finally} that restores both — the same restore discipline the game-test suite uses for its
 * config flips, protecting anything else in this JVM that assumes facades are populated.
 */
public class FacadeGenerationToggleTester extends VanillaSetupBaseTester {

    /** Builds the NBT a saved facade carries under {@code "state"} for the given block state. */
    private static CompoundTag facadeNbtFor(BlockState state) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("state", NbtUtils.writeBlockState(state));
        return nbt;
    }

    @Test
    public void toggleKillsDerivationAndPreservesSavedFacades() {
        // The config machinery itself must be live in the FML-JUnit JVM — JUnitMain boots
        // ServerModLoader, which loads the COMMON spec before any test runs. If this ever fails,
        // the environment regressed and every config-reading code path deserves re-auditing.
        Assertions.assertTrue(BCUnifiedConfig.SPEC.isLoaded(),
                "the COMMON config must be loaded in the FML-JUnit environment");

        // Baseline: with generation enabled (the default) the enumeration must have run.
        BlockState bricks = Blocks.BRICKS.defaultBlockState();
        FacadeStateManager.ensureInitialized();
        Assertions.assertNotNull(FacadeStateManager.validFacadeStates.get(bricks),
                "precondition: BRICKS must be a valid facade state while generation is enabled");

        boolean previous = BCSiliconConfig.disableFacadeGeneration.get();
        try {
            BCSiliconConfig.disableFacadeGeneration.set(true);
            FacadeStateManager.resetForTest();
            FacadeStateManager.ensureInitialized();

            // Derivation is fully suppressed...
            Assertions.assertTrue(FacadeStateManager.isFacadeGenerationDisabled(),
                    "the helper must reflect the config toggle");
            Assertions.assertTrue(FacadeStateManager.validFacadeStates.isEmpty(),
                    "no enumeration scan may run while generation is disabled");
            Assertions.assertTrue(FacadeStateManager.stackFacades.isEmpty(),
                    "no stack-to-facade index may be built while generation is disabled");
            Assertions.assertNotNull(FacadeStateManager.defaultState,
                    "defaultState must stay assigned while disabled — facade read paths dereference it");
            Assertions.assertSame(FacadeStateManager.defaultState, FacadeStateManager.previewState,
                    "previewState falls back to the air default while disabled");

            ItemStack derived = BCSiliconItems.PLUG_FACADE.get().getFacadeForBlock(bricks);
            Assertions.assertTrue(derived.isEmpty(),
                    "no facade item may be derived for any block while generation is disabled");

            // ...but saved facades keep their identity (issue #29 is "off", not "destroy my world").
            FacadePhasedState read = FacadePhasedState.readFromNbt(facadeNbtFor(bricks));
            Assertions.assertSame(bricks, read.stateInfo.state,
                    "a saved facade must keep its block identity while generation is disabled");
            Assertions.assertFalse(read.stateInfo.requiredStack.isEmpty(),
                    "the fallback info keeps a real required stack so naming/tooltips stay correct");
            Assertions.assertSame(bricks,
                    FacadePhasedState.readFromNbt(read.writeToNbt()).stateInfo.state,
                    "NBT round-trip must not degrade a saved facade to air (chunk re-save would destroy it)");
            PacketBufferBC buf = PacketBufferBC.asPacketBufferBc(Unpooled.buffer());
            read.writeToBuffer(buf);
            Assertions.assertSame(bricks, FacadePhasedState.readFromBuffer(buf).stateInfo.state,
                    "the client-sync buffer path must keep a saved facade's identity too");
            buf.release();

            // Mid-session flips must stay inert until re-init: the fallback follows the INIT-TIME
            // latch, never the live config. A live read here meant that un-ticking the option in the
            // config screen mid-session (no restart prompt for COMMON edits) made every chunk
            // re-load collapse placed facades to air — and the next chunk save destroyed them.
            BCSiliconConfig.disableFacadeGeneration.set(false);
            Assertions.assertSame(bricks, FacadePhasedState.readFromNbt(facadeNbtFor(bricks)).stateInfo.state,
                    "a mid-session config flip must not change the fallback until the next re-init");
            BCSiliconConfig.disableFacadeGeneration.set(true);
        } finally {
            BCSiliconConfig.disableFacadeGeneration.set(previous);
            FacadeStateManager.resetForTest();
            FacadeStateManager.ensureInitialized();
        }

        // Post-restore: the enumeration is back (for this JVM's other facade-touching code), and the
        // ENABLED-mode behavior is unchanged — a state that is not a valid facade still collapses to
        // the air default, exactly as before the toggle existed.
        Assertions.assertFalse(FacadeStateManager.isFacadeGenerationDisabled(),
                "restoring the config must restore the enabled helper state");
        Assertions.assertNotNull(FacadeStateManager.validFacadeStates.get(bricks),
                "re-enabling must bring the enumeration back");

        BlockState torch = Blocks.TORCH.defaultBlockState();
        Assertions.assertNull(FacadeStateManager.validFacadeStates.get(torch),
                "precondition: torch is not a valid facade state");
        Assertions.assertSame(FacadeStateManager.defaultState,
                FacadePhasedState.readFromNbt(facadeNbtFor(torch)).stateInfo,
                "enabled mode: invalid states still collapse to the air default (existing behavior)");
    }
}
