package buildcraft.lib.guide;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;

import buildcraft.api.registry.IScriptableRegistry.ISimpleEntryDeserializer;

public final class GuideBook {

    public static final ISimpleEntryDeserializer<GuideBook> DESERIALISER = GuideBook::deserialize;

    public final ResourceLocation name;
    public final ResourceLocation itemIcon;
    public final Component title;
    public final boolean appendAllEntries;
    public final GuideContentsData data = new GuideContentsData(this);

    private static GuideBook deserialize(Object nameObj, JsonObject json, JsonDeserializationContext ctx) {
        ResourceLocation name = (ResourceLocation) nameObj;
        ResourceLocation itemIcon = ResourceLocation.parse("buildcraftcore:guide_main");
        // Read title from JSON, falling back to name
        String titleStr = json.has("title") ? json.get("title").getAsString() : name.toString();
        Component title = Component.literal(titleStr);
        boolean addAll = json.has("all_entries") ? json.get("all_entries").getAsBoolean() : true;
        return new GuideBook(name, itemIcon, title, addAll);
    }

    public GuideBook(ResourceLocation name, ResourceLocation itemIcon, Component title, boolean appendAllEntries) {
        this.name = name;
        this.itemIcon = itemIcon;
        this.title = title;
        this.appendAllEntries = appendAllEntries;
    }

    @Override
    public String toString() {
        return "GuideBook [ " + name + ", title = " + title.getString() + " ]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        return name.equals(((GuideBook) obj).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
