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

package art.arcane.iris.core.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.service.IrisEngineSVC;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.tools.IrisPackBenchmarking;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.context.IrisContext;
import art.arcane.iris.engine.object.IrisJigsawStructurePlacement;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.decree.DecreeExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.util.decree.specialhandlers.NullableDimensionHandler;
import art.arcane.iris.util.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.io.IO;
import art.arcane.iris.util.mantle.TectonicPlate;
import art.arcane.iris.util.math.Position2;
import art.arcane.volmlib.util.math.M;
import art.arcane.iris.util.matter.Matter;
import art.arcane.iris.util.nbt.mca.MCAFile;
import art.arcane.iris.util.nbt.mca.MCAUtil;
import art.arcane.iris.util.parallel.MultiBurst;
import art.arcane.iris.util.plugin.VolmitSender;
import art.arcane.iris.util.scheduling.J;
import art.arcane.iris.util.scheduling.jobs.Job;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Director(name = "Developer", origin = DirectorOrigin.BOTH, description = "Iris World Manager", aliases = {"dev"})
public class CommandDeveloper implements DecreeExecutor {
    private static final long DELETE_CHUNK_HEARTBEAT_MS = 5000L;
    private static final int DELETE_CHUNK_MAX_ATTEMPTS = 2;
    private static final int DELETE_CHUNK_STACK_LIMIT = 20;
    private static final Set<String> ACTIVE_DELETE_CHUNK_WORLDS = ConcurrentHashMap.newKeySet();
    private CommandTurboPregen turboPregen;
    private CommandLazyPregen lazyPregen;

    @Director(description = "Get Loaded TectonicPlates Count", origin = DirectorOrigin.BOTH, sync = true)
    public void EngineStatus() {
        Iris.service(IrisEngineSVC.class)
                .engineStatus(sender());
    }

    @Director(description = "Send a test exception to sentry")
    public void Sentry() {
        Engine engine = engine();
        if (engine != null) IrisContext.getOr(engine);
        Iris.reportError(new Exception("This is a test"));
    }

    @Director(description = "QOL command to open an overworld studio world", sync = true)
    public void so() {
        sender().sendMessage(C.GREEN + "Opening studio for the \"Overworld\" pack (seed: 1337)");
        Iris.service(StudioSVC.class).open(sender(), 1337, "overworld");
    }

    @Director(description = "Set aura spins")
    public void aura(
            @Param(description = "The h color value", defaultValue = "-20")
            int h,
            @Param(description = "The s color value", defaultValue = "7")
            int s,
            @Param(description = "The b color value", defaultValue = "8")
            int b
    ) {
        IrisSettings.get().getGeneral().setSpinh(h);
        IrisSettings.get().getGeneral().setSpins(s);
        IrisSettings.get().getGeneral().setSpinb(b);
        IrisSettings.get().forceSave();
        sender().sendMessage("<rainbow>Aura Spins updated to " + h + " " + s + " " + b);
    }

    @Director(description = "Bitwise calculations")
    public void bitwise(
            @Param(description = "The first value to run calculations on")
            int value1,
            @Param(description = "The operator: | & ^ << >> %")
            String operator,
            @Param(description = "The second value to run calculations on")
            int value2
    ) {
        Integer v = null;
        switch (operator) {
            case "|" -> v = value1 | value2;
            case "&" -> v = value1 & value2;
            case "^" -> v = value1 ^ value2;
            case "%" -> v = value1 % value2;
            case ">>" -> v = value1 >> value2;
            case "<<" -> v = value1 << value2;
        }
        if (v == null) {
            sender().sendMessage(C.RED + "The operator you entered: (" + operator + ") is invalid!");
            return;
        }
        sender().sendMessage(C.GREEN + "" + value1 + " " + C.GREEN + operator.replaceAll("<", "≺").replaceAll(">", "≻").replaceAll("%", "％") + " " + C.GREEN + value2 + C.GREEN + " returns " + C.GREEN + v);
    }

    @Director(description = "Update the pack of a world (UNSAFE!)", name = "update-world", aliases = "^world")
    public void updateWorld(
            @Param(description = "The world to update", contextual = true)
            World world,
            @Param(description = "The pack to install into the world", contextual = true, aliases = "dimension")
            IrisDimension pack,
            @Param(description = "Make sure to make a backup & read the warnings first!", defaultValue = "false", aliases = "c")
            boolean confirm,
            @Param(description = "Should Iris download the pack again for you", defaultValue = "false", name = "fresh-download", aliases = {"fresh", "new"})
            boolean freshDownload
    ) {
        if (!confirm) {
            sender().sendMessage(new String[]{
                    C.RED + "You should always make a backup before using this",
                    C.YELLOW + "Issues caused by this can be, but are not limited to:",
                    C.YELLOW + " - Broken chunks (cut-offs) between old and new chunks (before & after the update)",
                    C.YELLOW + " - Regenerated chunks that do not fit in with the old chunks",
                    C.YELLOW + " - Structures not spawning again when regenerating",
                    C.YELLOW + " - Caves not lining up",
                    C.YELLOW + " - Terrain layers not lining up",
                    C.RED + "Now that you are aware of the risks, and have made a back-up:",
                    C.RED + "/iris developer update-world " + world.getName() + " " + pack.getLoadKey() + " confirm=true"
            });
            return;
        }

        File folder = world.getWorldFolder();
        folder.mkdirs();

        if (freshDownload) {
            Iris.service(StudioSVC.class).downloadSearch(sender(), pack.getLoadKey(), false, true);
        }

        Iris.service(StudioSVC.class).installIntoWorld(sender(), pack.getLoadKey(), folder);
    }

    @Director(description = "Dev cmd to fix all the broken objects caused by faulty shrinkwarp")
    public void fixObjects(
            @Param(aliases = "dimension", description = "The dimension type to create the world with")
            IrisDimension type
    ) {
        if (type == null) {
            sender().sendMessage("Type cant be null?");
            return;
        }

        IrisData dm = IrisData.get(Iris.instance.getDataFolder("packs", type.getLoadKey()));
        var loader = dm.getObjectLoader();
        var processed = new KMap<String, IrisPosition>();

        var objects = loader.getPossibleKeys();
        var pieces = dm.getJigsawPieceLoader().getPossibleKeys();
        var sender = sender();

        sender.sendMessage(C.IRIS + "Found " + objects.length + " objects in " + type.getLoadKey());
        sender.sendMessage(C.IRIS + "Found " + pieces.length + " jigsaw pieces in " + type.getLoadKey());

        final int total = objects.length;
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger changed = new AtomicInteger();

        new Job() {
            @Override
            public String getName() {
                return "Fixing Objects";
            }

            @Override
            public void execute() {
                Arrays.stream(pieces).parallel()
                        .map(dm.getJigsawPieceLoader()::load)
                        .filter(Objects::nonNull)
                        .forEach(piece -> {
                            var offset = processed.compute(piece.getObject(), (key, o) -> {
                                if (o != null) return o;
                                var obj = loader.load(key);
                                if (obj == null) return new IrisPosition();

                                obj.shrinkwrap();
                                try {
                                    if (!obj.getShrinkOffset().isZero()) {
                                        changed.incrementAndGet();
                                        obj.write(obj.getLoadFile());
                                    }
                                    completeWork();
                                } catch (IOException e) {
                                    Iris.error("Failed to write object " + obj.getLoadKey());
                                    e.printStackTrace();
                                    return new IrisPosition();
                                }

                                return new IrisPosition(obj.getShrinkOffset());
                            });
                            if (offset.getX() == 0 && offset.getY() == 0 && offset.getZ() == 0)
                                return;

                            piece.getConnectors().forEach(connector -> connector.setPosition(connector.getPosition().add(offset)));

                            try {
                                IO.writeAll(piece.getLoadFile(), dm.getGson().toJson(piece));
                            } catch (IOException e) {
                                Iris.error("Failed to write jigsaw piece " + piece.getLoadKey());
                                e.printStackTrace();
                            }
                        });

                Arrays.stream(loader.getPossibleKeys()).parallel()
                        .filter(key -> !processed.containsKey(key))
                        .map(loader::load)
                        .forEach(obj -> {
                            if (obj == null) {
                                completeWork();
                                return;
                            }

                            obj.shrinkwrap();
                            if (obj.getShrinkOffset().isZero()) {
                                completeWork();
                                return;
                            }

                            try {
                                obj.write(obj.getLoadFile());
                                completeWork();
                                changed.incrementAndGet();
                            } catch (IOException e) {
                                Iris.error("Failed to write object " + obj.getLoadKey());
                                e.printStackTrace();
                            }
                        });
            }

            @Override
            public void completeWork() {
                completed.incrementAndGet();
            }

            @Override
            public int getTotalWork() {
                return total;
            }

            @Override
            public int getWorkCompleted() {
                return completed.get();
            }
        }.execute(sender, () -> {
            var failed = total - completed.get();
            if (failed != 0) sender.sendMessage(C.IRIS + "" + failed + " objects failed!");
            if (changed.get() != 0) sender.sendMessage(C.IRIS + "" + changed.get() + " objects had their offsets changed!");
            else sender.sendMessage(C.IRIS + "No objects had their offsets changed!");
        });
    }

    @Director(description = "Test")
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

    @Director(description = "Test")
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
    @Director(description = "Generate Iris structures for all loaded datapack structures")
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

    @Director(description = "Test")
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

    @Director(description = "Upgrade to another Minecraft version")
    public void upgrade(
            @Param(description = "The version to upgrade to", defaultValue = "latest") DataVersion version) {
        sender().sendMessage(C.GREEN + "Upgrading to " + version.getVersion() + "...");
        ServerConfigurator.installDataPacks(version.get(), false);
        sender().sendMessage(C.GREEN + "Done upgrading! You can now update your server version to " + version.getVersion());
    }

    @Director(description = "test")
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

    @Director(description = "Delete nearby chunk blocks for regen testing", name = "delete-chunk", aliases = {"delchunk", "dc"}, origin = DirectorOrigin.PLAYER, sync = true)
    public void deleteChunk(
            @Param(description = "Radius in chunks around your current chunk", defaultValue = "0")
            int radius,
            @Param(description = "How many chunks to process in parallel (0 = auto)", aliases = {"threads", "concurrency"}, defaultValue = "0")
            int parallelism
    ) {
        if (radius < 0) {
            sender().sendMessage(C.RED + "Radius must be 0 or greater.");
            return;
        }

        World world = player().getWorld();
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world.");
            return;
        }
        String worldKey = world.getName().toLowerCase(Locale.ROOT);
        if (!ACTIVE_DELETE_CHUNK_WORLDS.add(worldKey)) {
            sender().sendMessage(C.RED + "A delete-chunk run is already active for this world.");
            return;
        }

        int threads = resolveDeleteChunkThreadCount(parallelism);
        int centerX = player().getLocation().getBlockX() >> 4;
        int centerZ = player().getLocation().getBlockZ() >> 4;
        List<Position2> targets = buildDeleteChunkTargets(centerX, centerZ, radius);
        int totalChunks = targets.size();
        String runId = world.getName() + "-" + System.currentTimeMillis();
        PlatformChunkGenerator access = IrisToolbelt.access(world);
        if (access == null || access.getEngine() == null) {
            ACTIVE_DELETE_CHUNK_WORLDS.remove(worldKey);
            sender().sendMessage(C.RED + "The engine access for this world is null.");
            return;
        }

        art.arcane.iris.util.mantle.Mantle mantle = access.getEngine().getMantle().getMantle();
        VolmitSender sender = sender();

        sender.sendMessage(C.GREEN + "Deleting blocks in " + C.GOLD + totalChunks + C.GREEN + " chunk(s) with " + C.GOLD + threads + C.GREEN + " worker(s).");
        if (J.isFolia()) {
            sender.sendMessage(C.YELLOW + "Folia maintenance mode enabled for lock-safe chunk wipe + mantle purge.");
        }
        sender.sendMessage(C.YELLOW + "Delete-chunk run id: " + C.GOLD + runId + C.YELLOW + ".");
        Iris.info("Delete-chunk run start: id=" + runId
                + " world=" + world.getName()
                + " center=" + centerX + "," + centerZ
                + " radius=" + radius
                + " workers=" + threads
                + " chunks=" + totalChunks);

        Set<Thread> workerThreads = ConcurrentHashMap.newKeySet();
        AtomicInteger workerCounter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Iris-DeleteChunk-" + runId + "-" + workerCounter.incrementAndGet());
            thread.setDaemon(true);
            workerThreads.add(thread);
            return thread;
        };

        Thread orchestrator = new Thread(() -> runDeleteChunkOrchestrator(
                sender,
                world,
                mantle,
                targets,
                threads,
                runId,
                worldKey,
                workerThreads,
                threadFactory
        ), "Iris-DeleteChunk-Orchestrator-" + runId);
        orchestrator.setDaemon(true);
        try {
            orchestrator.start();
            Iris.info("Delete-chunk worker dispatched on dedicated thread=" + orchestrator.getName() + " id=" + runId + ".");
        } catch (Throwable e) {
            ACTIVE_DELETE_CHUNK_WORLDS.remove(worldKey);
            sender.sendMessage(C.RED + "Failed to start delete-chunk worker thread. See console.");
            Iris.reportError(e);
        }
    }

    private int resolveDeleteChunkThreadCount(int parallelism) {
        int threads = parallelism <= 0 ? Runtime.getRuntime().availableProcessors() : parallelism;
        if (J.isFolia() && parallelism <= 0) {
            threads = 1;
        }
        return Math.max(1, threads);
    }

    private List<Position2> buildDeleteChunkTargets(int centerX, int centerZ, int radius) {
        int expected = (radius * 2 + 1) * (radius * 2 + 1);
        List<Position2> targets = new ArrayList<>(expected);
        for (int ring = 0; ring <= radius; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) {
                        continue;
                    }
                    targets.add(new Position2(centerX + x, centerZ + z));
                }
            }
        }
        return targets;
    }

    private void runDeleteChunkOrchestrator(
            VolmitSender sender,
            World world,
            art.arcane.iris.util.mantle.Mantle mantle,
            List<Position2> targets,
            int threadCount,
            String runId,
            String worldKey,
            Set<Thread> workerThreads,
            ThreadFactory threadFactory
    ) {
        long runStart = System.currentTimeMillis();
        AtomicReference<String> phase = new AtomicReference<>("bootstrap");
        AtomicLong phaseSince = new AtomicLong(runStart);
        AtomicBoolean runDone = new AtomicBoolean(false);
        Thread watchdog = createDeleteChunkSetupWatchdog(world, runId, runDone, phase, phaseSince);
        watchdog.start();

        IrisToolbelt.beginWorldMaintenance(world, "delete-chunk");
        try (ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount, threadFactory)) {
            setDeleteChunkPhase(phase, phaseSince, "dispatch", world, runId);
            DeleteChunkSummary summary = executeDeleteChunkQueue(world, mantle, targets, pool, workerThreads, runId);
            if (summary.failedChunks() <= 0) {
                sender.sendMessage(C.GREEN + "Deleted blocks in " + C.GOLD + summary.successChunks() + C.GREEN + "/" + C.GOLD + summary.totalChunks() + C.GREEN + " chunk(s).");
                return;
            }

            sender.sendMessage(C.RED + "Delete-chunk completed with " + C.GOLD + summary.failedChunks() + C.RED + " failed chunk(s).");
            sender.sendMessage(C.YELLOW + "Successful chunks: " + C.GOLD + summary.successChunks() + C.YELLOW + "/" + C.GOLD + summary.totalChunks() + C.YELLOW + ".");
            sender.sendMessage(C.YELLOW + "Retry attempts used: " + C.GOLD + summary.retryCount() + C.YELLOW + ".");
            if (!summary.failedPreview().isEmpty()) {
                sender.sendMessage(C.YELLOW + "Failed chunks sample: " + C.GOLD + summary.failedPreview());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.sendMessage(C.RED + "Delete-chunk run was interrupted.");
            Iris.warn("Delete-chunk run interrupted: id=" + runId + " world=" + world.getName());
        } catch (Throwable e) {
            sender.sendMessage(C.RED + "Delete-chunk run failed. See console.");
            Iris.reportError(e);
        } finally {
            runDone.set(true);
            watchdog.interrupt();
            IrisToolbelt.endWorldMaintenance(world, "delete-chunk");
            ACTIVE_DELETE_CHUNK_WORLDS.remove(worldKey);
            Iris.info("Delete-chunk run closed: id=" + runId + " world=" + world.getName() + " totalMs=" + (System.currentTimeMillis() - runStart));
        }
    }

    private DeleteChunkSummary executeDeleteChunkQueue(
            World world,
            art.arcane.iris.util.mantle.Mantle mantle,
            List<Position2> targets,
            ThreadPoolExecutor pool,
            Set<Thread> workerThreads,
            String runId
    ) throws InterruptedException {
        ArrayDeque<DeleteChunkTask> pending = new ArrayDeque<>(targets.size());
        long queuedAt = System.currentTimeMillis();
        for (Position2 target : targets) {
            pending.addLast(new DeleteChunkTask(target.getX(), target.getZ(), 1, queuedAt));
        }

        ConcurrentMap<String, DeleteChunkActiveTask> activeTasks = new ConcurrentHashMap<>();
        ExecutorCompletionService<DeleteChunkResult> completion = new ExecutorCompletionService<>(pool);
        List<Position2> failedChunks = new ArrayList<>();

        int totalChunks = targets.size();
        int successChunks = 0;
        int failedCount = 0;
        int retryCount = 0;
        long submittedTasks = 0L;
        long finishedTasks = 0L;
        int completedChunks = 0;
        int inFlight = 0;
        int unchangedHeartbeats = 0;
        int lastCompleted = -1;
        long lastDump = 0L;

        while (inFlight < pool.getMaximumPoolSize() && !pending.isEmpty()) {
            DeleteChunkTask task = pending.removeFirst();
            completion.submit(() -> runDeleteChunkTask(task, world, mantle, activeTasks));
            inFlight++;
            submittedTasks++;
        }

        while (completedChunks < totalChunks) {
            Future<DeleteChunkResult> future = completion.poll(DELETE_CHUNK_HEARTBEAT_MS, TimeUnit.MILLISECONDS);
            if (future == null) {
                if (completedChunks == lastCompleted) {
                    unchangedHeartbeats++;
                } else {
                    unchangedHeartbeats = 0;
                    lastCompleted = completedChunks;
                }

                Iris.warn("Delete-chunk heartbeat: id=" + runId
                        + " completed=" + completedChunks + "/" + totalChunks
                        + " remaining=" + (totalChunks - completedChunks)
                        + " queued=" + pending.size()
                        + " inFlight=" + inFlight
                        + " submitted=" + submittedTasks
                        + " finishedTasks=" + finishedTasks
                        + " retries=" + retryCount
                        + " failed=" + failedCount
                        + " poolActive=" + pool.getActiveCount()
                        + " poolQueue=" + pool.getQueue().size()
                        + " poolDone=" + pool.getCompletedTaskCount()
                        + " activeTasks=" + formatDeleteChunkActiveTasks(activeTasks));

                if (unchangedHeartbeats >= 3 && System.currentTimeMillis() - lastDump >= 10000L) {
                    lastDump = System.currentTimeMillis();
                    Iris.warn("Delete-chunk appears stalled; dumping worker stack traces for id=" + runId + ".");
                    dumpDeleteChunkWorkerStacks(workerThreads, world.getName());
                }
                continue;
            }

            DeleteChunkResult result;
            try {
                result = future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new IllegalStateException("Delete-chunk worker failed unexpectedly for run " + runId, cause);
            }

            inFlight--;
            finishedTasks++;
            long duration = result.finishedAtMs() - result.startedAtMs();

            if (result.success()) {
                completedChunks++;
                successChunks++;
                if (result.task().attempt() > 1) {
                    Iris.warn("Delete-chunk recovered after retry: id=" + runId
                            + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                            + " attempt=" + result.task().attempt()
                            + " durationMs=" + duration);
                } else if (duration >= 5000L) {
                    Iris.warn("Delete-chunk slow: id=" + runId
                            + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                            + " durationMs=" + duration
                            + " loadedAtStart=" + result.loadedAtStart());
                }
            } else if (result.task().attempt() < DELETE_CHUNK_MAX_ATTEMPTS) {
                retryCount++;
                DeleteChunkTask retryTask = result.task().retry(System.currentTimeMillis());
                pending.addLast(retryTask);
                Iris.warn("Delete-chunk retry scheduled: id=" + runId
                        + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                        + " failedAttempt=" + result.task().attempt()
                        + " nextAttempt=" + retryTask.attempt()
                        + " error=" + result.errorSummary());
            } else {
                completedChunks++;
                failedCount++;
                Position2 failed = new Position2(result.task().chunkX(), result.task().chunkZ());
                failedChunks.add(failed);
                Iris.warn("Delete-chunk terminal failure: id=" + runId
                        + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                        + " attempts=" + result.task().attempt()
                        + " error=" + result.errorSummary());
                if (result.error() != null) {
                    Iris.reportError(result.error());
                }
            }

            while (inFlight < pool.getMaximumPoolSize() && !pending.isEmpty()) {
                DeleteChunkTask task = pending.removeFirst();
                completion.submit(() -> runDeleteChunkTask(task, world, mantle, activeTasks));
                inFlight++;
                submittedTasks++;
            }
        }

        String preview = formatDeleteChunkFailedPreview(failedChunks);
        Iris.info("Delete-chunk run complete: id=" + runId
                + " world=" + world.getName()
                + " total=" + totalChunks
                + " success=" + successChunks
                + " failed=" + failedCount
                + " retries=" + retryCount
                + " submittedTasks=" + submittedTasks
                + " finishedTasks=" + finishedTasks
                + " failedPreview=" + preview);
        return new DeleteChunkSummary(totalChunks, successChunks, failedCount, retryCount, preview);
    }

    private DeleteChunkResult runDeleteChunkTask(
            DeleteChunkTask task,
            World world,
            art.arcane.iris.util.mantle.Mantle mantle,
            ConcurrentMap<String, DeleteChunkActiveTask> activeTasks
    ) {
        String worker = Thread.currentThread().getName();
        long startedAt = System.currentTimeMillis();
        boolean loadedAtStart = false;
        try {
            loadedAtStart = world.isChunkLoaded(task.chunkX(), task.chunkZ());
        } catch (Throwable ignored) {
        }

        activeTasks.put(worker, new DeleteChunkActiveTask(task.chunkX(), task.chunkZ(), task.attempt(), startedAt, loadedAtStart));
        try {
            DeleteChunkRegionResult regionResult = wipeChunkRegion(world, task.chunkX(), task.chunkZ());
            if (!regionResult.success()) {
                return DeleteChunkResult.failure(task, worker, startedAt, System.currentTimeMillis(), loadedAtStart, regionResult.error());
            }
            mantle.deleteChunk(task.chunkX(), task.chunkZ());
            return DeleteChunkResult.success(task, worker, startedAt, System.currentTimeMillis(), loadedAtStart);
        } catch (Throwable e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DeleteChunkResult.failure(task, worker, startedAt, System.currentTimeMillis(), loadedAtStart, e);
        } finally {
            activeTasks.remove(worker);
        }
    }

    private DeleteChunkRegionResult wipeChunkRegion(World world, int chunkX, int chunkZ) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        if (!J.runRegion(world, chunkX, chunkZ, () -> {
            try {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof org.bukkit.entity.Player)) {
                        entity.remove();
                    }
                }

                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight();
                for (int xx = 0; xx < 16; xx++) {
                    for (int zz = 0; zz < 16; zz++) {
                        for (int yy = minY; yy < maxY; yy++) {
                            chunk.getBlock(xx, yy, zz).setType(org.bukkit.Material.AIR, false);
                        }
                    }
                }
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                latch.countDown();
            }
        })) {
            return DeleteChunkRegionResult.fail(new IllegalStateException("Failed to schedule region task for chunk " + chunkX + "," + chunkZ));
        }

        if (!latch.await(30, TimeUnit.SECONDS)) {
            return DeleteChunkRegionResult.fail(new TimeoutException("Timed out waiting for region task at chunk " + chunkX + "," + chunkZ));
        }

        Throwable thrown = failure.get();
        if (thrown != null) {
            return DeleteChunkRegionResult.fail(thrown);
        }
        return DeleteChunkRegionResult.ok();
    }

    private Thread createDeleteChunkSetupWatchdog(
            World world,
            String runId,
            AtomicBoolean runDone,
            AtomicReference<String> phase,
            AtomicLong phaseSince
    ) {
        Thread watchdog = new Thread(() -> {
            while (!runDone.get()) {
                try {
                    Thread.sleep(DELETE_CHUNK_HEARTBEAT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (!runDone.get()) {
                    long elapsed = System.currentTimeMillis() - phaseSince.get();
                    Iris.warn("Delete-chunk setup heartbeat: id=" + runId
                            + " phase=" + phase.get()
                            + " elapsedMs=" + elapsed
                            + " world=" + world.getName());
                }
            }
        }, "Iris-DeleteChunk-SetupWatchdog-" + runId);
        watchdog.setDaemon(true);
        return watchdog;
    }

    private void setDeleteChunkPhase(
            AtomicReference<String> phase,
            AtomicLong phaseSince,
            String next,
            World world,
            String runId
    ) {
        phase.set(next);
        phaseSince.set(System.currentTimeMillis());
        Iris.info("Delete-chunk phase: id=" + runId + " phase=" + next + " world=" + world.getName());
    }

    private String formatDeleteChunkFailedPreview(List<Position2> failedChunks) {
        if (failedChunks.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int index = 0;
        for (Position2 chunk : failedChunks) {
            if (index > 0) {
                builder.append(", ");
            }
            if (index >= 10) {
                builder.append("...");
                break;
            }
            builder.append(chunk.getX()).append(",").append(chunk.getZ());
            index++;
        }
        builder.append("]");
        return builder.toString();
    }

    private String formatDeleteChunkActiveTasks(ConcurrentMap<String, DeleteChunkActiveTask> activeTasks) {
        if (activeTasks.isEmpty()) {
            return "{}";
        }

        StringBuilder builder = new StringBuilder("{");
        int count = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, DeleteChunkActiveTask> entry : activeTasks.entrySet()) {
            if (count > 0) {
                builder.append(", ");
            }
            if (count >= 8) {
                builder.append("...");
                break;
            }
            DeleteChunkActiveTask activeTask = entry.getValue();
            builder.append(entry.getKey())
                    .append("=")
                    .append(activeTask.chunkX())
                    .append(",")
                    .append(activeTask.chunkZ())
                    .append("@")
                    .append(activeTask.attempt())
                    .append("/")
                    .append(now - activeTask.startedAtMs())
                    .append("ms")
                    .append(activeTask.loadedAtStart() ? ":loaded" : ":cold");
            count++;
        }
        builder.append("}");
        return builder.toString();
    }

    private void dumpDeleteChunkWorkerStacks(Set<Thread> explicitThreads, String worldName) {
        Set<Thread> threads = new LinkedHashSet<>();
        threads.addAll(explicitThreads);
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }
            String name = thread.getName();
            if (name.startsWith("Iris-DeleteChunk-")
                    || name.startsWith("Iris EngineSVC-")
                    || name.startsWith("Iris World Manager")
                    || name.contains(worldName)) {
                threads.add(thread);
            }
        }

        for (Thread thread : threads) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }
            Iris.warn("Delete-chunk worker thread=" + thread.getName() + " state=" + thread.getState());
            StackTraceElement[] trace = thread.getStackTrace();
            int limit = Math.min(trace.length, DELETE_CHUNK_STACK_LIMIT);
            for (int i = 0; i < limit; i++) {
                Iris.warn("  at " + trace[i]);
            }
        }
    }

    private record DeleteChunkTask(int chunkX, int chunkZ, int attempt, long queuedAtMs) {
        private DeleteChunkTask retry(long now) {
            return new DeleteChunkTask(chunkX, chunkZ, attempt + 1, now);
        }
    }

    private record DeleteChunkActiveTask(int chunkX, int chunkZ, int attempt, long startedAtMs, boolean loadedAtStart) {
    }

    private record DeleteChunkResult(
            DeleteChunkTask task,
            String worker,
            long startedAtMs,
            long finishedAtMs,
            boolean loadedAtStart,
            boolean success,
            Throwable error
    ) {
        private static DeleteChunkResult success(DeleteChunkTask task, String worker, long startedAtMs, long finishedAtMs, boolean loadedAtStart) {
            return new DeleteChunkResult(task, worker, startedAtMs, finishedAtMs, loadedAtStart, true, null);
        }

        private static DeleteChunkResult failure(DeleteChunkTask task, String worker, long startedAtMs, long finishedAtMs, boolean loadedAtStart, Throwable error) {
            return new DeleteChunkResult(task, worker, startedAtMs, finishedAtMs, loadedAtStart, false, error);
        }

        private String errorSummary() {
            if (error == null) {
                return "unknown";
            }
            String message = error.getMessage();
            if (message == null || message.isEmpty()) {
                return error.getClass().getSimpleName();
            }
            return error.getClass().getSimpleName() + ": " + message;
        }
    }

    private record DeleteChunkRegionResult(boolean success, Throwable error) {
        private static DeleteChunkRegionResult ok() {
            return new DeleteChunkRegionResult(true, null);
        }

        private static DeleteChunkRegionResult fail(Throwable error) {
            return new DeleteChunkRegionResult(false, error);
        }
    }

    private record DeleteChunkSummary(int totalChunks, int successChunks, int failedChunks, int retryCount, String failedPreview) {
    }

    @Director(description = "UnloadChunks for good reasons.")
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

    @Director
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

    @Director(description = "Test", aliases = {"ip"})
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

    @Director(description = "Test the compression algorithms")
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
