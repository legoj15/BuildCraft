/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import java.util.List;

//? if >=1.21.10 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?}

/**
 * Per-node facade over the vanilla GUI graphics object, isolating the 26.1 ⇄ pre-CalVer cliff to a
 * single class so the rest of BuildCraft's GUI code stays version-agnostic.
 *
 * <p>26.1 renamed {@code GuiGraphics} to {@code GuiGraphicsExtractor} and renamed four draw methods
 * ({@code drawString→text}, {@code renderItem→item}, {@code renderFakeItem→fakeItem},
 * {@code renderItemDecorations→itemDecorations}). BuildCraft calls the 26.1 names through this facade;
 * the {@code //? if} directives for that divergence live ONLY here. Future pre-1.21.6 nodes
 * (1.21.10, 1.21.1) extend the directives in this one class rather than across the GUI tree.
 *
 * <p>The wrapped vanilla object is exposed as {@link #raw} for the handful of methods whose signatures
 * reference node-divergent or rarely-used types ({@code pose}, {@code peekScissorStack},
 * {@code textRendererForWidget}, {@code submitPictureInPictureRenderState}); call those via {@code g.raw}.
 */
public final class BCGraphics {

    //? if >=26.1 {
    public final GuiGraphicsExtractor raw;

    public BCGraphics(GuiGraphicsExtractor raw) {
        this.raw = raw;
    }
    //?} else {
    /*public final GuiGraphics raw;

    public BCGraphics(GuiGraphics raw) {
        this.raw = raw;
    }*/
    //?}

    // ── text: 26.1 text(...) ⇄ 1.21.11 drawString(...) ──────────────────────────
    public void text(Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        raw.text(font, text, x, y, color);
        //?} else {
        /*raw.drawString(font, text, x, y, color);*/
        //?}
    }

    public void text(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        //? if >=26.1 {
        raw.text(font, text, x, y, color, dropShadow);
        //?} else {
        /*raw.drawString(font, text, x, y, color, dropShadow);*/
        //?}
    }

    public void text(Font font, String text, int x, int y, int color) {
        //? if >=26.1 {
        raw.text(font, text, x, y, color);
        //?} else {
        /*raw.drawString(font, text, x, y, color);*/
        //?}
    }

    public void text(Font font, String text, int x, int y, int color, boolean dropShadow) {
        //? if >=26.1 {
        raw.text(font, text, x, y, color, dropShadow);
        //?} else {
        /*raw.drawString(font, text, x, y, color, dropShadow);*/
        //?}
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        //? if >=26.1 {
        raw.text(font, text, x, y, color);
        //?} else {
        /*raw.drawString(font, text, x, y, color);*/
        //?}
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        //? if >=26.1 {
        raw.text(font, text, x, y, color, dropShadow);
        //?} else {
        /*raw.drawString(font, text, x, y, color, dropShadow);*/
        //?}
    }

    // ── item: 26.1 item(...) ⇄ 1.21.11 renderItem(...) ──────────────────────────
    public void item(ItemStack stack, int x, int y) {
        //? if >=26.1 {
        raw.item(stack, x, y);
        //?} else {
        /*raw.renderItem(stack, x, y);*/
        //?}
    }

    public void item(ItemStack stack, int x, int y, int seed) {
        //? if >=26.1 {
        raw.item(stack, x, y, seed);
        //?} else {
        /*raw.renderItem(stack, x, y, seed);*/
        //?}
    }

    // ── fakeItem: 26.1 fakeItem(...) ⇄ 1.21.11 renderFakeItem(...) ───────────────
    public void fakeItem(ItemStack stack, int x, int y) {
        //? if >=26.1 {
        raw.fakeItem(stack, x, y);
        //?} else {
        /*raw.renderFakeItem(stack, x, y);*/
        //?}
    }

    public void fakeItem(ItemStack stack, int x, int y, int seed) {
        //? if >=26.1 {
        raw.fakeItem(stack, x, y, seed);
        //?} else {
        /*raw.renderFakeItem(stack, x, y, seed);*/
        //?}
    }

    // ── itemDecorations: 26.1 itemDecorations(...) ⇄ 1.21.11 renderItemDecorations(...) ─
    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        //? if >=26.1 {
        raw.itemDecorations(font, stack, x, y);
        //?} else {
        /*raw.renderItemDecorations(font, stack, x, y);*/
        //?}
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y, String text) {
        //? if >=26.1 {
        raw.itemDecorations(font, stack, x, y, text);
        //?} else {
        /*raw.renderItemDecorations(font, stack, x, y, text);*/
        //?}
    }

    // ── identical-name delegations (same on both nodes) ─────────────────────────
    public void fill(int x1, int y1, int x2, int y2, int color) {
        raw.fill(x1, y1, x2, y2, color);
    }

    //? if >=1.21.10 {
    public void fill(RenderPipeline pipeline, int x1, int y1, int x2, int y2, int color) {
        raw.fill(pipeline, x1, y1, x2, y2, color);
    }
    //?}

    public void enableScissor(int x1, int y1, int x2, int y2) {
        raw.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        raw.disableScissor();
    }

    public void nextStratum() {
        //? if >=1.21.10 {
        raw.nextStratum();
        //?}
        // 1.21.1 GuiGraphics has no stratum system; immediate-mode draw order provides the
        // z-ordering nextStratum() guarantees on modern (a later draw paints on top of earlier ones).
    }

    //? if >=1.21.10 {
    public org.joml.Matrix3x2fStack pose() {
        return raw.pose();
    }
    //?} else {
    /*public com.mojang.blaze3d.vertex.PoseStack pose() {
        return raw.pose();
    }*/
    //?}

    //? if >=1.21.10 {
    public void blitSprite(RenderPipeline pipeline, TextureAtlasSprite sprite, int x, int y, int width, int height) {
        raw.blitSprite(pipeline, sprite, x, y, width, height);
    }

    public void blitSprite(RenderPipeline pipeline, Identifier texture, int x, int y, int width, int height) {
        raw.blitSprite(pipeline, texture, x, y, width, height);
    }

    public void blitSprite(RenderPipeline pipeline, TextureAtlasSprite sprite, int x, int y, int width, int height, int color) {
        raw.blitSprite(pipeline, sprite, x, y, width, height, color);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
            int width, int height, int textureWidth, int textureHeight) {
        raw.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureSize) {
        raw.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureSize);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        raw.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        raw.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
    }
    //?} else {
    /*// 1.21.1 GuiGraphics has no RenderPipeline parameter. blitSprite(ResourceLocation,…) exists; a
    // TextureAtlasSprite is drawn via blit(x, y, blitOffset, w, h, sprite[, r, g, b, a]). The region
    // blit overloads order args (x, y, width, height, u, v, regionW, regionH, texW, texH) — width/height
    // come BEFORE u/v on 1.21.1 (reverse of modern); the plain (no-region) overload keeps u/v first.
    public void blitSprite(TextureAtlasSprite sprite, int x, int y, int width, int height) {
        raw.blit(x, y, 0, width, height, sprite);
    }

    public void blitSprite(Identifier texture, int x, int y, int width, int height) {
        raw.blitSprite(texture, x, y, width, height);
    }

    public void blitSprite(TextureAtlasSprite sprite, int x, int y, int width, int height, int color) {
        raw.blit(x, y, 0, width, height, sprite,
            net.minecraft.util.FastColor.ARGB32.red(color) / 255.0F,
            net.minecraft.util.FastColor.ARGB32.green(color) / 255.0F,
            net.minecraft.util.FastColor.ARGB32.blue(color) / 255.0F,
            net.minecraft.util.FastColor.ARGB32.alpha(color) / 255.0F);
    }

    public void blit(Identifier texture, int x, int y, float u, float v,
            int width, int height, int textureWidth, int textureHeight) {
        raw.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blit(Identifier texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureSize) {
        raw.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureSize, textureSize);
    }

    public void blit(Identifier texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        raw.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public void blit(Identifier texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        raw.setColor(
            net.minecraft.util.FastColor.ARGB32.red(color) / 255.0F,
            net.minecraft.util.FastColor.ARGB32.green(color) / 255.0F,
            net.minecraft.util.FastColor.ARGB32.blue(color) / 255.0F,
            net.minecraft.util.FastColor.ARGB32.alpha(color) / 255.0F);
        raw.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        raw.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }*/
    //?}

    // On 1.21.1 there is no deferred "for next frame" tooltip; renderTooltip(...) draws immediately.
    // BuildCraft calls these late in the render pass, so immediate draw lands on top as intended.
    public void setTooltipForNextFrame(Component text, int x, int y) {
        //? if >=1.21.10 {
        raw.setTooltipForNextFrame(text, x, y);
        //?} else {
        /*raw.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, text, x, y);*/
        //?}
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        //? if >=1.21.10 {
        raw.setTooltipForNextFrame(font, text, x, y);
        //?} else {
        /*raw.renderTooltip(font, text, x, y);*/
        //?}
    }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        //? if >=1.21.10 {
        raw.setTooltipForNextFrame(font, stack, x, y);
        //?} else {
        /*raw.renderTooltip(font, stack, x, y);*/
        //?}
    }

    public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
        //? if >=1.21.10 {
        raw.setTooltipForNextFrame(lines, x, y);
        //?} else {
        /*raw.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, lines, x, y);*/
        //?}
    }

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
        //? if >=1.21.10 {
        raw.setTooltipForNextFrame(font, lines, x, y);
        //?} else {
        /*raw.renderTooltip(font, lines, x, y);*/
        //?}
    }

    public void setTooltipForNextFrame(Font font, List<net.minecraft.network.chat.Component> textComponents,
            java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> tooltipComponent, int x, int y) {
        //? if >=1.21.10 {
        raw.setTooltipForNextFrame(font, textComponents, tooltipComponent, x, y);
        //?} else {
        /*raw.renderTooltip(font, textComponents, tooltipComponent, x, y);*/
        //?}
    }
}
