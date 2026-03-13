/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockHeatExchange.EnumExchangePart;
import buildcraft.lib.fluid.FluidSmoother;
import buildcraft.lib.misc.FluidUtilBC;

/**
 * Heat Exchanger tile entity. Manages the multi-block structure logic,
 * fluid exchange between heatable and coolable fluids, and client-side
 * rendering state. Ported from 1.12.2 TileHeatExchange.
 */
public class TileHeatExchange extends BlockEntity implements IDebuggable {

    /** Maximum fluid transfer per tick for each number of middle sections (1-3 middles).
     * Numbers should be divisors of 1000. */
    private static final int[] FLUID_MULT = { 5, 10, 20 };

    protected ExchangeSection section;
    private boolean checkNeighbours;

    // Sync tracking
    private int lastSyncHash = 0;

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
        if (section != null) return section;
        Direction thisFacing = getFacing();
        if (thisFacing == null || level == null) return null;
        Direction dirToStart = thisFacing.getClockWise();
        for (int i = 1; i < 6; i++) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dirToStart, i));
            if (neighbour instanceof TileHeatExchange other) {
                if (other.section instanceof ExchangeSectionStart s) {
                    return s;
                }
            } else {
                break;
            }
        }
        return null;
    }

    public void markCheckNeighbours() {
        checkNeighbours = true;
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
            checkNeighbours = false;
            Deque<TileHeatExchange> exchangers = findAdjacentExchangers();
            // Link start and end sections on the client
            if (exchangers.size() > 2) {
                TileHeatExchange start = exchangers.getFirst();
                TileHeatExchange end = exchangers.getLast();
                if (start.isStart() && end.isEnd()) {
                    ((ExchangeSectionStart) start.section).endSection = (ExchangeSectionEnd) end.section;
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
        h = h * 31 + section.tankInput.getFluidAmount();
        h = h * 31 + section.tankOutput.getFluidAmount();
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

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (section != null) {
            output.putBoolean("hasSection", true);
            output.putBoolean("isStart", section instanceof ExchangeSectionStart);
            if (!section.tankInput.getFluid().isEmpty()) {
                output.store("sectionInput", FluidStack.CODEC, section.tankInput.getFluid());
            }
            if (!section.tankOutput.getFluid().isEmpty()) {
                output.store("sectionOutput", FluidStack.CODEC, section.tankOutput.getFluid());
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
        if (input.getBooleanOr("hasSection", false)) {
            boolean isStart = input.getBooleanOr("isStart", true);
            if (isStart) {
                ExchangeSectionStart s = new ExchangeSectionStart(this);
                s.tankInput.setFluid(input.read("sectionInput", FluidStack.CODEC).orElse(FluidStack.EMPTY));
                s.tankOutput.setFluid(input.read("sectionOutput", FluidStack.CODEC).orElse(FluidStack.EMPTY));
                s.middleCount = input.getIntOr("middleCount", 1);
                s.progress = input.getIntOr("progress", 0);
                int stateOrd = input.getIntOr("progressState", 0);
                s.progressState = EnumProgressState.values()[Math.min(stateOrd, EnumProgressState.values().length - 1)];
                s.inputCoolantAmountCharge = input.getIntOr("coolantCharge", 0);
                s.inputHeatantAmountCharge = input.getIntOr("heatantCharge", 0);
                section = s;
            } else {
                ExchangeSectionEnd e = new ExchangeSectionEnd(this);
                e.tankInput.setFluid(input.read("sectionInput", FluidStack.CODEC).orElse(FluidStack.EMPTY));
                e.tankOutput.setFluid(input.read("sectionOutput", FluidStack.CODEC).orElse(FluidStack.EMPTY));
                section = e;
            }
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
        public final FluidTank tankInput, tankOutput;
        public final FluidSmoother smoothedTankInput, smoothedTankOutput;
        private TileHeatExchange tile;

        ExchangeSection(TileHeatExchange tile) {
            tankInput = new FluidTank(2000);
            tankOutput = new FluidTank(2000);
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
            left.add("tank_input = " + FluidUtilBC.getDebugString(tankInput.getFluid()));
            left.add("tank_output = " + FluidUtilBC.getDebugString(tankOutput.getFluid()));
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
        IFluidHandler getFluidAutoOutputTarget() {
            return null;
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
            super(tile);
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

        private boolean isHeatant(FluidStack fluid) {
            IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
            if (manager == null) return false;
            return manager.getHeatableRegistry().getRecipeForInput(fluid) != null;
        }

        @Nullable
        @Override
        IFluidHandler getFluidAutoOutputTarget() {
            Direction facing = getTile().getFacing();
            if (facing == null || getTile().level == null) return null;
            BlockPos targetPos = getTile().worldPosition.relative(facing.getClockWise());
            @SuppressWarnings("unchecked")
            IFluidHandler handler = (IFluidHandler) getTile().level.getCapability(
                    (net.neoforged.neoforge.capabilities.BlockCapability) Capabilities.Fluid.BLOCK,
                    targetPos, facing.getCounterClockWise());
            return handler;
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
            FluidTank c_in = endSection.tankInput;
            FluidTank c_out = tankOutput;
            FluidTank h_in = tankInput;
            FluidTank h_out = endSection.tankOutput;
            IRefineryRecipeManager reg = BuildcraftRecipeRegistry.refineryRecipes;
            if (reg == null) {
                progressState = EnumProgressState.STOPPING;
                return;
            }
            ICoolableRecipe c_recipe = reg.getCoolableRegistry().getRecipeForInput(c_in.getFluid());
            IHeatableRecipe h_recipe = reg.getHeatableRegistry().getRecipeForInput(h_in.getFluid());
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
                    : c_out.fill(c_out_f.copy(), IFluidHandler.FluidAction.SIMULATE);
            int h_out_amount = h_out_f == null || h_out_f.isEmpty()
                    ? max_amount
                    : h_out.fill(h_out_f.copy(), IFluidHandler.FluidAction.SIMULATE);

            int c_in_amount = drainableAmount(c_in, c_in_f);
            int h_in_amount = drainableAmount(h_in, h_in_f);

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
            FluidStack c_in_f = end.tankInput.getFluid();
            // If coolant is lava, spew smoke from start side
            if (!c_in_f.isEmpty() && c_in_f.getFluid() == Fluids.LAVA) {
                Direction facing = getTile().getFacing();
                if (facing != null) {
                    spewForth(from, facing.getClockWise(), true);
                }
            }

            FluidStack h_in_f = tankInput.getFluid();
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
            IFluidHandler thisOut = getFluidAutoOutputTarget();
            if (thisOut != null) {
                moveFluid(tankOutput, thisOut, 1000);
            }
            if (endSection != null) {
                IFluidHandler endOut = endSection.getFluidAutoOutputTarget();
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

        private static int drainableAmount(FluidTank t, @Nullable FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return 0;
            FluidStack f2 = t.drain(fluid.copy(), IFluidHandler.FluidAction.SIMULATE);
            return f2.isEmpty() ? 0 : f2.getAmount();
        }

        private static void fillTank(FluidTank t, @Nullable FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return;
            t.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        }

        private static void drainTank(FluidTank t, @Nullable FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) return;
            t.drain(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        }

        private static void moveFluid(FluidTank from, IFluidHandler to, int maxAmount) {
            FluidStack drained = from.drain(maxAmount, IFluidHandler.FluidAction.SIMULATE);
            if (drained.isEmpty()) return;
            int filled = to.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                from.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public static class ExchangeSectionEnd extends ExchangeSection {
        ExchangeSectionEnd(TileHeatExchange tile) {
            super(tile);
        }

        private boolean isCoolant(FluidStack fluid) {
            IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
            if (manager == null) return false;
            return manager.getCoolableRegistry().getRecipeForInput(fluid) != null;
        }

        @Nullable
        @Override
        IFluidHandler getFluidAutoOutputTarget() {
            if (getTile().level == null) return null;
            @SuppressWarnings("unchecked")
            IFluidHandler handler = (IFluidHandler) getTile().level.getCapability(
                    (net.neoforged.neoforge.capabilities.BlockCapability) Capabilities.Fluid.BLOCK,
                    getTile().worldPosition.above(), Direction.DOWN);
            return handler;
        }
    }
}
