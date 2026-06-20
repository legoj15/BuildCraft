/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.blocks.CustomRotationHelper;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemWrench_Neptune;

/**
 * Coverage for {@link VanillaRotationHandlers}. Each test drives
 * {@link CustomRotationHelper#attemptRotateBlock} directly to exercise the
 * registration + per-block handler. One integration test confirms the wrench
 * item's {@code useOn} path still routes to the rotation handler.
 */
public class VanillaRotationTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static InteractionResult rotate(GameTestHelper helper, BlockPos relPos, Direction side) {
        BlockPos absPos = helper.absolutePos(relPos);
        return CustomRotationHelper.INSTANCE.attemptRotateBlock(
                helper.getLevel(), absPos, helper.getBlockState(relPos), side);
    }

    /** Furnace: horizontal 4-face cycle, order E→S→W→N. */
    public static void testFurnaceCyclesHorizontally(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));

        Direction[] expected = { Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH };
        for (Direction next : expected) {
            InteractionResult r = rotate(helper, pos, Direction.UP);
            assertTrue(r == InteractionResult.SUCCESS, "expected SUCCESS, got " + r);
            Direction got = helper.getBlockState(pos).getValue(FurnaceBlock.FACING);
            assertTrue(got == next, "furnace expected " + next + ", got " + got);
        }
        helper.succeed();
    }

    /** Dispenser: 6-face free rotation, order E→S→D→W→N→U. */
    public static void testDispenserCyclesAllSix(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));

        Direction[] expected = {
                Direction.SOUTH, Direction.DOWN, Direction.WEST, Direction.NORTH, Direction.UP, Direction.EAST
        };
        for (Direction next : expected) {
            assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS, "expected SUCCESS");
            Direction got = helper.getBlockState(pos).getValue(DispenserBlock.FACING);
            assertTrue(got == next, "dispenser expected " + next + ", got " + got);
        }
        helper.succeed();
    }

    /** Hopper: cycles E→S→W→N→D (no UP — FACING_HOPPER excludes it). */
    public static void testHopperCyclesFiveFaces(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        Direction[] expected = {
                Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.DOWN
        };
        for (Direction next : expected) {
            assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS, "expected SUCCESS");
            Direction got = helper.getBlockState(pos).getValue(HopperBlock.FACING);
            assertTrue(got == next, "hopper expected " + next + ", got " + got);
        }
        helper.succeed();
    }

    /** A piston with EXTENDED=true must refuse rotation (FAIL), not silently pass. */
    public static void testExtendedPistonRefuses(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        // We can't safely set a piston to EXTENDED=true through normal placement (it'd
        // try to push a head block) — just set the state directly.
        helper.setBlock(pos, Blocks.PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.NORTH)
                .setValue(PistonBaseBlock.EXTENDED, true));

        InteractionResult r = rotate(helper, pos, Direction.UP);
        assertTrue(r == InteractionResult.FAIL, "extended piston must FAIL, got " + r);
        Direction stillFacing = helper.getBlockState(pos).getValue(PistonBaseBlock.FACING);
        assertTrue(stillFacing == Direction.NORTH, "facing must not change on FAIL, got " + stillFacing);
        helper.succeed();
    }

    /** Banner (standing): ROTATION_16 increments by 1, wraps at 16. */
    public static void testStandingBannerSpins16(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        //? if >=26.2 {
        /*helper.setBlock(pos, Blocks.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BannerBlock.ROTATION, 0));*/
        //?} else {
        helper.setBlock(pos, Blocks.WHITE_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 0));
        //?}

        for (int i = 1; i <= 16; i++) {
            assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS, "expected SUCCESS at step " + i);
            int got = helper.getBlockState(pos).getValue(BannerBlock.ROTATION);
            int want = i & 0xF;
            assertTrue(got == want, "banner step " + i + ": expected " + want + ", got " + got);
        }
        helper.succeed();
    }

    /** Skull (floor): ROTATION_16 increments, same path as banner/sign. */
    public static void testFloorSkullSpins16(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 7));

        assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS, "expected SUCCESS");
        int got = helper.getBlockState(pos).getValue(SkullBlock.ROTATION);
        assertTrue(got == 8, "skull expected 8, got " + got);
        helper.succeed();
    }

    /**
     * Trapdoor: the HALF flips whenever the current FACING is the first in the cycle
     * (EAST). Matches 1.12.2 semantics — net effect is an 8-position cycle through
     * (4 facings × 2 halves). No neighbour blocks required: 1.12.2 trapdoors rotated
     * freely regardless of support and modern MC keeps that — see
     * {@link #testTrapDoorRotatesFreestanding} for the freestanding-cycle regression.
     */
    public static void testTrapDoorHalfFlipOnWrap(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.EAST)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM));

        assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS, "expected SUCCESS");
        BlockState after = helper.getBlockState(pos);
        assertTrue(after.getValue(TrapDoorBlock.FACING) == Direction.SOUTH,
                "trapdoor FACING expected SOUTH, got " + after.getValue(TrapDoorBlock.FACING));
        assertTrue(after.getValue(TrapDoorBlock.HALF) == Half.TOP,
                "trapdoor HALF expected TOP (flipped at start of new cycle), got " + after.getValue(TrapDoorBlock.HALF));

        // A second click from (SOUTH, TOP) should NOT flip (FACING != EAST).
        rotate(helper, pos, Direction.UP);
        BlockState after2 = helper.getBlockState(pos);
        assertTrue(after2.getValue(TrapDoorBlock.HALF) == Half.TOP,
                "trapdoor HALF should not flip at SOUTH→WEST, got " + after2.getValue(TrapDoorBlock.HALF));
        helper.succeed();
    }

    /**
     * Regression: trapdoors with no neighbouring blocks must still rotate through
     * all 8 (FACING, HALF) positions — 1.12.2 had no attach restriction at all.
     * An earlier iteration of this code validated the hinge side via
     * {@code isFaceSturdy} and broke freestanding trapdoors entirely (they got
     * stuck on FAIL with only the slide sound playing). 8 consecutive clicks must
     * land back on the starting state.
     */
    public static void testTrapDoorRotatesFreestanding(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        // Air everywhere — no neighbours. The trapdoor floats.
        helper.setBlock(pos, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.EAST)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM));

        // 1.12.2 cycle starting from (E, BOT):
        //   (E,BOT) → (S,TOP) → (W,TOP) → (N,TOP) → (E,TOP) → (S,BOT) → (W,BOT) → (N,BOT) → (E,BOT)
        Direction[] expectedFacings = {
                Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST
        };
        Half[] expectedHalves = {
                Half.TOP, Half.TOP, Half.TOP, Half.TOP,
                Half.BOTTOM, Half.BOTTOM, Half.BOTTOM, Half.BOTTOM
        };
        for (int i = 0; i < expectedFacings.length; i++) {
            assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS,
                    "step " + (i + 1) + " on freestanding trapdoor expected SUCCESS");
            BlockState after = helper.getBlockState(pos);
            assertTrue(after.getValue(TrapDoorBlock.FACING) == expectedFacings[i]
                            && after.getValue(TrapDoorBlock.HALF) == expectedHalves[i],
                    "step " + (i + 1) + " expected (" + expectedFacings[i] + "," + expectedHalves[i]
                            + "), got (" + after.getValue(TrapDoorBlock.FACING) + "," + after.getValue(TrapDoorBlock.HALF) + ")");
        }
        helper.succeed();
    }

    /**
     * Door: rotating the lower half also rotates the upper half (they must stay in
     * sync). The HINGE flips whenever current FACING is the first in the cycle
     * (EAST), matching 1.12.2's 8-position cycle (4 facings × 2 hinges).
     */
    public static void testDoorRotatesBothHalvesAndFlipsHingeOnWrap(GameTestHelper helper) {
        BlockPos lower = new BlockPos(2, 2, 2);
        BlockPos upper = lower.above();
        helper.setBlock(lower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        helper.setBlock(upper, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));

        assertTrue(rotate(helper, lower, Direction.UP) == InteractionResult.SUCCESS, "expected SUCCESS");

        BlockState lo = helper.getBlockState(lower);
        BlockState hi = helper.getBlockState(upper);
        assertTrue(lo.getValue(DoorBlock.FACING) == Direction.SOUTH, "lower FACING expected SOUTH, got " + lo.getValue(DoorBlock.FACING));
        assertTrue(hi.getValue(DoorBlock.FACING) == Direction.SOUTH, "upper FACING expected SOUTH (synced), got " + hi.getValue(DoorBlock.FACING));
        assertTrue(hi.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT,
                "hinge expected RIGHT (flipped at start of new cycle), got " + hi.getValue(DoorBlock.HINGE));

        // From (SOUTH, RIGHT), a second click should NOT flip hinge (FACING != EAST).
        rotate(helper, lower, Direction.UP);
        BlockState hi2 = helper.getBlockState(upper);
        assertTrue(hi2.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT,
                "hinge should not flip at SOUTH→WEST step, got " + hi2.getValue(DoorBlock.HINGE));
        helper.succeed();
    }

    /** WallTorch: skips invalid attach orientations (no solid block behind that side). */
    public static void testWallTorchSkipsUnsupportedFace(GameTestHelper helper) {
        // Build an L-shaped support: solid block at the SOUTH neighbour only.
        // Place a wall torch attached to that south face (FACING=SOUTH means attached
        // to the block to the NORTH of the torch — wait, let me re-check the convention.
        //
        // In WallTorchBlock, FACING is the direction the torch is pointing — i.e. the
        // block that supports it is at pos.relative(FACING.getOpposite()).
        // So FACING=SOUTH means support is at the block to the NORTH.
        BlockPos torchPos = new BlockPos(2, 2, 2);
        BlockPos supportNorth = torchPos.relative(Direction.NORTH);
        helper.setBlock(supportNorth, Blocks.STONE);

        helper.setBlock(torchPos, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));

        // Order is E→S→W→N. Starting at SOUTH, next valid steps would be WEST, NORTH, EAST,
        // but only SOUTH has support. All others should be invalid → FAIL.
        InteractionResult r = rotate(helper, torchPos, Direction.UP);
        assertTrue(r == InteractionResult.FAIL,
                "wall torch with only one valid orientation should FAIL to rotate, got " + r);
        helper.succeed();
    }

    /** Double chest: rotating one half flips both halves 180° in unison. */
    public static void testDoubleChestRotatesBothHalves(GameTestHelper helper) {
        // Two chests sitting side-by-side along the X axis form a double chest. With
        // FACING=NORTH, the LEFT/RIGHT halves are determined by which side is which.
        BlockPos west = new BlockPos(2, 2, 2);
        BlockPos east = west.east();

        helper.setBlock(west, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(east, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));

        assertTrue(rotate(helper, west, Direction.UP) == InteractionResult.SUCCESS, "expected SUCCESS");

        BlockState w = helper.getBlockState(west);
        BlockState e = helper.getBlockState(east);
        assertTrue(w.getValue(ChestBlock.FACING) == Direction.SOUTH,
                "west half should face SOUTH after 180° flip, got " + w.getValue(ChestBlock.FACING));
        assertTrue(e.getValue(ChestBlock.FACING) == Direction.SOUTH,
                "east half should face SOUTH after 180° flip, got " + e.getValue(ChestBlock.FACING));
        // After a 180° flip the LEFT/RIGHT designations swap.
        assertTrue(w.getValue(ChestBlock.TYPE) == ChestType.RIGHT,
                "west half should become RIGHT, got " + w.getValue(ChestBlock.TYPE));
        assertTrue(e.getValue(ChestBlock.TYPE) == ChestType.LEFT,
                "east half should become LEFT, got " + e.getValue(ChestBlock.TYPE));
        helper.succeed();
    }

    /**
     * 1.12.2-parity wrench gating on vanilla blocks: non-crouch wrench falls
     * through to the block's normal interaction (e.g. trapdoor toggles open),
     * crouch wrench invokes rotation. Both branches matter — claiming the
     * interaction unconditionally would steal the trapdoor's open/close gesture
     * from players who just have the wrench in hand.
     */
    public static void testWrenchOnItemUseFirstCrouchGate(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        //? if >=26.2 {
        /*helper.setBlock(pos, Blocks.COPPER_TRAPDOOR.weathering()
                .pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED).defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM));*/
        //?} else {
        helper.setBlock(pos, Blocks.COPPER_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM));
        //?}

        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        BlockPos absPos = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.UP, absPos, false);
        ItemWrench_Neptune wrenchItem = (ItemWrench_Neptune) BCCoreItems.WRENCH.get();

        // --- Non-crouch: onItemUseFirst must return PASS so the trapdoor's normal
        //     useWithoutItem can toggle OPEN. ---
        net.minecraft.world.entity.player.Player nonCrouch =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        nonCrouch.setShiftKeyDown(false);
        net.minecraft.world.item.context.UseOnContext nonCrouchCtx =
                new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), nonCrouch,
                        InteractionHand.MAIN_HAND, wrench, hit);

        InteractionResult r1 = wrenchItem.onItemUseFirst(wrench, nonCrouchCtx);
        assertTrue(r1 == InteractionResult.PASS,
                "non-crouch wrench on trapdoor must PASS so block's useWithoutItem runs, got " + r1);
        assertTrue(helper.getBlockState(pos).getValue(TrapDoorBlock.FACING) == Direction.NORTH,
                "non-crouch must NOT rotate the trapdoor — that would steal the open/close gesture");

        // --- Crouch: onItemUseFirst claims the interaction and rotates. ---
        net.minecraft.world.entity.player.Player crouch =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        crouch.setShiftKeyDown(true);
        net.minecraft.world.item.context.UseOnContext crouchCtx =
                new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), crouch,
                        InteractionHand.MAIN_HAND, wrench, hit);

        InteractionResult r2 = wrenchItem.onItemUseFirst(wrench, crouchCtx);
        assertTrue(r2 == InteractionResult.SUCCESS,
                "crouch wrench on trapdoor must SUCCEED via onItemUseFirst, got " + r2);
        BlockState after = helper.getBlockState(pos);
        assertTrue(after.getValue(TrapDoorBlock.FACING) == Direction.EAST,
                "crouch wrench should rotate NORTH→EAST, got " + after.getValue(TrapDoorBlock.FACING));
        assertTrue(!after.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN),
                "crouch rotate must not toggle OPEN");
        helper.succeed();
    }

    /**
     * Button (FaceAttachedHorizontalDirectionalBlock): cycles through all 12
     * (FACE × FACING) orientations, not just the 4 horizontal facings. With
     * solid blocks on every adjacent face the rotation steps through wall,
     * floor, and ceiling positions in turn.
     */
    public static void testButtonCyclesThroughAllTwelveOrientations(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        // Stone on every face so every (FACE, FACING) candidate has support.
        for (Direction d : Direction.values()) {
            helper.setBlock(pos.relative(d), Blocks.STONE);
        }
        helper.setBlock(pos, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

        // Order: WALL E→S→W→N, then FLOOR E→S→W→N, then CEILING E→S→W→N, then back to WALL E.
        AttachFace[] expectedFaces = {
                AttachFace.WALL, AttachFace.WALL, AttachFace.WALL,
                AttachFace.FLOOR, AttachFace.FLOOR, AttachFace.FLOOR, AttachFace.FLOOR,
                AttachFace.CEILING, AttachFace.CEILING, AttachFace.CEILING, AttachFace.CEILING,
                AttachFace.WALL
        };
        Direction[] expectedFacings = {
                Direction.SOUTH, Direction.WEST, Direction.NORTH,
                Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH,
                Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH,
                Direction.EAST
        };
        for (int i = 0; i < expectedFaces.length; i++) {
            assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS,
                    "step " + (i + 1) + " expected SUCCESS");
            AttachFace face = helper.getBlockState(pos).getValue(BlockStateProperties.ATTACH_FACE);
            Direction facing = helper.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING);
            assertTrue(face == expectedFaces[i] && facing == expectedFacings[i],
                    "step " + (i + 1) + " expected (" + expectedFaces[i] + "," + expectedFacings[i]
                            + "), got (" + face + "," + facing + ")");
        }
        helper.succeed();
    }

    /**
     * Regression: a button with only ONE supported attach face must skip every
     * unsupported candidate in the 12-state cycle and stay on the supported one.
     * Without the canSurvive guard the wrench would happily leave the button
     * stuck on an air face. Multiple clicks must all land back on FLOOR because
     * it's the only supported face.
     */
    public static void testButtonSkipsAttachToAir(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        // Only the block below is solid → only FACE=FLOOR is supported.
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

        // The wrench cycles through all 12 candidates but only the 4 FLOOR ones
        // canSurvive; the rotation must stay on FLOOR every click.
        Direction[] expectedFacings = { Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST };
        for (int i = 0; i < expectedFacings.length; i++) {
            assertTrue(rotate(helper, pos, Direction.UP) == InteractionResult.SUCCESS,
                    "step " + (i + 1) + " expected SUCCESS");
            BlockState after = helper.getBlockState(pos);
            assertTrue(after.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.FLOOR,
                    "step " + (i + 1) + ": FACE must stay FLOOR (only supported), got "
                            + after.getValue(BlockStateProperties.ATTACH_FACE));
            assertTrue(after.getValue(BlockStateProperties.HORIZONTAL_FACING) == expectedFacings[i],
                    "step " + (i + 1) + ": FACING expected " + expectedFacings[i] + ", got "
                            + after.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        helper.succeed();
    }

    /** Integration: the wrench item's useOn drives the same rotation handler. */
    public static void testWrenchUseOnRotatesFurnace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));

        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        BlockPos absPos = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.UP, absPos, false);

        net.minecraft.world.item.context.UseOnContext ctx = new net.minecraft.world.item.context.UseOnContext(
                helper.getLevel(),
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL),
                InteractionHand.MAIN_HAND,
                wrench,
                hit);

        InteractionResult r = ((ItemWrench_Neptune) BCCoreItems.WRENCH.get()).useOn(ctx);
        assertTrue(r == InteractionResult.SUCCESS, "wrench useOn on furnace should SUCCEED, got " + r);
        Direction got = helper.getBlockState(pos).getValue(FurnaceBlock.FACING);
        assertTrue(got == Direction.EAST, "furnace should rotate NORTH→EAST via wrench useOn, got " + got);
        helper.succeed();
    }
}
