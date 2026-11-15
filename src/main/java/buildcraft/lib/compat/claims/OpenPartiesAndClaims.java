/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.claims;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import xaero.pac.common.server.api.OpenPACServerAPI;

import buildcraft.api.core.BCLog;
import buildcraft.platform.Platform;

/**
 * Open Parties and Claims' answer to "may this machine place a block here?", for the placement probes the loaders' own
 * claim hooks never put to it. OPaC guards a player's placement through the clicked block (a right-click on a claimed
 * cell) and, on NeoForge, a placement event whose placed block it waves through when it is air or when the block's own
 * item may be used there; it has no Fabric placement event at all and registers nothing with Common Protection API. A
 * machine's placement probe ({@code canPlace}: a Quarry's frame, the Blueprint Builder) names the cell's current air,
 * which the handler never refuses, and a pipe's extension names the pipe, which it lets through when the pipe's item
 * may be used there. So the machine asks OPaC's own server API, the route OPaC documents for exactly these callers
 * (a placement the loader event is "not specific enough" for, and Fabric): the chunk protection's
 * {@code onEntityPlaceBlock} with no block state, which judges the placer against the claim and the claim's
 * exceptions and nothing else. With no state none of the handler's block exemptions apply (air, a replaceable block,
 * an item usable there), so a stranger's machine is refused over grass as over air. The machine's fake player carries
 * its owner's profile, so a claim's owner and party pass and everyone else is refused, as OPaC already answers the
 * break probe.
 *
 * <p>Not asked: a machine's item click ({@code BlockUtil#tryUseItemOnBlock}: the Filler, a stripes pipe's handlers).
 * With {@code minePlayerProtected} off it posts the right-click first, which OPaC does answer on both loaders (its
 * guard judges the clicked cell and the cell beside the face), so a claimed cell refuses it already: the
 * {@code crossmod-claims-opac} Filler, stripes pipe
 * and Stripes robot probes are refused by a build without this class (#203), where the Quarry frame and Builder
 * probes are let through.
 *
 * <p>Soft dependency: the API is named only inside {@link Api}, which the JVM loads on the first ask once OPaC is
 * loaded, so BuildCraft runs without OPaC and a world without it never touches this. The mod is compiled against, never
 * shipped (the same shape as the Curios lookup). If OPaC is loaded and the call fails (an API that moved), the ask is
 * refused, once logged: a protection check that cannot be made must not let the placement through.
 *
 * <p>Asked from both loaders' {@code PlatformProtection}: the placement probe ({@code canPlace}) and the block a
 * stripes pipe extends into ({@code canPlaceOver}). Server thread only, like every caller.
 */
public final class OpenPartiesAndClaims {

    private static final String MOD_ID = "openpartiesandclaims";

    private OpenPartiesAndClaims() {}

    /** Read on the first ask, not at class load: the loader has finished loading its mod list by then. */
    private static final class Presence {
        static final boolean LOADED = Platform.loader().isModLoaded(MOD_ID);
    }

    /**
     * Whether {@code placer} may set a block down at {@code pos}. True when OPaC is not loaded, and for a level that is
     * not a server level (nothing to claim).
     *
     * @param placer the machine's fake player (or the entity placing); null asks as no one, which OPaC treats as an
     *               entity that is never a player
     */
    public static boolean mayPlace(Level level, BlockPos pos, @Nullable Entity placer) {
        return !Presence.LOADED || !(level instanceof ServerLevel server) || Api.mayPlace(server, pos, placer);
    }

    /** {@link #mayPlace} for every cell a placement fills, the first refusal ending the ask. */
    public static boolean mayPlaceAll(Level level, Iterable<BlockPos> cells, @Nullable Entity placer) {
        if (!Presence.LOADED) {
            return true;
        }
        for (BlockPos cell : cells) {
            if (!mayPlace(level, cell, placer)) {
                return false;
            }
        }
        return true;
    }

    /** The one place OPaC's classes are named. */
    private static final class Api {
        /** Whether a failure has been logged already: once per run is enough, a placement is asked every few ticks. */
        private static boolean failureLogged;

        static boolean mayPlace(ServerLevel level, BlockPos pos, @Nullable Entity placer) {
            try {
                // null state: no block-specific exception applies, so a plain block is not waved through because its
                // item "may be used" at the cell (OPaC's own shortcut for a placement event that names the block).
                return !OpenPACServerAPI.get(level.getServer()).getChunkProtection()
                        .onEntityPlaceBlock((BlockState) null, placer, level, pos);
            } catch (RuntimeException | LinkageError e) {
                if (!failureLogged) {
                    failureLogged = true;
                    BCLog.logger.error("[compat.claims] Open Parties and Claims could not be asked about a placement at "
                            + pos + "; every machine placement is refused while it cannot", e);
                }
                return false;
            }
        }
    }
}
