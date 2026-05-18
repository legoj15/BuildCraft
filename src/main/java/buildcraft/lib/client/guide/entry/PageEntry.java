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

    public PageEntry(PageValueType<T> type, JsonTypeTags typeTags, Identifier book, T value) {
        this(type, typeTags, book, value, false);
    }

    public PageEntry(PageValueType<T> type, JsonTypeTags typeTags, Identifier book, T value, boolean creativeOnly) {
        super(type, value);
        this.typeTags = typeTags;
        this.book = book;
        this.creativeOnly = creativeOnly;
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
    }

    public PageValue<T> toPageValue() {
        return new PageValue<>(type, value);
    }

    @Override
    public String toString() {
        return value.getClass().getSimpleName() + ": " + value;
    }
}
