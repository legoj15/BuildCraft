/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.gradle

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import org.gradle.api.GradleException

// ─── The Fabric node's game-test manifests ───────────────────────────────────
// The Fabric node registers the game tests BuildCraftGameTests.registerAll registers on its view of the source: a test
// the node cannot run sits in a `//? if neoforge {` arm there, which Stonecutter comments out on Fabric. A
// test_instance manifest whose function is not registered would fail that test at run time ("no such function"), so
// the copied manifests of the tests this node leaves out are dropped. The registrations are read from the source
// with its comments stripped, so the commented arms count as absent.

object FabricGameTests {

    private val REGISTRATION = Regex("""reg\.accept\(\s*"buildcraftunofficial:([a-z0-9_./-]+)"""")
    private val REGISTRATION_CALL = Regex("""reg\.accept\(""")

    /**
     * The test ids [registrar] (this node's BuildCraftGameTests.java) registers outside comments. Every
     * `reg.accept(` call must name its test as a string literal: a registration through a constant, a concatenation
     * or a helper is invisible to [REGISTRATION], so its manifest would be dropped as unregistered and the test would
     * silently stop running on Fabric.
     */
    fun registeredIds(registrar: File): Set<String> {
        val code = stripComments(registrar.readText())
        val calls = REGISTRATION_CALL.findAll(code).count()
        val literal = REGISTRATION.findAll(code).count()
        if (calls != literal) {
            throw GradleException(
                "${calls - literal} of the $calls reg.accept(...) calls in $registrar do not name their test as a " +
                    "\"buildcraftunofficial:<id>\" string literal, the only form the manifest check reads (a manifest " +
                    "it cannot match would be dropped, and the test would silently stop running on Fabric)."
            )
        }
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

/**
 * A log sink for a JavaExec's `standardOutput` that holds no file handle between writes: Gradle never closes a
 * caller-supplied stream, so a plain FileOutputStream would stay open until the JDK's cleaner got to it (on Windows,
 * long enough to lock the file against a delete). Each write opens the file in append mode and closes it again; the
 * caller truncates the file first. A run's console is tens of MB in 8 KB chunks, so the reopening costs nothing.
 */
class AppendingLogStream(private val file: File) : OutputStream() {
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        FileOutputStream(file, true).use { it.write(b, off, len) }
    }
}
