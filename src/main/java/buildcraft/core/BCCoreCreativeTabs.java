package buildcraft.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.lib.BCLibItems;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.BCFactoryItems;
import buildcraft.energy.BCEnergyItems;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.transport.BCTransportItems;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.BCSiliconItems;

public class BCCoreCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BCCore.MODID);

    public static final ResourceKey<CreativeModeTab> MAIN_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCCore.MODID, "main"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.buildcraft.main"))
                            .icon(() -> BCCoreItems.WRENCH.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                // Config Guide
                                output.accept(BCLibItems.GUIDE_CONFIG.get());
                                // Guide Book
                                output.accept(BCLibItems.GUIDE.get());
                                // Book Note
                                output.accept(BCLibItems.GUIDE_NOTE.get());
                                // Debugger
                                output.accept(BCLibItems.DEBUGGER.get());
                                // Dev-only Power Tester (only when launched with -Dbuildcraft.dev=true)
                                if (BCCoreItems.POWER_TESTER != null) {
                                    output.accept(BCCoreItems.POWER_TESTER.get());
                                }
                                // Dev-only Goggles (only when launched with -Dbuildcraft.dev=true)
                                if (BCCoreItems.GOGGLES != null) {
                                    output.accept(BCCoreItems.GOGGLES.get());
                                }
                                // Water Spring
                                output.accept(BCCoreItems.SPRING_WATER.get());
                                // Oil Spring
                                output.accept(BCCoreItems.SPRING_OIL.get());
                                // Decoratives
                                output.accept(BCCoreItems.DECORATED_DESTROY.get());
                                output.accept(BCCoreItems.DECORATED_BLUEPRINT.get());
                                output.accept(BCCoreItems.DECORATED_TEMPLATE.get());
                                output.accept(BCCoreItems.DECORATED_PAPER.get());
                                output.accept(BCCoreItems.DECORATED_LEATHER.get());
                                output.accept(BCCoreItems.DECORATED_LASER.get());
                                // Land Mark
                                output.accept(BCCoreItems.MARKER_VOLUME.get());
                                // Path Mark
                                output.accept(BCCoreItems.MARKER_PATH.get());
                                // Engines
                                output.accept(BCCoreItems.ENGINE_REDSTONE.get());
                                output.accept(BCEnergyItems.ENGINE_STONE.get());
                                output.accept(BCEnergyItems.ENGINE_IRON.get());
                                output.accept(BCEnergyItems.ENGINE_FE.get());
                                output.accept(BCEnergyItems.DYNAMO_MJ.get());
                                output.accept(BCCoreItems.ENGINE_CREATIVE.get());
                                // Wrench
                                output.accept(BCCoreItems.WRENCH.get());
                                // Gears
                                output.accept(BCCoreItems.GEAR_WOOD.get());
                                output.accept(BCCoreItems.GEAR_STONE.get());
                                output.accept(BCCoreItems.GEAR_IRON.get());
                                output.accept(BCCoreItems.GEAR_GOLD.get());
                                output.accept(BCCoreItems.GEAR_DIAMOND.get());
                                // Paintbrushes
                                output.accept(ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), null));
                                for (DyeColor color : DyeColor.values()) {
                                    output.accept(ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), color));
                                }
                                // List, Map, Connector, Volume Box
                                output.accept(BCCoreItems.LIST.get());
                                output.accept(BCCoreItems.MAP_LOCATION.get());
                                output.accept(BCCoreItems.MARKER_CONNECTOR.get());
                                output.accept(BCCoreItems.VOLUME_BOX.get());
                                // Snapshots
                                output.accept(BCBuildersItems.BLUEPRINT_CLEAN.get());
                                output.accept(BCBuildersItems.TEMPLATE_CLEAN.get());
                                output.accept(BCBuildersItems.SCHEMATIC_SINGLE_CLEAN.get());
                                output.accept(BCBuildersItems.FILLER_PLANNER.get());
                                // Builder Blocks
                                output.accept(BCBuildersBlocks.FILLER.get());
                                output.accept(BCBuildersBlocks.BUILDER.get());
                                output.accept(BCBuildersBlocks.ARCHITECT.get());
                                output.accept(BCBuildersBlocks.LIBRARY.get());
                                output.accept(BCBuildersBlocks.REPLACER.get());
                                output.accept(BCBuildersBlocks.FRAME.get());
                                output.accept(BCBuildersBlocks.QUARRY.get());
                                // Oil Glob
                                output.accept(BCEnergyItems.GLOB_OF_OIL.get());
                                // Factory Blocks
                                output.accept(BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());
                                output.accept(BCFactoryBlocks.MINING_WELL.get());
                                output.accept(BCFactoryBlocks.PUMP.get());
                                output.accept(BCFactoryBlocks.FLOOD_GATE.get());
                                output.accept(BCFactoryBlocks.TANK.get());
                                output.accept(BCFactoryBlocks.CHUTE.get());
                                output.accept(BCFactoryBlocks.DISTILLER.get());
                                output.accept(BCFactoryBlocks.HEAT_EXCHANGE.get());
                                output.accept(BCFactoryItems.WATER_GEL_SPAWN.get());
                                output.accept(BCFactoryItems.GELLED_WATER.get());
                                // Robotics (Zone Planner is dev-only)
                                if (BCRoboticsItems.ZONE_PLANNER != null) {
                                    output.accept(BCRoboticsItems.ZONE_PLANNER.get());
                                }
                                // Transport
                                output.accept(BCTransportItems.FILTERED_BUFFER.get());
                                output.accept(BCTransportItems.WATERPROOF.get());
                                // Silicon
                                output.accept(BCSiliconBlocks.LASER.get());
                                output.accept(BCSiliconBlocks.ASSEMBLY_TABLE.get());
                                output.accept(BCSiliconBlocks.ADVANCED_CRAFTING_TABLE.get());
                                output.accept(BCSiliconBlocks.INTEGRATION_TABLE.get());
                                output.accept(BCSiliconItems.REDSTONE_RED_CHIPSET.get());
                                output.accept(BCSiliconItems.REDSTONE_IRON_CHIPSET.get());
                                output.accept(BCSiliconItems.REDSTONE_GOLD_CHIPSET.get());
                                output.accept(BCSiliconItems.REDSTONE_QUARTZ_CHIPSET.get());
                                output.accept(BCSiliconItems.REDSTONE_DIAMOND_CHIPSET.get());
                                output.accept(BCSiliconItems.GATE_COPIER.get());
                                // Fluids
                                for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
                                    output.accept(entry.bucket().get());
                                }
                            })
                            .build());

    public static void init(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}


