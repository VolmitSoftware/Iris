/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import java.io.File

plugins {
    id ("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Iris"

val useLocalVolmLib: Boolean = providers.gradleProperty("useLocalVolmLib")
    .orElse("true")
    .map { value: String -> value.equals("true", ignoreCase = true) }
    .get()
val localVolmLibDirectory: File = file("../VolmLib")

if (useLocalVolmLib && localVolmLibDirectory.resolve("settings.gradle.kts").exists()) {
    includeBuild(localVolmLibDirectory) {
        dependencySubstitution {
            substitute(module("com.github.VolmitSoftware:VolmLib")).using(project(":shared"))
            substitute(module("com.github.VolmitSoftware.VolmLib:shared")).using(project(":shared"))
            substitute(module("com.github.VolmitSoftware.VolmLib:volmlib-shared")).using(project(":shared"))
        }
    }
}

include(":core", ":core:agent")
include(
    ":nms:v1_21_R7",
)
