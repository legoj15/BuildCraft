/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >=1.21.10 {
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}
import net.minecraft.network.chat.Component;

import buildcraft.lib.gui.BCGraphics;

/**
 * THE BuildCraft button: vanilla's own button face ({@code widget/button} sprites, so resource packs restyle it with
 * every other button in the game) with an optional BuildCraft icon and/or label on top. Screens configure one through
 * {@link #builder} — they never subclass it or paint buttons out of their background texture
 * ({@code ButtonUnificationGuardTester} enforces that). Plain text buttons keep using vanilla's {@code Button}.
 * <p>
 * What the builder covers, all live (read every frame, so no screen pushes state into its buttons):
 * <ul>
 * <li><b>icon</b> — a {@link ButtonIcon}: a GUI-atlas {@link ButtonSprite}, an item, or a state-dependent choice;</li>
 * <li><b>label</b> — vanilla's centred button text;</li>
 * <li><b>latched</b> — a switched-on toggle / the chosen radio option, drawn "pressed in" (see {@link ButtonFace});</li>
 * <li><b>activeWhen</b> — whether it can be pressed at all (greyed out otherwise);</li>
 * <li><b>tooltip</b> — re-created only when its text changes; an icon-only button also narrates it;</li>
 * <li><b>mouse buttons</b> — left only by default; the action receives which button pressed it.</li>
 * </ul>
 * This class owns every per-line divergence of vanilla's button API — the content-render hook (26.1
 * {@code extractContents}, 1.21.11 {@code renderContents}, 1.21.10/1.21.1 {@code renderWidget}), the press hook
 * ({@code onPress(InputWithModifiers)} vs {@code onPress()}), and the mouse-button filter — so button code
 * elsewhere carries no Stonecutter directives.
 */
public class BCButton extends AbstractButton {

    /** What a press does. {@code mouseButton} is GLFW's (0 left, 1 right, 2 middle); a keyboard press reports 0. */
    @FunctionalInterface
    public interface Action {
        void press(int mouseButton);
    }

    private final Action action;
    private final int mouseButtons;
    @Nullable
    private final ButtonIcon icon;
    private final boolean showLabel;
    @Nullable
    private final BooleanSupplier latched;
    @Nullable
    private final BooleanSupplier activeWhen;
    @Nullable
    private final Supplier<Component> tooltip;
    @Nullable
    private Component shownTooltip;

    protected BCButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height,
            builder.label != null ? builder.label : Component.empty());
        this.action = builder.action;
        this.mouseButtons = builder.mouseButtons;
        this.icon = builder.icon;
        this.showLabel = builder.label != null;
        this.latched = builder.latched;
        this.activeWhen = builder.activeWhen;
        this.tooltip = builder.tooltip;
        refreshLiveState();
    }

    public static Builder builder(int x, int y, int width, int height) {
        return new Builder(x, y, width, height);
    }

    /** Whether this button currently shows as a switched-on toggle / the chosen option. */
    public boolean isLatched() {
        return latched != null && latched.getAsBoolean();
    }

    public ButtonFace face() {
        return ButtonFace.of(this.active, this.isHoveredOrFocused(), isLatched());
    }

    // ── render: the vanilla content hook diverges per line; all of them land in drawButtonContent ──
    //? if >=26.1 {
    @Override
    protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawButtonContent(new BCGraphics(graphics), mouseX, mouseY, partialTick);
    }
    //?} elif >=1.21.11 {
    /*@Override
    protected void renderContents(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawButtonContent(new BCGraphics(graphics), mouseX, mouseY, partialTick);
    }*/
    //?} elif >=1.21.10 {
    /*@Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawButtonContent(new BCGraphics(graphics), mouseX, mouseY, partialTick);
        // 1.21.10's AbstractButton.renderWidget (which this replaces) also asked for the hover cursor; 1.21.11+
        // does that outside the content hook.
        if (this.isHovered()) {
            graphics.requestCursor(this.isActive()
                ? com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND
                : com.mojang.blaze3d.platform.cursor.CursorTypes.NOT_ALLOWED);
        }
    }*/
    //?} else {
    /*@Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawButtonContent(new BCGraphics(graphics), mouseX, mouseY, partialTick);
    }*/
    //?}

    /** Face, then icon, then label. Override only for a genuinely new kind of button (keep it in this package). */
    protected void drawButtonContent(BCGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshLiveState();
        drawFace(graphics);
        if (icon != null) {
            icon.draw(graphics, getX(), getY(), getWidth(), getHeight(), ButtonFace.iconTint(this.active, this.alpha));
        }
        if (showLabel) {
            drawDefaultButtonLabel(graphics);
        }
    }

    /** Vanilla's 9-sliced button sprite for the current {@link #face()}. */
    protected void drawFace(BCGraphics graphics) {
        ButtonFace face = face();
        graphics.guiSprite(SPRITES.get(face.enabledSprite(), face.focusedSprite()),
            getX(), getY(), getWidth(), getHeight(), ButtonFace.white(this.alpha));
    }

    /** Vanilla's centred button label (greyed by vanilla when inactive; a latched button stays full-bright). */
    protected void drawDefaultButtonLabel(BCGraphics graphics) {
        //? if >=26.1 {
        extractDefaultLabel(graphics.raw.textRendererForWidget(this,
            net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects.NONE));
        //?} elif >=1.21.11 {
        /*renderDefaultLabel(graphics.raw.textRendererForWidget(this,
            net.minecraft.client.gui.GuiGraphics.HoveredTextEffects.NONE));*/
        //?} elif >=1.21.10 {
        /*renderString(graphics.raw, net.minecraft.client.Minecraft.getInstance().font,
            net.minecraft.util.ARGB.color(this.alpha, getFGColor()));*/
        //?} else {
        /*// 1.21.1: ARGB.color(alpha, rgb) is absent; pack ARGB as vanilla AbstractButton does (rgb | alpha<<24).
        renderString(graphics.raw, net.minecraft.client.Minecraft.getInstance().font,
            getFGColor() | (net.minecraft.util.Mth.ceil(this.alpha * 255.0F) << 24));*/
        //?}
    }

    /** Pulls the live suppliers into vanilla's widget state (once per frame, and at construction). */
    private void refreshLiveState() {
        if (activeWhen != null) {
            this.active = activeWhen.getAsBoolean();
        }
        if (tooltip == null) {
            return;
        }
        Component now = tooltip.get();
        if (Objects.equals(now, shownTooltip)) {
            return;
        }
        shownTooltip = now;
        setTooltip(now == null ? null : Tooltip.create(now));
        if (!showLabel) {
            // Narration reads the message; an icon-only button never draws one, so it narrates its tooltip.
            setMessage(now == null ? Component.empty() : now);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // ── press: mouse presses come through onClick (which knows the button), keyboard through onPress ──
    //? if >=1.21.10 {
    @Override
    public void onPress(InputWithModifiers input) {
        action.press(0);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        action.press(event.button());
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return MouseButtons.accepts(mouseButtons, buttonInfo.button());
    }
    //?} else {
    /*@Override
    public void onPress() {
        action.press(0);
    }

    // NeoForge's button-aware overload (IAbstractWidgetExtension); vanilla's onClick(double, double) has no button.
    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        action.press(button);
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return MouseButtons.accepts(mouseButtons, button);
    }*/
    //?}

    public static final class Builder {
        private final int x, y, width, height;
        private Action action = mouseButton -> {};
        private int mouseButtons = MouseButtons.LEFT;
        @Nullable
        private ButtonIcon icon;
        @Nullable
        private Component label;
        @Nullable
        private BooleanSupplier latched;
        @Nullable
        private BooleanSupplier activeWhen;
        @Nullable
        private Supplier<Component> tooltip;

        private Builder(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /** A left-click (or keyboard) press runs {@code onPress}. */
        public Builder onPress(Runnable onPress) {
            this.action = mouseButton -> onPress.run();
            this.mouseButtons = MouseButtons.LEFT;
            return this;
        }

        /** Presses by any of {@code mouseButtons} ({@link MouseButtons} mask) reach {@code action} with the button. */
        public Builder onPress(int mouseButtons, Action action) {
            this.action = action;
            this.mouseButtons = mouseButtons;
            return this;
        }

        public Builder icon(ButtonSprite sprite) {
            return icon(ButtonIcon.sprite(sprite));
        }

        public Builder icon(ButtonIcon icon) {
            this.icon = icon;
            return this;
        }

        public Builder label(Component label) {
            this.label = label;
            return this;
        }

        public Builder latched(BooleanSupplier latched) {
            this.latched = latched;
            return this;
        }

        /** Live "can be pressed" state (vanilla's {@code active}): greys the face, icon and label when false. */
        public Builder activeWhen(BooleanSupplier activeWhen) {
            this.activeWhen = activeWhen;
            return this;
        }

        public Builder tooltip(Component tooltip) {
            return tooltip(() -> tooltip);
        }

        /** A live tooltip: re-read every frame, re-created only when the text changes. */
        public Builder tooltip(Supplier<Component> tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public BCButton build() {
            return new BCButton(this);
        }
    }
}
