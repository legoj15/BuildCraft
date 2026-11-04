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
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
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

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.material.Fluid;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;

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

    @Override
    public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(
            Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider) {
        BlockEntityRenderers.register(type.get(), provider);
    }

    @Override
    public <T extends Entity> void entityRenderer(Supplier<? extends EntityType<? extends T>> type,
            EntityRendererProvider<T> provider) {
        EntityRenderers.register(type.get(), provider);
    }

    @Override
    public <T extends ParticleOptions> void particleSpriteSet(Supplier<? extends ParticleType<T>> type,
            SpriteParticleFactory<T> factory) {
        ParticleProviderRegistry.PendingParticleProvider<T> pending = factory::create;
        ParticleProviderRegistry.getInstance().register(type.get(), pending);
    }

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

    @Override
    public void blockTintSources(Supplier<? extends Block> block, Supplier<List<BlockTintSource>> sources) {
        BlockColorRegistry.register(sources.get(), block.get());
    }

    @Override
    public void renderPipeline(Supplier<RenderPipeline> pipeline) {
        RenderPipelines.register(pipeline.get());
    }

    @Override
    public void spriteSource(Identifier id, MapCodec<? extends SpriteSource> codec) {
        SpriteSourceRegistry.register(id, codec);
    }

    @Override
    public void clientReloadListener(Identifier id, PreparableReloadListener listener) {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id, listener);
    }

    @Override
    public void onModelsBaking(Runnable listener) {
        // A model loading plugin's initializer runs at the start of every model load, before anything bakes.
        ModelLoadingPlugin.register(context -> listener.run());
    }

    @Override
    public void editBakedModels(Consumer<ModelBakery.BakingResult> editor) {
        // Gap: Fabric API 26.3 offers per-model after-bake modifiers only (ModelModifier.AfterBakeBlock/AfterBakeItem),
        // never the whole baked set, so a whole-set editor cannot run; the editor's model swaps are absent on Fabric.
    }

    @Override
    public void pipeBlockModel(Supplier<? extends Block> block, PipeModelQuads quads) {
        // A per-model after-bake modifier, registered afresh on every model load, wraps each state of the block.
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
                (model, modelContext) -> modelContext.state().is(block.get())
                        ? new FabricPipeBlockModel(model, quads) : model));
    }

    @Override
    public synchronized void onModelsBaked(HookOrder order, Runnable listener) {
        if (modelsBaked.isEmpty()) {
            // One reload listener, ordered after the model manager's, whose apply step makes the new models live.
            Identifier id = Identifier.fromNamespaceAndPath("buildcraftunofficial", "models_baked");
            ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
            loader.registerReloadListener(id, (ResourceManagerReloadListener) manager -> runModelsBaked());
            loader.addListenerOrdering(ResourceReloaderKeys.Client.MODELS, id);
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

    @Override
    public <S extends PictureInPictureRenderState> void pipRenderer(Class<S> stateClass,
            Supplier<PictureInPictureRenderer<S>> factory) {
        // Fabric keys a PiP renderer by the state class its renderer reports, so the class is not passed on.
        PictureInPictureRendererRegistry.register(context -> factory.get());
    }

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
        // Gap: Fabric API 26.3 has no per-block hit/break particle hook (NeoForge's IClientBlockExtensions); it would
        // take a mixin on ParticleEngine. The block keeps vanilla's particles.
    }

    @Override
    public void fluidAppearance(Supplier<? extends Fluid> source, Supplier<? extends Fluid> flowing,
            Identifier stillTexture, Identifier flowingTexture, int fogColor, float fogDistance) {
        FluidRenderingRegistry.register(source.get(), flowing.get(),
                new FluidModel.Unbaked(new Material(stillTexture), new Material(flowingTexture), null, null));
        // Gap: Fabric API 26.3 has no fluid fog hook, so the camera-inside fog stays vanilla's.
    }
}
*///?}
