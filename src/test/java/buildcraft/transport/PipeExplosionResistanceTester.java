/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
//? if >=1.21.10 {
import net.minecraft.world.level.ServerExplosion;
//?}
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.silicon.plug.PluggableFacade;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * A facade armours the side of the pipe it covers: an explosion on that side meets the facade's block, so the
 * pipe resists it as well as that block would. Ported from 1.12.2's {@code BlockPipeHolder#getExplosionResistance},
 * which picks the side facing the blast and asks that side's pluggable; the modern hook is NeoForge's
 * context-aware {@code IBlockExtension#getExplosionResistance}, which vanilla's explosion rays call per block.
 *
 * <p>Unlike 1.12.2, a pluggable can only <em>raise</em> the pipe's resistance — a glass facade no longer makes the
 * pipe behind it easier to blow up than a bare pipe.
 */
public class PipeExplosionResistanceTester {

    private static final BlockPos PIPE = new BlockPos(2, 2, 2);

    public static void testFacadeArmoursTheSideItCovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(PIPE, BCTransportBlocks.PIPE_HOLDER.get());
        BlockPos abs = helper.absolutePos(PIPE);
        TilePipeHolder tile = (TilePipeHolder) level.getBlockEntity(abs);
        helper.assertTrue(tile != null, "pipe holder must have a block entity");
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));

        Explosion fromEast = explosionAt(level, Vec3.atCenterOf(abs).add(2.5, 0.4, -0.3));
        Explosion fromWest = explosionAt(level, Vec3.atCenterOf(abs).add(-2.5, 0.4, 0.3));

        float bare = level.getBlockState(abs).getExplosionResistance(level, abs, fromEast);
        helper.assertTrue(bare > 0 && bare < 100, "sanity: a bare pipe has its own modest resistance, got " + bare);

        FacadeStateManager.ensureInitialized();
        FacadeBlockStateInfo obsidian = FacadeStateManager.getInfoForBlock(Blocks.OBSIDIAN);
        helper.assertTrue(obsidian != null, "obsidian must be a valid facade state");
        tile.replacePluggable(Direction.EAST, new PluggableFacade(BCSiliconPlugs.facade, tile, Direction.EAST,
            FacadeInstance.createSingle(obsidian, false)));

        float covered = level.getBlockState(abs).getExplosionResistance(level, abs, fromEast);
        float obsidianResistance = Blocks.OBSIDIAN.defaultBlockState().getExplosionResistance(level, abs, fromEast);
        helper.assertTrue(covered == obsidianResistance,
            "a blast on the facade's side must meet the obsidian facade (" + obsidianResistance + "), got " + covered);
        helper.assertTrue(covered > bare, "the facade-covered side must resist more than a bare pipe");

        float uncovered = level.getBlockState(abs).getExplosionResistance(level, abs, fromWest);
        helper.assertTrue(uncovered == bare,
            "a blast on the uncovered side must meet the bare pipe (" + bare + "), got " + uncovered);

        // A facade weaker than the pipe must not weaken it.
        FacadeBlockStateInfo glass = FacadeStateManager.getInfoForBlock(Blocks.GLASS);
        helper.assertTrue(glass != null, "glass must be a valid facade state");
        tile.replacePluggable(Direction.WEST, new PluggableFacade(BCSiliconPlugs.facade, tile, Direction.WEST,
            FacadeInstance.createSingle(glass, false)));
        float glassSide = level.getBlockState(abs).getExplosionResistance(level, abs, fromWest);
        helper.assertTrue(glassSide == bare,
            "a glass facade must not make the pipe weaker than bare (" + bare + "), got " + glassSide);

        helper.succeed();
    }

    /** An explosion object at {@code centre} that is never detonated — only its centre is consulted. */
    private static Explosion explosionAt(ServerLevel level, Vec3 centre) {
        //? if >=1.21.10 {
        return new ServerExplosion(level, null, null, null, centre, 1.0f, false, Explosion.BlockInteraction.KEEP);
        //?} else {
        /*return new Explosion(level, null, centre.x, centre.y, centre.z, 1.0f, false, Explosion.BlockInteraction.KEEP);*/
        //?}
    }
}
