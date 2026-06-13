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

    // Game-test registration. 1.21.5+ uses the dynamic Registries.TEST_FUNCTION registry + JSON
    // test_instance manifests; 1.21.1 (below that cliff) has neither, so it reflects TestFunctions
    // straight into GameTestRegistry against the buildcraftunofficial:empty arena
    // (data/buildcraftunofficial/structure/empty.nbt — a 16x16x16 air arena standing in for the
    // 1.21.5+ minecraft:empty structure, with the 1.21.1 DataVersion; the size gives encaseStructure
    // room for the oil-gen surface scan). registerAll() below is the SINGLE source of truth for all
    // node lines;
    // each node only differs in the registrar lambda it passes.
    //? if >=1.21.10 {
    @net.neoforged.bus.api.SubscribeEvent
    public static void onRegister(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
            registerAll((id, sup) -> event.register(Registries.TEST_FUNCTION, net.minecraft.resources.Identifier.parse(id), sup));
        }
    }
    //?} else {
    /*@net.neoforged.bus.api.SubscribeEvent
    public static void onRegisterGameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        registerAll((id, sup) -> addReflectively(id, sup));
    }

    // 1.21.1 has no dynamic TEST_FUNCTION registry; GameTestRegistry.TEST_FUNCTIONS is a private
    // mutable list the runner reads via getAllTestFunctions(). We build a TestFunction from the same
    // (id, supplier) the modern path registers and add it directly (dev/CI-only path).
    private static void addReflectively(String id, java.util.function.Supplier<java.util.function.Consumer<GameTestHelper>> sup) {
        try {
            java.lang.reflect.Field f = net.minecraft.gametest.framework.GameTestRegistry.class.getDeclaredField("TEST_FUNCTIONS");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Collection<net.minecraft.gametest.framework.TestFunction> coll =
                (java.util.Collection<net.minecraft.gametest.framework.TestFunction>) f.get(null);
            // skyAccess=true (11th arg) so StructureUtils.encaseStructure omits the BARRIER CEILING
            // (it always builds walls, but adds a ceiling only when !skyAccess). Without it the oil-gen
            // surface-scan (findSolidSurfaceTop, top-down) hits the ceiling instead of the test floor,
            // and spring tests can't place fluid above. Full ctor: batch, name, structure, rotation,
            // maxTicks, setupTicks, required, manualOnly, maxAttempts, requiredSuccesses, skyAccess, fn.
            coll.add(new net.minecraft.gametest.framework.TestFunction(
                "defaultBatch", id, "buildcraftunofficial:empty",
                net.minecraft.world.level.block.Rotation.NONE, 100, 0L, true, false, 1, 1, true, sup.get()));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register 1.21.1 game test " + id, e);
        }
    }*/
    //?}

    /** Single source of truth for every game-test registration. Both nodes call this; only the
     *  registrar differs (modern dynamic registry vs 1.21.1 GameTestRegistry reflection). */
    private static void registerAll(java.util.function.BiConsumer<String, java.util.function.Supplier<java.util.function.Consumer<GameTestHelper>>> reg) {
        // Pipes
        reg.accept("buildcraftunofficial:pipe_routing_test_simple", () -> PipeRoutingTest::testSimplePipeExtraction);
        reg.accept("buildcraftunofficial:pipe_records_owner_on_placement", () -> buildcraft.transport.PipeOwnerTester::testPipeRecordsOwnerOnPlacement);

        // Painted pipe connectivity — pins canColoursConnect's null/equal-colour rules
        // across the full paint pipeline (direct setColour, paintbrush event, NBT round-trip).
        reg.accept("buildcraftunofficial:painted_pipe_unpainted_void_fluid_connects", () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testUnpaintedVoidFluidPipeConnects);
        reg.accept("buildcraftunofficial:painted_pipe_pink_void_connects_to_unpainted", () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testPinkPaintedVoidFluidPipeStillConnectsToUnpaintedNeighbour);
        reg.accept("buildcraftunofficial:painted_pipe_paintbrush_pink_void_connects", () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testPaintbrushPaintedVoidFluidPipeStillConnects);
        reg.accept("buildcraftunofficial:painted_pipe_nbt_roundtrip_preserves", () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testNbtRoundTripPreservesColourAndConnections);
        reg.accept("buildcraftunofficial:painted_pipe_two_pink_connect", () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testTwoPinkFluidPipesConnect);
        reg.accept("buildcraftunofficial:painted_pipe_different_colours_do_not_connect", () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testDifferentColouredFluidPipesDoNotConnect);
        reg.accept("buildcraftunofficial:painted_pipe_no_fluid_stuck_toward_different_colour", () -> buildcraft.transport.pipe.PaintedPipeConnectionTester::testNoFluidStuckTowardDifferentColouredNeighbour);

        // Wire connectivity predicate gating the logic_transportation advancement
        reg.accept("buildcraftunofficial:wire_connectivity_isolated", () -> buildcraft.transport.block.PipeWireConnectivityTester::testIsolatedWireNotConnected);
        reg.accept("buildcraftunofficial:wire_connectivity_in_cube_same_colour", () -> buildcraft.transport.block.PipeWireConnectivityTester::testInCubeSameColourConnected);
        reg.accept("buildcraftunofficial:wire_connectivity_in_cube_different_colour", () -> buildcraft.transport.block.PipeWireConnectivityTester::testInCubeDifferentColourNotConnected);
        reg.accept("buildcraftunofficial:wire_connectivity_cross_tile_same_colour", () -> buildcraft.transport.block.PipeWireConnectivityTester::testCrossTileSameColourConnected);

        // Per-player wire-colour tracking gating the colorful_electrician advancement
        reg.accept("buildcraftunofficial:wire_colours_fresh_attachment_empty", () -> buildcraft.transport.WireColoursPlacedTester::testFreshAttachmentEmpty);
        reg.accept("buildcraftunofficial:wire_colours_mark_placed_first_sighting_only", () -> buildcraft.transport.WireColoursPlacedTester::testMarkPlacedReturnsTrueOnlyOnFirstSighting);
        reg.accept("buildcraftunofficial:wire_colours_complete_only_after_all_sixteen", () -> buildcraft.transport.WireColoursPlacedTester::testCompleteOnlyAfterAllSixteenColours);

        // Per-player pluggable-kind tracking gating the all_plugged_up advancement
        reg.accept("buildcraftunofficial:pluggables_placed_fresh_attachment_empty", () -> buildcraft.transport.PluggablesPlacedTester::testFreshAttachmentEmpty);
        reg.accept("buildcraftunofficial:pluggables_placed_mark_placed_first_sighting_only", () -> buildcraft.transport.PluggablesPlacedTester::testMarkPlacedReturnsTrueOnlyOnFirstSighting);
        reg.accept("buildcraftunofficial:pluggables_placed_complete_only_after_all_eight", () -> buildcraft.transport.PluggablesPlacedTester::testCompleteOnlyAfterAllEightKinds);

        // Placement-preview AABB contract — IItemPluggable.getPlacementBoundingBox(side) must
        // match the pluggable's actual getBoundingBox(side) for every non-default-sized item,
        // otherwise the outline lies about where the pluggable will land.
        reg.accept("buildcraftunofficial:placement_preview_blocker_matches", () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testBlockerPreviewMatchesPlacedBox);
        reg.accept("buildcraftunofficial:placement_preview_power_adaptor_matches", () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testPowerAdaptorPreviewMatchesPlacedBox);
        reg.accept("buildcraftunofficial:placement_preview_lens_matches", () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testLensPreviewMatchesPlacedBox);
        reg.accept("buildcraftunofficial:placement_preview_facade_matches", () -> buildcraft.transport.client.render.PipePlacementHighlightTester::testFacadePreviewMatchesPlacedBox);

        // Per-player oil/fuel production tracking gating the refine_and_redefine advancement
        reg.accept("buildcraftunofficial:refine_and_redefine_fresh_attachment_empty", () -> buildcraft.factory.OilAndFuelProductionTester::testFreshAttachmentEmpty);
        reg.accept("buildcraftunofficial:refine_and_redefine_clamps_at_target", () -> buildcraft.factory.OilAndFuelProductionTester::testRecordProductionClampsAtTarget);
        reg.accept("buildcraftunofficial:refine_and_redefine_ignores_unknown_and_non_positive", () -> buildcraft.factory.OilAndFuelProductionTester::testRecordProductionIgnoresUnknownAndNonPositive);
        reg.accept("buildcraftunofficial:refine_and_redefine_completion_edge_once", () -> buildcraft.factory.OilAndFuelProductionTester::testCompletionEdgeFiresExactlyOnce);

        // Power pipe flow chain — engine→wood diamond→diamond→tester end-to-end.
        // The renderer-side regression (visible gaps) is geometry math, pinned
        // directly by PipeFlowRendererPowerGeometryTester.
        reg.accept("buildcraftunofficial:power_pipe_engine_to_tester", () -> buildcraft.transport.pipe.flow.PipeFlowPowerTester::testEngineThroughDiamondPipesPowersTester);

        // FE pipes must size a receiver's demand by a simulated insert, not buffer headroom —
        // otherwise bufferless pass-through sinks (AE2's Energy Acceptor) are never fed.
        reg.accept("buildcraftunofficial:fe_pipe_bufferless_receiver_demand", () -> buildcraft.transport.pipe.flow.PipeFlowRedstoneFluxDemandTester::testBufferlessReceiverReportsDemand);

        // Transport Storage
        reg.accept("buildcraftunofficial:filtered_buffer_drops", () -> buildcraft.transport.FilteredBufferTester::testFilteredBufferDrops);

        // Custom crafting recipes (1.12.2 parity): pipe paint/bleach + facade hollow-swap
        reg.accept("buildcraftunofficial:pipe_paint_recolour", () -> buildcraft.transport.PipePaintRecipeTester::testRecolour);
        reg.accept("buildcraftunofficial:pipe_paint_count_matches_slots", () -> buildcraft.transport.PipePaintRecipeTester::testCountMatchesPipeSlots);
        reg.accept("buildcraftunofficial:pipe_paint_bleach", () -> buildcraft.transport.PipePaintRecipeTester::testBleach);
        reg.accept("buildcraftunofficial:pipe_paint_mixed_types_rejected", () -> buildcraft.transport.PipePaintRecipeTester::testMixedPipeTypesRejected);
        reg.accept("buildcraftunofficial:pipe_paint_rejects_clean_brush", () -> buildcraft.transport.PipePaintRecipeTester::testRejectsCleanBrush);
        reg.accept("buildcraftunofficial:pipe_paint_rejects_undercharged_brush", () -> buildcraft.transport.PipePaintRecipeTester::testRejectsUnderchargedBrush);
        reg.accept("buildcraftunofficial:facade_swap_toggles_hollow", () -> buildcraft.silicon.FacadeSwapRecipeTester::testSwapTogglesHollow);
        reg.accept("buildcraftunofficial:facade_swap_rejects_two", () -> buildcraft.silicon.FacadeSwapRecipeTester::testRejectsTwoFacades);
        reg.accept("buildcraftunofficial:dyed_pipe_symmetric_stone", () -> buildcraft.transport.DyedPipeRecipeTester::testSymmetricStonePipe);
        reg.accept("buildcraftunofficial:dyed_pipe_asymmetric_diamond_wood", () -> buildcraft.transport.DyedPipeRecipeTester::testAsymmetricDiamondWoodBothOrientations);
        reg.accept("buildcraftunofficial:dyed_pipe_colourless_glass_rejected", () -> buildcraft.transport.DyedPipeRecipeTester::testColourlessGlassRejected);

        // Fluids
        reg.accept("buildcraftunofficial:oil_water_interaction", () -> buildcraft.energy.OilWaterInteractionTest::testOilOverWater);
        reg.accept("buildcraftunofficial:oil_bobbing_physics", () -> buildcraft.energy.FluidPhysicsTest::testOilBobbing);
        reg.accept("buildcraftunofficial:energy_fluid_motion_scale_water_like", () -> buildcraft.energy.FluidPhysicsTest::energyFluidsMotionScaleIsWaterLike);
        reg.accept("buildcraftunofficial:crude_oil_is_not_water_like", () -> buildcraft.energy.FluidPhysicsTest::crudeOilIsNotWaterLike);

        // Inventory Transactors
        reg.accept("buildcraftunofficial:item_transactor_simple_moving", () -> buildcraft.lib.inventory.ItemTransactorTester::testSimpleMoving);
        reg.accept("buildcraftunofficial:item_transactor_limited_inventory", () -> buildcraft.lib.inventory.ItemTransactorTester::testLimitedInventory);
        reg.accept("buildcraftunofficial:item_handler_simple_component_round_trip", () -> buildcraft.lib.inventory.ItemTransactorTester::testComponentRoundTrip);
        reg.accept("buildcraftunofficial:item_handler_simple_legacy_id_count_fallback", () -> buildcraft.lib.inventory.ItemTransactorTester::testLegacyIdCountFallback);
        
        // Shape Patterns
        reg.accept("buildcraftunofficial:shape_pattern_tiny_template", () -> buildcraft.core.builders.patterns.ShapePatternsTester::testTinyTemplate);
        reg.accept("buildcraftunofficial:shape_pattern_sphere_equality", () -> buildcraft.core.builders.patterns.ShapePatternsTester::testSphereEquality);
        // List Matching
        reg.accept("buildcraftunofficial:list_tools_matching", () -> buildcraft.lib.list.ListTester::testTools);
        reg.accept("buildcraftunofficial:list_tags_matching", () -> buildcraft.lib.list.ListTester::testTags);
        reg.accept("buildcraftunofficial:list_armor_matching", () -> buildcraft.lib.list.ListTester::testArmor);
        reg.accept("buildcraftunofficial:list_fluid_matching", () -> buildcraft.lib.list.ListTester::testFluid);
        reg.accept("buildcraftunofficial:list_end_to_end_by_type", () -> buildcraft.lib.list.ListTester::testEndToEndByType);
        reg.accept("buildcraftunofficial:list_tags_single_segment_material_fallback", () -> buildcraft.lib.list.ListTester::testTagsSingleSegmentMaterialFallback);
        reg.accept("buildcraftunofficial:list_tags_ignore_tree_tutorial_tag", () -> buildcraft.lib.list.ListTester::testTagsIgnoreTreeTutorialTag);
        reg.accept("buildcraftunofficial:list_end_to_end_both_flags_union", () -> buildcraft.lib.list.ListTester::testEndToEndBothFlagsUnion);
        reg.accept("buildcraftunofficial:list_precise_enchantment_round_trip", () -> buildcraft.lib.list.ListTester::testPreciseEnchantmentRoundTrip);
        
        // Core Blocks
        reg.accept("buildcraftunofficial:core_spring_water", () -> buildcraft.core.block.SpringTester::testWaterSpring);
        reg.accept("buildcraftunofficial:core_spring_oil", () -> buildcraft.core.block.SpringTester::testOilSpring);
        reg.accept("buildcraftunofficial:core_spring_oil_attaches_tile", () -> buildcraft.core.block.SpringTester::testOilSpringAttachesTile);
        reg.accept("buildcraftunofficial:core_spring_oil_regenerates_through_water", () -> buildcraft.core.block.SpringTester::testOilSpringRegeneratesThroughWater);
        reg.accept("buildcraftunofficial:core_spring_oil_keeps_solid_above", () -> buildcraft.core.block.SpringTester::testOilSpringKeepsSolidBlockAbove);

        // Core Markers
        reg.accept("buildcraftunofficial:marker_orientation", () -> buildcraft.core.marker.MarkerTester::testMarkerOrientation);
        reg.accept("buildcraftunofficial:marker_volume_los", () -> buildcraft.core.marker.MarkerTester::testVolumeLineOfSight);
        reg.accept("buildcraftunofficial:marker_path_los", () -> buildcraft.core.marker.MarkerTester::testPathLineOfSight);
        reg.accept("buildcraftunofficial:marker_volume_triangulation_2d", () -> buildcraft.core.marker.MarkerTester::testVolumeTriangulation2D);
        reg.accept("buildcraftunofficial:marker_volume_triangulation_3d", () -> buildcraft.core.marker.MarkerTester::testVolumeTriangulation3D);

        // Oil generation primitives (SurfacePool shape + defensive tree-clearing)
        reg.accept("buildcraftunofficial:oilgen_set_oil_skips_unloaded_chunk", () -> buildcraft.energy.generation.OilGenStructureTester::testSetOilSkipsUnloadedChunk);
        reg.accept("buildcraftunofficial:oilgen_set_oil_skips_block_entity", () -> buildcraft.energy.generation.OilGenStructureTester::testSetOilSkipsBlockEntity);
        reg.accept("buildcraftunofficial:oilgen_surface_pool_clean_shape", () -> buildcraft.energy.generation.OilGenStructureTester::testSurfacePoolCleanShape);
        reg.accept("buildcraftunofficial:oilgen_surface_pool_clears_tall_tree_fully", () -> buildcraft.energy.generation.OilGenStructureTester::testSurfacePoolClearsTallTreeFully);

        // Energy Engines
        reg.accept("buildcraftunofficial:engine_redstone_dry_run", () -> buildcraft.energy.EngineTester::testRedstoneEngineDryRunHeat);
        reg.accept("buildcraftunofficial:engine_redstone_safe_limit", () -> buildcraft.energy.EngineTester::testRedstoneEngineSafeLimit);
        reg.accept("buildcraftunofficial:engine_stirling_fuel", () -> buildcraft.energy.EngineTester::testStirlingEngineFuel);
        reg.accept("buildcraftunofficial:engine_stirling_releases_stored_power", () -> buildcraft.energy.EngineTester::testStirlingEngineReleasesStoredPowerWithoutFuel);
        reg.accept("buildcraftunofficial:engine_stirling_explosion", () -> buildcraft.energy.EngineTester::testStirlingEngineExplosion);
        reg.accept("buildcraftunofficial:engine_combustion_stable", () -> buildcraft.energy.EngineTester::testCombustionEngineStable);
        reg.accept("buildcraftunofficial:engine_overheat_no_explode_default", () -> buildcraft.energy.EngineTester::testStirlingEngineOverheatNoExplodeDefault);
        reg.accept("buildcraftunofficial:engine_overheat_explodes_when_configured", () -> buildcraft.energy.EngineTester::testStirlingEngineExplodesWhenConfigured);
        reg.accept("buildcraftunofficial:engine_clear_overheat_api", () -> buildcraft.energy.EngineTester::testEngineClearOverheatApi);
        reg.accept("buildcraftunofficial:engine_has_alternate_receiver_isolated", () -> buildcraft.energy.EngineTester::testEngineHasAlternateReceiverIsolated);
        reg.accept("buildcraftunofficial:combustion_engine_coolant_accepts_ice", () -> buildcraft.energy.EngineTester::testCombustionEngineCoolantTankAcceptsIce);
        reg.accept("buildcraftunofficial:engine_combustion_overheat_coolant_recovery", () -> buildcraft.energy.EngineTester::testCombustionEngineOverheatCoolantRecovery);

        // Engine fuel registry — data source for the JEI combustion-fuel/coolant categories
        reg.accept("buildcraftunofficial:fuel_registry_populated_for_jei", () -> buildcraft.energy.FuelRegistryTester::testFuelRegistryPopulatedForJei);

        // JEI "+" recipe-transfer move logic (Assembly Table / Distiller / Heat Exchanger)
        reg.accept("buildcraftunofficial:jei_transfer_moves_items", () -> buildcraft.lib.compat.jei.JeiTransferTester::testTransferMovesItems);
        reg.accept("buildcraftunofficial:jei_transfer_moves_bucket", () -> buildcraft.lib.compat.jei.JeiTransferTester::testTransferMovesBucket);
        reg.accept("buildcraftunofficial:jei_transfer_skips_missing", () -> buildcraft.lib.compat.jei.JeiTransferTester::testTransferSkipsMissing);

        // Energy Converter (Dynamo MJ + FE Engine)
        reg.accept("buildcraftunofficial:dynamo_upgrade_slot_filtering", () -> buildcraft.energy.EnergyConverterTester::testDynamoUpgradeSlotFiltering);
        reg.accept("buildcraftunofficial:engine_fe_upgrade_slot_filtering", () -> buildcraft.energy.EnergyConverterTester::testEngineFeUpgradeSlotFiltering);
        reg.accept("buildcraftunofficial:dynamo_upgrade_effectiveness", () -> buildcraft.energy.EnergyConverterTester::testDynamoUpgradeEffectiveness);
        reg.accept("buildcraftunofficial:engine_fe_upgrade_effectiveness", () -> buildcraft.energy.EnergyConverterTester::testEngineFeUpgradeEffectiveness);
        reg.accept("buildcraftunofficial:dynamo_gui_insertion", () -> buildcraft.energy.EnergyConverterTester::testDynamoUpgradeGUIInsertion);
        reg.accept("buildcraftunofficial:engine_fe_gui_insertion", () -> buildcraft.energy.EnergyConverterTester::testEngineFeUpgradeGUIInsertion);
        
        // GUI Headless Tests
        reg.accept("buildcraftunofficial:gui_test_dynamo_upgrade", () -> buildcraft.lib.test.gui.GuiTester::testDynamoUpgrade);
        reg.accept("buildcraftunofficial:gui_test_filler_ui", () -> buildcraft.lib.test.gui.GuiTester::testFillerUI);
        // Guide book recipe lookups off the server-synced cache (multiplayer recipe-panel fix)
        reg.accept("buildcraftunofficial:guide_recipe_lookup_synced_cache", () -> buildcraft.lib.test.guide.GuideRecipeLookupTester::testGuideRecipeLookupFromSyncedCache);
        // Slot Tests
        reg.accept("buildcraftunofficial:slot_max_stack_size", () -> buildcraft.lib.gui.SlotBaseTester::testSlotMaxStackSize);

        // Filler Inventory Filtering
        reg.accept("buildcraftunofficial:filler_block_item_filter", () -> buildcraft.builders.FillerInventoryTester::testFillerBlockItemFilter);

        // Filler Planner is a pattern-only screen — its container must expose no slots (no player inventory).
        reg.accept("buildcraftunofficial:filler_planner_has_no_slots", () -> buildcraft.builders.FillerPlannerContainerTester::testFillerPlannerHasNoSlots);

        // Quarry frames must not be washed away (and dropped) by flowing fluid (infinite-frame exploit).
        reg.accept("buildcraftunofficial:frame_not_washed_away_by_fluid", () -> buildcraft.builders.block.BlockFrameTester::testFrameNotWashedAwayByFluid);
        reg.accept("buildcraftunofficial:quarry_rig_fire_immune", () -> buildcraft.builders.entity.EntityQuarryRigTester::testRigIsFireImmune);

        // Template Builder — fillable-slot classification (grass tufts / snow / fluids).
        // Regression guard for the user-reported "Filler with Excavate doesn't clear grass
        // tufts" bug: TemplateBuilder.isBlockCorrect used to flag any non-air block as
        // CORRECT, so replaceable blocks (tall_grass, snow_layer, …) sat unfilled in the
        // box. Fluids stay excluded so the fluid-mode logic keeps owning that path.
        reg.accept("buildcraftunofficial:template_fillable_air", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testAirIsFillable);
        reg.accept("buildcraftunofficial:template_fillable_tall_grass", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testTallGrassIsFillable);
        reg.accept("buildcraftunofficial:template_fillable_short_grass", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testShortGrassIsFillable);
        reg.accept("buildcraftunofficial:template_fillable_snow_layer", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testSnowLayerIsFillable);
        reg.accept("buildcraftunofficial:template_fillable_water_source_excluded", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testWaterSourceIsNotFillable);
        reg.accept("buildcraftunofficial:template_fillable_lava_source_excluded", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testLavaSourceIsNotFillable);
        reg.accept("buildcraftunofficial:template_fillable_solid_not", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testSolidBlockIsNotFillable);
        reg.accept("buildcraftunofficial:template_fillable_waterlogged_fence_not", () -> buildcraft.builders.snapshot.TemplateBuilderFillableSlotTester::testWaterloggedFenceIsNotFillable);

        // Builder Drops (self + inventory contents)
        reg.accept("buildcraftunofficial:builder_drops_contents_and_self", () -> buildcraft.builders.BuilderDropsTester::testBuilderDropsContentsAndSelf);
        reg.accept("buildcraftunofficial:architect_drops_contents_and_self", () -> buildcraft.builders.BuilderDropsTester::testArchitectDropsContentsAndSelf);
        // Builder consumed-path NBT round-trip — gates both the client-side path-laser
        // render and the server-side chunk-reload persistence of the route.
        reg.accept("buildcraftunofficial:builder_path_survives_nbt_roundtrip", () -> buildcraft.builders.BuilderDropsTester::testBuilderPathSurvivesNbtRoundTrip);

        // Snapshot.countNonAirCells — drives start_of_something_big's 1024 threshold.
        reg.accept("buildcraftunofficial:snapshot_template_counts_set_bits", () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testTemplateCountsSetBits);
        reg.accept("buildcraftunofficial:snapshot_blueprint_excludes_air", () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testBlueprintExcludesAirEntries);
        reg.accept("buildcraftunofficial:snapshot_blueprint_fluid_source_counts", () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testBlueprintFluidSourceCountsAsNonAir);
        reg.accept("buildcraftunofficial:snapshot_blueprint_empty_null_safety", () -> buildcraft.builders.snapshot.SnapshotCountNonAirCellsTester::testBlueprintEmptyAndNullSafety);

        // Offline pipe-model reconstruction for the blueprint 3D preview (PipePreviewModel).
        // Pipes share one block, so the preview must rebuild each pipe's real model from its
        // captured NBT instead of new ItemStack(block) (which resolves every pipe to the Wooden
        // Diamond FE Pipe, standing up). The model KEY needs only the pipe registry, not GL.
        reg.accept("buildcraftunofficial:pipe_preview_all_definitions_reconstruct", () -> buildcraft.builders.client.render.pip.PipePreviewModelTester::testAllPipeDefinitionsReconstruct);
        reg.accept("buildcraftunofficial:pipe_preview_connection_bits_decode", () -> buildcraft.builders.client.render.pip.PipePreviewModelTester::testConnectionBitsDecodeIntoModelKey);
        reg.accept("buildcraftunofficial:pipe_preview_bad_nbt_yields_null", () -> buildcraft.builders.client.render.pip.PipePreviewModelTester::testBadNbtYieldsNull);

        // Compressed-NBT payload framing — encoder/decoder must agree on the exact byte
        // length, otherwise netty drops the custom_payload packet with "found N bytes extra".
        reg.accept("buildcraftunofficial:architect_preview_payload_roundtrip_small", () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripSmall);
        reg.accept("buildcraftunofficial:architect_preview_payload_roundtrip_medium", () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripMedium);
        reg.accept("buildcraftunofficial:architect_preview_payload_roundtrip_large", () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripLarge);
        reg.accept("buildcraftunofficial:architect_preview_payload_roundtrip_huge", () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripHuge);
        reg.accept("buildcraftunofficial:architect_preview_payload_roundtrip_boundary_sweep", () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testArchitectPreviewPayloadRoundTripBoundarySweep);
        reg.accept("buildcraftunofficial:snapshot_response_payload_roundtrip_medium", () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testSnapshotResponsePayloadRoundTripMedium);
        reg.accept("buildcraftunofficial:snapshot_response_payload_roundtrip_large", () -> buildcraft.builders.snapshot.CompressedNbtPayloadFramingTester::testSnapshotResponsePayloadRoundTripLarge);

        // Builder paving_the_way + start_of_something_big — predicates and NBT shape.
        reg.accept("buildcraftunofficial:builder_paving_rejects_no_path", () -> buildcraft.builders.tile.BuilderAdvancementsTester::testPavingTheWayRejectsSinglePositionBuilder);
        reg.accept("buildcraftunofficial:builder_paving_rejects_mid_path", () -> buildcraft.builders.tile.BuilderAdvancementsTester::testPavingTheWayRejectsMidPath);
        reg.accept("buildcraftunofficial:builder_paving_accepts_at_last_position", () -> buildcraft.builders.tile.BuilderAdvancementsTester::testPavingTheWayAcceptsAtLastPosition);
        reg.accept("buildcraftunofficial:builder_big_structure_threshold_matches_spec", () -> buildcraft.builders.tile.BuilderAdvancementsTester::testBigStructureThresholdMatchesSpec);
        reg.accept("buildcraftunofficial:builder_advancement_state_survives_nbt_roundtrip", () -> buildcraft.builders.tile.BuilderAdvancementsTester::testNbtRoundTripPreservesAdvancementState);
        reg.accept("buildcraftunofficial:builder_fresh_advancement_state_zero", () -> buildcraft.builders.tile.BuilderAdvancementsTester::testFreshBuilderAdvancementStateIsZero);

        // Quarry TaskBreakBlock drop routing in both phases (frame-clearing drillPos=null, mining drillPos!=null)
        reg.accept("buildcraftunofficial:quarry_frame_clearing_routes_drops", () -> buildcraft.builders.tile.TileQuarryDropsTester::testFrameClearingRoutesDropsToAdjacentChest);
        reg.accept("buildcraftunofficial:quarry_mining_routes_drops", () -> buildcraft.builders.tile.TileQuarryDropsTester::testMiningRoutesDropsToAdjacentChest);
        reg.accept("buildcraftunofficial:quarry_tick_reconciles_stale_mining_floor", () -> buildcraft.builders.tile.TileQuarryMiningDepthTester::testTickReconcilesStaleMiningFloor);

        // Owner-on-placement contract — load-bearing for both quarry advancements
        reg.accept("buildcraftunofficial:quarry_on_placed_by_records_owner", () -> buildcraft.builders.tile.TileQuarryOwnerTester::onPlacedByRecordsOwner);

        // Per-owner pairing predicate gating the destroying_the_world advancement
        reg.accept("buildcraftunofficial:destroying_the_world_same_player_grants", () -> buildcraft.builders.tile.DestroyingTheWorldTester::samePlayerTwoFullQuarriesGrants);
        reg.accept("buildcraftunofficial:destroying_the_world_different_owners_no_grant", () -> buildcraft.builders.tile.DestroyingTheWorldTester::differentOwnersDoNotGrant);
        reg.accept("buildcraftunofficial:destroying_the_world_undersized_no_grant", () -> buildcraft.builders.tile.DestroyingTheWorldTester::undersizedFrameDoesNotGrant);
        reg.accept("buildcraftunofficial:destroying_the_world_outside_window_no_grant", () -> buildcraft.builders.tile.DestroyingTheWorldTester::outsideWindowDoesNotGrant);
        reg.accept("buildcraftunofficial:destroying_the_world_never_stamped_no_grant", () -> buildcraft.builders.tile.DestroyingTheWorldTester::neverStampedDoesNotGrant);

        // Blueprint palette replacement (used by the Replacer block).
        // Logic also verified end-to-end in-client via the Replacer GUI.
        reg.accept("buildcraftunofficial:blueprint_replace_scan_context", () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testScanContextDifferenceDoesNotBlockMatch);
        reg.accept("buildcraftunofficial:blueprint_replace_data_untouched", () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testReplaceLeavesDataArrayUntouched);
        reg.accept("buildcraftunofficial:blueprint_replace_multi_occurrences", () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testMultipleOccurrencesInPaletteAllReplaced);
        reg.accept("buildcraftunofficial:blueprint_replace_no_match", () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testNoMatchLeavesPaletteUnchanged);
        reg.accept("buildcraftunofficial:blueprint_count_matching_cells", () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testCountMatchingCellsCountsBlocks);
        reg.accept("buildcraftunofficial:blueprint_replace_null_noop", () -> buildcraft.builders.snapshot.BlueprintReplaceTester::testReplaceNullIsNoOp);

        // Builder fluid-handling mode (waterlog/destroy at place sites; enum sanity).
        reg.accept("buildcraftunofficial:fluidmode_replace_water_waterloggable", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceWaterSourceWithWaterloggableBlock);
        reg.accept("buildcraftunofficial:fluidmode_replace_water_solid", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceWaterSourceWithSolidBlock);
        reg.accept("buildcraftunofficial:fluidmode_replace_lava_waterloggable", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceLavaSourceWithWaterloggableBlock);
        reg.accept("buildcraftunofficial:fluidmode_clear_destroys_source_at_place", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testClearDestroysSourceInsteadOfWaterlogging);
        reg.accept("buildcraftunofficial:fluidmode_no_replace_no_waterlog", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testNoReplaceModeDoesNotWaterlog);
        reg.accept("buildcraftunofficial:fluidmode_flowing_not_destroyed", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFlowingFluidIsNotSpeciallyDestroyed);
        reg.accept("buildcraftunofficial:fluidmode_cycle_all_states", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFluidModeCyclesThroughAllStates);
        reg.accept("buildcraftunofficial:fluidmode_ordinal_clamps", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFluidModeFromOrdinalClampsOutOfRange);
        reg.accept("buildcraftunofficial:fluidmode_isbuilt_accepts_waterlogged_world", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltAcceptsWorldWaterloggedWhenSchematicDry);
        reg.accept("buildcraftunofficial:fluidmode_isbuilt_rejects_dry_world", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltRejectsWorldDryWhenSchematicWaterlogged);
        reg.accept("buildcraftunofficial:fluidmode_fragile_deferred_with_neighbour_source", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFragileBlockDeferredWhenNeighbourSourceExists);
        reg.accept("buildcraftunofficial:fluidmode_fragile_placed_when_isolated", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFragileBlockPlacedWhenSourceIsIsolated);
        reg.accept("buildcraftunofficial:fluidmode_solid_placed_despite_adjacent_fluid", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testSolidBlockPlacedDespiteAdjacentFluid);
        reg.accept("buildcraftunofficial:fluidmode_fragile_deferred_for_fluid_above", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testFragileBlockDeferredForFluidAbove);
        reg.accept("buildcraftunofficial:fluidmode_waterloggable_not_deferred", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testWaterloggableBlockNotDeferredNearWater);
        reg.accept("buildcraftunofficial:fluidmode_no_replace_skips_fragile_check", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testNoReplaceModeSkipsFragileCheck);
        reg.accept("buildcraftunofficial:blockutil_break_clears_water_source", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsClearsWaterSource);
        reg.accept("buildcraftunofficial:blockutil_break_clears_flowing_water", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsClearsFlowingWater);
        reg.accept("buildcraftunofficial:blockutil_break_solid_blocks", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsStillBreaksSolidBlocks);
        reg.accept("buildcraftunofficial:blockutil_break_honours_tool_for_loot", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsHonoursToolForLoot);
        reg.accept("buildcraftunofficial:blockutil_break_respects_tool_tier", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsRespectsToolTier);
        reg.accept("buildcraftunofficial:blockutil_break_with_xp_reports_xp", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsWithXpReportsXp);
        reg.accept("buildcraftunofficial:blockutil_break_captures_fluid_source", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsWithXpCapturesFluidSource);
        reg.accept("buildcraftunofficial:blockutil_break_skips_flowing_fluid_capture", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testBreakBlockAndGetDropsWithXpSkipsFlowingFluidCapture);
        reg.accept("buildcraftunofficial:blockutil_water_break_cost", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testWaterBreakCostIsOneMj);
        reg.accept("buildcraftunofficial:blockutil_lava_break_cost", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testLavaBreakCostIsOneMj);
        reg.accept("buildcraftunofficial:blockutil_stone_break_cost_unchanged", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testSolidBlockBreakCostUnchanged);
        reg.accept("buildcraftunofficial:fluidmode_clear_dries_waterlogged_block", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testClearModeDriesWaterloggedBlock);
        reg.accept("buildcraftunofficial:fluidmode_replace_still_opportunistically_waterlogs", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testReplaceModeStillOpportunisticallyWaterlogs);
        reg.accept("buildcraftunofficial:fluidmode_isbuilt_strict_in_clear", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltStrictInClearMode);
        reg.accept("buildcraftunofficial:fluidmode_isbuilt_lenient_in_replace", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsBuiltLenientInReplaceMode);
        reg.accept("buildcraftunofficial:fluidmode_clear_only_under_clear", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsWaterlogClearOnlyOnlyFiresUnderClear);
        reg.accept("buildcraftunofficial:fluidmode_clear_only_requires_matching_block", () -> buildcraft.builders.snapshot.FluidHandlingModeTester::testIsWaterlogClearOnlyRequiresMatchingBlock);

        // Builder block-survival gating (canSurvive guard in SchematicBlockDefault.build()).
        reg.accept("buildcraftunofficial:builder_support_torch_on_floor", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testTorchOnFloorPlaces);
        reg.accept("buildcraftunofficial:builder_support_wall_torch_with_support", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testWallTorchSurvivesWithSupport);
        reg.accept("buildcraftunofficial:builder_support_wall_torch_no_support", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testWallTorchRejectedWithoutSupport);
        reg.accept("buildcraftunofficial:builder_support_ladder_freestanding_rejected", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testLadderRejectedFreestanding);
        reg.accept("buildcraftunofficial:builder_support_fence_always_survives", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testFenceAlwaysSurvives);
        reg.accept("buildcraftunofficial:builder_support_sea_pickle_over_water", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testSeaPickleOverWaterSurvives);
        reg.accept("buildcraftunofficial:builder_leaves_placed_persistent", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testLeavesPlacedAsPersistent);
        reg.accept("buildcraftunofficial:builder_wall_isbuilt_ignores_connections", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testWallIsBuiltIgnoresConnections);
        reg.accept("buildcraftunofficial:builder_fence_isbuilt_ignores_connections", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testFenceIsBuiltIgnoresConnections);
        reg.accept("buildcraftunofficial:builder_deserialize_migrates_wall_ignored_properties", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testDeserializeMigratesIgnoredPropertiesFromCurrentRules);
        reg.accept("buildcraftunofficial:builder_deserialize_migrates_leaves_ignored_properties", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testDeserializeMigratesLeavesIgnoredProperties);
        reg.accept("buildcraftunofficial:builder_tall_grass_places_both_halves", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testTallGrassLowerPlacesBothHalves);
        reg.accept("buildcraftunofficial:builder_tall_grass_upper_linked_to_lower", () -> buildcraft.builders.snapshot.SupportRequiredPlacementTester::testTallGrassUpperHalfLinkedToLower);

        // Container-contents capture — INCLUDE-mode invariants: the items_list extractor
        // surfaces chest contents into computeRequiredItems(), AND the build() path restores
        // those items into the placed chest. Both sides of the player-pays-and-receives flow.
        reg.accept("buildcraftunofficial:container_contents_listed_as_required", () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testChestContentsListedAsRequiredItems);
        reg.accept("buildcraftunofficial:container_contents_restored_on_build", () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testChestBuiltWithContentsFromSchematic);
        // IGNORE-mode contract: computeRequiredItems(false) drops items_list contributions
        // and build(..., includeContents=false) places the chest with an empty inventory.
        reg.accept("buildcraftunofficial:container_contents_ignore_omits_required", () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testIgnoreModeOmitsContentsFromRequiredItems);
        reg.accept("buildcraftunofficial:container_contents_ignore_builds_empty", () -> buildcraft.builders.snapshot.ContainerContentsScanTester::testIgnoreModeBuildsEmptyChest);
        // Pipe required-item resolution: each captured pipe schematic must resolve its own pipe
        // item (type lives in BE NBT, not the shared holder block) — distinct types stay distinct.
        reg.accept("buildcraftunofficial:pipe_schematic_resolves_per_type_item", () -> buildcraft.builders.snapshot.SchematicBlockPipeTester::pipeSchematicResolvesPerTypeItem);
        // Pluggables (gates/facades/plugs/lenses) captured on a pipe must be costed too — build()
        // restores them from NBT, so the Builder must charge for them rather than place them free.
        reg.accept("buildcraftunofficial:pipe_schematic_costs_pluggables", () -> buildcraft.builders.snapshot.SchematicBlockPipeTester::pipeSchematicCostsPluggables);
        // Pluggables must rotate with the blueprint: a plug on NORTH moves to EAST under a 90° CW
        // rotation, otherwise facades stay on captured faces after a rotated build and block flow.
        reg.accept("buildcraftunofficial:pipe_schematic_rotates_pluggable_faces", () -> buildcraft.builders.snapshot.SchematicBlockPipeTester::pipeSchematicRotatesPluggableFaces);

        // Heat Exchanger fluid filtering (heatant on START, coolant on END; output drain-only).
        // NOTE: this and the other fluid testers gated >=1.21.10 below (DistillerTester,
        // FloodGateTester, TankManagerTester) are MODERN-ONLY — they're written entirely against the
        // NeoForge Transfer API (FluidResource / FluidStacksResourceHandler / Transaction.openRoot),
        // which is absent on 1.21.1, so their methods don't compile there. Running them on 1.21.1
        // needs a rewrite to the classic IFluidHandler / BCFluidTank facade. Deferred — it's test
        // coverage only: the production fluid tiles already use the facade and pass in-game on 1.21.1.
        //? if >=1.21.10 {
        reg.accept("buildcraftunofficial:heat_exchanger_output_rejects_external_insert", () -> buildcraft.factory.HeatExchangerTester::testOutputTankRejectsExternalInsert);
        reg.accept("buildcraftunofficial:heat_exchanger_output_accepts_internal_insert", () -> buildcraft.factory.HeatExchangerTester::testOutputTankAcceptsInternalInsert);
        reg.accept("buildcraftunofficial:heat_exchanger_internal_flag_resets", () -> buildcraft.factory.HeatExchangerTester::testOutputTankInternalFlagResetsAfterCall);
        reg.accept("buildcraftunofficial:heat_exchanger_atomic_craft_balanced", () -> buildcraft.factory.HeatExchangerTester::testAtomicCraftCommitsBalancedFillAndDrain);
        reg.accept("buildcraftunofficial:heat_exchanger_atomic_craft_rolls_back", () -> buildcraft.factory.HeatExchangerTester::testAtomicCraftRollsBackOnUndersizedFill);
        reg.accept("buildcraftunofficial:heat_exchanger_tank_clears_on_empty_load", () -> buildcraft.factory.HeatExchangerTester::testTankClearsWhenLoadedFromEmptySave);
        reg.accept("buildcraftunofficial:heat_exchanger_slot_caps_at_max_stack_size", () -> buildcraft.factory.HeatExchangerTester::testItemHandlerRespectsConfiguredMaxStackSize);
        //?}

        // Distiller wrench rotation (1.12.2 parity)
        //? if >=1.21.10 {
        reg.accept("buildcraftunofficial:distiller_wrench_rotates_clockwise", () -> buildcraft.factory.DistillerTester::testWrenchRotatesClockwise);
        reg.accept("buildcraftunofficial:distiller_wrench_passes_through_use_item_on", () -> buildcraft.factory.DistillerTester::testWrenchPassesThroughUseItemOn);
        //?}

        // Wrench rotation of vanilla blocks (1.12.2 parity — see VanillaRotationHandlers)
        reg.accept("buildcraftunofficial:vanilla_rotation_furnace", () -> buildcraft.lib.block.VanillaRotationTester::testFurnaceCyclesHorizontally);
        reg.accept("buildcraftunofficial:vanilla_rotation_dispenser", () -> buildcraft.lib.block.VanillaRotationTester::testDispenserCyclesAllSix);
        reg.accept("buildcraftunofficial:vanilla_rotation_hopper", () -> buildcraft.lib.block.VanillaRotationTester::testHopperCyclesFiveFaces);
        reg.accept("buildcraftunofficial:vanilla_rotation_piston_extended_refuses", () -> buildcraft.lib.block.VanillaRotationTester::testExtendedPistonRefuses);
        reg.accept("buildcraftunofficial:vanilla_rotation_banner_16", () -> buildcraft.lib.block.VanillaRotationTester::testStandingBannerSpins16);
        reg.accept("buildcraftunofficial:vanilla_rotation_skull_16", () -> buildcraft.lib.block.VanillaRotationTester::testFloorSkullSpins16);
        reg.accept("buildcraftunofficial:vanilla_rotation_trapdoor_half_flip", () -> buildcraft.lib.block.VanillaRotationTester::testTrapDoorHalfFlipOnWrap);
        reg.accept("buildcraftunofficial:vanilla_rotation_door_both_halves_and_hinge", () -> buildcraft.lib.block.VanillaRotationTester::testDoorRotatesBothHalvesAndFlipsHingeOnWrap);
        reg.accept("buildcraftunofficial:vanilla_rotation_wall_torch_attach_check", () -> buildcraft.lib.block.VanillaRotationTester::testWallTorchSkipsUnsupportedFace);
        reg.accept("buildcraftunofficial:vanilla_rotation_trapdoor_freestanding", () -> buildcraft.lib.block.VanillaRotationTester::testTrapDoorRotatesFreestanding);
        reg.accept("buildcraftunofficial:vanilla_rotation_button_twelve_orientations", () -> buildcraft.lib.block.VanillaRotationTester::testButtonCyclesThroughAllTwelveOrientations);
        reg.accept("buildcraftunofficial:vanilla_rotation_button_attach_to_air", () -> buildcraft.lib.block.VanillaRotationTester::testButtonSkipsAttachToAir);
        reg.accept("buildcraftunofficial:vanilla_rotation_double_chest", () -> buildcraft.lib.block.VanillaRotationTester::testDoubleChestRotatesBothHalves);
        reg.accept("buildcraftunofficial:vanilla_rotation_wrench_useon_furnace", () -> buildcraft.lib.block.VanillaRotationTester::testWrenchUseOnRotatesFurnace);
        reg.accept("buildcraftunofficial:vanilla_rotation_wrench_crouch_gate", () -> buildcraft.lib.block.VanillaRotationTester::testWrenchOnItemUseFirstCrouchGate);

        // Flood gate wrench toggle (advancement-granting useItemOn path)
        //? if >=1.21.10 {
        reg.accept("buildcraftunofficial:flood_gate_wrench_toggles_side", () -> buildcraft.factory.FloodGateTester::testWrenchTogglesSide);
        reg.accept("buildcraftunofficial:flood_gate_wrench_on_top_face_falls_through", () -> buildcraft.factory.FloodGateTester::testWrenchOnTopFaceFallsThrough);
        reg.accept("buildcraftunofficial:flood_gate_non_wrench_falls_through", () -> buildcraft.factory.FloodGateTester::testNonWrenchItemFallsThrough);
        reg.accept("buildcraftunofficial:flood_gate_flooding_the_world_advancement", () -> buildcraft.factory.FloodGateTester::testFloodingTheWorldAdvancement);
        //?}

        // Filler building_for_the_future advancement (LOOP-mode completion + setControlMode re-arm)
        reg.accept("buildcraftunofficial:filler_building_for_the_future_advancement", () -> buildcraft.builders.FillerAdvancementTester::testBuildingForTheFutureAdvancement);

        // Paper advancement contract (4 criteria, all required, names matching PaperAdvancement constants)
        reg.accept("buildcraftunofficial:paper_advancement_contract", () -> buildcraft.core.PaperAdvancementTester::testPaperAdvancementContract);

        // Pump infinite-source detection (vanilla regen-rule parity per anchor block)
        reg.accept("buildcraftunofficial:pump_infinite_strip_1x3_centre_vs_edges", () -> buildcraft.factory.PumpInfiniteDetectionTester::testStrip1x3CentreInfiniteEdgesFinite);
        reg.accept("buildcraftunofficial:pump_infinite_isolated_source_finite", () -> buildcraft.factory.PumpInfiniteDetectionTester::testIsolatedSourceFinite);
        reg.accept("buildcraftunofficial:pump_infinite_pond_2x2_all_corners", () -> buildcraft.factory.PumpInfiniteDetectionTester::testPond2x2AllCornersInfinite);
        reg.accept("buildcraftunofficial:pump_infinite_strip_1x5_interior_vs_edges", () -> buildcraft.factory.PumpInfiniteDetectionTester::testStrip1x5InteriorInfiniteEdgesFinite);
        reg.accept("buildcraftunofficial:pump_infinite_no_support_below", () -> buildcraft.factory.PumpInfiniteDetectionTester::testNoSupportBelowIsFinite);
        reg.accept("buildcraftunofficial:pump_infinite_diagonals_dont_count", () -> buildcraft.factory.PumpInfiniteDetectionTester::testDiagonalNeighboursDoNotCount);
        reg.accept("buildcraftunofficial:pump_infinite_water_below_supports", () -> buildcraft.factory.PumpInfiniteDetectionTester::testWaterBelowProvidesSupport);
        reg.accept("buildcraftunofficial:pump_infinite_null_safety", () -> buildcraft.factory.PumpInfiniteDetectionTester::testNullSafetyShortCircuits);

        // Pump spring-aware probe — drilling past water to a submerged oil spring
        reg.accept("buildcraftunofficial:pump_probe_oil_beneath_water", () -> buildcraft.factory.PumpSpringProbeTester::testOilBeneathWaterIsFound);
        reg.accept("buildcraftunofficial:pump_probe_dry_spring_under_water", () -> buildcraft.factory.PumpSpringProbeTester::testDrySpringUnderWaterReportsNoOil);
        reg.accept("buildcraftunofficial:pump_probe_plain_water_unaffected", () -> buildcraft.factory.PumpSpringProbeTester::testPlainWaterColumnUnaffected);
        reg.accept("buildcraftunofficial:pump_probe_solid_obstruction", () -> buildcraft.factory.PumpSpringProbeTester::testSolidObstructionStopsProbe);

        // Distiller tank gating (1.12.2 setFilter / setCanDrain / setCanFill parity)
        //? if >=1.21.10 {
        reg.accept("buildcraftunofficial:distiller_input_rejects_non_distillable", () -> buildcraft.factory.DistillerTester::testInputTankRejectsNonDistillableInsert);
        reg.accept("buildcraftunofficial:distiller_input_blocks_external_extract", () -> buildcraft.factory.DistillerTester::testInputTankBlocksExternalExtract);
        reg.accept("buildcraftunofficial:distiller_output_rejects_external_insert", () -> buildcraft.factory.DistillerTester::testOutputTanksRejectExternalInsertButAcceptInternal);
        reg.accept("buildcraftunofficial:distiller_heating_and_distilling_advancement", () -> buildcraft.factory.DistillerTester::testHeatingAndDistillingAdvancement);
        //?}

        // Advancements (fine_riches + sticky_dipping JSON shape, oil_fluids tag content, predicate sanity)
        reg.accept("buildcraftunofficial:advancement_fine_riches_loaded", () -> buildcraft.energy.AdvancementTester::testFineRichesAdvancementLoaded);
        reg.accept("buildcraftunofficial:advancement_sticky_dipping_loaded", () -> buildcraft.energy.AdvancementTester::testStickyDippingAdvancementLoaded);
        reg.accept("buildcraftunofficial:advancement_lava_power_loaded", () -> buildcraft.energy.AdvancementTester::testLavaPowerAdvancementLoaded);
        reg.accept("buildcraftunofficial:advancement_precision_crafting_loaded", () -> buildcraft.energy.AdvancementTester::testPrecisionCraftingAdvancementLoaded);
        reg.accept("buildcraftunofficial:advancement_oil_fluids_tag_contents", () -> buildcraft.energy.AdvancementTester::testOilFluidsTagContainsAllOilSourceBlocks);
        reg.accept("buildcraftunofficial:advancement_oil_fluids_tag_exclusivity", () -> buildcraft.energy.AdvancementTester::testOilFluidsTagExcludesNonOilFluids);
        reg.accept("buildcraftunofficial:advancement_fine_riches_predicate_negative", () -> buildcraft.energy.AdvancementTester::testWouldGenerateOilReturnsFalseInTestEnvironment);
        reg.accept("buildcraftunofficial:advancement_fine_riches_biome_tier_gate", () -> buildcraft.energy.AdvancementTester::testFineRichesBiomeTierGate);

        // Electronic Library
        reg.accept("buildcraftunofficial:library_slot_filtering", () -> buildcraft.builders.ElectronicLibraryTester::testSlotFiltering);
        reg.accept("buildcraftunofficial:library_download_cycle", () -> buildcraft.builders.ElectronicLibraryTester::testDownloadCycle);
        reg.accept("buildcraftunofficial:library_upload_progress", () -> buildcraft.builders.ElectronicLibraryTester::testUploadProgressIncrements);
        reg.accept("buildcraftunofficial:library_download_idle", () -> buildcraft.builders.ElectronicLibraryTester::testDownloadIdleWhenEmpty);

        // Block drops — every BuildCraft block should drop itself + its real inventory
        // when broken with the correct tool, drop only its inventory when broken by
        // hand, and never drop PHANTOM (template/filter) slot contents.
        reg.accept("buildcraftunofficial:drops_decorated_pickaxe", () -> BlockDropsTester::testDecoratedPickaxeDropsSelf);
        reg.accept("buildcraftunofficial:drops_decorated_hand", () -> BlockDropsTester::testDecoratedHandBreakDropsNothing);
        reg.accept("buildcraftunofficial:drops_chute_pickaxe", () -> BlockDropsTester::testChutePickaxeDropsContentsAndSelf);
        reg.accept("buildcraftunofficial:drops_chute_hand", () -> BlockDropsTester::testChuteHandBreakDropsContentsOnly);
        reg.accept("buildcraftunofficial:drops_autoworkbench_skips_phantom", () -> BlockDropsTester::testAutoWorkbenchSkipsPhantomSlots);
        reg.accept("buildcraftunofficial:drops_tank_pickaxe", () -> BlockDropsTester::testTankPickaxeDropsFluidShardAndSelf);
        reg.accept("buildcraftunofficial:drops_tank_hand", () -> BlockDropsTester::testTankHandBreakDropsFluidShardOnly);
        reg.accept("buildcraftunofficial:drops_stirling_hand", () -> BlockDropsTester::testStirlingEngineHandBreakDropsFuel);
        reg.accept("buildcraftunofficial:drops_stirling_pickaxe", () -> BlockDropsTester::testStirlingEnginePickaxeDropsFuelAndSelf);
        reg.accept("buildcraftunofficial:drops_filtered_buffer_skips_filter", () -> BlockDropsTester::testFilteredBufferSkipsFilterSlots);
        reg.accept("buildcraftunofficial:drops_marker_hand", () -> BlockDropsTester::testMarkerHandBreakDropsSelf);

        // Stripes pipe direction NBT sync — covered by JUnit unit tests rather than game tests
        // (the regression is in PipeBehaviour.readFromNbt's no-op default; PipeBehaviourStripesSyncTester
        // exercises writeToNbt/readFromNbt round-trip directly with a null IPipe).

        // Pipe-specific drops — pluggable / wire click-break, pipe + cargo full break,
        // hand-break with cargo retention but no pipe item.
        reg.accept("buildcraftunofficial:pipe_pluggable_break", () -> buildcraft.transport.PipeDropsTester::testPluggableBreakDropsItemAndKeepsPipe);
        reg.accept("buildcraftunofficial:pipe_wire_break", () -> buildcraft.transport.PipeDropsTester::testWireBreakDropsItemAndKeepsPipe);
        reg.accept("buildcraftunofficial:pipe_pickaxe_break_drops_everything", () -> buildcraft.transport.PipeDropsTester::testPipePickaxeBreakDropsEverything);
        reg.accept("buildcraftunofficial:pipe_hand_break_drops_everything", () -> buildcraft.transport.PipeDropsTester::testPipeHandBreakDropsEverything);
        reg.accept("buildcraftunofficial:pipe_fluid_break_drops_shards", () -> buildcraft.transport.PipeDropsTester::testFluidPipeBreakDropsFluidShards);

        // Machine ↔ pipe connectivity — item pipes must see machine inventories exposed as
        // Capabilities.Item.BLOCK (Auto Workbench, laser tables, Electronic Library).
        reg.accept("buildcraftunofficial:autoworkbench_wood_pipe_extracts", () -> buildcraft.factory.MachinePipeConnectivityTester::testWoodPipeExtractsFromAutoWorkbench);
        reg.accept("buildcraftunofficial:assemblytable_wood_pipe_skips_resources", () -> buildcraft.factory.MachinePipeConnectivityTester::testWoodPipeSkipsAssemblyTableResources);

        // Gate modifier recipes must match the input gate by data-component variant, not just the
        // PLUG_GATE Item — see GateRecipeVariantTester for the regression context.
        reg.accept("buildcraftunofficial:gate_modifier_recipe_accepts_correct_variant", () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeAcceptsGoldPlainGate);
        reg.accept("buildcraftunofficial:gate_modifier_recipe_rejects_wrong_material", () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeRejectsIronPlainGate);
        reg.accept("buildcraftunofficial:gate_modifier_recipe_rejects_already_modified", () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeRejectsAlreadyModifiedGate);
        reg.accept("buildcraftunofficial:gate_modifier_recipe_rejects_wrong_logic", () -> buildcraft.silicon.GateRecipeVariantTester::testOrLogicRecipeRejectsAndLogicGate);
        //? if >=1.21.10 {
        // SlotDisplay recipe-display API is 1.21.5+; this test is inapplicable on 1.21.1 (not registered there).
        reg.accept("buildcraftunofficial:gate_modifier_recipe_display_preserves_variant", () -> buildcraft.silicon.GateRecipeVariantTester::testGoldLapisRecipeDisplayPreservesGoldVariant);
        //?}
        reg.accept("buildcraftunofficial:basic_gate_recipe_emitted_by_collector", () -> buildcraft.silicon.GateRecipeVariantTester::testBasicGateRecipeExistsAndCollectorEmitsIt);
        // JEI U/R lookup simulation — walks the collected entries with the same subtype key
        // (GateVariant.getVariantName) the JEI subtype interpreter uses. If U on a Basic Gate
        // or R on a higher-level gate shows nothing in-game, one of these two will fail.
        reg.accept("buildcraftunofficial:basic_gate_appears_as_input_in_modifier_recipes", () -> buildcraft.silicon.GateRecipeVariantTester::testBasicGateAppearsAsInputInModifierRecipes);
        reg.accept("buildcraftunofficial:higher_level_gate_appears_as_output_in_modifier_recipes", () -> buildcraft.silicon.GateRecipeVariantTester::testHigherLevelGateAppearsAsOutputInModifierRecipes);
        // Guide-book recipe rendering — pins that modifier-upgrade recipes render the gate
        // input with the correct variant (was rendering as default CLAY_BRICK "Basic Gate"
        // because `Ingredient.items()` drops data-component patches) and that CLAY_BRICK
        // Basic Gate has no assembly usages.
        reg.accept("buildcraftunofficial:guide_modifier_recipe_renders_correct_input_variant", () -> buildcraft.silicon.GateRecipeVariantTester::testGuideBookModifierRecipeRendersCorrectInputVariant);
        reg.accept("buildcraftunofficial:basic_clay_brick_gate_has_no_assembly_usages", () -> buildcraft.silicon.GateRecipeVariantTester::testBasicClayBrickGateHasNoAssemblyUsages);
        // Facade recipe redirects are server-authoritative: stackRedirects is published only when an
        // integrated server shares the client JVM, and empty on a dedicated server. Pin both halves —
        // empty map yields no facade for a redirect-only item, populated map yields the facade. See
        // FacadeRedirectTester and the todos.md "Facade redirects are client-only" entry.
        reg.accept("buildcraftunofficial:facade_empty_redirects_produce_no_facade", () -> buildcraft.silicon.FacadeRedirectTester::testEmptyRedirectsProduceNoFacadeForMappedItem);
        reg.accept("buildcraftunofficial:facade_populated_redirects_make_facade", () -> buildcraft.silicon.FacadeRedirectTester::testPopulatedRedirectsMakeFacadeForMappedItem);
        // Bare facade naming must survive FacadeStateManager.defaultState being null — the
        // multiplayer-join window before facade init, where the guide's item-index warm names a
        // bare facade and used to NPE the login handler (client disconnect). See FacadeNamingTester.
        reg.accept("buildcraftunofficial:facade_bare_name_survives_null_default_state", () -> buildcraft.silicon.FacadeNamingTester::testBareFacadeNameSurvivesNullDefaultState);
        // Pins the Logic Gates guide page's <recipe_cycle match="..."/> selectors: each assembly
        // match picks one variant's AND+OR pair, and each swap direction has its 12 crafting recipes.
        reg.accept("buildcraftunofficial:guide_gate_recipe_cycle_selectors", () -> buildcraft.silicon.GateRecipeVariantTester::testGuideGateRecipeCycleSelectors);

        // Crafting-table gate recipes restored from 1.12.2 — Basic Gate (clay brick), Iron
        // and Nether Brick basic-gate alternatives, Iron modifier upgrades (lapis/quartz),
        // and the AND<->OR shapeless swap for every non-clay-brick (material, modifier).
        reg.accept("buildcraftunofficial:basic_gate_crafting_recipe", () -> buildcraft.silicon.GateCraftingRecipeTester::testBasicGateRecipe);
        reg.accept("buildcraftunofficial:iron_and_basic_crafting_recipe", () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndBasicCraftRecipe);
        reg.accept("buildcraftunofficial:nether_brick_and_basic_crafting_recipe", () -> buildcraft.silicon.GateCraftingRecipeTester::testNetherBrickAndBasicCraftRecipe);
        reg.accept("buildcraftunofficial:iron_and_lapis_crafting_recipe", () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndLapisCraftRecipe);
        reg.accept("buildcraftunofficial:iron_and_quartz_crafting_recipe", () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndQuartzCraftRecipe);
        reg.accept("buildcraftunofficial:gate_swap_iron_and_to_or", () -> buildcraft.silicon.GateCraftingRecipeTester::testIronAndToOrSwap);
        reg.accept("buildcraftunofficial:gate_swap_iron_or_to_and", () -> buildcraft.silicon.GateCraftingRecipeTester::testIronOrToAndSwap);
        reg.accept("buildcraftunofficial:gate_swap_gold_diamond_and_to_or", () -> buildcraft.silicon.GateCraftingRecipeTester::testGoldDiamondAndToOrSwap);
        reg.accept("buildcraftunofficial:gate_swap_clay_brick_excluded", () -> buildcraft.silicon.GateCraftingRecipeTester::testClayBrickSwapNotAvailable);

        // Factory recipe parity — restored Gelled Water -> Water Bucket (1.12.2's water_gel_to_bucket;
        // the only consumer of Gelled Water, silently lost during the modern port then re-added).
        reg.accept("buildcraftunofficial:water_gel_to_bucket_recipe", () -> buildcraft.factory.WaterGelRecipeTester::water_gel_to_bucket_recipe);

        // Tank / IronTanks compatibility (issue #20) — IronTanks keeps the 8-glass crafting grid;
        // BuildCraft's tank stays reachable via the #buildcraftunofficial:tanks tag in the four
        // machines (Pump checked here), its 8-glass crafting recipe when IronTanks is absent, and an
        // always-available 6-cheap-glass Assembly Table recipe.
        reg.accept("buildcraftunofficial:tank_compat_pump_accepts_buildcraft_tank", () -> buildcraft.factory.TankRecipeCompatTester::testPumpAcceptsBuildcraftTank);
        reg.accept("buildcraftunofficial:tank_compat_craftable_without_irontanks", () -> buildcraft.factory.TankRecipeCompatTester::testTankCraftableWithoutIronTanks);
        reg.accept("buildcraftunofficial:tank_compat_assembly_six_glass", () -> buildcraft.factory.TankRecipeCompatTester::testAssemblyTankRecipe);

        // Conflicting-recipe cycle-output for the Auto Workbench / Advanced Crafting Table (issue #20
        // follow-up). Two test-only recipes (test_cycle_a -> diamond, test_cycle_b -> emerald) both
        // consume a single bedrock, so a one-bedrock grid matches both.
        reg.accept("buildcraftunofficial:cycle_output_find_matching_recipes", () -> buildcraft.lib.CraftingOutputCycleTester::testFindMatchingRecipesConflicts);
        reg.accept("buildcraftunofficial:cycle_output_advanced_crafting_table", () -> buildcraft.lib.CraftingOutputCycleTester::testAdvancedCraftingTableCyclesOutput);

        reg.accept("buildcraftunofficial:autoworkbench_cobblestone_pipe_connects", () -> buildcraft.factory.MachinePipeConnectivityTester::testCobblestonePipeConnectsToAutoWorkbench);
        reg.accept("buildcraftunofficial:autoworkbench_clay_pipe_inserts", () -> buildcraft.factory.MachinePipeConnectivityTester::testClayPipeInsertsIntoAutoWorkbench);
        reg.accept("buildcraftunofficial:architect_wood_pipe_extracts", () -> buildcraft.factory.MachinePipeConnectivityTester::testWoodPipeExtractsFinishedBlueprintFromArchitect);
        reg.accept("buildcraftunofficial:architect_clay_pipe_inserts", () -> buildcraft.factory.MachinePipeConnectivityTester::testClayPipeInsertsBlankBlueprintIntoArchitect);
        reg.accept("buildcraftunofficial:item_machines_expose_item_capability", () -> buildcraft.factory.MachinePipeConnectivityTester::testItemMachinesExposeItemHandlerCapability);
        reg.accept("buildcraftunofficial:mj_battery_machines_expose_fe_autoconvert", () -> buildcraft.factory.MachinePipeConnectivityTester::testMjBatteryMachinesExposeFeWhenAutoconvertEnabled);
        reg.accept("buildcraftunofficial:mj_battery_machines_hide_fe_mj_only", () -> buildcraft.factory.MachinePipeConnectivityTester::testMjBatteryMachinesHideFeUnderMjOnly);

        // Tank bookkeeping — composite ResourceHandler<FluidResource> capacity-respect
        // and cross-slot spillover (modelled on TileBuilder.tankManager's per-slot delegate).
        //? if >=1.21.10 {
        reg.accept("buildcraftunofficial:tank_single_capacity", () -> buildcraft.lib.fluid.TankManagerTester::testSingleTankCapacityRespect);
        reg.accept("buildcraftunofficial:tank_single_extract_returns_only_held", () -> buildcraft.lib.fluid.TankManagerTester::testSingleTankExtractReturnsOnlyWhatExists);
        reg.accept("buildcraftunofficial:tank_composite_insert_spillover", () -> buildcraft.lib.fluid.TankManagerTester::testCompositeInsertSpillsAcrossSlots);
        reg.accept("buildcraftunofficial:tank_composite_extract_spillover", () -> buildcraft.lib.fluid.TankManagerTester::testCompositeExtractDrainsAcrossSlots);
        reg.accept("buildcraftunofficial:tank_composite_insert_rolls_back", () -> buildcraft.lib.fluid.TankManagerTester::testCompositeInsertRollsBackOnAbort);
        //?}

        // Wire system signaling — pins out the gatesChanged reset behavior so the
        // "recompute every tick" bodge can't slip back in.
        reg.accept("buildcraftunofficial:wire_steady_state_leaves_flag_false", () -> buildcraft.transport.WireSystemTester::testSteadyStateLeavesGatesChangedFalse);
        reg.accept("buildcraftunofficial:wire_gate_emit_propagates_and_resets", () -> buildcraft.transport.WireSystemTester::testGateEmitPropagatesAndFlagResets);
        reg.accept("buildcraftunofficial:wire_resolve_actions_clearing_marks_dirty", () -> buildcraft.transport.WireSystemTester::testGateResolveActionsClearingMarksGatesChanged);

        // Silicon — Gates: redstone-trigger sync. Pins that runtime display state
        // (isOn/triggerOn/actionOn) is recomputed every tick and never resurrected by an
        // inbound NBT data sync — the client desync this guards against.
        reg.accept("buildcraftunofficial:gate_redstone_trigger_tracks_signal", () -> buildcraft.silicon.gate.GateRedstoneSyncTester::testTriggerTracksRedstoneSignal);
        reg.accept("buildcraftunofficial:gate_redstone_nbt_sync_no_clobber", () -> buildcraft.silicon.gate.GateRedstoneSyncTester::testNbtSyncDoesNotClobberLiveState);
        reg.accept("buildcraftunofficial:gate_redstone_client_update_carries_state", () -> buildcraft.silicon.gate.GateRedstoneSyncTester::testClientUpdateCarriesDisplayState);

        // Statement-parameter ItemStack serialization — pins the gate item-filter NBT
        // round-trip (writeToNbt <-> constructor) that the 26.1 port had stubbed out.
        reg.accept("buildcraftunofficial:statement_item_param_round_trip", () -> buildcraft.core.statements.StatementSerializationTester::testItemStackParamRoundTrip);
        reg.accept("buildcraftunofficial:statement_item_param_empty_round_trip", () -> buildcraft.core.statements.StatementSerializationTester::testEmptyItemStackParamRoundTrip);
        reg.accept("buildcraftunofficial:statement_item_exact_param_round_trip", () -> buildcraft.core.statements.StatementSerializationTester::testItemStackExactParamRoundTrip);

        // Blueprint placement (JEI "+" / recipe book) must resolve TAG-based ingredients —
        // a TagSlotDisplay needs SlotDisplayContext.REGISTRIES in the context or it resolves to
        // an empty stream, leaving phantom slots blank for the gears (and most recipes). See
        // CraftingUtilBlueprintTester.
        reg.accept("buildcraftunofficial:blueprint_place_resolves_tag_ingredients", () -> buildcraft.lib.misc.CraftingUtilBlueprintTester::testTagIngredientsResolveIntoBlueprint);

        // Backwards-compat registry aliases (LegacyAliases) — every legacy block/item/BE/fluid ID
        // must resolve to a live current entry, so old worlds and inventories keep their content.
        reg.accept("buildcraftunofficial:legacy_aliases_resolve", () -> buildcraft.lib.registry.LegacyAliasTester::testAliasesResolve);
    }
}
