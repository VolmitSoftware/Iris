plugins {
    kotlin("jvm") version embeddedKotlinVersion
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.ow2.asm:asm:9.8")
    implementation("com.github.VolmitSoftware:NMSTools:c88961416f")
    implementation("io.papermc.paperweight:paperweight-userdev:2.0.0-beta.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}