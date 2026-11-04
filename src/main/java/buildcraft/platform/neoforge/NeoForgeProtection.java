/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import buildcraft.platform.event.PlatformProtection;

/**
 * {@link PlatformProtection} on NeoForge: the events of NeoForge's event bus. Break and place probes are
 * {@link NeoForgeBreakEvent} and {@link NeoForgePlaceProbe}; everything else posts the event NeoForge's own hook for a
 * player's act posts.
 */
public final class NeoForgeProtection implements PlatformProtection {

    @Override
    public boolean canBreak(Level level, BlockPos pos, BlockState state, Player player) {
        return NeoForgeBreakEvent.canBreak(level, pos, state, player);
    }

    @Override
    public boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player) {
        return NeoForgePlaceProbe.canPlace(level, cells, player);
    }

    @Override
    public boolean isProbe(Object event) {
        return NeoForgeBreakEvent.isProbe(event) || NeoForgePlaceProbe.isProbe(event);
    }

    @Override
    public Registration onBreak(Consumer<BlockBreak> listener) {
        return NeoForgeBreakEvent.onBreak(listener);
    }

    /**
     * Posts {@link AttackEntityEvent} directly rather than through {@code CommonHooks.onPlayerAttackTarget} (the same
     * event), whose second half would run the held weapon's own left-click hook.
     */
    @Override
    public boolean canAttack(Player attacker, Entity target) {
        return !NeoForge.EVENT_BUS.post(new AttackEntityEvent(attacker, target)).isCanceled();
    }

    @Override
    public boolean canHarvest(Player player, BlockState state, BlockGetter level, BlockPos pos) {
        return EventHooks.doPlayerHarvestCheck(player, state, level, pos);
    }

    @Override
    public ClickVerdict rightClickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        PlayerInteractEvent.RightClickBlock ask = CommonHooks.onRightClickBlock(player, hand, hit.getBlockPos(), hit);
        if (ask.isCanceled() || ask.getUseItem().isFalse()) {
            return ClickVerdict.REFUSED;
        }
        return ask.getUseItem().isTrue() ? ClickVerdict.FORCE_ITEM : ClickVerdict.DEFAULT;
    }

    @Override
    public boolean canUseItem(Player player, InteractionHand hand) {
        return CommonHooks.onItemRightClick(player, hand) == null;
    }

    /** NeoForge's patched {@code ItemStack.useOn} captures the placement and posts its event itself. */
    @Override
    public InteractionResult useItemOn(UseOnContext context) {
        return context.getItemInHand().useOn(context);
    }

    @Override
    public CapturedBlock capture(ServerLevel level, BlockPos pos) {
        return new Captured(BlockSnapshot.create(level.dimension(), level, pos));
    }

    @Override
    public boolean canPlaceOver(Entity placer, CapturedBlock replaced, Direction face) {
        return !EventHooks.onBlockPlace(placer, ((Captured) replaced).snapshot(), face);
    }

    /** A {@link BlockSnapshot}, the record NeoForge's placement event carries. */
    private record Captured(BlockSnapshot snapshot) implements CapturedBlock {
        @Override
        public BlockPos pos() {
            return snapshot.getPos();
        }

        @Override
        public BlockState state() {
            return snapshot.getState();
        }

        @Override
        public boolean restore(int flags) {
            return snapshot.restore(flags);
        }
    }
}
//?}
