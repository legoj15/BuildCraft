/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

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

    // Engine status icon textures (from 1.12.2 BCLibSprites)
    private static final Identifier ICON_ACTIVE = Identifier.parse("buildcraftunofficial:textures/icons/engine_active.png");
    private static final Identifier ICON_INACTIVE = Identifier.parse("buildcraftunofficial:textures/icons/engine_inactive.png");
    private static final Identifier ICON_WARM = Identifier.parse("buildcraftunofficial:textures/icons/engine_warm.png");
    private static final Identifier ICON_OVERHEAT = Identifier.parse("buildcraftunofficial:textures/icons/engine_overheat.png");

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
    protected void drawIcon(double x, double y, GuiGraphicsExtractor graphics) {
        // Select the appropriate engine status icon based on power stage
        // Matches 1.12.2 LedgerEngine.drawIcon() logic
        Identifier icon;
        boolean animated = false;
        EnumPowerStage stage = powerStageSupplier.get();
        switch (stage) {
            case OVERHEAT:
                icon = ICON_OVERHEAT;
                break;
            case RED:
            case YELLOW:
                icon = ICON_WARM;
                animated = true;
                break;
            default:
                if (engineOnSupplier.get()) {
                    icon = ICON_ACTIVE;
                    animated = true;
                } else {
                    icon = ICON_INACTIVE;
                }
        }

        if (animated) {
            // Sprite sheets are 16x96 (6 frames, 16x16 each, stacked vertically).
            // frametime=1 in the 1.12.2 .mcmeta → advance one frame per game tick.
            int totalFrames = 6;
            int ticksPerFrame = 1;
            long ticks = System.currentTimeMillis() / 50; // ~20 ticks/sec like MC
            int frame = (int) ((ticks / ticksPerFrame) % totalFrames);
            float vOffset = frame * 16f;
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                (int) x, (int) y, 0f, vOffset, 16, 16, 16, 96);
        } else {
            // Static icons (inactive, overheat) are 16x16
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                (int) x, (int) y, 0f, 0f, 16, 16, 16, 16);
        }
    }
}
