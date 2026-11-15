/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.gradle

import groovy.json.JsonSlurper
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency

/**
 * `-PwithMods=alias[,alias...]`, the opt-in third-party mods of the dev runtimes (cross-mod checks, #186). Both loader
 * scripts (bc.neoforge, bc.fabric) read it through here, so the alias rules live once.
 *
 * An alias is defined per node in versions/<node>/gradle.properties as `withmod.<alias>=<coordinate>[;<coordinate>...]`:
 * the mod and every library it needs that the dev runtime does not already carry (each coordinate goes on the runtime
 * without its own dependencies, because Modrinth's maven publishes no transitive metadata, so a library has to be named).
 * A node that does not define a requested alias skips it with a lifecycle line (the mod may simply not exist for that
 * line); an alias defined empty is a deliberate no-op (the mod is already on that node's runtime).
 *
 * Read the result into a local at configuration time. A task action must never capture this object's inputs
 * (the configuration cache cannot serialize a Project).
 */
object WithMods {
    /** The coordinates the requested aliases name on [project]'s node, in request order, each once. Empty without the property. */
    fun coordinates(project: Project): List<String> {
        val requested = (project.findProperty("withMods") as String?).orEmpty()
            .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val coordinates = LinkedHashSet<String>()
        for (alias in requested) {
            val definition = project.findProperty("withmod.$alias") as String?
            if (definition == null) {
                project.logger.lifecycle("-PwithMods=$alias ignored on ${project.name}: this node defines no withmod.$alias")
                continue
            }
            for (coordinate in definition.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
                if (coordinate.count { it == ':' } != 2) {
                    throw GradleException(
                        "withmod.$alias on ${project.name} names '$coordinate', which is not a group:name:version coordinate"
                    )
                }
                coordinates.add(coordinate)
            }
        }
        return coordinates.toList()
    }

    /**
     * The coordinate `withmod.[alias]` names for [module] (`group:name`) on [project]'s node, for a mod BuildCraft names
     * behind a loaded-mod check and so compiles against: the compiler and the `-PwithMods` dev runtime then see one jar,
     * the version this node pins, and the pin lives once. Throws a [GradleException] naming the node when the alias or
     * the module is missing, since the shared source needs it on every node.
     */
    fun compileCoordinate(project: Project, alias: String, module: String): String {
        val definition = project.findProperty("withmod.$alias") as String?
            ?: throw GradleException("${project.name} defines no withmod.$alias, which the shared source compiles against")
        return definition.split(';').map { it.trim() }.firstOrNull { it.startsWith("$module:") }
            ?: throw GradleException("withmod.$alias on ${project.name} names no $module coordinate: '$definition'")
    }

    /** A jar unpacked from another jar's META-INF/jars/; [isMod] when it carries a fabric.mod.json (so it needs remapping). */
    data class NestedJar(val file: File, val isMod: Boolean)

    /**
     * The jar-in-jar contents of [coordinates]' jars, unpacked under [into], for a Loom remap node (the 1.21.x Fabric
     * nodes): Loom's remapped copy of a mod drops the `jars` list from its fabric.mod.json, so Fabric Loader never
     * loads a mod's own nested libraries there (C2ME's submodules, Flan's YAML reader, Iris's shader compiler) unless
     * they are handed over as dependencies. A nested mod whose id the top-level coordinates already carry, or that
     * [runtimeHas] says the runtime has (Fabric API's own modules: a second, older copy would shadow them), is left out,
     * and so is everything inside it. Two nested jars with one file name count as one. Resolves at configuration time.
     */
    fun nestedJars(project: Project, coordinates: List<String>, into: File, runtimeHas: (String) -> Boolean): List<NestedJar> {
        val queue = ArrayDeque<File>()
        val carried = HashSet<String>()
        for (coordinate in coordinates) {
            val dependency = project.dependencies.create(coordinate) as ModuleDependency
            dependency.isTransitive = false
            val jar = project.configurations.detachedConfiguration(dependency).singleFile
            modId(jar.readBytes())?.let { carried.add(it) }
            queue.add(jar)
        }
        val found = LinkedHashMap<String, NestedJar>()
        while (queue.isNotEmpty()) {
            ZipFile(queue.removeFirst()).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory || !entry.name.startsWith("META-INF/jars/") || !entry.name.endsWith(".jar")) continue
                    val name = entry.name.substringAfterLast('/')
                    if (name in found) continue
                    val bytes = zip.getInputStream(entry).readBytes()
                    val id = modId(bytes)
                    if (id != null && (id in carried || runtimeHas(id))) continue
                    val target = into.resolve(name)
                    if (!target.isFile || target.length() != bytes.size.toLong()) {
                        target.parentFile.mkdirs()
                        target.writeBytes(bytes)
                    }
                    if (id != null) carried.add(id)
                    found[name] = NestedJar(target, id != null)
                    queue.add(target)
                }
            }
        }
        return found.values.toList()
    }

    /** The `id` of the mod a jar's fabric.mod.json declares, or null when the jar has none (a plain library). */
    private fun modId(jar: ByteArray): String? {
        ZipInputStream(jar.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name != "fabric.mod.json") continue
                val json = JsonSlurper().parseText(zip.readBytes().toString(Charsets.UTF_8)) as Map<*, *>
                return json["id"] as String?
            }
        }
    }
}
