/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.gradle

import java.io.File

// ─── NeoForge -> MinecraftForge data transform ───────────────────────────────
// BC's data JSON is authored NeoForge-shaped (src/main/resources stays canonical). The Forge node's processResources
// rewrites its COPIED output here, the way DataFabric does for Fabric. Every id and field below was read from Forge
// 26.3-66.1.8's own sources (.forge-ref/sources-26.3-66.1.8) and universal jar, not guessed:
//   1. "neoforge:conditions" (a list) -> "forge:condition" (ONE condition, ICondition.DEFAULT_FIELD), each condition
//      translated to its Forge twin. Forge reads that key at the top of any datum loaded through a data-driven registry
//      (the ResourceManagerRegistryLoadTask patch wraps the element codec in ConditionCodec, which covers recipes on 26.x)
//      or a SimpleJsonResourceReloadListener, and nowhere deeper, so a condition anywhere but the file's root fails the
//      build. A list of several becomes forge:and. BC's own condition types (buildcraftunofficial:*) pass through:
//      ForgeDataServices registers them in forge:condition_codecs under the same id.
//   2. {"neoforge:ingredient_type": "neoforge:components", items, components, strict?} ->
//      {"type": "buildcraftunofficial:components", items, components}. Forge has no component-matching ingredient (its
//      only stack-matching one compares the custom_data tag), so the type is BuildCraft's own, a serializer registered in
//      forge:ingredient_serializers by ForgeCraftingServices with NeoForge's non-strict semantics.
//   3. data/neoforge/** is dropped (recipe_priorities: Forge has no recipe priorities). data/<ns>/neoforge/biome_modifier/**
//      moves to data/<ns>/forge/biome_modifier/ (Forge's datapack registry forge:biome_modifier), with neoforge:add_features
//      renamed forge:add_features (same fields: biomes, features, step). BC's own modifier type
//      (buildcraftunofficial:add_oil) passes through: ForgeDataServices registers it in forge:biome_modifier_serializers.
//   4. neoforge:fluid_container item models -> buildcraftunofficial:fluid_container, BuildCraft's own item model type
//      with NeoForge's fields (ForgeFluidContainerModel, the Forge twin of FabricFluidContainerModel), and NeoForge's
//      mask texture becomes Forge's own mask of the same name (assets/forge/textures/item/mask/, in Forge's universal
//      jar). Not Forge's forge:fluid_container: that is a block-model geometry baked for the one fluid its JSON names
//      (the fragile shard could not show its contents), it looks for the fluid's sprite on the item atlas, and Forge
//      26.3 ships no tint for its fluid layer.
//   5. 1.21.1 only (after DataDownport1211): the classic models/item files that down-port writes name NeoForge model
//      loaders (neoforge:fluid_container for the 31 fluid containers, neoforge:separate_transforms for the Goggles).
//      neoforge:separate_transforms becomes Forge 52's forge:separate_transforms (same fields: base, perspectives).
//      neoforge:fluid_container becomes buildcraftunofficial:fluid_container, ForgeFluidContainerGeometry: Forge 52's own
//      DynamicFluidContainerModel (same fields, read by Forge's own reader) but with the contained fluid read through
//      BuildCraft's transfer layer, as Forge 52's FluidUtil never finds an item's fluid handler (the fragile shard would
//      show no fluid). Its mask texture becomes Forge's mask of the same name, as in step 4. Step 4 still rewrites the
//      down-port's items/ definitions, which 1.21.1 never reads, so the guard below holds the same on every line.
// Anything it cannot translate fails the build with the file named, rather than shipping data Forge would ignore or
// reject. verifyNoNeoForgeData then fails on any "neoforge:" left in the output.

object DataForge {

    private const val NF = "neoforge:"
    private const val CONDITION_FIELD = "forge:condition"

    // BuildCraft's components ingredient type id: ForgeCraftingServices registers its serializer under this id.
    private const val COMPONENTS_INGREDIENT_TYPE = "buildcraftunofficial:components"

    // The only files data/neoforge/** holds, each with why dropping it is safe on Forge. Anything else there fails.
    private val KNOWN_NEOFORGE_NAMESPACE_FILES = mapOf(
        // NeoForge's recipe-priority override (IronTanks wins the shared 8-glass tank grid). Forge has no recipe
        // priorities (nothing in its sources or patches): the tie falls to vanilla's recipe order. A known gap, as on Fabric.
        "recipe_priorities.json" to "no Forge twin",
    )

    // NeoForge biome-modifier types and the Forge type each becomes, with the fields both read (anything else fails).
    private val BIOME_MODIFIER_TYPES = mapOf(
        "neoforge:add_features" to ("forge:add_features" to setOf("type", "biomes", "features", "step")),
    )

    // Forge's own mask textures (assets/forge/textures/item/mask/ in its universal jar), by file name. NeoForge's mask of
    // the same name becomes Forge's. Any other neoforge: texture fails.
    private val FORGE_MASKS = setOf("bucket_fluid", "bucket_fluid_cover", "bucket_fluid_cover_drip", "bucket_fluid_drip")

    // What a neoforge:fluid_container item model may carry: the fields ForgeFluidContainerModel's codec reads, with
    // NeoForge's defaults.
    private val FLUID_CONTAINER_KEYS =
        setOf("type", "textures", "fluid", "flip_gas", "cover_is_mask", "apply_fluid_luminosity", "force_opaque_fluid")

    // Strings that may remain in the processed output, each with the reason. Empty today.
    private val ALLOWED_NEOFORGE_STRINGS: Map<String, String> = emptyMap()

    /** Runs the whole transform on a processed resources dir. */
    fun apply(resourcesDir: File) {
        val dataDir = resourcesDir.resolve("data")
        dropNeoForgeNamespace(dataDir.resolve("neoforge"))
        moveBiomeModifiers(dataDir)
        dataDir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { transformDataFile(it) }
        resourcesDir.resolve("assets").listFiles()?.forEach { ns ->
            renameClassicModelLoaders(ns)
            rewriteFluidContainerModels(ns)
        }
    }

    // NeoForge model loaders a classic (pre-1.21.4) model file may name, and the id each becomes on Forge 52 (step 5).
    private val CLASSIC_MODEL_LOADERS = mapOf(
        "neoforge:fluid_container" to "buildcraftunofficial:fluid_container",
        "neoforge:separate_transforms" to "forge:separate_transforms",
    )

    // ─── 5: classic model files (the 1.21.1 down-port's output) ──────────────

    @Suppress("UNCHECKED_CAST")
    private fun renameClassicModelLoaders(ns: File) {
        val modelsDir = ns.resolve("models")
        if (!modelsDir.isDirectory) return
        modelsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.toList().forEach { f ->
            if (!f.readText().contains(NF)) return@forEach
            val root = parse(f) as? Map<String, Any?> ?: return@forEach
            val loader = root["loader"] as? String ?: return@forEach
            val forgeLoader = CLASSIC_MODEL_LOADERS[loader] ?: fail(f, "model loader $loader has no Forge twin")
            val out = LinkedHashMap<String, Any?>(root)
            out["loader"] = forgeLoader
            (root["textures"] as? Map<String, Any?>)?.let { textures ->
                out["textures"] = textures.mapValues { (_, tex) -> if (tex is String && tex.startsWith(NF)) forgeMask(tex, f) else tex }
            }
            write(f, out)
        }
    }

    private fun parse(f: File): Any? = groovy.json.JsonSlurper().parse(f)

    private fun write(f: File, root: Any?) {
        f.parentFile.mkdirs()
        f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(root)))
    }

    private fun fail(f: File, msg: String): Nothing =
        throw IllegalStateException("Forge data transform: $msg in ${f.path}")

    // ─── 1 + 2: conditions and ingredients, in a data file ───────────────────

    private fun transformDataFile(f: File) {
        if (!f.readText().contains(NF)) return
        val root = parse(f)
        write(f, transformNode(root, f, isRoot = true))
    }

    @Suppress("UNCHECKED_CAST")
    private fun transformNode(node: Any?, f: File, isRoot: Boolean): Any? = when (node) {
        is Map<*, *> -> {
            val m = node as Map<String, Any?>
            // The second form is the 1.21.1 down-port's (DataDownport1211 renames the dispatch key to "type", which
            // 1.21.1's NeoForge reads), so the same ingredient arrives either way.
            if (m.containsKey("neoforge:ingredient_type") || m["type"] == "neoforge:components") {
                componentsIngredient(m, f)
            } else {
                val out = LinkedHashMap<String, Any?>()
                for ((k, v) in m) {
                    if (k == "neoforge:conditions") {
                        if (!isRoot) fail(f, "neoforge:conditions below the file's root (Forge reads $CONDITION_FIELD only there)")
                        val list = v as? List<*> ?: fail(f, "neoforge:conditions is not a list")
                        val conditions = list.map { condition(it, f) }
                        // An empty list is "always": no key at all is the same answer.
                        when (conditions.size) {
                            0 -> Unit
                            1 -> out[CONDITION_FIELD] = conditions[0]
                            else -> out[CONDITION_FIELD] = forge("and", "values" to conditions)
                        }
                    } else {
                        out[k] = transformNode(v, f, isRoot = false)
                    }
                }
                out
            }
        }
        is List<*> -> node.map { transformNode(it, f, isRoot = false) }
        else -> node
    }

    private fun forge(id: String, vararg fields: Pair<String, Any?>) =
        linkedMapOf<String, Any?>("type" to "forge:$id", *fields)

    @Suppress("UNCHECKED_CAST")
    private fun condition(node: Any?, f: File): Map<String, Any?> {
        val c = node as? Map<String, Any?> ?: fail(f, "a condition is not an object")
        val type = c["type"] as? String ?: fail(f, "a condition has no type")
        fun field(name: String): Any = c[name] ?: fail(f, "condition $type has no $name")
        fun list(name: String): List<*> = field(name) as? List<*> ?: fail(f, "condition $type's $name is not a list")
        // Forge's tag_empty and item_exists read item ids and tags only; NeoForge's name a registry (default item).
        fun itemRegistryOnly() {
            val registry = c["registry"] ?: "minecraft:item"
            if (registry != "minecraft:item") fail(f, "condition $type on registry $registry has no Forge twin (Forge's is items only)")
        }
        return when (type) {
            "neoforge:always" -> forge("true")
            "neoforge:never" -> forge("false")
            "neoforge:not" -> forge("not", "value" to condition(field("value"), f))
            "neoforge:and" -> forge("and", "values" to list("values").map { condition(it, f) })
            "neoforge:or" -> forge("or", "values" to list("values").map { condition(it, f) })
            "neoforge:mod_loaded" -> forge("mod_loaded", "modid" to field("modid"))
            "neoforge:tag_empty" -> { itemRegistryOnly(); forge("tag_empty", "tag" to field("tag")) }
            "neoforge:registered" -> { itemRegistryOnly(); forge("item_exists", "item" to field("value")) }
            else -> {
                if (type.startsWith(NF) || type.startsWith("forge:") || !type.contains(':')) {
                    fail(f, "condition type $type has no Forge translation")
                }
                // A mod's own condition type: registered in forge:condition_codecs under the same id (DataServices.registerCondition).
                c
            }
        }
    }

    private fun componentsIngredient(m: Map<String, Any?>, f: File): Map<String, Any?> {
        val type = m["neoforge:ingredient_type"] ?: m["type"]
        if (type != "neoforge:components") fail(f, "ingredient type $type has no Forge translation")
        val unknown = m.keys - setOf("neoforge:ingredient_type", "type", "items", "components", "strict")
        if (unknown.isNotEmpty()) fail(f, "neoforge:components ingredient has unknown keys $unknown")
        // BuildCraft's ingredient is NeoForge's non-strict test only (the listed components present and equal).
        if (m["strict"] == true) fail(f, "a strict neoforge:components ingredient has no Forge twin")
        return linkedMapOf(
            "type" to COMPONENTS_INGREDIENT_TYPE,
            "items" to (m["items"] ?: fail(f, "neoforge:components ingredient has no items")),
            "components" to (m["components"] ?: fail(f, "neoforge:components ingredient has no components")),
        )
    }

    // ─── 3: NeoForge-only data dirs ──────────────────────────────────────────

    private fun dropNeoForgeNamespace(dir: File) {
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            KNOWN_NEOFORGE_NAMESPACE_FILES[f.relativeTo(dir).invariantSeparatorsPath]
                ?: fail(f, "data/neoforge file with no known Forge handling (add it to DataForge)")
        }
        dir.deleteRecursively()
    }

    @Suppress("UNCHECKED_CAST")
    private fun moveBiomeModifiers(dataDir: File) {
        dataDir.listFiles()?.forEach { ns ->
            val nf = ns.resolve("neoforge")
            nf.listFiles()?.filter { it.name != "biome_modifier" }?.forEach {
                fail(it, "NeoForge-only data with no known Forge handling (add it to DataForge)")
            }
            val dir = nf.resolve("biome_modifier")
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val root = parse(f) as? Map<String, Any?> ?: fail(f, "a biome modifier is not an object")
                val type = root["type"] as? String ?: fail(f, "a biome modifier has no type")
                val out: Map<String, Any?> = if (type.startsWith(NF)) {
                    val (forgeType, fields) = BIOME_MODIFIER_TYPES[type]
                        ?: fail(f, "biome modifier type $type has no Forge twin (add it to DataForge.BIOME_MODIFIER_TYPES)")
                    val extra = root.keys - fields
                    if (extra.isNotEmpty()) fail(f, "biome modifier $type has fields Forge's $forgeType does not read: $extra")
                    LinkedHashMap(root).also { it["type"] = forgeType }
                } else {
                    // BuildCraft's own type: registered under the same id by ForgeDataServices.
                    root
                }
                write(ns.resolve("forge/biome_modifier").resolve(f.relativeTo(dir).path), out)
            }
            nf.deleteRecursively()
        }
    }

    // ─── 4: fluid_container item models ──────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun rewriteFluidContainerModels(ns: File) {
        val itemsDir = ns.resolve("items")
        if (!itemsDir.isDirectory) return
        itemsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.toList().forEach { f ->
            val root = parse(f) as? Map<String, Any?> ?: return@forEach
            val spec = root["model"] as? Map<String, Any?> ?: return@forEach
            if (spec["type"] != "neoforge:fluid_container") return@forEach
            val unknown = spec.keys - FLUID_CONTAINER_KEYS
            if (unknown.isNotEmpty()) fail(f, "neoforge:fluid_container has fields ForgeFluidContainerModel does not read: $unknown")
            val textures = spec["textures"] as? Map<String, Any?>
                ?: fail(f, "neoforge:fluid_container model has no textures")
            val outTextures = LinkedHashMap<String, Any?>()
            textures.forEach { (slot, tex) ->
                outTextures[slot] = if (tex is String && tex.startsWith(NF)) forgeMask(tex, f) else tex
            }
            val model = LinkedHashMap(spec)
            model["type"] = "buildcraftunofficial:fluid_container"
            model["textures"] = outTextures
            val out = LinkedHashMap(root)
            out["model"] = model
            write(f, out)
        }
    }

    private fun forgeMask(tex: String, f: File): String {
        val prefix = "neoforge:item/mask/"
        val mask = tex.removePrefix(prefix)
        if (!tex.startsWith(prefix) || mask !in FORGE_MASKS) fail(f, "no Forge twin of fluid-container texture $tex")
        return "forge:item/mask/$mask"
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
                "Forge data transform: \"neoforge:\" left in the processed resources (translate it in DataForge, " +
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
