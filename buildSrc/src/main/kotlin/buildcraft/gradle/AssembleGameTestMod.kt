/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.gradle

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

// ─── The Forge node's game-test mod directory ────────────────────────────────
// Forge's dev run finds a mod by the META-INF/mods.toml of one classpath directory, and Java refuses two modules that hold
// the same package, which BuildCraft's tests (written in the packages of the code they test) would be if the test source
// set's output stayed a directory of its own. So the game-test run loads ONE directory: the main source set's output (its
// classes, the transformed resources, mods.toml, the mixin config) with the test source set's output laid over it.
//
// The two outputs share one kind of file: a datapack tag. The test set adds `shears` to `c:tools/wrench` so a foreign tool is
// available as a wrench (WrenchTagTester), at the path BuildCraft's own wrench tag has. NeoForge and Fabric hand both files
// to the game as two packs and the tag merges; here they would be one file, so a tag that both outputs hold is written as
// the union of the two `values` lists. Any other file in both is a mistake and fails the task.
abstract class AssembleGameTestMod : DefaultTask() {

    /** The main source set's output directories (classes and resources, merged into one by ForgeGradle). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainOutput: ConfigurableFileCollection

    /** The test source set's output directories. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testOutput: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val modDir: DirectoryProperty

    @TaskAction
    fun assemble() {
        val dest = modDir.get().asFile
        dest.deleteRecursively()
        dest.mkdirs()
        for (root in mainOutput.files.filter { it.isDirectory }) {
            copyTree(root, dest, merge = false)
        }
        for (root in testOutput.files.filter { it.isDirectory }) {
            copyTree(root, dest, merge = true)
        }
    }

    // [merge]: a file already in [dest] (the destination was emptied first, so the main output put it there) is a collision.
    private fun copyTree(root: File, dest: File, merge: Boolean) {
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val target = dest.resolve(rel)
            target.parentFile.mkdirs()
            if (merge && target.exists()) {
                if (!isTag(rel)) {
                    throw GradleException("$rel is in both the main and the test output; only datapack tags are merged.")
                }
                target.writeText(unionTag(target, file))
            } else {
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun isTag(rel: String): Boolean {
        val parts = rel.split('/')
        return rel.endsWith(".json") && parts.size > 3 && parts[0] == "data" && "tags" in parts
    }

    // A tag file's `values` of both files, the first one's entries first, each once; every other key must be equal.
    private fun unionTag(a: File, b: File): String {
        @Suppress("UNCHECKED_CAST")
        val left = groovy.json.JsonSlurper().parse(a) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val right = groovy.json.JsonSlurper().parse(b) as Map<String, Any?>
        for (key in (left.keys + right.keys)) {
            if (key != "values" && left[key] != right[key]) {
                throw GradleException("The tag ${a.name} differs in '$key' between the main and the test output; cannot merge.")
            }
        }
        val values = ((left["values"] as? List<*>).orEmpty() + (right["values"] as? List<*>).orEmpty()).distinct()
        return groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(left + ("values" to values)))
    }
}
