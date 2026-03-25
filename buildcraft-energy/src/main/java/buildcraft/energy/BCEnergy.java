package buildcraft.energy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.enums.EnumSpring;
import buildcraft.energy.client.BCEnergyFluidsClient;
import buildcraft.energy.tile.TileSpringOil;
import buildcraft.lib.misc.MultiTankResourceHandler;

@Mod(BCEnergy.MODID)
public class BCEnergy {
    public static final String MODID = "buildcraftenergy";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCEnergy.class);

    public static BCEnergy INSTANCE;

    public BCEnergy(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // Register all deferred registries
        BCEnergyFluids.init(modEventBus);
        BCEnergyBlocks.init(modEventBus);
        BCEnergyItems.init(modEventBus);
        BCEnergyBlockEntities.init(modEventBus);
        BCEnergyMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(BCEnergyFluidsClient.class);
            modEventBus.register(buildcraft.energy.client.BCEnergyClient.class);
        }

        // Creative tab
        modEventBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, this::addCreativeTabItems);

        // Register NeoForge capabilities for engines
        modEventBus.addListener(this::registerCapabilities);

        // Setup event for things that need registries to be frozen
        modEventBus.addListener(this::commonSetup);

        // MC 26.1: Register recipes on ServerAboutToStartEvent instead of commonSetup,
        // because ItemStack/FluidStack constructors now require bound registry components.
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);

        LOGGER.info("BuildCraft Energy initialized");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Fluid capability for the combustion engine — exposes fuel, coolant, residue tanks.
        // Only the residue tank (index 2) allows extraction, matching 1.12.2's InternalFluidHandler
        // where drain() only operated on tankResidue.
        // Returns null on the output face to prevent fluid pipes connecting to the "nose".
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            BCEnergyBlockEntities.ENGINE_IRON.get(),
            (engine, direction) -> {
                if (direction == engine.getOrientation()) return null;
                return new MultiTankResourceHandler(
                    engine.tankFuel, engine.tankCoolant, engine.tankResidue
                ) {
                    @Override
                    public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                            int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                        // Only allow extraction from the residue tank (index 2)
                        if (index != 2) return 0;
                        return super.extract(index, resource, amount, transaction);
                    }
                };
            }
        );

        // Item handler capability for the stirling engine — allows item pipes
        // to insert fuel (1.12.2 used ItemHandlerSimple with EnumAccess.BOTH).
        // Returns null on the output face to prevent item pipes connecting to the "nose".
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            BCEnergyBlockEntities.ENGINE_STONE.get(),
            (engine, direction) -> direction == engine.getOrientation() ? null : engine.fuelItemHandler
        );

        // MJ connector capability for stone and iron engines — needed for power pipe
        // connection detection (matches the registrations in BCCore for redstone/creative engines)
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCEnergyBlockEntities.ENGINE_STONE.get(),
            (engine, direction) -> engine.getMjConnector()
        );
        event.registerBlockEntity(
            buildcraft.api.mj.MjAPI.CAP_CONNECTOR,
            BCEnergyBlockEntities.ENGINE_IRON.get(),
            (engine, direction) -> engine.getMjConnector()
        );
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        // Insert engines after the redstone engine to match 1.12.2 order:
        // Redstone Engine, Stirling Engine, Combustion Engine, Creative Engine
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            net.minecraft.world.item.ItemStack redstoneAnchor = new net.minecraft.world.item.ItemStack(
                    buildcraft.core.BCCoreItems.ENGINE_REDSTONE.get());
            net.minecraft.world.item.ItemStack stoneStack = new net.minecraft.world.item.ItemStack(
                    BCEnergyItems.ENGINE_STONE.get());
            net.minecraft.world.item.ItemStack ironStack = new net.minecraft.world.item.ItemStack(
                    BCEnergyItems.ENGINE_IRON.get());

            event.insertAfter(redstoneAnchor, stoneStack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.insertAfter(stoneStack, ironStack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);

            // Glob of Oil — positioned between the quarry and autoworkbench to match 1.12.2
            // Use registry lookup since buildcraft-energy doesn't depend on buildcraft-builders
            net.minecraft.world.level.block.Block quarryBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getValue(net.minecraft.resources.Identifier.parse("buildcraftbuilders:quarry"));
            if (quarryBlock != net.minecraft.world.level.block.Blocks.AIR) {
                net.minecraft.world.item.ItemStack quarryAnchor = new net.minecraft.world.item.ItemStack(quarryBlock);
                net.minecraft.world.item.ItemStack globStack = new net.minecraft.world.item.ItemStack(
                        BCEnergyItems.GLOB_OF_OIL.get());
                event.insertAfter(quarryAnchor, globStack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } else {
                // Fallback: just append if quarry isn't loaded
                event.accept(new net.minecraft.world.item.ItemStack(BCEnergyItems.GLOB_OF_OIL.get()));
            }

            // Fluid buckets — only add cool (heat==0) variants, matching 1.12.2 behavior
            for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
                if (entry.heat() == 0) {
                    event.accept(new net.minecraft.world.item.ItemStack(entry.bucket().get()),
                            net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                }
            }
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // MC 26.1: Recipe registration moved to ServerAboutToStartEvent
            // — see onServerAboutToStart()

            // Wire the oil spring enum to our fluid and tile entity
            EnumSpring.OIL.liquidBlock = BCEnergyFluids.OIL_COOL.block().get().defaultBlockState();
            EnumSpring.OIL.tileConstructor = () -> {
                return null;
            };

            // Initialize world gen
            BCEnergyWorldGen.init();
        });
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        // MC 26.1: Recipe/fuel/coolant registration moved here from commonSetup
        // because ItemStack/FluidStack constructors now access Holder.components(),
        // which is only bound after registries finish freezing.
        BCEnergyRecipes.init();
    }
}
