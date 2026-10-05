plugins {
    id("java-library")
}

group = "com.volmit"
version = "1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Tests that need a JDK package exported to unnamed modules run in their own JVM, so the flag
// cannot hide a missing check from the other tests.
val addExportsTag = "jdk-add-exports"

tasks.test {
    useJUnitPlatform { excludeTags(addExportsTag) }
}

val addExportsTest = tasks.register<Test>("addExportsTest") {
    description = "Runs the tests that need --add-exports java.base/jdk.internal.misc=ALL-UNNAMED."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(addExportsTag) }
    jvmArgs("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED")
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(addExportsTest)
}
