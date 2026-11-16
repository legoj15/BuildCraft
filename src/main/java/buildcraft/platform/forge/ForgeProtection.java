/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.listener.EventListener;

import buildcraft.lib.compat.claims.OpenPartiesAndClaims;
import buildcraft.platform.event.PlatformProtection;

/^*
 * {@link PlatformProtection} on MinecraftForge (not NeoForge): the events of Forge's own event buses, posted the way
 * Forge's own hooks post them for a player's act. The break probe and listeners and the placement probe
 * ({@link ForgePlaceProbe}) are here; every other ask posts the event Forge's hook for a player's act posts. Placements
 * are also put to Open Parties and Claims' own API ({@link OpenPartiesAndClaims}), whose event handler waves a machine's
 * placement through.
 *
 * <p><b>Why not Forge's hooks.</b> {@code ForgeHooks} and {@code ForgeEventFactory} are {@code @ApiStatus.Internal}, so
 * each ask builds and posts its event itself (the bodies are the one-liners those hooks are). Forge's patched vanilla
 * posts the same events from the same places, so a claim mod that listens for a player's act listens for the
 * machine's.
 *
 * <p><b>Break events.</b> Forge reads a break's outcome from the event's result ({@code Result.DENY}), not from
 * cancellation, and its constructor needs no reflection (NeoForge renamed the class between 26.1.1 and 26.1.2). A probe
 * and a listener's refusal therefore both read and write the result; a probe also counts a cancellation as a refusal,
 * which is the stricter reading and the safe one for a protection check. Forge has no probe flag: the event being posted
 * by {@link #canBreak} is remembered here.
 *
 * <p><b>Harvest.</b> Forge's patched {@code Player.hasCorrectToolForDrops(BlockState)} posts the harvest check itself,
 * starting from vanilla's answer, so {@link #canHarvest} asks it and posts nothing more (posting too would run the check
 * twice).
 *
 * <p><b>Click.</b> Forge answers a refused right-click with the cancellation result at once and consults the use results
 * only when the event was not cancelled, so {@link #rightClickBlock} reads cancellation first, then {@code useItem}.
 *^/
public final class ForgeProtection implements PlatformProtection {

    private static final Logger LOGGER = LogUtils.getLogger();

    /^* The probe {@link #canBreak} is posting right now (the innermost one, if a listener probes again), or null. *^/
    @Nullable
    private static BlockEvent.BreakEvent breakProbeInFlight;

    @Override
    public boolean canBreak(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player, Result.DEFAULT);
        BlockEvent.BreakEvent outer = breakProbeInFlight;
        breakProbeInFlight = event;
        boolean cancelled;
        try {
            cancelled = BlockEvent.BreakEvent.BUS.post(event);
        } finally {
            breakProbeInFlight = outer;
        }
        return !cancelled && !event.getResult().isDenied();
    }

    /^*
     * The placement event's probe, then Open Parties and Claims' own placement ask: the probe's placed block is the
     * cell's current one (air), which OPaC's event handler lets through whatever the claim ({@link OpenPartiesAndClaims}).
     *^/
    @Override
    public boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player) {
        return ForgePlaceProbe.canPlace(level, cells, player)
            && OpenPartiesAndClaims.mayPlaceAll(level, cells, player);
    }

    /^*
     * Registers on {@link BlockEvent.BreakEvent#BUS} as a predicate, so a listener that cancels ends the chain as a
     * cancelling listener does (the ones after it are not told). A break something before it already refused (result
     * denied, which is also how Forge marks a break its own pre-checks refused) is not passed on.
     *^/
    @Override
    public Registration onBreak(Consumer<BlockBreak> listener) {
        Predicate<BlockEvent.BreakEvent> each = event -> {
            if (event.getResult().isDenied()) {
                return false;
            }
            BreakView view = new BreakView(event);
            listener.accept(view);
            if (!view.cancelled) {
                return false;
            }
            event.setResult(Result.DENY);
            return true;
        };
        EventListener registered = BlockEvent.BreakEvent.BUS.addListener(each);
        return new Registration() {
            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    BlockEvent.BreakEvent.BUS.removeListener(registered);
                }
            }
        };
    }

    /^*
     * Posts {@link AttackEntityEvent} directly rather than through {@code ForgeHooks.onPlayerAttackTarget} (the same
     * event, and internal), whose second half would run the held weapon's own left-click hook.
     *^/
    @Override
    public boolean canAttack(Player attacker, Entity target) {
        return !AttackEntityEvent.BUS.post(new AttackEntityEvent(attacker, target));
    }

    // Gap: Forge's HarvestCheck carries no level or position (NeoForge's does), so a claim mod that decides harvest
    // by position cannot be asked here; `level` and `pos` go unused. Breaking itself is still asked by position.
    @Override
    public boolean canHarvest(Player player, BlockState state, BlockGetter level, BlockPos pos) {
        return player.hasCorrectToolForDrops(state);
    }

    @Override
    public ClickVerdict rightClickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        PlayerInteractEvent.RightClickBlock ask = new PlayerInteractEvent.RightClickBlock(player, hand,
                hit.getBlockPos(), hit);
        if (PlayerInteractEvent.RightClickBlock.BUS.post(ask) || ask.getUseItem().isDenied()) {
            return ClickVerdict.REFUSED;
        }
        return ask.getUseItem().isAllowed() ? ClickVerdict.FORCE_ITEM : ClickVerdict.DEFAULT;
    }

    /^* The event Forge's {@code ServerPlayerGameMode.useItem} posts before the item is used; nothing cancelled means go on. *^/
    @Override
    public boolean canUseItem(Player player, InteractionHand hand) {
        return !PlayerInteractEvent.RightClickItem.BUS.post(new PlayerInteractEvent.RightClickItem(player, hand));
    }

    /^*
     * Forge's patched {@code ItemStack.useOn} captures the placement and posts its event itself. It also keeps the
     * stack the placement event sees apart from the one the use left: {@code ForgeHooks.onPlaceItemIntoWorld} copies the
     * stack before the use and, when a listener refuses the placement, puts that COPY in the player's hand and leaves
     * the caller's stack object holding what the use made of it (a seed or a block item one short). NeoForge's hook
     * restores the caller's stack in place instead, and {@link PlatformProtection#useItemOn} promises that ("the stack
     * is restored"): every caller goes on reading the stack object it passed. So a refusal is put back here the same
     * way, and the caller's stack goes back in the hand.
     *^/
    @Override
    public InteractionResult useItemOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player == null || context.getLevel().isClientSide()) {
            return stack.useOn(context);
        }
        ItemStack before = stack.copy();
        InteractionResult result = stack.useOn(context);
        ItemStack held = player.getItemInHand(context.getHand());
        // Only the hook's own refusal puts a content-equal copy of the pre-use stack in the hand; a use that moved some
        // other stack into the hand (a transformed one) keeps it, and the caller reads it from the result.
        if (result == InteractionResult.FAIL && held != stack && ItemStack.matches(held, before)) {
            stack.setCount(held.getCount());
            stack.applyComponents(held.getComponents());
            player.setItemInHand(context.getHand(), stack);
        }
        return result;
    }

    @Override
    public CapturedBlock capture(ServerLevel level, BlockPos pos) {
        return new Captured(BlockSnapshot.create(level.dimension(), level, pos));
    }

    /^*
     * Open Parties and Claims first, then the placement event: a placement OPaC refuses posts no event, so no
     * {@code onBlockPlace} listener is told about a placement that then does not happen. The event is the one
     * {@code ForgeEventFactory.onBlockPlace} posts: placed against the block behind the face.
     *^/
    @Override
    public boolean canPlaceOver(Entity placer, CapturedBlock replaced, Direction face) {
        if (!OpenPartiesAndClaims.mayPlace(placer.level(), replaced.pos(), placer)) {
            return false;
        }
        BlockSnapshot snapshot = ((Captured) replaced).snapshot();
        BlockState placedAgainst = placer.level().getBlockState(snapshot.getPos().relative(face.getOpposite()));
        return !BlockEvent.EntityPlaceEvent.BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, placedAgainst, placer));
    }

    /^*
     * A {@link BlockSnapshot}, the record Forge's placement event carries. Forge's own restore methods take a force and a
     * notify flag, not {@code setBlock} flags, so this restores the way NeoForge's {@code restore(int)} does: the state
     * with the caller's flags, the clients told, then the block entity's data loaded back.
     *^/
    private record Captured(BlockSnapshot snapshot) implements CapturedBlock {
        @Override
        public BlockPos pos() {
            return snapshot.getPos();
        }

        @Override
        public BlockState state() {
            return snapshot.getReplacedBlock();
        }

        @Override
        public boolean restore(int flags) {
            LevelAccessor level = snapshot.getLevel();
            if (level == null) {
                return false;
            }
            BlockPos pos = snapshot.getPos();
            BlockState replaced = snapshot.getReplacedBlock();
            BlockState current = snapshot.getCurrentBlock();
            if (!level.setBlock(pos, replaced, flags)) {
                return false;
            }
            if (level instanceof Level realLevel) {
                realLevel.sendBlockUpdated(pos, current, replaced, flags);
            }
            CompoundTag tag = snapshot.getTag();
            if (tag != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    try (ProblemReporter.ScopedCollector problems =
                            new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
                        blockEntity.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), tag));
                    }
                    blockEntity.setChanged();
                }
            }
            return true;
        }
    }

    /^* A delivered break event, as a listener sees it; a refusal is remembered here and written to the event by the
     *  registered predicate. *^/
    private static final class BreakView implements BlockBreak {
        private final BlockEvent.BreakEvent event;
        boolean cancelled;

        BreakView(BlockEvent.BreakEvent event) {
            this.event = event;
        }

        @Override
        public LevelAccessor level() {
            return event.getLevel();
        }

        @Override
        public BlockPos pos() {
            return event.getPos();
        }

        @Override
        public BlockState state() {
            return event.getState();
        }

        @Override
        public Player player() {
            return event.getPlayer();
        }

        @Override
        public boolean isProbe() {
            return event == breakProbeInFlight;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
*///?}
