/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.jvm;

import com.volmit.iris.util.plugin.VolmitSender;

public class VMJavaFX {
    private VolmitSender sender;

    public VMJavaFX(VolmitSender user) {
        this.sender = user;

    }

    public void start() {
        try {
            // Start JavaFX in a new JVM
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "--module-path", "path/to/javafx-sdk/lib",  // Set path to JavaFX SDK
                    "--add-modules", "javafx.controls,javafx.fxml",
                    "-jar", "path/to/javafx-application.jar"
            );
            processBuilder.inheritIO();
            processBuilder.start();
            sender.sendMessage("JavaFX application is launched!");
        } catch (Exception e) {
            sender.sendMessage("Failed to launch JavaFX application.");
            e.printStackTrace();
        }
    }


}
