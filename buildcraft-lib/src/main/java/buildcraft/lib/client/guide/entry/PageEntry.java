package buildcraft.lib.client.guide.entry;

import net.minecraft.resources.Identifier;

import com.google.gson.JsonObject;

import buildcraft.lib.client.guide.data.JsonTypeTags;

public final class PageEntry<T> extends PageValue<T> {

    public final JsonTypeTags typeTags;
    public final Identifier book;

    public PageEntry(PageValueType<T> type, JsonTypeTags typeTags, Identifier book, T value) {
        super(type, value);
        this.typeTags = typeTags;
        this.book = book;
    }

    public PageEntry(PageValueType<T> type, Identifier name, JsonObject json, T value) {
        super(type, value);
        // Read book identifier from JSON
        this.book = json.has("book")
            ? Identifier.parse(json.get("book").getAsString())
            : Identifier.parse("buildcraft:guide");
        String tagType = json.has("tag_type") ? json.get("tag_type").getAsString() : "";
        String subType = json.has("tag_subtype") ? json.get("tag_subtype").getAsString() : "";
        this.typeTags = new JsonTypeTags(name.getNamespace(), tagType, subType);
    }

    public PageValue<T> toPageValue() {
        return new PageValue<>(type, value);
    }

    @Override
    public String toString() {
        return value.getClass().getSimpleName() + ": " + value;
    }
}
