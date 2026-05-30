package buildcraft.lib.client.guide.entry;

import net.minecraft.resources.Identifier;

import com.google.gson.JsonObject;

import buildcraft.lib.client.guide.data.JsonTypeTags;

public final class PageEntry<T> extends PageValue<T> {

    public final JsonTypeTags typeTags;
    public final Identifier book;
    /** True if the entry documents something only obtainable via creative mode,
     *  OP/cheats, or commands. The contents-page generator forwards this to
     *  {@link buildcraft.lib.client.guide.parts.contents.PageLink#creativeOnly}
     *  so the entry is hidden from survival players without cheats access. */
    public final boolean creativeOnly;
    /** TOC sort weight (lower sorts earlier; ties fall back to alphabetical). Defaults to 0,
     *  so unweighted entries keep the historical pure-alphabetical order. A group/chapter
     *  inherits the lowest weight among its members (see
     *  {@link buildcraft.lib.client.guide.parts.contents.ContentsNode#getSortIndex}). */
    public final int sortIndex;

    public PageEntry(PageValueType<T> type, JsonTypeTags typeTags, Identifier book, T value) {
        this(type, typeTags, book, value, false);
    }

    public PageEntry(PageValueType<T> type, JsonTypeTags typeTags, Identifier book, T value, boolean creativeOnly) {
        super(type, value);
        this.typeTags = typeTags;
        this.book = book;
        this.creativeOnly = creativeOnly;
        this.sortIndex = 0;
    }

    public PageEntry(PageValueType<T> type, Identifier name, JsonObject json, T value) {
        super(type, value, PageValue.getTitleOverride(json));
        // Read book identifier from JSON
        this.book = json.has("book")
            ? Identifier.parse(json.get("book").getAsString())
            : Identifier.parse("buildcraft:guide");
        String tagType = json.has("tag_type") ? json.get("tag_type").getAsString() : "";
        String subType = json.has("tag_subtype") ? json.get("tag_subtype").getAsString() : "";
        this.typeTags = new JsonTypeTags(name.getNamespace(), tagType, subType);
        this.creativeOnly = json.has("creative_only") && json.get("creative_only").getAsBoolean();
        this.sortIndex = json.has("sort") ? json.get("sort").getAsInt() : 0;
    }

    public PageValue<T> toPageValue() {
        return new PageValue<>(type, value);
    }

    @Override
    public String toString() {
        return value.getClass().getSimpleName() + ": " + value;
    }
}
