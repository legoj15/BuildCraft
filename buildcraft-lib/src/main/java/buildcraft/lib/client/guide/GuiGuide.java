/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.font.FontManager;
import buildcraft.lib.client.guide.parts.GuideChapter;
import buildcraft.lib.client.guide.parts.GuidePageBase;
import buildcraft.lib.client.sprite.SpriteNineSliced;
import buildcraft.lib.client.sprite.SpriteRaw;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.pos.MousePosition;
import buildcraft.lib.guide.GuideBook;
import buildcraft.lib.guide.GuideBookRegistry;
import buildcraft.lib.guide.GuideContentsData;

/** Main guide book screen. This is a STUB — rendering is deferred until the full UI port.
 * Provides the fields and types that GuidePart, GuideText, PageLink, and other classes reference. */
public class GuiGuide extends Screen {

    // --- Texture identifiers (used by static GuiIcon constants) ---
    public static final Identifier ICONS_1 = Identifier.parse("minecraft:textures/gui/icons.png");
    public static final Identifier ICONS_2 = Identifier.parse("buildcraftlib:textures/gui/guide/icons.png");
    public static final Identifier COVER = Identifier.parse("buildcraftlib:textures/gui/guide/cover.png");
    public static final Identifier LEFT_PAGE = Identifier.parse("buildcraftlib:textures/gui/guide/left_page.png");
    public static final Identifier RIGHT_PAGE = Identifier.parse("buildcraftlib:textures/gui/guide/right_page.png");
    public static final Identifier LEFT_PAGE_BACK = Identifier.parse("buildcraftlib:textures/gui/guide/left_page_back.png");
    public static final Identifier RIGHT_PAGE_BACK = Identifier.parse("buildcraftlib:textures/gui/guide/right_page_back.png");
    public static final Identifier NOTE = Identifier.parse("buildcraftlib:textures/gui/guide/note.png");

    // --- Static icons (referenced by parts, contents, etc.) ---
    public static final SpriteRaw BOX_EMPTY_SPRITE = new SpriteRaw(ICONS_2, 0, 0, 16, 16, 256);
    public static final SpriteRaw BOX_PLUS_SPRITE = new SpriteRaw(ICONS_2, 16, 0, 16, 16, 256);
    public static final SpriteRaw BOX_MINUS_SPRITE = new SpriteRaw(ICONS_2, 32, 0, 16, 16, 256);
    public static final SpriteRaw BOX_SELECTED_SPRITE = new SpriteRaw(ICONS_2, 48, 0, 16, 16, 256);
    public static final SpriteRaw BOX_CODE_SPRITE = new SpriteRaw(ICONS_2, 0, 16, 16, 16, 256);
    public static final SpriteRaw CHAPTER_MARKER_SPRITE = new SpriteRaw(ICONS_2, 0, 32, 32, 32, 256);
    public static final SpriteRaw CHAPTER_MARKER_LEFT_SPRITE = new SpriteRaw(ICONS_2, 0, 32, 24, 32, 256);

    public static final GuiIcon BOX_EMPTY = new GuiIcon(BOX_EMPTY_SPRITE, 256);
    public static final GuiIcon BOX_PLUS = new GuiIcon(BOX_PLUS_SPRITE, 256);
    public static final GuiIcon BOX_MINUS = new GuiIcon(BOX_MINUS_SPRITE, 256);
    public static final GuiIcon BOX_SELECTED = new GuiIcon(BOX_SELECTED_SPRITE, 256);
    public static final GuiIcon BOX_CODE = new GuiIcon(BOX_CODE_SPRITE, 256);
    public static final GuiIcon CHAPTER_MARKER = new GuiIcon(CHAPTER_MARKER_SPRITE, 256);
    public static final GuiIcon CHAPTER_MARKER_LEFT = new GuiIcon(CHAPTER_MARKER_LEFT_SPRITE, 256);

    public static final SpriteNineSliced CHAPTER_MARKER_9 = new SpriteNineSliced(CHAPTER_MARKER_SPRITE, 8, 8, 24, 24, 32);
    public static final SpriteNineSliced CHAPTER_MARKER_9_LEFT = new SpriteNineSliced(CHAPTER_MARKER_LEFT_SPRITE, 8, 8, 24, 24, 24, 32);
    public static final SpriteNineSliced CHAPTER_MARKER_9_RIGHT;
    public static final SpriteNineSliced BOX_CODE_SLICED = new SpriteNineSliced(BOX_CODE_SPRITE, 4, 4, 12, 12, 16);

    public static final GuiIcon BOOK_COVER = new GuiIcon(new SpriteRaw(COVER, 0, 0, 202, 248, 256), 256);
    public static final GuiIcon BOOK_BINDING = new GuiIcon(new SpriteRaw(COVER, 204, 0, 11, 248, 256), 256);
    public static final GuiIcon PAGE_LEFT = new GuiIcon(new SpriteRaw(LEFT_PAGE, 0, 0, 193, 248, 256), 256);
    public static final GuiIcon PAGE_RIGHT = new GuiIcon(new SpriteRaw(RIGHT_PAGE, 0, 0, 193, 248, 256), 256);
    public static final GuiIcon NOTE_PAGE = new GuiIcon(new SpriteRaw(NOTE, 0, 0, 131, 164, 256), 256);

    public static final TypeOrder[] SORTING_TYPES = {
        new TypeOrder("buildcraft.guide.order.source"),
        new TypeOrder("buildcraft.guide.order.type"),
        new TypeOrder("buildcraft.guide.order.type_sub"),
        new TypeOrder("buildcraft.guide.order.alphabetical")
    };

    public static final IGuiArea FLOATING_CHAPTER_MENU;

    static {
        // Mirror the chapter marker for the right variant
        CHAPTER_MARKER_9_RIGHT = new SpriteNineSliced(
            new SpriteRaw(ICONS_2, 8, 32, 24, 32, 256), 0, 8, 16, 24, 24, 32
        );
        FLOATING_CHAPTER_MENU = new GuiRectangle(0, 0, 50, 200);
    }

    // --- Instance fields used by GuidePart, GuideText, PageLink, etc. ---
    public final MousePosition mouse = new MousePosition();
    public final List<List<String>> tooltips = new ArrayList<>();
    @Nullable
    public final GuideBook book;

    private final List<GuideChapter> chapters = new ArrayList<>();
    private GuidePageBase currentPage;
    private IFontRenderer currentFont = FontManager.INSTANCE.getOrLoadFont("SansSerif", 9);

    public GuiGuide() {
        this((GuideBook) null);
    }

    public GuiGuide(String bookName) {
        this(GuideBookRegistry.INSTANCE.getBook(bookName));
    }

    private GuiGuide(@Nullable GuideBook book) {
        super(Component.literal("BuildCraft Guide"));
        this.book = book;
    }

    public IFontRenderer getCurrentFont() {
        return this.currentFont;
    }

    public List<GuideChapter> getChapters() {
        return chapters;
    }

    public void refreshChapters() {
        chapters.clear();
        if (currentPage != null) {
            chapters.addAll(currentPage.getChapters());
        }
    }

    // Rendering methods are deferred until the full UI port
}
