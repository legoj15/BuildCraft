/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import com.google.common.collect.MapMaker;
import com.google.common.math.IntMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/^*
 * The capabilities of the vanilla blocks that have none on Forge because they have no block entity: a cauldron's fluid
 * handler and a composter's item handler. NeoForge registers both itself (its {@code CauldronWrapper} over
 * {@code CauldronFluidContent}, and a forwarding {@code SidedInvWrapper} over the composter's own container), and
 * Fabric API's transfer API does for Fabric; Forge has neither, and {@link ForgeTransfer#query} otherwise answers
 * nothing for a block without a block entity. These are the 1.21.1 NeoForge classic wrappers over Forge's own handler
 * types, so a pipe sees the same cauldron and composter on every loader.
 *
 * <p><b>Cauldrons.</b> The empty, water and lava cauldrons, as NeoForge registers them by default (powder snow is
 * not a fluid, and has no content there either): a whole bucket (1000 mB) is the cauldron's total, water has levels 1 to
 * 3 and lava one. A cauldron moves fluid only in the steps the levels and the total allow (the greatest common divisor
 * of the two): water in whole buckets (3 levels, 1000 mB), never a third of one, so a cauldron at level 1 reads as 333
 * mB and gives none of it. A fill takes any tag on the stack off (NeoForge discards it); a drain of a tagged stack gives
 * nothing, the cauldron holds only the plain fluid. The block changes with an update at once on an executed call; the
 * adapters over a classic handler simulate first and replay on commit, so a rolled-back transaction never reaches here.
 *
 * <p><b>Composters.</b> The composter's own {@code WorldlyContainerHolder} container for the asked side
 * ({@link SidedInvWrapper}), asked again on every call because the container is a new object for each fill level and the
 * state can change between a lookup and its use. A block that is no longer the composter answers an empty handler (and a
 * block that is no longer a cauldron, an empty tank), where NeoForge throws: a lookup held for a tick must not crash
 * the tick that follows a block change.
 *
 * <p>One handler per position (and side) is kept while something holds it, so the caches above, which
 * rebuild their BuildCraft view when the handler object changes, do not allocate on each tick.
 ^/
final class ForgeVanillaHandlers {
    private ForgeVanillaHandlers() {}

    /^* One kind of cauldron, as {@code CauldronFluidContent} describes it. ^/
    private record Content(Block block, Fluid fluid, int totalAmount, int maxLevel, @Nullable IntegerProperty levelProperty) {
        /^* The level the cauldron state holds: 0 for an empty one, 1 for one with no level property. ^/
        int currentLevel(BlockState state) {
            if (fluid == Fluids.EMPTY) {
                return 0;
            }
            return levelProperty == null ? 1 : state.getValue(levelProperty);
        }
    }

    private static final Content EMPTY_CAULDRON =
        new Content(Blocks.CAULDRON, Fluids.EMPTY, FluidType.BUCKET_VOLUME, 1, null);
    private static final Content WATER_CAULDRON =
        new Content(Blocks.WATER_CAULDRON, Fluids.WATER, FluidType.BUCKET_VOLUME, 3, LayeredCauldronBlock.LEVEL);
    private static final Content LAVA_CAULDRON =
        new Content(Blocks.LAVA_CAULDRON, Fluids.LAVA, FluidType.BUCKET_VOLUME, 1, null);

    @Nullable
    private static Content forBlock(Block block) {
        if (block == Blocks.CAULDRON) {
            return EMPTY_CAULDRON;
        }
        if (block == Blocks.WATER_CAULDRON) {
            return WATER_CAULDRON;
        }
        return block == Blocks.LAVA_CAULDRON ? LAVA_CAULDRON : null;
    }

    @Nullable
    private static Content forFluid(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return EMPTY_CAULDRON;
        }
        if (fluid == Fluids.WATER) {
            return WATER_CAULDRON;
        }
        return fluid == Fluids.LAVA ? LAVA_CAULDRON : null;
    }

    private record Key(Level level, BlockPos pos, @Nullable Direction side) {}

    /^* One handler per place while a lookup's view holds it (weak values); the key keeps the level until the entry is swept. ^/
    private static final ConcurrentMap<Key, IFluidHandler> CAULDRONS = new MapMaker().weakValues().makeMap();
    private static final ConcurrentMap<Key, IItemHandler> COMPOSTERS = new MapMaker().weakValues().makeMap();

    /^*
     * What {@code capability} holds for the block-entity-less vanilla block {@code state} at {@code pos}, or null: a
     * cauldron's {@code FLUID_HANDLER}, a composter's {@code ITEM_HANDLER} from {@code side}.
     ^/
    @Nullable
    @SuppressWarnings("unchecked")
    static <T> T find(Level level, BlockPos pos, BlockState state, Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            if (forBlock(state.getBlock()) == null) {
                return null;
            }
            BlockPos at = pos.immutable();
            return (T) CAULDRONS.computeIfAbsent(new Key(level, at, null), key -> new CauldronHandler(level, at));
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER && state.is(Blocks.COMPOSTER)) {
            BlockPos at = pos.immutable();
            return (T) COMPOSTERS.computeIfAbsent(new Key(level, at, side), key -> new ComposterHandler(level, at, side));
        }
        return null;
    }

    /^* The composter's container for {@code side}, read from the block as it is now. ^/
    private static final class ComposterHandler implements IItemHandler {
        private final Level level;
        private final BlockPos pos;
        @Nullable
        private final Direction side;

        ComposterHandler(Level level, BlockPos pos, @Nullable Direction side) {
            this.level = level;
            this.pos = pos;
            this.side = side;
        }

        private IItemHandler current() {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.COMPOSTER)) {
                return EmptyHandler.INSTANCE;
            }
            return new SidedInvWrapper(((WorldlyContainerHolder) Blocks.COMPOSTER).getContainer(state, level, pos), side);
        }

        @Override
        public int getSlots() {
            return current().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return current().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return current().insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return current().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return current().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return current().isItemValid(slot, stack);
        }
    }

    /^* A cauldron as a one-tank handler; the 1.21.1 NeoForge {@code CauldronWrapper} on Forge's types. ^/
    private static final class CauldronHandler implements IFluidHandler {
        private final Level level;
        private final BlockPos pos;

        CauldronHandler(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            BlockState state = level.getBlockState(pos);
            Content content = forBlock(state.getBlock());
            if (content == null || content.fluid() == Fluids.EMPTY) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(content.fluid(), content.totalAmount() * content.currentLevel(state) / content.maxLevel());
        }

        @Override
        public int getTankCapacity(int tank) {
            Content content = forBlock(level.getBlockState(pos).getBlock());
            return content == null ? 0 : content.totalAmount();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return forFluid(stack.getFluid()) != null;
        }

        /^* Sets the cauldron to {@code content} at {@code newLevel}, when the call executes. ^/
        private void updateLevel(Content content, int newLevel, FluidAction action) {
            if (action.execute()) {
                BlockState newState = content.block().defaultBlockState();
                if (content.levelProperty() != null) {
                    newState = newState.setValue(content.levelProperty(), newLevel);
                }
                level.setBlockAndUpdate(pos, newState);
            }
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            Content insert = forFluid(resource.getFluid());
            if (insert == null) {
                return 0;
            }
            BlockState state = level.getBlockState(pos);
            Content current = forBlock(state.getBlock());
            if (current == null || current.fluid() != Fluids.EMPTY && resource.getFluid() != current.fluid()) {
                return 0;
            }
            // Only whole increments fit: the cauldron's levels and its total share a step (the greatest common divisor).
            int d = IntMath.gcd(insert.maxLevel(), insert.totalAmount());
            int amountIncrements = insert.totalAmount() / d;
            int levelIncrements = insert.maxLevel() / d;
            int currentLevel = current.currentLevel(state);
            int insertedIncrements = Math.min(resource.getAmount() / amountIncrements,
                (insert.maxLevel() - currentLevel) / levelIncrements);
            if (insertedIncrements > 0) {
                updateLevel(insert, currentLevel + insertedIncrements * levelIncrements, action);
            }
            return insertedIncrements * amountIncrements;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.hasTag()) {
                return FluidStack.EMPTY;
            }
            BlockState state = level.getBlockState(pos);
            Content content = forBlock(state.getBlock());
            if (content == null || resource.getFluid() != content.fluid()) {
                return FluidStack.EMPTY;
            }
            return drain(state, content, resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            BlockState state = level.getBlockState(pos);
            Content content = forBlock(state.getBlock());
            return content == null ? FluidStack.EMPTY : drain(state, content, maxDrain, action);
        }

        private FluidStack drain(BlockState state, Content content, int maxDrain, FluidAction action) {
            int d = IntMath.gcd(content.maxLevel(), content.totalAmount());
            int amountIncrements = content.totalAmount() / d;
            int levelIncrements = content.maxLevel() / d;
            int currentLevel = content.currentLevel(state);
            int extractedIncrements = Math.min(maxDrain / amountIncrements, currentLevel / levelIncrements);
            if (extractedIncrements <= 0 || content.fluid() == Fluids.EMPTY) {
                return FluidStack.EMPTY;
            }
            int newLevel = currentLevel - extractedIncrements * levelIncrements;
            if (newLevel == 0) {
                // Fully drained: back to the empty cauldron.
                if (action.execute()) {
                    level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                }
            } else {
                updateLevel(content, newLevel, action);
            }
            return new FluidStack(content.fluid(), extractedIncrements * amountIncrements);
        }
    }
}
*///?}
