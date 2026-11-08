/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * about the owner ({@link #owner}).
 *
 * <p><b>Placements.</b> Fabric API has no block-placement event, so a placement is asked of the protection providers
 * alone, as NeoForge's placement event is all a machine's placement asks there (vanilla's spawn protection and world
 * border gate a player's click packet, never a machine); a block an item use sets down is recorded as NeoForge's
 * snapshot capture records it ({@link #useItemOn}). BuildCraft's own {@code onBlockPlace} listeners are told about
 * these machine placements, never about a player's.
 *
 * <p><b>Gaps.</b> A claim mod that guards placements through its own mixins alone, not Common Protection API, is not
 * asked about a placement. Common Protection API has no item-use check: an item used in the air asks
 * whether the block the player stands in may be interacted with. Fabric has no harvest-check event:
 * {@link #canHarvest} is vanilla's tool check. Fabric callbacks cannot be unregistered: see {@link #onBreak}.
 *^/
public final class FabricProtection implements PlatformProtection {

    /^* The break probe {@link #canBreak} is invoking right now (the innermost one), or null. Server thread only. *^/
    private static Probe probeInFlight;

    /^* The player whose right-click {@link #rightClickBlock} is asking about right now (the innermost), or null. *^/
    private static Player clickAskInFlight;

    /^* The level a refused item use's blocks are being put back in right now, or null. Server thread only. *^/
    private static Level restoringIn;

    /^* The profile Common Protection API asks about for {@code player}: its name and id (the API's 2.x, 26.1 on), its
     *  game profile on the 1.21.x lines (the API's 1.x). *^/
    //? if >=26.1 {
    public static NameAndId owner(Player player) {
        return player.nameAndId();
    }
    //?} else {
    /^public static com.mojang.authlib.GameProfile owner(Player player) {
        return player.getGameProfile();
    }
    ^///?}

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
        return CommonProtection.canBreakBlock(level, pos, owner(player), player);
    }

    /^*
     * Each cell is asked of the protection providers. Not of vanilla's {@code mayInteract} (spawn protection, the
     * world border): NeoForge's placement probe never asks it, and neither does a machine's break.
     *^/
    @Override
    public boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a placement fills at least one cell");
        }
        for (BlockPos cell : cells) {
            if (!CommonProtection.canPlaceBlock(level, cell, owner(player), player)) {
                return false;
            }
        }
        return true;
    }

    /^*
     * Whether {@link #canBreak} is asking, right now, whether {@code player} may break the block at {@code pos}: the
     * {@link PlayerBlockBreakEvents#BEFORE} callback running is that probe, not a break. For BuildCraft's own
     * callbacks on that event, which a probe must not reach (NeoForge's probe posts its break event and no more).
     *^/
    public static boolean isBreakProbe(Level level, BlockPos pos, Player player) {
        Probe probe = probeInFlight;
        return probe != null && probe.matches(level, pos, player);
    }

    /^*
     * The {@link #onBreak} listeners, in registration order: one {@link PlayerBlockBreakEvents#BEFORE} callback (Fabric
     * events cannot be unregistered) hands each break to them, so a closed registration leaves nothing behind.
     *^/
    private static final List<Consumer<BlockBreak>> BREAK_LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean breakCallbackRegistered;

    /^*
     * Registers on {@link PlayerBlockBreakEvents#BEFORE}, through the one callback BuildCraft registers there on the
     * first call. A listener that cancels ends the break as a refusing callback does: the listeners after it are not
     * told.
     *^/
    @Override
    public Registration onBreak(Consumer<BlockBreak> listener) {
        synchronized (BREAK_LISTENERS) {
            if (!breakCallbackRegistered) {
                breakCallbackRegistered = true;
                PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
                    BlockPos at = pos.immutable();
                    for (Consumer<BlockBreak> each : BREAK_LISTENERS) {
                        View view = new View(level, at, state, player);
                        each.accept(view);
                        if (view.cancelled) {
                            return false;
                        }
                    }
                    return true;
                });
            }
        }
        BREAK_LISTENERS.add(listener);
        return () -> BREAK_LISTENERS.remove(listener);
    }

    @Override
    public boolean canAttack(Player attacker, Entity target) {
        return AttackEntityCallback.EVENT.invoker()
                .interact(attacker, attacker.level(), InteractionHand.MAIN_HAND, target, null) == InteractionResult.PASS
            && CommonProtection.canDamageEntity(attacker.level(), target, owner(attacker), attacker);
    }

    // Gap: no harvest-check event on Fabric; vanilla's tool check.
    @Override
    public boolean canHarvest(Player player, BlockState state, BlockGetter level, BlockPos pos) {
        return player.hasCorrectToolForDrops(state);
    }

    /^*
     * A callback that answered anything but PASS took the click (or refused it): the item is not used either way. A
     * provider refusing the interaction with the clicked block refuses it too. The ask is NeoForge's right-click
     * event and no more: BuildCraft's {@code UseFirstItem} route (NeoForge's {@code onItemUseFirst}, which a
     * machine's click never runs) stays out of it ({@link #isClickAsk}).
     *^/
    @Override
    public ClickVerdict rightClickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        Player outer = clickAskInFlight;
        clickAskInFlight = player;
        InteractionResult result;
        try {
            result = UseBlockCallback.EVENT.invoker().interact(player, player.level(), hand, hit);
        } finally {
            clickAskInFlight = outer;
        }
        if (result != InteractionResult.PASS) {
            return ClickVerdict.REFUSED;
        }
        return CommonProtection.canInteractBlock(player.level(), hit.getBlockPos(), owner(player), player)
            ? ClickVerdict.DEFAULT : ClickVerdict.REFUSED;
    }

    /^* Whether {@link #rightClickBlock} is asking, right now, about a right-click by {@code player}. *^/
    public static boolean isClickAsk(Player player) {
        return player != null && clickAskInFlight == player;
    }

    // Gap: Common Protection API has no item-use check; the block the player stands in is asked (as a claim mod's
    // item right-click listener sees the use: at the player's position).
    @Override
    public boolean canUseItem(Player player, InteractionHand hand) {
        return UseItemCallback.EVENT.invoker().interact(player, player.level(), hand) == InteractionResult.PASS
            && CommonProtection.canInteractBlock(player.level(), player.blockPosition(), owner(player), player);
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
     * placement. A refused placement is put back as NeoForge puts it back: in reverse, with nothing dropping while it
     * is ({@link #isRestoring}: a half that breaks when the cell beside it is put back pops nothing).
     *
     * <p>Gap: unlike NeoForge, the blocks' own placement side effects and neighbour updates have already run when a
     * refused placement is put back.
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
        var profile = player != null ? owner(player) : CommonProtection.UNKNOWN;
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
        List<Captured> changed = new ArrayList<>(capture.before.values());
        Level outerRestore = restoringIn;
        restoringIn = level;
        try {
            for (int i = changed.size() - 1; i >= 0; i--) {
                changed.get(i).restore(Block.UPDATE_ALL);
            }
        } finally {
            restoringIn = outerRestore;
        }
        stack.setCount(before.getCount());
        stack.applyComponents(before.getComponents());
        return InteractionResult.FAIL;
    }

    /^*
     * Whether {@code level} is putting a refused item use's blocks back right now: NeoForge's
     * {@code restoringBlockSnapshots}, which {@code BlockDropsMixin} reads where NeoForge's patched {@code Block} does,
     * so no item or experience drops while it is.
     *^/
    public static boolean isRestoring(Level level) {
        return level != null && restoringIn == level;
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
     * Asked of the protection providers, about the placer's profile (a placer that is no player is
     * {@link CommonProtection#UNKNOWN}, as the API has it); not of vanilla's {@code mayInteract}, as {@link #canPlace}.
     *^/
    @Override
    public boolean canPlaceOver(Entity placer, CapturedBlock replaced, Direction face) {
        if (!(placer.level() instanceof ServerLevel level)) {
            return true;
        }
        Player player = placer instanceof Player p ? p : null;
        var profile = player != null ? owner(player) : CommonProtection.UNKNOWN;
        BlockPos pos = replaced.pos();
        boolean allowed = CommonProtection.canPlaceBlock(level, pos, profile, player);
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
            return isBreakProbe(level, pos, player);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
*///?}
