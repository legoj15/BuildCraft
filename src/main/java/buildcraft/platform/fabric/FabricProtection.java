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
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import eu.pb4.common.protection.api.CommonProtection;

import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.HookOrder;
import buildcraft.platform.event.PlatformProtection;

/^*
 * {@link PlatformProtection} on Fabric: Fabric API's player callbacks, invoked the way Fabric's own hooks invoke them
 * for a player's act, and Common Protection API, the API Fabric claim mods answer through (bundled in the jar). A
 * callback answering anything but {@code PASS} stops the act there, and a protection provider answering false refuses
 * it: either refuses the machine's. The machine's fake player carries its owner's profile, so the providers are asked
 * about the owner ({@link Player#nameAndId}).
 *
 * <p><b>Placements.</b> Fabric API has no block-placement event, so a placement is asked of the protection providers
 * and of vanilla's {@code mayInteract} (spawn protection and the world border); a block an item use sets down is
 * recorded as NeoForge's snapshot capture records it ({@link #useItemOn}). BuildCraft's own {@code onBlockPlace}
 * listeners are told about these machine placements, never about a player's.
 *
 * <p><b>Gaps.</b> A claim mod that guards placements through its own mixins alone, not Common Protection API, is not
 * asked about a placement. Common Protection API has no item-use check: an item used in the air asks
 * whether the block the player stands in may be interacted with. Fabric has no harvest-check event:
 * {@link #canHarvest} is vanilla's tool check. Fabric callbacks cannot be unregistered: see {@link #onBreak}.
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
            if (!PlayerBlockBreakEvents.BEFORE.invoker()
                    .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos))) {
                return false;
            }
        } finally {
            probeInFlight = outer;
        }
        return CommonProtection.canBreakBlock(level, pos, player.nameAndId(), player);
    }

    /^* Each cell is asked of vanilla's mayInteract (spawn protection, world border) and of the protection providers. *^/
    @Override
    public boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a placement fills at least one cell");
        }
        for (BlockPos cell : cells) {
            if (!player.mayInteract(level, cell) || !CommonProtection.canPlaceBlock(level, cell, player.nameAndId(), player)) {
                return false;
            }
        }
        return true;
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
                .interact(attacker, attacker.level(), InteractionHand.MAIN_HAND, target, null) == InteractionResult.PASS
            && CommonProtection.canDamageEntity(attacker.level(), target, attacker.nameAndId(), attacker);
    }

    // Gap: no harvest-check event on Fabric; vanilla's tool check.
    @Override
    public boolean canHarvest(Player player, BlockState state, BlockGetter level, BlockPos pos) {
        return player.hasCorrectToolForDrops(state);
    }

    /^*
     * A callback that answered anything but PASS took the click (or refused it): the item is not used either way. A
     * provider refusing the interaction with the clicked block refuses it too.
     *^/
    @Override
    public ClickVerdict rightClickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, player.level(), hand, hit);
        if (result != InteractionResult.PASS) {
            return ClickVerdict.REFUSED;
        }
        return CommonProtection.canInteractBlock(player.level(), hit.getBlockPos(), player.nameAndId(), player)
            ? ClickVerdict.DEFAULT : ClickVerdict.REFUSED;
    }

    // Gap: Common Protection API has no item-use check; the block the player stands in is asked (as a claim mod's
    // item right-click listener sees the use: at the player's position).
    @Override
    public boolean canUseItem(Player player, InteractionHand hand) {
        return UseItemCallback.EVENT.invoker().interact(player, player.level(), hand) == InteractionResult.PASS
            && CommonProtection.canInteractBlock(player.level(), player.blockPosition(), player.nameAndId(), player);
    }

    /^* The placement capture {@link #useItemOn} is running right now (the innermost one), or null. Server thread only. *^/
    private static PlacementCapture captureInFlight;

    /^* The blocks one item use changed, each as it stood before its first change, in the order they changed. *^/
    private static final class PlacementCapture {
        final ServerLevel level;
        final java.util.LinkedHashMap<BlockPos, Captured> before = new java.util.LinkedHashMap<>();

        PlacementCapture(ServerLevel level) {
            this.level = level;
        }
    }

    /^* Called by {@code LevelMixin} before {@code level} sets the block at {@code pos}. *^/
    public static void beforeSetBlock(Level level, BlockPos pos) {
        PlacementCapture capture = captureInFlight;
        if (capture != null && capture.level == level && !capture.before.containsKey(pos)) {
            BlockPos at = pos.immutable();
            capture.before.put(at, captured(capture.level, at));
        }
    }

    /^*
     * NeoForge's placement capture ({@code CommonHooks.onPlaceItemIntoWorld}) for Fabric: the block changes the use
     * makes are recorded ({@code LevelMixin}), and each changed cell is asked of the protection providers as a
     * placement. Gap: unlike NeoForge, the blocks' own placement side effects and neighbour updates have already run
     * when a refused placement is put back.
     *^/
    @Override
    public InteractionResult useItemOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!(context.getLevel() instanceof ServerLevel level) || stack.getItem() instanceof BucketItem) {
            return stack.useOn(context);
        }
        ItemStack before = stack.copy();
        PlacementCapture outer = captureInFlight;
        PlacementCapture capture = new PlacementCapture(level);
        captureInFlight = capture;
        InteractionResult result;
        try {
            result = stack.useOn(context);
        } finally {
            captureInFlight = outer;
        }
        if (!result.consumesAction()) {
            return result;
        }
        Player player = context.getPlayer();
        NameAndId profile = player != null ? player.nameAndId() : CommonProtection.UNKNOWN;
        BlockPos first = null;
        boolean refused = false;
        for (Captured cell : capture.before.values()) {
            if (level.getBlockState(cell.pos()) == cell.state()) {
                continue;
            }
            if (first == null) {
                first = cell.pos();
            }
            if (!CommonProtection.canPlaceBlock(level, cell.pos(), profile, player)) {
                refused = true;
                break;
            }
        }
        if (first == null) {
            return result;
        }
        // One placement as NeoForge posts one (a multi-cell one at its first cell), before anything is put back.
        postPlace(new Placed(level, first, level.getBlockState(first.relative(context.getClickedFace().getOpposite())),
                player, refused));
        if (!refused) {
            return result;
        }
        List<Captured> changed = new java.util.ArrayList<>(capture.before.values());
        for (int i = changed.size() - 1; i >= 0; i--) {
            changed.get(i).restore(Block.UPDATE_ALL);
        }
        stack.setCount(before.getCount());
        stack.applyComponents(before.getComponents());
        return InteractionResult.FAIL;
    }

    @Override
    public CapturedBlock capture(ServerLevel level, BlockPos pos) {
        return captured(level, pos.immutable());
    }

    private static Captured captured(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag tag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
        return new Captured(level, pos.immutable(), level.getBlockState(pos), tag);
    }

    /^*
     * Asked of vanilla's mayInteract (spawn protection, world border) and of the protection providers, about the
     * placer's profile (a placer that is no player is {@link CommonProtection#UNKNOWN}, as the API has it).
     *^/
    @Override
    public boolean canPlaceOver(Entity placer, CapturedBlock replaced, Direction face) {
        if (!(placer.level() instanceof ServerLevel level)) {
            return true;
        }
        Player player = placer instanceof Player p ? p : null;
        NameAndId profile = player != null ? player.nameAndId() : CommonProtection.UNKNOWN;
        BlockPos pos = replaced.pos();
        boolean allowed = placer.mayInteract(level, pos) && CommonProtection.canPlaceBlock(level, pos, profile, player);
        postPlace(new Placed(level, pos, level.getBlockState(pos.relative(face.getOpposite())), placer, !allowed));
        return allowed;
    }

    /^* The {@code GameHooks.onBlockPlace} listeners, by order: Fabric has no placement event of its own to put them on. *^/
    private static final java.util.Map<HookOrder, List<PlaceListener>> PLACE_LISTENERS = new java.util.EnumMap<>(
            java.util.Map.of(HookOrder.EARLY, new java.util.concurrent.CopyOnWriteArrayList<>(),
                    HookOrder.DEFAULT, new java.util.concurrent.CopyOnWriteArrayList<>(),
                    HookOrder.LATE, new java.util.concurrent.CopyOnWriteArrayList<>()));

    private record PlaceListener(boolean receiveCancelled, Consumer<GameHooks.BlockPlace> listener) {}

    /^* {@code GameHooks.onBlockPlace} on Fabric: told about the placements {@link #useItemOn} and {@link #canPlaceOver}
     *  see. *^/
    static void addPlaceListener(HookOrder order, boolean receiveCancelled, Consumer<GameHooks.BlockPlace> listener) {
        PLACE_LISTENERS.get(order).add(new PlaceListener(receiveCancelled, listener));
    }

    private static void postPlace(Placed placed) {
        for (HookOrder order : HookOrder.values()) {
            for (PlaceListener entry : PLACE_LISTENERS.get(order)) {
                if (!placed.isCancelled() || entry.receiveCancelled()) {
                    entry.listener().accept(placed);
                }
            }
        }
    }

    /^* A machine's placement (never a probe), already decided: the protection providers asked before it is posted. *^/
    private record Placed(Level level, BlockPos pos, BlockState placedAgainst, Entity entity, boolean isCancelled)
            implements GameHooks.BlockPlace {
        @Override
        public BlockState placedBlock() {
            return level.getBlockState(pos);
        }

        @Override
        public boolean isProbe() {
            return false;
        }
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
