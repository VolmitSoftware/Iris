plugins {
    java
}

tasks.jar {
    manifest.attributes(
        "Agent-Class" to "art.arcane.iris.util.project.agent.Installer",
        "Premain-Class" to "art.arcane.iris.util.project.agent.Installer",
        "Can-Redefine-Classes" to true,
        "Can-Retransform-Classes" to true
    )
}