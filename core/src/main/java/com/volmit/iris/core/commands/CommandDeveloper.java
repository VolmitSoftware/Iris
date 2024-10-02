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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.tools.IrisPackBenchmarking;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.core.tools.IrisWorldDump;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EnginePlayer;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.service.EngineStatusSVC;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.VolmitSender;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Decree(name = "Developer", origin = DecreeOrigin.BOTH, description = "Iris World Manager", aliases = {"dev"})
public class CommandDeveloper implements DecreeExecutor {
    private CommandTurboPregen turboPregen;
    private CommandUpdater updater;

    @Decree(description = "Get Loaded TectonicPlates Count", origin = DecreeOrigin.BOTH, aliases = "status", sync = true)
    public void EngineStatus() {
        var status = EngineStatusSVC.getStatus();

        sender().sendMessage("-------------------------");
        sender().sendMessage(C.DARK_PURPLE + "Engine Status");
        sender().sendMessage(C.DARK_PURPLE + "Total Engines: " + C.LIGHT_PURPLE + status.engineCount());
        sender().sendMessage(C.DARK_PURPLE + "Total Loaded Chunks: " + C.LIGHT_PURPLE + status.loadedChunks());
        sender().sendMessage(C.DARK_PURPLE + "Tectonic Limit: " + C.LIGHT_PURPLE + status.tectonicLimit());
        sender().sendMessage(C.DARK_PURPLE + "Tectonic Total Plates: " + C.LIGHT_PURPLE + status.tectonicPlates());
        sender().sendMessage(C.DARK_PURPLE + "Tectonic Active Plates: " + C.LIGHT_PURPLE + status.activeTectonicPlates());
        sender().sendMessage(C.DARK_PURPLE + "Tectonic ToUnload: " + C.LIGHT_PURPLE + status.queuedTectonicPlates());
        sender().sendMessage(C.DARK_PURPLE + "Lowest Tectonic Unload Duration: " + C.LIGHT_PURPLE + Form.duration(status.minTectonicUnloadDuration()));
        sender().sendMessage(C.DARK_PURPLE + "Highest Tectonic Unload Duration: " + C.LIGHT_PURPLE + Form.duration(status.maxTectonicUnloadDuration()));
        sender().sendMessage(C.DARK_PURPLE + "Cache Size: " + C.LIGHT_PURPLE + Form.f(IrisData.cacheSize()));
        sender().sendMessage("-------------------------");
    }

    @Decree(description = "Test")
    public void benchmarkMantle(
            @Param(description = "The world to bench", aliases = {"world"})
            World world
    ) throws IOException, ClassNotFoundException {
        Engine engine = IrisToolbelt.access(world).getEngine();
        int maxHeight = engine.getTarget().getHeight();
        File folder = new File(Bukkit.getWorldContainer(), world.getName());
        int c = 0;
        //MCAUtil.read()

        File tectonicplates = new File(folder, "mantle");
        for (File i : Objects.requireNonNull(tectonicplates.listFiles())) {
            TectonicPlate.read(maxHeight, i);
            c++;
            sender().sendMessage("Loaded count: " + c);

        }

    }

    @Decree(description = "Test")
    public void packBenchmark(
            @Param(description = "The pack to bench", defaultValue = "overworld", aliases = {"pack"})
            IrisDimension dimension,
            @Param(description = "The address to use", defaultValue = "-")
            String address,
            @Param(description = "Headless", defaultValue = "true")
            boolean headless,
            @Param(description = "GUI", defaultValue = "false")
            boolean gui,
            @Param(description = "Diameter in regions", defaultValue = "5")
            int diameter
    ) {
        int rb = diameter << 9;
        Iris.info("Benchmarking pack " + dimension.getName() + " with diameter: " + rb + "(" + diameter + ")");
        IrisPackBenchmarking benchmark = new IrisPackBenchmarking(dimension, address.replace("-", "").trim(), diameter, headless, gui);
        benchmark.runBenchmark();
    }

    @Decree(description = "test")
    public void mca(
            @Param(description = "String") World world) {
        try {
            IrisWorldDump dump = new IrisWorldDump(world, sender());
            dump.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Decree(description = "test")
    public void test() {
        try {

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Decree(description = "UnloadChunks for good reasons.")
    public void unloadchunks() {
        List<World> IrisWorlds = new ArrayList<>();
        int chunksUnloaded = 0;
        for (World world : Bukkit.getWorlds()) {
            try {
                if (IrisToolbelt.access(world).getEngine() != null) {
                    IrisWorlds.add(world);
                }
            } catch (Exception e) {
                // no
            }
        }

        for (World world : IrisWorlds) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunk.isLoaded()) {
                    chunk.unload();
                    chunksUnloaded++;
                }
            }
        }
        Iris.info(C.IRIS + "Chunks Unloaded: " + chunksUnloaded);

    }

    @Decree
    public void objects(@Param(defaultValue = "overworld") IrisDimension dimension) {
        var loader = dimension.getLoader().getObjectLoader();
        var sender = sender();
        var keys = loader.getPossibleKeys();
        var burst = MultiBurst.burst.burst(keys.length);
        AtomicInteger failed = new AtomicInteger();
        for (String key : keys) {
            burst.queue(() -> {
                if (loader.load(key) == null)
                    failed.incrementAndGet();
            });
        }
        burst.complete();
        sender.sendMessage(C.RED + "Failed to load " + failed.get() + " of " + keys.length + " objects");
    }

    @Decree(description = "Test", aliases = {"ip"})
    public void network() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(networkInterfaces)) {
                Iris.info("Display Name: %s", ni.getDisplayName());
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                for (InetAddress ia : Collections.list(inetAddresses)) {
                    Iris.info("IP: %s", ia.getHostAddress());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Decree(description = "All players in iris worlds")
    public void getPlayers() {
        KList<World> IrisWorlds = new KList<>();
        for (World w : Bukkit.getServer().getWorlds()) {
            if(IrisToolbelt.isIrisWorld(w)) {
                IrisWorlds.add(w);
            }
        }

        if (sender().isPlayer()) {
            sender().sendMessage(C.BLUE + "Iris Worlds: ");
            for (World IrisWorld : IrisWorlds.copy()) {
                sender().sendMessage(C.IRIS + "- " + IrisWorld.getName() + C.GRAY + ", " + IrisToolbelt.access(IrisWorld).getEngine().getEnginePlayers().stream().count() + " players");
                for (EnginePlayer player : IrisToolbelt.access(IrisWorld).getEngine().getEnginePlayers()) {
                    sender().sendMessage(C.DARK_GRAY + "> " + player.getPlayer().getName());
                }
            }
        } else {
            Iris.info(C.BLUE + "Iris Worlds: ");
            for (World IrisWorld : IrisWorlds.copy()) {
                Iris.info(C.IRIS + "- " + IrisWorld.getName() + C.GRAY + ", " + IrisToolbelt.access(IrisWorld).getEngine().getEnginePlayers().stream().count() + " players");
                for (EnginePlayer player : IrisToolbelt.access(IrisWorld).getEngine().getEnginePlayers()) {
                    Iris.info(C.DARK_GRAY + "> " + player.getPlayer().getName());
                }
            }
        }
    }

    @Decree(description = "Test the compression algorithms")
    public void compression(
            @Param(description = "base IrisWorld") World world,
            @Param(description = "raw TectonicPlate File") String path,
            @Param(description = "Algorithm to Test") String algorithm,
            @Param(description = "Amount of Tests") int amount) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }

        File file = new File(path);
        if (!file.exists()) return;

        Engine engine = IrisToolbelt.access(world).getEngine();
        if (engine != null) {
            int height = engine.getTarget().getHeight();
            VolmitSender sender = sender();
            new Thread(() -> {
                try {
                    DataInputStream raw = new DataInputStream(new FileInputStream(file));
                    TectonicPlate plate = new TectonicPlate(height, raw);
                    raw.close();

                    double d1 = 0;
                    double d2 = 0;
                    long size = 0;
                    File folder = new File("tmp");
                    folder.mkdirs();
                    for (int i = 0; i < amount; i++) {
                        File tmp = new File(folder, RandomStringUtils.randomAlphanumeric(10) + "." + algorithm + ".bin");
                        DataOutputStream dos = createOutput(tmp, algorithm);
                        long start = System.currentTimeMillis();
                        plate.write(dos);
                        dos.close();
                        d1 += System.currentTimeMillis() - start;
                        if (size == 0)
                            size = tmp.length();
                        start = System.currentTimeMillis();
                        DataInputStream din = createInput(tmp, algorithm);
                        new TectonicPlate(height, din);
                        din.close();
                        d2 += System.currentTimeMillis() - start;
                        tmp.delete();
                    }
                    IO.delete(folder);
                    sender.sendMessage(algorithm + " is " + Form.fileSize(size) + " big after compression");
                    sender.sendMessage(algorithm + " Took " + d2 / amount + "ms to read");
                    sender.sendMessage(algorithm + " Took " + d1 / amount + "ms to write");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }, "Compression Test").start();
        } else {
            Iris.info(C.RED + "Engine is null!");
        }
    }

    private DataInputStream createInput(File file, String algorithm) throws Throwable {
        FileInputStream in = new FileInputStream(file);

        return new DataInputStream(switch (algorithm) {
            case "gzip" -> new GZIPInputStream(in);
            case "lz4f" -> new LZ4FrameInputStream(in);
            case "lz4b" -> new LZ4BlockInputStream(in);
            default -> throw new IllegalStateException("Unexpected value: " + algorithm);
        });
    }

    private DataOutputStream createOutput(File file, String algorithm) throws Throwable {
        FileOutputStream out = new FileOutputStream(file);

        return new DataOutputStream(switch (algorithm) {
            case "gzip" -> new GZIPOutputStream(out);
            case "lz4f" -> new LZ4FrameOutputStream(out);
            case "lz4b" -> new LZ4BlockOutputStream(out);
            default -> throw new IllegalStateException("Unexpected value: " + algorithm);
        });
    }
}


