/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.profiling.ProfilerFiller;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;
import buildcraft.api.registry.EventBuildCraftReload;
import buildcraft.api.statements.IStatement;

import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.client.guide.entry.IEntryLinkConsumer;
import buildcraft.lib.client.guide.entry.ItemStackValueFilter;
import buildcraft.lib.client.guide.entry.PageEntry;
import buildcraft.lib.client.guide.entry.PageEntryExternal;
import buildcraft.lib.client.guide.entry.PageValue;
import buildcraft.lib.client.guide.entry.PageValueType;
import buildcraft.lib.client.guide.loader.IPageLoader;
import buildcraft.lib.client.guide.loader.MarkdownPageLoader;
import buildcraft.lib.client.guide.loader.XmlPageLoader;
import buildcraft.lib.client.guide.parts.GuidePage;
import buildcraft.lib.client.guide.parts.GuidePageFactory;
import buildcraft.lib.client.guide.parts.GuidePageStandInRecipes;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.client.guide.parts.GuidePartGroup;
import buildcraft.lib.client.guide.parts.GuidePartLink;
import buildcraft.lib.client.guide.parts.GuideText;
import buildcraft.lib.client.guide.parts.contents.ContentsNode;
import buildcraft.lib.client.guide.parts.contents.ContentsNodeGui;
import buildcraft.lib.client.guide.parts.contents.GuidePageContents;
import buildcraft.lib.client.guide.parts.contents.IContentsNode;
import buildcraft.lib.client.guide.parts.contents.PageLink;
import buildcraft.lib.client.guide.parts.contents.PageLinkItemStack;
import buildcraft.lib.client.guide.parts.contents.PageLinkNormal;
import buildcraft.lib.client.guide.ref.GuideGroupManager;
import buildcraft.lib.client.guide.ref.GuideGroupSet;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.statement.GuiElementStatementSource;
import buildcraft.lib.guide.GuideBook;
import buildcraft.lib.guide.GuideBookRegistry;
import buildcraft.lib.guide.GuideContentsData;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.search.ISuffixArray;
import buildcraft.lib.misc.search.SimpleSuffixArray;

@SuppressWarnings("deprecation")
public enum GuideManager {
    INSTANCE;

    public static final String DEFAULT_LANG = "en_us";
    public static final Map<String, IPageLoader> PAGE_LOADERS = new HashMap<>();
    public static final GuideContentsData BOOK_ALL_DATA = new GuideContentsData(null);
    public static final boolean DEBUG = BCDebugging.shouldDebugLog("lib.guide.loader");

    private final List<PageEntry<?>> entries = new ArrayList<>();

    private final Map<Identifier, GuidePageFactory> pages = new HashMap<>();
    private final Map<ItemStack, GuidePageFactory> generatedPages = new HashMap<>();

    public ISuffixArray<PageLink> quickSearcher;
    private final Set<PageLink> pageLinksAdded = new HashSet<>();
    private final Map<GuideBook, Map<TypeOrder, ContentsNode>> contents = new HashMap<>();
    /** Page links for programmatic "category" entries (e.g. "Filler Patterns", "Emzuli
     *  Extraction Presets") — those entries don't live in {@link GuidePageRegistry}'s
     *  reloadable map but still need to be linkable from markdown via the
     *  {@code <link to="..."/>} tag. {@link buildcraft.lib.client.guide.loader.XmlPageLoader#loadLink}
     *  consults this map at render time after failing to find a regular page entry.
     *  Cleared at the start of every {@link #generateContentsPage()} so reloads stay
     *  coherent. Keyed by an {@link Identifier} of the form {@code domain:group_name}
     *  matching the {@link GuideGroupManager} group identifier so authors can use the
     *  same id from markdown that they'd use for a {@code <group>} tag. */
    private final Map<Identifier, PageLink> categoryLinks = new HashMap<>();

    /** Cached body parts (description text + any inline tags) parsed from each category's
     *  .md file. Populated during {@link #reload0} after {@code loadLangInternal}. The
     *  category-page builder consumes these factories to materialize the description on
     *  page open, then appends programmatic extras (group listing, back-link). Keyed
     *  the same way as {@link #categoryLinks}. */
    private final Map<Identifier, List<GuidePartFactory>> categoryBodies = new HashMap<>();

    /** {@link buildcraft.api.statements.IStatement}s that have been folded into a
     *  category-collapsed TOC entry (filler patterns, Emzuli extraction presets, ...)
     *  and so should be suppressed from the default leaf-iteration in
     *  {@link buildcraft.lib.client.guide.entry.PageEntryStatement#iterateAllDefault}.
     *  Populated by walking the groups named in {@link #CATEGORY_BODY_SOURCES} —
     *  data-driven, so adding the next category needs no PageEntryStatement edit. */
    private final Set<buildcraft.api.statements.IStatement> hiddenStatements = new HashSet<>();

    public final Set<Object> objectsAdded = new HashSet<>();

    private boolean isInReload = false;

    // Bumped at the end of every successful reload0(). GuiGuide polls this in tick()
    // and rebuilds its open pages via GuidePageBase.createReloaded() when it changes,
    // so an open Guide Book stays usable after /reload or datapack sync rather than
    // rendering against the stale ContentsNode / GuidePageFactory references the reload
    // just orphaned. volatile because reads happen on the client thread; writes happen
    // wherever reload() is dispatched from.
    private volatile int reloadGeneration = 0;

    public int getReloadGeneration() {
        return reloadGeneration;
    }

    /** Look up the {@link PageLink} for a programmatic "category" entry (e.g.
     *  {@code buildcraft:filler_patterns}). Returns {@code null} if no category is
     *  registered under that id — callers should fall back to whatever they were
     *  doing before. */
    @Nullable
    public PageLink getCategoryLink(Identifier id) {
        return categoryLinks.get(id);
    }

    /** True iff {@code statement} has been folded into a category-collapsed TOC entry
     *  (e.g. filler patterns, Emzuli extraction presets) and should NOT be emitted as
     *  its own leaf entry in the auto-iterated index. Consulted by
     *  {@link buildcraft.lib.client.guide.entry.PageEntryStatement#iterateAllDefault}. */
    public boolean isStatementHiddenByCategory(buildcraft.api.statements.IStatement statement) {
        return hiddenStatements.contains(statement);
    }

    static {
        PAGE_LOADERS.put("md", MarkdownPageLoader.INSTANCE);
    }

    public void onRegistryReload(EventBuildCraftReload.FinishLoad event) {
        if (isInReload) return;
        if (event.manager.isLoadingAll()) return;
        if (event.reloadingRegistries.contains(GuideBookRegistry.INSTANCE)) {
            reload();
        }
    }

    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Skip if we haven't loaded yet. The very first resource-manager reload fires
        // during Minecraft construction — before BC item Holders have their components
        // bound — and populateDefaultGroups()'s ItemStack constructions throw
        // "Components not bound yet" NPEs at that stage. The first real load happens
        // through ensureLoaded() when the user opens a guide book (by which point
        // FMLLoadCompleteEvent has fired and components are bound), and after that
        // contents is non-empty so subsequent F3+T reloads proceed normally. The
        // contents.isEmpty() check is the same predicate ensureLoaded() uses, so
        // "already loaded" and "ready to reload" are the same condition.
        if (contents.isEmpty()) return;
        reload(resourceManager);
    }

    public void reload() {
        reload(Minecraft.getInstance().getResourceManager());
    }

    /** Trigger a reload only if the contents tree hasn't been built yet. Belt-and-suspenders
     * for the guide-book entry path: callers can invoke this before opening a guide screen
     * to guarantee the registry is populated, even if the resource-manager listener didn't
     * fire (e.g. tests, dev environments, listener registration regressions). */
    public void ensureLoaded() {
        if (contents.isEmpty()) {
            reload();
        }
    }

    private void reload(ResourceManager resourceManager) {
        if (isInReload) {
            throw new IllegalStateException("Cannot reload while we are reloading!");
        }
        try {
            isInReload = true;
            reload0(resourceManager);
        } finally {
            isInReload = false;
        }
    }

    private void reload0(ResourceManager resourceManager) {
        Stopwatch watch = Stopwatch.createStarted();

        GuideBookRegistry.INSTANCE.reload();
        GuidePageRegistry.INSTANCE.reload();

        entries.clear();
        GuidePageRegistry manager = GuidePageRegistry.INSTANCE;
        Map<GuideBook, Set<String>> domains = new HashMap<>();
        domains.put(null, new HashSet<>());
        for (GuideBook book : GuideBookRegistry.INSTANCE.getAllEntries()) {
            domains.put(book, new HashSet<>());
        }

        // GuideCraftingRecipes not yet ported — skip recipe indexing

        for (PageEntry<?> entry : manager.getAllEntries()) {
            domains.get(null).add(entry.typeTags.domain);
            GuideBook book = GuideBookRegistry.INSTANCE.getBook(entry.book.toString());
            Set<String> domainSet = domains.get(book);
            if (domainSet != null && book != null) {
                domainSet.add(entry.typeTags.domain);
            }
            entries.add(entry);
        }

        BOOK_ALL_DATA.generate(domains.get(null));
        for (Entry<GuideBook, Set<String>> entry : domains.entrySet()) {
            if (entry.getKey() == null) continue;
            entry.getKey().data.generate(entry.getValue());
        }
        pages.clear();

        String currentLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        String langCode;
        if (currentLanguage == null) {
            BCLog.logger.warn("Current language was null!");
            langCode = DEFAULT_LANG;
        } else {
            langCode = currentLanguage;
        }

        loadLangInternal(resourceManager, DEFAULT_LANG);
        if (!DEFAULT_LANG.equals(langCode)) {
            loadLangInternal(resourceManager, langCode);
        }

        // Populate guide-group memberships now that BC modules are loaded and the
        // fuel/coolant/refinery registries are stable. Must happen before
        // generateContentsPage() so any group-derived contents can resolve correctly.
        GuideGroupManager.populateDefaultGroups();

        // Read the category-page bodies from disk while ResourceManager is still in
        // hand. The .md files are parsed into GuidePartFactory lists that the
        // category-page builder will materialize on each page open.
        loadCategoryBodies(resourceManager, langCode);

        generateContentsPage();

        watch.stop();
        long time = watch.elapsed(TimeUnit.MICROSECONDS);
        int p = entries.size();
        int a = pages.size();
        int e = p - a;
        BCLog.logger.info(
            "[lib.guide] Loaded " + p + " possible and " + a + " actual guide pages (" + e + " not found) in "
                + time / 1000 + "ms."
        );

        // Last statement so a thrown reload leaves the counter unchanged — the open
        // GuiGuide keeps using its previous (still-coherent) state instead of thrashing
        // against a half-built registry.
        reloadGeneration++;
    }

    @SuppressWarnings("unchecked")
    private void loadLangInternal(ResourceManager resourceManager, String lang) {
        main_iteration:
        for (Entry<Object, PageEntry<?>> mapEntry : GuidePageRegistry.INSTANCE
            .getReloadableEntryMap().entrySet()) {
            Identifier entryKey = (Identifier) mapEntry.getKey();
            String domain = entryKey.getNamespace();
            String path = "compat/buildcraft/guide/" + lang + "/" + entryKey.getPath();

            for (Entry<String, IPageLoader> entry : PAGE_LOADERS.entrySet()) {
                Identifier fLoc = Identifier.fromNamespaceAndPath(domain, path + "." + entry.getKey());

                try {
                    var resource = resourceManager.getResource(fLoc);
                    if (resource.isPresent()) {
                        // Read the resource bytes once. An empty / whitespace-only file is
                        // treated as if missing — the loader returns a non-null but empty
                        // factory which produces a page with no content, and that empty
                        // page silently disappears from the TOC. Routing empty files to
                        // the stub generator below makes them render as "(WIP)" placeholders
                        // exactly like genuinely-missing files do.
                        byte[] bytes;
                        try (InputStream stream = resource.get().open()) {
                            bytes = stream.readAllBytes();
                        }
                        if (bytes.length == 0 || new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim().isEmpty()) {
                            if (GuideManager.DEBUG) {
                                BCLog.logger.info("[lib.guide.loader] Empty page '" + entryKey + "' — using stub.");
                            }
                            break;
                        }
                        try (InputStream stream = new java.io.ByteArrayInputStream(bytes)) {
                            GuidePageFactory factory = entry.getValue().loadPage(
                                stream, entryKey, mapEntry.getValue(),
                                net.minecraft.util.profiling.InactiveProfiler.INSTANCE
                            );
                            pages.put(entryKey, factory);
                            if (GuideManager.DEBUG) {
                                BCLog.logger.info("[lib.guide.loader] Loaded page '" + entryKey + "'.");
                            }
                            continue main_iteration;
                        }
                    }
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }

            if (pages.containsKey(entryKey)) continue;

            // Fallback: synthesize a stub page so every registered entry still shows
            // in the TOC, even without a matching .md file on disk. This keeps the
            // guide book working as we port content incrementally — missing pages
            // render as a WIP placeholder rather than disappearing from the index.
            try {
                PageEntry<?> stubEntry = mapEntry.getValue();
                String title = stubEntry.title != null ? stubEntry.title : entryKey.getPath();
                String stubContent =
                    "<chapter name=\"" + title + " (WIP)\"/>\n"
                        + "This guide book entry is a placeholder and has not been written yet.\n";
                try (BufferedReader stubReader = new BufferedReader(new StringReader(stubContent))) {
                    GuidePageFactory factory = XmlPageLoader.INSTANCE.loadPage(
                        stubReader, entryKey, stubEntry,
                        net.minecraft.util.profiling.InactiveProfiler.INSTANCE
                    );
                    pages.put(entryKey, factory);
                }
                if (GuideManager.DEBUG) {
                    BCLog.logger.info("[lib.guide.loader] Generated stub page for '" + entryKey + "'.");
                }
            } catch (IOException io) {
                io.printStackTrace();
                String endings;
                if (PAGE_LOADERS.size() == 1) {
                    endings = PAGE_LOADERS.keySet().iterator().next();
                } else {
                    endings = PAGE_LOADERS.keySet().toString();
                }
                BCLog.logger.warn(
                    "[lib.guide.loader] Unable to load guide page '" + entryKey + "' (full path = '" + domain + ":"
                        + path + "." + endings + "') and stub synthesis failed!"
                );
            }
        }
    }

    private void generateContentsPage() {
        objectsAdded.clear();
        contents.clear();
        // Pre-compute the set of statements that category entries will absorb, so the
        // iterateAllDefault sweep below sees up-to-date membership and skips those leaves.
        // Has to happen BEFORE the PageValueType iteration loop, not inside addCategoryEntries
        // (which runs after) — otherwise leaves are added before the category replaces them.
        populateHiddenStatements();
        genTypeMap(null);
        for (GuideBook book : GuideBookRegistry.INSTANCE.getAllEntries()) {
            genTypeMap(book);
        }
        quickSearcher = new SimpleSuffixArray<>();
        pageLinksAdded.clear();

        for (Entry<Object, PageEntry<?>> mapEntry : GuidePageRegistry.INSTANCE.getReloadableEntryMap()
            .entrySet()) {
            Identifier partialLocation = (Identifier) mapEntry.getKey();
            GuidePageFactory entryFactory = GuideManager.INSTANCE.getFactoryFor(partialLocation);

            PageEntry<?> entry = mapEntry.getValue();

            // Entries whose value has been folded into a category-collapsed TOC entry
            // (e.g. the JSON-INSN registration of "Red Extraction Preset" via guide.txt —
            // the Emzuli Extraction Presets category surfaces it instead) get hidden in
            // the default TOC but stay searchable: emit them as startVisible=false so the
            // suffix array still indexes them and a search match reveals them under their
            // natural chapter. The factory remains registered so click-resolution from
            // the category page (GuideManager#getFactoryFor → getEntryFor) still opens
            // the stub/markdown page.
            Object basicValue = entry.getBasicValue();
            boolean hidden = basicValue instanceof buildcraft.api.statements.IStatement stmt
                && isStatementHiddenByCategory(stmt);

            String translatedTitle = entry.title;
            ISimpleDrawable icon = entry.createDrawable();
            PageLine line = new PageLine(icon, icon, 2, translatedTitle, true);

            if (entryFactory != null) {
                objectsAdded.add(basicValue);
                PageLinkNormal pageLink = new PageLinkNormal(line, !hidden, entry.getTooltip(), entryFactory,
                    entry.creativeOnly);
                addChild(entry.book, entry.typeTags, pageLink);
            }
            // entryFactory == null path intentionally falls through without
            // touching objectsAdded — PageEntryStatement#iterateAllDefault will
            // emit a synthesizing PageLinkStatement (with the same hidden-by-category
            // visibility flag baked into iterateAllDefault) so the leaf still exists.
        }

        // Auto-iterated categories (Triggers, Actions, ...) are filed per sort order by
        // fileExtraEntry: their own top-level chapter under Sort By Type, nested under the
        // single "BuildCraft" mod chapter under Sort By Mod, and flat under Alphabetical —
        // mirroring how regular entries (Blocks/Items/Pipes) place themselves via getOrdered.
        // The previous version filed them as a top-level chapter in *every* order, which
        // leaked "Triggers"/"Actions" chapters into Mod and Alphabetical sorts. Statements
        // carry no subtype.
        final IEntryLinkConsumer adder = (tags, page) -> fileExtraEntry(tags.type, null, page);

        for (PageValueType<?> type : GuidePageRegistry.INSTANCE.types.values()) {
            type.iterateAllDefault(adder, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        }

        // Consolidated category entries: single TOC links that open a page listing
        // many small entries that would otherwise clutter the index alphabetically.
        // Each is paired with a group registered in GuideGroupManager so the listed
        // entries also de-dup from the auto-iterated leaves above (see e.g.
        // PageEntryStatement#iterateAllDefault skipping IFillerPattern).
        addCategoryEntries(adder);

        quickSearcher.generate(net.minecraft.util.profiling.InactiveProfiler.INSTANCE);

        for (Map<TypeOrder, ContentsNode> map : contents.values()) {
            for (ContentsNode node : map.values()) {
                node.sort();
            }
        }
    }

    /** Source-of-truth list of category .md files to load. Each entry is
     *  {@code (groupDomain, groupName, mdRelativePath)} where the mdRelativePath is the
     *  resource-pack path under {@code compat/buildcraft/guide/<lang>/} (sans extension).
     *  Adding a new category means: register the group in
     *  {@link GuideGroupManager#populateDefaultGroups}, add its {@code addXCategory}
     *  helper below, drop a row here, and create the .md file. */
    private static final String[][] CATEGORY_BODY_SOURCES = {
        { "buildcraft", "filler_patterns",     "buildcraftunofficial:concept/filler_patterns" },
        { "buildcraft", "extraction_presets",  "buildcraftunofficial:concept/emzuli_extraction_presets" },
        { "buildcraft", "pipe_signals",        "buildcraftunofficial:concept/pipe_signals" },
        { "buildcraft", "set_pipe_direction",  "buildcraftunofficial:concept/set_pipe_direction" },
        { "buildcraft", "paint_pipe_colour",   "buildcraftunofficial:action/pipe_colour" },
        { "buildcraft", "set_power_limit",     "buildcraftunofficial:concept/set_power_limit" },
    };

    /** Walk the groups named in {@link #CATEGORY_BODY_SOURCES} and stash every
     *  {@link buildcraft.api.statements.IStatement} that's an entry of any of them into
     *  {@link #hiddenStatements}. Done at the top of {@link #generateContentsPage()} so
     *  {@link buildcraft.lib.client.guide.entry.PageEntryStatement#iterateAllDefault}
     *  can suppress those statements from the alphabetical leaf index — they're
     *  surfaced via the category entry instead. Adding the next category needs no
     *  edits here: just register the group + the source row, and statements in that
     *  group automatically get hidden. */
    private void populateHiddenStatements() {
        hiddenStatements.clear();
        for (String[] row : CATEGORY_BODY_SOURCES) {
            GuideGroupSet set = GuideGroupManager.get(row[0], row[1]);
            if (set == null) continue;
            for (PageValue<?> entry : set.entries) {
                if (entry.value instanceof buildcraft.api.statements.IStatement stmt) {
                    hiddenStatements.add(stmt);
                }
            }
        }
    }

    /** Read each category's body .md from the resource pack and parse it into a list of
     *  {@link GuidePartFactory}s, keyed by {@code domain:groupName} (matching the
     *  {@link #categoryLinks} key scheme). Falls back to {@link #DEFAULT_LANG} when the
     *  current language has no localized variant. Logs but does not throw on a missing
     *  or malformed file — the category page just renders without its description text
     *  in that case. */
    private void loadCategoryBodies(ResourceManager rm, String langCode) {
        categoryBodies.clear();
        for (String[] row : CATEGORY_BODY_SOURCES) {
            String domain = row[0];
            String groupName = row[1];
            Identifier mdRel = Identifier.parse(row[2]);

            List<GuidePartFactory> factories = tryLoadCategoryBody(rm, mdRel, langCode);
            if (factories == null && !DEFAULT_LANG.equals(langCode)) {
                factories = tryLoadCategoryBody(rm, mdRel, DEFAULT_LANG);
            }
            if (factories != null) {
                categoryBodies.put(Identifier.fromNamespaceAndPath(domain, groupName), factories);
            } else {
                BCLog.logger.warn("[lib.guide] Missing category body markdown at "
                    + mdRel + " — the " + domain + ":" + groupName
                    + " category page will render without its description.");
            }
        }
    }

    @Nullable
    private List<GuidePartFactory> tryLoadCategoryBody(ResourceManager rm, Identifier mdRel, String lang) {
        Identifier full = Identifier.fromNamespaceAndPath(mdRel.getNamespace(),
            "compat/buildcraft/guide/" + lang + "/" + mdRel.getPath() + ".md");
        try {
            var resource = rm.getResource(full);
            if (resource.isEmpty()) return null;
            try (InputStream in = resource.get().open();
                 java.io.InputStreamReader isr =
                     new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {
                return MarkdownPageLoader.INSTANCE.loadParts(
                    br, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            }
        } catch (IOException io) {
            BCLog.logger.warn("[lib.guide] Failed to read category body " + full + ": " + io);
            return null;
        }
    }

    /** Append the hardcoded "category" TOC entries — single index links whose pages list
     *  a guide-group of related items. The companion-side bookkeeping (registering the
     *  group, suppressing the individual leaves from the auto-iterated index) lives next
     *  to the data: groups in {@link GuideGroupManager#populateDefaultGroups()}, leaf
     *  filtering inside the relevant {@code iterateAllDefault} (e.g.
     *  {@code PageEntryStatement} skipping {@code IFillerPattern}). */
    private void addCategoryEntries(IEntryLinkConsumer adder) {
        // Reset before each rebuild so a /reload doesn't accumulate stale links from the
        // previous generation (the matching ContentsNode is rebuilt fresh, so leftover
        // entries here would only manifest as stale <link> targets).
        categoryLinks.clear();
        addFillerPatternsCategory(adder);
        addEmzuliExtractionPresetsCategory(adder);
        addPipeSignalsCategory(adder);
        addSetPipeDirectionCategory(adder);
        addPaintPipeColourCategory(adder);
        addSetPowerLimitCategory(adder);
    }

    /** File an auto-iterated leaf — a statement Trigger/Action, or a consolidated "category"
     *  entry — into every opted-in book's contents tree, placed correctly <i>per sort order</i>
     *  (the previous version filed it as a top-level chapter in <em>every</em> order, which
     *  leaked "Triggers"/"Actions" chapters into Sort By Mod and Alphabetical where the regular
     *  entries collapse under "BuildCraft" / go flat):
     *  <ul>
     *  <li><b>Alphabetical</b> (no tags) — flat under the root, no chapter grouping, a pure
     *      A–Z list.</li>
     *  <li><b>Mod-first</b> (Sort By Mod) — under the single "BuildCraft" mod chapter, with
     *      {@code chapterKey} as a sub-header beneath it, so the order shows exactly one
     *      top-level chapter (mirrors how regular entries nest their type under the mod).</li>
     *  <li><b>Type-first</b> (Sort By Type) — as its own top-level chapter named by
     *      {@code chapterKey} (e.g. "Triggers"/"Actions"), parallel to Blocks/Items/Pipes.</li>
     *  </ul>
     *  When {@code subtypeKey} is non-null/non-empty <em>and the order groups by subtype</em>
     *  (Sort By Type), the leaf lands one level deeper under the category chapter (e.g.
     *  {@code Actions > Item Transport}); orders without a subtype level (Sort By Mod) drop it,
     *  so a pipe-item category action sits directly under {@code Actions} alongside the plain
     *  actions instead of inside an Item Transport sub-group. Both keys are full localization
     *  keys. Idempotent on {@link #pageLinksAdded} / {@link #quickSearcher}, so filing the same
     *  page under several chapters (e.g. Pipe Signals under both Triggers and Actions) is safe. */
    private void fileExtraEntry(String chapterKey, @Nullable String subtypeKey, PageLink page) {
        if (pageLinksAdded.add(page)) {
            quickSearcher.add(page, page.getSearchName());
        }
        String chapterTitle = LocaleUtil.localize(chapterKey);
        String subtypeTitle = (subtypeKey == null || subtypeKey.isEmpty())
            ? null : LocaleUtil.localize(subtypeKey);
        // Every auto-iterated entry is BuildCraft's (this is a single mod); regular entries
        // build the same "BuildCraft" node via getOrdered(mod_type)[0], so the get-or-create
        // inside placeExtraEntryInOrder reuses it rather than making a second mod tab.
        String modTitle = LocaleUtil.localize(ETypeTag.MOD.preText + "buildcraft");
        for (Entry<GuideBook, Map<TypeOrder, ContentsNode>> bookEntry : contents.entrySet()) {
            @Nullable GuideBook book = bookEntry.getKey();
            if (book != null && !book.appendAllEntries) continue;
            for (Entry<TypeOrder, ContentsNode> orderEntry : bookEntry.getValue().entrySet()) {
                placeExtraEntryInOrder(orderEntry.getKey(), orderEntry.getValue(),
                    modTitle, chapterTitle, subtypeTitle, page);
            }
        }
    }

    /** Pure placement of one already-deduped {@code leaf} into a single sort {@code order}'s
     *  contents {@code root}. Walks the order's own tag sequence and emits one chapter level per
     *  tag the entry has a value for — exactly mirroring {@link JsonTypeTags#getOrdered} for
     *  regular entries — so the depth always matches the order: flat under Alphabetical
     *  (no tags), {@code BuildCraft > Actions} under Sort By Mod ({@code [MOD, TYPE]}, no
     *  subtype level), {@code Actions > Item Transport} under Sort By Type
     *  ({@code [TYPE, SUB_TYPE]}). Static and registry-free so it can be unit-tested directly. */
    static void placeExtraEntryInOrder(TypeOrder order, ContentsNode root, String modTitle,
        String chapterTitle, @Nullable String subtypeTitle, IContentsNode leaf) {
        ContentsNode node = root;
        int indent = 0;
        for (ETypeTag tag : order.tags) {
            String title = switch (tag) {
                case MOD -> modTitle;
                case TYPE -> chapterTitle;
                case SUB_TYPE -> subtypeTitle;
                case SUB_MOD -> null; // auto-iterated entries carry no submod
            };
            if (title == null || title.isEmpty()) {
                continue; // skip tags this entry has no value for (mirrors getOrdered)
            }
            node = getOrCreateChapter(node, title, indent++);
        }
        node.addChild(leaf);
    }

    /** Thin wrapper over {@link #fileExtraEntry} kept for the {@link #registerCategory} call
     *  site (category entries that specify an explicit subtype). */
    private void addToChapterSubtype(String chapterKey, @Nullable String subtypeKey, PageLink page) {
        fileExtraEntry(chapterKey, subtypeKey, page);
    }

    /** Locate or create a child {@link ContentsNode} of {@code parent} with the given
     *  localized {@code title}. Throws if a non-{@link ContentsNode} child already exists
     *  at that key — same invariant the inline walk in {@link #addChild} and the
     *  {@link IEntryLinkConsumer} adder lambda enforce. */
    private static ContentsNode getOrCreateChapter(ContentsNode parent, String title, int indent) {
        IContentsNode subNode = parent.getChild(title);
        if (subNode instanceof ContentsNode) {
            return (ContentsNode) subNode;
        } else if (subNode == null) {
            ContentsNode created = new ContentsNode(title, indent);
            parent.addChild(created);
            return created;
        } else {
            throw new IllegalStateException("Unknown node type " + subNode.getClass());
        }
    }

    /** Build a category TOC entry plus the matching markdown-linkable {@link PageLink}.
     *  All categories share the same shape: an icon, a title, a description body parsed
     *  from a per-category {@code .md} file, and a {@link GuidePartGroup} that lists the
     *  named group's entries in registration order. The entry is filed under each tag in
     *  {@code chapterTagTypes} (each a localization key — typically the same chapter the
     *  leaf entries used to live in, so readers find it where they already look) and
     *  registered in {@link #categoryLinks} under {@code domain:groupName} so
     *  {@code <link to="domain:groupName"/>} works in markdown.
     *
     *  <p>Most categories pass a single chapter tag — they collapse leaves that all live
     *  in one chapter (Filler Patterns are pure actions; Emzuli presets are pure actions).
     *  Pipe Signals span both Triggers and Actions, so the same TOC link is filed under
     *  both — the {@link IEntryLinkConsumer} dedups its search-index population for us
     *  (see {@code pageLinksAdded.add(page)}), so this only adds the link to two
     *  ContentsNodes.
     *
     *  <p>{@code chapterSubtypes} (nullable, or with nullable elements) is a parallel array
     *  to {@code chapterTagTypes}: when non-null at index {@code i}, the entry lands at
     *  {@code chapterTagTypes[i] > chapterSubtypes[i]} instead of directly under the
     *  chapter. Without this, a category entry sits as a peer of the chapter's subtype
     *  headers ({@code Basic}, {@code Item Transport}, {@code Pluggables}, ...) and sorts
     *  alphabetically into them — reading as "tucked under Basic". Subtype keys are full
     *  localization keys (e.g. {@code "buildcraft.guide.chapter.subtype.pipe_item"}),
     *  same convention {@code chapterTagTypes} uses.
     *
     *  <p>{@code extraParts} (nullable) supplies any additional {@link GuidePart}s to
     *  insert between the description and the group listing — typically a manual
     *  {@code <link>}-style backlink to a related page that {@link GuideGroupManager}'s
     *  auto Linked-To/From machinery doesn't already cover. Evaluated per page open so
     *  the parts can capture the live {@link GuiGuide}. */
    private void registerCategory(IEntryLinkConsumer adder, String domain, String groupName,
        String[] chapterTagTypes, @Nullable String[] chapterSubtypes,
        ISimpleDrawable icon, String title,
        @Nullable java.util.function.Function<GuiGuide, List<GuidePart>> extraParts) {
        GuideGroupSet groupSet = GuideGroupManager.get(domain, groupName);
        if (groupSet == null) return;

        Identifier groupId = Identifier.fromNamespaceAndPath(domain, groupName);
        PageLine line = new PageLine(icon, icon, 2, title, true);
        GuidePageFactory factory = g -> {
            List<GuidePart> parts = new ArrayList<>();
            // Description body — parsed from the category's .md file at reload time.
            // If the file was missing or unparseable, the page just renders without it
            // (the warning is logged once during reload, see loadCategoryBodies).
            List<GuidePartFactory> bodyFactories = categoryBodies.get(groupId);
            if (bodyFactories != null) {
                for (GuidePartFactory bf : bodyFactories) {
                    GuidePart part = bf.createNew(g);
                    if (part != null) parts.add(part);
                }
            }
            if (extraParts != null) {
                parts.addAll(extraParts.apply(g));
            }
            parts.add(new GuidePartGroup(g, groupSet, GuideGroupSet.GroupDirection.SRC_TO_ENTRY));
            // PageEntryExternal carries no value-specific behaviour beyond exposing the
            // string as the page title — perfect for a category page that doesn't map
            // to a single in-world object.
            return new GuidePage(g, parts, new PageValue<>(PageEntryExternal.INSTANCE, title));
        };

        PageLinkNormal link = new PageLinkNormal(line, true, ImmutableList.of(title), factory);
        categoryLinks.put(groupId, link);
        for (int i = 0; i < chapterTagTypes.length; i++) {
            String chapterKey = chapterTagTypes[i];
            String subtypeKey = (chapterSubtypes != null && i < chapterSubtypes.length)
                ? chapterSubtypes[i] : null;
            if (subtypeKey == null || subtypeKey.isEmpty()) {
                adder.addChild(new JsonTypeTags(chapterKey), link);
            } else {
                addToChapterSubtype(chapterKey, subtypeKey, link);
            }
        }
    }

    /** "Filler Patterns" — collapses ~19 alphabetically-listed pattern actions into one
     *  category entry under Actions, using the stairs sprite as the icon. The body
     *  comes from {@code concept/filler_patterns.md}; this method just supplies the
     *  TOC-side metadata (icon, title, chapter placement). */
    private void addFillerPatternsCategory(IEntryLinkConsumer adder) {
        // Use the same statement-slot rendering the patterns themselves use elsewhere in
        // the guide (16x16 framed slot with the pattern's sprite), so the icon visually
        // matches the leaf entries this category replaces.
        ISimpleDrawable icon = (x, y) -> GuiElementStatementSource.drawGuiSlot(
            buildcraft.builders.BCBuildersStatements.PATTERN_STAIRS, x, y);
        registerCategory(adder, "buildcraft", "filler_patterns",
            new String[] { "buildcraft.guide.contents.actions" },
            // Filed under Automation: the filler block is an automation machine, and
            // the patterns are GUI choices for how it shapes its area-fill — same
            // subtype the filler itself sits under (Blocks > Automation).
            new String[] { "buildcraft.guide.chapter.subtype.automation" },
            icon,
            "Filler Patterns",
            null);
    }

    /** "Emzuli Extraction Presets" — collapses the four colour-keyed extraction-preset
     *  actions (red/green/blue/yellow) into one category entry, using the red preset's
     *  sprite as the icon. The body comes from {@code concept/emzuli_extraction_presets.md};
     *  the manual back-link to the Emzuli pipe is appended programmatically because
     *  the auto-emitted Linked-From machinery doesn't cover the source-of-group case
     *  (the pipe is the SOURCE of the {@code extraction_presets} group, not an entry
     *  in some other group containing the category). */
    private void addEmzuliExtractionPresetsCategory(IEntryLinkConsumer adder) {
        ISimpleDrawable icon = (x, y) -> GuiElementStatementSource.drawGuiSlot(
            // ACTION_EXTRACTION_PRESET[0] is the SQUARE/RED preset (see SlotIndex enum
            // order in PipeBehaviourEmzuli) — the visually-canonical "default" preset.
            buildcraft.transport.BCTransportStatements.ACTION_EXTRACTION_PRESET[0], x, y);
        registerCategory(adder, "buildcraft", "extraction_presets",
            new String[] { "buildcraft.guide.contents.actions" },
            new String[] { "buildcraft.guide.chapter.subtype.pipe_item" },
            icon,
            "Emzuli Extraction Presets",
            g -> {
                ItemStack emzuliStack = new ItemStack(
                    buildcraft.transport.BCTransportItems.PIPE_EMZULI_ITEM.get());
                PageLink emzuliLink = PageLinkItemStack.create(true, emzuliStack,
                    net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
                return ImmutableList.of(new GuidePartLink(g, emzuliLink));
            });
    }

    /** "Pipe Signals" — collapses 16 colours × (1 action + 2 triggers) = 48 statements
     *  that previously sprawled across the Triggers and Actions chapters into a single
     *  TOC entry. Filed under BOTH chapter tags so a reader looking under either Triggers
     *  or Actions still finds it where they would have looked for the per-colour leaves.
     *  The body comes from {@code concept/pipe_signals.md} — including its own
     *  {@code <link to="buildcraftunofficial:item/wire"/>} back to the pipe wire page,
     *  so no extraParts are needed (unlike Emzuli, whose md had no inline wire link). */
    private void addPipeSignalsCategory(IEntryLinkConsumer adder) {
        // Use the BLACK active sprite for the icon — the user's reference document was
        // "Black Pipe Signal", and BLACK's the canonical example wire colour throughout
        // the codebase (it's the colour the now-removed guide.txt entries pointed at).
        ISimpleDrawable icon = (x, y) -> GuiElementStatementSource.drawGuiSlot(
            buildcraft.transport.BCTransportStatements.ACTION_PIPE_SIGNAL[
                net.minecraft.world.item.DyeColor.BLACK.ordinal()], x, y);
        registerCategory(adder, "buildcraft", "pipe_signals",
            new String[] {
                "buildcraft.guide.contents.triggers",
                "buildcraft.guide.contents.actions",
            },
            // Filed under Pluggables in both chapters: the wire pluggable (and the
            // pulsar that drives it) is the semantic source of pipe signals — readers
            // looking for "anything to do with wires" find the category there alongside
            // the wire and pulsar pages, instead of in Item Transport with the items
            // those signals end up gating.
            new String[] {
                "buildcraft.guide.chapter.subtype.pipe_plug",
                "buildcraft.guide.chapter.subtype.pipe_plug",
            },
            icon,
            "Pipe Signals",
            null);
    }

    /** "Set pipe direction" — collapses the six "Face the X side" actions (one per
     *  Direction) into a single category entry under Actions. The icon is a vanilla
     *  compass — the only directional vanilla item that visually communicates "this
     *  is about which way to face" without picking one specific Direction's sprite.
     *  The body comes from {@code concept/set_pipe_direction.md}; the per-direction
     *  pages at {@code action/pipe_direction_*.md} stay on disk and remain reachable
     *  by clicking from the category page (their guide.txt registrations are kept,
     *  so {@link #getFactoryFor} still resolves them — the {@code hiddenStatements}
     *  filter only suppresses TOC leaves, not click-resolution from the group).
     *  <p>
     *  The icon blits {@code minecraft:item/compass_16} (vanilla's needle-straight-up
     *  frame, the threshold-0.0 entry in {@code items/compass.json}'s range_dispatch)
     *  directly as a sprite, bypassing the dispatch entirely — without a level/entity
     *  context the dispatch falls back to a time-based wobble, which is why a plain
     *  {@code ItemStack(Items.COMPASS)} icon spins. Resolution goes through
     *  {@link buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder} because
     *  modern Minecraft (1.21+) stitches item textures onto a separate
     *  {@link net.minecraft.client.renderer.texture.TextureAtlas#LOCATION_ITEMS} atlas
     *  rather than the historical unified blocks atlas — going straight at
     *  {@code LOCATION_BLOCKS} returns the missing-texture sprite. The holder walks
     *  blocks → items → GUI in order and caches the hit. Resource packs that retexture
     *  the compass replace the per-frame {@code compass_XX.png} textures, so the
     *  rendered sprite still reflects the active resource pack. */
    private void addSetPipeDirectionCategory(IEntryLinkConsumer adder) {
        buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder compassFrame =
            buildcraft.lib.client.sprite.SpriteHolderRegistry.getHolder("minecraft:item/compass_16");
        ISimpleDrawable icon = (x, y) -> {
            net.minecraft.client.gui.GuiGraphicsExtractor graphics =
                buildcraft.lib.gui.GuiIcon.getGuiGraphics();
            if (graphics == null) return;
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = compassFrame.getSprite();
            if (sprite == null) return;
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                sprite, (int) x, (int) y, 16, 16, 0xFFFFFFFF);
        };
        registerCategory(adder, "buildcraft", "set_pipe_direction",
            new String[] { "buildcraft.guide.contents.actions" },
            new String[] { "buildcraft.guide.chapter.subtype.pipe_item" },
            icon,
            "Set Pipe Direction",
            null);
    }

    /** "Paint Passing Items" — collapses the 16 "Paint Items &lt;colour&gt;" actions (one per
     *  dye colour) into a single category entry under Actions. The icon is the black
     *  paintbrush sprite (the canonical example used in the existing pipe_colour.md
     *  writeup). The body is the existing "Paint Items Black" page
     *  ({@code action/pipe_colour.md}), whose text applies equally to all colour
     *  variants since the only difference between them is which colour they paint. */
    private void addPaintPipeColourCategory(IEntryLinkConsumer adder) {
        ISimpleDrawable icon = (x, y) -> GuiElementStatementSource.drawGuiSlot(
            buildcraft.transport.BCTransportStatements.ACTION_PIPE_COLOUR[
                net.minecraft.world.item.DyeColor.BLACK.ordinal()], x, y);
        registerCategory(adder, "buildcraft", "paint_pipe_colour",
            new String[] { "buildcraft.guide.contents.actions" },
            new String[] { "buildcraft.guide.chapter.subtype.pipe_item" },
            icon,
            "Paint Passing Items",
            null);
    }

    /** "Set Power Limit" — collapses the 4 limiter pipes × 7 levels = 28
     *  "Switch to N MJ/t / RF/t limit" actions into a single category entry under
     *  Actions. The icon is the iron-MJ limiter at limitShift=3 (the {@code m16}
     *  partially-filled bar sprite), a mid-range limit that reads as "throttling"
     *  rather than "fully open" or "blocked". The body comes from
     *  {@code concept/set_power_limit.md}. This umbrella group is key-less and feeds only
     *  this category page; each limiter pipe (Iron/Diamond Kinesis and Iron/Diamond FE)
     *  instead keys its own single-pipe {@code set_power_limit_*} group in
     *  GuideGroupManager, so a pipe page's "Linked To: Set Power Limit" chapter lists only
     *  that pipe's own seven levels. */
    private void addSetPowerLimitCategory(IEntryLinkConsumer adder) {
        // ACTION_IRON_POWER_LIMIT[3] is the limitShift=3 entry — sprite m16. See
        // BCTransportStatements (i = numLevels-1-shift); i=3 ⇒ shift=3 ⇒ index 3 of
        // {m256, m128, m64, m16, m8, m2, m0} = m16, a partially-filled limiter bar.
        ISimpleDrawable icon = (x, y) -> GuiElementStatementSource.drawGuiSlot(
            buildcraft.transport.BCTransportStatements.ACTION_IRON_POWER_LIMIT[3], x, y);
        registerCategory(adder, "buildcraft", "set_power_limit",
            new String[] { "buildcraft.guide.contents.actions" },
            new String[] { "buildcraft.guide.chapter.subtype.pipe_item" },
            icon,
            "Set Power Limit",
            null);
    }

    private void genTypeMap(GuideBook book) {
        Map<TypeOrder, ContentsNode> map = new HashMap<>();
        contents.put(book, map);
        for (TypeOrder order : GuiGuide.SORTING_TYPES) {
            map.put(order, new ContentsNode("root", -1));
        }
    }

    private void addChild(Identifier bookType, JsonTypeTags tags, PageLink page) {
        if (pageLinksAdded.add(page)) {
            quickSearcher.add(page, page.getSearchName());
        }

        for (Entry<GuideBook, Map<TypeOrder, ContentsNode>> bookEntry : contents.entrySet()) {
            @Nullable GuideBook book = bookEntry.getKey();
            if (book != null && !book.name.equals(bookType)) continue;
            Map<TypeOrder, ContentsNode> map = bookEntry.getValue();
            for (Entry<TypeOrder, ContentsNode> entry : map.entrySet()) {
                TypeOrder order = entry.getKey();
                String[] ordered = tags.getOrdered(order);
                ContentsNode[] nodePath = new ContentsNode[ordered.length];
                ContentsNode node = entry.getValue();
                for (int i = 0; i < ordered.length; i++) {
                    String title = LocaleUtil.localize(ordered[i]);
                    IContentsNode subNode = node.getChild(title);
                    if (subNode instanceof ContentsNode) {
                        node = (ContentsNode) subNode;
                        nodePath[i] = node;
                    } else if (subNode == null) {
                        ContentsNode subContents = new ContentsNode(title, i);
                        node.addChild(subContents);
                        node = subContents;
                        nodePath[i] = node;
                    } else {
                        throw new IllegalStateException("Unknown node type " + subNode.getClass());
                    }
                }
                if (nodePath.length == 0) {
                    node.addChild(page);
                } else {
                    nodePath[nodePath.length - 1].addChild(page);
                }
            }
        }
    }

    @Nullable
    public GuidePageFactory getFactoryFor(Identifier partialLocation) {
        return pages.get(partialLocation);
    }

    @Nullable
    public GuidePageFactory getFactoryFor(Object value) {
        if (value instanceof ItemStackValueFilter) {
            value = ((ItemStackValueFilter) value).stack.baseStack;
        } else if (value instanceof ItemStackKey) {
            value = ((ItemStackKey) value).baseStack;
        }
        if (value instanceof ItemStack) {
            return getPageFor((ItemStack) value);
        }
        return getFactoryFor(getEntryFor(value));
    }

    public static Identifier getEntryFor(Object obj) {
        for (Entry<Object, PageEntry<?>> entry : GuidePageRegistry.INSTANCE.getReloadableEntryMap()
            .entrySet()) {
            if (entry.getValue().matches(obj)) {
                return (Identifier) entry.getKey();
            }
        }
        return null;
    }

    @Nonnull
    public GuidePageFactory getPageFor(@Nonnull ItemStack stack) {
        Identifier entry = getEntryFor(stack);
        if (entry != null) {
            GuidePageFactory factory = getFactoryFor(entry);
            if (factory != null) {
                return factory;
            }
        }
        return generatedPages.computeIfAbsent(stack, GuidePageStandInRecipes::createFactory);
    }

    public ContentsNodeGui getGuiContents(GuiGuide gui, GuidePageContents guidePageContents, TypeOrder sortingOrder) {
        if (contents.isEmpty()) {
            // Contents not yet generated — trigger a reload
            reload();
        }
        Map<TypeOrder, ContentsNode> map = contents.get(gui.book);
        if (map == null) {
            // Fall back to the "all books" entry (null key)
            map = contents.get(null);
        }
        if (map == null) {
            throw new IllegalStateException("Unknown book " + gui.book + " and no fallback contents available");
        }
        ContentsNode node = map.get(sortingOrder);
        if (node == null) {
            throw new IllegalStateException("Unknown sorting order " + sortingOrder);
        }
        node.resetVisibility();
        return new ContentsNodeGui(gui, node);
    }
}
