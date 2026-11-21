/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

//? if >=1.21.10 {
import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
//?}
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
//? if >=1.21.10 {
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
//? if >=1.21.10 {
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.model.sprite.Material;
//?} else {
/^import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.ItemLike;
^///?}
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
//? if >=1.21.10 {
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
//?}
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
//? if >=1.21.10 {
import net.minecraftforge.eventbus.internal.Event;
//?} else {
/^import net.minecraftforge.eventbus.api.Event;
^///?}
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.platform.client.ClientRegistrationSinks;
import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.event.HookOrder;
import buildcraft.platform.forge.ForgeBus;
import buildcraft.platform.forge.ForgeFlowingFluids;
import buildcraft.platform.forge.mixin.client.SpriteSourcesAccessor;

/^*
 * {@link ClientRegistrationSinks} on MinecraftForge (not NeoForge). Most calls are queued and replayed inside the Forge
 * event that takes the registration, as NeoForge's twin does; what differs is where those events live and the few
 * registrations Forge has no event for.
 *
 * <p><b>Replay.</b> The first call for an event subscribes one listener on that event ({@link ForgeBus#listenClient}: the
 * event's own bus on 26.3's EventBus 7, the mod bus on 1.21.1's EventBus 6; the client-setup event is a mod-bus event on
 * both, {@link ForgeBus#listenMod}), and that listener replays the queue in call order when the event fires. Suppliers
 * are read only then, once the registries are filled (Forge constructs BuildCraft, where these calls are made, before
 * its registry events). A call made after its event has fired would never be replayed, so it throws instead: every call
 * belongs in the client init. Unlike NeoForge, several of these events are posted from vanilla constructors (block and
 * item colours, particles, the HUD layers, the PiP renderers), still after mod construction.
 *
 * <p><b>No Forge event.</b> Sprite source types (and on 26.3 item tint source types and render pipelines) have no
 * registration event on Forge, so they go straight into vanilla's own id maps at the call, as on Fabric (the tint sources
 * and pipelines are opened by Forge's access transformer, the sprite sources by {@link SpriteSourcesAccessor}). Forge's
 * reload-listener event takes no id; Forge's block-particle hooks are a method on the block class, so a registered
 * override is asked by a client mixin instead ({@link ForgeBlockParticles}); and Curios has no Forge build on either line
 * (ForgeAccessorySlots), so {@link #headAccessoryRenderer} draws nothing (as on Fabric). Forge has no menu-screen event:
 * vanilla's registration (opened by Forge's access transformer) runs at client setup.
 *
 * <p><b>Fluids.</b> A fluid's look starts with the fluid type's client extensions (textures, tint, the camera-inside fog),
 * attached to the type's own initializer ({@link ForgeFlowingFluids.Type#attachClientExtensions}) when the client
 * reload-listener event fires: after the registries are filled and before the first resource reload, which on 1.21.1
 * reads the textures for the world fluid renderer and the fluid containers' models (client setup would race that reload,
 * as Forge runs it during the reload). On 26.3 the world model is a {@code FluidModel} ({@code ModelEvent.BakeFluidModels},
 * which Forge posts on every model bake, where NeoForge has a registration event).
 *
 * <p><b>Models on 1.21.1.</b> One map holds every baked model (block and item, keyed by model location), as on NeoForge
 * 1.21.1; the fluid containers' classic models load through {@link ForgeFluidContainerGeometry}, registered here.
 *^/
public final class ForgeClientRegistrationSinks implements ClientRegistrationSinks {

    private final Map<Class<?>, List<Consumer<?>>> pending = new HashMap<>();
    /^* The event types whose listener has started replaying. ^/
    private final Set<Class<?>> fired = new HashSet<>();

    /^*
     * Built once, by the client init's first registration ({@code ClientPlatform}), which is also where the Forge client's
     * own model types go: Forge needs no client entrypoint of its own (Fabric's registers the same type there).
     *^/
    public ForgeClientRegistrationSinks() {
        //? if >=1.21.10 {
        ForgeFluidContainerModel.register();
        //?} else {
        /^ForgeBus.listenClient(ModelEvent.RegisterGeometryLoaders.class,
                event -> event.register(ForgeFluidContainerGeometry.NAME, ForgeFluidContainerGeometry.LOADER));
        ^///?}
    }

    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menuScreen(
            Supplier<? extends MenuType<M>> type, MenuScreens.ScreenConstructor<M, U> screen) {
        // Forge has no menu-screen event; vanilla's registration at client setup, where Forge's own documentation puts it.
        clientSetup(() -> MenuScreens.register(type.get(), screen));
    }

    //? if >=1.21.10 {
    @Override
    public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(
            Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider) {
        queue(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(type.get(), provider));
    }
    //?} else {
    /^@Override
    public <T extends BlockEntity> void blockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
            BlockEntityRendererProvider<T> provider) {
        queue(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(type.get(), provider));
    }
    ^///?}

    @Override
    public <T extends Entity> void entityRenderer(Supplier<? extends EntityType<? extends T>> type,
            EntityRendererProvider<T> provider) {
        queue(EntityRenderersEvent.RegisterRenderers.class, event -> event.registerEntityRenderer(type.get(), provider));
    }

    @Override
    public <T extends ParticleOptions> void particleSpriteSet(Supplier<? extends ParticleType<T>> type,
            SpriteParticleFactory<T> factory) {
        queue(RegisterParticleProvidersEvent.class, event -> event.registerSpriteSet(type.get(), factory::create));
    }

    @Override
    public void guiLayerAboveAll(Identifier id, GuiLayer layer) {
        // The root draw stack's last layer is drawn last, above the whole vanilla HUD.
        queue(AddGuiOverlayLayersEvent.class, event -> event.getLayeredDraw().add(id, layer::render));
    }

    @Override
    public void guiLayerAbove(VanillaGuiLayer anchor, Identifier id, GuiLayer layer) {
        // Forge nests the HUD in named stacks; the crosshair is in the pre-sleep one.
        Identifier stack = switch (anchor) {
            case CROSSHAIR -> ForgeLayeredDraw.PRE_SLEEP_STACK;
        };
        Identifier other = switch (anchor) {
            case CROSSHAIR -> ForgeLayeredDraw.CROSSHAIR;
        };
        queue(AddGuiOverlayLayersEvent.class, event -> event.getLayeredDraw().addAbove(stack, id, other, layer::render));
    }

    //? if >=1.21.10 {
    @Override
    public void itemTintSource(Identifier id, MapCodec<? extends ItemTintSource> codec) {
        ItemTintSources.ID_MAPPER.put(id, codec);
    }

    @Override
    public void blockTintSources(Supplier<? extends Block> block, Supplier<List<BlockTintSource>> sources) {
        queue(RegisterColorHandlersEvent.Block.class, event -> event.register(sources.get(), block.get()));
    }

    @Override
    public void renderPipeline(Supplier<com.mojang.renderpearl.api.pipeline.RenderPipeline> pipeline) {
        // Vanilla's own registration (RenderPipelines.register) is one put into this map, which the shader reload
        // compiles every entry of; the first reload is after mod construction.
        com.mojang.renderpearl.api.pipeline.RenderPipeline resolved = pipeline.get();
        RenderPipelines.PIPELINES_BY_LOCATION.put(resolved.getLocation(), resolved);
    }

    @Override
    public void spriteSource(Identifier id, MapCodec<? extends SpriteSource> codec) {
        SpriteSourcesAccessor.buildcraft$idMapper().put(id, codec);
    }
    //?} else {
    /^@Override
    public void itemColor(ItemColor color, Supplier<ItemLike[]> items) {
        queue(RegisterColorHandlersEvent.Item.class, event -> {
            ItemLike[] resolved = items.get();
            if (resolved.length > 0) {
                event.register(color, resolved);
            }
        });
    }

    @Override
    public void blockColor(BlockColor color, Supplier<? extends Block> block) {
        queue(RegisterColorHandlersEvent.Block.class, event -> event.register(color, block.get()));
    }

    @Override
    public void spriteSourceType(Identifier id, SpriteSourceType type) {
        // Vanilla's own registration is one put into this map, which the atlas codec reads on every atlas load.
        SpriteSourcesAccessor.buildcraft$types().put(id, type);
    }
    ^///?}

    @Override
    public void clientReloadListener(Identifier id, PreparableReloadListener listener) {
        // Forge's event takes no id.
        queue(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(listener));
    }

    // The model hooks fire on every reload, not once, and are added straight to the bus (no replay queue), so each
    // listener keeps its own place among the others, as on NeoForge.
    @Override
    public void onModelsBaking(Runnable listener) {
        ForgeBus.listenClient(ModelEvent.ModifyBakingResult.class, event -> listener.run());
    }

    //? if >=1.21.10 {
    @Override
    public void editItemModels(ItemModelEditor editor) {
        ForgeBus.listenClient(ModelEvent.ModifyBakingResult.class, event -> event.getResults().itemStackModels()
                .replaceAll((id, baked) -> baked == null ? null : editor.edit(id, baked)));
    }
    //?} else {
    /^@Override
    public void editBakedModels(Consumer<Map<ModelResourceLocation, BakedModel>> editor) {
        ForgeBus.listenClient(ModelEvent.ModifyBakingResult.class, event -> editor.accept(event.getModels()));
    }
    ^///?}

    @Override
    public void onModelsBaked(HookOrder order, Runnable listener) {
        ForgeBus.listenClient(ModelEvent.BakingCompleted.class, order, event -> listener.run());
    }

    @Override
    public void pipeBlockModel(Supplier<? extends Block> block, PipeModelQuads quads) {
        ForgeBus.listenClient(ModelEvent.ModifyBakingResult.class, event -> {
            //? if >=1.21.10 {
            var blockModels = event.getResults().blockStateModels();
            for (BlockState state : block.get().getStateDefinition().getPossibleStates()) {
                var vanillaModel = blockModels.get(state);
                if (vanillaModel != null) {
                    blockModels.put(state, new ForgePipeBlockStateModel(vanillaModel, quads));
                }
            }
            //?} else {
            /^// All baked models (block and item) share one map keyed by model location on this line.
            Map<ModelResourceLocation, BakedModel> models = event.getModels();
            for (BlockState state : block.get().getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel vanillaModel = models.get(location);
                if (vanillaModel != null) {
                    models.put(location, new ForgePipeBlockStateModel(vanillaModel, quads));
                }
            }
            ^///?}
        });
    }

    //? if >=1.21.10 {
    @Override
    public <S extends PictureInPictureRenderState> void pipRenderer(Class<S> stateClass,
            Supplier<PictureInPictureRenderer<S>> factory) {
        // Forge keys the renderer by its own render-state class, which is stateClass.
        queue(RegisterPictureInPictureRendererEvent.class, event -> event.register(factory.get()));
    }
    //?}

    @Override
    public void clientSetup(Runnable work) {
        queue(FMLClientSetupEvent.class, true, event -> event.enqueueWork(work));
    }

    @Override
    public void headAccessoryRenderer(Supplier<? extends Item> item) {
        // Curios has no Forge build on either line (ForgeAccessorySlots), so there is no accessory slot to draw from.
    }

    @Override
    public void blockParticles(Supplier<? extends Block> block, BlockParticleOverride particles) {
        clientSetup(() -> ForgeBlockParticles.register(block.get(), particles));
    }

    @Override
    public void fluidAppearance(Supplier<? extends Fluid> source, Supplier<? extends Fluid> flowing,
            Identifier stillTexture, Identifier flowingTexture, int fogColor, float fogDistance) {
        // Not a reload listener: the event is only the first moment after the registries and before the first reload.
        queue(RegisterClientReloadListenersEvent.class, event -> {
            if (!(source.get().getFluidType() instanceof ForgeFlowingFluids.Type type)) {
                throw new IllegalStateException(source.get() + " is not a BuildCraft fluid; its look cannot be set");
            }
            type.attachClientExtensions(new ForgeFluidTypeExtensions(stillTexture, flowingTexture, fogColor,
                    fogDistance));
        });
        //? if >=1.21.10 {
        // Posted on every model bake (a worker thread), so a listener, not a queue. Forge puts vanilla's water and lava
        // models over the ones registered here, which never name those fluids.
        FluidModel.Unbaked unbaked = new FluidModel.Unbaked(new Material(stillTexture), new Material(flowingTexture),
                null, null);
        ModelEvent.BakeFluidModels.BUS.addListener(event -> {
            Fluid still = source.get();
            FluidModel model = unbaked.bake(event.materials(), () -> "BuildCraft fluid " + still);
            event.register(still, model);
            event.register(flowing.get(), model);
        });
        //?}
    }

    private <E extends Event> void queue(Class<E> type, Consumer<E> registration) {
        queue(type, false, registration);
    }

    private synchronized <E extends Event> void queue(Class<E> type, boolean modBus, Consumer<E> registration) {
        if (fired.contains(type)) {
            throw new IllegalStateException(type.getSimpleName() + " registration queued after the event fired; "
                    + "register during client init");
        }
        List<Consumer<?>> registrations = pending.get(type);
        if (registrations == null) {
            registrations = new ArrayList<>();
            pending.put(type, registrations);
            List<Consumer<?>> replayed = registrations;
            Consumer<E> listener = event -> replay(type, replayed, event);
            if (modBus) {
                ForgeBus.listenMod(type, listener);
            } else {
                ForgeBus.listenClient(type, listener);
            }
        }
        registrations.add(registration);
    }

    @SuppressWarnings("unchecked") // each list only ever holds consumers of the event type it is keyed by
    private synchronized <E> void replay(Class<?> type, List<Consumer<?>> registrations, E event) {
        fired.add(type);
        for (Consumer<?> registration : registrations) {
            ((Consumer<E>) registration).accept(event);
        }
    }
}
*///?}
