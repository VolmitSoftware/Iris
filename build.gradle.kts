import org.gradle.jvm.tasks.Jar

plugins {
    base
    `java-base`
}

group = "art.arcane"
version = "1.0-SNAPSHOT"

val irisRoot = rootDir.resolve("../Iris").canonicalFile
val moduleSources = layout.projectDirectory.dir("src/main/java")

tasks.register<Jar>("builtinSourcesJar") {
    group = "build"
    description = "Packages the built-in Iris sources owned by this module."
    archiveBaseName.set("iris-default-module")
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    inputs.dir(moduleSources)
    from(moduleSources)
}

val checkBuiltins = tasks.register<Exec>("checkBuiltins") {
    group = "verification"
    description = "Compiles the built-ins with Iris and checks source ownership and Bukkit purity."
    workingDir(irisRoot)
    commandLine(
        "./gradlew",
        ":core:compileJava",
        ":core:bukkitPurityRatchet",
        "-PincludeModdedAdapters=false",
        "--console=plain",
    )
}

// The built-ins as a pack module jar. Iris packages and checks it from its own compiled classes (core's builtinModulesJar,
// which reads builtin-modules.txt) and bundles it into the plugin; this copies it to build/libs for other hosts and tools.
val builtinModulesJarName = "iris-builtins.jar"

val packageBuiltinModulesInIris = tasks.register<Exec>("packageBuiltinModulesInIris") {
    description = "Runs Iris's :core:builtinModulesJar, which compiles Iris with the built-ins, packages and checks the jar."
    workingDir(irisRoot)
    val offline = if (gradle.startParameter.isOffline) listOf("--offline") else emptyList()
    commandLine(listOf("./gradlew", ":core:builtinModulesJar", "-PincludeModdedAdapters=false", "--console=plain") + offline)
}

tasks.register<Copy>("builtinModulesJar") {
    group = "build"
    description = "Copies the checked jar Iris built to build/libs/$builtinModulesJarName, the built-in modules as a pack module jar."
    dependsOn(packageBuiltinModulesInIris)
    from(irisRoot.resolve("core/build/builtin-modules/$builtinModulesJarName"))
    into(layout.buildDirectory.dir("libs"))
}

tasks.named("check") {
    dependsOn(checkBuiltins, "builtinSourcesJar", "builtinModulesJar")
}

tasks.named("assemble") {
    dependsOn("builtinSourcesJar")
}
