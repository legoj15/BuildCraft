/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge && >=1.21.10 {
/*import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import net.minecraftforge.event.OnDatapackSyncEvent;

import buildcraft.api.core.BCLog;
import buildcraft.platform.Platform;
import buildcraft.platform.network.PacketContext;

/^*
 * NeoForge's opt-in recipe sync ({@code OnDatapackSyncEvent#sendRecipes}, received as {@code RecipesReceivedEvent}) on
 * MinecraftForge, which has neither: since MC 1.21.2 the server no longer sends a client the recipe list, and the guide
 * book's crafting and smelting panels need it on a client of a remote server. Fabric answers with Fabric API's
 * per-serializer sync; Forge has nothing to switch on (its own datapack-sync event can add nothing to vanilla's
 * recipe packet, which carries only the stonecutter recipes and the item properties), so BuildCraft sends the recipes
 * itself, as NeoForge's own {@code RecipeContentPayload} does, over a payload of BuildCraft's.
 *
 * <p><b>Why the data codec, not {@code RecipeHolder.STREAM_CODEC}.</b> NeoForge ships each recipe through its serializer's
 * stream codec. Forge 26.3-66.1.8 cannot: its {@code Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC} (the codec of every empty
 * slot of a shaped recipe) builds an empty {@code Ingredient} to encode with and decodes into one, and the constructor
 * refuses an empty ingredient ("Ingredients can't be empty"): every shaped recipe with a gap fails to encode, and would
 * fail to decode on the client (measured at boot: 295 of the 1518 crafting and smelting recipes, vanilla's boats, signs
 * and stairs among them). Each recipe goes as its id and {@code Recipe.DIRECT_CODEC}'s NBT instead: the codec every
 * recipe is loaded from, which round-trips a gap (all 1518 re-encode to the same bytes).
 *
 * <p><b>Server side.</b> {@link #request} (called by {@code ForgeGameHooks#syncRecipeTypes} from the common init)
 * registers the payload and one {@code OnDatapackSyncEvent} listener, which runs on every login and every
 * {@code /reload}, as NeoForge's does, and sends the players it names every recipe of the requested types. Three
 * departures from NeoForge's one payload, all for Forge 26.3's missing packet splitter (a clientbound payload is held to
 * vanilla's 1 MiB): the recipes go in {@link #BATCH}-sized payloads, each tagged with its place in the run, the client
 * joins them; a recipe whose serializer cannot encode (a third party's, which NeoForge would let kill the login) is
 * encoded once in a scratch buffer first and left out, with one warning; and a run is sent even when empty, so the
 * client's store refreshes as it does for an empty NeoForge map.
 *
 * <p><b>Client side.</b> The payload handler collects a run and, when its last batch has arrived, hands the whole list
 * to the listeners added with {@link #addClientListener}. That method is the hook the client slice fills: Forge's
 * {@code ClientHooks#onRecipesReceived} (F6) is one call to it, feeding {@code ClientGuideRecipeCache} exactly as
 * NeoForge's event and Fabric's synchronized-recipes event do. This class names no client type, so a server loads it.
 * Clearing the previous server's recipes on logout stays with the client slice ({@code onLoggingOut}).
 *^/
public final class ForgeRecipeSync {
    /^* Recipes per payload at most: a crafting recipe encodes to about a hundred bytes, a gate-recipe components
     *  ingredient to a few hundred. ^/
    static final int BATCH = 128;
    /^* Encoded bytes per payload at most. The count alone bounds nothing: the run carries every mod's crafting and
     *  smelting recipes, and one pack's oversized recipes would push a batch past the 1 MiB a clientbound payload may be
     *  on Forge 26.3 (no packet splitter), which drops the client at login. Half the cap leaves room for framing. ^/
    static final int BATCH_BYTES = 512 * 1024;

    private static final List<RecipeType<?>> TYPES = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Collection<RecipeHolder<?>>>> CLIENT_LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean registered;
    /^* The run being received (client main thread only), or null between runs. ^/
    private static List<RecipeHolder<?>> run;

    private ForgeRecipeSync() {}

    /^* One slice of a run of recipes: batch {@code index} of {@code count}. ^/
    public record Batch(int index, int count, List<RecipeHolder<?>> recipes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Batch> TYPE = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("buildcraftunofficial", "recipe_sync"));

        /^* A recipe as its id and the data codec's NBT (see the class comment). ^/
        static final StreamCodec<RegistryFriendlyByteBuf, RecipeHolder<?>> HOLDER = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.RECIPE), RecipeHolder::id,
                ByteBufCodecs.fromCodecWithRegistries(Recipe.DIRECT_CODEC), RecipeHolder::value,
                RecipeHolder::new);

        public static final StreamCodec<RegistryFriendlyByteBuf, Batch> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Batch::index,
                ByteBufCodecs.VAR_INT, Batch::count,
                HOLDER.apply(ByteBufCodecs.list()), Batch::recipes,
                Batch::new);

        @Override
        public CustomPacketPayload.Type<Batch> type() {
            return TYPE;
        }
    }

    /^* Has the recipes of {@code types} sent to every client with the datapack contents. From the common init. ^/
    public static synchronized void request(Collection<RecipeType<?>> types) {
        TYPES.addAll(types);
        if (registered) {
            return;
        }
        registered = true;
        Platform.network().clientbound(Batch.TYPE, Batch.STREAM_CODEC, ForgeRecipeSync::receive);
        ForgeBus.listen(OnDatapackSyncEvent.class, event -> {
            MinecraftServer server = event.getPlayerList().getServer();
            List<Batch> batches = batches(server, Set.copyOf(TYPES));
            for (ServerPlayer player : event.getPlayers()) {
                for (Batch batch : batches) {
                    Platform.network().sendToPlayer(player, batch);
                }
            }
        });
    }

    /^* The run of every recipe of {@code types} the server holds, as payloads of at most {@link #BATCH} recipes. ^/
    static List<Batch> batches(MinecraftServer server, Set<RecipeType<?>> types) {
        RegistryAccess registries = server.registryAccess();
        List<RecipeHolder<?>> sendable = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        List<String> left = new ArrayList<>();
        RuntimeException firstFailure = null;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (types.contains(holder.value().getType())) {
                int size;
                try {
                    size = encodedSize(holder, registries);
                    if (size > BATCH_BYTES) {
                        throw new IllegalStateException("encodes to " + size + " bytes, over the per-payload budget");
                    }
                } catch (RuntimeException failure) {
                    left.add(holder.id().identifier().toString());
                    if (firstFailure == null) {
                        firstFailure = failure;
                    }
                    continue;
                }
                sendable.add(holder);
                sizes.add(size);
            }
        }
        if (!left.isEmpty()) {
            BCLog.logger.warn("[recipe sync] {} recipes cannot be encoded and are not sent to clients (their guide panels"
                    + " stay empty), first: {}", left.size(), left.subList(0, Math.min(left.size(), 5)), firstFailure);
        }
        // Cut a batch at the recipe count or the byte budget, whichever comes first. An empty run is still one
        // (empty) batch, so the client learns the server has none.
        List<List<RecipeHolder<?>>> cuts = new ArrayList<>();
        int from = 0;
        int bytes = 0;
        for (int i = 0; i < sendable.size(); i++) {
            if (i > from && (i - from == BATCH || bytes + sizes.get(i) > BATCH_BYTES)) {
                cuts.add(List.copyOf(sendable.subList(from, i)));
                from = i;
                bytes = 0;
            }
            bytes += sizes.get(i);
        }
        cuts.add(List.copyOf(sendable.subList(from, sendable.size())));
        List<Batch> out = new ArrayList<>(cuts.size());
        for (int i = 0; i < cuts.size(); i++) {
            out.add(new Batch(i, cuts.size(), cuts.get(i)));
        }
        return out;
    }

    /^* How many bytes {@code holder} encodes to; throws what its codec throws when it cannot be encoded. ^/
    private static int encodedSize(RecipeHolder<?> holder, RegistryAccess registries) {
        ByteBuf scratch = Unpooled.buffer();
        try {
            Batch.HOLDER.encode(new RegistryFriendlyByteBuf(scratch, registries), holder);
            return scratch.readableBytes();
        } finally {
            scratch.release();
        }
    }

    /^* The client slice's hook: {@code listener} gets the whole run when its last batch has arrived. ^/
    public static void addClientListener(Consumer<Collection<RecipeHolder<?>>> listener) {
        CLIENT_LISTENERS.add(listener);
    }

    private static void receive(Batch batch, PacketContext ctx) {
        if (batch.index() == 0 || run == null) {
            run = new ArrayList<>();
        }
        run.addAll(batch.recipes());
        if (batch.index() == batch.count() - 1) {
            List<RecipeHolder<?>> complete = List.copyOf(run);
            run = null;
            for (Consumer<Collection<RecipeHolder<?>>> listener : CLIENT_LISTENERS) {
                listener.accept(complete);
            }
        }
    }
}
*///?}
