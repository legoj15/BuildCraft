/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import static buildcraft.lib.test.GameTestUtil.check;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BuilderRefloodTester;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * A machine a Builder builds belongs to the Builder's owner, never to the owner its blueprint was scanned from: the
 * built tag carries no owner, and the Builder stamps its own on what it placed (none, for a Builder nobody owns). A
 * machine that dug as the blueprint's owner could break where only that player may, and a copy of someone's
 * blueprint would hand its builder that player's permissions. The machine is a Quarry, the one that acts on its own
 * the moment it is built (it lays out its default area, which reaches far outside the test's cell; it has no power,
 * so it digs nothing, and is removed with the Builder on every way out).
 */
public class BlueprintBuiltOwnerTester {

    private static final GameProfile BLUEPRINT_OWNER =
            new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Alice");
    private static final GameProfile BUILDER_OWNER =
            new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Bob");

    /** Work ticks counted from the cell ticking (a Builder is a block entity, so it runs only once its chunk does):
     *  one block to place. {@link #POLL_TICKS} adds the wait for the cell, and the manifests' {@code max_ticks} stay
     *  above it. */
    private static final int WORK_TICKS = 300;
    private static final int POLL_TICKS = EntityArenaUtil.CELL_READY_TICKS + WORK_TICKS;

    /** Bob's Builder builds Alice's Quarry: it is Bob's. */
    public static void blueprintBuilderOwnsWhatItBuilds(GameTestHelper helper) {
        buildQuarryFromAlicesBlueprint(helper, BUILDER_OWNER);
    }

    /** A Builder nobody owns builds an unowned Quarry, not Alice's. */
    public static void unownedBlueprintBuilderBuildsAnUnownedMachine(GameTestHelper helper) {
        buildQuarryFromAlicesBlueprint(helper, null);
    }

    private static void buildQuarryFromAlicesBlueprint(GameTestHelper helper, @Nullable GameProfile builderOwner) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceRel = new BlockPos(0, 2, 4);
        BlockPos builtRel = BuilderRefloodTester.cell(0);
        BlockPos builtAbs = helper.absolutePos(builtRel);
        BlockPos builderAbs = helper.absolutePos(BuilderRefloodTester.BUILDER);
        TileBuilder[] builder = {null};
        Runnable cleanup = () -> {
            helper.setBlock(sourceRel, Blocks.AIR);
            helper.setBlock(builtRel, Blocks.AIR);
            if (builder[0] != null) {
                builder[0].setSnapshot(ItemStack.EMPTY);
                builder[0].clearContent();
                level.removeBlock(builderAbs, false);
            }
        };
        try {
            // Alice's Quarry, scanned the way the Architect does: the blueprint holds her as its owner.
            BlockState quarryState = BCBuildersBlocks.QUARRY.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
            helper.setBlock(sourceRel, quarryState);
            TileQuarry source = (TileQuarry) level.getBlockEntity(helper.absolutePos(sourceRel));
            check(source != null, "the placed Quarry must have its block entity");
            source.onPlacedBy(null, ItemStack.EMPTY);
            source.setOwner(BLUEPRINT_OWNER);
            BlockPos sourceAbs = helper.absolutePos(sourceRel);
            ISchematicBlock scanned = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(
                    level, BlockPos.ZERO, sourceAbs, level.getBlockState(sourceAbs), quarryState.getBlock()));
            check(scanned instanceof SchematicBlockDefault, "a Quarry must scan to a SchematicBlockDefault");
            check(GameProfileUtil.getId(BLUEPRINT_OWNER).toString()
                            .equals(NBTUtilBC.getString(((SchematicBlockDefault) scanned).tileNbt, "ownerUUID", "")),
                    "precondition: the blueprint must hold its owner, got " + ((SchematicBlockDefault) scanned).tileNbt);
            helper.setBlock(sourceRel, Blocks.AIR);

            builder[0] = BuilderRefloodTester.setUpBuilder(helper, new int[] {BuilderRefloodTester.FIRST_EXTRA_KIND},
                    new BlockPos[0], List.of(), List.of(scanned));
            builder[0].setOwner(builderOwner);
            int slot = 3;
            for (ItemStack required : scanned.computeRequiredItems()) {
                builder[0].setResource(slot++, required.copy());
            }
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }

        BooleanSupplier built = () -> {
            if (!level.getBlockState(builtAbs).is(BCBuildersBlocks.QUARRY.get())) {
                return false;
            }
            check(level.getBlockEntity(builtAbs) instanceof TileQuarry, "the built Quarry must have its block entity");
            GameProfile owner = ((TileQuarry) level.getBlockEntity(builtAbs)).getOwner();
            if (builderOwner == null) {
                check(owner == null, "a Quarry built by a Builder nobody owns must have no owner, got " + describe(owner));
            } else {
                check(owner != null && GameProfileUtil.getId(owner).equals(GameProfileUtil.getId(builderOwner)),
                        "a Quarry built by the Builder of " + describe(builderOwner) + " must belong to them, got "
                                + describe(owner));
            }
            return true;
        };
        String stalled = "the Builder places the Quarry from the blueprint";
        EntityArenaUtil.tickUntil(helper, POLL_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, WORK_TICKS, built,
                        () -> stalled + ": the cell holds " + level.getBlockState(builtAbs)),
                helper::succeed, stalled, cleanup);
    }

    private static String describe(@Nullable GameProfile owner) {
        return owner == null ? "no owner" : GameProfileUtil.getName(owner) + " (" + GameProfileUtil.getId(owner) + ")";
    }
}
