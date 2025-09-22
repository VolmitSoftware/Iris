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
plugins {
    id ("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Iris"

include(":core", ":core:agent")
include(
        ":nms:v1_21_R6",
        ":nms:v1_21_R5",
        ":nms:v1_21_R4",
        ":nms:v1_21_R3",
        ":nms:v1_21_R2",
        ":nms:v1_21_R1",
        ":nms:v1_20_R4",
        ":nms:v1_20_R3",
        ":nms:v1_20_R2",
        ":nms:v1_20_R1",
)