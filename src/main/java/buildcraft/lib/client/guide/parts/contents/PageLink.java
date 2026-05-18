package buildcraft.lib.client.guide.parts.contents;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.parts.GuidePageFactory;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.GuideText;

public abstract class PageLink implements IContentsLeaf {

    public final PageLine text;
    public final boolean startVisible;
    /** When true, the entry is hidden unless {@link #canAccessCreativeOnlyContent()}
     *  returns true. Used for entries documenting items the player has no survival
     *  way to obtain (creative engine, debugger, volume box). The gate AND's with
     *  {@link #startVisible} and with the search-match set, so creative-only entries
     *  stay out of both the default TOC and search results for survival players. */
    public final boolean creativeOnly;
    private final String lowerCaseName;
    private boolean visible;

    public PageLink(PageLine text, boolean startVisible) {
        this(text, startVisible, false);
    }

    public PageLink(PageLine text, boolean startVisible, boolean creativeOnly) {
        this.text = text;
        this.startVisible = startVisible;
        this.creativeOnly = creativeOnly;
        lowerCaseName = text.text.toLowerCase(Locale.ROOT);
        visible = startVisible && (!creativeOnly || canAccessCreativeOnlyContent());
    }

    /** True iff the local client player could plausibly obtain a "creative-only"
     *  item: in creative mode, holds OP level 2+, or is the host of a singleplayer/LAN
     *  world that has cheats enabled. Returns false in every other case (including
     *  when no client player exists yet, e.g. on the title screen). Cheap enough to
     *  call on every guide tick — {@code resetVisibility()} runs once per frame while
     *  the contents page is open with no search text. */
    public static boolean canAccessCreativeOnlyContent() {
        Minecraft mc = Minecraft.getInstance();
        // mc==null in unit-test JVMs (Minecraft.instance only gets assigned during client
        // bootstrap); also covers any future code path that calls the guide helpers before
        // client init. mc.player==null on the title screen / between worlds.
        if (mc == null) return false;
        Player p = mc.player;
        if (p == null) return false;
        if (p.getAbilities().instabuild) return true;
        // COMMANDS_GAMEMASTER is the modern equivalent of the old hasPermissions(2):
        // PermissionLevel.GAMEMASTERS == id 2. The client-side LocalPlayer's permission
        // set is populated from the server's OP level via ClientboundEntityEventPacket
        // events 24-28, so this check works in both SP and MP.
        if (p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;
        MinecraftServer sp = mc.getSingleplayerServer();
        return sp != null && sp.getWorldData().isAllowCommands();
    }

    @Override
    public String getSearchName() {
        return lowerCaseName;
    }

    @Nullable
    protected List<String> getTooltip() {
        return null;
    }

    public void appendTooltip(GuiGuide gui) {
        List<String> tooltip = getTooltip();
        if (tooltip != null && !tooltip.isEmpty()) {
            gui.tooltips.add(tooltip);
        }
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(Set<PageLink> matches) {
        visible = matches.contains(this) && (!creativeOnly || canAccessCreativeOnlyContent());
    }

    @Override
    public void resetVisibility() {
        visible = startVisible && (!creativeOnly || canAccessCreativeOnlyContent());
    }

    @Override
    public GuidePart createGuidePart(GuiGuide gui) {
        return new GuideText(gui, text) {
            @Override
            protected void renderTooltip() {
                appendTooltip(gui);
            }
        };
    }

    public abstract GuidePageFactory getFactoryLink();
}
