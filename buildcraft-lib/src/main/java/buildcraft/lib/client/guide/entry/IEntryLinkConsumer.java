package buildcraft.lib.client.guide.entry;

import buildcraft.lib.client.guide.data.JsonTypeTags;

/** Consumer interface for adding page entries to the guide contents. */
public interface IEntryLinkConsumer {
    // PageLink parameter replaced with Object until the contents system is ported
    void addChild(JsonTypeTags tags, Object link);
}
