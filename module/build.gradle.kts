import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.jvm.tasks.Jar
import java.io.ByteArrayOutputStream

plugins {
    `java-library`
}

group = "art.arcane"
version = "1.0-SNAPSHOT"

base {
    archivesName.set("iris-module")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // VolmLib, when no sibling checkout is found
}

val volmLibCoordinate = providers.gradleProperty("volmLibCoordinate").get()

// What a module may use: the Iris API, VolmLib and Gson, which every Iris host provides, and the JDK. None of them ships
// with the module.
dependencies {
    compileOnly("com.volmit:iris-api")
    compileOnly(volmLibCoordinate) {
        isTransitive = false
    }
    compileOnly("com.google.code.gson:gson:2.14.0")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}

// The same options as Iris's own compilation, so the classes, and the module jars made from them, are the ones Iris
// shipped when it compiled these sources itself.
tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.debugOptions.debugLevel = "none"
    // lombok.config decides which members Lombok generates; Gradle does not know it is an input.
    inputs.file(layout.projectDirectory.file("lombok.config"))
        .withPropertyName("lombokConfig")
        .withPathSensitivity(PathSensitivity.NONE)
}

// The version every built-in module reports: Iris's own, so a pack's copy of the built-ins jar names the Iris build it came
// The built-in modules as pack module jars, one per "jar" section of builtin-modules.txt: build/shipped-modules/iris-<name>.jar,
// with index.json naming the id and version of every module in them. Iris bundles that folder and installs the jars a
// pack's modules.json lists.
val moduleEntryFile = "com.volmit.IrisModule"
val builtinModulesFile = layout.projectDirectory.file("builtin-modules.txt").asFile
val shippedJarPrefix = "iris-"
val shippedIndexName = "index.json"
// The packages an Iris host exposes to pack modules besides the API (Iris's IrisModuleHost.EXPOSED_PACKAGES).
val exposedHostPackages = listOf("art.arcane.iris.builtin", "art.arcane.iris.core.structure.authoring", "art.arcane.volmlib",
    "com.google.gson")

data class ListedJar(val name: String, val moduleClasses: MutableList<String> = mutableListOf(),
                     val classPatterns: MutableList<String> = mutableListOf()) {
    val fileName get() = "iris-$name.jar"
    val taskSuffix get() = name.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
    val packagedPatterns
        get() = moduleClasses.flatMap { moduleClass ->
            val path = moduleClass.replace('.', '/')
            listOf("$path.class", "$path\$*.class")
        } + classPatterns
}

fun readListedJars(): List<ListedJar> {
    val jars = mutableListOf<ListedJar>()
    builtinModulesFile.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size < 2 || parts[1].isBlank()) {
                throw GradleException("${builtinModulesFile.name}: '$line' needs a value after '${parts[0]}'")
            }
            val value = parts[1].trim()
            if (parts[0] == "jar") {
                if (jars.any { it.name == value }) {
                    throw GradleException("${builtinModulesFile.name}: jar '$value' is listed twice")
                }
                jars.add(ListedJar(value))
                return@forEach
            }
            val jar = jars.lastOrNull()
                ?: throw GradleException("${builtinModulesFile.name}: '$line' comes before the first 'jar' line")
            when (parts[0]) {
                "module" -> jar.moduleClasses.add(value)
                "include" -> jar.classPatterns.add(value)
                else -> throw GradleException("${builtinModulesFile.name}: unknown line '$line'; start it with 'jar', 'module' or 'include'")
            }
        }
    jars.filter { it.moduleClasses.isEmpty() }.forEach {
        throw GradleException("${builtinModulesFile.name}: jar '${it.name}' lists no module; a pack module jar needs one")
    }
    return jars
}

val listedJars = readListedJars()
val builtinModulesBuild = layout.buildDirectory.dir("builtin-modules")

fun entryFileOf(jar: ListedJar) = builtinModulesBuild.map { it.file("services/${jar.name}/$moduleEntryFile") }

val writeModuleEntries = tasks.register("writeModuleEntries") {
    description = "Writes each jar's META-INF/services/$moduleEntryFile, one module class per line."
    inputs.file(builtinModulesFile)
    outputs.dir(builtinModulesBuild.map { it.dir("services") })
    doLast {
        listedJars.forEach { jar ->
            val entriesFile = entryFileOf(jar).get().asFile
            entriesFile.parentFile.mkdirs()
            entriesFile.writeText(jar.moduleClasses.joinToString("\n") + "\n")
        }
    }
}

// A jar's bytes decide whether a pack's copy is the one Iris ships, so the same classes must give the same bytes.
fun Jar.packagedAsShippedModule() {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    // Iris's own jar compresses this one as a whole, which squeezes more out of the classes than compressing them twice.
    entryCompression = ZipEntryCompression.STORED
    inputs.file(builtinModulesFile)
}

val packageModuleJars = listedJars.map { listed ->
    tasks.register<Jar>("package${listed.taskSuffix}ModuleJar") {
        description = "Packages ${listed.fileName} before the built-in modules are checked."
        archiveFileName.set(listed.fileName)
        destinationDirectory.set(builtinModulesBuild.map { it.dir("jars") })
        packagedAsShippedModule()
        from(sourceSets.main.map { it.java.classesDirectory }) {
            include(listed.packagedPatterns)
        }
        from(entryFileOf(listed)) {
            into("META-INF/services")
        }
        dependsOn(writeModuleEntries)
    }
}

// The checks judge a pack's jars together, as a pack load does, so they read all the built-ins as one jar. Nothing ships it.
val allModulesJarName = "iris-builtins.jar"
val writeAllModuleEntries = tasks.register("writeAllModuleEntries") {
    description = "Writes the entry file that lists every built-in module, for the jar the checks read."
    val entries = builtinModulesBuild.map { it.file("all-services/$moduleEntryFile") }
    inputs.file(builtinModulesFile)
    outputs.file(entries)
    doLast {
        val entriesFile = entries.get().asFile
        entriesFile.parentFile.mkdirs()
        entriesFile.writeText(listedJars.flatMap { it.moduleClasses }.joinToString("\n") + "\n")
    }
}

val packageAllModules = tasks.register<Jar>("packageAllModules") {
    description = "Packages every built-in module into one jar for the pack module checks."
    archiveFileName.set(allModulesJarName)
    destinationDirectory.set(builtinModulesBuild.map { it.dir("unchecked") })
    packagedAsShippedModule()
    from(sourceSets.main.map { it.java.classesDirectory }) {
        include(listedJars.flatMap { it.packagedPatterns })
    }
    from(writeAllModuleEntries) {
        into("META-INF/services")
    }
}

// The check runs on the API's host side, which knows the rules of a pack load; the module classes are on its class path as
// the host's, so a listed class that uses one left out of every jar fails as it would in Iris.
val builtinModulesCheckClasspath = configurations.create("builtinModulesCheckClasspath") {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

val tools = sourceSets.create("tools")

dependencies {
    builtinModulesCheckClasspath("com.volmit:iris-api")
    builtinModulesCheckClasspath("com.volmit:iris-host")
    builtinModulesCheckClasspath(volmLibCoordinate)
    builtinModulesCheckClasspath("com.google.code.gson:gson:2.14.0")
    "toolsImplementation"("com.volmit:iris-api")
    "toolsImplementation"("com.google.code.gson:gson:2.14.0")
}

val checkedModulesJar = tasks.register<JavaExec>("checkedModulesJar") {
    group = "build"
    description = "Checks the built-in modules with IrisApi's pack class and reference checks, as a pack load would, and" +
        " writes them as one jar to build/libs/$allModulesJarName for Iris's own check."
    val unchecked = packageAllModules.flatMap { it.archiveFile }
    val checked = layout.buildDirectory.file("libs/$allModulesJarName")
    classpath = builtinModulesCheckClasspath + files(sourceSets.main.map { it.java.classesDirectory })
    mainClass.set("com.volmit.host.ModuleJarCheck")
    inputs.file(unchecked)
    outputs.file(checked)
    argumentProviders.add(CommandLineArgumentProvider { listOf(unchecked.get().asFile.absolutePath) + exposedHostPackages })
    isIgnoreExitValue = true
    val problems = ByteArrayOutputStream()
    errorOutput = problems
    doFirst {
        // A jar from an earlier run that passed must not survive a run that fails.
        checked.get().asFile.delete()
    }
    doLast {
        if (executionResult.get().exitValue != 0) {
            throw GradleException("The built-in modules break the rules of a pack module jar:\n" + problems.toString().trim() +
                "\nFix the classes listed in ${builtinModulesFile.name}, or move what they use into a jar or into art.arcane.iris.builtin.")
        }
        unchecked.get().asFile.copyTo(checked.get().asFile, overwrite = true)
    }
}

val writeShippedModuleIndex = tasks.register<JavaExec>("writeShippedModuleIndex") {
    description = "Writes $shippedIndexName: the hash of every shipped module jar and the id and version its modules report."
    val jars = files(packageModuleJars.map { it.flatMap(Jar::getArchiveFile) })
    val index = builtinModulesBuild.map { it.file(shippedIndexName) }
    classpath = tools.runtimeClasspath + builtinModulesCheckClasspath + files(sourceSets.main.map { it.java.classesDirectory })
    mainClass.set("art.arcane.iris.modulebuild.ShippedModuleIndex")
    inputs.files(jars)
    outputs.file(index)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(index.get().asFile.absolutePath) + jars.files.sortedBy { it.name }.map { it.absolutePath }
    })
    // The index must not describe modules that failed the checks.
    dependsOn(checkedModulesJar)
}

val shippedModulesFolder = layout.buildDirectory.dir("shipped-modules")
val shippedModules = tasks.register<Sync>("shippedModules") {
    group = "build"
    description = "Builds build/shipped-modules: the checked built-in module jars and their $shippedIndexName."
    from(packageModuleJars)
    from(writeShippedModuleIndex)
    into(shippedModulesFolder)
}

fun Configuration.asModuleArtifacts(capability: String) {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
    outgoing.capability("art.arcane:$capability:${project.version}")
}

// Iris checks the one-jar form once more against what its own Bukkit jar leaves out. It asks for it by this capability.
configurations.create("builtinModulesJarElements") {
    asModuleArtifacts("iris-builtins")
    outgoing.artifact(checkedModulesJar.map { layout.buildDirectory.file("libs/$allModulesJarName").get() }) {
        builtBy(checkedModulesJar)
    }
}

// Iris bundles the folder of shipped jars into its plugin. It asks for it by this capability.
configurations.create("shippedModulesElements") {
    asModuleArtifacts("iris-shipped-modules")
    outgoing.artifact(shippedModulesFolder) {
        builtBy(shippedModules)
    }
}

tasks.named("check") {
    dependsOn(shippedModules)
}

tasks.named("assemble") {
    dependsOn(shippedModules)
}
