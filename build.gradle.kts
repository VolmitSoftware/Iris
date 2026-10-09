import org.gradle.jvm.tasks.Jar
import java.io.ByteArrayOutputStream

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

// The built-ins as a pack module jar, to put into a pack's modules/ folder. What goes in is listed in builtin-modules.txt.

/** What [builtinModulesFile] lists: module classes (the jar's entry points) and further class patterns. */
data class BuiltinModuleList(val moduleClasses: List<String>, val classPatterns: List<String>) {
    /** Ant patterns, relative to the class root, of every class the jar takes from Iris's compiled classes. */
    fun packagedClassPatterns(): List<String> =
        moduleClasses.flatMap { listOf(it.replace('.', '/') + ".class", it.replace('.', '/') + "$*.class") } + classPatterns
}

fun readBuiltinModuleList(file: File): BuiltinModuleList {
    val moduleClasses = mutableListOf<String>()
    val classPatterns = mutableListOf<String>()
    file.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach { line ->
        val (keyword, value) = line.split(Regex("\\s+"), limit = 2).let { it[0] to it.getOrElse(1) { "" }.trim() }
        require(value.isNotEmpty()) { "${file.name}: '$line' needs a value after '$keyword'" }
        when (keyword) {
            "module" -> moduleClasses += value
            "include" -> classPatterns += value
            else -> throw GradleException("${file.name}: unknown line '$line'; start it with 'module' or 'include'")
        }
    }
    return BuiltinModuleList(moduleClasses, classPatterns)
}

val builtinModulesFile = layout.projectDirectory.file("builtin-modules.txt").asFile
val builtinModuleList = readBuiltinModuleList(builtinModulesFile)
val builtinModulesJarName = "iris-builtins.jar"
val builtinModuleEntryFile = "com.volmit.IrisModule"

/** Host packages pack modules may use besides the IrisApi. Keep in step with IrisModuleHost.BUILT_IN_API_PACKAGE in Iris. */
val exposedHostPackages = listOf("art.arcane.iris.builtin")
val irisClasses = irisRoot.resolve("core/build/classes/java/main")
val builtinModulesBuild = layout.buildDirectory.dir("builtin-modules")
val checkedJarsDirectory = layout.buildDirectory.dir("libs")

val irisApiForChecks by configurations.creating {
    isCanBeConsumed = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}

dependencies {
    irisApiForChecks("com.volmit:IrisApi")
}

val compileBuiltins = tasks.register<Exec>("compileBuiltins") {
    description = "Compiles Iris core together with the built-in sources, which is where the jar's classes come from."
    workingDir(irisRoot)
    val offline = if (gradle.startParameter.isOffline) listOf("--offline") else emptyList()
    commandLine(listOf("./gradlew", ":core:compileJava", "-PincludeModdedAdapters=false", "--console=plain") + offline)
}

val writeBuiltinModuleEntries = tasks.register("writeBuiltinModuleEntries") {
    description = "Writes the jar's META-INF/services/$builtinModuleEntryFile, one module class per line."
    val entries = builtinModulesBuild.map { it.file("services/$builtinModuleEntryFile") }
    inputs.file(builtinModulesFile)
    outputs.file(entries)
    doLast {
        entries.get().asFile.apply { parentFile.mkdirs() }.writeText(builtinModuleList.moduleClasses.joinToString("\n", postfix = "\n"))
    }
}

val packageBuiltinModules = tasks.register<Jar>("packageBuiltinModules") {
    description = "Packages the listed built-in modules and classes before they are checked."
    dependsOn(compileBuiltins)
    archiveFileName.set(builtinModulesJarName)
    destinationDirectory.set(builtinModulesBuild.map { it.dir("unchecked") })
    inputs.file(builtinModulesFile)
    val patterns = builtinModuleList.packagedClassPatterns()
    // An empty include list would take every class of Iris.
    if (patterns.isNotEmpty()) {
        from(irisClasses) { include(patterns) }
        from(writeBuiltinModuleEntries) { into("META-INF/services") }
    }
}

val checkBuiltinModulesJar = tasks.register<JavaExec>("checkBuiltinModulesJar") {
    description = "Runs IrisApi's pack class and reference checks on the packaged jar, as a pack load would."
    dependsOn(packageBuiltinModules)
    // A jar without module entries is not a loadable pack module; there is nothing to check until a module is listed.
    onlyIf { builtinModuleList.moduleClasses.isNotEmpty() }
    val packaged = packageBuiltinModules.flatMap { it.archiveFile }
    classpath = irisApiForChecks + files(irisClasses)
    mainClass.set("com.volmit.host.ModuleJarCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    argumentProviders.add(CommandLineArgumentProvider { listOf(packaged.get().asFile.absolutePath) + exposedHostPackages })
    isIgnoreExitValue = true
    val problems = ByteArrayOutputStream()
    errorOutput = problems
    doFirst {
        // A jar from an earlier run that passed must not survive a run that fails.
        checkedJarsDirectory.get().file(builtinModulesJarName).asFile.delete()
    }
    doLast {
        if (executionResult.get().exitValue == 0) return@doLast
        throw GradleException(
            "The built-in modules jar breaks the rules of a pack module jar:\n" + problems.toString().trim() +
                "\nFix the classes listed in ${builtinModulesFile.name}, or move what they use into the jar or into art.arcane.iris.builtin.")
    }
}

tasks.register<Copy>("builtinModulesJar") {
    group = "build"
    description = "Builds build/libs/$builtinModulesJarName, the built-in modules as a pack module jar for a pack's modules/ folder."
    dependsOn(checkBuiltinModulesJar)
    from(packageBuiltinModules.flatMap { it.archiveFile })
    into(checkedJarsDirectory)
}

tasks.named("check") {
    dependsOn(checkBuiltins, "builtinSourcesJar", "builtinModulesJar")
}

tasks.named("assemble") {
    dependsOn("builtinSourcesJar")
}
