/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.statements.ActionStationRequestItems;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateLogic;
import buildcraft.silicon.gate.GateVariant;

/** The slot encoding behind {@link DockingStationPipe#getRequest}: {@code side} in bits 4-6, the gate's
 *  action-slot index in bits 2-3 (so slots 0-3), the action's parameter index in bits 0-1 (so parameters 0-3).
 *
 *  <p>The worry was that an 8-slot gate's action slots 5-8 can never be reached by that encoding, so a Request
 *  Items action in them would silently produce nothing. It cannot happen: Request Items needs at least one action
 *  PARAMETER to say what it wants, only the Quartz and Diamond modifiers add action parameters, and both halve the
 *  gate's slot count. So every gate that can hold the action has at most four slots, and the four-slot encoding
 *  covers all of them. An 8-slot gate (Gold, no modifier or Lapis) takes no action parameters and refuses the
 *  action outright. This pins the three facts that argument rests on, so a new gate variant or a changed modifier
 *  that breaks it fails here instead of silently dropping requests. */
public class RequestItemsSlotEncodingTest extends VanillaSetupBaseTester {

    /** The encoding's action field is two bits wide. */
    private static final int ENCODED_ACTION_SLOTS = 4;
    /** The encoding's parameter field is two bits wide. */
    private static final int ENCODED_PARAMETERS = 4;

    private static boolean canHold(GateVariant variant) {
        // The very check the gate GUI and the statement picker apply (GateLogic.isValidAction).
        return new GateLogic(null, variant).isValidAction(BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS);
    }

    /** Every gate a player can actually make: a modifier only goes on a material that accepts one (a clay brick
     *  gate has a single slot and cannot be modified). */
    private static List<GateVariant> craftableVariants() {
        List<GateVariant> variants = new ArrayList<>();
        for (EnumGateLogic logic : EnumGateLogic.values()) {
            for (EnumGateMaterial material : EnumGateMaterial.values()) {
                for (EnumGateModifier modifier : EnumGateModifier.values()) {
                    if (modifier == EnumGateModifier.NO_MODIFIER || material.canBeModified) {
                        variants.add(new GateVariant(logic, material, modifier));
                    }
                }
            }
        }
        return variants;
    }

    @Test
    public void everyGateThatCanHoldRequestItemsFitsTheFourSlotEncoding() {
        int holders = 0;
        for (GateVariant variant : craftableVariants()) {
            if (!canHold(variant)) {
                continue;
            }
            holders++;
            Assertions.assertTrue(variant.numSlots <= ENCODED_ACTION_SLOTS,
                    variant + " can hold Request Items but has " + variant.numSlots
                            + " slots - slots past " + ENCODED_ACTION_SLOTS
                            + " would never surface a request through DockingStationPipe.getRequest");
            Assertions.assertTrue(variant.numActionArgs <= ENCODED_PARAMETERS,
                    variant + " has more action parameters than the encoding addresses");
        }
        Assertions.assertTrue(holders > 0, "vacuous: no gate variant can hold Request Items at all");
    }

    @Test
    public void anEightSlotGateRefusesRequestItems() {
        int eightSlotVariants = 0;
        for (GateVariant variant : craftableVariants()) {
            if (variant.numSlots <= ENCODED_ACTION_SLOTS) {
                continue;
            }
            eightSlotVariants++;
            Assertions.assertEquals(0, variant.numActionArgs,
                    variant + " has " + variant.numSlots + " slots, so it must carry no action parameters");
            Assertions.assertFalse(canHold(variant),
                    variant + " must not be offered Request Items: the action has nothing to say "
                            + "without a parameter");
        }
        Assertions.assertTrue(eightSlotVariants > 0, "vacuous: there is no gate with more than four slots");
    }

    @Test
    public void requestItemsNeedsAParameterAndFitsTheEncodedParameterField() {
        ActionStationRequestItems action = BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS;
        Assertions.assertTrue(action.minParameters() >= 1,
                "the 8-slot argument rests on Request Items needing at least one action parameter");
        Assertions.assertTrue(action.maxParameters() <= ENCODED_PARAMETERS,
                "every parameter the action can carry must be addressable by the encoding");
    }
}
