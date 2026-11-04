/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.gradle

import java.io.File

// ─── NeoForge -> Fabric data transform ───────────────────────────────────────
// BC's data JSON is authored NeoForge-shaped (src/main/resources stays canonical). A Fabric node's processResources
// rewrites its COPIED output here, the way DataDownport1211 down-ports 1.21.1's copy:
//   1. "neoforge:conditions" -> "fabric:load_conditions", each condition translated to its Fabric API twin
//      (fabric-resource-conditions-api-v1; ids and field names read from the 26.3 jar). BC's own condition types
//      (buildcraftunofficial:*) pass through: DataServices.registerCondition registers them on Fabric too.
//   2. {"neoforge:ingredient_type": "neoforge:components", items, components, strict?} ->
//      {"fabric:type": "fabric:components", base, components} (fabric-recipe-api-v1's ComponentsIngredient, whose
//      match is NeoForge's non-strict one: the listed components must be present and equal).
//   3. data/neoforge/** and data/<ns>/neoforge/biome_modifier/** are dropped: Fabric reads neither. The biome
//      modifiers are code on Fabric (FabricDataServices); each dropped modifier must be one this file knows.
//   4. neoforge:fluid_container item models -> buildcraftunofficial:fluid_container, the Fabric twin of that model
//      type (same codec), with each NeoForge mask texture swapped for the Fabric node's own one.
// Anything it cannot translate fails the build with the file named, rather than shipping data Fabric would
// ignore or reject. verifyNoNeoForgeData then fails on any "neoforge:" left in the output.

object DataFabric {

    private const val NF = "neoforge:"

    // The only files data/neoforge/** holds, each with why dropping it is safe on Fabric. Anything else there fails.
    private val KNOWN_NEOFORGE_NAMESPACE_FILES = mapOf(
        // NeoForge's recipe-priority override (IronTanks wins the shared 8-glass tank grid). Fabric API has no
        // recipe priorities: on Fabric the tie falls to vanilla's recipe order. A known gap.
        "recipe_priorities.json" to "no Fabric twin",
    )

    // The biome modifiers a Fabric node drops, by file name, with the exact JSON each must hold and where Fabric
    // gets the same behaviour from. A new or changed modifier fails the build until its Fabric twin is written.
    private val KNOWN_BIOME_MODIFIERS: Map<String, Map<String, Any?>> = mapOf(
        // BCEnergyFeatures.init -> DataServices.registerAllBiomesFeature (BiomeSelectors.all() on Fabric).
        "add_oil.json" to mapOf("type" to "buildcraftunofficial:add_oil", "feature" to "buildcraftunofficial:oil"),
        // FabricDataServices.addTaggedBiomeFeatures.
        "add_quarry_pit.json" to mapOf(
            "type" to "neoforge:add_features", "biomes" to "#minecraft:is_overworld",
            "features" to "buildcraftunofficial:quarry_pit", "step" to "underground_decoration"
        ),
        "add_water_spring.json" to mapOf(
            "type" to "neoforge:add_features", "biomes" to "#minecraft:is_overworld",
            "features" to "buildcraftunofficial:water_spring", "step" to "underground_decoration"
        ),
    )

    // Strings that may remain in the processed output, each with the reason. Empty today.
    private val ALLOWED_NEOFORGE_STRINGS: Map<String, String> = emptyMap()

    /**
     * Runs the whole transform on a processed resources dir. [fabricDataCode] is FabricDataServices.java (this
     * node's view): every tag-selected biome feature a dropped modifier names must appear in it.
     */
    fun apply(resourcesDir: File, fabricDataCode: File) {
        val dataDir = resourcesDir.resolve("data")
        dropNeoForgeNamespace(dataDir.resolve("neoforge"))
        dropBiomeModifiers(dataDir, fabricDataCode.readText())
        dataDir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { transformDataFile(it) }
        resourcesDir.resolve("assets").listFiles()?.forEach { ns ->
            rewriteFluidContainerModels(ns.resolve("items"))
        }
    }

    private fun parse(f: File): Any? = groovy.json.JsonSlurper().parse(f)

    private fun write(f: File, root: Any?) {
        f.parentFile.mkdirs()
        f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(root)))
    }

    private fun fail(f: File, msg: String): Nothing =
        throw IllegalStateException("Fabric data transform: $msg in ${f.path}")

    // ─── 1 + 2: conditions and ingredients, anywhere in a data file ──────────

    private fun transformDataFile(f: File) {
        if (!f.readText().contains(NF)) return
        val root = parse(f)
        write(f, transformNode(root, f))
    }

    @Suppress("UNCHECKED_CAST")
    private fun transformNode(node: Any?, f: File): Any? = when (node) {
        is Map<*, *> -> {
            val m = node as Map<String, Any?>
            if (m.containsKey("neoforge:ingredient_type")) {
                componentsIngredient(m, f)
            } else {
                val out = LinkedHashMap<String, Any?>()
                for ((k, v) in m) {
                    if (k == "neoforge:conditions") {
                        val list = v as? List<*> ?: fail(f, "neoforge:conditions is not a list")
                        out["fabric:load_conditions"] = list.map { condition(it, f) }
                    } else {
                        out[k] = transformNode(v, f)
                    }
                }
                out
            }
        }
        is List<*> -> node.map { transformNode(it, f) }
        else -> node
    }

    @Suppress("UNCHECKED_CAST")
    private fun condition(node: Any?, f: File): Map<String, Any?> {
        val c = node as? Map<String, Any?> ?: fail(f, "a condition is not an object")
        val type = c["type"] as? String ?: fail(f, "a condition has no type")
        fun field(name: String): Any = c[name] ?: fail(f, "condition $type has no $name")
        fun list(name: String): List<*> = field(name) as? List<*> ?: fail(f, "condition $type's $name is not a list")
        fun fabric(id: String, vararg fields: Pair<String, Any?>) = linkedMapOf<String, Any?>("condition" to "fabric:$id", *fields)
        return when (type) {
            "neoforge:always" -> fabric("true")
            "neoforge:never" -> fabric("false")
            "neoforge:not" -> fabric("not", "value" to condition(field("value"), f))
            "neoforge:and" -> fabric("and", "values" to list("values").map { condition(it, f) })
            "neoforge:or" -> fabric("or", "values" to list("values").map { condition(it, f) })
            "neoforge:mod_loaded" -> fabric("all_mods_loaded", "values" to listOf(field("modid")))
            // NeoForge's registry field defaults to minecraft:item; Fabric's is written out so no default matters.
            "neoforge:tag_empty" -> fabric("not", "value" to fabric("tags_populated",
                "registry" to (c["registry"] ?: "minecraft:item"), "values" to listOf(field("tag"))))
            "neoforge:registered" -> fabric("registry_contains",
                "registry" to (c["registry"] ?: "minecraft:item"), "values" to listOf(field("value")))
            "neoforge:feature_flags_enabled" -> fabric("features_enabled", "features" to field("flags"))
            else -> {
                if (type.startsWith(NF) || type.startsWith("fabric:") || !type.contains(':')) {
                    fail(f, "condition type $type has no Fabric translation")
                }
                // A mod's own condition type: registered on Fabric under the same id (DataServices.registerCondition).
                val out = LinkedHashMap<String, Any?>()
                out["condition"] = type
                c.filterKeys { it != "type" }.forEach { (k, v) -> out[k] = v }
                out
            }
        }
    }

    private fun componentsIngredient(m: Map<String, Any?>, f: File): Map<String, Any?> {
        val type = m["neoforge:ingredient_type"]
        if (type != "neoforge:components") fail(f, "ingredient type $type has no Fabric translation")
        val unknown = m.keys - setOf("neoforge:ingredient_type", "items", "components", "strict")
        if (unknown.isNotEmpty()) fail(f, "neoforge:components ingredient has unknown keys $unknown")
        // Fabric's ComponentsIngredient has no exhaustive mode; NeoForge's strict=false is the same test.
        if (m["strict"] == true) fail(f, "a strict neoforge:components ingredient has no Fabric twin")
        // NeoForge's items is an item holder set (id, #tag or list); Fabric's base is an Ingredient, which on
        // 26.x has the same JSON forms.
        return linkedMapOf(
            "fabric:type" to "fabric:components",
            "base" to (m["items"] ?: fail(f, "neoforge:components ingredient has no items")),
            "components" to (m["components"] ?: fail(f, "neoforge:components ingredient has no components")),
        )
    }

    // ─── 3: NeoForge-only data dirs ──────────────────────────────────────────

    private fun dropNeoForgeNamespace(dir: File) {
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            KNOWN_NEOFORGE_NAMESPACE_FILES[f.relativeTo(dir).invariantSeparatorsPath]
                ?: fail(f, "data/neoforge file with no known Fabric handling (add it to DataFabric)")
        }
        dir.deleteRecursively()
    }

    private fun dropBiomeModifiers(dataDir: File, fabricDataCode: String) {
        dataDir.listFiles()?.forEach { ns ->
            ns.resolve("neoforge").listFiles()?.filter { it.name != "biome_modifier" }?.forEach {
                fail(it, "NeoForge-only data with no known Fabric handling (add it to DataFabric)")
            }
            val dir = ns.resolve("neoforge/biome_modifier")
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val expected = KNOWN_BIOME_MODIFIERS[f.relativeTo(dir).invariantSeparatorsPath]
                    ?: fail(f, "biome modifier with no Fabric twin (add it to DataFabric.KNOWN_BIOME_MODIFIERS)")
                if (parse(f) != expected) fail(f, "biome modifier differs from what its Fabric twin adds")
                val feature = expected["features"] as String?
                if (feature != null && !fabricDataCode.contains("\"$feature\"")) {
                    fail(f, "biome modifier's feature $feature is not added by FabricDataServices")
                }
            }
            ns.resolve("neoforge").deleteRecursively()
        }
    }

    // ─── 4: fluid_container item models ──────────────────────────────────────
    // Fabric's twin of NeoForge's dynamic fluid-container model is BuildCraft's own item model type,
    // buildcraftunofficial:fluid_container (FabricFluidContainerModel), whose codec is NeoForge's. Only the type id
    // changes, and each NeoForge texture it names becomes the Fabric node's own texture for that role (drawn for
    // BuildCraft, in src/main/resources-fabric; NeoForge's LGPL masks are not copied).

    // NeoForge texture -> the Fabric node's own texture of the same role. Any other neoforge: texture fails.
    private val FLUID_CONTAINER_TEXTURES = mapOf(
        "neoforge:item/mask/bucket_fluid_drip" to "buildcraftunofficial:item/mask/bucket_fluid",
    )

    @Suppress("UNCHECKED_CAST")
    private fun rewriteFluidContainerModels(itemsDir: File) {
        if (!itemsDir.isDirectory) return
        itemsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
            val root = parse(f) as? Map<String, Any?> ?: return@forEach
            val spec = root["model"] as? Map<String, Any?> ?: return@forEach
            if (spec["type"] != "neoforge:fluid_container") return@forEach
            val textures = spec["textures"] as? Map<String, Any?>
                ?: fail(f, "neoforge:fluid_container model has no textures")
            val outTextures = LinkedHashMap<String, Any?>()
            textures.forEach { (slot, tex) ->
                outTextures[slot] = if (tex is String && tex.startsWith(NF)) {
                    FLUID_CONTAINER_TEXTURES[tex] ?: fail(f, "no Fabric twin of fluid-container texture $tex")
                } else tex
            }
            val model = LinkedHashMap(spec)
            model["type"] = "buildcraftunofficial:fluid_container"
            model["textures"] = outTextures
            val out = LinkedHashMap(root)
            out["model"] = model
            write(f, out)
        }
    }

    // ─── The guard ───────────────────────────────────────────────────────────

    /** Fails if any processed resource still contains "neoforge:" outside [ALLOWED_NEOFORGE_STRINGS]. */
    fun verifyNoNeoForgeData(resourcesDir: File) {
        val needle = NF.toByteArray()
        val offenders = resourcesDir.walkTopDown().filter { it.isFile }.filter { f ->
            val text = f.readBytes()
            if (indexOf(text, needle) < 0) return@filter false
            var s = String(text, Charsets.UTF_8)
            ALLOWED_NEOFORGE_STRINGS.keys.forEach { s = s.replace(it, "") }
            s.contains(NF)
        }.map { it.relativeTo(resourcesDir).invariantSeparatorsPath }.toList()
        if (offenders.isNotEmpty()) {
            throw IllegalStateException(
                "Fabric data transform: \"neoforge:\" left in the processed resources (translate it in DataFabric, " +
                    "or allow it there with a reason): ${offenders.joinToString()}"
            )
        }
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
