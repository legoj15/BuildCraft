/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render.pip;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.transport.client.model.key.PipeModelKey;

/**
 * Pins the offline pipe-model reconstruction behind the blueprint 3D preview ({@link PipePreviewModel}).
 * <p>
 * Every pipe shares one {@code pipe_holder} block, so the preview cannot use {@code new ItemStack(block)}
 * — that resolves every pipe to whichever {@code ItemPipeHolder} last won the block→item map (the Wooden
 * Diamond FE Pipe) and draws its connectionless vertical item model. Instead the preview rebuilds each
 * pipe's real model from the captured {@code "pipe"} NBT. These tests run on a dedicated server because
 * the model <i>key</i> only needs the pipe registry, not a GL context (only the eventual quad generation
 * does). They verify that:
 * <ol>
 *   <li>every registered pipe definition reconstructs to a non-null key carrying that same definition —
 *       which also proves offline {@link buildcraft.transport.pipe.Pipe} construction never needs a world
 *       (the stub holder returns {@code null} for {@code getPipeWorld()}); a type that did would surface
 *       here as a null key;</li>
 *   <li>the packed {@code "con"} connection bits decode into the model key's per-side distances;</li>
 *   <li>missing / unknown-definition NBT degrades to {@code null} (caller falls back) instead of throwing.</li>
 * </ol>
 */
public class PipePreviewModelTester {

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new IllegalStateException(msg);
        }
    }

    /** Builds the tile-NBT shape {@code TilePipeHolder.saveAdditional} produces: pipe data under "pipe". */
    private static CompoundTag tileNbtFor(String def, int con) {
        CompoundTag pipe = new CompoundTag();
        pipe.putString("def", def);
        pipe.putInt("con", con);
        CompoundTag tile = new CompoundTag();
        tile.put("pipe", pipe);
        return tile;
    }

    /** (1) Every registered pipe definition reconstructs to a key carrying that same definition. */
    public static void testAllPipeDefinitionsReconstruct(GameTestHelper helper) {
        int count = 0;
        for (PipeDefinition def : PipeApi.pipeRegistry.getAllRegisteredPipes()) {
            PipeModelKey key = PipePreviewModel.modelKey(tileNbtFor(def.identifier, 0));
            check(key != null, "modelKey was null for pipe definition " + def.identifier
                    + " — offline reconstruction failed (does its construction touch the world?)");
            check(key.definition == def, "reconstructed key has the wrong definition for "
                    + def.identifier + " — got "
                    + (key.definition == null ? "null" : key.definition.identifier));
            count++;
        }
        check(count > 0, "no pipe definitions were registered — registry empty?");
        helper.succeed();
    }

    /** (2) The packed "con" bits decode into the model key's connection distances: a PIPE connection
     *  (0b01, two bits per {@link Direction#ordinal()}) gives a positive distance, an unset side 0. */
    public static void testConnectionBitsDecodeIntoModelKey(GameTestHelper helper) {
        int con = (0b01 << (Direction.NORTH.ordinal() * 2)) | (0b01 << (Direction.UP.ordinal() * 2));
        PipeModelKey key = PipePreviewModel.modelKey(tileNbtFor("buildcraftunofficial:wood_item", con));
        check(key != null, "modelKey null for wood_item");
        for (Direction d : Direction.values()) {
            float dist = key.connected[d.ordinal()];
            boolean expectConnected = d == Direction.NORTH || d == Direction.UP;
            if (expectConnected) {
                check(dist > 0, d + " should be connected (distance > 0) but was " + dist);
            } else {
                check(dist == 0, d + " should be unconnected (distance 0) but was " + dist);
            }
        }
        helper.succeed();
    }

    /** (3) Missing / unknown NBT yields null (graceful fallback), never an exception. */
    public static void testBadNbtYieldsNull(GameTestHelper helper) {
        check(PipePreviewModel.modelKey(null) == null,
                "null tile NBT should give null");
        check(PipePreviewModel.modelKey(new CompoundTag()) == null,
                "tile NBT without a 'pipe' tag should give null");
        CompoundTag emptyPipe = new CompoundTag();
        emptyPipe.put("pipe", new CompoundTag());
        check(PipePreviewModel.modelKey(emptyPipe) == null,
                "empty 'pipe' tag should give null");
        check(PipePreviewModel.modelKey(tileNbtFor("buildcraftunofficial:does_not_exist", 0)) == null,
                "unknown pipe definition should give null, not throw");
        helper.succeed();
    }
}
