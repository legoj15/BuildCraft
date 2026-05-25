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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.misc.LocaleUtil;

/**
 * Ledger that displays engine power stats: current output, stored power, and heat.
 * Uses supplier functions so data can come from synced ContainerData (client)
 * or directly from the tile entity (server/single-player).
 */
@SuppressWarnings("this-escape")
public class LedgerEngine extends Ledger_Neptune {
    // 1.12.2 overlay colour: 0xFF_D4_6C_1F (brownish-red)
    private static final int OVERLAY_COLOUR = 0xD46C1F;
    private static final int HEADER_COLOUR = 0xE1C92F;
    private static final int SUB_HEADER_COLOUR = 0xAAAFB8;
    private static final int TEXT_COLOUR = 0x000000;

    // Engine status icon sprites — stitched into the blocks atlas via
    // assets/minecraft/atlases/blocks.json's "icons/" source. Routing
    // through TextureAtlasSprite means Minecraft's atlas animation system honours
    // the engine_{active,warm}.png.mcmeta sidecars, so resource packs can override
    // frametime / frame order / interpolation without touching code.
    private static final SpriteHolder ICON_ACTIVE = SpriteHolderRegistry.getHolder("buildcraftunofficial:icons/engine_active");
    private static final SpriteHolder ICON_INACTIVE = SpriteHolderRegistry.getHolder("buildcraftunofficial:icons/engine_inactive");
    private static final SpriteHolder ICON_WARM = SpriteHolderRegistry.getHolder("buildcraftunofficial:icons/engine_warm");
    private static final SpriteHolder ICON_OVERHEAT = SpriteHolderRegistry.getHolder("buildcraftunofficial:icons/engine_overheat");

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
        // Select the appropriate engine status icon based on power stage.
        // Matches 1.12.2 LedgerEngine.drawIcon() logic.
        SpriteHolder holder;
        EnumPowerStage stage = powerStageSupplier.get();
        switch (stage) {
            case OVERHEAT:
                holder = ICON_OVERHEAT;
                break;
            case RED:
            case YELLOW:
                holder = ICON_WARM;
                break;
            default:
                holder = engineOnSupplier.get() ? ICON_ACTIVE : ICON_INACTIVE;
        }
        TextureAtlasSprite sprite = holder.getSprite();
        if (sprite != null) {
            // blitSprite reads the sprite's CURRENT-frame UVs each call, so
            // animated entries (active, warm) advance automatically through the
            // atlas's animation ticker — no manual frame computation required.
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, (int) x, (int) y, 16, 16);
        }
    }
}
