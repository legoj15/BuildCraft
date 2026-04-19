/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
import buildcraft.lib.client.guide.entry.PageValueType;
import buildcraft.lib.client.guide.loader.IPageLoader;
import buildcraft.lib.client.guide.loader.MarkdownPageLoader;
import buildcraft.lib.client.guide.parts.GuidePageFactory;
import buildcraft.lib.client.guide.parts.GuidePageStandInRecipes;
import buildcraft.lib.client.guide.parts.contents.ContentsNode;
import buildcraft.lib.client.guide.parts.contents.ContentsNodeGui;
import buildcraft.lib.client.guide.parts.contents.GuidePageContents;
import buildcraft.lib.client.guide.parts.contents.IContentsNode;
import buildcraft.lib.client.guide.parts.contents.PageLink;
import buildcraft.lib.client.guide.parts.contents.PageLinkNormal;
import buildcraft.lib.client.guide.ref.GuideGroupManager;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.guide.GuideBook;
import buildcraft.lib.guide.GuideBookRegistry;
import buildcraft.lib.guide.GuideContentsData;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.search.ISuffixArray;
import buildcraft.lib.misc.search.SimpleSuffixArray;

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

    public final Set<Object> objectsAdded = new HashSet<>();

    private boolean isInReload = false;

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
        reload(resourceManager);
    }

    public void reload() {
        reload(Minecraft.getInstance().getResourceManager());
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
                        try (InputStream stream = resource.get().open()) {
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

            String endings;
            if (PAGE_LOADERS.size() == 1) {
                endings = PAGE_LOADERS.keySet().iterator().next();
            } else {
                endings = PAGE_LOADERS.keySet().toString();
            }
            BCLog.logger.warn(
                "[lib.guide.loader] Unable to load guide page '" + entryKey + "' (full path = '" + domain + ":" + path
                    + "." + endings + "') because we couldn't find any of the valid paths in any resource pack!"
            );
        }
    }

    private void generateContentsPage() {
        objectsAdded.clear();
        contents.clear();
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
            String translatedTitle = entry.title;
            ISimpleDrawable icon = entry.createDrawable();
            PageLine line = new PageLine(icon, icon, 2, translatedTitle, true);

            if (entryFactory != null) {
                objectsAdded.add(entry.getBasicValue());
                PageLinkNormal pageLink = new PageLinkNormal(line, true, entry.getTooltip(), entryFactory);
                addChild(entry.book, entry.typeTags, pageLink);
            }
        }

        // "All" group for books that opt-in
        ContentsNode othersRoot = new ContentsNode(LocaleUtil.localize("buildcraft.guide.contents.all_group"), 0);
        for (Entry<GuideBook, Map<TypeOrder, ContentsNode>> bookEntry : contents.entrySet()) {
            @Nullable GuideBook book = bookEntry.getKey();
            if (book != null && !book.appendAllEntries) continue;
            for (ContentsNode root : bookEntry.getValue().values()) {
                root.addChild(othersRoot);
            }
        }

        final IEntryLinkConsumer adder = (tags, page) -> {
            if (pageLinksAdded.add(page)) {
                quickSearcher.add(page, page.getSearchName());
            }
            String title = LocaleUtil.localize(tags.type);
            IContentsNode subNode = othersRoot.getChild(title);
            if (subNode instanceof ContentsNode) {
                subNode.addChild(page);
            } else if (subNode == null) {
                ContentsNode subContents = new ContentsNode(title, 1);
                othersRoot.addChild(subContents);
                subContents.addChild(page);
            } else {
                throw new IllegalStateException("Unknown node type " + subNode.getClass());
            }
        };

        for (PageValueType<?> type : GuidePageRegistry.INSTANCE.types.values()) {
            type.iterateAllDefault(adder, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        }

        quickSearcher.generate(net.minecraft.util.profiling.InactiveProfiler.INSTANCE);

        for (Map<TypeOrder, ContentsNode> map : contents.values()) {
            for (ContentsNode node : map.values()) {
                node.sort();
            }
        }
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
