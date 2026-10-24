// The host side (com.volmit.host): loads pack module jars and runs their stages. Hosts depend on this; pack modules
// never see it.
base {
    archivesName.set("iris-host")
}

dependencies {
    "api"(project(":api"))
}

// Tests that need a JDK package exported to unnamed modules run in their own JVM, so the flag
// cannot hide a missing check from the other tests.
val addExportsTag = "jdk-add-exports"
val testSourceSet = the<SourceSetContainer>()["test"]

tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags(addExportsTag) }
}

val addExportsTest = tasks.register<Test>("addExportsTest") {
    description = "Runs the tests that need --add-exports java.base/jdk.internal.misc=ALL-UNNAMED."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags(addExportsTag) }
    jvmArgs("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED")
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(addExportsTest)
}
