import org.gradle.jvm.tasks.Jar

plugins {
    base
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

tasks.named("check") {
    dependsOn(checkBuiltins, "builtinSourcesJar")
}

tasks.named("assemble") {
    dependsOn("builtinSourcesJar")
}
