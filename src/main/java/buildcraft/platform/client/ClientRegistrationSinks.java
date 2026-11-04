/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import java.util.function.Supplier;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Client-only registration sinks: plain register calls made during BuildCraft's client initialisation, with no loader
 * event in sight (docs/platform-layer.md, "Client render").
 *
 * <p>On NeoForge each call is queued and replayed, in call order, inside the matching mod-bus event
 * ({@code RegisterMenuScreensEvent}, {@code EntityRenderersEvent.RegisterRenderers} and so on), so the registration
 * order is the one BuildCraft's init makes. On Fabric the same calls run immediately. Registry-backed arguments are
 * therefore {@link Supplier}s: on NeoForge the client init runs during mod construction, before the registries are
 * filled, and a supplier is only read once its event fires.
 */
public interface ClientRegistrationSinks {

    /** The screen a menu type opens. */
    <M extends AbstractContainerMenu, U extends net.minecraft.client.gui.screens.Screen
            & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void menuScreen(
            Supplier<? extends MenuType<M>> type, MenuScreens.ScreenConstructor<M, U> screen);

    /** A block entity renderer. */
    //? if >=1.21.10 {
    <T extends BlockEntity, S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> void
            blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
                    BlockEntityRendererProvider<T, S> provider);
    //?} else {
    /*<T extends BlockEntity> void blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
            BlockEntityRendererProvider<T> provider);*/
    //?}

    /** An entity renderer. */
    <T extends Entity> void entityRenderer(Supplier<? extends EntityType<? extends T>> type,
            EntityRendererProvider<T> provider);

    /** A particle provider built from the particle type's sprite set. */
    <T extends ParticleOptions> void particleSpriteSet(Supplier<? extends ParticleType<T>> type,
            SpriteParticleFactory<T> factory);

    /** A HUD layer drawn above every other layer. */
    void guiLayerAboveAll(Identifier id, GuiLayer layer);

    /** A HUD layer drawn directly above one of the vanilla layers. */
    void guiLayerAbove(VanillaGuiLayer anchor, Identifier id, GuiLayer layer);

    //? if >=1.21.10 {
    /** A data-driven item tint source type, referenced by id from item model JSON. */
    void itemTintSource(Identifier id,
            com.mojang.serialization.MapCodec<? extends net.minecraft.client.color.item.ItemTintSource> codec);
    //?} else {
    /*/^* A classic per-item colour handler (the line has no data-driven item tint sources). The items are read when the
     *  colour handlers register; an empty array registers nothing. *^/
    void itemColor(net.minecraft.client.color.item.ItemColor color,
            Supplier<net.minecraft.world.level.ItemLike[]> items);*/
    //?}

    //? if >=26.1 {
    /** The block's tint sources, one per tint index; the list is built when the colour handlers register. */
    void blockTintSources(Supplier<? extends Block> block,
            Supplier<java.util.List<net.minecraft.client.color.block.BlockTintSource>> sources);
    //?} else {
    /*/^* The block's classic colour handler. *^/
    void blockColor(net.minecraft.client.color.block.BlockColor color, Supplier<? extends Block> block);*/
    //?}

    //? if >=26.3 {
    /** A custom render pipeline, so the renderer knows it before any render type that uses it is queried. */
    void renderPipeline(Supplier<com.mojang.renderpearl.api.pipeline.RenderPipeline> pipeline);
    //?} elif >=1.21.10 {
    /*/^* A custom render pipeline, so the renderer knows it before any render type that uses it is queried. *^/
    void renderPipeline(Supplier<com.mojang.blaze3d.pipeline.RenderPipeline> pipeline);*/
    //?}

    //? if >=1.21.10 {
    /** A sprite source type, referenced by id from atlas JSON. */
    void spriteSource(Identifier id,
            com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.texture.atlas.SpriteSource> codec);
    //?} else {
    /*/^* A sprite source type, referenced by id from atlas JSON. *^/
    void spriteSourceType(Identifier id, net.minecraft.client.renderer.texture.atlas.SpriteSourceType type);*/
    //?}

    /** A client resource reload listener (resource packs, F3+T). The id is ignored where the loader has none. */
    void clientReloadListener(Identifier id, PreparableReloadListener listener);

    /**
     * Runs {@code listener} each time the models bake (at startup and on every resource reload), before the baked
     * models go live (NeoForge: {@code ModelEvent.ModifyBakingResult}; Fabric: the start of each model load). Listeners
     * run in registration order, interleaved with the {@link #editBakedModels} editors.
     */
    void onModelsBaking(Runnable listener);

    //? if >=1.21.10 {
    /**
     * Edits the baked models each time they bake, before they go live (NeoForge: {@code ModelEvent.ModifyBakingResult}).
     * Fabric API has per-model after-bake modifiers only, no whole-set edit, so there the editor does not run.
     */
    void editBakedModels(java.util.function.Consumer<net.minecraft.client.resources.model.ModelBakery.BakingResult> editor);
    //?} else {
    /*/^* Edits the baked models (block and item, one map on this line) each time they bake, before they go live. *^/
    void editBakedModels(java.util.function.Consumer<java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation,
            net.minecraft.client.resources.model.BakedModel>> editor);*/
    //?}

    /**
     * Runs {@code listener} each time the models have baked and the new atlases and models are live (at startup and on
     * every resource reload; NeoForge: {@code ModelEvent.BakingCompleted}; Fabric: a client reload listener ordered
     * after the model manager). Listeners run by {@code order}, then in registration order.
     */
    void onModelsBaked(buildcraft.platform.event.HookOrder order, Runnable listener);

    /**
     * Wraps the baked model of every state of {@code block}, each time the models bake, in a block model that draws
     * {@code quads} from the {@link buildcraft.platform.render.PipeRenderData} its block entity hands over, and the
     * wrapped model where there is none (NeoForge: a {@code DynamicBlockStateModel} swapped in from
     * {@code ModelEvent.ModifyBakingResult}, an {@code IDynamicBakedModel} on 1.21.1; Fabric: a block model emitting
     * through {@code FabricBlockStateModel#emitQuads}, swapped in by an after-bake modifier).
     */
    void pipeBlockModel(Supplier<? extends Block> block, PipeModelQuads quads);

    //? if >=26.2 {
    /** A picture-in-picture GUI renderer for one render-state class. */
    <S extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void pipRenderer(
            Class<S> stateClass, Supplier<net.minecraft.client.gui.render.pip.PictureInPictureRenderer<S>> factory);
    //?} elif >=26.1 {
    /*/^* A picture-in-picture GUI renderer for one render-state class. *^/
    <S extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void pipRenderer(
            Class<S> stateClass, java.util.function.Function<net.minecraft.client.renderer.MultiBufferSource.BufferSource,
                    net.minecraft.client.gui.render.pip.PictureInPictureRenderer<S>> factory);*/
    //?} elif >=1.21.10 {
    /*/^* A picture-in-picture GUI renderer for one render-state class. *^/
    <S extends net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState> void pipRenderer(
            Class<S> stateClass, java.util.function.Function<net.minecraft.client.renderer.MultiBufferSource.BufferSource,
                    net.minecraft.client.gui.render.pip.PictureInPictureRenderer<S>> factory);*/
    //?}

    /**
     * Work for the client-setup phase, after registries are filled (NeoForge: enqueued on the main thread from
     * {@code FMLClientSetupEvent}; Fabric: run now, as client initialisation already runs there).
     */
    void clientSetup(Runnable work);

    /**
     * Draws {@code item} on the wearer's head when it is worn in an accessory mod's slot, the way vanilla draws a
     * non-armour item in the helmet slot (NeoForge: a Curios renderer, registered at client setup when Curios is
     * loaded). Fabric has no such mod on 26.3, so there it does nothing.
     */
    void headAccessoryRenderer(Supplier<? extends net.minecraft.world.item.Item> item);

    /**
     * Replaces the block's hit and break particles (NeoForge: the block's {@code IClientBlockExtensions}). Fabric API
     * 26.3 has no per-block hook for these, so there the block keeps vanilla's particles.
     */
    void blockParticles(Supplier<? extends Block> block, BlockParticleOverride particles);

    /**
     * How a fluid looks outside its block model: the still and flowing sprites (for buckets, containers and, from
     * 26.1, its world model) and, with the camera inside it, the fog colour and distance.
     *
     * @param fogColor the fog colour as 0xRRGGBB
     * @param fogDistance the distance at which the fog is opaque, in blocks
     */
    void fluidAppearance(Supplier<? extends net.minecraft.world.level.material.Fluid> source,
            Supplier<? extends net.minecraft.world.level.material.Fluid> flowing, Identifier stillTexture,
            Identifier flowingTexture, int fogColor, float fogDistance);

    /**
     * A block's own hit and break particles. Each method returns true when it handled the effect (spawning nothing
     * silences it), false to let vanilla spawn its default particles.
     */
    interface BlockParticleOverride {
        //? if >=26.3 {
        boolean addHitEffects(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face,
                net.minecraft.client.particle.ParticleEngine engine);
        //?} else {
        /*boolean addHitEffects(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                net.minecraft.world.phys.HitResult target, net.minecraft.client.particle.ParticleEngine engine);*/
        //?}

        boolean addDestroyEffects(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                net.minecraft.client.particle.ParticleEngine engine);
    }

    /** A HUD layer: draws one frame of an overlay. */
    @FunctionalInterface
    interface GuiLayer {
        //? if >=26.1 {
        void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
        //?} else {
        /*void render(net.minecraft.client.gui.GuiGraphics graphics, DeltaTracker deltaTracker);*/
        //?}
    }

    /**
     * Builds a particle provider from its sprite set. BuildCraft's own shape of vanilla's sprite-set registration,
     * whose interface moved class at 1.21.10 and is not public outside NeoForge's patched vanilla.
     */
    @FunctionalInterface
    interface SpriteParticleFactory<T extends ParticleOptions> {
        net.minecraft.client.particle.ParticleProvider<T> create(net.minecraft.client.particle.SpriteSet sprites);
    }

    /** The vanilla HUD layers a BuildCraft layer can anchor to; each loader maps them to its own layer ids. */
    enum VanillaGuiLayer {
        CROSSHAIR
    }
}
