/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.misc.LocaleUtil;

/**
 * Ledger that displays engine power stats: current output, stored power, and heat.
 * Uses supplier functions so data can come from synced ContainerData (client)
 * or directly from the tile entity (server/single-player).
 */
public class LedgerEngine extends Ledger_Neptune {
    // 1.12.2 overlay colour: 0xFF_D4_6C_1F (brownish-red)
    private static final int OVERLAY_COLOUR = 0xD46C1F;
    private static final int HEADER_COLOUR = 0xE1C92F;
    private static final int SUB_HEADER_COLOUR = 0xAAAFB8;
    private static final int TEXT_COLOUR = 0x000000;

    // Icon colours for power stages (used since BCLibSprites aren't ported)
    private static final int ICON_INACTIVE = 0x808080;   // grey
    private static final int ICON_ACTIVE   = 0x44AA44;   // green
    private static final int ICON_WARM     = 0xDD8800;   // orange
    private static final int ICON_OVERHEAT = 0xDD2200;   // red

    private final Supplier<EnumPowerStage> powerStageSupplier;
    private final Supplier<Boolean> engineOnSupplier;

    public LedgerEngine(BuildCraftGui gui, LongSupplier currentOutput, LongSupplier storedPower,
                        Supplier<Float> heatLevel, Supplier<EnumPowerStage> powerStage,
                        Supplier<Boolean> engineOn, boolean expandPositive) {
        super(gui, OVERLAY_COLOUR, expandPositive);
        this.title = "gui.power";
        this.powerStageSupplier = powerStage;
        this.engineOnSupplier = engineOn;

        appendText(LocaleUtil.localize("gui.currentOutput") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeMjFlow(currentOutput.getAsLong()), TEXT_COLOUR);
        appendText(LocaleUtil.localize("gui.stored") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeMj(storedPower.getAsLong()), TEXT_COLOUR);
        appendText(LocaleUtil.localize("gui.heat") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeHeat(heatLevel.get()), TEXT_COLOUR);

        calculateMaxSize();
    }

    @Override
    public int getTitleColour() {
        return HEADER_COLOUR;
    }

    @Override
    protected void drawIcon(double x, double y, GuiGraphics graphics) {
        // Draw a 16x16 coloured square as the power-stage icon
        int iconColour;
        EnumPowerStage stage = powerStageSupplier.get();
        switch (stage) {
            case OVERHEAT:
                iconColour = ICON_OVERHEAT;
                break;
            case RED:
            case YELLOW:
                iconColour = ICON_WARM;
                break;
            default:
                iconColour = engineOnSupplier.get() ? ICON_ACTIVE : ICON_INACTIVE;
        }
        graphics.fill((int) x, (int) y, (int) x + 16, (int) y + 16, 0xFF000000 | iconColour);

        // Draw a small lightning bolt shape to indicate it's a power icon
        int cx = (int) x + 6;
        int cy = (int) y + 3;
        // Simple 2-pixel wide bolt
        graphics.fill(cx, cy, cx + 4, cy + 4, 0xFFFFFF00);
        graphics.fill(cx - 1, cy + 4, cx + 3, cy + 5, 0xFFFFFF00);
        graphics.fill(cx + 1, cy + 5, cx + 5, cy + 9, 0xFFFFFF00);
    }
}
