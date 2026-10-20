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
// Deliberately narrow: only the shapes BC ships are converted. Anything else (functions/"modifier",
// entity_properties predicates, predicate references, untested triggers) fails the build with a
// message naming the file, so a new data file cannot silently mis-load on one side.
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

    // A loot pool or entry: "condition" -> "conditions": [...]; functions are not supported.
    @Suppress("UNCHECKED_CAST")
    private fun convertConditionHolder(ctx: Ctx, m: MutableMap<String, Any?>) {
        if (m.containsKey("conditions")) ctx.fail("uses the pre-26.3 \"conditions\" list; author a single \"condition\" object")
        if (m.containsKey("functions") || m.containsKey("modifier")) ctx.fail("loot functions/modifiers have no down-port yet")
        if (m.containsKey("condition")) {
            m["conditions"] = listOf(convertCondition(ctx, m.remove("condition")))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertLootTable(ctx: Ctx, root: MutableMap<String, Any?>) {
        if (root.containsKey("functions") || root.containsKey("modifier")) ctx.fail("loot functions/modifiers have no down-port yet")
        (root["pools"] as? List<Any?>)?.forEach { p ->
            val pool = p as? MutableMap<String, Any?> ?: ctx.fail("pool is not an object")
            convertConditionHolder(ctx, pool)
            (pool["entries"] as? List<Any?>)?.forEach { e ->
                convertConditionHolder(ctx, e as? MutableMap<String, Any?> ?: ctx.fail("entry is not an object"))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertAdvancement(ctx: Ctx, root: MutableMap<String, Any?>) {
        val criteria = root["criteria"] as? Map<String, Any?> ?: return
        for ((name, c) in criteria) {
            val crit = c as? Map<String, Any?> ?: ctx.fail("criterion $name is not an object")
            val trigger = crit["trigger"] as? String ?: ctx.fail("criterion $name has no trigger")
            if (trigger !in STABLE_TRIGGERS) ctx.fail("trigger $trigger (criterion $name) has no verified down-port; check its codec on every node and add it to STABLE_TRIGGERS")
            val cond = crit["conditions"] as? MutableMap<String, Any?> ?: continue
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
                    r is List<*> && r.size == 1 && r[0] is String -> r[0] as String
                    else -> ctx.fail("criterion $name: pre-26.3 recipe_crafted takes exactly one recipe id; split $r into one criterion per recipe")
                }
                cond["recipe_id"] = id
            }
        }
    }

    private fun process(dir: File, downport: Boolean, convert: (Ctx, MutableMap<String, Any?>) -> Unit) {
        if (!dir.isDirectory) return
        val slurper = groovy.json.JsonSlurper()
        dir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
            @Suppress("UNCHECKED_CAST")
            val root = slurper.parse(f) as? MutableMap<String, Any?> ?: return@forEach
            convert(Ctx(f), root) // validates on every node (throws on pre-26.3 keys)
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
