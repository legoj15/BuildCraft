/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.gradle

import java.io.File

// ─── 1.21.1 recipe data-format down-port ─────────────────────────────────────
// The recipe JSONs are hand-authored in the 1.21.2+ datapack format: bare-string ingredients
// ("#c:dyes/black", "buildcraftunofficial:gear") and the record-shaped minecraft:custom_model_data
// ({"strings":[...],"floats":[...]}). MC 1.21.1 wants object ingredients ({"tag":...}/{"item":...})
// and a single-int custom_model_data. Stonecutter doesn't touch resources, so we transform the
// COPIED output (src stays the canonical modern form; only the 1.21.1 node's processResources runs
// this — the released nodes get no doLast and are byte-identical).
//
// Moved out of the build script so the processResources/processTestResources doLast actions do not
// capture script-object references, which Gradle's configuration cache cannot serialize.

object DataDownport1211 {

    // ItemDisplayContext's serialized names on 1.21.1, the keys separate_transforms looks perspectives up by.
    private val CONTEXTS_1211 = setOf(
        "none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand",
        "head", "gui", "ground", "fixed"
    )

    fun convIngredient1211(v: Any?): Any? = when (v) {
        is String -> if (v.startsWith("#")) linkedMapOf("tag" to v.substring(1)) else linkedMapOf("item" to v)
        is List<*> -> v.map { convIngredient1211(it) }
        else -> v
    }

    // Recursively down-port nested 1.21.2+ recipe-JSON shapes to 1.21.1:
    //  - rewrite minecraft:custom_model_data from its 1.21.5+ record form ({strings/floats}) to the
    //    1.21.1 single int 0 (result components AND component-matching ingredients like neoforge:components
    //    for gate variants). NOT dropped: BC's 1.21.1 gate/pipe items carry CustomModelData(0) (the
    //    multi-string variant routing collapses to a single int on 1.21.1; the real variant identity is
    //    in custom_data), and the gate-recipe game tests assert the crafted output has CustomModelData[0];
    //  - rename the custom-ingredient dispatch key "neoforge:ingredient_type" -> "type": 1.21.1's
    //    CraftingHelper.makeIngredientMapCodec dispatches on "type" (NeoForgeExtraCodecs.dispatchMapOrElse),
    //    a key NeoForge renamed in 1.21.2. Safe: only ingredient objects carry it; the recipe root's
    //    own "type" (e.g. minecraft:crafting_shaped) has no neoforge:ingredient_type to clash with.
    @Suppress("UNCHECKED_CAST")
    fun stripCustomModelData(node: Any?) {
        when (node) {
            is MutableMap<*, *> -> {
                val m = node as MutableMap<String, Any?>
                if (m.containsKey("minecraft:custom_model_data")) {
                    m["minecraft:custom_model_data"] = 0
                }
                if (m.containsKey("neoforge:ingredient_type")) {
                    m["type"] = m.remove("neoforge:ingredient_type")
                }
                m.values.toList().forEach { stripCustomModelData(it) }
            }
            is List<*> -> node.forEach { stripCustomModelData(it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun convertRecipe1211(root: Any?) {
        val r = root as? MutableMap<String, Any?> ?: return
        (r["key"] as? MutableMap<String, Any?>)?.let { key ->
            for (k in key.keys.toList()) key[k] = convIngredient1211(key[k])
        }
        (r["ingredients"] as? List<Any?>)?.let { r["ingredients"] = it.map { e -> convIngredient1211(e) } }
        if (r.containsKey("ingredient")) r["ingredient"] = convIngredient1211(r["ingredient"])
        stripCustomModelData(r)
    }

    // Rewrite advancement background values from the 1.21.10+ bare-id form ("namespace:path") to the
    // 1.21.1 full-path form ("namespace:textures/path.png"). On 1.21.1 the background field is a raw
    // ResourceLocation blitted directly as a file path; on 1.21.10+ it is wrapped in ClientAsset.ResourceTexture
    // which auto-prepends "textures/" and appends ".png". No-op if the dir is absent.
    @Suppress("UNCHECKED_CAST")
    fun downportAdvancementBackground1211(advancementDir: File) {
        if (!advancementDir.isDirectory) return
        val slurper = groovy.json.JsonSlurper()
        advancementDir.walkTopDown().filter { it.extension == "json" }.forEach { f ->
            val root = slurper.parse(f) as? MutableMap<String, Any?> ?: return@forEach
            val display = root["display"] as? MutableMap<String, Any?> ?: return@forEach
            val bg = display["background"] as? String ?: return@forEach
            val colon = bg.indexOf(':')
            if (colon >= 0) {
                display["background"] = "${bg.substring(0, colon)}:textures/${bg.substring(colon + 1)}.png"
                f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(root)))
            }
        }
    }

    // Down-port every recipe JSON in a processed `…/recipe` dir in place (no-op if the dir is absent).
    // Shared by the main and test processResources hooks so test-only fixture recipes (e.g. the
    // cycle-output game tests' test_cycle_a/b) get the same 1.21.1 treatment as the shipped recipes.
    fun downportRecipeDir1211(recipeDir: File) {
        if (!recipeDir.isDirectory) return
        val slurper = groovy.json.JsonSlurper()
        recipeDir.listFiles()?.filter { it.extension == "json" }?.forEach { f ->
            val root = slurper.parse(f)
            convertRecipe1211(root)
            f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(root)))
        }
    }

    // 1.21.1 ITEM-MODEL FORMAT BACKPORT (1.21.1 node only — modern src untouched, released nodes
    // byte-identical). 1.21.4+ defines item models in assets/<ns>/items/<name>.json (the component-driven
    // model selector: minecraft:model / minecraft:range_dispatch / select / condition). 1.21.1 has none of
    // that — it reads the classic assets/<ns>/models/item/<name>.json (parent + textures + custom_model_data
    // overrides). Without a classic model an item shows MISSING TEXTURE in inventories (the in-world block
    // still renders from its blockstate). So for every items/ definition we synthesise the classic model:
    //   • minecraft:model            -> { parent: <referenced model> }  (only when no hand-authored classic
    //                                    model already ships — never clobber a textured one)
    //   • minecraft:range_dispatch   -> classic base (existing model, or parent=fallback) + classic
    //     (custom_model_data)            `overrides` translated from the range entries. The matching items
    //                                    set CUSTOM_MODEL_DATA on 1.21.1 (gated `else` branches in
    //                                    Item{Paintbrush_BC8,List_BC8,MapLocation,GateCopier}), so the
    //                                    overrides resolve the colour/state variant the selector picked.
    //   • minecraft:select           -> NeoForge's per-view loader, neoforge:separate_transforms, which
    //     (display_context)              replaces the classic model: the fallback's content is the base,
    //                                    each case's contexts map to its model (the Goggles: a 3D model on
    //                                    the head, the flat icon everywhere else). Any other select
    //                                    property fails the build: no 1.21.1 equivalent.
    @Suppress("UNCHECKED_CAST")
    fun generateOldItemModels1211(itemsDir: File, modelsItemDir: File) {
        if (!itemsDir.isDirectory) return
        val slurper = groovy.json.JsonSlurper()
        fun write(file: File, model: Any?) {
            file.parentFile.mkdirs()
            file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(model)))
        }
        val namespace = modelsItemDir.parentFile.parentFile.name
        val itemFiles = itemsDir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        // Every classic model a select rewrite replaces, known before any is written, so whether one select
        // may point at another's does not depend on the order the files are listed in.
        val selectIds = itemFiles.filter { ((slurper.parse(it) as? Map<*, *>)?.get("model") as? Map<*, *>)?.get("type") == "minecraft:select" }
            .map { "$namespace:item/${it.nameWithoutExtension}" }.toSet()
        itemFiles.forEach { f ->
            val root = slurper.parse(f) as? Map<String, Any?> ?: return@forEach
            val spec = root["model"] as? Map<String, Any?> ?: return@forEach
            val outFile = modelsItemDir.resolve("${f.nameWithoutExtension}.json")
            when (spec["type"]) {
                "minecraft:model" -> {
                    if (!outFile.exists()) write(outFile, linkedMapOf("parent" to spec["model"]))
                }
                "neoforge:fluid_container" -> {
                    // Fluid buckets: 1.21.4+ items/ uses `"type": "neoforge:fluid_container"`; the classic
                    // models/item/ form is the same dynamic model under `"loader"` (same id, same fields).
                    if (!outFile.exists()) {
                        val model = linkedMapOf<String, Any?>("loader" to "neoforge:fluid_container")
                        spec.forEach { (k, v) -> if (k != "type") model[k] = v }
                        // The classic loader takes its item transforms from THIS json's "display" block
                        // (26.x sources them from item/generated itself instead) — absent, every
                        // fluid_container item rendered at raw 16x16 scale when held/dropped/worn.
                        // Stamp the standard flat-item display, verbatim from vanilla item/generated
                        // on this line, unless the source carries its own.
                        if (!model.containsKey("display")) {
                            model["display"] = linkedMapOf(
                                "ground" to linkedMapOf(
                                    "rotation" to listOf(0, 0, 0), "translation" to listOf(0, 2, 0),
                                    "scale" to listOf(0.5, 0.5, 0.5)
                                ),
                                "head" to linkedMapOf(
                                    "rotation" to listOf(0, 180, 0), "translation" to listOf(0, 13, 7),
                                    "scale" to listOf(1, 1, 1)
                                ),
                                "thirdperson_righthand" to linkedMapOf(
                                    "rotation" to listOf(0, 0, 0), "translation" to listOf(0, 3, 1),
                                    "scale" to listOf(0.55, 0.55, 0.55)
                                ),
                                "firstperson_righthand" to linkedMapOf(
                                    "rotation" to listOf(0, -90, 25), "translation" to listOf(1.13, 3.2, 1.13),
                                    "scale" to listOf(0.68, 0.68, 0.68)
                                ),
                                "fixed" to linkedMapOf(
                                    "rotation" to listOf(0, 180, 0), "scale" to listOf(1, 1, 1)
                                )
                            )
                        }
                        write(outFile, model)
                    }
                }
                "minecraft:range_dispatch" -> {
                    if (spec["property"] == "minecraft:custom_model_data") {
                        val entries = (spec["entries"] as? List<Map<String, Any?>>) ?: emptyList()
                        val overrides = entries.map { e ->
                            linkedMapOf(
                                "predicate" to linkedMapOf("custom_model_data" to e["threshold"]),
                                "model" to (e["model"] as Map<String, Any?>)["model"]
                            )
                        }
                        val out: MutableMap<String, Any?> = if (outFile.exists()) {
                            slurper.parse(outFile) as MutableMap<String, Any?>
                        } else {
                            linkedMapOf("parent" to (spec["fallback"] as Map<String, Any?>)["model"])
                        }
                        out["overrides"] = overrides
                        write(outFile, out)
                    }
                }
                "minecraft:select" -> {
                    check(spec["property"] == "minecraft:display_context") {
                        "items/${f.name}: select on ${spec["property"]} has no 1.21.1 equivalent in DataDownport1211"
                    }
                    // 1.21.1 has nowhere to put a whole-item transformation (the classic model only has
                    // per-view display blocks), so one on the select would silently vanish.
                    check("transformation" !in spec) {
                        "items/${f.name}: a select-level transformation has no 1.21.1 equivalent in DataDownport1211"
                    }
                    // This output replaces the item's hand-authored classic model, so no case may point at it.
                    val ownId = "$namespace:item/${f.nameWithoutExtension}"
                    // A plain minecraft:model of this mod, read from the processed output. Tints and
                    // transformation have no 1.21.1 form; overrides are read only from the top-level model on
                    // 1.21.1, so on an inlined base or a perspective parent they would never apply.
                    fun modelOf(node: Any?, what: String): Pair<String, Map<String, Any?>> {
                        val m = node as? Map<String, Any?> ?: error("items/${f.name}: the $what is not an object")
                        val id = m["model"] as? String
                        check(m["type"] == "minecraft:model" && id != null) {
                            "items/${f.name}: the $what must be a plain minecraft:model with a model id"
                        }
                        val extra = m.keys - setOf("type", "model")
                        check(extra.isEmpty()) { "items/${f.name}: the $what's $extra has no 1.21.1 equivalent" }
                        check(id.substringBefore(':') == namespace) { "items/${f.name}: the $what $id is not a model of this mod" }
                        // Another select's classic model is replaced by its wrapper, which 1.21.1 cannot nest.
                        check(id == ownId || id !in selectIds) {
                            "items/${f.name}: the $what $id is another select item's model, which its own rewrite replaces"
                        }
                        val file = modelsItemDir.parentFile.resolve(id.substringAfter(':') + ".json")
                        check(file.isFile) { "items/${f.name}: the $what $id does not exist ($file)" }
                        val content = runCatching { slurper.parse(file) }.getOrElse {
                            error("items/${f.name}: the $what $id is not valid JSON ($file): ${it.message}")
                        } as? Map<String, Any?> ?: error("items/${f.name}: the $what $id is not a JSON object")
                        check("overrides" !in content) { "items/${f.name}: the $what $id has overrides, which 1.21.1 drops under a per-view model" }
                        // Already a per-view model (hand-authored, or this rewrite's output on a second pass over
                        // the same output): inlining or parenting it would nest one wrapper in another.
                        check(content["loader"] != "neoforge:separate_transforms") {
                            "items/${f.name}: the $what $id is already a neoforge:separate_transforms model, which 1.21.1 cannot nest"
                        }
                        return id to content
                    }
                    val cases = spec["cases"] as? List<*>
                    check(!cases.isNullOrEmpty()) { "items/${f.name}: a select needs a non-empty cases list" }
                    val perspectives = linkedMapOf<String, Any?>()
                    for ((i, raw) in cases.withIndex()) {
                        val case = raw as? Map<String, Any?> ?: error("items/${f.name}: case $i is not an object")
                        val target = modelOf(case["model"], "case $i model").first
                        check(target != ownId) { "items/${f.name}: case $i points at $ownId, the model this rewrite replaces" }
                        val contexts = case["when"].let { if (it is List<*>) it else listOfNotNull(it) }
                        check(contexts.isNotEmpty()) { "items/${f.name}: case $i has no \"when\"" }
                        contexts.forEach { c ->
                            val name = c as? String ?: error("items/${f.name}: case $i \"when\" holds a non-string $c")
                            check(name !in perspectives) { "items/${f.name}: display context \"$name\" is named twice" }
                            // An unknown name would never match on 1.21.1: that view would quietly keep the base.
                            check(name in CONTEXTS_1211) { "items/${f.name}: 1.21.1 has no display context \"$name\"" }
                            perspectives[name] = linkedMapOf("parent" to target)
                        }
                    }
                    // The fallback is usually the very model this rewrite replaces (the goggles' flat icon), so
                    // its content becomes the base inline instead of a parent reference. 1.21.1 bakes a nested
                    // item/generated model into the flat icon like a top-level one. GUI lighting comes from the
                    // wrapper's own gui_light, and a wrapper with no parent defaults to "side" (lit like a
                    // block), so it carries the flat items' "front". The particle is looked up in the unbaked
                    // base, and vanilla's item/generated names none (a plain icon gets layer0 only when its icon
                    // is generated), so the base carries it. One difference stays: ItemEntityRenderer lifts a
                    // dropped item by a quarter of the ground scale the model reports, and the wrapper reports
                    // no transforms (scale 1, not item/generated's 0.5), so it floats 2 px higher than a plain
                    // icon. Drawing still goes through the base's transforms.
                    val fallback = modelOf(spec["fallback"] ?: error("items/${f.name}: a select needs a fallback, the 1.21.1 base"), "fallback").second
                    val wrapper = linkedMapOf<String, Any?>("loader" to "neoforge:separate_transforms")
                    val base = LinkedHashMap(fallback)
                    val flat = (fallback["parent"] as? String)?.removePrefix("minecraft:") in setOf("item/generated", "item/handheld")
                    if (flat) {
                        (fallback["gui_light"] ?: "front").let { wrapper["gui_light"] = it }
                        val textures = LinkedHashMap((fallback["textures"] as? Map<String, Any?>) ?: emptyMap())
                        if ("particle" !in textures && "layer0" in textures) textures["particle"] = textures["layer0"]
                        base["textures"] = textures
                    } else {
                        fallback["gui_light"]?.let { wrapper["gui_light"] = it }
                    }
                    wrapper["base"] = base
                    wrapper["perspectives"] = perspectives
                    write(outFile, wrapper)
                }
            }
        }
    }
}
