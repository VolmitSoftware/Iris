rootProject.name = "IrisModule"

// Keep the public API discoverable when importing the default module.
includeBuild("../IrisApi")

// The built-ins jar check sees the libraries Iris shares with pack modules; VolmLib comes from the sibling checkout, as for Iris.
includeBuild("../VolmLib") {
    dependencySubstitution {
        substitute(module("com.github.VolmitSoftware:VolmLib")).using(project(":shared"))
    }
}
