/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.gradle

import java.io.File
import org.gradle.api.GradleException

// ─── The Fabric node's game-test manifests ───────────────────────────────────
// The Fabric node registers the game tests BuildCraftGameTests.registerAll registers on its view of the source: a test
// the node cannot run sits in a `//? if neoforge {` arm there, which Stonecutter comments out on Fabric. A
// test_instance manifest whose function is not registered would fail that test at run time ("no such function"), so
// the copied manifests of the tests this node leaves out are dropped. The registrations are read from the source
// with its comments stripped, so the commented arms count as absent.

object FabricGameTests {

    private val REGISTRATION = Regex("""reg\.accept\(\s*"buildcraftunofficial:([a-z0-9_./-]+)"""")

    /** The test ids [registrar] (this node's BuildCraftGameTests.java) registers outside comments. */
    fun registeredIds(registrar: File): Set<String> {
        val code = stripComments(registrar.readText())
        return REGISTRATION.findAll(code).map { it.groupValues[1] }.toSet()
    }

    /**
     * Deletes every test_instance manifest under [resourcesDir] whose test [registrar] does not register, and fails
     * on a registered test without one (the NeoForge nodes' GameTestManifestTester guards the same pairing).
     */
    fun dropUnregisteredManifests(resourcesDir: File, registrar: File) {
        val registered = registeredIds(registrar)
        if (registered.isEmpty()) {
            throw GradleException("No game-test registrations found in $registrar")
        }
        val dir = resourcesDir.resolve("data/buildcraftunofficial/test_instance")
        val manifests = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
        val present = manifests.map { it.name.removeSuffix(".json") }.toSet()
        val missing = registered - present
        if (missing.isNotEmpty()) {
            throw GradleException("Game tests registered without a test_instance manifest: ${missing.sorted()}")
        }
        for (manifest in manifests) {
            if (manifest.name.removeSuffix(".json") !in registered && !manifest.delete()) {
                throw GradleException("Cannot delete $manifest")
            }
        }
    }

    // Java comments out, string literals kept (a `//` or `/*` inside a string is not a comment).
    private fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '"' || c == '\'' -> {
                    val end = literalEnd(src, i, c)
                    out.append(src, i, end)
                    i = end
                }
                src.startsWith("//", i) -> {
                    val nl = src.indexOf('\n', i)
                    i = if (nl < 0) src.length else nl
                }
                src.startsWith("/*", i) -> {
                    val close = src.indexOf("*/", i + 2)
                    i = if (close < 0) src.length else close + 2
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun literalEnd(src: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < src.length) {
            when (src[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return src.length
    }
}
