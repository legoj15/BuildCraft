/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

//? if >=26.1 {
import net.minecraft.client.color.block.BlockTintSource;
//?}
//? if >=1.21.10 {
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
//?}
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
//? if >=26.1 {
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//?} elif >=1.21.10 {
/^import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
^///?}
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.minecraft.world.level.material.Fluid;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
//? if >=26.1 {
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
//?} elif >=1.21.10 {
/^import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.AtlasSourceRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
^///?} else {
/^import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.AtlasSourceTypeRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
^///?}
//? if >=1.21.10 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
//?}

import buildcraft.platform.client.ClientRegistrationSinks;
import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.event.HookOrder;

/^*
 * {@link ClientRegistrationSinks} on Fabric: every call registers immediately, as Fabric runs client initialisation
 * after the registries are filled. Vanilla registries Fabric API has no wrapper for (menu screens, block entity
 * renderers, item tint sources, render pipelines) are reached through the methods and fields Fabric API's transitive
 * access wideners open.
 *^/
public final class FabricClientRegistrationSinks implements ClientRegistrationSinks {
    private final EnumMap<HookOrder, List<Runnable>> modelsBaked = new EnumMap<>(HookOrder.class);

    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menuScreen(
            Supplier<? extends MenuType<M>> type, MenuScreens.ScreenConstructor<M, U> screen) {
        MenuScreens.register(type.get(), screen);
    }

    //? if >=1.21.10 {
    @Override
    public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(
            Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider) {
        BlockEntityRenderers.register(type.get(), provider);
    }
    //?} else {
    /^@Override
    public <T extends BlockEntity> void blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
            BlockEntityRendererProvider<T> provider) {
        BlockEntityRenderers.register(type.get(), provider);
    }
    ^///?}

    @Override
    public <T extends Entity> void entityRenderer(Supplier<? extends EntityType<? extends T>> type,
            EntityRendererProvider<T> provider) {
        //? if >=1.21.10 {
        EntityRenderers.register(type.get(), provider);
        //?} else {
        /^// EntityRenderers.register is private on 1.21.1; Fabric API's registry feeds the same map.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type.get(), provider);
        ^///?}
    }

    @Override
    public <T extends ParticleOptions> void particleSpriteSet(Supplier<? extends ParticleType<T>> type,
            SpriteParticleFactory<T> factory) {
        //? if >=26.1 {
        ParticleProviderRegistry.PendingParticleProvider<T> pending = factory::create;
        ParticleProviderRegistry.getInstance().register(type.get(), pending);
        //?} else {
        /^ParticleFactoryRegistry.PendingParticleFactory<T> pending = factory::create;
        ParticleFactoryRegistry.getInstance().register(type.get(), pending);
        ^///?}
    }

    //? if >=1.21.10 {
    @Override
    public void guiLayerAboveAll(Identifier id, GuiLayer layer) {
        HudElementRegistry.addLast(id, layer::render);
    }

    @Override
    public void guiLayerAbove(VanillaGuiLayer anchor, Identifier id, GuiLayer layer) {
        Identifier other = switch (anchor) {
            case CROSSHAIR -> VanillaHudElements.CROSSHAIR;
        };
        HudElementRegistry.attachElementAfter(other, id, layer::render);
    }

    @Override
    public void itemTintSource(Identifier id, MapCodec<? extends ItemTintSource> codec) {
        // Fabric API has no item-tint-source registry; its access wideners open vanilla's id mapper instead.
        ItemTintSources.ID_MAPPER.put(id, codec);
    }
    //?} else {
    /^// Fabric API 1.21.1 has one HUD callback, after the whole vanilla HUD (with the depth test off, so on top of it,
    // and drawn with the HUD hidden too, as NeoForge's mod layers are). The layers anchored on a vanilla one draw
    // first, then the above-all ones, the order NeoForge stacks them in; an anchored layer is therefore also above
    // the vanilla layers after its anchor (the hotbar, chat), which it shares no screen space with.
    private final List<GuiLayer> anchoredLayers = new ArrayList<>();
    private final List<GuiLayer> topLayers = new ArrayList<>();
    private boolean hudRegistered;

    private void hudCallback() {
        if (!hudRegistered) {
            hudRegistered = true;
            HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
                for (GuiLayer layer : anchoredLayers) {
                    layer.render(graphics, deltaTracker);
                }
                for (GuiLayer layer : topLayers) {
                    layer.render(graphics, deltaTracker);
                }
            });
        }
    }

    @Override
    public void guiLayerAboveAll(Identifier id, GuiLayer layer) {
        hudCallback();
        topLayers.add(layer);
    }

    @Override
    public void guiLayerAbove(VanillaGuiLayer anchor, Identifier id, GuiLayer layer) {
        hudCallback();
        anchoredLayers.add(layer);
    }

    // BuildCraft's client content is declared during its main init, before FabricRegistryServices builds the items:
    // an item list read then (every pipe item, say) is empty. The colours wait for the client initializer, which Fabric
    // runs after every main one, as NeoForge waits for its colour-handler event.
    private static final List<Runnable> pendingItemColors = new ArrayList<>();
    private static boolean itemColorsFlushed;

    @Override
    public void itemColor(net.minecraft.client.color.item.ItemColor color,
            Supplier<net.minecraft.world.level.ItemLike[]> items) {
        Runnable register = () -> {
            net.minecraft.world.level.ItemLike[] list = items.get();
            if (list.length > 0) {
                ColorProviderRegistry.ITEM.register(color, list);
            }
        };
        if (itemColorsFlushed) {
            register.run();
        } else {
            pendingItemColors.add(register);
        }
    }

    // Registers the item colours declared before the items existed; BCFabricClientEntrypoint calls it.
    static void flushItemColors() {
        itemColorsFlushed = true;
        pendingItemColors.forEach(Runnable::run);
        pendingItemColors.clear();
    }
    ^///?}

    //? if >=26.1 {
    @Override
    public void blockTintSources(Supplier<? extends Block> block, Supplier<List<BlockTintSource>> sources) {
        BlockColorRegistry.register(sources.get(), block.get());
    }
    //?} else {
    /^@Override
    public void blockColor(net.minecraft.client.color.block.BlockColor color, Supplier<? extends Block> block) {
        ColorProviderRegistry.BLOCK.register(color, block.get());
    }
    ^///?}

    //? if >=26.3 {
    @Override
    public void renderPipeline(Supplier<com.mojang.renderpearl.api.pipeline.RenderPipeline> pipeline) {
        RenderPipelines.register(pipeline.get());
    }
    //?} elif >=1.21.10 {
    /^@Override
    public void renderPipeline(Supplier<com.mojang.blaze3d.pipeline.RenderPipeline> pipeline) {
        RenderPipelines.register(pipeline.get());
    }
    ^///?}

    //? if >=26.1 {
    @Override
    public void spriteSource(Identifier id, MapCodec<? extends SpriteSource> codec) {
        SpriteSourceRegistry.register(id, codec);
    }
    //?} elif >=1.21.10 {
    /^@Override
    public void spriteSource(Identifier id, MapCodec<? extends SpriteSource> codec) {
        AtlasSourceRegistry.register(id, codec);
    }
    ^///?} else {
    /^@Override
    public void spriteSourceType(Identifier id, net.minecraft.client.renderer.texture.atlas.SpriteSourceType type) {
        AtlasSourceTypeRegistry.register(id, type);
    }
    ^///?}

    @Override
    public void clientReloadListener(Identifier id, PreparableReloadListener listener) {
        //? if >=26.1 {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id, listener);
        //?} elif >=1.21.10 {
        /^ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(id, listener);
        ^///?} else {
        /^ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new Identified(id, listener, null));
        ^///?}
    }

    //? if >=1.21.10 {
    @Override
    public void onModelsBaking(Runnable listener) {
        // A model loading plugin's initializer runs at the start of every model load, before anything bakes.
        ModelLoadingPlugin.register(context -> listener.run());
    }
    //?} else {
    /^// The three bake-time hooks share one queue on this line, run in the order they were added, where NeoForge's
    // ModifyBakingResult listeners run (FabricBakedModelEdits).
    @Override
    public void onModelsBaking(Runnable listener) {
        FabricBakedModelEdits.add(models -> listener.run());
    }

    @Override
    public void editBakedModels(java.util.function.Consumer<java.util.Map<
            net.minecraft.client.resources.model.ModelResourceLocation,
            net.minecraft.client.resources.model.BakedModel>> editor) {
        FabricBakedModelEdits.add(editor);
    }

    @Override
    public void pipeBlockModel(Supplier<? extends Block> block, PipeModelQuads quads) {
        // All baked models (block and item) share one map keyed by ModelResourceLocation on this line.
        FabricBakedModelEdits.add(models -> {
            for (net.minecraft.world.level.block.state.BlockState state
                    : block.get().getStateDefinition().getPossibleStates()) {
                net.minecraft.client.resources.model.ModelResourceLocation key =
                        net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                net.minecraft.client.resources.model.BakedModel vanilla = models.get(key);
                if (vanilla != null) {
                    models.put(key, new FabricClassicPipeBlockModel(vanilla, quads));
                }
            }
        });
    }

    // A reload listener under its id (Fabric API 1.21.1 asks every listener for one), ordered after dependsOn.
    private record Identified(Identifier id, PreparableReloadListener listener, Identifier dependsOn)
            implements IdentifiableResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return id;
        }

        @Override
        public Collection<Identifier> getFabricDependencies() {
            return dependsOn == null ? List.of() : List.of(dependsOn);
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor prepareExecutor,
                Executor applyExecutor) {
            return listener.reload(barrier, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
        }

        @Override
        public String getName() {
            return listener.getName();
        }
    }
    ^///?}

    //? if >=1.21.10 {
    @Override
    public void editItemModels(ItemModelEditor editor) {
        // A per-item after-bake modifier, registered afresh on every model load; itemId() is the item model's key.
        ModelLoadingPlugin.register(context -> context.modifyItemModelAfterBake().register(
                (model, itemContext) -> model == null ? null : editor.edit(itemContext.itemId(), model)));
    }

    @Override
    public void pipeBlockModel(Supplier<? extends Block> block, PipeModelQuads quads) {
        // A per-model after-bake modifier, registered afresh on every model load, wraps each state of the block.
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
                (model, modelContext) -> modelContext.state().is(block.get())
                        ? new FabricPipeBlockModel(model, quads) : model));
    }
    //?}

    @Override
    public synchronized void onModelsBaked(HookOrder order, Runnable listener) {
        if (modelsBaked.isEmpty()) {
            // One reload listener, ordered after the model manager's, whose apply step makes the new models live.
            Identifier id = Identifier.fromNamespaceAndPath("buildcraftunofficial", "models_baked");
            //? if >=26.1 {
            ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
            loader.registerReloadListener(id, (ResourceManagerReloadListener) manager -> runModelsBaked());
            loader.addListenerOrdering(ResourceReloaderKeys.Client.MODELS, id);
            //?} elif >=1.21.10 {
            /^ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
            loader.registerReloader(id, (ResourceManagerReloadListener) manager -> runModelsBaked());
            loader.addReloaderOrdering(ResourceReloaderKeys.Client.MODELS, id);
            ^///?} else {
            /^ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new Identified(id,
                    (ResourceManagerReloadListener) manager -> runModelsBaked(), ResourceReloadListenerKeys.MODELS));
            ^///?}
        }
        modelsBaked.computeIfAbsent(order, key -> new ArrayList<>()).add(listener);
    }

    private synchronized void runModelsBaked() {
        for (HookOrder order : HookOrder.values()) {
            for (Runnable listener : modelsBaked.getOrDefault(order, List.of())) {
                listener.run();
            }
        }
    }

    //? if >=26.2 {
    @Override
    public <S extends PictureInPictureRenderState> void pipRenderer(Class<S> stateClass,
            Supplier<PictureInPictureRenderer<S>> factory) {
        // Fabric keys a PiP renderer by the state class its renderer reports, so the class is not passed on.
        PictureInPictureRendererRegistry.register(context -> factory.get());
    }
    //?} elif >=26.1 {
    /^// Before 26.2 a PiP renderer draws into the buffer source the GUI renderer is built with, the game's own.
    @Override
    public <S extends PictureInPictureRenderState> void pipRenderer(Class<S> stateClass,
            java.util.function.Function<net.minecraft.client.renderer.MultiBufferSource.BufferSource,
                    PictureInPictureRenderer<S>> factory) {
        PictureInPictureRendererRegistry.register(
                context -> factory.apply(context.minecraft().renderBuffers().bufferSource()));
    }
    ^///?} elif >=1.21.10 {
    /^// Fabric API's special GUI element registry hands the renderer the GUI renderer's buffer source.
    @Override
    public <S extends PictureInPictureRenderState> void pipRenderer(Class<S> stateClass,
            java.util.function.Function<net.minecraft.client.renderer.MultiBufferSource.BufferSource,
                    PictureInPictureRenderer<S>> factory) {
        SpecialGuiElementRegistry.register(context -> factory.apply(context.vertexConsumers()));
    }
    ^///?}

    @Override
    public void clientSetup(Runnable work) {
        work.run();
    }

    @Override
    public void headAccessoryRenderer(Supplier<? extends net.minecraft.world.item.Item> item) {
        // Gap: no accessory-slot mod has a Fabric 26.3 build (Curios is NeoForge-only), so there is nothing to draw.
    }

    @Override
    public void blockParticles(Supplier<? extends Block> block, BlockParticleOverride particles) {
        // Fabric API 26.3 has no per-block hit/break particle hook (NeoForge's IClientBlockExtensions); on 26.x those
        // particles are spawned by ClientLevel, where ClientLevelMixin asks FabricBlockParticles first.
        FabricBlockParticles.register(block.get(), particles);
    }

    @Override
    public void fluidAppearance(Supplier<? extends Fluid> source, Supplier<? extends Fluid> flowing,
            Identifier stillTexture, Identifier flowingTexture, int fogColor, float fogDistance) {
        //? if >=26.1 {
        FluidRenderingRegistry.register(source.get(), flowing.get(),
                new FluidModel.Unbaked(new Material(stillTexture), new Material(flowingTexture), null, null));
        //?} else {
        /^FluidRenderHandlerRegistry.INSTANCE.register(source.get(), flowing.get(),
                new SimpleFluidRenderHandler(stillTexture, flowingTexture));
        ^///?}
        // Fabric API 26.3 has no fluid fog hook: FogRendererMixin applies this through FabricFluidFog.
        FabricFluidFog.register(source.get(), flowing.get(), fogColor, fogDistance);
    }
}
*///?}
