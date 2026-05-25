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
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_records_owner_on_placement"), () -> buildcraft.transport.PipeOwnerTester::testPipeRecordsOwnerOnPlacement);

            // Painted pipe connectivity — pins canColoursConnect's null/equal-colour rules
            // across the full paint pipeline (direct setColour, paintbrush event, NBT round-trip).
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:painted_pipe_unpainted_void_fluid_connects"), () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testUnpaintedVoidFluidPipeConnects);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:painted_pipe_pink_void_connects_to_unpainted"), () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testPinkPaintedVoidFluidPipeStillConnectsToUnpaintedNeighbour);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:painted_pipe_paintbrush_pink_void_connects"), () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testPaintbrushPaintedVoidFluidPipeStillConnects);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:painted_pipe_nbt_roundtrip_preserves"), () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testNbtRoundTripPreservesColourAndConnections);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:painted_pipe_two_pink_connect"), () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testTwoPinkFluidPipesConnect);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:painted_pipe_different_colours_do_not_connect"), () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testDifferentColouredFluidPipesDoNotConnect);

            // Wire connectivity predicate gating the logic_transportation advancement
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_connectivity_isolated"), () -> buildcraft.transport.block.PipeWireConnectivityTester::testIsolatedWireNotConnected);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_connectivity_in_cube_same_colour"), () -> buildcraft.transport.block.PipeWireConnectivityTester::testInCubeSameColourConnected);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_connectivity_in_cube_different_colour"), () -> buildcraft.transport.block.PipeWireConnectivityTester::testInCubeDifferentColourNotConnected);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_connectivity_cross_tile_same_colour"), () -> buildcraft.transport.block.PipeWireConnectivityTester::testCrossTileSameColourConnected);

            // Per-player wire-colour tracking gating the colorful_electrician advancement
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_colours_fresh_attachment_empty"), () -> buildcraft.transport.WireColoursPlacedTester::testFreshAttachmentEmpty);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_colours_mark_placed_first_sighting_only"), () -> buildcraft.transport.WireColoursPlacedTester::testMarkPlacedReturnsTrueOnlyOnFirstSighting);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_colours_complete_only_after_all_sixteen"), () -> buildcraft.transport.WireColoursPlacedTester::testCompleteOnlyAfterAllSixteenColours);

            // Per-player pluggable-kind tracking gating the all_plugged_up advancement
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pluggables_placed_fresh_attachment_empty"), () -> buildcraft.transport.PluggablesPlacedTester::testFreshAttachmentEmpty);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pluggables_placed_mark_placed_first_sighting_only"), () -> buildcraft.transport.PluggablesPlacedTester::testMarkPlacedReturnsTrueOnlyOnFirstSighting);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pluggables_placed_complete_only_after_all_eight"), () -> buildcraft.transport.PluggablesPlacedTester::testCompleteOnlyAfterAllEightKinds);

            // Placement-preview AABB contract — IItemPluggable.getPlacementBoundingBox(side) must
            // match the pluggable's actual getBoundingBox(side) for every non-default-sized item,
            // otherwise the outline lies about where the pluggable will land.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:placement_preview_blocker_matches"), () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testBlockerPreviewMatchesPlacedBox);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:placement_preview_power_adaptor_matches"), () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testPowerAdaptorPreviewMatchesPlacedBox);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:placement_preview_lens_matches"), () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testLensPreviewMatchesPlacedBox);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:placement_preview_facade_matches"), () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testFacadePreviewMatchesPlacedBox);

            // Per-player oil/fuel production tracking gating the refine_and_redefine advancement
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:refine_and_redefine_fresh_attachment_empty"), () -> buildcraft.factory.OilAndFuelProductionTester::testFreshAttachmentEmpty);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:refine_and_redefine_clamps_at_target"), () -> buildcraft.factory.OilAndFuelProductionTester::testRecordProductionClampsAtTarget);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:refine_and_redefine_ignores_unknown_and_non_positive"), () -> buildcraft.factory.OilAndFuelProductionTester::testRecordProductionIgnoresUnknownAndNonPositive);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:refine_and_redefine_completion_edge_once"), () -> buildcraft.factory.OilAndFuelProductionTester::testCompletionEdgeFiresExactlyOnce);

            // Power pipe flow chain — engine→wood diamond→diamond→tester end-to-end.
            // The renderer-side regression (visible gaps) is geometry math, pinned
            // directly by PipeFlowRendererPowerGeometryTester.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:power_pipe_engine_to_tester"), () -> buildcraft.transport.pipe.flow.PipeFlowPowerTester::testEngineThroughDiamondPipesPowersTester);

            // Transport Storage
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:filtered_buffer_drops"), () -> buildcraft.transport.FilteredBufferTester::testFilteredBufferDrops);
            
            // Fluids
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:oil_water_interaction"), () -> buildcraft.energy.OilWaterInteractionTest::testOilOverWater);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:oil_bobbing_physics"), () -> buildcraft.energy.FluidPhysicsTest::testOilBobbing);
            
            // Inventory Transactors
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:item_transactor_simple_moving"), () -> buildcraft.lib.inventory.ItemTransactorTester::testSimpleMoving);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:item_transactor_limited_inventory"), () -> buildcraft.lib.inventory.ItemTransactorTester::testLimitedInventory);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:item_handler_simple_component_round_trip"), () -> buildcraft.lib.inventory.ItemTransactorTester::testComponentRoundTrip);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:item_handler_simple_legacy_id_count_fallback"), () -> buildcraft.lib.inventory.ItemTransactorTester::testLegacyIdCountFallback);
            
            // Shape Patterns
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:shape_pattern_tiny_template"), () -> buildcraft.core.builders.patterns.ShapePatternsTester::testTinyTemplate);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:shape_pattern_sphere_equality"), () -> buildcraft.core.builders.patterns.ShapePatternsTester::testSphereEquality);
            // List Matching
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_tools_matching"), () -> buildcraft.lib.list.ListTester::testTools);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_tags_matching"), () -> buildcraft.lib.list.ListTester::testTags);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_armor_matching"), () -> buildcraft.lib.list.ListTester::testArmor);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_fluid_matching"), () -> buildcraft.lib.list.ListTester::testFluid);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_end_to_end_by_type"), () -> buildcraft.lib.list.ListTester::testEndToEndByType);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_tags_single_segment_material_fallback"), () -> buildcraft.lib.list.ListTester::testTagsSingleSegmentMaterialFallback);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_end_to_end_both_flags_union"), () -> buildcraft.lib.list.ListTester::testEndToEndBothFlagsUnion);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:list_precise_enchantment_round_trip"), () -> buildcraft.lib.list.ListTester::testPreciseEnchantmentRoundTrip);
            
            // Core Blocks
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:core_spring_water"), () -> buildcraft.core.block.SpringTester::testWaterSpring);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:core_spring_oil"), () -> buildcraft.core.block.SpringTester::testOilSpring);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:core_spring_oil_attaches_tile"), () -> buildcraft.core.block.SpringTester::testOilSpringAttachesTile);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:core_spring_oil_regenerates_through_water"), () -> buildcraft.core.block.SpringTester::testOilSpringRegeneratesThroughWater);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:core_spring_oil_keeps_solid_above"), () -> buildcraft.core.block.SpringTester::testOilSpringKeepsSolidBlockAbove);

            // Core Markers
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:marker_orientation"), () -> buildcraft.core.marker.MarkerTester::testMarkerOrientation);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:marker_volume_los"), () -> buildcraft.core.marker.MarkerTester::testVolumeLineOfSight);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:marker_path_los"), () -> buildcraft.core.marker.MarkerTester::testPathLineOfSight);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:marker_volume_triangulation_2d"), () -> buildcraft.core.marker.MarkerTester::testVolumeTriangulation2D);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:marker_volume_triangulation_3d"), () -> buildcraft.core.marker.MarkerTester::testVolumeTriangulation3D);

            // Oil generation primitives (SurfacePool shape + defensive tree-clearing)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:oilgen_surface_pool_clean_shape"), () -> buildcraft.energy.generation.OilGenStructureTester::testSurfacePoolCleanShape);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:oilgen_surface_pool_clears_tall_tree_fully"), () -> buildcraft.energy.generation.OilGenStructureTester::testSurfacePoolClearsTallTreeFully);

            // Energy Engines
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_redstone_dry_run"), () -> buildcraft.energy.EngineTester::testRedstoneEngineDryRunHeat);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_redstone_safe_limit"), () -> buildcraft.energy.EngineTester::testRedstoneEngineSafeLimit);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_stirling_fuel"), () -> buildcraft.energy.EngineTester::testStirlingEngineFuel);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_stirling_explosion"), () -> buildcraft.energy.EngineTester::testStirlingEngineExplosion);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_combustion_stable"), () -> buildcraft.energy.EngineTester::testCombustionEngineStable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_overheat_no_explode_default"), () -> buildcraft.energy.EngineTester::testStirlingEngineOverheatNoExplodeDefault);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_overheat_explodes_when_configured"), () -> buildcraft.energy.EngineTester::testStirlingEngineExplodesWhenConfigured);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_clear_overheat_api"), () -> buildcraft.energy.EngineTester::testEngineClearOverheatApi);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_has_alternate_receiver_isolated"), () -> buildcraft.energy.EngineTester::testEngineHasAlternateReceiverIsolated);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:combustion_engine_coolant_accepts_ice"), () -> buildcraft.energy.EngineTester::testCombustionEngineCoolantTankAcceptsIce);

            // Energy Converter (Dynamo MJ + FE Engine)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:dynamo_upgrade_slot_filtering"), () -> buildcraft.energy.EnergyConverterTester::testDynamoUpgradeSlotFiltering);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_fe_upgrade_slot_filtering"), () -> buildcraft.energy.EnergyConverterTester::testEngineFeUpgradeSlotFiltering);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:dynamo_upgrade_effectiveness"), () -> buildcraft.energy.EnergyConverterTester::testDynamoUpgradeEffectiveness);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_fe_upgrade_effectiveness"), () -> buildcraft.energy.EnergyConverterTester::testEngineFeUpgradeEffectiveness);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:dynamo_gui_insertion"), () -> buildcraft.energy.EnergyConverterTester::testDynamoUpgradeGUIInsertion);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:engine_fe_gui_insertion"), () -> buildcraft.energy.EnergyConverterTester::testEngineFeUpgradeGUIInsertion);
            
            // GUI Headless Tests
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gui_test_dynamo_upgrade"), () -> buildcraft.lib.test.gui.GuiTester::testDynamoUpgrade);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gui_test_filler_ui"), () -> buildcraft.lib.test.gui.GuiTester::testFillerUI);
            // Slot Tests
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:slot_max_stack_size"), () -> buildcraft.lib.gui.SlotBaseTester::testSlotMaxStackSize);

            // Filler Inventory Filtering
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:filler_block_item_filter"), () -> buildcraft.builders.FillerInventoryTester::testFillerBlockItemFilter);

            // Template Builder — fillable-slot classification (grass tufts / snow / fluids).
            // Regression guard for the user-reported "Filler with Excavate doesn't clear grass
            // tufts" bug: TemplateBuilder.isBlockCorrect used to flag any non-air block as
            // CORRECT, so replaceable blocks (tall_grass, snow_layer, …) sat unfilled in the
            // box. Fluids stay excluded so the fluid-mode logic keeps owning that path.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_air"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testAirIsFillable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_tall_grass"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testTallGrassIsFillable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_short_grass"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testShortGrassIsFillable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_snow_layer"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testSnowLayerIsFillable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_water_source_excluded"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testWaterSourceIsNotFillable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_lava_source_excluded"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testLavaSourceIsNotFillable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_solid_not"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testSolidBlockIsNotFillable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:template_fillable_waterlogged_fence_not"), () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testWaterloggedFenceIsNotFillable);

            // Builder Drops (self + inventory contents)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_drops_contents_and_self"), () -> buildcraft.builders.BuilderDropsTester::testBuilderDropsContentsAndSelf);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:architect_drops_contents_and_self"), () -> buildcraft.builders.BuilderDropsTester::testArchitectDropsContentsAndSelf);
            // Builder consumed-path NBT round-trip — gates both the client-side path-laser
            // render and the server-side chunk-reload persistence of the route.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_path_survives_nbt_roundtrip"), () -> buildcraft.builders.BuilderDropsTester::testBuilderPathSurvivesNbtRoundTrip);

            // Snapshot.countNonAirCells — drives start_of_something_big's 1024 threshold.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:snapshot_template_counts_set_bits"), () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testTemplateCountsSetBits);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:snapshot_blueprint_excludes_air"), () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testBlueprintExcludesAirEntries);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:snapshot_blueprint_fluid_source_counts"), () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testBlueprintFluidSourceCountsAsNonAir);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:snapshot_blueprint_empty_null_safety"), () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testBlueprintEmptyAndNullSafety);

            // Compressed-NBT payload framing — encoder/decoder must agree on the exact byte
            // length, otherwise netty drops the custom_payload packet with "found N bytes extra".
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:architect_preview_payload_roundtrip_small"), () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripSmall);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:architect_preview_payload_roundtrip_medium"), () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripMedium);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:architect_preview_payload_roundtrip_large"), () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripLarge);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:architect_preview_payload_roundtrip_huge"), () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripHuge);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:architect_preview_payload_roundtrip_boundary_sweep"), () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripBoundarySweep);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:snapshot_response_payload_roundtrip_medium"), () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testSnapshotResponsePayloadRoundTripMedium);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:snapshot_response_payload_roundtrip_large"), () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testSnapshotResponsePayloadRoundTripLarge);

            // Builder paving_the_way + start_of_something_big — predicates and NBT shape.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_paving_rejects_no_path"), () -> buildcraft.builders.tile.BuilderAdvancementsTester::testPavingTheWayRejectsSinglePositionBuilder);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_paving_rejects_mid_path"), () -> buildcraft.builders.tile.BuilderAdvancementsTester::testPavingTheWayRejectsMidPath);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_paving_accepts_at_last_position"), () -> buildcraft.builders.tile.BuilderAdvancementsTester::testPavingTheWayAcceptsAtLastPosition);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_big_structure_threshold_matches_spec"), () -> buildcraft.builders.tile.BuilderAdvancementsTester::testBigStructureThresholdMatchesSpec);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_advancement_state_survives_nbt_roundtrip"), () -> buildcraft.builders.tile.BuilderAdvancementsTester::testNbtRoundTripPreservesAdvancementState);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_fresh_advancement_state_zero"), () -> buildcraft.builders.tile.BuilderAdvancementsTester::testFreshBuilderAdvancementStateIsZero);

            // Quarry TaskBreakBlock drop routing in both phases (frame-clearing drillPos=null, mining drillPos!=null)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:quarry_frame_clearing_routes_drops"), () -> buildcraft.builders.tile.TileQuarryDropsTester::testFrameClearingRoutesDropsToAdjacentChest);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:quarry_mining_routes_drops"), () -> buildcraft.builders.tile.TileQuarryDropsTester::testMiningRoutesDropsToAdjacentChest);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:quarry_tick_reconciles_stale_mining_floor"), () -> buildcraft.builders.tile.TileQuarryMiningDepthTester::testTickReconcilesStaleMiningFloor);

            // Owner-on-placement contract — load-bearing for both quarry advancements
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:quarry_on_placed_by_records_owner"), () -> buildcraft.builders.tile.TileQuarryOwnerTester::onPlacedByRecordsOwner);

            // Per-owner pairing predicate gating the destroying_the_world advancement
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:destroying_the_world_same_player_grants"), () -> buildcraft.builders.tile.DestroyingTheWorldTester::samePlayerTwoFullQuarriesGrants);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:destroying_the_world_different_owners_no_grant"), () -> buildcraft.builders.tile.DestroyingTheWorldTester::differentOwnersDoNotGrant);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:destroying_the_world_undersized_no_grant"), () -> buildcraft.builders.tile.DestroyingTheWorldTester::undersizedFrameDoesNotGrant);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:destroying_the_world_outside_window_no_grant"), () -> buildcraft.builders.tile.DestroyingTheWorldTester::outsideWindowDoesNotGrant);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:destroying_the_world_never_stamped_no_grant"), () -> buildcraft.builders.tile.DestroyingTheWorldTester::neverStampedDoesNotGrant);

            // Blueprint palette replacement (used by the Replacer block).
            // Logic also verified end-to-end in-client via the Replacer GUI.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blueprint_replace_scan_context"), () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testScanContextDifferenceDoesNotBlockMatch);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blueprint_replace_data_untouched"), () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testReplaceLeavesDataArrayUntouched);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blueprint_replace_multi_occurrences"), () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testMultipleOccurrencesInPaletteAllReplaced);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blueprint_replace_no_match"), () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testNoMatchLeavesPaletteUnchanged);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blueprint_count_matching_cells"), () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testCountMatchingCellsCountsBlocks);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blueprint_replace_null_noop"), () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testReplaceNullIsNoOp);

            // Builder fluid-handling mode (waterlog/destroy at place sites; enum sanity).
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_replace_water_waterloggable"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceWaterSourceWithWaterloggableBlock);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_replace_water_solid"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceWaterSourceWithSolidBlock);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_replace_lava_waterloggable"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceLavaSourceWithWaterloggableBlock);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_clear_destroys_source_at_place"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testClearDestroysSourceInsteadOfWaterlogging);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_no_replace_no_waterlog"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testNoReplaceModeDoesNotWaterlog);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_flowing_not_destroyed"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFlowingFluidIsNotSpeciallyDestroyed);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_cycle_all_states"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFluidModeCyclesThroughAllStates);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_ordinal_clamps"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFluidModeFromOrdinalClampsOutOfRange);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_isbuilt_accepts_waterlogged_world"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltAcceptsWorldWaterloggedWhenSchematicDry);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_isbuilt_rejects_dry_world"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltRejectsWorldDryWhenSchematicWaterlogged);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_fragile_deferred_with_neighbour_source"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFragileBlockDeferredWhenNeighbourSourceExists);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_fragile_placed_when_isolated"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFragileBlockPlacedWhenSourceIsIsolated);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_solid_placed_despite_adjacent_fluid"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testSolidBlockPlacedDespiteAdjacentFluid);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_fragile_deferred_for_fluid_above"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFragileBlockDeferredForFluidAbove);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_waterloggable_not_deferred"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testWaterloggableBlockNotDeferredNearWater);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_no_replace_skips_fragile_check"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testNoReplaceModeSkipsFragileCheck);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_clears_water_source"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsClearsWaterSource);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_clears_flowing_water"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsClearsFlowingWater);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_solid_blocks"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsStillBreaksSolidBlocks);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_honours_tool_for_loot"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsHonoursToolForLoot);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_respects_tool_tier"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsRespectsToolTier);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_with_xp_reports_xp"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsWithXpReportsXp);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_captures_fluid_source"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsWithXpCapturesFluidSource);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_break_skips_flowing_fluid_capture"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsWithXpSkipsFlowingFluidCapture);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_water_break_cost"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testWaterBreakCostIsOneMj);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_lava_break_cost"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testLavaBreakCostIsOneMj);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:blockutil_stone_break_cost_unchanged"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testSolidBlockBreakCostUnchanged);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_clear_dries_waterlogged_block"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testClearModeDriesWaterloggedBlock);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_replace_still_opportunistically_waterlogs"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceModeStillOpportunisticallyWaterlogs);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_isbuilt_strict_in_clear"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltStrictInClearMode);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_isbuilt_lenient_in_replace"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltLenientInReplaceMode);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_clear_only_under_clear"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsWaterlogClearOnlyOnlyFiresUnderClear);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluidmode_clear_only_requires_matching_block"), () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsWaterlogClearOnlyRequiresMatchingBlock);

            // Builder block-survival gating (canSurvive guard in SchematicBlockDefault.build()).
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_support_torch_on_floor"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testTorchOnFloorPlaces);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_support_wall_torch_with_support"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testWallTorchSurvivesWithSupport);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_support_wall_torch_no_support"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testWallTorchRejectedWithoutSupport);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_support_ladder_freestanding_rejected"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testLadderRejectedFreestanding);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_support_fence_always_survives"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testFenceAlwaysSurvives);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_support_sea_pickle_over_water"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testSeaPickleOverWaterSurvives);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_leaves_placed_persistent"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testLeavesPlacedAsPersistent);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_wall_isbuilt_ignores_connections"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testWallIsBuiltIgnoresConnections);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_fence_isbuilt_ignores_connections"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testFenceIsBuiltIgnoresConnections);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_deserialize_migrates_wall_ignored_properties"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testDeserializeMigratesIgnoredPropertiesFromCurrentRules);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:builder_deserialize_migrates_leaves_ignored_properties"), () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testDeserializeMigratesLeavesIgnoredProperties);

            // Container-contents capture — INCLUDE-mode invariants: the items_list extractor
            // surfaces chest contents into computeRequiredItems(), AND the build() path restores
            // those items into the placed chest. Both sides of the player-pays-and-receives flow.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:container_contents_listed_as_required"), () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testChestContentsListedAsRequiredItems);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:container_contents_restored_on_build"), () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testChestBuiltWithContentsFromSchematic);
            // IGNORE-mode contract: computeRequiredItems(false) drops items_list contributions
            // and build(..., includeContents=false) places the chest with an empty inventory.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:container_contents_ignore_omits_required"), () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testIgnoreModeOmitsContentsFromRequiredItems);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:container_contents_ignore_builds_empty"), () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testIgnoreModeBuildsEmptyChest);

            // Heat Exchanger fluid filtering (heatant on START, coolant on END; output drain-only)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:heat_exchanger_output_rejects_external_insert"), () -> buildcraft.factory.HeatExchangerTester::testOutputTankRejectsExternalInsert);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:heat_exchanger_output_accepts_internal_insert"), () -> buildcraft.factory.HeatExchangerTester::testOutputTankAcceptsInternalInsert);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:heat_exchanger_internal_flag_resets"), () -> buildcraft.factory.HeatExchangerTester::testOutputTankInternalFlagResetsAfterCall);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:heat_exchanger_atomic_craft_balanced"), () -> buildcraft.factory.HeatExchangerTester::testAtomicCraftCommitsBalancedFillAndDrain);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:heat_exchanger_atomic_craft_rolls_back"), () -> buildcraft.factory.HeatExchangerTester::testAtomicCraftRollsBackOnUndersizedFill);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:heat_exchanger_tank_clears_on_empty_load"), () -> buildcraft.factory.HeatExchangerTester::testTankClearsWhenLoadedFromEmptySave);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:heat_exchanger_slot_caps_at_max_stack_size"), () -> buildcraft.factory.HeatExchangerTester::testItemHandlerRespectsConfiguredMaxStackSize);

            // Distiller wrench rotation (1.12.2 parity)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:distiller_wrench_rotates_clockwise"), () -> buildcraft.factory.DistillerTester::testWrenchRotatesClockwise);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:distiller_wrench_passes_through_use_item_on"), () -> buildcraft.factory.DistillerTester::testWrenchPassesThroughUseItemOn);

            // Wrench rotation of vanilla blocks (1.12.2 parity — see VanillaRotationHandlers)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_furnace"), () -> buildcraft.lib.block.VanillaRotationTester::testFurnaceCyclesHorizontally);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_dispenser"), () -> buildcraft.lib.block.VanillaRotationTester::testDispenserCyclesAllSix);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_hopper"), () -> buildcraft.lib.block.VanillaRotationTester::testHopperCyclesFiveFaces);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_piston_extended_refuses"), () -> buildcraft.lib.block.VanillaRotationTester::testExtendedPistonRefuses);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_banner_16"), () -> buildcraft.lib.block.VanillaRotationTester::testStandingBannerSpins16);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_skull_16"), () -> buildcraft.lib.block.VanillaRotationTester::testFloorSkullSpins16);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_trapdoor_half_flip"), () -> buildcraft.lib.block.VanillaRotationTester::testTrapDoorHalfFlipOnWrap);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_door_both_halves_and_hinge"), () -> buildcraft.lib.block.VanillaRotationTester::testDoorRotatesBothHalvesAndFlipsHingeOnWrap);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_wall_torch_attach_check"), () -> buildcraft.lib.block.VanillaRotationTester::testWallTorchSkipsUnsupportedFace);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_trapdoor_freestanding"), () -> buildcraft.lib.block.VanillaRotationTester::testTrapDoorRotatesFreestanding);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_button_twelve_orientations"), () -> buildcraft.lib.block.VanillaRotationTester::testButtonCyclesThroughAllTwelveOrientations);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_button_attach_to_air"), () -> buildcraft.lib.block.VanillaRotationTester::testButtonSkipsAttachToAir);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_double_chest"), () -> buildcraft.lib.block.VanillaRotationTester::testDoubleChestRotatesBothHalves);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_wrench_useon_furnace"), () -> buildcraft.lib.block.VanillaRotationTester::testWrenchUseOnRotatesFurnace);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:vanilla_rotation_wrench_crouch_gate"), () -> buildcraft.lib.block.VanillaRotationTester::testWrenchOnItemUseFirstCrouchGate);

            // Flood gate wrench toggle (advancement-granting useItemOn path)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:flood_gate_wrench_toggles_side"), () -> buildcraft.factory.FloodGateTester::testWrenchTogglesSide);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:flood_gate_wrench_on_top_face_falls_through"), () -> buildcraft.factory.FloodGateTester::testWrenchOnTopFaceFallsThrough);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:flood_gate_non_wrench_falls_through"), () -> buildcraft.factory.FloodGateTester::testNonWrenchItemFallsThrough);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:flood_gate_flooding_the_world_advancement"), () -> buildcraft.factory.FloodGateTester::testFloodingTheWorldAdvancement);

            // Filler building_for_the_future advancement (LOOP-mode completion + setControlMode re-arm)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:filler_building_for_the_future_advancement"), () -> buildcraft.builders.FillerAdvancementTester::testBuildingForTheFutureAdvancement);

            // Paper advancement contract (4 criteria, all required, names matching PaperAdvancement constants)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:paper_advancement_contract"), () -> buildcraft.core.PaperAdvancementTester::testPaperAdvancementContract);

            // Pump infinite-source detection (vanilla regen-rule parity per anchor block)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_strip_1x3_centre_vs_edges"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testStrip1x3CentreInfiniteEdgesFinite);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_isolated_source_finite"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testIsolatedSourceFinite);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_pond_2x2_all_corners"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testPond2x2AllCornersInfinite);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_strip_1x5_interior_vs_edges"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testStrip1x5InteriorInfiniteEdgesFinite);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_no_support_below"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testNoSupportBelowIsFinite);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_diagonals_dont_count"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testDiagonalNeighboursDoNotCount);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_water_below_supports"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testWaterBelowProvidesSupport);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_infinite_null_safety"), () -> buildcraft.factory.PumpInfiniteDetectionTester::testNullSafetyShortCircuits);

            // Pump spring-aware probe — drilling past water to a submerged oil spring
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_probe_oil_beneath_water"), () -> buildcraft.factory.PumpSpringProbeTester::testOilBeneathWaterIsFound);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_probe_dry_spring_under_water"), () -> buildcraft.factory.PumpSpringProbeTester::testDrySpringUnderWaterReportsNoOil);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_probe_plain_water_unaffected"), () -> buildcraft.factory.PumpSpringProbeTester::testPlainWaterColumnUnaffected);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pump_probe_solid_obstruction"), () -> buildcraft.factory.PumpSpringProbeTester::testSolidObstructionStopsProbe);

            // Distiller tank gating (1.12.2 setFilter / setCanDrain / setCanFill parity)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:distiller_input_rejects_non_distillable"), () -> buildcraft.factory.DistillerTester::testInputTankRejectsNonDistillableInsert);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:distiller_input_blocks_external_extract"), () -> buildcraft.factory.DistillerTester::testInputTankBlocksExternalExtract);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:distiller_output_rejects_external_insert"), () -> buildcraft.factory.DistillerTester::testOutputTanksRejectExternalInsertButAcceptInternal);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:distiller_heating_and_distilling_advancement"), () -> buildcraft.factory.DistillerTester::testHeatingAndDistillingAdvancement);

            // Advancements (fine_riches + sticky_dipping JSON shape, oil_fluids tag content, predicate sanity)
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_fine_riches_loaded"), () -> buildcraft.energy.AdvancementTester::testFineRichesAdvancementLoaded);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_sticky_dipping_loaded"), () -> buildcraft.energy.AdvancementTester::testStickyDippingAdvancementLoaded);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_lava_power_loaded"), () -> buildcraft.energy.AdvancementTester::testLavaPowerAdvancementLoaded);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_precision_crafting_loaded"), () -> buildcraft.energy.AdvancementTester::testPrecisionCraftingAdvancementLoaded);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_oil_fluids_tag_contents"), () -> buildcraft.energy.AdvancementTester::testOilFluidsTagContainsAllOilSourceBlocks);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_oil_fluids_tag_exclusivity"), () -> buildcraft.energy.AdvancementTester::testOilFluidsTagExcludesNonOilFluids);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_fine_riches_predicate_negative"), () -> buildcraft.energy.AdvancementTester::testWouldGenerateOilReturnsFalseInTestEnvironment);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:advancement_fine_riches_biome_tier_gate"), () -> buildcraft.energy.AdvancementTester::testFineRichesBiomeTierGate);

            // Electronic Library
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:library_slot_filtering"), () -> buildcraft.builders.ElectronicLibraryTester::testSlotFiltering);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:library_download_cycle"), () -> buildcraft.builders.ElectronicLibraryTester::testDownloadCycle);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:library_upload_progress"), () -> buildcraft.builders.ElectronicLibraryTester::testUploadProgressIncrements);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:library_download_idle"), () -> buildcraft.builders.ElectronicLibraryTester::testDownloadIdleWhenEmpty);

            // Block drops — every BuildCraft block should drop itself + its real inventory
            // when broken with the correct tool, drop only its inventory when broken by
            // hand, and never drop PHANTOM (template/filter) slot contents.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_decorated_pickaxe"), () -> BlockDropsTester::testDecoratedPickaxeDropsSelf);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_decorated_hand"), () -> BlockDropsTester::testDecoratedHandBreakDropsNothing);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_chute_pickaxe"), () -> BlockDropsTester::testChutePickaxeDropsContentsAndSelf);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_chute_hand"), () -> BlockDropsTester::testChuteHandBreakDropsContentsOnly);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_autoworkbench_skips_phantom"), () -> BlockDropsTester::testAutoWorkbenchSkipsPhantomSlots);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_tank_pickaxe"), () -> BlockDropsTester::testTankPickaxeDropsFluidShardAndSelf);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_tank_hand"), () -> BlockDropsTester::testTankHandBreakDropsFluidShardOnly);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_stirling_hand"), () -> BlockDropsTester::testStirlingEngineHandBreakDropsFuel);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_stirling_pickaxe"), () -> BlockDropsTester::testStirlingEnginePickaxeDropsFuelAndSelf);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_filtered_buffer_skips_filter"), () -> BlockDropsTester::testFilteredBufferSkipsFilterSlots);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:drops_marker_hand"), () -> BlockDropsTester::testMarkerHandBreakDropsSelf);

            // Stripes pipe direction NBT sync — covered by JUnit unit tests rather than game tests
            // (the regression is in PipeBehaviour.readFromNbt's no-op default; PipeBehaviourStripesSyncTester
            // exercises writeToNbt/readFromNbt round-trip directly with a null IPipe).

            // Pipe-specific drops — pluggable / wire click-break, pipe + cargo full break,
            // hand-break with cargo retention but no pipe item.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_pluggable_break"), () -> buildcraft.transport.PipeDropsTester::testPluggableBreakDropsItemAndKeepsPipe);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_wire_break"), () -> buildcraft.transport.PipeDropsTester::testWireBreakDropsItemAndKeepsPipe);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_pickaxe_break_drops_everything"), () -> buildcraft.transport.PipeDropsTester::testPipePickaxeBreakDropsEverything);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_hand_break_drops_everything"), () -> buildcraft.transport.PipeDropsTester::testPipeHandBreakDropsEverything);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_fluid_break_drops_shards"), () -> buildcraft.transport.PipeDropsTester::testFluidPipeBreakDropsFluidShards);

            // Machine ↔ pipe connectivity — item pipes must see machine inventories exposed as
            // Capabilities.Item.BLOCK (Auto Workbench, laser tables, Electronic Library).
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:autoworkbench_wood_pipe_extracts"), () -> buildcraft.factory.MachinePipeConnectivityTester::testWoodPipeExtractsFromAutoWorkbench);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:assemblytable_wood_pipe_skips_resources"), () -> buildcraft.factory.MachinePipeConnectivityTester::testWoodPipeSkipsAssemblyTableResources);

            // Gate modifier recipes must match the input gate by data-component variant, not just the
            // PLUG_GATE Item — see GateRecipeVariantTester for the regression context.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_modifier_recipe_accepts_correct_variant"), () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeAcceptsGoldPlainGate);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_modifier_recipe_rejects_wrong_material"), () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeRejectsIronPlainGate);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_modifier_recipe_rejects_already_modified"), () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeRejectsAlreadyModifiedGate);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_modifier_recipe_rejects_wrong_logic"), () -> buildcraft.silicon.GateRecipeVariantTester::testOrLogicRecipeRejectsAndLogicGate);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_modifier_recipe_display_preserves_variant"), () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeDisplayPreservesGoldVariant);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:basic_gate_recipe_emitted_by_collector"), () -> buildcraft.silicon.GateRecipeVariantTester::testBasicGateRecipeExistsAndCollectorEmitsIt);

            // Crafting-table gate recipes restored from 1.12.2 — Basic Gate (clay brick), Iron
            // and Nether Brick basic-gate alternatives, Iron modifier upgrades (lapis/quartz),
            // and the AND<->OR shapeless swap for every non-clay-brick (material, modifier).
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:basic_gate_crafting_recipe"), () -> buildcraft.silicon.GateCraftingRecipeTester::testBasicGateRecipe);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:iron_and_basic_crafting_recipe"), () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndBasicCraftRecipe);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:nether_brick_and_basic_crafting_recipe"), () -> buildcraft.silicon.GateCraftingRecipeTester::testNetherBrickAndBasicCraftRecipe);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:iron_and_lapis_crafting_recipe"), () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndLapisCraftRecipe);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:iron_and_quartz_crafting_recipe"), () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndQuartzCraftRecipe);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_swap_iron_and_to_or"), () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndToOrSwap);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_swap_iron_or_to_and"), () -> buildcraft.silicon.GateCraftingRecipeTester::testIronOrToAndSwap);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_swap_gold_diamond_and_to_or"), () -> buildcraft.silicon.GateCraftingRecipeTester::testGoldDiamondAndToOrSwap);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_swap_clay_brick_excluded"), () -> buildcraft.silicon.GateCraftingRecipeTester::testClayBrickSwapNotAvailable);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:autoworkbench_cobblestone_pipe_connects"), () -> buildcraft.factory.MachinePipeConnectivityTester::testCobblestonePipeConnectsToAutoWorkbench);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:autoworkbench_clay_pipe_inserts"), () -> buildcraft.factory.MachinePipeConnectivityTester::testClayPipeInsertsIntoAutoWorkbench);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:item_machines_expose_item_capability"), () -> buildcraft.factory.MachinePipeConnectivityTester::testItemMachinesExposeItemHandlerCapability);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:mj_battery_machines_expose_fe_autoconvert"), () -> buildcraft.factory.MachinePipeConnectivityTester::testMjBatteryMachinesExposeFeWhenAutoconvertEnabled);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:mj_battery_machines_hide_fe_mj_only"), () -> buildcraft.factory.MachinePipeConnectivityTester::testMjBatteryMachinesHideFeUnderMjOnly);

            // Tank bookkeeping — composite ResourceHandler<FluidResource> capacity-respect
            // and cross-slot spillover (modelled on TileBuilder.tankManager's per-slot delegate).
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:tank_single_capacity"), () -> buildcraft.lib.fluid.TankManagerTester::testSingleTankCapacityRespect);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:tank_single_extract_returns_only_held"), () -> buildcraft.lib.fluid.TankManagerTester::testSingleTankExtractReturnsOnlyWhatExists);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:tank_composite_insert_spillover"), () -> buildcraft.lib.fluid.TankManagerTester::testCompositeInsertSpillsAcrossSlots);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:tank_composite_extract_spillover"), () -> buildcraft.lib.fluid.TankManagerTester::testCompositeExtractDrainsAcrossSlots);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:tank_composite_insert_rolls_back"), () -> buildcraft.lib.fluid.TankManagerTester::testCompositeInsertRollsBackOnAbort);

            // Wire system signaling — pins out the gatesChanged reset behavior so the
            // "recompute every tick" bodge can't slip back in.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_steady_state_leaves_flag_false"), () -> buildcraft.transport.WireSystemTester::testSteadyStateLeavesGatesChangedFalse);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_gate_emit_propagates_and_resets"), () -> buildcraft.transport.WireSystemTester::testGateEmitPropagatesAndFlagResets);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:wire_resolve_actions_clearing_marks_dirty"), () -> buildcraft.transport.WireSystemTester::testGateResolveActionsClearingMarksGatesChanged);

            // Silicon — Gates: redstone-trigger sync. Pins that runtime display state
            // (isOn/triggerOn/actionOn) is recomputed every tick and never resurrected by an
            // inbound NBT data sync — the client desync this guards against.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_redstone_trigger_tracks_signal"), () -> buildcraft.silicon.gate.GateRedstoneSyncTester::testTriggerTracksRedstoneSignal);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_redstone_nbt_sync_no_clobber"), () -> buildcraft.silicon.gate.GateRedstoneSyncTester::testNbtSyncDoesNotClobberLiveState);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:gate_redstone_client_update_carries_state"), () -> buildcraft.silicon.gate.GateRedstoneSyncTester::testClientUpdateCarriesDisplayState);

            // Statement-parameter ItemStack serialization — pins the gate item-filter NBT
            // round-trip (writeToNbt <-> constructor) that the 26.1 port had stubbed out.
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:statement_item_param_round_trip"), () -> buildcraft.core.statements.StatementSerializationTester::testItemStackParamRoundTrip);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:statement_item_param_empty_round_trip"), () -> buildcraft.core.statements.StatementSerializationTester::testEmptyItemStackParamRoundTrip);
            event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse("buildcraftunofficial:statement_item_exact_param_round_trip"), () -> buildcraft.core.statements.StatementSerializationTester::testItemStackExactParamRoundTrip);
        }
    }
}
