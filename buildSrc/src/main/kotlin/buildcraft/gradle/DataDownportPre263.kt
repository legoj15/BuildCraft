/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.gradle

import java.io.File

// ─── 26.3 loot/advancement data-format down-port ─────────────────────────────
// Minecraft 26.3 rewrote the loot-condition plumbing that advancements and loot tables share:
//   - a loot condition dispatches on "type" (was "condition");
//   - a loot pool / entry takes ONE optional "condition" (was a "conditions" list);
//   - an advancement trigger's "player" is ONE loot condition (was a condition list, or an
//     inline entity predicate);
//   - minecraft:recipe_crafted takes a recipe holder set "recipes" (was a single "recipe_id").
// No JSON shape is valid on both sides: each codec ignores the other side's keys silently (a loot
// pool keeps dropping, just without its condition) or rejects the shape outright. So the sources are
// authored in the 26.3 form and every node below 26.3 rewrites its COPIED output here (src stays
// canonical; the 26.3 node only validates).
//
// An entry's loot function is the same split: 26.3 takes ONE "modifier" object dispatched on "type", every
// older node a "functions" list dispatched on "function".
//
// Deliberately narrow: only the shapes BC ships are converted (composite loot entries recurse through
// "children"). Anything else (a pool's or the table's "modifier", an unverified function, a function's own
// conditions, entity_properties predicates, predicate references, untested triggers, a non-object
// root/criteria/conditions) fails the build with a message naming the file, so a new data file cannot silently
// mis-load on one side.
//
// validate=true on every node: the 26.3-form check also catches an old-form key added to src,
// which 26.3 would otherwise ignore without a word.

object DataDownportPre263 {

    // Condition types whose own fields are spelled identically on every node; only the dispatch key
    // and the list-vs-single wrapping differ. Verified in .neoforge-ref for 1.21.1 .. 26.3.
    private val STABLE_CONDITIONS = setOf(
        "minecraft:survives_explosion",
        "minecraft:location_check",
        "minecraft:all_of",
        "minecraft:any_of",
        "minecraft:inverted"
    )

    // Loot functions whose own fields are spelled identically on every node (verified in .neoforge-ref for
    // 1.21.1 .. 26.3: copy_components takes "source", "include" and "exclude" everywhere, and "block_entity"
    // is a valid source name on every node); only the dispatch key and the list-vs-single wrapping differ.
    private val STABLE_FUNCTIONS = setOf(
        "minecraft:copy_components"
    )

    // Triggers whose condition fields (besides "player" and recipe_crafted's "recipes") read the same
    // on every node.
    private val STABLE_TRIGGERS = setOf(
        "minecraft:impossible",
        "minecraft:inventory_changed",
        "minecraft:location",
        "minecraft:recipe_crafted"
    )

    private class Ctx(val file: File) {
        fun fail(msg: String): Nothing =
            throw IllegalStateException("26.3 data down-port: ${file.path}: $msg")
    }

    // 26.3 condition object -> pre-26.3 condition object (renames the dispatch key, recurses into
    // composite terms).
    @Suppress("UNCHECKED_CAST")
    private fun convertCondition(ctx: Ctx, node: Any?): Map<String, Any?> {
        val m = node as? Map<String, Any?>
            ?: ctx.fail("loot condition must be an inline object, got: $node (predicate references are not down-ported)")
        if (m.containsKey("condition")) ctx.fail("loot condition uses the pre-26.3 \"condition\" key; author it with \"type\": $m")
        val type = m["type"] as? String ?: ctx.fail("loot condition has no \"type\": $m")
        if (type !in STABLE_CONDITIONS) ctx.fail("loot condition $type has no verified down-port; check its codec on every node and add it to STABLE_CONDITIONS")
        val out = linkedMapOf<String, Any?>("condition" to type)
        for ((k, v) in m) {
            when (k) {
                "type" -> {}
                "term" -> out[k] = convertCondition(ctx, v)
                "terms" -> out[k] = (v as? List<Any?> ?: ctx.fail("\"terms\" must be a list: $v")).map { convertCondition(ctx, it) }
                else -> out[k] = v
            }
        }
        return out
    }

    // 26.3 entry modifier object -> pre-26.3 function object (renames the dispatch key).
    @Suppress("UNCHECKED_CAST")
    private fun convertFunction(ctx: Ctx, node: Any?): Map<String, Any?> {
        val m = node as? Map<String, Any?>
            ?: ctx.fail("loot modifier must be one inline object, got: $node (function lists and references are not down-ported)")
        if (m.containsKey("function")) ctx.fail("loot modifier uses the pre-26.3 \"function\" key; author it with \"type\": $m")
        val type = m["type"] as? String ?: ctx.fail("loot modifier has no \"type\": $m")
        if (type !in STABLE_FUNCTIONS) ctx.fail("loot function $type has no verified down-port; check its codec on every node and add it to STABLE_FUNCTIONS")
        if (m.containsKey("condition") || m.containsKey("conditions")) ctx.fail("a loot function's own conditions have no down-port yet: $m")
        val out = linkedMapOf<String, Any?>("function" to type)
        for ((k, v) in m) {
            if (k != "type") out[k] = v
        }
        return out
    }

    // A loot pool or entry: "condition" -> "conditions": [...]. An entry's "modifier" -> "functions": [...]
    // (allowModifier); a pool's or the table's has no down-port.
    @Suppress("UNCHECKED_CAST")
    private fun convertConditionHolder(ctx: Ctx, m: MutableMap<String, Any?>, allowModifier: Boolean) {
        if (m.containsKey("conditions")) ctx.fail("uses the pre-26.3 \"conditions\" list; author a single \"condition\" object")
        if (m.containsKey("functions")) ctx.fail("uses the pre-26.3 \"functions\" list; author a single \"modifier\" object")
        if (m.containsKey("modifier")) {
            if (!allowModifier) ctx.fail("a pool's or table's loot modifier has no down-port yet (only an entry's)")
            m["functions"] = listOf(convertFunction(ctx, m.remove("modifier")))
        }
        if (m.containsKey("condition")) {
            m["conditions"] = listOf(convertCondition(ctx, m.remove("condition")))
        }
    }

    // One loot entry. The composite entries (alternatives / group / sequence) nest further entries under
    // "children"; each child is a plain entry with its own "condition" in the same shape as its parent's, so
    // the same conversion recurses.
    @Suppress("UNCHECKED_CAST")
    private fun convertEntry(ctx: Ctx, e: Any?) {
        val entry = e as? MutableMap<String, Any?> ?: ctx.fail("entry is not an object")
        convertConditionHolder(ctx, entry, allowModifier = true)
        if (entry.containsKey("children")) {
            val children = entry["children"] as? List<Any?> ?: ctx.fail("entry \"children\" must be a list: ${entry["children"]}")
            children.forEach { convertEntry(ctx, it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertLootTable(ctx: Ctx, root: MutableMap<String, Any?>) {
        if (root.containsKey("functions") || root.containsKey("modifier")) ctx.fail("loot functions/modifiers have no down-port yet")
        (root["pools"] as? List<Any?>)?.forEach { p ->
            val pool = p as? MutableMap<String, Any?> ?: ctx.fail("pool is not an object")
            convertConditionHolder(ctx, pool, allowModifier = false)
            (pool["entries"] as? List<Any?>)?.forEach { convertEntry(ctx, it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertAdvancement(ctx: Ctx, root: MutableMap<String, Any?>) {
        val rawCriteria = root["criteria"] ?: return
        val criteria = rawCriteria as? Map<String, Any?> ?: ctx.fail("\"criteria\" must be an object: $rawCriteria")
        for ((name, c) in criteria) {
            val crit = c as? Map<String, Any?> ?: ctx.fail("criterion $name is not an object")
            val trigger = crit["trigger"] as? String ?: ctx.fail("criterion $name has no trigger")
            if (trigger !in STABLE_TRIGGERS) ctx.fail("trigger $trigger (criterion $name) has no verified down-port; check its codec on every node and add it to STABLE_TRIGGERS")
            val rawCond = crit["conditions"] ?: continue
            val cond = rawCond as? MutableMap<String, Any?> ?: ctx.fail("criterion $name: \"conditions\" must be an object: $rawCond")
            if (cond.containsKey("player")) {
                val p = cond["player"]
                if (p is List<*>) ctx.fail("criterion $name: \"player\" is a pre-26.3 condition list; author one condition object")
                cond["player"] = listOf(convertCondition(ctx, p))
            }
            if (trigger == "minecraft:recipe_crafted") {
                if (cond.containsKey("recipe_id")) ctx.fail("criterion $name uses the pre-26.3 \"recipe_id\"; author \"recipes\"")
                val r = cond.remove("recipes") ?: ctx.fail("criterion $name: recipe_crafted needs \"recipes\"")
                val id = when {
                    r is String && !r.startsWith("#") -> r
                    r is List<*> && r.size == 1 && r[0] is String && !(r[0] as String).startsWith("#") -> r[0] as String
                    else -> ctx.fail("criterion $name: pre-26.3 recipe_crafted takes exactly one literal recipe id (no #tag, no several); split $r into one criterion per recipe")
                }
                cond["recipe_id"] = id
            }
        }
    }

    private fun process(dir: File, downport: Boolean, convert: (Ctx, MutableMap<String, Any?>) -> Unit) {
        if (!dir.isDirectory) return
        val slurper = groovy.json.JsonSlurper()
        dir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
            val ctx = Ctx(f)
            val parsed = slurper.parse(f)
            @Suppress("UNCHECKED_CAST")
            val root = parsed as? MutableMap<String, Any?> ?: ctx.fail("JSON root must be an object, got: $parsed")
            convert(ctx, root) // validates on every node (throws on pre-26.3 keys)
            if (downport) f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(root)))
        }
    }

    // Entry point for a processed resources dir's `data/<namespace>` folder. downport=false (the 26.3
    // node and later) only validates that the sources are in the 26.3 form.
    fun apply(namespaceDataDir: File, downport: Boolean) {
        process(namespaceDataDir.resolve("loot_table"), downport, this::convertLootTable)
        process(namespaceDataDir.resolve("advancement"), downport, this::convertAdvancement)
    }
}
