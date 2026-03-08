/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts.contents;

import java.util.Collections;
import java.util.List;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.guide.TypeOrder;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.parts.GuideChapter;
import buildcraft.lib.client.guide.parts.GuidePageBase;
import buildcraft.lib.client.guide.parts.GuidePart.PagePosition;

/** The base menu for showing all the locations. Controls the guide book table of contents. */
public class GuidePageContents extends GuidePageBase {

    private ContentsNodeGui contents;

    public GuidePageContents(GuiGuide gui) {
        super(gui);
        loadMainGui();
        setupChapters();
    }

    @Override
    public GuidePageBase createReloaded() {
        GuidePageContents newPage = new GuidePageContents(gui);
        newPage.numPages = numPages;
        newPage.goToPage(getIndex());
        return newPage;
    }

    public void loadMainGui() {
        contents = GuideManager.INSTANCE.getGuiContents(gui, this, gui.sortingOrder);
    }

    @Override
    public void setFontRenderer(IFontRenderer fontRenderer) {
        super.setFontRenderer(fontRenderer);
        contents.setFontRenderer(fontRenderer);
    }

    @Override
    public List<GuideChapter> getChapters() {
        return contents.getChapters();
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
    }

    @Override
    protected void renderPage(int x, int y, int width, int height, int index) {
        // Search bar, order buttons, and front page rendering deferred until full UI port
        // (needs EditBox, ConfigurableFontRenderer, RenderUtil, BCLibConfig, XmlPageLoader)
        PagePosition pos = new PagePosition(0, 0);
        pos = contents.render(x, y, width, height, pos, index);
        if (numPages == -1) {
            numPages = pos.page + 1;
        }
        super.renderPage(x, y, width, height, index);
    }

    @Override
    public void handleMouseClick(int x, int y, int width, int height, int mouseX, int mouseY, int mouseButton,
        int index, boolean isEditing) {
        super.handleMouseClick(x, y, width, height, mouseX, mouseY, mouseButton, index, isEditing);
        contents.onClicked(x, y, width, height, new PagePosition(2, 0), index);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }
}
