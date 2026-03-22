package buildcraft.silicon;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;

public class BCSiliconCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BCSilicon.MODID);

    // Reference the transport plugs tab key by identifier (avoids cross-module compile dependency)
    static final ResourceKey<CreativeModeTab> TRANSPORT_PLUGS_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath("buildcrafttransport", "plugs"));

    public static final ResourceKey<CreativeModeTab> FACADE_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCSilicon.MODID, "facades"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FACADE_TAB =
            CREATIVE_MODE_TABS.register("facades", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.buildcraft.facades"))
                            .icon(() -> {
                                FacadeBlockStateInfo preview = FacadeStateManager.previewState;
                                if (preview != null && preview != FacadeStateManager.defaultState) {
                                    return BCSiliconItems.PLUG_FACADE.get()
                                        .createItemStack(FacadeInstance.createSingle(preview, false));
                                }
                                return BCSiliconItems.PLUG_FACADE.get().getDefaultInstance();
                            })
                            .withTabsBefore(TRANSPORT_PLUGS_TAB_KEY)
                            .build());

    public static void init(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
