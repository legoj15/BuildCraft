package buildcraft;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;

import buildcraft.integration.pipes.PipeRoutingTest;

@EventBusSubscriber(modid = "buildcraftunofficial")
public class BuildCraftGameTests {
    // 1.21.11 introduced dynamic TEST_FUNCTION registries. We create a DeferredRegister for it.
    // Wait, TEST_FUNCTION is in net.minecraft.core.registries.Registries.TEST_FUNCTION
    @net.neoforged.bus.api.SubscribeEvent
    public static void onRegister(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
            // Pipes
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_routing_test_simple"), () -> PipeRoutingTest::testSimplePipeExtraction);
            
            // Inventory Transactors
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:item_transactor_simple_moving"), () -> buildcraft.lib.inventory.ItemTransactorTester::testSimpleMoving);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:item_transactor_limited_inventory"), () -> buildcraft.lib.inventory.ItemTransactorTester::testLimitedInventory);
            
            // Shape Patterns
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:shape_pattern_tiny_template"), () -> buildcraft.core.builders.patterns.ShapePatternsTester::testTinyTemplate);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:shape_pattern_sphere_equality"), () -> buildcraft.core.builders.patterns.ShapePatternsTester::testSphereEquality);
            // List Matching
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_tools_matching"), () -> buildcraft.lib.list.ListTester::testTools);
            
            // Core Blocks
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:core_spring_water"), () -> buildcraft.core.block.SpringTester::testWaterSpring);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:core_spring_oil"), () -> buildcraft.core.block.SpringTester::testOilSpring);
        }
    }
}
