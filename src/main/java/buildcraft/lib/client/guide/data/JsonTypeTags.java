/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import buildcraft.api.core.BCLog;

import buildcraft.lib.client.guide.ETypeTag;
import buildcraft.lib.client.guide.TypeOrder;

public class JsonTypeTags {
    public static final JsonTypeTags EMPTY = new JsonTypeTags("", "", "");

    public final String domain;
    public final String type;
    public final String subType;
    /** Raw game-system key (e.g. {@code "power"}, from a page entry's {@code tag_system}), or null.
     *  Unlike the other tags it has no "unknown" fallback: an entry nobody filed under a system
     *  should sit at the contents root, not under an invented "Unknown" chapter. */
    @Nullable
    public final String system;

    public JsonTypeTags(String domain, String type, String subType) {
        this(domain, type, subType, null);
    }

    public JsonTypeTags(String domain, String type, String subType, @Nullable String system) {
        this.domain = domain;
        this.type = type;
        this.subType = subType;
        this.system = system;
    }

    public JsonTypeTags(String type) {
        this(null, type, null);
    }

    public String[] getOrdered(TypeOrder typeOrder) {
        if (domain == null && subType == null) {
            // Built-in type for "others"
            return new String[] { type };
        }

        // Build the list while skipping tags whose raw value is empty/null so we don't insert
        // empty-label levels like "buildcraft.guide.chapter.subtype." into the TOC hierarchy.
        List<String> collected = new ArrayList<>(typeOrder.tags.size());
        for (int i = 0; i < typeOrder.tags.size(); i++) {
            ETypeTag tag = typeOrder.tags.get(i);
            String raw = getRaw(tag);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            collected.add(tag.preText + raw);
        }
        return collected.toArray(new String[0]);
    }

    private String getRaw(ETypeTag tag) {
        if (tag == ETypeTag.MOD) {
            return getMod(domain, 0);
        } else if (tag == ETypeTag.SUB_MOD) {
            return getMod(domain, 1);
        } else if (tag == ETypeTag.TYPE) {
            return type;
        } else if (tag == ETypeTag.SUB_TYPE) {
            return subType;
        } else if (tag == ETypeTag.SYSTEM) {
            return system;
        }
        throw new IllegalStateException("Don't know the type " + tag);
    }

    private String getTyped(ETypeTag tag) {
        String raw = getRaw(tag);
        return tag.preText + (raw == null ? "" : raw);
    }

    private static String getMod(String domain, int index) {
        if (domain.startsWith("buildcraft")) {
            return index == 0 ? "buildcraft" : domain.substring("buildcraft".length());
        }
        return index == 0 ? domain : "";
    }

    public JsonTypeTags inheritMissingTags(JsonTypeTags parent) {
        String d = firstNonEmpty(this.domain, parent.domain, "unknown");
        String t = firstNonEmpty(this.type, parent.type, "unknown");
        String st = firstNonEmpty(this.subType, parent.subType, "unknown");
        // No "unknown" default for the system — see the field's Javadoc.
        String sys = firstNonEmpty(this.system, parent.system);
        if (sys != null && sys.isBlank()) sys = null;
        return new JsonTypeTags(d, t, st, sys);
    }

    private static String firstNonEmpty(String... strings) {
        String current = null;
        for (String string : strings) {
            current = string;
            if (current != null && !current.isBlank()) {
                break;
            }
        }
        return current;
    }

    public void printContents(int indent) {
        StringBuilder f = new StringBuilder();
        while (indent > 0) {
            f.append("  ");
            indent--;
        }
        BCLog.logger.info(f + "domain = " + domain + ",");
        BCLog.logger.info(f + "type = " + type + ",");
        BCLog.logger.info(f + "sub_type = " + subType + ",");
        BCLog.logger.info(f + "system = " + system);
    }
}
