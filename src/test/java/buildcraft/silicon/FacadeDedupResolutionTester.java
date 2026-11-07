/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Issue #147: the client's visual dedup ({@code FacadeDeduplicator}) narrows the facade list it DISPLAYS, but a
 * facade the server accepted (a dedicated server never dedups) must still resolve to its real block when it reaches
 * the client. Before the fix the dedup replaced the same map the read paths looked states up in, so a double brick
 * slab facade read back as the air default: invisible on the pipe, and named {@code minecraft:air}.
 *
 * <p>The deduplicator itself needs baked block models (client only), so this publishes the same kind of narrowed
 * display snapshot it does and checks that resolution ignores it.
 */
public class FacadeDedupResolutionTester extends VanillaSetupBaseTester {

    private static CompoundTag facadeNbtFor(BlockState state) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("state", NbtUtils.writeBlockState(state));
        return nbt;
    }

    @Test
    public void dedupedStatesStillResolve() {
        FacadeStateManager.ensureInitialized();
        BlockState doubleSlab = Blocks.BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        FacadeBlockStateInfo info = FacadeStateManager.validFacadeStates.get(doubleSlab);
        Assertions.assertNotNull(info, "precondition: a double brick slab is a valid facade state on an un-deduped side");

        try {
            // What the dedup publishes on a client: the display list without the look-alike.
            SortedMap<BlockState, FacadeBlockStateInfo> narrowed = new TreeMap<>(FacadeStateManager.validFacadeStates);
            narrowed.remove(doubleSlab);
            FacadeStateManager.validFacadeStates = Collections.unmodifiableSortedMap(narrowed);

            Assertions.assertSame(doubleSlab, FacadePhasedState.readFromNbt(facadeNbtFor(doubleSlab)).stateInfo.state,
                    "a facade synced as NBT must keep its block after the client deduped it from the display list");

            PacketBufferBC buf = PacketBufferBC.asPacketBufferBc(Unpooled.buffer());
            try {
                info.createPhased(null).writeToBuffer(buf);
                Assertions.assertSame(doubleSlab, FacadePhasedState.readFromBuffer(buf).stateInfo.state,
                        "the buffer path must resolve a deduped state too");
            } finally {
                buf.release();
            }

            BlockState torch = Blocks.TORCH.defaultBlockState();
            Assertions.assertSame(FacadeStateManager.defaultState,
                    FacadePhasedState.readFromNbt(facadeNbtFor(torch)).stateInfo,
                    "a state that was never a valid facade still collapses to the air default");
        } finally {
            FacadeStateManager.resetForTest();
            FacadeStateManager.ensureInitialized();
        }
    }
}
