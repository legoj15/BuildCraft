/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
//? if >=1.21.10 {
import net.minecraft.world.entity.animal.equine.AbstractHorse;
//?} else {
/^import net.minecraft.world.entity.animal.horse.AbstractHorse;
^///?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import buildcraft.api.capability.BCCapability;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCLookupCache;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.CapabilityRegistrar;
import buildcraft.platform.transfer.TransferServices;

/^*
 * {@link TransferServices} on Forge, which has only the classic capability model on every line: {@code Capability} keys,
 * {@code LazyOptional} answers, {@code IItemHandler} / {@code IFluidHandler} / {@code IEnergyStorage} handlers with a
 * simulate flag, and no transfer API and no transaction. So this is the 1.21.1 arm of {@code NeoForgeTransfer} with
 * Forge's own handler types: the platform adapters run each call in a BuildCraft transaction
 * ({@link LegacyTransactions}), and a foreign classic handler is simulated and replayed when the root commits
 * ({@link ForgeItemStorages}, {@link ForgeFluidStorages}, {@link ForgeEnergyStorages}).
 *
 * <p><b>Exposing.</b> There is no registration event on 26.x, so {@link #registerCapabilities} queues the callbacks for
 * {@code FMLCommonSetupEvent} and fills a per-type provider table that an {@code AttachCapabilitiesEvent} listener turns
 * into a capability provider on each block entity of a registered type ({@link ForgeTileCapabilities}, which has the
 * whole account). The storage tokens become the Forge capabilities {@code ITEM_HANDLER}, {@code FLUID_HANDLER} and
 * {@code ENERGY}; every other token (the MJ ones) a capability of the same id and type
 * ({@link ForgeCapabilityTokens}), its objects handed out as they are.
 *
 * <p><b>Querying.</b> Forge has no position-keyed capability lookup, so a query asks the block entity itself, as Forge's
 * own {@code FluidUtil.getFluidHandler(Level, BlockPos, Direction)} does ({@link #query}); an entity is asked directly.
 * A block with no block entity answers nothing except the two vanilla blocks NeoForge and Fabric API register providers
 * for, which Forge does not ({@link ForgeVanillaHandlers}: a cauldron's fluid handler, a composter's inventory);
 * Forge's patched vanilla block entities (chests, furnaces, hoppers, brewing stands) answer as usual.
 *
 * <p><b>Caches.</b> Forge has no {@code BlockCapabilityCache}, and nothing that tells a neighbour's reader its answer
 * changed other than a {@code LazyOptional}'s invalidation, which only a holder of that optional hears. The cache here
 * therefore asks the neighbour on every {@code get()} (a block entity lookup and the provider call, no allocation while
 * the answer is the same object) and keeps the BuildCraft view while the Forge handler stays the same, which is the
 * contract {@link BCLookupCache} states; it is never stale, which a cache keyed on invalidation could not promise for
 * BuildCraft's own side-dependent answers. As NeoForge's cache does, it answers null for an unloaded position instead
 * of loading the chunk.
 *
 * <p><b>Fluid container items</b> (buckets, the fragile shard): the six contract methods at the end delegate to the
 * static method of the same name on {@code ForgeFluidContainers}, as {@code NeoForgeTransfer} does to its twin.
 ^/
public final class ForgeTransfer implements TransferServices {
    @Override
    public BCTransaction openRoot() {
        return LegacyTransactions.openRoot();
    }

    @Override
    public BCTransaction openInCurrent() {
        return LegacyTransactions.openInCurrent();
    }

    @Override
    public <S> JournalHandle newJournal(JournalCallbacks<S> callbacks) {
        return LegacyTransactions.newJournal(callbacks);
    }

    @Override
    public void registerCapabilities(Consumer<CapabilityRegistrar> registration) {
        ForgeTileCapabilities.queue(registration);
    }

    /^* What {@code capability} holds for the block at {@code pos} from {@code side}, or null (the block entity's answer). ^/
    @Nullable
    private static <T> T query(Level level, BlockPos pos, Capability<T> capability, @Nullable Direction side) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasBlockEntity()) {
            return ForgeVanillaHandlers.find(level, pos, state, capability, side);
        }
        BlockEntity tile = level.getBlockEntity(pos);
        return tile == null ? null : tile.getCapability(capability, side).resolve().orElse(null);
    }

    @Override
    @Nullable
    public BCItemStorage findItem(Level level, BlockPos pos, @Nullable Direction side) {
        return ForgeItemStorages.fromForge(query(level, pos, ForgeCapabilities.ITEM_HANDLER, side));
    }

    @Override
    @Nullable
    public BCItemStorage findItem(Entity entity) {
        // A horse is its inventory on every loader. Forge's own patch attaches that handler in registerGoals(), which
        // Mob's constructor runs before the patch's `itemHandler = null` field initialiser wipes it (AbstractHorse.java
        // lines 146 and 1039 of the patched source), so Forge's lookup never answers for a horse and a chested donkey
        // would fall through to the equipment wrapper: read the container ourselves. (Forge 1.21.1's patch sets the handler
        // in createInventory(), after that initialiser, so its own answer would do there; the same read keeps one path.)
        if (entity instanceof AbstractHorse horse && horse.isAlive()) {
            IItemHandler handler = ForgeVanillaHandlers.horse(horse);
            if (handler != null) {
                return ForgeItemStorages.fromForge(handler);
            }
        }
        IItemHandler answer = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
        // NeoForge's fallback for a living entity is its hands and four armour slots, each taking only what fits there;
        // Forge's stock wrapper adds the body slot and takes anything anywhere. On 1.21.1 both loaders' answer is the
        // same hands-and-armour wrapper that takes anything, so Forge's own stands.
        //? if >=1.21.10 {
        if (answer != null && entity instanceof LivingEntity living && !(entity instanceof Player)
                && ForgeVanillaHandlers.isStockEquipment(answer)) {
            answer = ForgeVanillaHandlers.equipment(living);
        }
        //?}
        return ForgeItemStorages.fromForge(answer);
    }

    @Override
    public BCLookupCache<BCItemStorage> itemCache(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return new BCLookupCache<>() {
            private IItemHandler handler;
            private BCItemStorage view;

            @Override
            @Nullable
            public BCItemStorage get() {
                IItemHandler current = level.isLoaded(pos) ? query(level, pos, ForgeCapabilities.ITEM_HANDLER, side) : null;
                if (current != handler) {
                    handler = current;
                    view = ForgeItemStorages.fromForge(current);
                }
                return view;
            }
        };
    }

    @Override
    @Nullable
    public BCEnergyStorage findEnergy(Level level, BlockPos pos, @Nullable Direction side) {
        IEnergyStorage handler = query(level, pos, ForgeCapabilities.ENERGY, side);
        return ForgeEnergyStorages.fromForge(handler);
    }

    @Override
    @Nullable
    public <T> T findDirect(BCCapability<T> capability, Level level, BlockPos pos, @Nullable Direction side) {
        return capability.type().cast(query(level, pos, ForgeCapabilityTokens.of(capability), side));
    }

    @Override
    @Nullable
    public BCFluidStorage findFluid(Level level, BlockPos pos, @Nullable Direction side) {
        return ForgeFluidStorages.fromForge(query(level, pos, ForgeCapabilities.FLUID_HANDLER, side));
    }

    @Override
    public BCLookupCache<BCFluidStorage> fluidCache(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return new BCLookupCache<>() {
            private IFluidHandler handler;
            private BCFluidStorage view;

            @Override
            @Nullable
            public BCFluidStorage get() {
                IFluidHandler current = level.isLoaded(pos) ? query(level, pos, ForgeCapabilities.FLUID_HANDLER, side) : null;
                if (current != handler) {
                    handler = current;
                    view = ForgeFluidStorages.fromForge(current);
                }
                return view;
            }
        };
    }

    // ---- Fluid container items: ForgeFluidContainers.

    @Override
    public BCFluidStack getFluidContained(ItemStack stack) {
        return ForgeFluidContainers.getFluidContained(stack);
    }

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return ForgeFluidContainers.isFluidContainer(stack);
    }

    @Override
    public boolean drainContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        return ForgeFluidContainers.drainContainerSlot(slots, slot, setSlot, tank);
    }

    @Override
    public boolean fillContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        return ForgeFluidContainers.fillContainerSlot(slots, slot, setSlot, tank);
    }

    @Override
    public boolean interactWithHeldContainer(Player player, InteractionHand hand, BlockPos pos, BCFluidStorage tank) {
        return ForgeFluidContainers.interactWithHeldContainer(player, hand, pos, tank);
    }

    @Override
    public boolean exchangeWithCarriedContainer(Player player, BCFluidStorage tank) {
        return ForgeFluidContainers.exchangeWithCarriedContainer(player, tank);
    }
}
*///?}
