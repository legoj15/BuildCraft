/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Queues;

import net.minecraft.client.Minecraft;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.10 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.BCLog;

import buildcraft.lib.BCLibItems;
import buildcraft.lib.client.guide.font.FontManager;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.font.MinecraftFont;
import buildcraft.lib.client.guide.parts.GuideChapter;
import buildcraft.lib.client.guide.parts.GuidePageBase;
import buildcraft.lib.client.guide.parts.contents.GuidePageContents;
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
import buildcraft.lib.misc.GuiUtil;
import buildcraft.lib.misc.LocaleUtil;

/** Main guide book screen. Implements the cover display, flip-open animation,
 * two-page layout with text/chapter rendering, page turning, and back navigation. */
@SuppressWarnings("this-escape")
public class GuiGuide extends Screen {

    // --- Texture identifiers ---
    public static final Identifier ICONS_1 = Identifier.parse("minecraft:textures/gui/icons.png");
    public static final Identifier ICONS_2 = Identifier.parse("buildcraftunofficial:textures/gui/guide/icons.png");
    public static final Identifier COVER = Identifier.parse("buildcraftunofficial:textures/gui/guide/cover.png");
    public static final Identifier LEFT_PAGE = Identifier.parse("buildcraftunofficial:textures/gui/guide/left_page.png");
    public static final Identifier RIGHT_PAGE = Identifier.parse("buildcraftunofficial:textures/gui/guide/right_page.png");
    public static final Identifier LEFT_PAGE_BACK = Identifier.parse("buildcraftunofficial:textures/gui/guide/left_page_back.png");
    public static final Identifier RIGHT_PAGE_BACK = Identifier.parse("buildcraftunofficial:textures/gui/guide/right_page_back.png");
    public static final Identifier LEFT_PAGE_FIRST = Identifier.parse("buildcraftunofficial:textures/gui/guide/left_page_first.png");
    public static final Identifier RIGHT_PAGE_LAST = Identifier.parse("buildcraftunofficial:textures/gui/guide/right_page_last.png");
    public static final Identifier NOTE = Identifier.parse("buildcraftunofficial:textures/gui/guide/note.png");

    // --- Book layout icons ---
    public static final GuiIcon BOOK_COVER = new GuiIcon(COVER, 0, 0, 202, 248);
    public static final GuiIcon BOOK_BINDING = new GuiIcon(COVER, 204, 0, 11, 248);

    public static final GuiIcon PAGE_LEFT = new GuiIcon(LEFT_PAGE, 0, 0, 193, 248);
    public static final GuiIcon PAGE_RIGHT = new GuiIcon(RIGHT_PAGE, 0, 0, 193, 248);

    public static final GuiIcon PAGE_LEFT_BACK = new GuiIcon(LEFT_PAGE_BACK, 0, 0, 193, 248);
    public static final GuiIcon PAGE_RIGHT_BACK = new GuiIcon(RIGHT_PAGE_BACK, 0, 0, 193, 248);

    public static final GuiIcon PAGE_LEFT_FIRST = new GuiIcon(LEFT_PAGE_FIRST, 0, 0, 193, 248);
    public static final GuiIcon PAGE_RIGHT_LAST = new GuiIcon(RIGHT_PAGE_LAST, 0, 0, 193, 248);

    public static final int PAGE_WIDTH = 168;
    public static final int PAGE_HEIGHT = 190;

    public static final GuiRectangle PAGE_LEFT_TEXT = new GuiRectangle(23, 25, PAGE_WIDTH, PAGE_HEIGHT);
    public static final GuiRectangle PAGE_RIGHT_TEXT = new GuiRectangle(4, 25, PAGE_WIDTH, PAGE_HEIGHT);

    // --- UI icons from the icons spritesheet ---
    public static final GuiIcon PEN_UP = new GuiIcon(ICONS_2, 0, 0, 14, 135);
    public static final GuiIcon PEN_ANGLED = new GuiIcon(ICONS_2, 17, 0, 100, 100);
    public static final GuiIcon PEN_HIDDEN_MIN = new GuiIcon(ICONS_2, 0, 4, 10, 5);
    public static final GuiIcon PEN_HIDDEN_MAX = new GuiIcon(ICONS_2, 0, 4, 10, 15);

    public static final GuiIcon TURN_BACK = new GuiIcon(ICONS_2, 23, 139, 18, 10);
    public static final GuiIcon TURN_BACK_HOVERED = new GuiIcon(ICONS_2, 23, 152, 18, 10);
    public static final GuiIcon TURN_FORWARDS = new GuiIcon(ICONS_2, 0, 139, 18, 10);
    public static final GuiIcon TURN_FORWARDS_HOVERED = new GuiIcon(ICONS_2, 0, 152, 18, 10);

    public static final GuiIcon BACK = new GuiIcon(ICONS_2, 48, 139, 17, 9);
    public static final GuiIcon BACK_HOVERED = new GuiIcon(ICONS_2, 48, 152, 17, 9);

    public static final GuiIcon BOX_EMPTY = new GuiIcon(ICONS_2, 0, 164, 16, 16);
    public static final GuiIcon BOX_MINUS = new GuiIcon(ICONS_2, 16, 164, 16, 16);
    public static final GuiIcon BOX_PLUS = new GuiIcon(ICONS_2, 32, 164, 16, 16);
    public static final GuiIcon BOX_TICKED = new GuiIcon(ICONS_2, 48, 164, 16, 16);
    public static final GuiIcon BOX_CHAPTER = new GuiIcon(ICONS_2, 64, 164, 16, 16);

    public static final GuiIcon BOX_SELECTED_EMPTY = new GuiIcon(ICONS_2, 0, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_MINUS = new GuiIcon(ICONS_2, 16, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_PLUS = new GuiIcon(ICONS_2, 32, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_TICKED = new GuiIcon(ICONS_2, 48, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_CHAPTER = new GuiIcon(ICONS_2, 64, 180, 16, 16);

    public static final SpriteRaw BOX_CODE_SPRITE = new SpriteRaw(ICONS_2, 80, 164, 16, 16, 256);
    public static final GuiIcon BOX_CODE = new GuiIcon(BOX_CODE_SPRITE, 256);
    public static final SpriteNineSliced BOX_CODE_SLICED = new SpriteNineSliced(BOX_CODE_SPRITE, 4, 4, 12, 12, 16);

    public static final GuiIcon BORDER_TOP_LEFT = new GuiIcon(ICONS_2, 0, 196, 13, 13);
    public static final GuiIcon BORDER_TOP_RIGHT = new GuiIcon(ICONS_2, 13, 196, 13, 13);
    public static final GuiIcon BORDER_BOTTOM_LEFT = new GuiIcon(ICONS_2, 0, 209, 13, 13);
    public static final GuiIcon BORDER_BOTTOM_RIGHT = new GuiIcon(ICONS_2, 13, 209, 13, 13);

    public static final GuiIcon ORDER_TYPE = new GuiIcon(ICONS_2, 0, 0, 14, 14);
    public static final GuiIcon ORDER_MOD_TYPE = new GuiIcon(ICONS_2, 14, 0, 14, 14);
    public static final GuiIcon ORDER_ALPHABETICAL = new GuiIcon(ICONS_2, 28, 0, 14, 14);

    public static final GuiIcon EXPANDED_ARROW = new GuiIcon(ICONS_2, 96, 164, 16, 16);
    public static final GuiIcon CLOSED_ARROW = new GuiIcon(ICONS_2, 96, 180, 16, 16);

    public static final GuiIcon CHAPTER_MARKER = new GuiIcon(ICONS_2, 0, 56, 32, 32);
    public static final GuiIcon CHAPTER_MARKER_LEFT = new GuiIcon(ICONS_2, 0, 56, 24, 32);
    public static final GuiIcon CHAPTER_MARKER_RIGHT = new GuiIcon(ICONS_2, 8, 56, 24, 32);

    public static final SpriteNineSliced CHAPTER_MARKER_9;
    public static final SpriteNineSliced CHAPTER_MARKER_9_LEFT;
    public static final SpriteNineSliced CHAPTER_MARKER_9_RIGHT;

    public static final GuiIcon NOTE_PAGE = new GuiIcon(NOTE, 0, 0, 131, 164);
    public static final GuiIcon NOTE_UNDERLAY = new GuiIcon(ICONS_2, 0, 1, 3, 4);
    public static final GuiIcon NOTE_OVERLAY = new GuiIcon(ICONS_2, 0, 1, 2, 3);

    public static final GuiIcon SEARCH_ICON = new GuiIcon(ICONS_2, 26, 196, 12, 12);
    public static final GuiIcon SEARCH_TAB_CLOSED = new GuiIcon(ICONS_2, 58, 196, 14, 6);
    public static final GuiIcon SEARCH_TAB_OPEN = new GuiIcon(ICONS_2, 40, 209, 106, 14);

    public static final GuiIcon[] ORDERS = { ORDER_TYPE, ORDER_MOD_TYPE, ORDER_ALPHABETICAL };

    public static final GuiRectangle BACK_POSITION = new GuiRectangle(
        PAGE_LEFT.width - BACK.width / 2, PAGE_LEFT.height - BACK.height - 2, BACK.width, BACK.height
    );

    public static final TypeOrder[] SORTING_TYPES = {
        new TypeOrder("buildcraft.guide.order.type_subtype", ETypeTag.TYPE, ETypeTag.SUB_TYPE),
        new TypeOrder("buildcraft.guide.order.mod_type", ETypeTag.MOD, ETypeTag.TYPE),
        new TypeOrder("buildcraft.guide.order.alphabetical")
    };

    public static final IGuiArea FLOATING_CHAPTER_MENU;

    private static final float BOOK_OPEN_TIME = 10f;

    static {
        CHAPTER_MARKER_9 = new SpriteNineSliced(CHAPTER_MARKER.sprite, 8, 8, 24, 24, 32);
        CHAPTER_MARKER_9_LEFT = new SpriteNineSliced(CHAPTER_MARKER_LEFT.sprite, 8, 8, 24, 24, 24, 32);
        CHAPTER_MARKER_9_RIGHT = new SpriteNineSliced(
            new SpriteRaw(ICONS_2, 8, 56, 24, 32, 256), 0, 8, 16, 24, 24, 32
        );
        FLOATING_CHAPTER_MENU = GuiUtil.moveRectangleToCentre(
            new GuiRectangle((PAGE_LEFT_TEXT.getWidth() + PAGE_RIGHT_TEXT.getWidth()) / 2, PAGE_LEFT.height - 20)
        );
    }

    // --- Instance fields ---
    public final MousePosition mouse = new MousePosition();
    @Nullable
    public final GuideBook book;
    public final GuideContentsData bookData;

    public TypeOrder sortingOrder = SORTING_TYPES[0];
    private boolean isOpen = false;
    private boolean isOpening = false;
    /** Small-screen only: when the window is too narrow for side tabs, a "Chapters" tab at the
     *  top of the right page toggles this centered overlay menu (restores 1.12.2 behavior). */
    private boolean showingContentsMenu = false;

    /** Float between -90 and 90 */
    private float openingAngleLast = -90, openingAngleNext = -90;

    public int minX, minY;
    @Nullable
    public ItemStack tooltipStack = null;
    public final List<List<String>> tooltips = new ArrayList<>();

    private final Deque<GuidePageBase> pages = Queues.newArrayDeque();
    private final List<GuideChapter> chapters = new ArrayList<>();
    private GuidePageBase currentPage;
    private IFontRenderer currentFont = FontManager.INSTANCE.getOrLoadFont("SansSerif", 9);
    private float lastPartialTicks;

    // Snapshot of GuideManager.getReloadGeneration() at GUI-construction time. tick()
    // compares against the current value and rebuilds open pages via createReloaded()
    // when the registry has been reloaded out from under us — without this, /reload
    // leaves the contents tree referencing orphaned ContentsNode / PageLink instances
    // and search returns blank.
    private int seenReloadGeneration = GuideManager.INSTANCE.getReloadGeneration();

    public GuiGuide() {
        this((GuideBook) null);
    }

    public GuiGuide(String bookName) {
        this(GuideBookRegistry.INSTANCE.getBook(bookName));
    }

    private GuiGuide(@Nullable GuideBook book) {
        super(Component.literal("BuildCraft Guide"));
        this.book = book;
        this.bookData = book != null ? book.data : GuideManager.BOOK_ALL_DATA;
        openPage(new GuidePageContents(this));
    }

    // --- Page management ---

    public void openPage(GuidePageBase page) {
        if (currentPage != null && currentPage.shouldPersistHistory()) {
            pages.push(currentPage);
        }
        setPageInternal(page);
    }

    public void closePage() {
        if (pages.isEmpty()) {
            minecraft.setScreen(null);
        } else {
            setPageInternal(pages.pop());
        }
    }

    public void goBackToMenu() {
        GuidePageBase newPage = currentPage;
        while (!pages.isEmpty()) {
            newPage = pages.pop();
        }
        setPageInternal(newPage);
    }

    private void setPageInternal(GuidePageBase page) {
        currentPage = page;
        refreshChapters();
    }

    public GuidePageBase getCurrentPage() {
        return currentPage;
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

    private void refreshAfterReload(int currentGen) {
        // Snapshot history head→tail (top-of-stack first) and rebuild each page
        // against the freshly-reloaded registry. createReloaded preserves user state
        // (search text, page index, etc.) per page subclass.
        List<GuidePageBase> snapshot = new ArrayList<>(pages);
        pages.clear();
        List<GuidePageBase> survivors = new ArrayList<>(snapshot.size());
        for (GuidePageBase old : snapshot) {
            GuidePageBase rebuilt = null;
            try {
                rebuilt = old.createReloaded();
            } catch (Throwable t) {
                buildcraft.api.core.BCLog.logger.warn(
                    "[lib.guide] Failed to rebuild history page " + old.getClass().getSimpleName()
                        + " after reload — dropping.", t
                );
            }
            if (rebuilt != null) {
                survivors.add(rebuilt);
            }
        }

        GuidePageBase rebuiltCurrent = null;
        if (currentPage != null) {
            try {
                rebuiltCurrent = currentPage.createReloaded();
            } catch (Throwable t) {
                buildcraft.api.core.BCLog.logger.warn(
                    "[lib.guide] Failed to rebuild current page " + currentPage.getClass().getSimpleName()
                        + " after reload — falling back to contents.", t
                );
            }
        }
        if (rebuiltCurrent == null) {
            // Fall back to a fresh contents page so the user lands somewhere usable
            // rather than seeing a stale render.
            rebuiltCurrent = new GuidePageContents(this);
        }
        currentPage = rebuiltCurrent;

        for (GuidePageBase survivor : survivors) {
            pages.addLast(survivor);
        }

        refreshChapters();
        seenReloadGeneration = currentGen;

        if (GuideManager.DEBUG) {
            buildcraft.api.core.BCLog.logger.info(
                "[lib.guide] GuiGuide refreshed for reload generation " + currentGen
                    + "; rebuilt " + (1 + survivors.size()) + " page(s), "
                    + (snapshot.size() - survivors.size()) + " dropped."
            );
        }
    }

    // --- Screen overrides ---

    @Override
    public void tick() {
        // Capture once per tick so a reload landing mid-tick doesn't leave seenReloadGeneration
        // ahead of what we actually rebuilt against. Run before super.tick() and the
        // currentPage.updateScreen() call below so updateScreen sees the rebuilt page.
        int currentGen = GuideManager.INSTANCE.getReloadGeneration();
        if (currentGen != seenReloadGeneration) {
            refreshAfterReload(currentGen);
        }

        super.tick();
        if (isOpen) {
            currentPage.updateScreen();
            for (GuideChapter chapter : chapters) {
                chapter.updateScreen();
            }
        } else if (isOpening) {
            openingAngleLast = openingAngleNext;
            openingAngleNext += 180 / BOOK_OPEN_TIME;
        }
        if (currentPage != null) {
            setupFontRenderer();
            currentPage.tick();
        }
    }

    public boolean isSmallScreen() {
        return this.width < 590;
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    //?} else {
    /*public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {*/
    //?}
        // Set the graphics context for font rendering and other sub-renderers
        BCGraphics bcg = new BCGraphics(graphics);
        MinecraftFont.setGuiGraphics(bcg);
        GuiIcon.setGuiGraphics(bcg);
        buildcraft.lib.gui.GuiStack.setGuiGraphics(bcg);
        buildcraft.lib.gui.GuiFluid.setGuiGraphics(bcg);

        // In 1.21+, Screen.extractRenderState receives getGameTimeDeltaTicks() — the
        // PER-FRAME delta in tick units (e.g., ~0.33 at 60 fps), NOT the 0-1 fraction
        // within the current tick that the lerp formulas in this class were ported from
        // 1.12.2 expecting. Using the delta directly makes every frame render a position
        // ~0.33 of the way from "last" to "next", visibly snapping rather than smoothly
        // interpolating. Substitute the proper 0-1 fraction here so all downstream
        // partialTicks consumers (cover flip, chapter hover lerp) interpolate correctly.
        //? if >=1.21.10 {
        partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?} else {
        /*// 1.21.1 exposes the DeltaTracker via getTimer(), not getDeltaTracker().
        partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);*/
        //?}

        lastPartialTicks = partialTicks;
        minX = (this.width - PAGE_LEFT.width * 2) / 2;
        minY = (this.height - BOOK_COVER.height) / 2;
        mouse.setMousePosition(mouseX, mouseY);

        // NeoForge 1.21.11: renderBackground is called by the parent
        // Screen.renderWithTooltipAndSubtitles, so we must NOT call it here
        // (calling it twice causes "Can only blur once per frame").

        try {
            if (isOpen) {
                drawOpen(partialTicks);
            } else if (isOpening) {
                drawOpening(partialTicks);
            } else {
                drawCover();
            }
        } catch (Throwable t) {
            BCLog.logger.error("[lib.guide] Failed to render the guide GUI", t);
            throw new RuntimeException(t);
        }

        // Clear the graphics context
        MinecraftFont.setGuiGraphics(null);
        GuiIcon.setGuiGraphics(null);
        buildcraft.lib.gui.GuiStack.setGuiGraphics(null);
        buildcraft.lib.gui.GuiFluid.setGuiGraphics(null);
    }

    public float getLastPartialTicks() {
        return this.lastPartialTicks;
    }

    // --- Drawing methods ---

    private void drawCover() {
        minX = (this.width - BOOK_COVER.width) / 2;
        minY = (this.height - BOOK_COVER.height) / 2;

        BOOK_COVER.drawAt(minX, minY);
    }

    private void drawOpening(float partialTicks) {
        minX = (this.width - BOOK_COVER.width) / 2;
        minY = (this.height - BOOK_COVER.height) / 2;

        float openingAngle = openingAngleLast * (1 - partialTicks) + openingAngleNext * partialTicks;
        float sin = Mth.sin((float) (openingAngle * Math.PI / 180));
        if (sin < 0) {
            sin *= -1;
        }
        if (openingAngle >= 90) {
            isOpen = true;
        }


        if (openingAngle < 0) {
            // Cover is swinging from closed to flat
            int coverWidth = (int) (sin * BOOK_COVER.width);
            sin = 1 - sin;
            float offset = sin * 50;
            int bindingWidth = (int) (sin * BOOK_BINDING.width);

            PAGE_RIGHT.drawAt(minX + BOOK_COVER.width - PAGE_RIGHT.width, minY);

            BOOK_COVER.drawCustomQuad(
                minX, minY + BOOK_COVER.height,
                minX + coverWidth, minY + BOOK_COVER.height + offset,
                minX + coverWidth, minY - offset,
                minX, minY
            );

            BOOK_BINDING.drawScaledInside(
                (int) (minX + coverWidth - bindingWidth * 0.5), (int) (minY - offset),
                bindingWidth, (int) (BOOK_BINDING.height + offset * 2)
            );

        } else if (openingAngle == 0) {
            // Cover is exactly flat
            PAGE_RIGHT.drawAt(minX + BOOK_COVER.width - PAGE_LEFT.width, minY);
            BOOK_COVER.drawAt(minX, minY);

        } else {
            // Left page is unfurling
            int pageWidth = (int) (sin * PAGE_LEFT.width);
            int bindingWidth = (int) ((1 - sin) * BOOK_BINDING.width);
            float offset = (1 - sin) * 50;

            minX = (this.width - PAGE_LEFT.width - pageWidth) / 2;
            minY = (this.height - BOOK_COVER.height) / 2;

            PAGE_RIGHT.drawAt(minX + pageWidth + bindingWidth, minY);

            PAGE_LEFT.drawCustomQuad(
                minX + bindingWidth, minY + PAGE_LEFT.height + offset,
                minX + bindingWidth + pageWidth, minY + PAGE_LEFT.height,
                minX + bindingWidth + pageWidth, minY,
                minX + bindingWidth, minY - offset
            );

            BOOK_BINDING.drawScaledInside(
                (int) (minX + bindingWidth * 0.5), (int) (minY - offset),
                bindingWidth, (int) (BOOK_BINDING.height + offset * 2)
            );
        }
    }

    private void drawOpen(float partialTicks) {

        int cp = currentPage.getPage();
        int pc = currentPage.getPageCount();
        boolean isHalfPageShown = cp + 1 == pc;

        // Draw page backgrounds
        (cp == 0 ? PAGE_LEFT_FIRST : PAGE_LEFT).drawAt(minX, minY);
        final GuiIcon lastPageIcon;
        if (cp + 2 == pc) {
            lastPageIcon = PAGE_RIGHT_LAST;
        } else if (isHalfPageShown) {
            lastPageIcon = PAGE_RIGHT_BACK;
        } else {
            lastPageIcon = PAGE_RIGHT;
        }
        lastPageIcon.drawAt(minX + PAGE_LEFT.width, minY);

        // Draw the title — once over the centre of each content page rather than
        // once over the spine. This is more readable (the spine art partly obscures
        // text drawn over the gutter) and gives every page a clear header. The
        // back-of-book "blank" half-page (when the entry ends on an odd page) gets
        // no title, since there's no content there to label.
        String title = currentPage.getTitle();
        if (title != null) {
            int titleWidth = currentFont.getStringWidth(title);
            int leftX = (int) (minX + PAGE_LEFT_TEXT.getX() + (PAGE_LEFT_TEXT.getWidth() - titleWidth) / 2);
            currentFont.drawString(title, leftX, minY + 12, 0xFF90816a);
            if (!isHalfPageShown) {
                int rightX = (int) (minX + PAGE_LEFT.width + PAGE_RIGHT_TEXT.getX()
                    + (PAGE_RIGHT_TEXT.getWidth() - titleWidth) / 2);
                currentFont.drawString(title, rightX, minY + 12, 0xFF90816a);
            }
        }

        // Reset state for content rendering
        tooltipStack = null;
        tooltips.clear();
        setupFontRenderer();
        for (GuideChapter chapter : chapters) {
            chapter.reset();
        }

        // Render page content
        currentPage.renderFirstPage(
            minX + (int) PAGE_LEFT_TEXT.getX(), minY + (int) PAGE_LEFT_TEXT.getY(),
            (int) PAGE_LEFT_TEXT.getWidth(), (int) PAGE_LEFT_TEXT.getHeight()
        );
        int secondPageX = minX + PAGE_LEFT.width + (int) PAGE_RIGHT_TEXT.getX();
        if (!isHalfPageShown) {
            currentPage.renderSecondPage(
                secondPageX, minY + (int) PAGE_RIGHT_TEXT.getY(),
                (int) PAGE_RIGHT_TEXT.getWidth(), (int) PAGE_RIGHT_TEXT.getHeight()
            );
        }

        // Draw chapter sidebar. When the book is too narrow for side tabs, fall back to a
        // "Chapters" tab at the top of the right page that toggles a centered overlay menu
        // (1.12.2 behavior). secondPageX was computed above for renderSecondPage.
        boolean drawContents = true;
        boolean smallScreen = isSmallScreen();
        if (smallScreen) {
            drawContents = showingContentsMenu;
            String str = LocaleUtil.localize("buildcraft.guide.chapter_list");
            if (showingContentsMenu) {
                CHAPTER_MARKER_9.draw(FLOATING_CHAPTER_MENU);
                currentFont.drawString(str, (int) FLOATING_CHAPTER_MENU.getX() + 7,
                    (int) FLOATING_CHAPTER_MENU.getY() + 7, 0);
            } else {
                boolean isHovered = new GuiRectangle(secondPageX, minY, 80, 10).contains(mouse);
                int tabY = minY + (isHovered ? -5 : 0);
                int strWidth = currentFont.getStringWidth(str);
                BCGraphics graphics = GuiIcon.getGuiGraphics();
                if (graphics != null) {
                    // Clip so the tab only peeks from the top edge rather than drawing its full
                    // height down over the page.
                    try (GuiUtil.AutoGlScissor scissor =
                        GuiUtil.scissor(graphics, secondPageX, 0, strWidth + 20, minY + 10)) {
                        CHAPTER_MARKER_9.draw(secondPageX, tabY, strWidth + 20, 100);
                        currentFont.drawString(str, secondPageX + 10, tabY + 3, 0);
                    }
                } else {
                    CHAPTER_MARKER_9.draw(secondPageX, tabY, strWidth + 20, 100);
                    currentFont.drawString(str, secondPageX + 10, tabY + 3, 0);
                }
            }
        }

        if (drawContents) {
            int chapterIndex = 0;
            for (GuideChapter chapter : chapters) {
                if (chapter.hasParent()) {
                    continue;
                }
                chapterIndex += chapter.draw(chapterIndex, partialTicks, smallScreen);
            }
        }

        // Draw the back button if there are pages on the stack
        if (!pages.isEmpty()) {
            GuiIcon icon = BACK;
            IGuiArea position = BACK_POSITION.offset(minX, minY);
            if (position.contains(mouse)) {
                icon = BACK_HOVERED;
            }
            icon.drawAt(position);
        }

        // Draw page turn arrows
        drawPageTurnArrows(cp, pc, isHalfPageShown);

        // Render tooltips last so they sit above all other content. Item tooltips
        // (set by GuidePartItem.drawItemStack when the mouse is over a stack)
        // win over text tooltips, matching 1.12.2's `if (tooltipStack != null) ... else ...`.
        // Without this pass `tooltipStack` and `tooltips` were assigned every frame
        // but never consumed — hovering over an item or a link did nothing visible.
        buildcraft.lib.gui.BCGraphics graphics = buildcraft.lib.gui.GuiIcon.getGuiGraphics();
        if (graphics != null) {
            int mx = (int) mouse.getX();
            int my = (int) mouse.getY();
            if (tooltipStack != null && !tooltipStack.isEmpty()) {
                graphics.setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, tooltipStack, mx, my);
            } else if (!tooltips.isEmpty()) {
                java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                for (java.util.List<String> tooltip : tooltips) {
                    for (String line : tooltip) {
                        lines.add(net.minecraft.network.chat.Component.literal(line));
                    }
                }
                if (!lines.isEmpty()) {
                    graphics.setTooltipForNextFrame(
                        net.minecraft.client.Minecraft.getInstance().font,
                        lines,
                        java.util.Optional.empty(),
                        mx, my
                    );
                }
            }
        }
    }

    private void drawPageTurnArrows(int currentPageIndex, int pageCount, boolean isHalfPage) {
        // Forward arrow (right side)
        if (currentPageIndex + 2 < pageCount) {
            int arrowX = minX + PAGE_LEFT.width + PAGE_RIGHT.width - TURN_FORWARDS.width - 10;
            int arrowY = minY + PAGE_RIGHT.height - TURN_FORWARDS.height - 8;
            GuiRectangle forwardRect = new GuiRectangle(arrowX, arrowY, TURN_FORWARDS.width, TURN_FORWARDS.height);
            GuiIcon icon = forwardRect.contains(mouse) ? TURN_FORWARDS_HOVERED : TURN_FORWARDS;
            icon.drawAt(arrowX, arrowY);
        }

        // Back arrow (left side)
        if (currentPageIndex > 0) {
            int arrowX = minX + 10;
            int arrowY = minY + PAGE_LEFT.height - TURN_BACK.height - 8;
            GuiRectangle backRect = new GuiRectangle(arrowX, arrowY, TURN_BACK.width, TURN_BACK.height);
            GuiIcon icon = backRect.contains(mouse) ? TURN_BACK_HOVERED : TURN_BACK;
            icon.drawAt(arrowX, arrowY);
        }
    }

    public void setupFontRenderer() {
        currentPage.setFontRenderer(currentFont);
    }

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (currentPage != null && currentPage.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        boolean doubleClick = false; // 1.21.1 mouseClicked has no double-click signal
        if (currentPage != null && currentPage.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }*/
    //?}
        mouse.setMousePosition((int) mouseX, (int) mouseY);

        if (mouseButton == 0) {
            if (isOpen) {
                int page0xMin = this.minX + (int) PAGE_LEFT_TEXT.getX();
                int page0xMax = page0xMin + (int) PAGE_LEFT_TEXT.getWidth();
                int page1xMin = this.minX + PAGE_LEFT.width + (int) PAGE_RIGHT_TEXT.getX();
                int page1xMax = page1xMin + (int) PAGE_RIGHT_TEXT.getWidth();
                int pageYMin = this.minY + (int) PAGE_RIGHT_TEXT.getY();
                int pageYMax = pageYMin + (int) PAGE_RIGHT_TEXT.getHeight();

                GuidePageBase current = currentPage;
                current.setFontRenderer(currentFont);

                // Check chapter clicks. On a small screen the side tabs are hidden, so only
                // hit-test chapters when the centered menu is actually open — otherwise a click
                // in the middle of the page could trigger an invisible central tab.
                boolean chaptersInteractive = !isSmallScreen() || showingContentsMenu;
                if (chaptersInteractive) {
                    for (GuideChapter chapter : chapters) {
                        int clickResult = chapter.handleClick();
                        if (clickResult > 0) {
                            // Selecting a chapter (result 1, not an expand-arrow toggle 2)
                            // closes the overlay menu.
                            if (showingContentsMenu && clickResult == 1) {
                                showingContentsMenu = false;
                            }
                            return true;
                        }
                    }
                }

                // Small-screen "Chapters" tab / overlay menu toggle (1.12.2 behavior).
                if (isSmallScreen()) {
                    if (showingContentsMenu) {
                        // A click outside the menu closes it; clicks inside are swallowed so
                        // they don't fall through to the page content beneath.
                        if (!FLOATING_CHAPTER_MENU.contains(mouse)) {
                            showingContentsMenu = false;
                        }
                        return true;
                    } else if (new GuiRectangle(
                        minX + PAGE_LEFT.width + (int) PAGE_RIGHT_TEXT.getX(), minY, 80, 10).contains(mouse)) {
                        showingContentsMenu = true;
                        return true;
                    }
                }

                // Check page turn arrows
                int cp = currentPage.getPage();
                int pc = currentPage.getPageCount();
                if (cp + 2 < pc) {
                    int arrowX = minX + PAGE_LEFT.width + PAGE_RIGHT.width - TURN_FORWARDS.width - 10;
                    int arrowY = minY + PAGE_RIGHT.height - TURN_FORWARDS.height - 8;
                    GuiRectangle forwardRect = new GuiRectangle(arrowX, arrowY, TURN_FORWARDS.width, TURN_FORWARDS.height);
                    if (forwardRect.contains(mouseX, mouseY)) {
                        currentPage.nextPage();
                        return true;
                    }
                }
                if (cp > 0) {
                    int arrowX = minX + 10;
                    int arrowY = minY + PAGE_LEFT.height - TURN_BACK.height - 8;
                    GuiRectangle backRect = new GuiRectangle(arrowX, arrowY, TURN_BACK.width, TURN_BACK.height);
                    if (backRect.contains(mouseX, mouseY)) {
                        currentPage.lastPage();
                        return true;
                    }
                }

                // Handle page content clicks
                current.handleMouseClick(
                    page0xMin, pageYMin, page0xMax - page0xMin, pageYMax - pageYMin,
                    (int) mouseX, (int) mouseY, mouseButton,
                    currentPage.getPage(), false
                );
                current.handleMouseClick(
                    page1xMin, pageYMin, page1xMax - page1xMin, pageYMax - pageYMin,
                    (int) mouseX, (int) mouseY, mouseButton,
                    currentPage.getPage() + 1, false
                );

                // Back button
                if (!pages.isEmpty() && BACK_POSITION.offset(minX, minY).contains(mouseX, mouseY)) {
                    closePage();
                    return true;
                }

            } else {
                // Click on cover to start opening
                if (mouseX >= minX && mouseY >= minY
                    && mouseX <= minX + BOOK_COVER.width
                    && mouseY <= minY + BOOK_COVER.height) {
                    if (isOpening || doubleClick) {
                        // Double-click to open instantly
                        isOpen = true;
                    }
                    isOpening = true;
                    return true;
                }
            }
        }

        //? if >=1.21.10 {
        return super.mouseClicked(event, doubleClick);
        //?} else {
        /*return super.mouseClicked(mouseX, mouseY, mouseButton);*/
        //?}
    }

    //? if >=1.21.10 {
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (currentPage != null && currentPage.keyPressed(keyEvent)) {
            return true;
        }

        // Page turning with left/right keys
        if (isOpen) {
            if (keyEvent.isLeft()) {
                currentPage.lastPage();
                return true;
            } else if (keyEvent.isRight()) {
                currentPage.nextPage();
                return true;
            }
        }

        return super.keyPressed(keyEvent);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (currentPage != null && currentPage.keyPressed(key, scancode, modifiers)) {
            return true;
        }

        // Page turning with left/right keys (GLFW_KEY_LEFT=263, GLFW_KEY_RIGHT=262)
        if (isOpen) {
            if (key == 263) {
                currentPage.lastPage();
                return true;
            } else if (key == 262) {
                currentPage.nextPage();
                return true;
            }
        }

        return super.keyPressed(key, scancode, modifiers);
    }*/
    //?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //? if >=1.21.10 {
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (currentPage != null && currentPage.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }
    //?} else {
    /*@Override
    public boolean charTyped(char chr, int modifiers) {
        if (currentPage != null && currentPage.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }*/
    //?}
}
