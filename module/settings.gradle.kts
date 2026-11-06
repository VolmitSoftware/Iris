rootProject.name = "IrisModule"

// The built-ins compile against the Iris API alone, from the sibling checkout. Never against iris-host: that is what a host
// such as Iris uses to load modules, and packs never see it.
includeBuild("../api") {
    dependencySubstitution {
        substitute(module("com.volmit:iris-api")).using(project(":api"))
        substitute(module("com.volmit:iris-host")).using(project(":host"))
    }
}

// VolmLib from a sibling checkout when there is one, as Iris resolves it (Iris's gradle/volmlib-resolution.settings.gradle):
// -PuseLocalVolmLib=false turns it off, -PlocalVolmLibDirectory=<path> or VOLMLIB_DIR names the checkout, and otherwise the
// first `VolmLib` folder with a settings script above this one is used. Without one, the build resolves the published
// coordinate in gradle.properties.
fun hasSettingsScript(directory: File): Boolean =
    File(directory, "settings.gradle.kts").exists() || File(directory, "settings.gradle").exists()

fun localVolmLibDirectory(): File? {
    val configuredPath = providers.gradleProperty("localVolmLibDirectory")
        .orElse(providers.environmentVariable("VOLMLIB_DIR"))
        .orNull
    if (!configuredPath.isNullOrBlank()) {
        val configured = File(configuredPath).let { if (it.isAbsolute) it else File(settingsDir, configuredPath) }
        if (hasSettingsScript(configured)) {
            return configured
        }
    }
    return generateSequence(settingsDir) { it.parentFile }
        .map { File(it, "VolmLib") }
        .firstOrNull { hasSettingsScript(it) }
}

val useLocalVolmLib = providers.gradleProperty("useLocalVolmLib").orElse("true").get().equals("true", ignoreCase = true)
val volmLibCheckout = if (useLocalVolmLib) localVolmLibDirectory() else null
if (volmLibCheckout != null) {
    includeBuild(volmLibCheckout) {
        dependencySubstitution {
            substitute(module("com.github.VolmitSoftware:VolmLib")).using(project(":shared"))
        }
    }
}
