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

package com.volmit.iris.server;

import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@Log(topic = "Iris-Server")
public class EntryPoint {

    public static void main(String[] args) throws Throwable {
        if (args.length < 4) {
            log.info("Usage: java -jar Iris.jar <version> <minMemory> <maxMemory> <server-port> [nodes]");
            System.exit(-1);
            return;
        }

        String[] nodes = new String[args.length - 4];
        System.arraycopy(args, 4, nodes, 0, nodes.length);
        try {
            runServer(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), nodes);
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Failed to start server", e);
            System.exit(-1);
        }
    }

    private static void runServer(String version, int minMemory, int maxMemory, int serverPort, String[] nodes) throws IOException {
        File serverJar = new File("cache", "spigot-"+version+".jar");
        if (!serverJar.getParentFile().exists() && !serverJar.getParentFile().mkdirs()) {
            log.severe("Failed to create cache directory");
            System.exit(-1);
            return;
        }


        if (!serverJar.exists()) {
            try (var in = new URL("https://download.getbukkit.org/spigot/spigot-"+ version+".jar").openStream()) {
                Files.copy(in, serverJar.toPath());
            }
            log.info("Downloaded spigot-"+version+".jar to "+serverJar.getAbsolutePath());
        }

        File pluginFile = new File("plugins/Iris.jar");
        if (pluginFile.exists()) pluginFile.delete();
        if (!pluginFile.getParentFile().exists() && !pluginFile.getParentFile().mkdirs()) {
            log.severe("Failed to create plugins directory");
            System.exit(-1);
            return;
        }

        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String path = System.getProperty("java.home") + File.separator + "bin" + File.separator + (windows ? "java.exe" : "java");

        try {
            File irisFile = new File(EntryPoint.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!irisFile.isFile()) {
                log.severe("Failed to locate the Iris plugin jar");
                System.exit(-1);
                return;
            }
            Files.createSymbolicLink(pluginFile.toPath(), irisFile.toPath());
        } catch (URISyntaxException ignored) {}

        List<String> cmd = new ArrayList<>(List.of(
                path,
                "-Xms" + minMemory + "M",
                "-Xmx" + maxMemory + "M",
                "-XX:+AlwaysPreTouch",
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-Ddisable.watchdog=true",
                "-Dcom.mojang.eula.agree=true",
                "-Dcom.volmit.iris.server.port="+serverPort
        ));
        if (nodes.length > 0)
            cmd.add("-Dcom.volmit.iris.server.remote=" + String.join(",", nodes));
        cmd.addAll(List.of("-jar", serverJar.getAbsolutePath(), "nogui"));

        var process = new ProcessBuilder(cmd)
                .inheritIO()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));


        while (true) {
            try {
                process.waitFor();
                break;
            } catch (InterruptedException ignored) {}
        }
    }
}
