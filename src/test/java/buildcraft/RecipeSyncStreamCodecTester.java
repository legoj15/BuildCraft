/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import buildcraft.silicon.recipe.FacadeSwapRecipe;
import buildcraft.transport.recipe.DyedPipeRecipe;
import buildcraft.transport.recipe.PipePaintRecipe;

/**
 * Regression guard for the login crash where a stateless custom crafting recipe's network stream
 * codec used {@code StreamCodec.unit(instance)}. On the NeoForge &gt;=1.21.10 recipe-content sync the
 * server encodes every datapack-loaded recipe (a <em>fresh</em> instance, built by
 * {@code MapCodec.unit(X::new)}) through its serializer's stream codec. {@code StreamCodec.unit}'s
 * encoder compares the value against the single instance it captured at class-init — and
 * {@code CustomRecipe} has no {@code equals()}, so the identity check failed with
 * {@code IllegalStateException: Can't encode '...recipe@a', expected '...@b'}, which netty turned
 * into a failed {@code clientbound/minecraft:custom_payload} and dropped the joining player.
 * <p>
 * Each test encodes a freshly-built recipe (distinct from anything the codec might hold) through its
 * real {@code STREAM_CODEC} and round-trips it. Pre-fix this throws; post-fix (no-op encoder +
 * fresh-instance decoder) it round-trips cleanly. Sub-26.1 nodes dodge the bug entirely — their
 * serializers serialize the {@code CraftingBookCategory} field rather than capturing an instance, and
 * they expose no {@code STREAM_CODEC} field to exercise, so each test here is a no-op on those nodes.
 */
public class RecipeSyncStreamCodecTester {

    private static <T> void assertRoundTrips(GameTestHelper helper,
            StreamCodec<RegistryFriendlyByteBuf, T> codec, T fresh, String id) {
        RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            // The value handed to the codec is a brand-new instance, exactly as the datapack loader
            // produces — and deliberately NOT the instance the codec was built around. Pre-fix this
            // line threw IllegalStateException "Can't encode ... expected ...".
            codec.encode(buf, fresh);
            T decoded = codec.decode(buf);
            if (decoded == null) {
                helper.fail(id + " recipe stream codec decoded to null after round-trip");
                return;
            }
            helper.succeed();
        } finally {
            buf.release();
        }
    }

    public static void testDyedPipeSyncRoundTrip(GameTestHelper helper) {
        // 26.1-only: no STREAM_CODEC field exists below the 26.1 cliff, so this is a no-op there (see class doc).
        //? if >=26.1 {
        assertRoundTrips(helper, DyedPipeRecipe.STREAM_CODEC, new DyedPipeRecipe(), "dyed_pipe");
        //?} else {
        /*helper.succeed();*/
        //?}
    }

    public static void testPipePaintSyncRoundTrip(GameTestHelper helper) {
        // 26.1-only: no STREAM_CODEC field exists below the 26.1 cliff, so this is a no-op there (see class doc).
        //? if >=26.1 {
        assertRoundTrips(helper, PipePaintRecipe.STREAM_CODEC, new PipePaintRecipe(), "pipe_paint");
        //?} else {
        /*helper.succeed();*/
        //?}
    }

    public static void testFacadeSwapSyncRoundTrip(GameTestHelper helper) {
        // 26.1-only: no STREAM_CODEC field exists below the 26.1 cliff, so this is a no-op there (see class doc).
        //? if >=26.1 {
        assertRoundTrips(helper, FacadeSwapRecipe.STREAM_CODEC, new FacadeSwapRecipe(), "facade_swap");
        //?} else {
        /*helper.succeed();*/
        //?}
    }
}
