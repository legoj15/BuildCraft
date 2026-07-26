/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.block;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.mj.ILaserTargetBlock;
import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.tile.TileLaserTableBase;

/**
 * Block for laser tables (Assembly Table, Advanced Crafting Table, Integration Table).
 * Implements ILaserTargetBlock so lasers can find and target it, and EntityBlock
 * so each variant creates the correct block entity.
 */
public class BlockLaserTable extends BlockBCTile_Neptune<TileLaserTableBase> implements ILaserTargetBlock {
    /** The 1.12 bounding box: full width, 9/16 height. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    /** Only needed to satisfy {@code BaseEntityBlock#codec}. It is unreachable at runtime — the sole
     *  consumer is {@code BlockTypes.CODEC} via the datagen block-list report, and BuildCraft neither
     *  registers into {@code Registries.BLOCK_TYPE} nor runs datagen — so the variant it reconstructs
     *  is irrelevant, as it is for every other BC block codec. */
    public static final MapCodec<BlockLaserTable> CODEC =
        simpleCodec(props -> new BlockLaserTable(props, BCSiliconBlockEntities.ASSEMBLY_TABLE));

    private final Supplier<? extends BlockEntityType<? extends TileLaserTableBase>> beTypeSupplier;

    public BlockLaserTable(BlockBehaviour.Properties properties,
        Supplier<? extends BlockEntityType<? extends TileLaserTableBase>> beTypeSupplier) {
        super(properties);
        this.beTypeSupplier = beTypeSupplier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // Load-bearing: this block was a plain Block (MODEL default) before the migration. Rooting it on
    // BaseEntityBlock makes it INVISIBLE on the 1.21.1 node (that override was dropped at 1.21.10),
    // so the explicit MODEL is what keeps the three tables rendering there.
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beTypeSupplier.get().create(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return beTypeSupplier.get();
    }

    /** Server-only ticker. Note this is now type-<em>matched</em> against the block's own BE type by
     *  {@code BlockBCTileSupport.ticker}; the pre-migration body was type-unchecked (it handed back a
     *  ticker for any BE type and filtered with an {@code instanceof} inside the lambda). */
    @Nullable
    @Override
    protected BlockEntityTicker<TileLaserTableBase> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.serverTick();
    }

    // useWithoutItem is inherited (BlockBCTileSupport.openTileMenu) — equivalent to the old body.

    /**
     * Trimmed override: {@code super} now performs the owner forward (via
     * {@code BlockBCTileSupport.placeOwner}), but the base deliberately does <em>not</em>
     * {@code markForGuiUpdate}. That push is still needed here specifically, because
     * {@code GuiAssemblyTable} and {@code GuiAdvancedCraftingTable} are two of the few BC GUIs that
     * carry a {@code LedgerOwnership} — without it the owner only reaches the client on the first
     * power-change sync, so a GUI opened immediately after placement reads "Unknown". No-re-mesh
     * push: owner is GUI-only data, the placement itself already drew the block.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof TileLaserTableBase table) {
            table.markForGuiUpdate();
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileLaserTableBase table) {
                NonNullList<ItemStack> drops = NonNullList.create();
                table.addDrops(drops, 0);
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        Block.popResource(level, pos, drop);
                    }
                }
                table.markDropsHandled();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // The <1.21.10 non-player-removal catch-all is inherited from BlockBCTile_Neptune
    // (>=1.21.10 uses TileBC_Neptune#preRemoveSideEffects).
}
