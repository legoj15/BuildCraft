package buildcraft.lib.client.guide.entry;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.util.profiling.ProfilerFiller;

import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;
import buildcraft.api.statements.IAction;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.ITrigger;
import buildcraft.api.statements.StatementManager;

import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.gui.ISimpleDrawable;

public class PageEntryStatement extends PageValueType<IStatement> {

    public static final PageEntryStatement INSTANCE = new PageEntryStatement();

    private static final JsonTypeTags TRIGGER_TAGS = new JsonTypeTags("buildcraft.guide.contents.triggers");
    private static final JsonTypeTags ACTION_TAGS = new JsonTypeTags("buildcraft.guide.contents.actions");

    @Override
    public Class<IStatement> getEntryClass() {
        return IStatement.class;
    }

    @Override
    public void iterateAllDefault(IEntryLinkConsumer consumer, ProfilerFiller prof) {
        // Deferred — needs GuideManager.objectsAdded and PageLinkStatement
        // Will iterate all registered statements when the contents system is ported
    }

    @Override
    public OptionallyDisabled<PageEntry<IStatement>> deserialize(Identifier name, JsonObject json,
        JsonDeserializationContext ctx) {
        if (!json.has("statement")) {
            throw new JsonSyntaxException("Missing 'statement' field in " + json);
        }
        String stmntName = json.get("statement").getAsString();
        IStatement stmnt = StatementManager.statements.get(stmntName);
        if (stmnt == null) {
            throw new JsonSyntaxException("Unknown statement '" + stmntName + "'");
        }
        return new OptionallyDisabled<>(new PageEntry<>(this, name, json, stmnt));
    }

    @Override
    public List<String> getTooltip(IStatement value) {
        return value.getTooltip();
    }

    @Override
    public String getTitle(IStatement value) {
        List<String> tooltip = value.getTooltip();
        if (tooltip.isEmpty()) {
            return value.getClass().toString();
        } else {
            return tooltip.get(0);
        }
    }

    @Override
    @Nullable
    public ISimpleDrawable createDrawable(IStatement value) {
        // Deferred — GuiElementStatementSource rendering requires the statement GUI system
        return null;
    }
}
