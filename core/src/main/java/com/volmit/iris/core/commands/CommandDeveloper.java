/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.ServerConfigurator;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.datapack.DataVersion;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import com.volmit.iris.core.pregenerator.ChunkUpdater;
import com.volmit.iris.core.service.IrisEngineSVC;
import com.volmit.iris.core.tools.IrisConverter;
import com.volmit.iris.core.tools.IrisPackBenchmarking;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.components.MantleObjectComponent;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisCave;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisEntity;
import com.volmit.iris.util.data.Dimension;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.math.Vector3d;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.VolmitSender;
import io.lumine.mythic.bukkit.adapters.BukkitEntity;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Decree(name = "Developer", origin = DecreeOrigin.BOTH, description = "Iris World Manager", aliases = {"dev"})
public class CommandDeveloper implements DecreeExecutor {
    private CommandTurboPregen turboPregen;
    private CommandUpdater updater;

    @Decree(description = "Get Loaded TectonicPlates Count", origin = DecreeOrigin.BOTH, sync = true)
    public void EngineStatus() {
        List<World> IrisWorlds = new ArrayList<>();
        int TotalLoadedChunks = 0;
        int TotalQueuedTectonicPlates = 0;
        int TotalNotQueuedTectonicPlates = 0;
        int TotalTectonicPlates = 0;

        long lowestUnloadDuration = 0;
        long highestUnloadDuration = 0;

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
            Engine engine = IrisToolbelt.access(world).getEngine();
            TotalQueuedTectonicPlates += (int) engine.getMantle().getToUnload();
            TotalNotQueuedTectonicPlates += (int) engine.getMantle().getNotQueuedLoadedRegions();
            TotalTectonicPlates += engine.getMantle().getLoadedRegionCount();
            if (highestUnloadDuration <= (long) engine.getMantle().getTectonicDuration()) {
                highestUnloadDuration = (long) engine.getMantle().getTectonicDuration();
            }
            if (lowestUnloadDuration >= (long) engine.getMantle().getTectonicDuration()) {
                lowestUnloadDuration = (long) engine.getMantle().getTectonicDuration();
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunk.isLoaded()) {
                    TotalLoadedChunks++;
                }
            }
        }
        Iris.info("-------------------------");
        Iris.info(C.DARK_PURPLE + "Engine Status");
        Iris.info(C.DARK_PURPLE + "Total Loaded Chunks: " + C.LIGHT_PURPLE + TotalLoadedChunks);
        Iris.info(C.DARK_PURPLE + "Tectonic Limit: " + C.LIGHT_PURPLE + IrisEngineSVC.getTectonicLimit());
        Iris.info(C.DARK_PURPLE + "Tectonic Total Plates: " + C.LIGHT_PURPLE + TotalTectonicPlates);
        Iris.info(C.DARK_PURPLE + "Tectonic Active Plates: " + C.LIGHT_PURPLE + TotalNotQueuedTectonicPlates);
        Iris.info(C.DARK_PURPLE + "Tectonic ToUnload: " + C.LIGHT_PURPLE + TotalQueuedTectonicPlates);
        Iris.info(C.DARK_PURPLE + "Lowest Tectonic Unload Duration: " + C.LIGHT_PURPLE + Form.duration(lowestUnloadDuration));
        Iris.info(C.DARK_PURPLE + "Highest Tectonic Unload Duration: " + C.LIGHT_PURPLE + Form.duration(highestUnloadDuration));
        Iris.info(C.DARK_PURPLE + "Cache Size: " + C.LIGHT_PURPLE + Form.f(IrisData.cacheSize()));
        Iris.info("-------------------------");
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
            Iris.info("Loaded count: " + c );

        }

    }

    @Decree(description = "Test")
    public void packBenchmark(
            @Param(description = "The pack to bench", aliases = {"pack"})
            IrisDimension dimension
    ) {
        Iris.info("test");
        IrisPackBenchmarking benchmark = new IrisPackBenchmarking(dimension, 1);

    }

    @Decree(description = "Upgrade to another Minecraft version")
    public void upgrade(
            @Param(description = "The version to upgrade to", defaultValue = "latest") DataVersion version) {
        sender().sendMessage(C.GREEN + "Upgrading to " + version.getVersion() + "...");
        ServerConfigurator.installDataPacks(version.get(), false);
        sender().sendMessage(C.GREEN + "Done upgrading! You can now update your server version to " + version.getVersion());
    }

    @Decree(description = "test")
    public void mca (
            @Param(description = "String") String world) {
        try {
            File[] McaFiles = new File(world, "region").listFiles((dir, name) -> name.endsWith(".mca"));
            for (File mca : McaFiles) {
                MCAFile MCARegion = MCAUtil.read(mca);
            }
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
        if(engine != null) {
            int height = engine.getTarget().getHeight();
            ExecutorService service = Executors.newFixedThreadPool(1);
            VolmitSender sender = sender();
            service.submit(() -> {
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
                    sender.sendMessage(algorithm + " Took " + d2/amount + "ms to read");
                    sender.sendMessage(algorithm + " Took " + d1/amount + "ms to write");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
            service.shutdown();
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


