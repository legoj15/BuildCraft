/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.lib.fluid.FluidSmoother;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.BCFactoryAttachments;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.MessageUtil;

/**
 * Distiller tile entity. Takes fluid input, consumes MJ power, and produces
 * two fluid outputs (gas + liquid) via distillation recipes.
 * Ported from 1.12.2 TileDistiller_BC8.
 */
@SuppressWarnings("this-escape")
public class TileDistiller_BC8 extends BlockEntity implements MenuProvider, IDebuggable {

    public static final long MAX_MJ_PER_TICK = 6 * MjAPI.MJ;

    private static final Identifier ADVANCEMENT_HEATING_AND_DISTILLING =
        Identifier.parse("buildcraftunofficial:heating_and_distilling");
    private static final Identifier ADVANCEMENT_REFINE_AND_REDEFINE =
        Identifier.parse("buildcraftunofficial:refine_and_redefine");

    private final InputTank tankIn = new InputTank();
    private final OutputTank tankGasOut = new OutputTank();
    private final OutputTank tankLiquidOut = new OutputTank();

    private final MjBattery mjBattery = new MjBattery(1024 * MjAPI.MJ);
    private final IMjReceiver mjReceiver = new MjBatteryReceiver(mjBattery);

    public final buildcraft.lib.tile.item.ItemHandlerSimple containerSlots = 
        new buildcraft.lib.tile.item.ItemHandlerSimple(3, 1);

    {
        containerSlots.setCallback((handler, slot, bef, aft) -> setChanged());
    }

    // Client-side fluid smoothers for render interpolation
    private final FluidSmoother smoothIn = new FluidSmoother(tankIn);
    private final FluidSmoother smoothGasOut = new FluidSmoother(tankGasOut);
    private final FluidSmoother smoothLiquidOut = new FluidSmoother(tankLiquidOut);

    private IDistillationRecipe currentRecipe;
    private long distillPower = 0;
    private boolean isActive = false;
    /** True when a recipe exists for the input fluid but at least one output tank can't accept the recipe's output. */
    private boolean isStuck = false;

    /** Player who placed this distiller — shown in the GUI ownership ledger and granted the
     *  Heating and Distilling advancement. Persisted to NBT; null until {@link #onPlacedBy} runs. */
    private GameProfile owner;

    /** Edge-detect latch for the Heating and Distilling advancement: true while the distiller was
     *  actively working a recipe last tick, so the grant check fires only on the rising edge. */
    private boolean wasDistillingForAdvancement = false;

    // Power average: exponential moving average in micro-MJ,
    // approximating 1.12.2's AverageLong(100) rolling average.
    private long powerAvgSmoothed = 0;
    private long powerAvgClient = 0;

    // Client-side animation state for power indicator cubes
    // Matches 1.12.2 expression: state increments when active, decays when inactive
    private double animState = 0;
    private double prevAnimState = 0;

    // Client-sync tracking — send block updates when fluid contents or active state change
    private int lastSyncedIn = -1;
    private int lastSyncedGas = -1;
    private int lastSyncedLiquid = -1;
    private boolean lastSyncedActive = false;
    private boolean lastSyncedStuck = false;
    private long lastSyncedPower = -1;

    public TileDistiller_BC8(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.DISTILLER.get(), pos, state);
    }

    // --- Accessors ---

    public InputTank getTankIn() {
        return tankIn;
    }

    public OutputTank getTankGasOut() {
        return tankGasOut;
    }

    public OutputTank getTankLiquidOut() {
        return tankLiquidOut;
    }

    public IMjReceiver getMjReceiver() {
        return mjReceiver;
    }

    /** @return The internal MJ battery, for Forge-Energy capability registration. */
    public MjBattery getBattery() {
        return mjBattery;
    }

    /** @return the profile of the player who placed this distiller, or {@code null} if unknown. */
    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    /**
     * Records the placing player as the owner and syncs it to tracking clients so the GUI
     * ownership ledger renders. Called from {@link buildcraft.factory.block.BlockDistiller#setPlacedBy}.
     */
    public void onPlacedBy(@Nullable LivingEntity placer) {
        if (placer instanceof Player player) {
            owner = player.getGameProfile();
            setChanged();
            if (level != null && !level.isClientSide()) {
                MessageUtil.sendUpdateToTrackingPlayers(this);
            }
        }
    }

    /**
     * Returns the appropriate tank for the given direction.
     * Horizontal sides → input tank, UP → gas output, DOWN → liquid output.
     */
    @Nullable
    public FluidStacksResourceHandler getTankForSide(@Nullable Direction side) {
        if (side == null) return null;
        if (side == Direction.UP) return tankGasOut;
        if (side == Direction.DOWN) return tankLiquidOut;
        return tankIn;
    }

    // --- Fluid Smoothers (for rendering) ---

    public FluidSmoother getSmoothIn() {
        return smoothIn;
    }

    public FluidSmoother getSmoothGasOut() {
        return smoothGasOut;
    }

    public FluidSmoother getSmoothLiquidOut() {
        return smoothLiquidOut;
    }

    // --- Animation Accessors (for renderer) ---

    public boolean isActive() {
        return isActive;
    }

    /** Returns true when a recipe is matched but at least one output tank is full. */
    public boolean isStuck() {
        return isStuck;
    }

    /** Returns the client-synced power average in micro-MJ. */
    public long getPowerAvgClient() {
        return powerAvgClient;
    }

    /** Returns the current animation phase for power indicator cubes. */
    public double getAnimState() {
        return animState;
    }

    /** Returns the previous tick's animation phase, for per-frame lerp in the renderer. */
    public double getPrevAnimState() {
        return prevAnimState;
    }

    /** Call from a client-side ticker to advance fluid smoothing and power animation. */
    public void clientTick() {
        smoothIn.tick();
        smoothGasOut.tick();
        smoothLiquidOut.tick();

        prevAnimState = animState;

        // Advance power cube animation (matches 1.12.2 expression logic)
        // powerAvgClient is in micro-MJ, MAX_MJ_PER_TICK is in micro-MJ
        double changeSpeed = isActive && MAX_MJ_PER_TICK > 0
                ? ((double) powerAvgClient / MAX_MJ_PER_TICK) * 0.06
                : 0.01;
        if (isActive) {
            animState += changeSpeed;
            // Wrap around: once state >= 1.5, subtract 1 to loop between 0.5 and 1.5
            if (animState >= 1.5) {
                animState -= 1.0;
                prevAnimState -= 1.0; // Keep prev in sync so lerp doesn't jump backwards
            }
        } else {
            animState = animState > changeSpeed ? animState - changeSpeed : 0;
        }
    }

    // --- Recipe Filtering ---

    private boolean isDistillableFluid(FluidStack fluid) {
        IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
        if (manager == null) return false;
        IDistillationRecipe recipe = manager.getDistillationRegistry().getRecipeForInput(fluid);
        return recipe != null;
    }

    /**
     * Heating and Distilling advancement predicate. The advancement marks that the player ran oil
     * through a Heat Exchanger before distilling it — i.e. distilled oil at a heat tier that does
     * not occur naturally in the current dimension.
     *
     * <p>Oil spawns <em>cool</em> in the Overworld (and everywhere else), but <em>searing</em> in
     * the Nether (a planned feature — see {@code todos.md}). So the grant fires whenever the input
     * heat differs from the dimension's natural spawn heat.
     *
     * @param inputHeat the heat tier of the recipe input (0 cool, 1 hot, 2 searing; -1 if the
     *                  input is not a BuildCraft oil fluid).
     * @param isNether  whether the distiller is in the Nether.
     * @return {@code true} if distilling this input should grant the advancement.
     */
    public static boolean qualifiesForHeatingAdvancement(int inputHeat, boolean isNether) {
        if (inputHeat < 0) {
            return false;
        }
        int naturalHeat = isNether ? 2 : 0;
        return inputHeat != naturalHeat;
    }

    /**
     * Credits the owner's {@code refine_and_redefine} tracker with the two output fluids
     * of a just-committed distillation step, and fires the advancement on the rising
     * completion edge. Crude {@code oil} is intentionally not produced by the Distiller
     * (it is only an input) — that base name is fed by {@link buildcraft.factory.tile.TilePump}
     * instead.
     *
     * <p>No-ops if no owner is set (pre-{@code track Distiller owner} placements) or if
     * the owner is currently offline — {@link buildcraft.lib.misc.AdvancementUtil#unlockAdvancement
     * (java.util.UUID, net.minecraft.world.level.Level, Identifier)} matches the
     * Heating-and-Distilling pattern here. Progress accrues on the attachment regardless,
     * so it lands when the player next logs in.
     */
    private void creditRefineAndRedefine(FluidStack outGas, FluidStack outLiquid) {
        if (owner == null || level == null || level.isClientSide()) return;
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;
        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(owner.id());
        if (player == null) return;
        var tracker = player.getData(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION.get());
        String gasBase = BCEnergyFluids.getBaseName(outGas.getFluid());
        if (gasBase != null) {
            String justSaturated = tracker.recordProduction(gasBase, outGas.getAmount());
            if (justSaturated != null) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_REFINE_AND_REDEFINE, justSaturated);
            }
        }
        String liquidBase = BCEnergyFluids.getBaseName(outLiquid.getFluid());
        if (liquidBase != null) {
            String justSaturated = tracker.recordProduction(liquidBase, outLiquid.getAmount());
            if (justSaturated != null) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_REFINE_AND_REDEFINE, justSaturated);
            }
        }
    }

    // --- Ticking ---

    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        if (level.getGameTime() % 5 == 0) {
            // Slot 0: Input container -> drain into tankIn
            net.minecraft.world.item.ItemStack inStack = containerSlots.getResource(0).toStack(containerSlots.getAmountAsInt(0));
            if (!inStack.isEmpty()) {
                @SuppressWarnings("removal")
                net.neoforged.neoforge.fluids.FluidActionResult result = net.neoforged.neoforge.fluids.FluidUtil.tryEmptyContainer(
                    inStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tankIn), Integer.MAX_VALUE, null, true
                );
                if (result.isSuccess()) {
                    containerSlots.setStackInSlot(0, result.getResult());
                }
            }
            // Slot 1: Output container gas -> fill from tankGasOut
            net.minecraft.world.item.ItemStack gasStack = containerSlots.getResource(1).toStack(containerSlots.getAmountAsInt(1));
            if (!gasStack.isEmpty()) {
                @SuppressWarnings("removal")
                net.neoforged.neoforge.fluids.FluidActionResult result = net.neoforged.neoforge.fluids.FluidUtil.tryFillContainer(
                    gasStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tankGasOut), Integer.MAX_VALUE, null, true
                );
                if (result.isSuccess()) {
                    containerSlots.setStackInSlot(1, result.getResult());
                }
            }
            // Slot 2: Output container liquid -> fill from tankLiquidOut
            net.minecraft.world.item.ItemStack liqStack = containerSlots.getResource(2).toStack(containerSlots.getAmountAsInt(2));
            if (!liqStack.isEmpty()) {
                @SuppressWarnings("removal")
                net.neoforged.neoforge.fluids.FluidActionResult result = net.neoforged.neoforge.fluids.FluidUtil.tryFillContainer(
                    liqStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.of(tankLiquidOut), Integer.MAX_VALUE, null, true
                );
                if (result.isSuccess()) {
                    containerSlots.setStackInSlot(2, result.getResult());
                }
            }
        }

        mjBattery.tick(level, worldPosition);

        currentRecipe = null;
        IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
        if (manager != null) {
            FluidStack inFluid = tankIn.getResource(0).toStack(tankIn.getAmountAsInt(0));
            if (!inFluid.isEmpty()) {
                currentRecipe = manager.getDistillationRegistry().getRecipeForInput(inFluid);
            }
        }

        if (currentRecipe == null) {
            mjBattery.addPowerChecking(distillPower, false);
            distillPower = 0;
            isActive = false;
            isStuck = false;
        } else {
            FluidStack reqIn = currentRecipe.in();
            FluidStack outLiquid = currentRecipe.outLiquid();
            FluidStack outGas = currentRecipe.outGas();

            FluidResource resIn = tankIn.getResource(0);
            boolean canExtract = !resIn.isEmpty()
                    && resIn.equals(FluidResource.of(reqIn))
                    && tankIn.getAmountAsInt(0) >= reqIn.getAmount();

            boolean canFillLiquid;
            try (Transaction tx = Transaction.openRoot()) {
                canFillLiquid = tankLiquidOut.insertInternal(0, FluidResource.of(outLiquid), outLiquid.getAmount(), tx) >= outLiquid.getAmount();
            }
            boolean canFillGas;
            try (Transaction tx = Transaction.openRoot()) {
                canFillGas = tankGasOut.insertInternal(0, FluidResource.of(outGas), outGas.getAmount(), tx) >= outGas.getAmount();
            }

            isStuck = !canFillLiquid || !canFillGas;

            if (canExtract && canFillLiquid && canFillGas) {
                long max = MAX_MJ_PER_TICK;
                max *= mjBattery.getStored() + max;
                max /= mjBattery.getCapacity() / 2;
                max = Math.min(max, MAX_MJ_PER_TICK);
                long power = mjBattery.extractPower(0, max);
                // Feed into EWMA (alpha ≈ 0.05 for ~20-tick smoothing)
                powerAvgSmoothed += (long) ((max - powerAvgSmoothed) * 0.05);
                distillPower += power;
                isActive = power > 0;
                long powerReq = currentRecipe.powerRequired();
                if (distillPower >= powerReq) {
                    isActive = true;
                    distillPower -= powerReq;
                    try (Transaction tx = Transaction.openRoot()) {
                        tankIn.extractInternal(0, FluidResource.of(reqIn), reqIn.getAmount(), tx);
                        tankGasOut.insertInternal(0, FluidResource.of(outGas), outGas.getAmount(), tx);
                        tankLiquidOut.insertInternal(0, FluidResource.of(outLiquid), outLiquid.getAmount(), tx);
                        tx.commit();
                    }
                    creditRefineAndRedefine(outGas, outLiquid);
                }
            } else {
                mjBattery.addPowerChecking(distillPower, false);
                distillPower = 0;
                isActive = false;
            }
        }

        // Heating and Distilling advancement — granted to the owner on the rising edge of the
        // distiller actually working a recipe whose input oil sits at a heat tier this dimension
        // does not spawn naturally (Nether oil spawns searing; everywhere else, cool). A non-natural
        // heat means the player ran the oil through a Heat Exchanger first.
        boolean distilling = isActive && currentRecipe != null;
        if (distilling && !wasDistillingForAdvancement && owner != null) {
            int inputHeat = BCEnergyFluids.getHeat(currentRecipe.in().getFluid());
            if (qualifiesForHeatingAdvancement(inputHeat, level.dimension() == Level.NETHER)) {
                AdvancementUtil.unlockAdvancement(owner.id(), level, ADVANCEMENT_HEATING_AND_DISTILLING);
            }
        }
        wasDistillingForAdvancement = distilling;

        // When idle, decay the EWMA toward zero
        if (currentRecipe == null || !isActive) {
            powerAvgSmoothed += (long) ((0 - powerAvgSmoothed) * 0.05);
        }
        // Round to nearest 0.5 MJ before syncing (matches 1.12.2 rounding)
        // This prevents EWMA values just below a threshold (e.g. 1,999,999)
        // from causing the texture index to drop by one level.
        long halfMJ = MjAPI.MJ / 2; // 500,000
        powerAvgClient = Math.round(powerAvgSmoothed / (double) halfMJ) * halfMJ;
        powerAvgClient = Math.min(powerAvgClient, MAX_MJ_PER_TICK);

        // Send client sync when fluid amounts or active state change
        int curIn = tankIn.getAmountAsInt(0);
        int curGas = tankGasOut.getAmountAsInt(0);
        int curLiq = tankLiquidOut.getAmountAsInt(0);
        boolean needsSync = curIn != lastSyncedIn || curGas != lastSyncedGas || curLiq != lastSyncedLiquid
                || isActive != lastSyncedActive || isStuck != lastSyncedStuck || powerAvgClient != lastSyncedPower;
        if (needsSync) {
            lastSyncedIn = curIn;
            lastSyncedGas = curGas;
            lastSyncedLiquid = curLiq;
            lastSyncedActive = isActive;
            lastSyncedStuck = isStuck;
            lastSyncedPower = powerAvgClient;
            setChanged();
            MessageUtil.sendUpdateToTrackingPlayers(this);
        }
    }

    // --- IDebuggable ---

    @Override
    public void getDebugInfo(java.util.List<String> left, java.util.List<String> right, Direction side) {
        // We use fluid stacks here to represent the contents
        left.add("In = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tankIn.getResource(0).toStack(tankIn.getAmountAsInt(0))));
        left.add("GasOut = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tankGasOut.getResource(0).toStack(tankGasOut.getAmountAsInt(0))));
        left.add("LiquidOut = " + buildcraft.lib.misc.FluidUtilBC.getDebugString(tankLiquidOut.getResource(0).toStack(tankLiquidOut.getAmountAsInt(0))));
        left.add("Battery = " + mjBattery.getDebugString());
        left.add("Progress = " + MjAPI.formatMj(distillPower));
        left.add("Rate = " + buildcraft.lib.misc.LocaleUtil.localizeMjFlow(powerAvgClient));
        left.add("CurrRecipe = " + currentRecipe);
    }

    @Override
    public void getClientDebugInfo(java.util.List<String> left, java.util.List<String> right, Direction side) {
        if (smoothIn != null) smoothIn.getDebugInfo(left, right, side);
        if (smoothGasOut != null) smoothGasOut.getDebugInfo(left, right, side);
        if (smoothLiquidOut != null) smoothLiquidOut.getDebugInfo(left, right, side);

        // Model Variables (matches 1.12.2 setClientModelVariables output)
        Direction facing = Direction.WEST;
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(buildcraft.factory.block.BlockDistiller.FACING)) {
                facing = state.getValue(buildcraft.factory.block.BlockDistiller.FACING);
            }
        }
        left.add("Model Variables:");
        left.add("  facing = " + facing);
        left.add("  active = " + isActive);
        left.add("  power_average = " + (powerAvgClient / MjAPI.MJ));
        left.add("  power_max = " + (MAX_MJ_PER_TICK / MjAPI.MJ));
        left.add("Current Model Variables:");
        left.add("  animState = " + String.format("%.4f", animState));
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.distiller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerDistiller(containerId, playerInv, this);
    }

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null && owner.id() != null) {
            output.putString("ownerUUID", owner.id().toString());
            if (owner.name() != null) {
                output.putString("ownerName", owner.name());
            }
        }
        // Save each tank's fluid contents directly
        if (!tankIn.getResource(0).isEmpty()) {
            output.store("fluidIn", FluidStack.CODEC, tankIn.getResource(0).toStack(tankIn.getAmountAsInt(0)));
        }
        if (!tankGasOut.getResource(0).isEmpty()) {
            output.store("fluidGasOut", FluidStack.CODEC, tankGasOut.getResource(0).toStack(tankGasOut.getAmountAsInt(0)));
        }
        if (!tankLiquidOut.getResource(0).isEmpty()) {
            output.store("fluidLiquidOut", FluidStack.CODEC, tankLiquidOut.getResource(0).toStack(tankLiquidOut.getAmountAsInt(0)));
        }
        output.putLong("mjStored", mjBattery.getStored());
        output.putLong("distillPower", distillPower);
        output.putBoolean("isActive", isActive);
        output.putBoolean("isStuck", isStuck);
        output.putLong("powerAvgClient", powerAvgClient);
        output.store("containerSlots", net.minecraft.nbt.CompoundTag.CODEC, containerSlots.serializeNBT());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String ownerUuid = input.getStringOr("ownerUUID", "");
        if (!ownerUuid.isEmpty()) {
            try {
                owner = new GameProfile(UUID.fromString(ownerUuid), input.getStringOr("ownerName", "Unknown"));
            } catch (IllegalArgumentException e) {
                owner = null;
            }
        }
        loadTank(tankIn, input, "fluidIn");
        loadTank(tankGasOut, input, "fluidGasOut");
        loadTank(tankLiquidOut, input, "fluidLiquidOut");
        mjBattery.addPowerChecking(input.getLongOr("mjStored", 0L), false);
        distillPower = input.getLongOr("distillPower", 0L);
        isActive = input.getBooleanOr("isActive", false);
        isStuck = input.getBooleanOr("isStuck", false);
        powerAvgClient = input.getLongOr("powerAvgClient", 0L);
        containerSlots.deserializeNBT(input.read("containerSlots", net.minecraft.nbt.CompoundTag.CODEC).orElseGet(net.minecraft.nbt.CompoundTag::new));
    }

    /**
     * Reads a fluid stack from {@code input} under {@code key} and writes it directly into
     * {@code tank} via {@link FluidStacksResourceHandler#set}. <em>Always</em> writes — when
     * the key is absent (saveAdditional only stores non-empty tanks) the tank is forced to
     * empty. Without this, a client whose tank previously held fluid would never see a server
     * drain reflected: the {@code getUpdateTag} → {@code loadAdditional} round-trip omits the
     * key for an empty server-side tank, so {@code insert()} (which adds to existing contents)
     * leaves stale client values in place forever.
     */
    private static void loadTank(FluidStacksResourceHandler tank, ValueInput input, String key) {
        FluidStack fluid = input.read(key, FluidStack.CODEC).orElse(FluidStack.EMPTY);
        if (fluid.isEmpty()) {
            tank.set(0, FluidResource.EMPTY, 0);
        } else {
            tank.set(0, FluidResource.of(fluid), fluid.getAmount());
        }
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

    /**
     * Input tank that gates external interactions to match 1.12.2's
     * {@code tankIn.setFilter(isDistillableFluid)} + {@code setCanDrain(false)}:
     * external {@code insert} accepts only fluids with a registered distillation
     * recipe, and external {@code extract} returns 0 outright. The serverTick craft
     * loop drains the tank via {@link #extractInternal}, which flips a guard for the
     * duration of one {@code super.extract} call so recipes still consume input.
     * NBT load uses {@code set()} directly to bypass both gates (saved fluid is
     * trusted, and the recipe registry may not be ready during early chunk load).
     */
    public class InputTank extends FluidStacksResourceHandler {
        private boolean internalExtract = false;

        public InputTank() {
            super(1, 4000);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            // Accept anything during initial registry load (when distillation
            // registry may be empty) — set() bypasses this anyway, but keep the
            // check robust against simulated capability checks before recipes load.
            return isDistillableFluid(resource.toStack(1));
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            return internalExtract ? super.extract(index, resource, amount, tx) : 0;
        }

        public int extractInternal(int index, FluidResource resource, int amount, TransactionContext tx) {
            internalExtract = true;
            try {
                return super.extract(index, resource, amount, tx);
            } finally {
                internalExtract = false;
            }
        }
    }

    /**
     * Output tank that rejects external insertions, matching 1.12.2's
     * {@code tankOut.setCanFill(false)}. The craft loop fills via
     * {@link #insertInternal}, which flips a guard for the duration of one
     * {@code super.insert} call so recipe results still land. External callers
     * (capability access, bucket right-clicks, GUI tank widget clicks) all funnel
     * through {@code insert}, which checks {@code isValid} and so is blocked.
     */
    public static class OutputTank extends FluidStacksResourceHandler {
        private boolean internalInsert = false;

        public OutputTank() {
            super(1, 4000);
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
}

