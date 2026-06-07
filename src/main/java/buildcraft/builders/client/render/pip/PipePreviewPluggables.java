/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render.pip;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pluggable.IPlugDynamicRenderer;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.client.PipeRegistryClient;
import buildcraft.transport.client.model.PipeModelCachePluggable;
import buildcraft.transport.client.model.PipeModelCachePluggable.PluggableKey;

/**
 * Client-only companion to {@link PipePreviewModel}: reconstructs a captured pipe's pluggables
 * (plugs, gates, lenses, filters, wires, facades) offline from its tile NBT and renders them into the
 * snapshot 3D preview, the same way the pipe body is. Static-baked pluggables (plugs/lenses/filters/
 * facades) come from {@link PipeModelCachePluggable} as vanilla {@code BakedQuad}s; dynamic-rendered
 * ones (gates and pulsars contribute zero static quads, to keep chunk re-meshes cheap) are drawn
 * through their registered {@link IPlugDynamicRenderer}.
 * <p>
 * Kept separate from {@link PipePreviewModel} so the body-model-key path stays free of any
 * {@code net.minecraft.client} / GL reference (it's exercised on a dedicated server by the
 * {@code pipe_preview_model_key} game test); this class is reached only from the two client preview
 * renderers. Any reconstruction/baking failure (e.g. a facade whose camouflage model isn't
 * available) is swallowed so a bad pluggable degrades to "pipe body only" rather than crashing.
 */
@SuppressWarnings("deprecation")
public final class PipePreviewPluggables {

    private PipePreviewPluggables() {}

    /** One-shot guard so a recurring per-frame failure logs once, not every frame. */
    private static boolean loggedFailure = false;

    /**
     * Renders every pluggable captured in {@code tileNbt} into {@code buffers}, at the cell the given
     * {@code poseStack} is already translated to. No-op if there are no pluggables or reconstruction
     * fails. Takes the PoseStack (not just its top Pose) because the dynamic-renderer pluggables
     * (gates, pulsars) apply their own per-side rotation.
     */
    public static void render(@Nullable CompoundTag tileNbt, PoseStack poseStack,
            MultiBufferSource buffers, int light) {
        if (tileNbt == null) {
            return;
        }
        CompoundTag plugs = NBTUtilBC.getCompound(tileNbt, "plugs");
        if (plugs.isEmpty()) {
            return;
        }
        try {
            Holder holder = new Holder();
            boolean any = false;
            for (Direction face : Direction.values()) {
                if (!plugs.contains(face.getName())) {
                    continue;
                }
                CompoundTag entry = NBTUtilBC.getCompound(plugs, face.getName());
                String id = NBTUtilBC.getString(entry, "id", "");
                if (id.isEmpty()) {
                    continue;
                }
                PluggableDefinition def = PipeApi.pluggableRegistry != null
                        ? PipeApi.pluggableRegistry.getDefinition(Identifier.parse(id)) : null;
                if (def == null) {
                    continue;
                }
                CompoundTag data = NBTUtilBC.getCompound(entry, "data");
                PipePluggable plug = def.readFromNbt(holder, face, data);
                if (plug != null) {
                    holder.plugs[face.ordinal()] = plug;
                    any = true;
                }
            }
            if (!any) {
                return;
            }
            PoseStack.Pose pose = poseStack.last();
            // Static-baked pluggables (plugs, lenses, filters, facades): their quads come from the
            // pluggable model cache — cutout then translucent (lens glass / colour overlays), the same
            // render types the pipe body uses so they sit together.
            renderQuads(PipeModelCachePluggable.cacheCutoutAll.bake(new PluggableKey(true, holder)),
                    pose, buffers.getBuffer(BCLibRenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS)), light);
            renderQuads(PipeModelCachePluggable.cacheTranslucentAll.bake(new PluggableKey(false, holder)),
                    pose, buffers.getBuffer(BCLibRenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)), light);
            // Dynamic-renderer pluggables (gates, pulsars) contribute ZERO static quads — in-world a
            // registered per-frame renderer draws them (so toggling one doesn't force a chunk re-mesh).
            // Drive that same renderer here. It applies its own per-side rotation (hence the PoseStack),
            // and offline there's no live world so PlugGateRenderer falls back to full-bright.
            VertexConsumer dynBuffer =
                    buffers.getBuffer(BCLibRenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS));
            for (PipePluggable plug : holder.plugs) {
                if (plug == null) {
                    continue;
                }
                IPlugDynamicRenderer<PipePluggable> dyn = PipeRegistryClient.getPlugRenderer(plug);
                if (dyn != null) {
                    dyn.render(plug, 0, 0, 0, 0, dynBuffer, poseStack);
                }
            }
        } catch (Throwable t) {
            // Degrade to pipe-body-only on any offline reconstruction/bake failure. Log once — this was
            // a silent black box before, so surface it if a future pluggable can't be rebuilt offline.
            if (!loggedFailure) {
                loggedFailure = true;
                org.apache.logging.log4j.LogManager.getLogger("BCPipePreviewPluggables").warn(
                        "Failed to render captured pluggables in the snapshot preview "
                                + "(degrading to pipe-body-only)", t);
            }
        }
    }

    private static void renderQuads(List<BakedQuad> quads, PoseStack.Pose pose, VertexConsumer vc, int light) {
        for (BakedQuad quad : quads) {
            emit(vc, pose, quad, light);
        }
    }

    /** Emit one baked quad through the {@link MutableQuad} path the pipe BODY already uses — which is
     *  version-agnostic and proven on every node, unlike the raw BakedQuad sinks. (26.1's
     *  {@code putBakedQuad} multiplies in the quad's own baked colours, which rendered the pluggables
     *  invisible in the offscreen preview; pre-26.1 {@code putBulkData} is a different signature again.)
     *  A fresh MutableQuad's vertices default to opaque white and {@code fromBakedItem} copies
     *  position/UV/sprite but not colour, so the pluggable's texture shows as-is; full-bright light
     *  matches the rest of the preview. Same call shape as {@code PlugBakerFacade}. */
    private static void emit(VertexConsumer vc, PoseStack.Pose pose, BakedQuad quad, int light) {
        new MutableQuad().fromBakedItem(quad).lighti(light).render(pose, vc);
    }

    /**
     * Render-only {@link IPipeHolder} good enough to reconstruct pluggables and read their model
     * keys. Mirrors {@link PipePreviewModel}'s body stub, but holds the reconstructed pluggables so
     * {@link PluggableKey} can collect them. World access never happens during model-key generation,
     * so every method safely returns a null/empty/no-op default.
     */
    private static final class Holder implements IPipeHolder {
        final PipePluggable[] plugs = new PipePluggable[6];

        @Override public Level getPipeWorld() { return null; }
        @Override public BlockPos getPipePos() { return BlockPos.ZERO; }
        @Override public BlockEntity getPipeTile() { return null; }
        @Override public IPipe getPipe() { return null; }
        @Override public boolean canPlayerInteract(Player player) { return false; }
        @Override public PipePluggable getPluggable(Direction side) { return plugs[side.ordinal()]; }
        @Override public BlockEntity getNeighbourTile(Direction side) { return null; }
        @Override public IPipe getNeighbourPipe(Direction side) { return null; }
        @Override public <T> T getCapabilityFromPipe(Direction side, Object capability) { return null; }
        @Override public IWireManager getWireManager() { return null; }
        @Override public GameProfile getOwner() { return null; }
        @Override public boolean fireEvent(PipeEvent event) { return false; }
        @Override public void scheduleRenderUpdate() {}
        @Override public void scheduleNetworkUpdate(PipeMessageReceiver... parts) {}
        @Override public void scheduleNetworkGuiUpdate(PipeMessageReceiver... parts) {}
        @Override public void sendMessage(PipeMessageReceiver to, IWriter writer) {}
        @Override public void sendGuiMessage(PipeMessageReceiver to, IWriter writer) {}
        @Override public void onPlayerOpen(Player player) {}
        @Override public void onPlayerClose(Player player) {}
        @Override public int getRedstoneInput(Direction side) { return 0; }
        @Override public boolean setRedstoneOutput(Direction side, int value) { return false; }
    }
}
