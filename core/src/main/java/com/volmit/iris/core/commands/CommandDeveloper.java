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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.volmit.iris.Iris;
import com.volmit.iris.core.ServerConfigurator;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.datapack.DataVersion;
import com.volmit.iris.core.service.IrisEngineSVC;
import com.volmit.iris.core.tools.IrisPackBenchmarking;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.engine.object.IrisJigsawStructurePlacement;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.decree.specialhandlers.NullableDimensionHandler;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.CountingDataInputStream;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.SneakyThrows;
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
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Decree(name = "Developer", origin = DecreeOrigin.BOTH, description = "Iris World Manager", aliases = {"dev"})
public class CommandDeveloper implements DecreeExecutor {
    private CommandTurboPregen turboPregen;
    private CommandLazyPregen lazyPregen;

    @Decree(description = "Get Loaded TectonicPlates Count", origin = DecreeOrigin.BOTH, sync = true)
    public void EngineStatus() {
        Iris.service(IrisEngineSVC.class)
                .engineStatus(sender());
    }

    @Decree(description = "Send a test exception to sentry")
    public void Sentry() {
        Engine engine = engine();
        if (engine != null) IrisContext.getOr(engine);
        Iris.reportError(new Exception("This is a test"));
    }

    @Decree(description = "Test")
    public void mantle(@Param(defaultValue = "false") boolean plate, @Param(defaultValue = "21474836474") String name) throws Throwable {
        var base = Iris.instance.getDataFile("dump", "pv." + name + ".ttp.lz4b.bin");
        var section = Iris.instance.getDataFile("dump", "pv." + name + ".section.bin");

        //extractSection(base, section, 5604930, 4397);

        if (plate) {
            try (var in = CountingDataInputStream.wrap(new BufferedInputStream(new FileInputStream(base)))) {
                new TectonicPlate(1088, in, true);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else Matter.read(section);
        if (!TectonicPlate.hasError())
            Iris.info("Read " + (plate ? base : section).length() + " bytes from " + (plate ? base : section).getAbsolutePath());
    }

    private void extractSection(File source, File target, long offset, int length) throws IOException {
        var raf = new RandomAccessFile(source, "r");
        var bytes = new byte[length];
        raf.seek(offset);
        raf.readFully(bytes);
        raf.close();
        Files.write(target.toPath(), bytes);
    }

    @Decree(description = "Test")
    public void dumpThreads() {
        try {
            File fi = Iris.instance.getDataFile("dump", "td-" + new java.sql.Date(M.ms()) + ".txt");
            FileOutputStream fos = new FileOutputStream(fi);
            Map<Thread, StackTraceElement[]> f = Thread.getAllStackTraces();
            PrintWriter pw = new PrintWriter(fos);

            pw.println(Thread.activeCount() + "/" + f.size());
            var run = Runtime.getRuntime();
            pw.println("Memory:");
            pw.println("\tMax: " + run.maxMemory());
            pw.println("\tTotal: " + run.totalMemory());
            pw.println("\tFree: " + run.freeMemory());
            pw.println("\tUsed: " + (run.totalMemory() - run.freeMemory()));

            for (Thread i : f.keySet()) {
                pw.println("========================================");
                pw.println("Thread: '" + i.getName() + "' ID: " + i.getId() + " STATUS: " + i.getState().name());

                for (StackTraceElement j : f.get(i)) {
                    pw.println("    @ " + j.toString());
                }

                pw.println("========================================");
                pw.println();
                pw.println();
            }

            pw.close();
            Iris.info("DUMPED! See " + fi.getAbsolutePath());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @SneakyThrows
    @Decree(description = "Generate Iris structures for all loaded datapack structures")
    public void generateStructures(
            @Param(description = "The pack to add the generated structures to", aliases = "pack", defaultValue = "null", customHandler = NullableDimensionHandler.class)
            IrisDimension dimension,
            @Param(description = "Ignore existing structures", defaultValue = "false")
            boolean force
    ) {
        var map = INMS.get().collectStructures();
        if (map.isEmpty()) {
            sender().sendMessage(C.IRIS + "No structures found");
            return;
        }

        sender().sendMessage(C.IRIS + "Found " + map.size() + " structures");

        final File dataDir;
        final IrisData data;
        final Set<String> existingStructures;
        final Map<String, Set<String>> snippets;
        final File dimensionFile;
        final File structuresFolder;
        final File snippetsFolder;

        var dimensionObj = new JsonObject();

        if (dimension == null) {
            dataDir = Iris.instance.getDataFolder("structures");
            IO.delete(dataDir);
            data = IrisData.get(dataDir);
            existingStructures = Set.of();
            snippets = Map.of();
            dimensionFile = new File(dataDir, "structures.json");
        } else {
            data = dimension.getLoader();
            dataDir = data.getDataFolder();
            existingStructures = new KSet<>(data.getJigsawStructureLoader().getPossibleKeys());

            dimensionObj = data.getGson().fromJson(IO.readAll(dimension.getLoadFile()), JsonObject.class);
            snippets = Optional.ofNullable(dimensionObj.getAsJsonArray("jigsawStructures"))
                    .map(array -> array.asList()
                            .stream()
                            .filter(JsonElement::isJsonPrimitive)
                            .collect(Collectors.toMap(element -> data.getGson()
                                            .fromJson(element, IrisJigsawStructurePlacement.class)
                                            .getStructure(),
                                    element -> Set.of(element.getAsString()),
                                    KSet::merge)))
                    .orElse(Map.of());

            dimensionFile = dimension.getLoadFile();
        }
        structuresFolder = new File(dataDir, "jigsaw-structures");
        snippetsFolder = new File(dataDir, "snippet" + "/" + IrisJigsawStructurePlacement.class.getAnnotation(Snippet.class).value());

        var gson = data.getGson();
        var jigsawStructures = Optional.ofNullable(dimensionObj.getAsJsonArray("jigsawStructures"))
                .orElse(new JsonArray(map.size()));

        map.forEach((key, placement) -> {
            String loadKey = "datapack/" + key.namespace() + "/" + key.key();
            if (existingStructures.contains(loadKey) && !force)
                return;

            var structures = placement.structures();
            var obj = placement.toJson(loadKey);
            if (obj == null || structures.isEmpty()) {
                sender().sendMessage(C.RED + "Failed to generate hook for " + key);
                return;
            }
            File snippetFile = new File(snippetsFolder, loadKey + ".json");
            try {
                IO.writeAll(snippetFile, gson.toJson(obj));
            } catch (IOException e) {
                sender().sendMessage(C.RED + "Failed to generate snippet for " + key);
                e.printStackTrace();
                return;
            }

            Set<String> loadKeys = snippets.getOrDefault(loadKey, Set.of(loadKey));
            jigsawStructures.asList().removeIf(e -> loadKeys.contains((e.isJsonObject() ? e.getAsJsonObject().get("structure") : e).getAsString()));
            jigsawStructures.add("snippet/" + loadKey);

            String structureKey;
            if (structures.size() > 1) {
                KList<String> common = new KList<>();
                for (int i = 0; i < structures.size(); i++) {
                    var tags = structures.get(i).tags();
                    if (i == 0) common.addAll(tags);
                    else common.removeIf(tag -> !tags.contains(tag));
                }
                structureKey = common.isNotEmpty() ? "#" + common.getFirst() : structures.getFirst().key();
            } else structureKey = structures.getFirst().key();

            JsonArray array = new JsonArray();
            if (structures.size() > 1) {
                structures.stream()
                        .flatMap(structure -> {
                            String[] arr = new String[structure.weight()];
                            Arrays.fill(arr, structure.key());
                            return Arrays.stream(arr);
                        })
                        .forEach(array::add);
            } else array.add(structureKey);

            obj = new JsonObject();
            obj.addProperty("structureKey", structureKey);
            obj.add("datapackStructures", array);

            File out = new File(structuresFolder, loadKey + ".json");
            out.getParentFile().mkdirs();
            try {
                IO.writeAll(out, gson.toJson(obj));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        dimensionObj.add("jigsawStructures", jigsawStructures);
        IO.writeAll(dimensionFile, gson.toJson(dimensionObj));

        data.hotloaded();
    }

    @Decree(description = "Test")
    public void packBenchmark(
            @Param(description = "The pack to bench", aliases = {"pack"}, defaultValue = "overworld")
            IrisDimension dimension,
            @Param(description = "Radius in regions", defaultValue = "2048")
            int radius,
            @Param(description = "Open GUI while benchmarking", defaultValue = "false")
            boolean gui
    ) {
        new IrisPackBenchmarking(dimension, radius, gui);
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
            @Param(description = "Amount of Tests") int amount,
            @Param(description = "Is versioned", defaultValue = "false") boolean versioned) {
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
                    CountingDataInputStream raw = CountingDataInputStream.wrap(new FileInputStream(file));
                    TectonicPlate plate = new TectonicPlate(height, raw, versioned);
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
                        CountingDataInputStream din = createInput(tmp, algorithm);
                        new TectonicPlate(height, din, true);
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

    private CountingDataInputStream createInput(File file, String algorithm) throws Throwable {
        FileInputStream in = new FileInputStream(file);

        return CountingDataInputStream.wrap(switch (algorithm) {
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


