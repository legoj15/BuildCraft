/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.transfer.FluidStorageUtil;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.TransferAccess;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockHeatExchange.EnumExchangePart;
import buildcraft.factory.container.ContainerHeatExchange;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.fluid.FluidSmoother;
import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.tile.AbstractBCSyncedBlockEntity;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Heat Exchanger tile entity. Manages the multi-block structure logic,
 * fluid exchange between heatable and coolable fluids, and client-side
 * rendering state. Ported from 1.12.2 TileHeatExchange.
 */
@SuppressWarnings("this-escape")
public class TileHeatExchange extends AbstractBCSyncedBlockEntity implements IBCMenuProvider, IDebuggable {

    /** Maximum fluid transfer per tick for each number of middle sections (1-3 middles).
     * Numbers should be divisors of 1000. */
    private static final int[] FLUID_MULT = { 5, 10, 20 };

    protected ExchangeSection section;
    private boolean checkNeighbours;

    // Sync tracking
    private int lastSyncHash = 0;

    /**
     * Bucket / fluid-shard slots, used by the START tile to drain filled containers
     * into the input tanks and fill empty containers from the output tanks.
     * <ul>
     *   <li>0: drains into END.tankInput (the multi-block's hot/coolable input)</li>
     *   <li>1: drains into START.tankInput (the cold/heatable input)</li>
     *   <li>2: fills from END.tankOutput (the heated output)</li>
     *   <li>3: fills from START.tankOutput (the cooled output)</li>
     * </ul>
     * Present on every tile (NBT-persistent) but only ticked when {@link #isStart()};
     * spilled on any removal by {@link #dropContentsOnRemoval} (the removal hook itself is
     * {@code AbstractBCSyncedBlockEntity}'s).
     */
    public final ItemHandlerSimple containerSlots = new ItemHandlerSimple(4, 1);

    {
        containerSlots.setCallback((handler, slot, bef, aft) -> setChanged());
    }

    public TileHeatExchange(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.HEAT_EXCHANGE.get(), pos, state);
    }

    // --- Removal drops (every removal: player break, explosion, piston, /setblock ... destroy) ---
    // The removal hook is AbstractBCSyncedBlockEntity's; this is just what this tile holds. Spills the section
    // tanks (START/END tiles only) as fragile shards plus the container slots, then empties them, which keeps a
    // second call from dropping them again (BlockHeatExchange drops nothing in playerWillDestroy: a player break
    // reaches the hook too). A command REPLACE wipes the same set (clearContent below).

    @Override
    public void dropContentsOnRemoval(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        ExchangeSection section = getSection();
        if (section != null) {
            buildcraft.lib.misc.BlockDropsUtil.dropFluidShards(level, pos, section.tankInput, section.tankOutput);
        }
        buildcraft.lib.misc.BlockDropsUtil.dropItems(level, pos, containerSlots);
        clearContent();
    }

    /** Wipes exactly what {@link #dropContentsOnRemoval} would spill: the section tanks (when this tile owns
     *  a section) and the container slots; see {@code AbstractBCBlockEntity#clearContent}. */
    @Override
    public void clearContent() {
        ExchangeSection section = getSection();
        if (section != null) {
            section.tankInput.setFluidStack(0, BCFluidStack.EMPTY);
            section.tankOutput.setFluidStack(0, BCFluidStack.EMPTY);
        }
        containerSlots.clearStacks();
    }

    // --- Section accessors ---

    public boolean isStart() {
        return section instanceof ExchangeSectionStart;
    }

    public boolean isEnd() {
        return section instanceof ExchangeSectionEnd;
    }

    @Nullable
    public ExchangeSection getSection() {
        return section;
    }

    /**
     * Returns the appropriate fluid tank for the given direction, or null if
     * fluid connections are not allowed on that face. Matches the 1.12.2 behavior:
     * <ul>
     *   <li>START: tankInput on DOWN, tankOutput on facing.getClockWise()</li>
     *   <li>END: tankOutput on UP, tankInput on facing.getCounterClockWise()</li>
     *   <li>MIDDLE / no section: null (no connections)</li>
     * </ul>
     */
    @Nullable
    public BCFluidTank getFluidTankForDirection(@Nullable Direction direction) {
        if (section == null || direction == null) return null;
        Direction facing = getFacing();
        if (facing == null) return null;

        if (section instanceof ExchangeSectionStart) {
            if (direction == Direction.DOWN) {
                return section.tankInput;
            }
            if (direction == facing.getClockWise()) {
                return section.tankOutput;
            }
        } else if (section instanceof ExchangeSectionEnd) {
            if (direction == Direction.UP) {
                return section.tankOutput;
            }
            if (direction == facing.getCounterClockWise()) {
                return section.tankInput;
            }
        }
        return null;
    }

    public void markCheckNeighbours() {
        checkNeighbours = true;
    }

    /**
     * Walks the multi-block (clockwise from this tile, the direction towards the
     * START section) and returns the START tile, or {@code null} if no start is
     * adjacent or the multi-block isn't formed. Used by the GUI to always open
     * a menu pointing at the section that owns the recipe state and end-section link.
     */
    @Nullable
    public TileHeatExchange findStart() {
        if (isStart()) return this;
        if (level == null) return null;
        Direction facing = getFacing();
        if (facing == null) return null;
        Direction dirToStart = facing.getClockWise();
        for (int i = 1; i < 6; i++) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dirToStart, i));
            if (neighbour instanceof TileHeatExchange other) {
                if (other.getFacing() != facing) {
                    return null;
                }
                if (other.isStart()) {
                    return other;
                }
            } else {
                return null;
            }
        }
        return null;
    }

    @Nullable
    Direction getFacing() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof BlockHeatExchange) {
            return state.getValue(BlockHeatExchange.FACING);
        }
        return null;
    }

    // --- Ticking ---

    public void serverTick() {
        if (level == null) return;
        if (checkNeighbours) {
            checkNeighbours = false;
            Deque<TileHeatExchange> exchangers = findAdjacentExchangers();
            if (exchangers.isEmpty()) {
                checkNeighbours = true;
            } else if (exchangers.size() < 3 || exchangers.size() > 5) {
                // Too short or too long to form: dissolve any section left from a valid length
                for (TileHeatExchange tile : exchangers) {
                    tile.removeSection();
                }
            } else {
                ExchangeSectionStart sectionStart = null;
                ExchangeSectionEnd sectionEnd = null;
                for (TileHeatExchange exchange : exchangers) {
                    exchange.checkNeighbours = false;
                    if (exchange.section instanceof ExchangeSectionStart existingStart) {
                        if (sectionStart == null) {
                            sectionStart = existingStart;
                        }
                    } else if (exchange.section instanceof ExchangeSectionEnd existingEnd) {
                        if (sectionEnd == null) {
                            sectionEnd = existingEnd;
                        }
                    }
                    exchange.section = null;
                }
                if (sectionStart == null) {
                    sectionStart = new ExchangeSectionStart(exchangers.getFirst());
                }
                if (sectionEnd == null) {
                    sectionEnd = new ExchangeSectionEnd(exchangers.getLast());
                }
                sectionStart.endSection = sectionEnd;
                sectionStart.middleCount = exchangers.size() - 2;
                exchangers.getFirst().setSection(sectionStart);
                exchangers.getLast().setSection(sectionEnd);
                // Update part properties for all tiles
                updatePartProperties(exchangers);
                // Sync all tiles
                for (TileHeatExchange exchange : exchangers) {
                    exchange.syncToClient();
                }
            }
        }
        if (section != null) {
            section.tick();
        }
        // Sync if anything changed
        int hash = computeSyncHash();
        if (hash != lastSyncHash) {
            lastSyncHash = hash;
            syncToClient();
        }
    }

    public void clientTick() {
        if (level == null) return;
        if (checkNeighbours) {
            Deque<TileHeatExchange> exchangers = findAdjacentExchangers();
            if (exchangers.size() < 3 || exchangers.size() > 5) {
                // Can never form; a later server sync sets checkNeighbours again
                checkNeighbours = false;
            } else {
                // Link start and end sections on the client — retry until successful
                TileHeatExchange start = exchangers.getFirst();
                TileHeatExchange end = exchangers.getLast();
                if (start.isStart() && end.isEnd()) {
                    ((ExchangeSectionStart) start.section).endSection = (ExchangeSectionEnd) end.section;
                    checkNeighbours = false;
                }
            }
        }
        if (section != null) {
            section.clientTick();
        }
    }

    private void removeSection() {
        if (section == null) return;
        // No block is removed here, so the removal hook never sees these tanks: spill them now (1.12.2 did too)
        if (level != null) {
            buildcraft.lib.misc.BlockDropsUtil.dropFluidShards(level, worldPosition, section.tankInput, section.tankOutput);
        }
        section = null;
        // Reset block state to MIDDLE when the multiblock is invalidated
        if (level != null) {
            BlockState oldState = getBlockState();
            if (oldState.getBlock() instanceof BlockHeatExchange) {
                BlockState newState = oldState.setValue(BlockHeatExchange.PART, EnumExchangePart.MIDDLE);
                if (oldState != newState) {
                    level.setBlock(worldPosition, newState, Block.UPDATE_ALL);
                }
            }
        }
        syncToClient();
    }

    private void setSection(ExchangeSection section) {
        if (this.section != section) {
            this.section = section;
            section.setTile(this);
            syncToClient();
        }
    }

    private void updatePartProperties(Deque<TileHeatExchange> exchangers) {
        if (level == null) return;
        TileHeatExchange[] arr = exchangers.toArray(new TileHeatExchange[0]);
        for (int i = 0; i < arr.length; i++) {
            TileHeatExchange tile = arr[i];
            EnumExchangePart part;
            if (i == 0) {
                part = EnumExchangePart.START;
            } else if (i == arr.length - 1) {
                part = EnumExchangePart.END;
            } else {
                part = EnumExchangePart.MIDDLE;
            }
            BlockState oldState = tile.getBlockState();
            if (oldState.getBlock() instanceof BlockHeatExchange) {
                BlockState newState = oldState.setValue(BlockHeatExchange.PART, part);
                if (oldState != newState) {
                    level.setBlock(tile.worldPosition, newState, Block.UPDATE_ALL);
                }
            }
        }
    }

    private Deque<TileHeatExchange> findAdjacentExchangers() {
        Direction thisFacing = getFacing();
        if (thisFacing == null) {
            return new ArrayDeque<>();
        }
        Direction dirToStart = thisFacing.getClockWise();
        Direction dirToEnd = thisFacing.getCounterClockWise();
        Deque<TileHeatExchange> exchangers = new ArrayDeque<>();
        exchangers.add(this);
        for (int i = 1; i < 6; i++) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dirToStart, i));
            if (neighbour instanceof TileHeatExchange other) {
                if (other.getFacing() != thisFacing) {
                    break;
                }
                exchangers.addFirst(other);
            } else {
                break;
            }
        }
        for (int i = 1; i < 6; i++) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dirToEnd, i));
            if (neighbour instanceof TileHeatExchange other) {
                if (other.getFacing() != thisFacing) {
                    break;
                }
                exchangers.addLast(other);
            } else {
                break;
            }
        }
        return exchangers;
    }

    /** Called by Block.rotateBlock equivalent and wrench interaction.
     * If single tile: rotate 90°. If multi-block: rotate 180° to swap start↔end. */
    public boolean rotate() {
        Direction thisFacing = getFacing();
        if (thisFacing == null || level == null) return false;
        Deque<TileHeatExchange> exchangers = findAdjacentExchangers();
        if (exchangers.size() == 1) {
            // Just this one, rotate 90°
            Direction[] horizontals = { Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST };
            int idx = 0;
            for (int i = 0; i < horizontals.length; i++) {
                if (horizontals[i] == thisFacing) {
                    idx = i;
                    break;
                }
            }
            Direction next = horizontals[(idx + 1) % 4];
            level.setBlock(worldPosition, withFacing(getBlockState(), worldPosition, next), Block.UPDATE_ALL);
        } else {
            // Rotate 180° — swap start and end
            ExchangeSectionStart start = null;
            ExchangeSectionEnd end = null;
            for (TileHeatExchange exchange : exchangers) {
                if (exchange.section instanceof ExchangeSectionStart s) {
                    start = s;
                } else if (exchange.section instanceof ExchangeSectionEnd e) {
                    end = e;
                }
                exchange.section = null;
                level.setBlock(exchange.worldPosition,
                        withFacing(exchange.getBlockState(), exchange.worldPosition, thisFacing.getOpposite()),
                        Block.UPDATE_ALL);
                exchange.checkNeighbours = true;
                exchange.setChanged();
            }
            if (start != null) {
                TileHeatExchange tile = exchangers.getLast();
                tile.section = start;
                start.setTile(tile);
                tile.setChanged();
                tile.syncToClient();
            }
            if (end != null) {
                TileHeatExchange tile = exchangers.getFirst();
                tile.section = end;
                end.setTile(tile);
                tile.setChanged();
                tile.syncToClient();
            }
        }
        return true;
    }

    /** The block state turned to {@code facing}, with its connection flags recomputed for that facing rather than
     *  left to the neighbour-shape pass. */
    private BlockState withFacing(BlockState state, BlockPos pos, Direction facing) {
        return BlockHeatExchange.updateConnections(state.setValue(BlockHeatExchange.FACING, facing), level, pos, facing);
    }

    private int computeSyncHash() {
        if (section == null) return 0;
        int h = section instanceof ExchangeSectionStart ? 1 : 2;
        h = h * 31 + section.tankInput.getAmountMb(0);
        h = h * 31 + section.tankOutput.getAmountMb(0);
        if (section instanceof ExchangeSectionStart s) {
            h = h * 31 + s.progressState.ordinal();
            h = h * 31 + s.middleCount;
        }
        return h;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            setChanged();
            MessageUtil.sendUpdateToTrackingPlayers(this);
        }
    }

    // --- IDebuggable ---

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        if (section == null) {
            left.add("section = null");
        } else {
            left.add("section = " + (section instanceof ExchangeSectionStart ? "start" : "end"));
            section.getDebugInfo(left, right, side);
        }
    }

    @Override
    public void getClientDebugInfo(List<String> left, List<String> right, Direction side) {
        if (section == null) {
            left.add("section = null");
        } else {
            left.add("section = " + (section instanceof ExchangeSectionStart ? "start" : "end"));
            section.getClientDebugInfo(left, right, side);
        }
    }

    // Note: Render bounding box is handled by RenderHeatExchange.shouldRender()
    // and getRenderBoundingBox() is not available on BlockEntity in 1.21.11.

    // --- Lifecycle ---

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (section instanceof ExchangeSectionStart s) {
            s.endSection = null;
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        checkNeighbours = true;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.heat_exchange");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerHeatExchange(containerId, playerInv, this);
    }

    // --- Save / Load ---

    // The saveAdditional/loadAdditional signature directive lives once in AbstractBCBlockEntity;
    // here we only override the version-neutral writeData/readData hooks it dispatches to.

    // Owner is deliberately NOT persisted here — see the same note on TileTank#writeData. The block
    // records a placing owner since the BlockBCTile_Neptune migration, but nothing reads it
    // (GuiHeatExchange carries no LedgerOwnership), so it is memory-only. Add the OwnerData calls if
    // an ownership ledger is ever added to this GUI.
    protected void writeData(BCValueOutput output) {
        output.store("containerSlots", CompoundTag.CODEC, containerSlots.serializeNBT());
        if (section != null) {
            output.putBoolean("hasSection", true);
            output.putBoolean("isStart", section instanceof ExchangeSectionStart);
            BCFluidStack inStack = section.tankInput.getFluidStack(0);
            if (!inStack.isEmpty()) {
                output.store("sectionInput", BCFluidStack.CODEC, inStack);
            }
            BCFluidStack outStack = section.tankOutput.getFluidStack(0);
            if (!outStack.isEmpty()) {
                output.store("sectionOutput", BCFluidStack.CODEC, outStack);
            }
            if (section instanceof ExchangeSectionStart s) {
                output.putInt("middleCount", s.middleCount);
                output.putInt("progress", s.progress);
                output.putInt("progressState", s.progressState.ordinal());
            }
        } else {
            output.putBoolean("hasSection", false);
        }
    }

    protected void readData(BCValueInput input) {
        containerSlots.deserializeNBT(input.read("containerSlots", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        if (input.getBooleanOr("hasSection", false)) {
            boolean isStart = input.getBooleanOr("isStart", true);
            if (isStart) {
                // Reuse existing section to preserve FluidSmoother state
                ExchangeSectionStart s;
                if (section instanceof ExchangeSectionStart existing) {
                    s = existing;
                } else {
                    s = new ExchangeSectionStart(this);
                }
                // Use set() to bypass the heatant/output isValid filters — saved fluid is trusted,
                // and the recipe registry may not be ready during early chunk load.
                loadTank(s.tankInput, input, "sectionInput");
                loadTank(s.tankOutput, input, "sectionOutput");
                s.middleCount = input.getIntOr("middleCount", 1);
                // Only sync progressState — progress is computed independently on the
                // client via updateProgress(), matching 1.12.2 behavior. Loading the server's
                // progress value would cause jitter due to network latency.
                int stateOrd = input.getIntOr("progressState", 0);
                // Full-range clamp: a negative count (corrupt or hand-edited save) must not index before the array.
                s.progressState = EnumProgressState.values()[MathUtil.clamp(stateOrd, 0, EnumProgressState.values().length - 1)];
                section = s;
            } else {
                // Reuse existing section to preserve FluidSmoother state
                ExchangeSectionEnd e;
                if (section instanceof ExchangeSectionEnd existing) {
                    e = existing;
                } else {
                    e = new ExchangeSectionEnd(this);
                }
                loadTank(e.tankInput, input, "sectionInput");
                loadTank(e.tankOutput, input, "sectionOutput");
                section = e;
            }
        } else if (section != null) {
            section = null;
        }
        checkNeighbours = true;
    }

    /**
     * Reads a fluid stack from {@code input} under {@code key} and writes it directly into
     * {@code tank} via {@link BCFluidTank#setFluidStack}. <em>Always</em> writes — when
     * the key is absent (saveAdditional only stores non-empty tanks) the tank is forced to
     * empty. Without this, a client whose tank previously held fluid would never see a server
     * drain reflected: the {@code getUpdateTag} → {@code loadAdditional} round-trip omits the
     * key for an empty server-side tank, and the old guard {@code if (!fluid.isEmpty())} would
     * leave the stale client value in place forever.
     */
    private static void loadTank(BCFluidTank tank, BCValueInput input, String key) {
        BCFluidStack fluid = input.read(key, BCFluidStack.CODEC).orElse(BCFluidStack.EMPTY);
        tank.setFluidStack(0, fluid);
    }

    // --- Network Sync ---
    // The getUpdateTag/getUpdatePacket pair (+ the <1.21.10 onDataPacket fix) lives once in
    // AbstractBCSyncedBlockEntity, which this class extends.

    // ========================================================================
    // Inner classes
    // ========================================================================

    public enum EnumProgressState {
        /** Progress is at 0, not moving. */
        OFF,
        /** Progress is increasing from 0 to max */
        PREPARING,
        /** Progress stays at max */
        RUNNING,
        /** Progress is decreasing from max to 0. */
        STOPPING
    }

    public static abstract class ExchangeSection {
        public final BCFluidTank tankInput;
        public final OutputTank tankOutput;
        public final FluidSmoother smoothedTankInput, smoothedTankOutput;
        private TileHeatExchange tile;

        ExchangeSection(TileHeatExchange tile, Predicate<BCFluidStack> inputFilter) {
            tankInput = new BCFluidTank(1, 2000) {
                @Override
                protected boolean isFluidValid(BCFluidStack stack) {
                    return inputFilter.test(stack);
                }
            };
            tankOutput = new OutputTank();
            smoothedTankInput = new FluidSmoother(tankInput);
            smoothedTankOutput = new FluidSmoother(tankOutput);
            this.tile = tile;
        }

        void tick() {
            // Server-side — no smoothing needed
        }

        void clientTick() {
            smoothedTankInput.tick();
            smoothedTankOutput.tick();
        }

        void getDebugInfo(List<String> left, List<String> right, Direction side) {
            BCFluidStack inStack = tankInput.getFluidStack(0);
            BCFluidStack outStack = tankOutput.getFluidStack(0);
            left.add("tank_input = " + FluidUtilBC.getDebugString(inStack));
            left.add("tank_output = " + FluidUtilBC.getDebugString(outStack));
        }

        void getClientDebugInfo(List<String> left, List<String> right, Direction side) {
            smoothedTankInput.getDebugInfo(left, right, side);
            smoothedTankOutput.getDebugInfo(left, right, side);
        }

        public TileHeatExchange getTile() {
            return tile;
        }

        public void setTile(TileHeatExchange tile) {
            this.tile = tile;
        }

        @Nullable
        BCFluidStorage getFluidAutoOutputTarget() {
            return null;
        }
    }

    /**
     * Output tank that rejects all external insertions — matches 1.12.2's
     * {@code tankOutput.setCanFill(false)}. The exchanger's craft() logic
     * fills via {@link #fillInternal} which bypasses the {@code isFluidValid}
     * gate. External callers (capability access, bucket right-clicks, GUI
     * tank widget clicks) all funnel through {@code fill}, which checks
     * {@code isFluidValid} and so is blocked.
     */
    public static class OutputTank extends BCFluidTank {
        private boolean internalInsert = false;

        public OutputTank() {
            super(1, 2000);
        }

        @Override
        protected boolean isFluidValid(BCFluidStack stack) {
            return internalInsert;
        }

        /**
         * Fills this output tank, bypassing the {@link #isFluidValid} gate that
         * blocks all external insertions. Flips {@code internalInsert} so the
         * underlying tank validator accepts the stack for the duration of the
         * {@code fill}, then restores it. {@code simulate} forwards to
         * {@link BCFluidTank#fill(int, BCFluidStack, boolean)} (true = test only).
         */
        public int fillInternal(BCFluidStack stack, boolean simulate) {
            internalInsert = true;
            try {
                return fill(0, stack, simulate);
            } finally {
                internalInsert = false;
            }
        }

        /** The transactional internal insert, so the shared-transaction atomicity/rollback unit tests
         *  (HeatExchangerTester) keep exercising it. Production craft() uses {@link #fillInternal}. */
        public int insertInternal(int index, BCFluidStack resource, int amount, BCTransactionContext tx) {
            internalInsert = true;
            try {
                return insert(index, resource, amount, tx);
            } finally {
                internalInsert = false;
            }
        }
    }

    public static class ExchangeSectionStart extends ExchangeSection {
        ExchangeSectionEnd endSection;
        public int middleCount;
        int progress = 0;
        int progressLast = 0;
        EnumProgressState progressState = EnumProgressState.OFF;

        ExchangeSectionStart(TileHeatExchange tile) {
            super(tile, ExchangeSectionStart::isHeatant);
        }

        public ExchangeSectionEnd getEndSection() {
            return endSection;
        }

        public EnumProgressState getProgressState() {
            return progressState;
        }

        public double getProgress(float partialTicks) {
            return (progressLast + (progress - progressLast) * partialTicks) / 120.0;
        }

        /** Heatant filter: water, oil, fuel — fluids that get heated by the START's coolant counterpart. */
        private static boolean isHeatant(BCFluidStack fluid) {
            IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
            if (manager == null) return false;
            return manager.getHeatableRegistry().getRecipeForInput(fluid) != null;
        }

        @Nullable
        @Override
        BCFluidStorage getFluidAutoOutputTarget() {
            Direction facing = getTile().getFacing();
            if (facing == null || getTile().level == null) return null;
            BlockPos targetPos = getTile().worldPosition.relative(facing.getClockWise());
            return TransferAccess.findFluid(getTile().level, targetPos, facing.getCounterClockWise());
        }

        @Override
        void tick() {
            super.tick();
            updateProgress();
            if (getTile().level != null && !getTile().level.isClientSide()) {
                if (endSection != null) {
                    craft();
                } else if (progressState != EnumProgressState.OFF) {
                    progressState = EnumProgressState.STOPPING;
                }
                output();
                processContainerSlots();
            }
        }

        /**
         * Drains filled fluid containers in slots 0/1 into the input tanks and fills empty
         * containers in slots 2/3 from the output tanks. Runs every 5 game ticks to match
         * the Distiller's slot cadence. Slots whose corresponding tank is unavailable
         * (END not yet linked) are skipped — items just sit in the slot until the
         * structure completes.
         */
        private void processContainerSlots() {
            TileHeatExchange tile = getTile();
            if (tile == null || tile.level == null) return;
            if (tile.level.getGameTime() % 5 != 0) return;

            // Slot 0: filled bucket → END.tankInput (hot fluid)
            if (endSection != null) {
                FluidUtilBC.drainContainerSlot(tile.containerSlots, 0, endSection.tankInput);
            }
            // Slot 1: filled bucket → START.tankInput (cold fluid)
            FluidUtilBC.drainContainerSlot(tile.containerSlots, 1, this.tankInput);
            // Slot 2: empty bucket ← END.tankOutput (heated fluid)
            if (endSection != null) {
                FluidUtilBC.fillContainerSlot(tile.containerSlots, 2, endSection.tankOutput);
            }
            // Slot 3: empty bucket ← START.tankOutput (cooled fluid)
            FluidUtilBC.fillContainerSlot(tile.containerSlots, 3, this.tankOutput);
        }

        @Override
        void clientTick() {
            super.clientTick();
            updateProgress();
            spawnParticles();
        }

        private void updateProgress() {
            progressLast = progress;
            switch (progressState) {
                case STOPPING -> {
                    progress--;
                    if (progress <= 0) {
                        progress = 0;
                        progressState = EnumProgressState.OFF;
                    }
                }
                case PREPARING, RUNNING -> {
                    int lag = 120;
                    progress++;
                    if (progress >= lag) {
                        progress = lag;
                        progressState = EnumProgressState.RUNNING;
                    }
                }
                default -> {}
            }
        }

        private void craft() {
            if (endSection == null) return;
            BCFluidTank c_in = endSection.tankInput;
            OutputTank c_out = tankOutput;
            BCFluidTank h_in = tankInput;
            OutputTank h_out = endSection.tankOutput;
            IRefineryRecipeManager reg = BuildcraftRecipeRegistry.refineryRecipes;
            if (reg == null) {
                progressState = EnumProgressState.STOPPING;
                return;
            }
            BCFluidStack c_in_fluid = c_in.getFluidStack(0);
            BCFluidStack h_in_fluid = h_in.getFluidStack(0);
            ICoolableRecipe c_recipe = reg.getCoolableRegistry().getRecipeForInput(c_in_fluid);
            IHeatableRecipe h_recipe = reg.getHeatableRegistry().getRecipeForInput(h_in_fluid);
            if (h_recipe == null || c_recipe == null) {
                progressState = EnumProgressState.STOPPING;
                return;
            }
            if (c_recipe.heatFrom() <= h_recipe.heatFrom()) {
                progressState = EnumProgressState.STOPPING;
                return;
            }
            int c_diff = c_recipe.heatFrom() - c_recipe.heatTo();
            int h_diff = h_recipe.heatTo() - h_recipe.heatFrom();
            if (h_diff < 1 || c_diff < 1) {
                progressState = EnumProgressState.STOPPING;
                return;
            }

            int max_amount = FLUID_MULT[Math.min(middleCount - 1, FLUID_MULT.length - 1)];
            BCFluidStack c_in_f = setAmount(c_recipe.in(), max_amount);
            BCFluidStack c_out_f = setAmount(c_recipe.out(), max_amount);
            BCFluidStack h_in_f = setAmount(h_recipe.in(), max_amount);
            BCFluidStack h_out_f = setAmount(h_recipe.out(), max_amount);

            int c_out_amount = c_out_f == null || c_out_f.isEmpty()
                    ? max_amount
                    : simulateInsert(c_out, c_out_f);
            int h_out_amount = h_out_f == null || h_out_f.isEmpty()
                    ? max_amount
                    : simulateInsert(h_out, h_out_f);

            int c_in_amount = simulateExtract(c_in, c_in_f);
            int h_in_amount = simulateExtract(h_in, h_in_f);

            int min_common = Math.min(Math.min(c_out_amount, h_out_amount),
                    Math.min(c_in_amount, h_in_amount));

            if (min_common > 0) {
                c_in_f = setAmount(c_recipe.in(), min_common);
                c_out_f = setAmount(c_recipe.out(), min_common);
                h_in_f = setAmount(h_recipe.in(), min_common);
                h_out_f = setAmount(h_recipe.out(), min_common);

                if (progressState == EnumProgressState.OFF) {
                    progressState = EnumProgressState.PREPARING;
                } else if (progressState == EnumProgressState.RUNNING) {
                    // All four operations must succeed by exactly min_common, or none execute.
                    // Without atomicity, a partial failure (e.g. fill matches simulation but a
                    // later drain returns less) leaks fluid — which manifests as input residue
                    // when one cycle's fill+drain pair gets out of sync near tank capacity.
                    // The version-neutral tank API has no shared transaction, so emulate the
                    // commit by simulating all four first and only executing once all pass.
                    boolean ok = true;
                    if (c_out_f != null && !c_out_f.isEmpty()) {
                        ok = c_out.fillInternal(c_out_f, true) == c_out_f.getAmount();
                    }
                    if (ok && h_out_f != null && !h_out_f.isEmpty()) {
                        ok = h_out.fillInternal(h_out_f, true) == h_out_f.getAmount();
                    }
                    if (ok) {
                        ok = drainExact(c_in, c_in_f, true);
                    }
                    if (ok) {
                        ok = drainExact(h_in, h_in_f, true);
                    }
                    if (ok) {
                        // Simulations all matched min_common — execute for real. Each tank is
                        // independent and single-slot, so these mirror the simulated amounts.
                        if (c_out_f != null && !c_out_f.isEmpty()) {
                            c_out.fillInternal(c_out_f, false);
                        }
                        if (h_out_f != null && !h_out_f.isEmpty()) {
                            h_out.fillInternal(h_out_f, false);
                        }
                        drainExact(c_in, c_in_f, false);
                        drainExact(h_in, h_in_f, false);
                    }
                }
            } else {
                progressState = EnumProgressState.STOPPING;
            }
        }

        private void spawnParticles() {
            if (progressState != EnumProgressState.RUNNING) return;
            ExchangeSectionEnd end = endSection;
            if (end == null || getTile().level == null) return;

            Vec3 from = Vec3.atCenterOf(getTile().getBlockPos());
            BCFluidStack c_in_f = end.tankInput.getFluidStack(0);
            // If coolant is lava, spew smoke from start side
            if (!c_in_f.isEmpty() && c_in_f.getFluid() == Fluids.LAVA) {
                Direction facing = getTile().getFacing();
                if (facing != null) {
                    spewForth(from, facing.getClockWise(), true);
                }
            }

            BCFluidStack h_in_f = tankInput.getFluidStack(0);
            from = Vec3.atCenterOf(end.getTile().getBlockPos());
            // If heatant is water, spew steam from end top
            if (!h_in_f.isEmpty() && h_in_f.getFluid() == Fluids.WATER) {
                spewForth(from, Direction.UP, false);
            }
        }

        private void spewForth(Vec3 from, Direction dir, boolean smoke) {
            Level w = getTile().getLevel();
            if (w == null) return;
            Vec3 vecDir = Vec3.atLowerCornerOf(buildcraft.lib.misc.PositionUtil.getDirectionNormal(dir));
            from = from.add(vecDir);
            double x = from.x, y = from.y, z = from.z;
            Vec3 motion = vecDir.scale(0.4);
            for (int i = 0; i < 3; i++) {
                double dx = motion.x + (Math.random() - 0.5) * 0.1;
                double dy = motion.y + (Math.random() - 0.5) * 0.1;
                double dz = motion.z + (Math.random() - 0.5) * 0.1;
                w.addParticle(smoke ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                        x, y, z, dx, dy, dz);
            }
        }

        private void output() {
            //? if >=1.21.10 {
            // 1.21.1 never auto-outputs (it did not before the platform layer either).
            BCFluidStorage thisOut = getFluidAutoOutputTarget();
            if (thisOut != null) {
                moveFluid(tankOutput, thisOut, 1000);
            }
            if (endSection != null) {
                BCFluidStorage endOut = endSection.getFluidAutoOutputTarget();
                if (endOut != null) {
                    moveFluid(endSection.tankOutput, endOut, 1000);
                }
            }
            //?}
        }

        @Override
        void getDebugInfo(List<String> left, List<String> right, Direction side) {
            super.getDebugInfo(left, right, side);
            left.add("progress = " + progress);
            left.add("state = " + progressState);
            left.add("has_end = " + (endSection != null));
        }

        // --- Helpers ---

        @Nullable
        private static BCFluidStack setAmount(@Nullable BCFluidStack fluid, int amount) {
            if (fluid == null || fluid.isEmpty()) return null;
            return fluid.copyWithAmount(amount);
        }

        private static int simulateExtract(BCFluidTank t, @Nullable BCFluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return 0;
            // Only count toward min_common if the tank actually holds the recipe input fluid,
            // matching the old fluid-specific extract(FluidResource.of(fluid), …).
            BCFluidStack inTank = t.getFluidStack(0);
            if (inTank.isEmpty() || !BCFluidStack.isSameFluidSameComponents(inTank, fluid)) {
                return 0;
            }
            return t.drain(0, fluid.getAmount(), true).getAmount();
        }

        private static int simulateInsert(OutputTank t, @Nullable BCFluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return 0;
            return t.fillInternal(fluid, true);
        }

        /**
         * Drains exactly {@code fluid.getAmount()} of {@code fluid}'s type from {@code t}, returning
         * whether the full amount came out (and matched the requested fluid). Replaces the old
         * fluid-specific {@code extract(FluidResource.of(fluid), amount, tx)} — the version-neutral
         * {@link BCFluidTank#drain} is amount-only, so the fluid identity is checked here.
         */
        private static boolean drainExact(BCFluidTank t, @Nullable BCFluidStack fluid, boolean simulate) {
            if (fluid == null || fluid.isEmpty()) return true;
            BCFluidStack inTank = t.getFluidStack(0);
            if (inTank.isEmpty() || !BCFluidStack.isSameFluidSameComponents(inTank, fluid)) {
                return false;
            }
            return t.drain(0, fluid.getAmount(), simulate).getAmount() == fluid.getAmount();
        }

        private static void moveFluid(BCFluidTank from, BCFluidStorage to, int maxAmount) {
            FluidStorageUtil.move(from, to, r -> true, maxAmount, null);
        }
    }

    public static class ExchangeSectionEnd extends ExchangeSection {
        ExchangeSectionEnd(TileHeatExchange tile) {
            super(tile, ExchangeSectionEnd::isCoolant);
        }

        /** Coolant filter: lava, hot oil, hot fuel — fluids that get cooled by the END's heatant counterpart. */
        private static boolean isCoolant(BCFluidStack fluid) {
            IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
            if (manager == null) return false;
            return manager.getCoolableRegistry().getRecipeForInput(fluid) != null;
        }

        @Nullable
        @Override
        BCFluidStorage getFluidAutoOutputTarget() {
            if (getTile().level == null) return null;
            return TransferAccess.findFluid(getTile().level, getTile().worldPosition.above(), Direction.DOWN);
        }
    }
}
