/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.block.BlockBCTile_Directional;
import buildcraft.lib.misc.BlockDropsUtil;
import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.tile.TileRequester;

/** The Requester block, ported from 7.1.x {@code buildcraft.robotics.BlockRequester}: a request network
 *  endpoint a delivery robot services. Horizontal facing (7.1.x {@code setRotatable(true)}), iron-like
 *  stats, opens the GUI on use (inherited from the base). */
public class BlockRequester extends BlockBCTile_Directional<TileRequester> {
    public static final MapCodec<BlockRequester> CODEC = simpleCodec(BlockRequester::new);

    public BlockRequester(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileRequester(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCRoboticsBlockEntities.REQUESTER.get();
    }

    // Load-bearing on the 1.21.1 node only — see the base class javadoc.
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Drops the delivery slots' contents (the phantom templates are excluded by the itemManager and are
     *  never real items). The block itself drops via its loot table. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileRequester requester) {
            BlockDropsUtil.dropTileContents(level, pos, requester);
            requester.markDropsHandled();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
