/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import buildcraft.platform.event.PlatformProtection;

/^*
 * {@link PlatformProtection} on Fabric: Fabric API's player callbacks, invoked the way Fabric's own hooks invoke them
 * for a player's act. A callback answering anything but {@code PASS} stops the act there, so it refuses the machine's.
 *
 * <p><b>Gaps.</b> Fabric API has no block-placement event, so {@link #canPlace} and {@link #canPlaceOver} fall back to
 * vanilla's {@code mayInteract} (spawn protection and the world border); claim mods that refuse placements through
 * their own mixins are not asked. Fabric has no harvest-check event either: {@link #canHarvest} is vanilla's tool check.
 * Fabric callbacks carry no event object, and cannot be unregistered: see {@link #isProbe} and {@link #onBreak}.
 *^/
public final class FabricProtection implements PlatformProtection {

    /^* The break probe {@link #canBreak} is invoking right now (the innermost one), or null. Server thread only. *^/
    private static Probe probeInFlight;

    private record Probe(Level level, BlockPos pos, Player player) {
        boolean matches(Level level, BlockPos pos, Player player) {
            return this.level == level && this.pos.equals(pos) && this.player == player;
        }
    }

    @Override
    public boolean canBreak(Level level, BlockPos pos, BlockState state, Player player) {
        Probe outer = probeInFlight;
        probeInFlight = new Probe(level, pos.immutable(), player);
        try {
            return PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
        } finally {
            probeInFlight = outer;
        }
    }

    // Gap: no placement event on Fabric; vanilla's mayInteract (spawn protection, world border) is asked per cell.
    @Override
    public boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a placement fills at least one cell");
        }
        for (BlockPos cell : cells) {
            if (!player.mayInteract(level, cell)) {
                return false;
            }
        }
        return true;
    }

    /^* Fabric callbacks carry no event object, so only BuildCraft's own {@link BlockBreak} views can be told apart. *^/
    @Override
    public boolean isProbe(Object event) {
        return event instanceof BlockBreak view && view.isProbe();
    }

    /^*
     * Registers on {@link PlayerBlockBreakEvents#BEFORE}. Fabric events cannot be unregistered, so closing the
     * registration leaves an inert callback behind instead.
     *^/
    @Override
    public Registration onBreak(Consumer<BlockBreak> listener) {
        Inert registration = new Inert();
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (registration.closed) {
                return true;
            }
            View view = new View(level, pos.immutable(), state, player);
            listener.accept(view);
            return !view.cancelled;
        });
        return registration;
    }

    /^* A registration whose callback stays registered but does nothing once closed. *^/
    private static final class Inert implements Registration {
        volatile boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    @Override
    public boolean canAttack(Player attacker, Entity target) {
        return AttackEntityCallback.EVENT.invoker()
            .interact(attacker, attacker.level(), InteractionHand.MAIN_HAND, target, null) == InteractionResult.PASS;
    }

    // Gap: no harvest-check event on Fabric; vanilla's tool check.
    @Override
    public boolean canHarvest(Player player, BlockState state, BlockGetter level, BlockPos pos) {
        return player.hasCorrectToolForDrops(state);
    }

    /^* A callback that answered anything but PASS took the click (or refused it): the item is not used either way. *^/
    @Override
    public ClickVerdict rightClickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, player.level(), hand, hit);
        return result == InteractionResult.PASS ? ClickVerdict.DEFAULT : ClickVerdict.REFUSED;
    }

    @Override
    public boolean canUseItem(Player player, InteractionHand hand) {
        return UseItemCallback.EVENT.invoker().interact(player, player.level(), hand) == InteractionResult.PASS;
    }

    @Override
    public CapturedBlock capture(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag tag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
        return new Captured(level, pos.immutable(), level.getBlockState(pos), tag);
    }

    // Gap: no placement event on Fabric; vanilla's mayInteract (spawn protection, world border) is asked.
    @Override
    public boolean canPlaceOver(Entity placer, CapturedBlock replaced, Direction face) {
        return !(placer.level() instanceof ServerLevel level) || placer.mayInteract(level, replaced.pos());
    }

    /^* A recorded block: what NeoForge's {@code BlockSnapshot} records, restored the same way. *^/
    private record Captured(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) implements CapturedBlock {
        @Override
        public boolean restore(int flags) {
            BlockState current = level.getBlockState(pos);
            if (!level.setBlock(pos, state, flags)) {
                return false;
            }
            level.sendBlockUpdated(pos, current, state, flags);
            if (tag != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                    be.setChanged();
                }
            }
            return true;
        }
    }

    /^* A player's break (or a probe) as a listener sees it. *^/
    private static final class View implements BlockBreak {
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;
        private final Player player;
        boolean cancelled;

        View(Level level, BlockPos pos, BlockState state, Player player) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.player = player;
        }

        @Override
        public LevelAccessor level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }

        @Override
        public BlockState state() {
            return state;
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public boolean isProbe() {
            Probe probe = probeInFlight;
            return probe != null && probe.matches(level, pos, player);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
*///?}
