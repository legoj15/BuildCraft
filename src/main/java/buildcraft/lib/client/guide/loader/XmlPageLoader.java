package buildcraft.lib.client.guide.loader;

import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.nbt.TagParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.ChatFormatting;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.guide.GuidePageRegistry;
import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.entry.PageEntry;
import buildcraft.lib.misc.RegistryKeyUtil;
import buildcraft.lib.client.guide.entry.PageValueType;
import buildcraft.lib.client.guide.parts.GuideChapterWithin;
import buildcraft.lib.client.guide.parts.GuideImageFactory;
import buildcraft.lib.client.guide.parts.GuideInlineLinkText;
import buildcraft.lib.client.guide.parts.GuideInlineLinkText.InlineLinkSpan;
import buildcraft.lib.client.guide.parts.GuidePageEntry;
import buildcraft.lib.client.guide.parts.GuidePageFactory;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.GuidePartCodeBlock;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.client.guide.parts.GuidePartGroup;
import buildcraft.lib.client.guide.parts.GuidePartLink;
import buildcraft.lib.client.guide.parts.GuidePartMulti;
import buildcraft.lib.client.guide.parts.GuidePartNewPage;
import buildcraft.lib.client.guide.parts.GuideText;
import buildcraft.lib.client.guide.parts.contents.PageLink;
import buildcraft.lib.client.guide.parts.contents.PageLinkNormal;
import buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache;
import buildcraft.lib.client.guide.parts.recipe.GuideAssemblyRecipes;
import buildcraft.lib.client.guide.parts.recipe.GuideCraftingFactory;
import buildcraft.lib.client.guide.parts.recipe.GuideCraftingRecipes;
import buildcraft.lib.client.guide.parts.recipe.IStackRecipes;
import buildcraft.lib.client.guide.parts.recipe.RecipeLookupHelper;
import buildcraft.lib.client.guide.ref.GuideGroupManager;
import buildcraft.lib.client.guide.ref.GuideGroupSet;
import buildcraft.lib.client.guide.ref.GuideGroupSet.GroupDirection;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.misc.LocaleUtil;

// This isn't a proper XML loader - there isn't a root tag.
// Instead it just assumes everything is a paragraph, unless more specific tags are given
public enum XmlPageLoader implements IPageLoaderText {
    INSTANCE;

    public static final Map<String, SpecialParser> TAG_FACTORIES = new HashMap<>();
    public static final Map<String, MultiPartJoiner> GUIDE_PART_MULTIS = new HashMap<>();

    /** Used to show "in-game" guide information, narrated from the perspective of the player. */
    public static boolean SHOW_LORE = true;

    /** Used to show extra "hints" that the described thing can be used for. */
    public static boolean SHOW_HINTS = false;

    /** Used to show all of the numbers used when calculating various things. */
    public static boolean SHOW_DETAIL = false; // Was BCLibConfig.guideShowDetail

    public static boolean shouldShowDetail() {
        return SHOW_DETAIL;
    }

    private static final class GuideTextFactory implements GuidePartFactory {
        public final String text;

        private GuideTextFactory(String text) {
            this.text = text;
        }

        @Override
        public GuidePart createNew(GuiGuide gui) {
            return new GuideText(gui, text);
        }
    }

    @FunctionalInterface
    public interface SpecialParser {
        List<GuidePartFactory> parse(XmlTag tag, ProfilerFiller prof);
    }

    @FunctionalInterface
    public interface SpecialParserSingle extends SpecialParser {
        @Override
        default List<GuidePartFactory> parse(XmlTag tag, ProfilerFiller prof) {
            GuidePartFactory single = parseSingle(tag, prof);
            if (single == null) return null;
            return ImmutableList.of(single);
        }

        GuidePartFactory parseSingle(XmlTag tag, ProfilerFiller prof);
    }

    @FunctionalInterface
    public interface MultiPartJoiner {
        GuidePartFactory join(XmlTag tag, List<GuidePartFactory> factories, ProfilerFiller prof);
    }

    static {
        // Note that text is done separately, so its not registered here
        putDuelMultiPartType("lore", () -> SHOW_LORE);
        putDuelMultiPartType("detail", () -> shouldShowDetail());
        putDuelMultiPartType("hint", () -> SHOW_HINTS);
        putSingle("new_page", (attr, prof) -> GuidePartNewPage::new);
        putSingle("chapter", XmlPageLoader::loadChapter);
        putSingle("recipe", XmlPageLoader::loadRecipe);
        putSingle("recipe_cycle", XmlPageLoader::loadRecipeCycle);
        putSingle("group", XmlPageLoader::loadGroup);
        putSingle("link", XmlPageLoader::loadLink);
        putMulti("recipes", XmlPageLoader::loadAllRecipes);
        putMulti("usages", XmlPageLoader::loadAllUsages);
        putMulti("recipes_usages", XmlPageLoader::loadAllRecipesAndUsages);
        putSingle("image", XmlPageLoader::loadImage);
        putCode("json_insn");
        putCode("guide_md");
    }

    public static void putDuelMultiPartType(String name, BooleanSupplier isVisible) {
        putSimpleMultiPartType(name, isVisible);
        putSimpleMultiPartType("no_" + name, () -> !isVisible.getAsBoolean());
    }

    public static void putSimpleMultiPartType(String name, BooleanSupplier isVisible) {
        putMultiPartType(name, (tag, factories, prof) -> (gui) -> {
            List<GuidePart> subParts = new ArrayList<>(factories.size());
            for (GuidePartFactory factory : factories) {
                subParts.add(factory.createNew(gui));
            }
            return new GuidePartMulti(gui, subParts, isVisible);
        });
    }

    public static void putCode(String name) {
        putMultiPartType(name, (tag, factories, prof) -> {
            List<String> lines = new ArrayList<>();
            for (GuidePartFactory factory : factories) {
                if (factory instanceof GuideTextFactory) {
                    lines.add(((GuideTextFactory) factory).text);
                } else {

                }
            }
            for (int i = 0; i < lines.size(); i++) {
                String str = lines.get(i);
                if (
                    str.startsWith("~{") && str.endsWith("}") && str.indexOf('{', 2) == -1 && str.indexOf('}') == str
                        .length() - 1
                ) {
                    lines.set(i, ChatFormatting.DARK_PURPLE + str);
                    continue;
                }
                str = str.replace("{", ChatFormatting.DARK_GREEN + "{" + ChatFormatting.RESET);
                str = str.replace("}", ChatFormatting.DARK_GREEN + "}" + ChatFormatting.RESET);
                str = str.replaceAll("\"(.+)\"", ChatFormatting.DARK_BLUE + "$0" + ChatFormatting.RESET);
                str = str.replaceAll("%[0-9]+", ChatFormatting.DARK_PURPLE + "$0" + ChatFormatting.RESET);
                str = str.replaceAll("//", ChatFormatting.DARK_GREEN + "//");
                lines.set(i, str);
            }
            return gui -> new GuidePartCodeBlock(gui, lines);
        });
    }

    public static void putMultiPartType(String name, MultiPartJoiner joiner) {
        GUIDE_PART_MULTIS.put(name, joiner);
    }

    public static void putSingle(String string, SpecialParserSingle parser) {
        putMulti(string, parser);
    }

    public static void putMulti(String string, SpecialParser parser) {
        TAG_FACTORIES.put(string, parser);
    }

    @Override
    public GuidePageFactory loadPage(BufferedReader reader, Identifier name, PageEntry<?> entry, ProfilerFiller prof)
        throws IOException {
        prof.push("xml");
        try {
            return loadPage0(reader, name, entry, prof);
        } finally {
            prof.pop();
        }
    }

    /** Parse a markdown/XML guide source into the list of {@link GuidePartFactory}s it
     *  describes — without wrapping them in a {@link buildcraft.lib.client.guide.parts.GuidePage}.
     *  Useful when the caller assembles a page that mixes loaded markdown content with
     *  programmatically-built parts (e.g. category pages, where the .md supplies the
     *  description but the group listing and back-link are appended in code). The
     *  returned factories can be invoked once per {@link buildcraft.lib.client.guide.GuiGuide}. */
    public static List<GuidePartFactory> loadParts(BufferedReader reader, ProfilerFiller prof) throws IOException {
        prof.push("xml");
        try {
            return parsePartFactories(reader, prof);
        } finally {
            prof.pop();
        }
    }

    private static GuidePageFactory loadPage0(BufferedReader reader, Identifier name, PageEntry<?> entry,
        ProfilerFiller prof) throws IOException, InvalidInputDataException {
        List<GuidePartFactory> factories = parsePartFactories(reader, prof);
        return (gui) -> {
            List<GuidePart> parts = new ArrayList<>();
            for (GuidePartFactory factory : factories) {
                parts.add(factory.createNew(gui));
            }
            return new GuidePageEntry(gui, parts, entry, name);
        };
    }

    private static List<GuidePartFactory> parsePartFactories(BufferedReader reader, ProfilerFiller prof)
        throws IOException, InvalidInputDataException {

        Deque<List<GuidePartFactory>> nestedParts = new ArrayDeque<>();
        Deque<XmlTag> nestedTags = new ArrayDeque<>();
        nestedParts.push(new ArrayList<>());
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("//")) {
                // Ignore comments
                continue;
            }
            if (line.startsWith("\\/\\/")) {
                line = "//" + line.substring(4);
            }
            prof.push("parse_tag");
            XmlTag tag = parseTag(line);
            prof.pop();
            if (tag != null) {
                if (tag.state == XmlTagState.COMPLETE) {
                    SpecialParser parser = TAG_FACTORIES.get(tag.name);
                    if (parser != null) {
                        prof.push("use_" + tag.name);
                        List<GuidePartFactory> factories = parser.parse(tag, prof);
                        prof.pop();
                        if (factories != null) {
                            nestedParts.peek().addAll(factories);
                            line = line.substring(tag.originalString.length());
                        } else {
                            int len = tag.originalString.length();
                            line = "<red>" + line.substring(0, len) + "</red>" + line.substring(len);
                        }
                    }
                } else if (tag.state == XmlTagState.START) {
                    MultiPartJoiner joiner = GUIDE_PART_MULTIS.get(tag.name);
                    if (joiner != null) {
                        nestedTags.push(tag);
                        nestedParts.push(new ArrayList<>());
                        line = line.substring(tag.originalString.length());
                    } else {
                        int len = tag.originalString.length();
                        line = "<red>" + line.substring(0, len) + "</red>" + line.substring(len);
                    }
                } else /* tag.state == XmlTagState.END */ {
                    MultiPartJoiner joiner = GUIDE_PART_MULTIS.get(tag.name);
                    if (joiner != null) {
                        if (nestedTags.isEmpty()) {
                            throw new InvalidInputDataException("Tried to close " + tag.name + " before openining it!");
                        }
                        XmlTag nameTag = nestedTags.pop();
                        if (!tag.name.equals(nameTag.name)) {
                            throw new InvalidInputDataException(
                                "Tried to close " + tag.name + " before instead of " + nameTag.name + "!"
                            );
                        }
                        List<GuidePartFactory> subParts = nestedParts.pop();
                        prof.push("join_" + tag.name);
                        GuidePartFactory joined = joiner.join(nameTag, subParts, prof);
                        prof.pop();
                        if (joined == null) {
                            nestedParts.peek().addAll(subParts);
                            int len = tag.originalString.length();
                            line = "<red>" + line.substring(0, len) + "</red>" + line.substring(len);
                        } else {
                            nestedParts.peek().add(joined);
                            line = line.substring(tag.originalString.length());
                        }
                    }
                }
                if (line.length() == 0) {
                    continue;
                }
            }
            // Last: add remaining elements as text
            if (line.length() == 0) {
                line = " ";
            }
            prof.push("text_format");
            Set<ChatFormatting> formattingElements = EnumSet.noneOf(ChatFormatting.class);
            Deque<ChatFormatting> formatColours = new ArrayDeque<>();
            String completeLine = "";
            // Inline-link spans collected from <link inline="true" .../> tags found
            // mid-line. Empty list = a plain GuideTextFactory is enough; non-empty =
            // emit a GuideInlineLinkText so the embedded titles are clickable.
            List<InlineLinkSpan> inlineLinks = new ArrayList<>();
            int i = 0;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == '<') {
                    XmlTag currentTag = parseTag(line.substring(i));
                    if (currentTag != null) {
                        ChatFormatting formatting = ChatFormatting.getByName(currentTag.name.replace("_", ""));
                        if (formatting != null) {
                            if (currentTag.state == XmlTagState.END) {
                                formattingElements.remove(formatting);
                                if (!formatColours.isEmpty() && formatColours.peek() == formatting) {
                                    formatColours.remove();
                                }
                            } else if (currentTag.state == XmlTagState.START) {
                                if (formatting.isColor()) {
                                    formatColours.push(formatting);
                                } else {
                                    formattingElements.add(formatting);
                                }
                            }
                            completeLine += ChatFormatting.RESET;
                            if (!formatColours.isEmpty() && formatColours.peek() != null) {
                                completeLine += formatColours.peek();
                            }
                            for (ChatFormatting format : formattingElements) {
                                completeLine += format;
                            }
                            i += currentTag.originalString.length();
                            continue;
                        }
                        // <link inline="ns:path"/> (shorthand) or
                        // <link inline="true" to="ns:path"/> (explicit) — embed a
                        // clickable hyperlink inside the current paragraph. The
                        // block-level <link to=".../> path (above, in the tag-dispatch
                        // block) handles the case where this is the only thing on a
                        // source line; this branch handles mid-line use.
                        // NOTE: bare `inline` (without `="..."`) won't reach here — the
                        // attribute parser at parseTag breaks on the first key without
                        // an `=`, also dropping `to=`. Authors must give `inline` a
                        // value.
                        if ("link".equals(currentTag.name) && currentTag.get("inline") != null) {
                            ResolvedLink resolved = resolveLinkTarget(currentTag, prof);
                            if (resolved != null) {
                                int visibleStart = GuideInlineLinkText.visibleLengthOf(completeLine);
                                completeLine += ChatFormatting.RESET;
                                completeLine += ChatFormatting.UNDERLINE;
                                completeLine += ChatFormatting.BLUE;
                                completeLine += resolved.title;
                                completeLine += ChatFormatting.RESET;
                                // Re-emit ambient formatting so surrounding <bold>/colour
                                // resumes after the link's trailing reset. Mirrors the
                                // colour-tag close branch above.
                                if (!formatColours.isEmpty() && formatColours.peek() != null) {
                                    completeLine += formatColours.peek();
                                }
                                for (ChatFormatting format : formattingElements) {
                                    completeLine += format;
                                }
                                inlineLinks.add(new InlineLinkSpan(
                                    visibleStart, resolved.title.length(), resolved.factoryFn, resolved.title
                                ));
                            } else {
                                // Resolver already logged; fall back to literal tag text
                                // so the broken link is visible to the author.
                                completeLine += currentTag.originalString;
                            }
                            i += currentTag.originalString.length();
                            continue;
                        }
                    }
                } else if (line.startsWith("&lt;", i)) {
                    c = '<';
                    i += 3;
                } else if (line.startsWith("&gt;", i)) {
                    c = '>';
                    i += 3;
                }
                completeLine += c;
                i++;
            }

            final String modLine = completeLine;
            if (inlineLinks.isEmpty()) {
                nestedParts.peek().add(new GuideTextFactory(modLine));
            } else {
                final List<InlineLinkSpan> spans = ImmutableList.copyOf(inlineLinks);
                nestedParts.peek().add(gui -> new GuideInlineLinkText(gui, modLine, spans));
            }
            prof.pop();
        }
        List<GuidePartFactory> factories = nestedParts.pop();
        if (nestedParts.size() != 0) {
            throw new InvalidInputDataException("We haven't closed " + nestedTags);
        }
        // Drop any trailing blank-line text parts. Every empty source line becomes a
        // GuideTextFactory(" ") (see the `line = " "` fallback above) that renders as an
        // invisible full-height line; a trailing run of them advances the layout cursor onto
        // a fresh page with nothing visible on it, leaving a phantom blank final page (and an
        // odd page count) in long entries. Interior blank lines are kept — they are real
        // paragraph spacing; only the trailing run is removed.
        while (!factories.isEmpty()) {
            GuidePartFactory last = factories.get(factories.size() - 1);
            if (last instanceof GuideTextFactory text && text.text.isBlank()) {
                factories.remove(factories.size() - 1);
            } else {
                break;
            }
        }
        return factories;
    }

    /** Parses a single tag. Note that the tag might not be the length of the whole string. */
    @Nullable
    public static XmlTag parseTag(String string) throws InvalidInputDataException {
        if (!string.startsWith("<")) {
            return null;
        }

        // Its a tag, hopefully its complete
        int end = string.indexOf('>');
        if (end < 0) {
            throw new InvalidInputDataException("Didn't find an end tag for " + string);
        }
        String tagContents = string.substring(1, end);
        boolean hasStart = tagContents.startsWith("/");
        if (hasStart) {
            tagContents = tagContents.substring(1);
        }
        boolean hasEnd = tagContents.endsWith("/");
        if (hasEnd) {
            tagContents = tagContents.substring(0, tagContents.length() - 1);
        }
        int paramStart = tagContents.indexOf(' ');
        String tag;
        Map<String, String> attributes;
        if (paramStart < 0) {
            tag = tagContents;
            attributes = ImmutableMap.of();
        } else {
            tag = tagContents.substring(0, paramStart);
            attributes = new HashMap<>();
            String attribs = tagContents.substring(paramStart + 1);
            while (attribs.length() > 0) {
                attribs = attribs.trim();
                int index = attribs.indexOf('=');
                if (index < 0) {
                    break;
                }
                String key = attribs.substring(0, index);
                String after = attribs.substring(index + 1);
                // Simplified attribute parsing: handle quoted and unquoted values
                String value;
                int totalLength = index + 1;
                if (after.startsWith("\"")) {
                    int closeQuote = after.indexOf('"', 1);
                    if (closeQuote < 0) {
                        throw new InvalidInputDataException("Not a valid tag value " + after);
                    }
                    value = after.substring(1, closeQuote);
                    totalLength += closeQuote + 1;
                } else {
                    int spaceIdx = after.indexOf(' ');
                    if (spaceIdx < 0) {
                        value = after;
                    } else {
                        value = after.substring(0, spaceIdx);
                    }
                    totalLength += value.length();
                }
                attributes.put(key, value);
                attribs = attribs.substring(totalLength);
            }
        }
        XmlTagState state;
        if (hasEnd) {
            state = XmlTagState.COMPLETE;
        } else if (hasStart) {
            state = XmlTagState.END;
        } else {
            state = XmlTagState.START;
        }
        return new XmlTag(tag, attributes, state, string.substring(0, end + 1));
    }

    public enum XmlTagState {
        /** {@code <tag>} */
        START,
        /** {@code <tag/>} */
        COMPLETE,
        /** {@code </tag>} */
        END;
    }

    public static class XmlTag {
        public final String name;
        public final Map<String, String> attributes;
        public final XmlTagState state;
        public final String originalString;

        public XmlTag(String name, Map<String, String> attributes, XmlTagState state, String originalString) {
            this.name = name;
            this.attributes = attributes;
            this.state = state;
            this.originalString = originalString;
        }

        @Nullable
        public String get(String key) {
            return attributes.get(key);
        }

        @Override
        public String toString() {
            return originalString;
        }
    }

    private static GuidePartFactory loadChapter(XmlTag tag, ProfilerFiller prof) {
        String name = tag.get("name");
        String level = tag.get("level");
        if (name == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Found a chapter tag without a name!" + tag);
            return null;
        }
        if (level == null) {
            level = "0";
        }
        try {
            int intLevel = Integer.parseInt(level);
            return chapter(name, intLevel);
        } catch (NumberFormatException nfe) {
            String str = "\u00a74" + tag.originalString + "\u00a7r";
            str = str.replace(level, "\u00a7c" + level + "\u00a74");
            return new GuideTextFactory(str);
        }
    }

    private static GuidePartFactory loadLink(XmlTag tag, ProfilerFiller prof) {
        // <link inline="true" to="..."/> at the start of a line: produce the same
        // icon-less hyperlink visual as the mid-line case, so authors get consistent
        // styling regardless of where the directive sits.
        if (tag.get("inline") != null) {
            ResolvedLink resolved = resolveLinkTarget(tag, prof);
            if (resolved == null) {
                return null;
            }
            String formatted = "" + ChatFormatting.RESET + ChatFormatting.UNDERLINE + ChatFormatting.BLUE
                + resolved.title + ChatFormatting.RESET;
            List<InlineLinkSpan> spans = ImmutableList.of(
                new InlineLinkSpan(0, resolved.title.length(), resolved.factoryFn, resolved.title)
            );
            return gui -> new GuideInlineLinkText(gui, formatted, spans);
        }
        String to = tag.get("to");
        String type = tag.get("type");
        if (to == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Found a link tag without a 'to' tag! " + tag);
            return null;
        }
        final PageLink link;
        if (type == null) {
            Identifier location = Identifier.parse(to);
            PageEntry<?> entry = (PageEntry<?>) GuidePageRegistry.INSTANCE.getReloadableEntryMap().get(location);
            if (entry == null) {
                // Programmatic "category" entries (filler patterns, emzuli extraction
                // presets, ...) live in GuideManager#categoryLinks rather than the
                // reloadable entry map, and that map is populated AFTER this parser
                // runs (in generateContentsPage). Defer the lookup to render time so
                // the link resolves once the contents page has been built — which is
                // always before the user can navigate to a markdown page that contains
                // the link.
                return gui -> {
                    PageLink categoryLink = GuideManager.INSTANCE.getCategoryLink(location);
                    if (categoryLink == null) {
                        BCLog.logger.warn("[lib.guide.loader.xml] Found a link tag to an unknown page! " + tag);
                        return null;
                    }
                    return new GuidePartLink(gui, categoryLink);
                };
            }
            String translatedTitle = entry.title;
            ISimpleDrawable icon = entry.createDrawable();
            PageLine line = new PageLine(icon, icon, 2, translatedTitle, true);

            link = new PageLinkNormal(line, true, entry.getTooltip(), gui -> {
                GuidePageFactory factory = GuideManager.INSTANCE.getFactoryFor(location);
                return factory == null ? null : factory.createNew(gui);
            });
        } else {
            PageValueType<?> valueType = GuidePageRegistry.INSTANCE.types.get(type);
            if (valueType != null) {
                @SuppressWarnings("unchecked")
                OptionallyDisabled<PageLink> linkq = (OptionallyDisabled<PageLink>) (OptionallyDisabled<?>) valueType.createLink(to, prof);
                if (linkq.isPresent()) {
                    link = linkq.get();
                } else {
                    BCLog.logger.warn(
                        "[lib.guide.loader.xml] Found a link tag that didn't link to anything valid: " + linkq
                            .getDisabledReason() + " " + tag
                    );
                    return null;
                }
            } else {
                BCLog.logger.warn(
                    "[lib.guide.loader.xml] Found a link tag with an unknown 'type'! (valid ones are "
                        + GuidePageRegistry.INSTANCE.types.keySet() + ") " + tag
                );
                return null;
            }
        }
        return gui -> new GuidePartLink(gui, link);
    }

    /** Holds the parse-time outputs of resolving a {@code <link to=".../>} directive
     *  for inline use: a title (used as the visible text) and a factory function that,
     *  given a {@link GuiGuide}, produces the {@link GuidePageFactory} to navigate to.
     *  The function form preserves the deferred-resolution pattern that
     *  {@link #loadLink}'s category-link branch needs (categoryLinks aren't populated
     *  until after this parser runs). */
    private static final class ResolvedLink {
        final String title;
        final Function<GuiGuide, GuidePageFactory> factoryFn;

        ResolvedLink(String title, Function<GuiGuide, GuidePageFactory> factoryFn) {
            this.title = title;
            this.factoryFn = factoryFn;
        }
    }

    /** Resolves a {@code <link>} tag to its title and a factory-function for inline
     *  use. Returns null and logs a warning if the target can't be resolved.
     *
     *  Accepts two equivalent author syntaxes:
     *  <ul>
     *    <li>{@code <link inline="true" to="ns:path"/>} — explicit flag + target</li>
     *    <li>{@code <link inline="ns:path"/>} — shorthand, the {@code inline} value
     *        IS the target (only kicks in when {@code to} is absent and the inline
     *        value isn't the literal string {@code "true"} acting as a flag)</li>
     *  </ul>
     */
    @Nullable
    private static ResolvedLink resolveLinkTarget(XmlTag tag, ProfilerFiller prof) {
        String to = tag.get("to");
        String type = tag.get("type");
        // Shorthand: <link inline="ns:path"/> with no `to` attribute. Promote the
        // inline value to act as the target. Excludes "true" (flag form) and the
        // empty string (probably a typo, let it fall through to the warning below).
        if (to == null) {
            String inline = tag.get("inline");
            if (inline != null && !inline.isEmpty() && !"true".equals(inline)) {
                to = inline;
            }
        }
        if (to == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Found a link tag without a 'to' tag! " + tag);
            return null;
        }
        if (type == null) {
            Identifier location = Identifier.parse(to);
            PageEntry<?> entry = (PageEntry<?>) GuidePageRegistry.INSTANCE.getReloadableEntryMap().get(location);
            if (entry != null) {
                return new ResolvedLink(
                    entry.title,
                    gui -> GuideManager.INSTANCE.getFactoryFor(location)
                );
            }
            // Deferred categoryLink: the title isn't known at parse time (categoryLinks
            // are populated after this parser runs). Use the raw `to` as a placeholder
            // title so the link still renders and clicks dispatch correctly via the
            // deferred lookup. Better than dropping the link entirely.
            return new ResolvedLink(to, gui -> {
                PageLink categoryLink = GuideManager.INSTANCE.getCategoryLink(location);
                if (categoryLink == null) {
                    BCLog.logger.warn("[lib.guide.loader.xml] Found a link tag to an unknown page! " + tag);
                    return null;
                }
                return categoryLink.getFactoryLink();
            });
        }
        PageValueType<?> valueType = GuidePageRegistry.INSTANCE.types.get(type);
        if (valueType == null) {
            BCLog.logger.warn(
                "[lib.guide.loader.xml] Found a link tag with an unknown 'type'! (valid ones are "
                    + GuidePageRegistry.INSTANCE.types.keySet() + ") " + tag
            );
            return null;
        }
        @SuppressWarnings("unchecked")
        OptionallyDisabled<PageLink> linkq = (OptionallyDisabled<PageLink>) (OptionallyDisabled<?>) valueType.createLink(to, prof);
        if (!linkq.isPresent()) {
            BCLog.logger.warn(
                "[lib.guide.loader.xml] Found a link tag that didn't link to anything valid: " + linkq
                    .getDisabledReason() + " " + tag
            );
            return null;
        }
        PageLink resolved = linkq.get();
        return new ResolvedLink(resolved.text.text, gui -> resolved.getFactoryLink());
    }

    private static GuidePartFactory loadImage(XmlTag tag, ProfilerFiller prof) {
        String src = tag.get("src");
        if (src == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Found an image tag without an src!" + tag);
            return null;
        }
        int width = parseInt("width", -1, tag);
        int height = parseInt("height", -1, tag);
        return new GuideImageFactory(src, width, height);
    }

    private static int parseInt(String name, int _default, XmlTag tag) {
        String value = tag.get(name);
        if (value == null) {
            return _default;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            BCLog.logger.warn(
                "[lib.guide.loader.xml] Found an invalid number for image tag (" + name + ") " + tag + nfe.getMessage()
            );
            return _default;
        }
    }

    private static GuidePartFactory loadRecipe(XmlTag tag, ProfilerFiller prof) {
        // `id` attribute: display one specific recipe looked up by its registry id. With
        // type="assembling" the id is an Assembly Table recipe's registry name (e.g.
        // "gate-and-IRON-NO_MODIFIER"); otherwise it's a crafting recipe's ResourceLocation
        // (e.g. "buildcraftunofficial:gate_basic"). Needed for outputs whose data components
        // the stack-based lookup can't address — every gate crafting recipe outputs a
        // component-bearing PLUG_GATE, so the bare <recipes stack="…:plug_gate"/> query never
        // matches them (ChangingItemStack compares outputs with isSameItemSameComponents and
        // the page stack carries no `gate` components); and the assembly stack lookup matches
        // by item only, so it can't single out one variant's recipe. Pinning by id sidesteps
        // both, and lets a page hand-author the crafting→assembly order per gate.
        String recipeId = tag.get("id");
        if (recipeId != null) {
            return loadRecipeById(recipeId, tag.get("type"));
        }
        ItemStack stack = loadItemStack(tag);
        if (stack == null) {
            return null;
        }
        String type = tag.get("type");
        if (type != null) {
            IStackRecipes recipes = RecipeLookupHelper.handlerTypes.get(type);
            if (recipes == null) {
                BCLog.logger.warn(
                    "[lib.guide.loader.xml] Unknown recipe type " + type + " - must be one of "
                        + RecipeLookupHelper.handlerTypes.keySet()
                );
            } else {
                List<GuidePartFactory> list = recipes.getRecipes(stack);
                if (list.size() > 0) {
                    return list.get(0);
                }
            }
        }
        List<GuidePartFactory> list = RecipeLookupHelper.getAllRecipes(stack);
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

    /** Look up a single crafting recipe by its registry id and build a display factory for it.
     *  Returns null (→ the tag renders as red error text) if the id is malformed, no recipe
     *  data is available yet (outside a world, before the server's recipe sync arrived), no
     *  recipe has that id, or the recipe isn't a crafting recipe. */
    @Nullable
    private static GuidePartFactory loadRecipeById(String id, @Nullable String type) {
        // type="assembling" → resolve the id as an Assembly Table recipe by its registry name;
        // any other type (or none) treats the id as a crafting recipe's ResourceLocation.
        if ("assembling".equals(type)) {
            GuidePartFactory factory = GuideAssemblyRecipes.getFactoryByName(id.trim());
            if (factory == null) {
                BCLog.logger.warn("[lib.guide.loader.xml] No assembly recipe found with name " + id);
            }
            return factory;
        }
        Identifier rl = Identifier.tryParse(id.trim());
        if (rl == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] " + id + " is not a valid recipe id!");
            return null;
        }
        for (RecipeHolder<?> holder : ClientGuideRecipeCache.getAllRecipeHolders()) {
            if (RegistryKeyUtil.id(holder.id()).equals(rl)) {
                if (holder.value() instanceof CraftingRecipe crafting) {
                    return GuideCraftingFactory.getFactory(crafting);
                }
                BCLog.logger.warn("[lib.guide.loader.xml] Recipe " + id + " is not a crafting recipe!");
                return null;
            }
        }
        BCLog.logger.warn("[lib.guide.loader.xml] No recipe found with id " + id);
        return null;
    }

    /** {@code <recipe_cycle match="substr" [type="assembling"]/>} — fold every recipe whose id (or, with
     *  {@code type="assembling"}, registry name) contains {@code match} into a single panel that cycles
     *  through them in lock-step. Collapses a family of near-identical recipes — a gate's AND/OR variants,
     *  or the 12 swap recipes in one direction — into one compact, animated entry instead of many panels. */
    private static GuidePartFactory loadRecipeCycle(XmlTag tag, ProfilerFiller prof) {
        String match = tag.get("match");
        if (match == null || match.isEmpty()) {
            BCLog.logger.warn("[lib.guide.loader.xml] <recipe_cycle> needs a non-empty `match`"
                + " (a recipe id/name substring): " + tag);
            return null;
        }
        boolean assembling = "assembling".equals(tag.get("type"));
        GuidePartFactory factory = assembling
            ? GuideAssemblyRecipes.getCyclingFactoryByNameMatch(match)
            : GuideCraftingRecipes.getCyclingFactoryByIdMatch(match);
        if (factory == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] <recipe_cycle> matched no "
                + (assembling ? "assembly" : "crafting") + " recipes for match=\"" + match + "\"");
        }
        return factory;
    }

    private static List<GuidePartFactory> loadAllRecipes(XmlTag tag, ProfilerFiller prof) {
        ItemStack stack = loadItemStack(tag);
        if (stack == null) {
            return null;
        }
        return RecipeLookupHelper.getAllRecipes(stack);
    }

    private static List<GuidePartFactory> loadAllUsages(XmlTag tag, ProfilerFiller prof) {
        ItemStack stack = loadItemStack(tag);
        if (stack == null) {
            return null;
        }
        return RecipeLookupHelper.getAllUsages(stack);
    }

    private static List<GuidePartFactory> loadAllRecipesAndUsages(XmlTag tag, ProfilerFiller prof) {
        ItemStack stack = loadItemStack(tag);
        if (stack == null) {
            return null;
        }
        String chapterLevelStr = tag.get("chapter_level");
        int chapterLevel = 0;
        if (chapterLevelStr != null) {
            try {
                chapterLevel = Integer.parseInt(chapterLevelStr);
            } catch (NumberFormatException nfe) {
                String str = "\u00a74" + tag.originalString + "\u00a7r";
                str = str.replace(chapterLevelStr, "\u00a7c" + chapterLevelStr + "\u00a74");
                return Collections.singletonList(new GuideTextFactory(str));
            }
        }
        return loadAllCrafting(stack, chapterLevel);
    }

    public static List<GuidePartFactory> loadAllCrafting(@Nonnull ItemStack stack, int chapterLevel) {
        List<GuidePartFactory> list = new ArrayList<>();
        List<GuidePartFactory> recipeParts = RecipeLookupHelper.getAllRecipes(stack);
        if (recipeParts.size() > 0) {
            // Soft break: gives recipes a clean spread when the body text leaves enough room
            // for a heading, but skips the advance when the cursor is already at or near the
            // top of a fresh page (so we don't strand a blank page between the body and the
            // recipes when the body happens to fill its page exactly).
            list.add(gui -> new GuidePartNewPage(gui, RECIPE_BREAK_THRESHOLD));
            if (recipeParts.size() == 1) {
                list.add(chapter("buildcraft.guide.recipe.create", chapterLevel));
            } else {
                list.add(chapter("buildcraft.guide.recipe.create.plural", chapterLevel));
            }
            list.addAll(recipeParts);
        }
        List<GuidePartFactory> usageParts = RecipeLookupHelper.getAllUsages(stack);
        // Ensure we don't have any duplicate recipes
        usageParts.removeAll(recipeParts);
        if (usageParts.size() > 0) {
            if (recipeParts.size() != 1) {
                list.add(gui -> new GuidePartNewPage(gui, RECIPE_BREAK_THRESHOLD));
            }
            if (usageParts.size() == 1) {
                list.add(chapter("buildcraft.guide.recipe.use", chapterLevel));
            } else {
                list.add(chapter("buildcraft.guide.recipe.use.plural", chapterLevel));
            }
            list.addAll(usageParts);
        }
        return list;
    }

    /** Below this cursor-pixel offset, programmatic page-break parts (recipe/use/group
     *  breathing-room breaks) skip the advance. Tuned to ~2 lines of text — small
     *  enough that "the page has content" feels true, large enough to suppress
     *  forced breaks that would strand a blank page after a body that exactly
     *  filled its own page. The {@code <new_page/>} markdown tag bypasses this
     *  threshold (author-requested breaks always fire). */
    public static final int RECIPE_BREAK_THRESHOLD = 30;

    public static GuidePartFactory chapter(String after) {
        return chapter(after, 0);
    }

    public static GuidePartFactory chapter(String after, int level) {
        return (gui) -> new GuideChapterWithin(gui, level, LocaleUtil.localize(after));
    }

    public static GuidePartFactory translate(String text) {
        return gui -> new GuideText(gui, new PageLine(0, LocaleUtil.localize(text), false));
    }

    public static GuidePartFactory loadGroup(XmlTag tag, ProfilerFiller prof) {
        String domain = tag.get("domain");
        String group = tag.get("group");
        if (domain == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Missing domain tag in " + tag);
        }
        if (group == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Missing group tag in " + tag);
        }
        if (domain == null || group == null) {
            return null;
        }
        GuideGroupSet set = GuideGroupManager.get(domain, group);
        if (set == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Unknown group " + domain + ":" + group);
            return null;
        }
        // Optional `direction` attribute: "to" (default) lists the group's entries — the
        // things this page links TO. "from" lists the group's sources — the things that
        // link FROM other pages into this one. Lets markdown override the default direction
        // and ensures any explicit declaration suppresses the auto-emitted Linked From/To
        // chapter for the same group (de-dup is by group identity, not direction).
        String dirAttr = tag.get("direction");
        GroupDirection direction = "from".equalsIgnoreCase(dirAttr)
            ? GroupDirection.ENTRY_TO_SRC
            : GroupDirection.SRC_TO_ENTRY;
        return gui -> new GuidePartGroup(gui, set, direction);
    }

    public static ItemStack loadItemStack(XmlTag tag) {
        String id = tag.get("stack");
        String count = tag.get("count");
        String nbt = tag.get("nbt");
        if (id == null) {
            BCLog.logger.warn("[lib.guide.loader.xml] Missing 'stack' for an itemstack from " + tag);
            return null;
        }
        Identifier itemId = Identifier.parse(id.trim());
        Optional<Item> optionalItem = BuiltInRegistries.ITEM.getOptional(itemId);
        if (optionalItem.isEmpty()) {
            BCLog.logger.warn("[lib.guide.loader.xml] " + id + " was not a valid item!");
            return null;
        }
        ItemStack stack = new ItemStack(optionalItem.get());

        if (count != null) {
            int stackSize = 1;
            try {
                stackSize = Integer.parseInt(count.trim());
            } catch (NumberFormatException nfe) {
                BCLog.logger.warn("[lib.guide.loader.xml] " + count + " was not a valid number: " + nfe.getMessage());
            }
            stack.setCount(stackSize);
        }

        // "data" attribute (metadata) ignored — metadata does not exist in 1.21

        if (nbt != null) {
            // In 1.21, item components replace NBT on stacks.
            // Legacy NBT parsing is not supported. Log and ignore.
            BCLog.logger.info("[lib.guide.loader.xml] NBT attribute on item stacks is not supported in 1.21, ignoring: " + nbt);
        }
        return stack;
    }
}
