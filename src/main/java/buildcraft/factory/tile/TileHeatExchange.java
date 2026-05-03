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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
import buildcraft.lib.fluid.FluidSmoother;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Heat Exchanger tile entity. Manages the multi-block structure logic,
 * fluid exchange between heatable and coolable fluids, and client-side
 * rendering state. Ported from 1.12.2 TileHeatExchange.
 */
public class TileHeatExchange extends BlockEntity implements MenuProvider, IDebuggable {

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
     * dropped on block break by {@code BlockHeatExchange.playerWillDestroy}.
     */
    public final ItemHandlerSimple containerSlots = new ItemHandlerSimple(4, 1);

    {
        containerSlots.setCallback((handler, slot, bef, aft) -> setChanged());
    }

    public TileHeatExchange(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.HEAT_EXCHANGE.get(), pos, state);
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
    public FluidStacksResourceHandler getFluidTankForDirection(@Nullable Direction direction) {
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
            } else if (exchangers.size() < 3) {
                for (TileHeatExchange tile : exchangers) {
                    tile.removeSection();
                }
            } else if (exchangers.size() <= 5) {
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
            // Link start and end sections on the client — retry until successful
            if (exchangers.size() > 2) {
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
            level.setBlock(worldPosition, getBlockState().setValue(BlockHeatExchange.FACING, next),
                    Block.UPDATE_ALL);
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
                        exchange.getBlockState().setValue(BlockHeatExchange.FACING, thisFacing.getOpposite()),
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

    private int computeSyncHash() {
        if (section == null) return 0;
        int h = section instanceof ExchangeSectionStart ? 1 : 2;
        h = h * 31 + section.tankInput.getAmountAsInt(0);
        h = h * 31 + section.tankOutput.getAmountAsInt(0);
        if (section instanceof ExchangeSectionStart s) {
            h = h * 31 + s.progressState.ordinal();
            h = h * 31 + s.middleCount;
        }
        return h;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("containerSlots", CompoundTag.CODEC, containerSlots.serializeNBT());
        if (section != null) {
            output.putBoolean("hasSection", true);
            output.putBoolean("isStart", section instanceof ExchangeSectionStart);
            FluidStack inStack = section.tankInput.getResource(0).toStack(section.tankInput.getAmountAsInt(0));
            if (!inStack.isEmpty()) {
                output.store("sectionInput", FluidStack.CODEC, inStack);
            }
            FluidStack outStack = section.tankOutput.getResource(0).toStack(section.tankOutput.getAmountAsInt(0));
            if (!outStack.isEmpty()) {
                output.store("sectionOutput", FluidStack.CODEC, outStack);
            }
            if (section instanceof ExchangeSectionStart s) {
                output.putInt("middleCount", s.middleCount);
                output.putInt("progress", s.progress);
                output.putInt("progressState", s.progressState.ordinal());
                output.putInt("coolantCharge", s.inputCoolantAmountCharge);
                output.putInt("heatantCharge", s.inputHeatantAmountCharge);
            }
        } else {
            output.putBoolean("hasSection", false);
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
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
                FluidStack inFluid = input.read("sectionInput", FluidStack.CODEC).orElse(FluidStack.EMPTY);
                if (!inFluid.isEmpty()) {
                    s.tankInput.set(0, FluidResource.of(inFluid), inFluid.getAmount());
                }
                FluidStack outFluid = input.read("sectionOutput", FluidStack.CODEC).orElse(FluidStack.EMPTY);
                if (!outFluid.isEmpty()) {
                    s.tankOutput.set(0, FluidResource.of(outFluid), outFluid.getAmount());
                }
                s.middleCount = input.getIntOr("middleCount", 1);
                // Only sync progressState — progress is computed independently on the
                // client via updateProgress(), matching 1.12.2 behavior. Loading the server's
                // progress value would cause jitter due to network latency.
                int stateOrd = input.getIntOr("progressState", 0);
                s.progressState = EnumProgressState.values()[Math.min(stateOrd, EnumProgressState.values().length - 1)];
                s.inputCoolantAmountCharge = input.getIntOr("coolantCharge", 0);
                s.inputHeatantAmountCharge = input.getIntOr("heatantCharge", 0);
                section = s;
            } else {
                // Reuse existing section to preserve FluidSmoother state
                ExchangeSectionEnd e;
                if (section instanceof ExchangeSectionEnd existing) {
                    e = existing;
                } else {
                    e = new ExchangeSectionEnd(this);
                }
                FluidStack inFluid = input.read("sectionInput", FluidStack.CODEC).orElse(FluidStack.EMPTY);
                if (!inFluid.isEmpty()) {
                    e.tankInput.set(0, FluidResource.of(inFluid), inFluid.getAmount());
                }
                FluidStack outFluid = input.read("sectionOutput", FluidStack.CODEC).orElse(FluidStack.EMPTY);
                if (!outFluid.isEmpty()) {
                    e.tankOutput.set(0, FluidResource.of(outFluid), outFluid.getAmount());
                }
                section = e;
            }
        } else if (section != null) {
            section = null;
        }
        checkNeighbours = true;
    }

    // --- Network Sync ---

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

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
        public final FluidStacksResourceHandler tankInput;
        public final OutputTank tankOutput;
        public final FluidSmoother smoothedTankInput, smoothedTankOutput;
        private TileHeatExchange tile;

        ExchangeSection(TileHeatExchange tile, Predicate<FluidStack> inputFilter) {
            tankInput = new FluidStacksResourceHandler(1, 2000) {
                @Override
                public boolean isValid(int index, FluidResource resource) {
                    return inputFilter.test(resource.toStack(1));
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
            FluidStack inStack = tankInput.getResource(0).toStack(tankInput.getAmountAsInt(0));
            FluidStack outStack = tankOutput.getResource(0).toStack(tankOutput.getAmountAsInt(0));
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
        ResourceHandler<FluidResource> getFluidAutoOutputTarget() {
            return null;
        }
    }

    /**
     * Output tank that rejects all external insertions — matches 1.12.2's
     * {@code tankOutput.setCanFill(false)}. The exchanger's craft() logic
     * fills via {@link #insertInternal} which bypasses the {@code isValid}
     * gate. External callers (capability access, bucket right-clicks, GUI
     * tank widget clicks) all funnel through {@code insert}, which checks
     * {@code isValid} and so is blocked.
     */
    public static class OutputTank extends FluidStacksResourceHandler {
        private boolean internalInsert = false;

        public OutputTank() {
            super(1, 2000);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return internalInsert;
        }

        public int insertInternal(int index, FluidResource resource, int amount, TransactionContext tx) {
            internalInsert = true;
            try {
                return super.insert(index, resource, amount, tx);
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
        int inputCoolantAmountCharge = 0;
        int inputHeatantAmountCharge = 0;

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
        private static boolean isHeatant(FluidStack fluid) {
            IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
            if (manager == null) return false;
            return manager.getHeatableRegistry().getRecipeForInput(fluid) != null;
        }

        @Nullable
        @Override
        ResourceHandler<FluidResource> getFluidAutoOutputTarget() {
            Direction facing = getTile().getFacing();
            if (facing == null || getTile().level == null) return null;
            BlockPos targetPos = getTile().worldPosition.relative(facing.getClockWise());
            return getTile().level.getCapability(
                    Capabilities.Fluid.BLOCK, targetPos, facing.getCounterClockWise());
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
        @SuppressWarnings("removal")
        private void processContainerSlots() {
            TileHeatExchange tile = getTile();
            if (tile == null || tile.level == null) return;
            if (tile.level.getGameTime() % 5 != 0) return;

            // Slot 0: filled bucket → END.tankInput (hot fluid)
            if (endSection != null) {
                drainSlotIntoTank(tile, 0, endSection.tankInput);
            }
            // Slot 1: filled bucket → START.tankInput (cold fluid)
            drainSlotIntoTank(tile, 1, this.tankInput);
            // Slot 2: empty bucket ← END.tankOutput (heated fluid)
            if (endSection != null) {
                fillSlotFromTank(tile, 2, endSection.tankOutput);
            }
            // Slot 3: empty bucket ← START.tankOutput (cooled fluid)
            fillSlotFromTank(tile, 3, this.tankOutput);
        }

        @SuppressWarnings("removal")
        private static void drainSlotIntoTank(TileHeatExchange tile, int slot, FluidStacksResourceHandler tank) {
            ItemStack stack = tile.containerSlots.getResource(slot)
                    .toStack(tile.containerSlots.getAmountAsInt(slot));
            if (stack.isEmpty()) return;
            net.neoforged.neoforge.fluids.FluidActionResult result =
                    net.neoforged.neoforge.fluids.FluidUtil.tryEmptyContainer(
                            stack,
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tank),
                            Integer.MAX_VALUE, null, true);
            if (result.isSuccess()) {
                tile.containerSlots.setStackInSlot(slot, result.getResult());
            }
        }

        @SuppressWarnings("removal")
        private static void fillSlotFromTank(TileHeatExchange tile, int slot, FluidStacksResourceHandler tank) {
            ItemStack stack = tile.containerSlots.getResource(slot)
                    .toStack(tile.containerSlots.getAmountAsInt(slot));
            if (stack.isEmpty()) return;
            net.neoforged.neoforge.fluids.FluidActionResult result =
                    net.neoforged.neoforge.fluids.FluidUtil.tryFillContainer(
                            stack,
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tank),
                            Integer.MAX_VALUE, null, true);
            if (result.isSuccess()) {
                tile.containerSlots.setStackInSlot(slot, result.getResult());
            }
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
            FluidStacksResourceHandler c_in = endSection.tankInput;
            OutputTank c_out = tankOutput;
            FluidStacksResourceHandler h_in = tankInput;
            OutputTank h_out = endSection.tankOutput;
            IRefineryRecipeManager reg = BuildcraftRecipeRegistry.refineryRecipes;
            if (reg == null) {
                progressState = EnumProgressState.STOPPING;
                return;
            }
            FluidStack c_in_fluid = c_in.getResource(0).toStack(c_in.getAmountAsInt(0));
            FluidStack h_in_fluid = h_in.getResource(0).toStack(h_in.getAmountAsInt(0));
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
            FluidStack c_in_f = setAmount(c_recipe.in(), max_amount);
            FluidStack c_out_f = setAmount(c_recipe.out(), max_amount);
            FluidStack h_in_f = setAmount(h_recipe.in(), max_amount);
            FluidStack h_out_f = setAmount(h_recipe.out(), max_amount);

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
                    fillTank(c_out, c_out_f);
                    drainTank(c_in, c_in_f);
                    fillTank(h_out, h_out_f);
                    drainTank(h_in, h_in_f);
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
            FluidStack c_in_f = end.tankInput.getResource(0).toStack(end.tankInput.getAmountAsInt(0));
            // If coolant is lava, spew smoke from start side
            if (!c_in_f.isEmpty() && c_in_f.getFluid() == Fluids.LAVA) {
                Direction facing = getTile().getFacing();
                if (facing != null) {
                    spewForth(from, facing.getClockWise(), true);
                }
            }

            FluidStack h_in_f = tankInput.getResource(0).toStack(tankInput.getAmountAsInt(0));
            from = Vec3.atCenterOf(end.getTile().getBlockPos());
            // If heatant is water, spew steam from end top
            if (!h_in_f.isEmpty() && h_in_f.getFluid() == Fluids.WATER) {
                spewForth(from, Direction.UP, false);
            }
        }

        private void spewForth(Vec3 from, Direction dir, boolean smoke) {
            Level w = getTile().getLevel();
            if (w == null) return;
            Vec3 vecDir = Vec3.atLowerCornerOf(dir.getUnitVec3i());
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
            ResourceHandler<FluidResource> thisOut = getFluidAutoOutputTarget();
            if (thisOut != null) {
                moveFluid(tankOutput, thisOut, 1000);
            }
            if (endSection != null) {
                ResourceHandler<FluidResource> endOut = endSection.getFluidAutoOutputTarget();
                if (endOut != null) {
                    moveFluid(endSection.tankOutput, endOut, 1000);
                }
            }
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
        private static FluidStack setAmount(@Nullable FluidStack fluid, int amount) {
            if (fluid == null || fluid.isEmpty()) return null;
            return fluid.copyWithAmount(amount);
        }

        private static int simulateExtract(FluidStacksResourceHandler t, @Nullable FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return 0;
            try (Transaction tx = Transaction.openRoot()) {
                return t.extract(0, FluidResource.of(fluid), fluid.getAmount(), tx);
            }
        }

        private static int simulateInsert(OutputTank t, @Nullable FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return 0;
            try (Transaction tx = Transaction.openRoot()) {
                return t.insertInternal(0, FluidResource.of(fluid), fluid.getAmount(), tx);
            }
        }

        private static void fillTank(OutputTank t, @Nullable FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return;
            try (Transaction tx = Transaction.openRoot()) {
                t.insertInternal(0, FluidResource.of(fluid), fluid.getAmount(), tx);
                tx.commit();
            }
        }

        private static void drainTank(FluidStacksResourceHandler t, @Nullable FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return;
            try (Transaction tx = Transaction.openRoot()) {
                t.extract(0, FluidResource.of(fluid), fluid.getAmount(), tx);
                tx.commit();
            }
        }

        private static void moveFluid(FluidStacksResourceHandler from, ResourceHandler<FluidResource> to, int maxAmount) {
            try (Transaction tx = Transaction.openRoot()) {
                int moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.move(
                    from, to, r -> true, maxAmount, tx
                );
                if (moved > 0) tx.commit();
            }
        }
    }

    public static class ExchangeSectionEnd extends ExchangeSection {
        ExchangeSectionEnd(TileHeatExchange tile) {
            super(tile, ExchangeSectionEnd::isCoolant);
        }

        /** Coolant filter: lava, hot oil, hot fuel — fluids that get cooled by the END's heatant counterpart. */
        private static boolean isCoolant(FluidStack fluid) {
            IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
            if (manager == null) return false;
            return manager.getCoolableRegistry().getRecipeForInput(fluid) != null;
        }

        @Nullable
        @Override
        ResourceHandler<FluidResource> getFluidAutoOutputTarget() {
            if (getTile().level == null) return null;
            return getTile().level.getCapability(
                    Capabilities.Fluid.BLOCK,
                    getTile().worldPosition.above(), Direction.DOWN);
        }
    }
}
