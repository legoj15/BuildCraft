/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import buildcraft.platform.client.ClientRegistrationSinks;
import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.event.HookOrder;
import buildcraft.platform.neoforge.NeoForgeGameHooks;
import buildcraft.platform.neoforge.NeoForgeModBus;

/**
 * {@link ClientRegistrationSinks} on NeoForge: each call is queued and replayed inside its mod-bus event.
 *
 * <p><b>Replay.</b> The first call for an event type subscribes one listener for it on BuildCraft's mod bus
 * ({@link NeoForgeModBus}; the calls are made from its client init), and that listener replays the event type's
 * queue in call order every time the event fires, exactly as the hand-written listeners it replaces did. Suppliers
 * are read only then, once the registries are filled.
 */
public final class NeoForgeClientRegistrationSinks implements ClientRegistrationSinks {

    private final Map<Class<?>, List<Consumer<?>>> pending = new HashMap<>();

    @Override
    public <M extends AbstractContainerMenu, U extends net.minecraft.client.gui.screens.Screen
            & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void menuScreen(
            Supplier<? extends MenuType<M>> type, MenuScreens.ScreenConstructor<M, U> screen) {
        queue(RegisterMenuScreensEvent.class, event -> event.register(type.get(), screen));
    }

    //? if >=1.21.10 {
    @Override
    public <T extends BlockEntity, S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> void
            blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
                    BlockEntityRendererProvider<T, S> provider) {
        queue(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(type.get(), provider));
    }
    //?} else {
    /*@Override
    public <T extends BlockEntity> void blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
            BlockEntityRendererProvider<T> provider) {
        queue(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(type.get(), provider));
    }*/
    //?}

    @Override
    public <T extends Entity> void entityRenderer(Supplier<? extends EntityType<? extends T>> type,
            EntityRendererProvider<T> provider) {
        queue(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(type.get(), provider));
    }

    @Override
    public <T extends ParticleOptions> void particleSpriteSet(Supplier<? extends ParticleType<T>> type,
            SpriteParticleFactory<T> factory) {
        // A method reference fits the event's sprite-set registration type on every line (it moved class at 1.21.10).
        queue(RegisterParticleProvidersEvent.class, event -> event.registerSpriteSet(type.get(), factory::create));
    }

    @Override
    public void guiLayerAboveAll(Identifier id, GuiLayer layer) {
        queue(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(id, layer::render));
    }

    @Override
    public void guiLayerAbove(VanillaGuiLayer anchor, Identifier id, GuiLayer layer) {
        Identifier other = switch (anchor) {
            case CROSSHAIR -> VanillaGuiLayers.CROSSHAIR;
        };
        queue(RegisterGuiLayersEvent.class, event -> event.registerAbove(other, id, layer::render));
    }

    //? if >=1.21.10 {
    @Override
    public void itemTintSource(Identifier id,
            com.mojang.serialization.MapCodec<? extends net.minecraft.client.color.item.ItemTintSource> codec) {
        queue(RegisterColorHandlersEvent.ItemTintSources.class, event -> event.register(id, codec));
    }
    //?} else {
    /*@Override
    public void itemColor(net.minecraft.client.color.item.ItemColor color,
            Supplier<net.minecraft.world.level.ItemLike[]> items) {
        queue(RegisterColorHandlersEvent.Item.class, event -> {
            net.minecraft.world.level.ItemLike[] resolved = items.get();
            if (resolved.length > 0) {
                event.register(color, resolved);
            }
        });
    }*/
    //?}

    //? if >=26.1 {
    @Override
    public void blockTintSources(Supplier<? extends Block> block,
            Supplier<List<net.minecraft.client.color.block.BlockTintSource>> sources) {
        queue(RegisterColorHandlersEvent.BlockTintSources.class, event -> event.register(sources.get(), block.get()));
    }
    //?} else {
    /*@Override
    public void blockColor(net.minecraft.client.color.block.BlockColor color, Supplier<? extends Block> block) {
        queue(RegisterColorHandlersEvent.Block.class, event -> event.register(color, block.get()));
    }*/
    //?}

    //? if >=26.3 {
    @Override
    public void renderPipeline(Supplier<com.mojang.renderpearl.api.pipeline.RenderPipeline> pipeline) {
        queue(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent.class,
                event -> event.registerPipeline(pipeline.get()));
    }
    //?} elif >=1.21.10 {
    /*@Override
    public void renderPipeline(Supplier<com.mojang.blaze3d.pipeline.RenderPipeline> pipeline) {
        queue(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent.class,
                event -> event.registerPipeline(pipeline.get()));
    }*/
    //?}

    //? if >=1.21.10 {
    @Override
    public void spriteSource(Identifier id,
            com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.texture.atlas.SpriteSource> codec) {
        queue(net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent.class, event -> event.register(id, codec));
    }

    @Override
    public void clientReloadListener(Identifier id, PreparableReloadListener listener) {
        queue(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.class,
                event -> event.addListener(id, listener));
    }
    //?} else {
    /*@Override
    public void spriteSourceType(Identifier id, net.minecraft.client.renderer.texture.atlas.SpriteSourceType type) {
        queue(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent.class, event -> event.register(id, type));
    }

    @Override
    public void clientReloadListener(Identifier id, PreparableReloadListener listener) {
        // The line's reload-listener event takes no id.
        queue(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class,
                event -> event.registerReloadListener(listener));
    }*/
    //?}

    // The model hooks fire on every reload, not once, and are added straight to the bus (no replay queue), so each
    // listener keeps its own place among the others: what the hand-written listeners they replace did.
    @Override
    public void onModelsBaking(Runnable listener) {
        NeoForgeModBus.get().addListener(ModelEvent.ModifyBakingResult.class, event -> listener.run());
    }

    //? if >=1.21.10 {
    @Override
    public void editBakedModels(
            Consumer<net.minecraft.client.resources.model.ModelBakery.BakingResult> editor) {
        NeoForgeModBus.get().addListener(ModelEvent.ModifyBakingResult.class,
                event -> editor.accept(event.getBakingResult()));
    }
    //?} else {
    /*@Override
    public void editBakedModels(Consumer<Map<net.minecraft.client.resources.model.ModelResourceLocation,
            net.minecraft.client.resources.model.BakedModel>> editor) {
        NeoForgeModBus.get().addListener(ModelEvent.ModifyBakingResult.class, event -> editor.accept(event.getModels()));
    }*/
    //?}

    @Override
    public void onModelsBaked(HookOrder order, Runnable listener) {
        NeoForgeModBus.get().addListener(NeoForgeGameHooks.priority(order), false, ModelEvent.BakingCompleted.class,
                event -> listener.run());
    }

    @Override
    public void pipeBlockModel(Supplier<? extends Block> block, PipeModelQuads quads) {
        // Added straight to the bus at the call, like editBakedModels, so it keeps its place among the bake editors.
        NeoForgeModBus.get().addListener(ModelEvent.ModifyBakingResult.class, event -> {
            //? if >=1.21.10 {
            var blockModels = event.getBakingResult().blockStateModels();
            for (net.minecraft.world.level.block.state.BlockState state : block.get().getStateDefinition().getPossibleStates()) {
                var vanillaModel = blockModels.get(state);
                if (vanillaModel != null) {
                    blockModels.put(state, new NeoForgePipeBlockStateModel(vanillaModel, quads));
                }
            }
            //?} else {
            /*// All baked models (block and item) share one map keyed by ModelResourceLocation on this line.
            Map<net.minecraft.client.resources.model.ModelResourceLocation, net.minecraft.client.resources.model.BakedModel>
                    models = event.getModels();
            for (net.minecraft.world.level.block.state.BlockState state : block.get().getStateDefinition().getPossibleStates()) {
                net.minecraft.client.resources.model.ModelResourceLocation mrl =
                        net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                net.minecraft.client.resources.model.BakedModel vanillaModel = models.get(mrl);
                if (vanillaModel != null) {
                    models.put(mrl, new NeoForgePipeBlockStateModel(vanillaModel, quads));
                }
            }*/
            //?}
        });
    }

    //? if >=26.2 {
    @Override
    public <S extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void pipRenderer(
            Class<S> stateClass, Supplier<net.minecraft.client.gui.render.pip.PictureInPictureRenderer<S>> factory) {
        queue(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent.class,
                event -> event.register(stateClass, factory));
    }
    //?} elif >=26.1 {
    /*@Override
    public <S extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void pipRenderer(
            Class<S> stateClass, java.util.function.Function<net.minecraft.client.renderer.MultiBufferSource.BufferSource,
                    net.minecraft.client.gui.render.pip.PictureInPictureRenderer<S>> factory) {
        queue(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent.class,
                event -> event.register(stateClass, factory));
    }*/
    //?} elif >=1.21.10 {
    /*@Override
    public <S extends net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState> void pipRenderer(
            Class<S> stateClass, java.util.function.Function<net.minecraft.client.renderer.MultiBufferSource.BufferSource,
                    net.minecraft.client.gui.render.pip.PictureInPictureRenderer<S>> factory) {
        queue(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent.class,
                event -> event.register(stateClass, factory));
    }*/
    //?}

    @Override
    public void clientSetup(Runnable work) {
        queue(FMLClientSetupEvent.class, event -> event.enqueueWork(work));
    }

    @Override
    public void headAccessoryRenderer(Supplier<? extends net.minecraft.world.item.Item> item) {
        // Curios draws nothing for a curio without a renderer; Accessories has a default one.
        clientSetup(() -> {
            if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
                NeoForgeCuriosHeadRenderer.register(item.get());
            }
        });
    }

    @Override
    public void blockParticles(Supplier<? extends Block> block, BlockParticleOverride particles) {
        queue(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent.class,
                event -> event.registerBlock(new NeoForgeBlockParticleExtensions(particles), block.get()));
    }

    @Override
    public void fluidAppearance(Supplier<? extends net.minecraft.world.level.material.Fluid> source,
            Supplier<? extends net.minecraft.world.level.material.Fluid> flowing, Identifier stillTexture,
            Identifier flowingTexture, int fogColor, float fogDistance) {
        queue(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent.class,
                event -> event.registerFluidType(
                        new NeoForgeFluidTypeExtensions(stillTexture, flowingTexture, fogColor, fogDistance),
                        source.get().getFluidType()));
        // Below 26.1 world fluids take their textures from the extension above; 26.1 dropped those hooks in favour
        // of a FluidModel registered here.
        //? if >=26.1 {
        queue(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent.class, event -> event.register(
                new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                        new net.minecraft.client.resources.model.sprite.Material(stillTexture),
                        new net.minecraft.client.resources.model.sprite.Material(flowingTexture), null, null),
                source.get(), flowing.get()));
        //?}
    }

    private synchronized <E extends Event & IModBusEvent> void queue(Class<E> type, Consumer<E> registration) {
        List<Consumer<?>> registrations = pending.get(type);
        if (registrations == null) {
            registrations = new ArrayList<>();
            pending.put(type, registrations);
            List<Consumer<?>> replayed = registrations;
            NeoForgeModBus.get()
                    .addListener(type, event -> replay(replayed, event));
        }
        registrations.add(registration);
    }

    @SuppressWarnings("unchecked") // each list only ever holds consumers of the event type it is keyed by
    private synchronized <E> void replay(List<Consumer<?>> registrations, E event) {
        for (Consumer<?> registration : registrations) {
            ((Consumer<E>) registration).accept(event);
        }
    }
}
//?}
