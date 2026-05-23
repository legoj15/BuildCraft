/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.api.tiles.IControllable.Mode;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileFiller;

/**
 * Coverage for the {@code building_for_the_future} advancement wiring on the Filler.
 * <p>
 * Per {@code CLAUDE.md}'s "Player-state testing limitation" — {@code makeMockPlayer}
 * returns a plain {@code Player}, not a {@code ServerPlayer}, so the actual award call
 * inside {@link buildcraft.lib.misc.AdvancementUtil} short-circuits. These tests instead
 * pin the predicates and wiring around the award: the advancement JSON exists with the
 * matching {@code code_trigger} criterion, the constant on {@link TileFiller} points at
 * the right id, and the LOOP-mode re-arm in {@code setControlMode} resets
 * {@code finished} so a player who switches a finished Filler to LOOP can still trigger
 * the award on the next cycle.
 */
public class FillerAdvancementTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testBuildingForTheFutureAdvancement(GameTestHelper helper) {
        // (a) JSON contract — advancement loaded with a 'code_trigger' criterion.
        net.minecraft.advancements.AdvancementHolder holder =
                helper.getLevel().getServer().getAdvancements().get(
                        net.minecraft.resources.Identifier.parse("buildcraftunofficial:building_for_the_future"));
        assertTrue(holder != null, "building_for_the_future advancement is not loaded");
        assertTrue(holder.value().criteria().containsKey("code_trigger"),
                "building_for_the_future must keep a 'code_trigger' criterion — "
                        + "AdvancementUtil.unlockAdvancement awards that criterion name");

        // (b) The constant TileFiller awards must match the JSON id (otherwise the grant
        //     reaches AdvancementUtil but logs an "undefined advancement" warning and
        //     silently no-ops).
        assertTrue(TileFiller.ADVANCEMENT_BUILDING_FOR_THE_FUTURE.equals(
                        net.minecraft.resources.Identifier.parse("buildcraftunofficial:building_for_the_future")),
                "TileFiller.ADVANCEMENT_BUILDING_FOR_THE_FUTURE must match the JSON id");

        // (c) setControlMode re-arm — entering LOOP from a non-LOOP state must reset
        //     `finished`, so a Filler that completed in ON mode kicks off another cycle
        //     (and can earn the LOOP-completion advancement) when the player flips LOOP.
        //     The tick loop is the only production path that sets `finished = true`, so
        //     this test reaches into the field via reflection rather than wiring up a
        //     full builder/inventory/box just to exercise the transition.
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCBuildersBlocks.FILLER.get());
        TileFiller filler = helper.getBlockEntity(pos, TileFiller.class);
        java.lang.reflect.Field finishedField;
        try {
            finishedField = TileFiller.class.getDeclaredField("finished");
            finishedField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("TileFiller.finished field missing — test predicate stale", e);
        }
        java.util.function.Consumer<Boolean> setFinished = v -> {
            try {
                finishedField.setBoolean(filler, v);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        };

        // OFF→ON transition resets `finished` (existing behaviour, sanity-checked here so a
        // future refactor that removes either reset is caught).
        filler.setControlMode(Mode.OFF);
        setFinished.accept(true);
        filler.setControlMode(Mode.ON);
        assertTrue(!filler.getFinished(),
                "OFF→ON must reset finished (pre-existing behaviour)");

        // ON→LOOP transition must also reset `finished` — this is the new contract that
        // lets a once-completed Filler re-arm and earn building_for_the_future on LOOP.
        filler.setControlMode(Mode.ON);
        setFinished.accept(true);
        filler.setControlMode(Mode.LOOP);
        assertTrue(!filler.getFinished(),
                "ON→LOOP must reset finished so a once-completed Filler re-arms on LOOP");

        // LOOP→LOOP must NOT reset (would defeat the !finished guard in the tick code and
        // cause repeated advancement-award calls every tick once the builder idles).
        setFinished.accept(true);
        filler.setControlMode(Mode.LOOP);
        assertTrue(filler.getFinished(),
                "LOOP→LOOP must leave finished alone so the tick !finished gate works");

        helper.succeed();
    }
}
