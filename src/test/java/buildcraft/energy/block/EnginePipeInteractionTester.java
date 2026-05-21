/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.block;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeDefinition.PipeDefinitionBuilder;
import buildcraft.api.transport.pipe.PipeFlowType;

/** Unit tests for {@link EnginePipeInteraction}'s pipe classification — which pipe types each
 *  engine treats as "place against me" rather than "open my GUI". */
public class EnginePipeInteractionTester {

    // Distinct flow-type instances stand in for PipeApi.flowItems/flowFluids/flowPower/flowRf/flowStructure.
    // EnginePipeInteraction.accepts() compares flow types by reference, exactly as it does in-game.
    private static final PipeFlowType ITEM = new PipeFlowType(null, null);
    private static final PipeFlowType FLUID = new PipeFlowType(null, null);
    private static final PipeFlowType POWER = new PipeFlowType(null, null);
    private static final PipeFlowType RF = new PipeFlowType(null, null);
    private static final PipeFlowType STRUCTURE = new PipeFlowType(null, null);

    private static PipeDefinition def(String path, PipeFlowType flow) {
        PipeDefinitionBuilder builder = new PipeDefinitionBuilder();
        builder.identifier = "buildcraftunofficial:" + path;
        builder.flowType = flow;
        return new PipeDefinition(builder);
    }

    @Test
    public void testExtractionPipeDetection() {
        // Wooden pipes and the emerald (diamond-wood) variant are extraction pipes.
        for (String path : new String[] { "wood_item", "wood_fluid", "wood_power", "wood_rf",
                "diamond_wood_item", "diamond_wood_fluid", "diamond_wood_power", "diamond_wood_rf" }) {
            Assertions.assertTrue(EnginePipeInteraction.isExtractionPipe(def(path, POWER)),
                    path + " should be an extraction pipe");
        }
        // Everything else is not — including plain diamond pipes, which sort rather than extract.
        for (String path : new String[] { "gold_power", "diamond_power", "iron_power", "gold_rf",
                "diamond_rf", "cobblestone_fluid", "stripes_item", "structure" }) {
            Assertions.assertFalse(EnginePipeInteraction.isExtractionPipe(def(path, POWER)),
                    path + " should not be an extraction pipe");
        }
    }

    @Test
    public void testCombustionEngineAccepts() {
        // Combustion engine: all fluid pipes (fuel/coolant input) + wooden kinesis pipes (MJ output).
        assertAccepts("combustion", FLUID, POWER, "gold_fluid", FLUID);
        assertAccepts("combustion", FLUID, POWER, "wood_fluid", FLUID);
        assertAccepts("combustion", FLUID, POWER, "void_fluid", FLUID);
        assertAccepts("combustion", FLUID, POWER, "wood_power", POWER);
        assertAccepts("combustion", FLUID, POWER, "diamond_wood_power", POWER);
        // Rejected: non-wooden kinesis, and unrelated families.
        assertRejects("combustion", FLUID, POWER, "gold_power", POWER);
        assertRejects("combustion", FLUID, POWER, "diamond_power", POWER);
        assertRejects("combustion", FLUID, POWER, "gold_item", ITEM);
        assertRejects("combustion", FLUID, POWER, "gold_rf", RF);
        assertRejects("combustion", FLUID, POWER, "structure", STRUCTURE);
    }

    @Test
    public void testStirlingEngineAccepts() {
        // Stirling engine: all item pipes (solid-fuel input) + wooden kinesis pipes (MJ output).
        assertAccepts("stirling", ITEM, POWER, "gold_item", ITEM);
        assertAccepts("stirling", ITEM, POWER, "stripes_item", ITEM);
        assertAccepts("stirling", ITEM, POWER, "wood_power", POWER);
        assertAccepts("stirling", ITEM, POWER, "diamond_wood_power", POWER);
        assertRejects("stirling", ITEM, POWER, "gold_power", POWER);
        assertRejects("stirling", ITEM, POWER, "gold_fluid", FLUID);
        assertRejects("stirling", ITEM, POWER, "structure", STRUCTURE);
    }

    @Test
    public void testDynamoMjAccepts() {
        // MJ dynamo: all kinesis pipes (MJ input) + wooden FE pipes (FE output).
        assertAccepts("dynamo", POWER, RF, "gold_power", POWER);
        assertAccepts("dynamo", POWER, RF, "diamond_power", POWER);
        assertAccepts("dynamo", POWER, RF, "wood_power", POWER);
        assertAccepts("dynamo", POWER, RF, "wood_rf", RF);
        assertAccepts("dynamo", POWER, RF, "diamond_wood_rf", RF);
        assertRejects("dynamo", POWER, RF, "gold_rf", RF);
        assertRejects("dynamo", POWER, RF, "diamond_rf", RF);
        assertRejects("dynamo", POWER, RF, "gold_fluid", FLUID);
        assertRejects("dynamo", POWER, RF, "structure", STRUCTURE);
    }

    @Test
    public void testFeEngineAccepts() {
        // FE engine: all FE pipes (FE input) + wooden kinesis pipes (MJ output).
        assertAccepts("fe", RF, POWER, "gold_rf", RF);
        assertAccepts("fe", RF, POWER, "diamond_rf", RF);
        assertAccepts("fe", RF, POWER, "wood_rf", RF);
        assertAccepts("fe", RF, POWER, "wood_power", POWER);
        assertAccepts("fe", RF, POWER, "diamond_wood_power", POWER);
        assertRejects("fe", RF, POWER, "gold_power", POWER);
        assertRejects("fe", RF, POWER, "diamond_power", POWER);
        assertRejects("fe", RF, POWER, "gold_fluid", FLUID);
        assertRejects("fe", RF, POWER, "structure", STRUCTURE);
    }

    private static void assertAccepts(String engine, PipeFlowType fullFamily, PipeFlowType extractionFamily,
            String pipePath, PipeFlowType pipeFlow) {
        Assertions.assertTrue(EnginePipeInteraction.accepts(def(pipePath, pipeFlow), fullFamily, extractionFamily),
                engine + " engine should place " + pipePath);
    }

    private static void assertRejects(String engine, PipeFlowType fullFamily, PipeFlowType extractionFamily,
            String pipePath, PipeFlowType pipeFlow) {
        Assertions.assertFalse(EnginePipeInteraction.accepts(def(pipePath, pipeFlow), fullFamily, extractionFamily),
                engine + " engine should open its GUI for " + pipePath);
    }
}
