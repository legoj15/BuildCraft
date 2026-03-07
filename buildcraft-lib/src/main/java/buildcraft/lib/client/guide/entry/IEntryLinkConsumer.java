package buildcraft.lib.client.guide.entry;

import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.client.guide.parts.contents.PageLink;

/** Consumer interface for adding page entries to the guide contents. */
public interface IEntryLinkConsumer {
    void addChild(JsonTypeTags tags, PageLink link);
}
