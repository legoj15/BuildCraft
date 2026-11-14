// Re-runnable check of the -PwithMods aliases (#186): resolves every `withmod.<alias>` coordinate a node defines, one by
// one and without dependencies, on that node's own repositories, and prints a line per coordinate:
//   WITHMOD <node> <alias> OK <coordinate> <downloaded jar>
//   WITHMOD <node> <alias> FAILED <coordinate> <reason>
//   WITHMOD <node> <alias> NOOP (defined empty)
// Run it after a version bump, or when a node's pins change; a FAILED line names the alias to re-pin.
//   ./gradlew withModsResolve -I scripts/withmods-resolve.init.gradle.kts --no-configuration-cache --console=plain
//   ./gradlew :26.3-fabric:withModsResolve -I scripts/withmods-resolve.init.gradle.kts --no-configuration-cache --console=plain
// An init script (not a task in the build) so nothing here is part of the normal build; the task closure reads the live
// project, hence --no-configuration-cache.
allprojects {
    if (parent != null) {
        afterEvaluate {
            tasks.register("withModsResolve") {
                group = "verification"
                description = "Resolves every withmod.* coordinate this node defines and prints one WITHMOD line each."
                doLast {
                    val node = project.name
                    val aliases = project.properties.keys.filter { it.startsWith("withmod.") }.sorted()
                    for (key in aliases) {
                        val alias = key.removePrefix("withmod.")
                        val coordinates = (project.properties[key] as String).split(';').map { it.trim() }.filter { it.isNotEmpty() }
                        if (coordinates.isEmpty()) {
                            println("WITHMOD $node $alias NOOP (defined empty)")
                        }
                        for (coordinate in coordinates) {
                            val dependency = project.dependencies.create(coordinate) as ModuleDependency
                            dependency.isTransitive = false
                            val lenient = project.configurations.detachedConfiguration(dependency).resolvedConfiguration.lenientConfiguration
                            val unresolved = lenient.unresolvedModuleDependencies
                            val artifacts = try { lenient.artifacts } catch (e: Exception) { emptySet() }
                            if (unresolved.isNotEmpty()) {
                                println("WITHMOD $node $alias FAILED $coordinate ${unresolved.first().problem.message}")
                            } else if (artifacts.size != 1) {
                                println("WITHMOD $node $alias FAILED $coordinate resolved ${artifacts.size} artifacts, expected 1")
                            } else {
                                println("WITHMOD $node $alias OK $coordinate ${artifacts.first().file.absolutePath}")
                            }
                        }
                    }
                }
            }
        }
    }
}
