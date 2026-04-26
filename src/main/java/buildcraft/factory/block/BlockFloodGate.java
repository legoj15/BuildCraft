/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tools.IToolWrench;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.tile.TileFloodGate;

/**
 * The Flood Gate block. Has 5 configurable sides (all except UP) that can be
 * toggled open/closed with a wrench.
 * Ported from 1.12.2 BlockFloodGate.
 */
public class BlockFloodGate extends BaseEntityBlock {
    public static final MapCodec<BlockFloodGate> CODEC =
            simpleCodec(BlockFloodGate::new);

    /** Map of direction → boolean property, excluding UP. */
    public static final Map<Direction, Property<Boolean>> CONNECTED_MAP;

    static {
        CONNECTED_MAP = new HashMap<>(BuildCraftProperties.CONNECTED_MAP);
        CONNECTED_MAP.remove(Direction.UP);
    }

    public BlockFloodGate(Properties properties) {
        super(properties);
        // Default state: all 5 sides open
        BlockState defaultState = this.stateDefinition.any();
        for (Property<Boolean> prop : CONNECTED_MAP.values()) {
            defaultState = defaultState.setValue(prop, true);
        }
        this.registerDefaultState(defaultState);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTED_MAP.values().forEach(builder::add);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFloodGate(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, BCFactoryBlockEntities.FLOOD_GATE.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.getItem() instanceof IToolWrench) {
            Direction side = hitResult.getDirection();
            if (side != Direction.UP && CONNECTED_MAP.containsKey(side)) {
                if (!level.isClientSide()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof TileFloodGate floodGate) {
                        // Toggle the side
                        if (!floodGate.openSides.remove(side)) {
                            floodGate.openSides.add(side);
                        }
                        // Reset BFS queue + adaptive rebuild delay so the gate
                        // re-plans from the new side configuration immediately.
                        floodGate.onSidesToggled();
                        // Update the blockstate to reflect the new open/closed sides
                        BlockState newState = state;
                        for (Map.Entry<Direction, Property<Boolean>> entry : CONNECTED_MAP.entrySet()) {
                            newState = newState.setValue(entry.getValue(),
                                    floodGate.openSides.contains(entry.getKey()));
                        }
                        // Use UPDATE_CLIENTS (not UPDATE_ALL) so we don't fire
                        // neighborChanged on the attached pipe. The flood gate's
                        // open/closed state is purely cosmetic — pipes connect by
                        // fluid capability, which is registered on all sides
                        // unconditionally — so no neighbour needs to react. Firing
                        // neighborChanged would cause TilePipeHolder to rebake its
                        // model, briefly dropping plug renderers (e.g. the pulsar).
                        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
                        floodGate.setChanged();
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }
}
