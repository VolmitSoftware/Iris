package art.arcane.iris.probe;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

public final class CaveStiltProbe {
    private static final String LOG = "[cave-stilt-probe]";
    private static final int SURFACE_MARGIN = 3;
    private static final int ORE_RUN = 3;
    private static final int STEM_RUN = 6;
    private static final int FLORA_RUN = 3;
    private static final int ROCK_RUN = 6;
    private static final int SHEET_HALF = 20;
    private static final int SHEET_UP = 22;
    private static final int SHEET_SCALE = 8;
    private static final byte MASK_OBJECT = 1;
    private static final List<String> DRY_SAMPLES = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> FLOATER_SAMPLES = Collections.synchronizedList(new ArrayList<>());

    private CaveStiltProbe() {
    }

    public static void main(String[] arguments) {
        int code = 2;
        try {
            long failures = switch (arguments[0]) {
                case "scan" -> scan(new File(arguments[1]), arguments[2], Long.parseLong(arguments[3]),
                        HydrologyWindows.WindowRange.parse(arguments[4]), new File(arguments[5]), Integer.parseInt(arguments[6]),
                        Integer.parseInt(arguments[7]));
                case "sheet" -> sheet(new File(arguments[1]), arguments[2], Long.parseLong(arguments[3]), Integer.parseInt(arguments[4]), Integer.parseInt(arguments[5]), Integer.parseInt(arguments[6]), new File(arguments[7]));
                case "dump" -> dump(new File(arguments[1]), arguments[2], Long.parseLong(arguments[3]), Integer.parseInt(arguments[4]), Integer.parseInt(arguments[5]), new File(arguments[6]));
                case "palette" -> palette(new File(arguments[1]), arguments[2]);
                case "diff" -> diff(new File(arguments[1]), new File(arguments[2]), Long.parseLong(arguments[3]));
                default -> throw new IllegalArgumentException("Unknown mode " + arguments[0]);
            };
            System.out.println(LOG + " failures=" + failures);
            code = failures == 0L ? 0 : 1;
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        System.exit(code);
    }

    private record Run(int x, int z, int bottom, int top, String block, String kind, int exposed, int lonely, String biome, String source, String object) {
        int length() {
            return top - bottom + 1;
        }
    }

    private record ColumnHash(long whole, long upper, byte mask) {
    }

    private static final class Instance {
        final Map<Long, int[]> cells = new HashMap<>();
        int minY = Integer.MAX_VALUE;
        int columns;
        int touching;
        int hanging;
        int lowestCells;
        int lowestAir;
        int touchingUp;
        int trunkBottoms;
        int trunkSupported;
        int rockLowest;
    }

    private static long scan(File pack, String dimension, long seed, HydrologyWindows.WindowRange chunksBox, File out, int threads, int images) throws Exception {
        out.mkdirs();
        List<Run> runs = Collections.synchronizedList(new ArrayList<>());
        Map<Long, ColumnHash[]> columns = new ConcurrentHashMap<>();
        Map<String, Instance> instances = new ConcurrentHashMap<>();
        Map<String, AtomicLong> cellCounts = new ConcurrentHashMap<>();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, dimension, LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "stilt")) {
            Engine engine = session.engine();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int chunkZ = chunksBox.minZ(); chunkZ <= chunksBox.maxZ(); chunkZ++) {
                    for (int chunkX = chunksBox.minX(); chunkX <= chunksBox.maxX(); chunkX++) {
                        int cx = chunkX;
                        int cz = chunkZ;
                        futures.add(pool.submit(() -> {
                            RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(engine, cx, cz);
                            scanChunk(engine, chunk, runs, columns, instances, cellCounts);
                            return null;
                        }));
                    }
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                pool.shutdown();
            }
            List<Throwable> reports = RealPackProbeSupport.settleAndDrain(engine);
            if (!reports.isEmpty()) {
                RealPackProbeSupport.printReports(LOG, "scan", reports);
            }
            runs.sort(Comparator.comparingInt(Run::x).thenComparingInt(Run::z).thenComparingInt(Run::bottom));
            writeRuns(new File(out, "runs-" + seed + ".tsv"), runs);
            writeColumns(new File(out, "columns-" + seed + ".bin"), columns);
            summarize(seed, runs, instances, cellCounts, chunksBox.count());
            writeInstances(new File(out, "objects-" + seed + ".tsv"), instances);
            renderSamples(engine, runs, new File(out, "img"), seed, images);
            return reports.size();
        }
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static void scanChunk(Engine engine, RealPackProbeSupport.GeneratedChunk chunk, List<Run> runs,
                                  Map<Long, ColumnHash[]> columns, Map<String, Instance> instances, Map<String, AtomicLong> cellCounts) {
        int height = chunk.height();
        Hunk<NativeBlockState> blocks = chunk.blocks();
        String[] names = new String[16 * 16 * height];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.get(lx, y, lz);
                    names[index(lx, y, lz)] = state == null ? "minecraft:air" : IrisProceduralBlocks.materialKey(state).intern();
                }
            }
        }
        MantleChunk<Matter> mantle = engine.getMantle().getMantle().useChunk(chunk.chunkX(), chunk.chunkZ());
        String[] markers = new String[16 * 16 * height];
        try {
            mantle.iterate(HydrologyCaveCell.class, (lx, y, lz, cell) -> {
                if (cell != null && cell.action() == HydrologyCaveAction.DRY_AIR && y < height && !isOpen(names[index(lx, y, lz)])) {
                    cellCounts.computeIfAbsent("dry-grotto-cell-solid " + names[index(lx, y, lz)], ignored -> new AtomicLong()).incrementAndGet();
                    if (DRY_SAMPLES.size() < 12) {
                        DRY_SAMPLES.add(names[index(lx, y, lz)] + " " + ((chunk.chunkX() << 4) + lx) + "," + y + "," + ((chunk.chunkZ() << 4) + lz));
                    }
                }
            });
            for (int section = 0; section << 4 < height; section++) {
                Matter matter = mantle.exists(section) ? mantle.get(section) : null;
                MatterSlice<String> slice = matter == null ? null : matter.getSlice(String.class);
                if (slice == null) {
                    continue;
                }
                for (int ly = 0; ly < 16 && (section << 4) + ly < height; ly++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            String marker = slice.get(lx, ly, lz);
                            if (marker != null && marker.indexOf('@') > 0) {
                                markers[index(lx, (section << 4) + ly, lz)] = marker;
                            }
                        }
                    }
                }
            }
        } finally {
            mantle.release();
        }
        ColumnHash[] hashes = new ColumnHash[256];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunk.chunkX() << 4) + lx;
                int z = (chunk.chunkZ() << 4) + lz;
                int surface = Engine.hostHeight(engine, x, z, true);
                long whole = 1125899906842597L;
                long upper = 1125899906842597L;
                byte mask = 0;
                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.get(lx, y, lz);
                    int code = state == null ? 0 : state.key().hashCode();
                    whole = whole * 1099511628211L + code;
                    if (y >= surface - SURFACE_MARGIN) {
                        upper = upper * 1099511628211L + code;
                    }
                    String marker = markers[index(lx, y, lz)];
                    if (marker != null && y < surface - SURFACE_MARGIN) {
                        mask = MASK_OBJECT;
                        noteInstance(instances, marker, names, markers, lx, y, lz, x, z, height);
                    }
                }
                hashes[lx << 4 | lz] = new ColumnHash(whole, upper, mask);
                scanColumn(engine, names, markers, lx, lz, x, z, Math.min(surface - SURFACE_MARGIN, height), runs, cellCounts);
            }
        }
        columns.put(columnKey(chunk.chunkX(), chunk.chunkZ()), hashes);
        scanFloaters(chunk, names, markers, cellCounts, FLOATER_SAMPLES);
    }

    private static void noteInstance(Map<String, Instance> instances, String marker, String[] names, String[] markers, int lx, int y, int lz, int x, int z, int height) {
        if (y + 1 < height && !marker.equals(markers[index(lx, y + 1, lz)]) && !isOpen(names[index(lx, y + 1, lz)])) {
            Instance top = instances.computeIfAbsent(marker, ignored -> new Instance());
            synchronized (top) {
                top.touchingUp++;
            }
        }
        if (y == 0 || marker.equals(markers[index(lx, y - 1, lz)])) {
            return;
        }
        String below = names[index(lx, y - 1, lz)];
        String self = names[index(lx, y, lz)];
        if (self.endsWith("mushroom_stem") || self.endsWith("_log")) {
            Instance trunk = instances.computeIfAbsent(marker, ignored -> new Instance());
            synchronized (trunk) {
                trunk.trunkBottoms++;
                if (!isOpen(below)) {
                    trunk.trunkSupported++;
                }
            }
        }
        Instance instance = instances.computeIfAbsent(marker, ignored -> new Instance());
        synchronized (instance) {
            int kind = isOpen(below) ? 0 : 1;
            instance.cells.put((long) y << 42 | (columnKey(x, z) & 0x3ffffffffffL), new int[]{y, kind, classify(names[index(lx, y, lz)]).equals("other") ? 1 : 0});
        }
    }

    private static int index(int lx, int y, int lz) {
        return y << 8 | lx << 4 | lz;
    }

    private static boolean isOpen(String name) {
        return name.endsWith(":air") || name.endsWith("cave_air") || name.endsWith(":water") || name.endsWith(":lava");
    }

    static String classify(String name) {
        String plain = name.substring(name.indexOf(':') + 1);
        if (plain.endsWith("_ore") || plain.equals("ancient_debris")) {
            return "ore";
        }
        if (plain.equals("mushroom_stem") || plain.endsWith("_mushroom_block") || plain.endsWith("_stem") || plain.endsWith("_hyphae")
                || plain.endsWith("_wart_block") || plain.equals("shroomlight")) {
            return "stem";
        }
        if (plain.endsWith("_log") || plain.endsWith("_wood") || plain.endsWith("_leaves") || plain.equals("hay_block") || plain.endsWith("_mushroom")
                || plain.contains("fungus") || plain.equals("moss_block") || plain.endsWith("_roots")) {
            return "flora";
        }
        return "other";
    }

    private static void scanColumn(Engine engine, String[] names, String[] markers, int lx, int lz, int x, int z, int top, List<Run> runs,
                                   Map<String, AtomicLong> cellCounts) {
        int y = 1;
        while (y < top) {
            String name = names[index(lx, y, lz)];
            int end = y;
            while (end + 1 < top && names[index(lx, end + 1, lz)].equals(name)) {
                end++;
            }
            if (!isOpen(name)) {
                String kind = classify(name);
                int minimum = switch (kind) {
                    case "ore" -> ORE_RUN;
                    case "stem" -> STEM_RUN;
                    case "flora" -> FLORA_RUN;
                    default -> ROCK_RUN;
                };
                int length = end - y + 1;
                if (length >= minimum && !kind.equals("other")) {
                    int exposed = 0;
                    int lonely = 0;
                    for (int level = y; level <= end; level++) {
                        if (exposedCell(names, lx, level, lz)) {
                            exposed++;
                        }
                        if (lonelyCell(names, name, lx, level, lz)) {
                            lonely++;
                        }
                    }
                    if (exposed * 2 >= length || lonely * 4 >= length * 3) {
                        String source = "natural";
                        String object = "";
                        String marker = markers[index(lx, (y + end) / 2, lz)];
                        if (marker != null) {
                            source = "object";
                            object = marker.substring(0, marker.indexOf('@'));
                        }
                        runs.add(new Run(x, z, y, end, name, kind, exposed, lonely, biomeNear(engine, names, lx, lz, x, z, y, end), source, object));
                    }
                }
            }
            y = end + 1;
        }
    }

    private static boolean exposedCell(String[] names, int lx, int y, int lz) {
        return (lx > 0 && isOpen(names[index(lx - 1, y, lz)])) || (lx < 15 && isOpen(names[index(lx + 1, y, lz)]))
                || (lz > 0 && isOpen(names[index(lx, y, lz - 1)])) || (lz < 15 && isOpen(names[index(lx, y, lz + 1)]));
    }

    private static boolean lonelyCell(String[] names, String name, int lx, int y, int lz) {
        return (lx == 0 || !names[index(lx - 1, y, lz)].equals(name)) && (lx == 15 || !names[index(lx + 1, y, lz)].equals(name))
                && (lz == 0 || !names[index(lx, y, lz - 1)].equals(name)) && (lz == 15 || !names[index(lx, y, lz + 1)].equals(name));
    }

    private static void scanFloaters(RealPackProbeSupport.GeneratedChunk chunk, String[] names, String[] markers, Map<String, AtomicLong> counts, List<String> samples) {
        int height = chunk.height();
        for (int lx = 1; lx < 15; lx++) {
            for (int lz = 1; lz < 15; lz++) {
                for (int y = 1; y < height - 1; y++) {
                    String name = names[index(lx, y, lz)];
                    if (isOpen(name) || !(classify(name).equals("ore") || name.endsWith("packed_ice"))) {
                        continue;
                    }
                    int open = (isOpen(names[index(lx - 1, y, lz)]) ? 1 : 0) + (isOpen(names[index(lx + 1, y, lz)]) ? 1 : 0)
                            + (isOpen(names[index(lx, y, lz - 1)]) ? 1 : 0) + (isOpen(names[index(lx, y, lz + 1)]) ? 1 : 0)
                            + (isOpen(names[index(lx, y - 1, lz)]) ? 1 : 0) + (isOpen(names[index(lx, y + 1, lz)]) ? 1 : 0);
                    if (classify(name).equals("ore")) {
                        noteLine(names, name, lx, y, lz, counts, height);
                    }
                    if (open >= 5) {
                        String source = markers[index(lx, y, lz)] == null ? "unmarked" : "object";
                        counts.computeIfAbsent(name + " " + source, ignored -> new AtomicLong()).incrementAndGet();
                        if (samples.size() < 60) {
                            samples.add(name + " " + ((chunk.chunkX() << 4) + lx) + "," + y + "," + ((chunk.chunkZ() << 4) + lz) + " open=" + open + " " + source);
                        }
                    }
                }
            }
        }
    }

    private static void noteLine(String[] names, String name, int lx, int y, int lz, Map<String, AtomicLong> counts, int height) {
        int[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        String[] labels = {"x", "y", "z"};
        for (int axis = 0; axis < 3; axis++) {
            int[] step = axes[axis];
            int beforeX = lx - step[0];
            int beforeY = y - step[1];
            int beforeZ = lz - step[2];
            if (beforeX < 0 || beforeY < 0 || beforeZ < 0 || names[index(beforeX, beforeY, beforeZ)].equals(name)) {
                continue;
            }
            int length = 1;
            boolean thin = true;
            int cx = lx;
            int cy = y;
            int cz = lz;
            while (cx >= 0 && cx < 16 && cz >= 0 && cz < 16 && cy < height && names[index(cx, cy, cz)].equals(name)) {
                for (int other = 0; other < 3; other++) {
                    if (other == axis) {
                        continue;
                    }
                    int[] side = axes[other];
                    for (int sign = -1; sign <= 1; sign += 2) {
                        int sx = cx + side[0] * sign;
                        int sy = cy + side[1] * sign;
                        int sz = cz + side[2] * sign;
                        if (sx >= 0 && sx < 16 && sz >= 0 && sz < 16 && sy >= 0 && sy < height && names[index(sx, sy, sz)].equals(name)) {
                            thin = false;
                        }
                    }
                }
                cx += step[0];
                cy += step[1];
                cz += step[2];
                length++;
            }
            length--;
            if (thin && length >= 3 && cx >= 0 && cx < 16 && cz >= 0 && cz < 16) {
                counts.computeIfAbsent("oreline " + labels[axis] + " len>=3", ignored -> new AtomicLong()).incrementAndGet();
            }
        }
    }

    private static long sheet(File pack, String dimension, long seed, int x, int y, int z, File file) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, dimension, LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "sheet")) {
            render(session.engine(), new Run(x, z, y, y, "column", "other", 0, 0, "", "", ""), file);
        }
        return 0L;
    }

    private static long dump(File pack, String dimension, long seed, int x, int z, File file) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, dimension, LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "dump");
             PrintWriter writer = new PrintWriter(file)) {
            Engine engine = session.engine();
            RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(engine, x >> 4, z >> 4);
            writer.println("surface " + Engine.hostHeight(engine, x, z, true));
            MantleChunk<Matter> mantle = engine.getMantle().getMantle().useChunk(x >> 4, z >> 4);
            try {
                for (int y = 0; y < chunk.height(); y++) {
                    NativeBlockState state = chunk.blocks().get(x & 15, y, z & 15);
                    Matter matter = mantle.exists(y >> 4) ? mantle.get(y >> 4) : null;
                    MatterSlice<String> slice = matter == null ? null : matter.getSlice(String.class);
                    writer.println(y + " " + (state == null ? "minecraft:air" : state.key()) + " " + (slice == null ? "" : slice.get(x & 15, y & 15, z & 15)));
                }
            } finally {
                mantle.release();
            }
        }
        return 0L;
    }

    private static long palette(File pack, String dimension) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, dimension, LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(1L, false, "palette")) {
            Path root = new File(pack, "objects").toPath();
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(path -> path.toString().endsWith(".iob")).sorted().toList();
            }
            int withOre = 0;
            int bottomOre = 0;
            for (Path file : files) {
                String relative = root.relativize(file).toString();
                String key = relative.substring(0, relative.length() - 4);
                IrisObject object = session.engine().getData().getObjectLoader().load(key);
                if (object == null) {
                    continue;
                }
                int lowest = Integer.MAX_VALUE;
                int ores = 0;
                for (Map.Entry<IrisBlockVector, NativeBlockState> entry : object.getBlocks()) {
                    if (entry.getValue().isOccluding()) {
                        lowest = Math.min(lowest, entry.getKey().getBlockY());
                    }
                    if (classify(IrisProceduralBlocks.materialKey(entry.getValue())).equals("ore")) {
                        ores++;
                    }
                }
                int bottom = 0;
                for (Map.Entry<IrisBlockVector, NativeBlockState> entry : object.getBlocks()) {
                    if (entry.getKey().getBlockY() == lowest && classify(IrisProceduralBlocks.materialKey(entry.getValue())).equals("ore")) {
                        bottom++;
                    }
                }
                if (ores > 0) {
                    withOre++;
                    bottomOre += bottom > 0 ? 1 : 0;
                    System.out.println(LOG + " ore-object " + key + " ore=" + ores + " bottomLayerOre=" + bottom);
                }
            }
            System.out.println(LOG + " objects=" + files.size() + " withOre=" + withOre + " oreInBottomLayer=" + bottomOre);
        }
        return 0L;
    }

    private static String biomeNear(Engine engine, String[] names, int lx, int lz, int x, int z, int bottom, int top) {
        int y = (bottom + top) / 2;
        IrisBiome biome = engine.getCaveOrMantleBiome(x, y, z);
        return biome == null ? "?" : biome.getLoadKey();
    }

    private static void writeRuns(File file, List<Run> runs) throws Exception {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("x\tz\tbottom\ttop\tlength\tblock\tkind\texposed\tlonely\tbiome\tsource\tobject");
            for (Run run : runs) {
                writer.printf(Locale.ROOT, "%d\t%d\t%d\t%d\t%d\t%s\t%s\t%d\t%d\t%s\t%s\t%s%n", run.x(), run.z(), run.bottom(), run.top(), run.length(),
                        run.block(), run.kind(), run.exposed(), run.lonely(), run.biome(), run.source(), run.object());
            }
        }
    }

    private static void writeColumns(File file, Map<Long, ColumnHash[]> columns) throws Exception {
        List<Long> keys = new ArrayList<>(columns.keySet());
        Collections.sort(keys);
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeInt(keys.size());
            for (long key : keys) {
                output.writeLong(key);
                for (ColumnHash hash : columns.get(key)) {
                    output.writeLong(hash.whole());
                    output.writeLong(hash.upper());
                    output.writeByte(hash.mask());
                }
            }
        }
    }

    private static Map<Long, ColumnHash[]> readColumns(File file) throws Exception {
        Map<Long, ColumnHash[]> columns = new HashMap<>();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int size = input.readInt();
            for (int entry = 0; entry < size; entry++) {
                long key = input.readLong();
                ColumnHash[] hashes = new ColumnHash[256];
                for (int column = 0; column < 256; column++) {
                    hashes[column] = new ColumnHash(input.readLong(), input.readLong(), input.readByte());
                }
                columns.put(key, hashes);
            }
        }
        return columns;
    }

    private static void summarize(long seed, List<Run> runs, Map<String, Instance> instances, Map<String, AtomicLong> cellCounts, int chunks) {
        Map<String, long[]> table = new TreeMap<>();
        Map<String, long[]> biomes = new TreeMap<>();
        for (Run run : runs) {
            String key = run.kind() + "\t" + run.source() + "\t" + run.block();
            long[] row = table.computeIfAbsent(key, ignored -> new long[3]);
            row[0]++;
            row[1] += run.length();
            row[2] = Math.max(row[2], run.length());
            if (!run.kind().equals("other") && !run.source().equals("natural")) {
                biomes.computeIfAbsent(run.biome() + "\t" + run.kind(), ignored -> new long[1])[0]++;
            }
        }
        System.out.println(LOG + " seed=" + seed + " chunks=" + chunks + " runs=" + runs.size());
        for (Map.Entry<String, long[]> row : table.entrySet()) {
            System.out.printf(Locale.ROOT, "%s run\t%s\tcount=%d\tcells=%d\tmax=%d%n", LOG, row.getKey(), row.getValue()[0], row.getValue()[1], row.getValue()[2]);
        }
        for (Map.Entry<String, long[]> row : biomes.entrySet()) {
            System.out.printf(Locale.ROOT, "%s biome\t%s\tcount=%d%n", LOG, row.getKey(), row.getValue()[0]);
        }
        for (Map.Entry<String, AtomicLong> floater : new TreeMap<>(cellCounts).entrySet()) {
            System.out.printf(Locale.ROOT, "%s floater\t%s\t%d%n", LOG, floater.getKey(), floater.getValue().get());
        }
        for (String sample : FLOATER_SAMPLES.subList(0, Math.min(12, FLOATER_SAMPLES.size()))) {
            System.out.println(LOG + " floater-at " + sample);
        }
        System.out.println(LOG + " dry-solid-samples " + DRY_SAMPLES);
        Map<String, long[]> lonely = new TreeMap<>();
        for (Run run : runs) {
            if (run.lonely() * 4 >= run.length() * 3 && !run.kind().equals("other")) {
                long[] row = lonely.computeIfAbsent(run.kind() + " " + run.source() + " " + run.block(), ignored -> new long[2]);
                row[0]++;
                row[1] = Math.max(row[1], run.length());
            }
        }
        for (Map.Entry<String, long[]> row : lonely.entrySet()) {
            System.out.printf(Locale.ROOT, "%s tile-column\t%s\tcount=%d\tmax=%d%n", LOG, row.getKey(), row.getValue()[0], row.getValue()[1]);
        }
        long total = 0L;
        long floating = 0L;
        long disc = 0L;
        long discRock = 0L;
        long hung = 0L;
        long trunked = 0L;
        long trunkHover = 0L;
        long lowestCells = 0L;
        long lowestAir = 0L;
        for (Instance instance : instances.values()) {
            settle(instance);
            total++;
            lowestCells += instance.lowestCells;
            lowestAir += instance.lowestAir;
            if (instance.touching == 0 && instance.touchingUp == 0) {
                floating++;
            }
            if (instance.touching == 0 && instance.touchingUp > 0) {
                hung++;
            }
            if (instance.trunkBottoms > 0) {
                trunked++;
                if (instance.trunkSupported == 0 && instance.touchingUp == 0) {
                    trunkHover++;
                }
            }
            if (instance.touchingUp == 0 && instance.lowestCells >= 5 && instance.lowestAir * 2 > instance.lowestCells) {
                disc++;
                if (instance.rockLowest * 2 > instance.lowestCells) {
                    discRock++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "%s objects\ttrunked=%d\ttrunkHover=%d\tinstances=%d\thung=%d\tfloating=%d\tdisc=%d\tdiscRock=%d\tlowestCells=%d\tlowestOverAir=%d%n", LOG, trunked, trunkHover, total, hung, floating, disc, discRock, lowestCells, lowestAir);
    }

    private static void settle(Instance instance) {
        int touching = 0;
        int hanging = 0;
        int minY = Integer.MAX_VALUE;
        for (int[] cell : instance.cells.values()) {
            minY = Math.min(minY, cell[0]);
            if (cell[1] == 1) {
                touching++;
            } else {
                hanging++;
            }
        }
        int lowest = 0;
        int lowestAir = 0;
        instance.rockLowest = 0;
        for (int[] cell : instance.cells.values()) {
            if (cell[0] == minY) {
                lowest++;
                if (cell[1] == 0) {
                    lowestAir++;
                }
                instance.rockLowest += cell[2];
            }
        }
        instance.minY = minY;
        instance.touching = touching;
        instance.hanging = hanging;
        instance.lowestCells = lowest;
        instance.lowestAir = lowestAir;
        instance.columns = instance.cells.size();
    }

    private static void writeInstances(File file, Map<String, Instance> instances) throws Exception {
        List<String> keys = new ArrayList<>(instances.keySet());
        Collections.sort(keys);
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("marker\tminY\tbottomCells\ttouching\thanging\tlowest\tlowestOverAir\ttouchingUp");
            for (String key : keys) {
                Instance instance = instances.get(key);
                writer.printf(Locale.ROOT, "%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d%n", key, instance.minY, instance.columns, instance.touching, instance.hanging, instance.lowestCells, instance.lowestAir, instance.touchingUp);
            }
        }
    }

    private static long diff(File before, File after, long seed) throws Exception {
        Map<Long, ColumnHash[]> first = readColumns(new File(before, "columns-" + seed + ".bin"));
        Map<Long, ColumnHash[]> second = readColumns(new File(after, "columns-" + seed + ".bin"));
        if (!first.keySet().equals(second.keySet())) {
            throw new IllegalStateException("Chunk sets differ for seed " + seed);
        }
        long columns = 0L;
        long identical = 0L;
        long objectColumns = 0L;
        long objectDiffer = 0L;
        long awayDiffer = 0L;
        long upperDiffer = 0L;
        long upperAway = 0L;
        long chunks = 0L;
        long cleanChunks = 0L;
        long cleanChunksDiffer = 0L;
        List<String> away = new ArrayList<>();
        for (Map.Entry<Long, ColumnHash[]> entry : first.entrySet()) {
            ColumnHash[] left = entry.getValue();
            ColumnHash[] right = second.get(entry.getKey());
            chunks++;
            boolean clean = true;
            boolean differs = false;
            for (int column = 0; column < 256; column++) {
                columns++;
                boolean object = (left[column].mask() | right[column].mask()) != 0;
                clean &= !object;
                boolean same = left[column].whole() == right[column].whole();
                differs |= !same;
                if (object) {
                    objectColumns++;
                }
                if (same) {
                    identical++;
                } else if (object) {
                    objectDiffer++;
                } else {
                    awayDiffer++;
                    if (away.size() < 40) {
                        int cx = (int) (entry.getKey() >> 32);
                        int cz = (int) (long) entry.getKey();
                        away.add(((cx << 4) + (column >> 4)) + "," + ((cz << 4) + (column & 15)));
                    }
                }
                if (left[column].upper() != right[column].upper()) {
                    upperDiffer++;
                    if (!object) {
                        upperAway++;
                    }
                }
            }
            if (clean) {
                cleanChunks++;
                if (differs) {
                    cleanChunksDiffer++;
                }
            }
        }
        long digestBefore = digest(first, new HashSet<>(), left -> left.upper());
        System.out.printf(Locale.ROOT, "%s diff seed=%d chunks=%d columns=%d identical=%d objectColumns=%d objectColumnsDiffer=%d awayColumnsDiffer=%d surfaceBandDiffer=%d surfaceBandAway=%d cleanChunks=%d cleanChunksDiffer=%d surfaceDigestBefore=%x%n",
                LOG, seed, chunks, columns, identical, objectColumns, objectDiffer, awayDiffer, upperDiffer, upperAway, cleanChunks, cleanChunksDiffer, digestBefore);
        if (!away.isEmpty()) {
            System.out.println(LOG + " away sample " + away);
        }
        return 0L;
    }

    private static long digest(Map<Long, ColumnHash[]> columns, Set<Long> skip, ToLongFunction<ColumnHash> field) {
        List<Long> keys = new ArrayList<>(columns.keySet());
        Collections.sort(keys);
        long digest = 1125899906842597L;
        for (long key : keys) {
            if (skip.contains(key)) {
                continue;
            }
            for (ColumnHash hash : columns.get(key)) {
                digest = digest * 1099511628211L + field.applyAsLong(hash);
            }
        }
        return digest;
    }

    private static void renderSamples(Engine engine, List<Run> runs, File out, long seed, int images) throws Exception {
        if (images <= 0) {
            return;
        }
        out.mkdirs();
        Map<String, Integer> taken = new HashMap<>();
        List<Run> ranked = new ArrayList<>();
        for (Run run : runs) {
            if (!run.kind().equals("other") && !run.source().equals("natural")) {
                ranked.add(run);
            }
        }
        for (Run run : runs) {
            if (run.kind().equals("ore") && run.length() >= 4) {
                ranked.add(run);
            }
        }
        ranked.sort(Comparator.comparingInt(Run::length).reversed());
        int written = 0;
        for (Run run : ranked) {
            String key = run.source() + "/" + run.block();
            if (taken.merge(key, 1, Integer::sum) > 2 || written >= images) {
                continue;
            }
            render(engine, run, new File(out, seed + "-" + run.source() + "-" + run.block().replace(':', '_') + "-" + run.x() + "_" + run.bottom() + "_" + run.z() + ".png"));
            written++;
        }
    }

    private static void render(Engine engine, Run run, File file) throws Exception {
        int y0 = Math.max(0, run.bottom() - 10);
        int y1 = y0 + SHEET_UP * 2;
        int wide = SHEET_HALF * 2 + 1;
        int high = y1 - y0 + 1;
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = new HashMap<>();
        BufferedImage image = new BufferedImage((wide * 2 + 2) * SHEET_SCALE, high * SHEET_SCALE + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x101014));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int view = 0; view < 2; view++) {
            for (int across = -SHEET_HALF; across <= SHEET_HALF; across++) {
                for (int y = y0; y <= y1; y++) {
                    int x = view == 0 ? run.x() + across : run.x();
                    int z = view == 0 ? run.z() : run.z() + across;
                    String name = name(engine, chunks, x, y, z);
                    graphics.setColor(new Color(color(name)));
                    int px = (across + SHEET_HALF + view * (wide + 2)) * SHEET_SCALE;
                    int py = (y1 - y) * SHEET_SCALE;
                    graphics.fillRect(px, py, SHEET_SCALE, SHEET_SCALE);
                    if (across == 0 && y >= run.bottom() && y <= run.top()) {
                        graphics.setColor(Color.WHITE);
                        graphics.drawRect(px, py, SHEET_SCALE - 1, SHEET_SCALE - 1);
                    }
                }
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.drawString(run.block() + " x" + run.length() + " at " + run.x() + "," + run.bottom() + ".." + run.top() + "," + run.z() + " " + run.source() + " " + run.object(), 6, high * SHEET_SCALE + 18);
        graphics.drawString("biome " + run.biome() + "  left: x-y slice, right: z-y slice", 6, high * SHEET_SCALE + 38);
        graphics.dispose();
        ImageIO.write(image, "png", file);
    }

    private static String name(Engine engine, Map<Long, RealPackProbeSupport.GeneratedChunk> chunks, int x, int y, int z) {
        long key = columnKey(x >> 4, z >> 4);
        RealPackProbeSupport.GeneratedChunk chunk = chunks.computeIfAbsent(key, ignored -> RealPackProbeSupport.generateChunk(engine, x >> 4, z >> 4));
        if (y >= chunk.height()) {
            return "minecraft:air";
        }
        NativeBlockState state = chunk.blocks().get(x & 15, y, z & 15);
        return state == null ? "minecraft:air" : IrisProceduralBlocks.materialKey(state);
    }

    private static int color(String name) {
        if (isOpen(name)) {
            return name.endsWith(":water") ? 0x2f62c8 : name.endsWith(":lava") ? 0xe0600f : 0x16161c;
        }
        String plain = name.substring(name.indexOf(':') + 1);
        if (plain.endsWith("_ore")) {
            return 0xf0d020;
        }
        return switch (plain) {
            case "mushroom_stem" -> 0xe8e0c8;
            case "red_mushroom_block" -> 0xc02020;
            case "brown_mushroom_block" -> 0x8a6a48;
            case "stone", "deepslate", "andesite", "tuff", "diorite", "granite" -> 0x60606a;
            case "dirt", "coarse_dirt", "podzol", "grass_block", "mycelium", "moss_block" -> 0x5a7040;
            default -> {
                int hash = plain.hashCode();
                yield 0x404040 | (hash & 0x7f7f7f);
            }
        };
    }
}
