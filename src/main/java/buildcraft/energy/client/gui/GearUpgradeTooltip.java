/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.mj.MjAPI;

import buildcraft.energy.tile.TileEngineGearBase;
import buildcraft.lib.misc.LocaleUtil;

/** The gear-upgrade list shown over the gear icons of the FE engine and the MJ dynamo: one line per upgrade item
 *  with the FE/RF rate it adds, in the unit and naming the player chose. */
final class GearUpgradeTooltip {
    private GearUpgradeTooltip() {}

    static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(LocaleUtil.localize("buildcraft.gui.rf_engine.upgrade_types"));
        TileEngineGearBase.initUpgrades();
        // The configured MJ-to-RF ratio, the same one the battery help text and the dynamo's output use.
        long mjPerRf = MjAPI.getRfConversion().mjPerRf;
        for (Map.Entry<Item, Long> entry : TileEngineGearBase.UPGRADE_VALUES.entrySet()) {
            String itemName = new ItemStack(entry.getKey()).getHoverName().getString();
            long rfPerTick = mjPerRf == 0 ? 0 : entry.getValue() / mjPerRf;
            lines.add(itemName + " = +" + LocaleUtil.localizeRfPerSecond(rfPerTick * 20));
        }
        return lines;
    }
}
