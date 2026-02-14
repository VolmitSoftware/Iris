plugins {
    java
}

tasks.jar {
    manifest.attributes(
        "Agent-Class" to "art.arcane.iris.util.agent.Installer",
        "Premain-Class" to "art.arcane.iris.util.agent.Installer",
        "Can-Redefine-Classes" to true,
        "Can-Retransform-Classes" to true
    )
}