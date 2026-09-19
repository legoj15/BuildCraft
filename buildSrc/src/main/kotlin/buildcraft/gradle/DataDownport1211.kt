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
    @Suppress("UNCHECKED_CAST")
    fun generateOldItemModels1211(itemsDir: File, modelsItemDir: File) {
        if (!itemsDir.isDirectory) return
        val slurper = groovy.json.JsonSlurper()
        fun write(file: File, model: Any?) {
            file.parentFile.mkdirs()
            file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(model)))
        }
        itemsDir.listFiles()?.filter { it.extension == "json" }?.forEach { f ->
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
            }
        }
    }
}
