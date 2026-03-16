package buildcraft.transport;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BCTransportCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BCTransport.MODID);

    public static final ResourceKey<CreativeModeTab> PIPES_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCTransport.MODID, "pipes"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PIPES_TAB =
            CREATIVE_MODE_TABS.register("pipes", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.buildcraft.pipes"))
                            .icon(() -> BCTransportItems.PIPE_DIAMOND_ITEM.get().getDefaultInstance())
                            .build());

    public static final ResourceKey<CreativeModeTab> PLUGS_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCTransport.MODID, "plugs"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PLUGS_TAB =
            CREATIVE_MODE_TABS.register("plugs", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.buildcraft.plugs"))
                            .icon(() -> BCTransportItems.PLUG_BLOCKER.get().getDefaultInstance())
                            .build());

    public static void init(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
