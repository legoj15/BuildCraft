/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts.recipe;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Client-side store of the recipes the guide book may display.
 *
 * <p>Since MC 1.21.2 the server no longer syncs the full recipe list to clients, so the guide
 * used to read the integrated server's RecipeManager — which is null on dedicated/LAN-joined
 * clients, silently dropping every crafting and smelting panel in multiplayer. Instead, the
 * server now requests NeoForge's opt-in recipe sync ({@code OnDatapackSyncEvent.sendRecipes}
 * in BCCore) and the client stores the result here when
 * {@code RecipesReceivedEvent} fires (on the 1.21.1 node, where vanilla still syncs all
 * recipes itself, {@code RecipesUpdatedEvent} feeds this store instead — see BCLibClient).
 * Both events fire on every world join and again on datapack {@code /reload}, and the
 * BCLibClient handler follows each refresh with a guide reload, so panels re-bake from
 * current data and never go stale across server switches.
 *
 * <p>Cleared on {@code ClientPlayerNetworkEvent.LoggingOut}. When the store is empty (e.g. a
 * BuildCraft server too old to send the sync), {@link #getAllRecipeHolders()} falls back to
 * the integrated server's RecipeManager, preserving the old singleplayer behaviour; with no
 * integrated server either, lookups see an empty list — pages render without recipe panels
 * rather than erroring. */
public class ClientGuideRecipeCache {

    private static volatile List<RecipeHolder<?>> synced = ImmutableList.of();
    private static volatile boolean seenSyncThisConnection = false;

    private ClientGuideRecipeCache() {}

    /** Replaces the store with the holders just received from the server. Called from the
     * recipe-sync event handlers in BCLibClient (render thread) and from game tests. */
    public static void setSynced(Collection<RecipeHolder<?>> holders) {
        synced = ImmutableList.copyOf(holders);
        seenSyncThisConnection = true;
    }

    /** Forgets the previous server's recipes (logout, and game-test cleanup). */
    public static void clear() {
        synced = ImmutableList.of();
        seenSyncThisConnection = false;
    }

    /** True once any sync (even an empty one) has arrived on the current connection —
     * distinguishes the join-time sync (guide warm-load, hidden by the loading screen) from
     * a mid-session datapack /reload re-sync (deferred re-parse; see
     * GuideManager.onRecipeDataReceived). Reset by {@link #clear()} on logout. */
    public static boolean hasSeenSyncThisConnection() {
        return seenSyncThisConnection;
    }

    /** Every recipe the guide may display. The synced store when present, otherwise the
     * integrated server's live RecipeManager (singleplayer fallback), otherwise empty. */
    public static Iterable<RecipeHolder<?>> getAllRecipeHolders() {
        List<RecipeHolder<?>> current = synced;
        if (!current.isEmpty()) {
            return current;
        }
        // The fallback must not even LINK net.minecraft.client.Minecraft unless we are on the
        // client dist: dedicated/game-test servers exercise this path through the merged dev
        // jar, and on the 1.21.1 line FML's RuntimeDistCleaner hard-fails the class load on
        // DEDICATED_SERVER (newer FML merely returns null from getInstance()). Hence the
        // dist guard plus a nested holder so the client-only reference resolves lazily.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            return ClientFallback.integratedServerRecipes();
        }
        return ImmutableList.of();
    }

    /** Client-dist-only indirection: keeps the {@link Minecraft} reference out of any code
     * path a server dist can execute (see the class-load note in getAllRecipeHolders). */
    private static final class ClientFallback {
        static Iterable<RecipeHolder<?>> integratedServerRecipes() {
            Minecraft mc = Minecraft.getInstance();
            MinecraftServer server = mc == null ? null : mc.getSingleplayerServer();
            if (server != null) {
                return server.getRecipeManager().getRecipes();
            }
            return ImmutableList.of();
        }
    }
}
