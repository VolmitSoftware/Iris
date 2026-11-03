package art.arcane.iris.probe;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntArrayTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.LongArrayTag;
import art.arcane.volmlib.util.nbt.tag.NumberTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import net.jpountz.lz4.LZ4BlockInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class WaterPerchCensus {
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int SECTOR_BYTES = 4096;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int LISTED_CELLS = 40;
    private static final int NOT_WATER = Integer.MIN_VALUE;
    private static final int MISSING = Integer.MIN_VALUE;
    private static final int FLOWING_BIT = 1;
    private static final int SOLID_BED_BIT = 2;
    private static final Set<String> NON_SOLID_BED = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final List<String> FLATTEN_STRUCTURES = List.of("minecraft:village", "minecraft:pillager_outpost",
            "minecraft:mansion", "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:igloo",
            "minecraft:swamp_hut", "minecraft:shipwreck_beached", "minecraft:ruined_portal");

    private WaterPerchCensus() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 1 || arguments.length > 2) {
            throw new IllegalArgumentException("Usage: <copied region folder> [blend padding]");
        }
        int padding = arguments.length == 2 ? Integer.parseInt(arguments[1]) : 24;
        Result result = scan(Path.of(arguments[0]), padding);
        for (String line : result.lines()) {
            System.out.println(line);
        }
        System.exit(result.insideSources() == 0 ? 0 : 1);
    }

    static Result scan(Path regionFolder, int padding) throws IOException {
        Map<Long, int[]> tops = new HashMap<>();
        Map<Long, int[]> waters = new HashMap<>();
        List<Start> starts = new ArrayList<>();
        try (Stream<Path> files = Files.list(regionFolder)) {
            for (Path file : files.sorted().toList()) {
                Matcher matcher = REGION_FILE.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    readRegion(file, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), tops, waters, starts);
                }
            }
        }
        Map<Long, List<Start>> startsByChunk = new HashMap<>();
        for (Start start : starts) {
            for (Bounds piece : start.pieces()) {
                for (int chunkX = (piece.minX() - padding) >> 4; chunkX <= (piece.maxX() + padding) >> 4; chunkX++) {
                    for (int chunkZ = (piece.minZ() - padding) >> 4; chunkZ <= (piece.maxZ() + padding) >> 4; chunkZ++) {
                        List<Start> near = startsByChunk.computeIfAbsent(chunkKey(chunkX, chunkZ), key -> new ArrayList<>());
                        if (!near.contains(start)) {
                            near.add(start);
                        }
                    }
                }
            }
        }
        long waterColumns = 0L;
        long insideSources = 0L;
        long outsideSources = 0L;
        long insideFlowing = 0L;
        long outsideFlowing = 0L;
        long skippedEdges = 0L;
        Map<String, Long> byStructure = new TreeMap<>();
        Map<Long, Boolean> insideChunks = new HashMap<>();
        List<String> cells = new ArrayList<>();
        for (Map.Entry<Long, int[]> chunk : waters.entrySet()) {
            int chunkX = (int) (chunk.getKey() >> 32);
            int chunkZ = (int) (long) chunk.getKey();
            for (int index = 0; index < 256; index++) {
                int water = chunk.getValue()[index];
                if (water == NOT_WATER) {
                    continue;
                }
                waterColumns++;
                int x = (chunkX << 4) + (index & 15);
                int z = (chunkZ << 4) + (index >> 4);
                int surface = waterY(water);
                boolean lower = false;
                boolean known = true;
                for (int[] direction : CARDINALS) {
                    int neighbourTop = top(tops, x + direction[0], z + direction[1]);
                    if (neighbourTop == MISSING) {
                        known = false;
                        continue;
                    }
                    lower |= neighbourTop < surface;
                }
                if (!known) {
                    skippedEdges++;
                }
                if (!lower || !solidBed(water)) {
                    continue;
                }
                Start blend = blendAt(startsByChunk.get(chunkKey(chunkX, chunkZ)), x, z, padding);
                boolean source = isSource(water);
                if (blend != null) {
                    if (source) {
                        insideSources++;
                        insideChunks.put(chunk.getKey(), true);
                        byStructure.merge(blend.id(), 1L, Long::sum);
                        if (cells.size() < LISTED_CELLS) {
                            cells.add(x + "," + surface + "," + z + " " + blend.id());
                        }
                    } else {
                        insideFlowing++;
                    }
                } else if (source) {
                    outsideSources++;
                    if (cells.size() < LISTED_CELLS) {
                        cells.add(x + "," + surface + "," + z + " outside");
                    }
                } else {
                    outsideFlowing++;
                }
            }
        }
        return new Result(tops.size(), waterColumns, skippedEdges, insideSources, outsideSources, insideFlowing, outsideFlowing,
                insideChunks.size(), starts.size(), byStructure, cells);
    }

    private static int encodeWater(int y, boolean source, boolean solidBed) {
        return (y << 2) | (source ? 0 : FLOWING_BIT) | (solidBed ? SOLID_BED_BIT : 0);
    }

    private static int waterY(int water) {
        return water >> 2;
    }

    private static boolean isSource(int water) {
        return (water & FLOWING_BIT) == 0;
    }

    private static boolean solidBed(int water) {
        return (water & SOLID_BED_BIT) != 0;
    }

    private static int top(Map<Long, int[]> tops, int x, int z) {
        int[] chunk = tops.get(chunkKey(x >> 4, z >> 4));
        return chunk == null ? MISSING : chunk[((z & 15) << 4) | (x & 15)];
    }

    private static Start blendAt(List<Start> near, int x, int z, int padding) {
        if (near == null) {
            return null;
        }
        for (Start start : near) {
            for (Bounds piece : start.pieces()) {
                if (x >= piece.minX() - padding && x <= piece.maxX() + padding
                        && z >= piece.minZ() - padding && z <= piece.maxZ() + padding) {
                    return start;
                }
            }
        }
        return null;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static void readRegion(Path file, int regionX, int regionZ, Map<Long, int[]> tops, Map<Long, int[]> waters,
                                   List<Start> starts) throws IOException {
        byte[] region = Files.readAllBytes(file);
        if (region.length < SECTOR_BYTES * 2) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(region);
        for (int index = 0; index < 1024; index++) {
            int location = buffer.getInt(index * 4);
            int sector = location >>> 8;
            if (sector == 0 || (long) sector * SECTOR_BYTES + 5 > region.length) {
                continue;
            }
            int chunkX = (regionX << 5) + (index & 31);
            int chunkZ = (regionZ << 5) + (index >> 5);
            int length = buffer.getInt(sector * SECTOR_BYTES);
            int compression = region[sector * SECTOR_BYTES + 4];
            if (length <= 1 || (compression & 0x80) != 0 || (long) sector * SECTOR_BYTES + 4 + length > region.length) {
                continue;
            }
            ByteArrayInputStream raw = new ByteArrayInputStream(region, sector * SECTOR_BYTES + 5, length - 1);
            try (InputStream input = switch (compression) {
                case 1 -> new GZIPInputStream(raw);
                case 2 -> new InflaterInputStream(raw);
                case 3 -> raw;
                case 4 -> new LZ4BlockInputStream(raw);
                default -> throw new IOException("Chunk " + chunkX + "," + chunkZ + " uses unknown compression " + compression);
            }) {
                Tag<?> parsed = NBTUtil.read(input, false).getTag();
                if (!(parsed instanceof CompoundTag root)) {
                    throw new IOException("Chunk " + chunkX + "," + chunkZ + " is not a compound");
                }
                readChunk(root, chunkX, chunkZ, tops, waters, starts);
            }
        }
    }

    private static void readChunk(CompoundTag root, int chunkX, int chunkZ, Map<Long, int[]> tops, Map<Long, int[]> waters,
                                  List<Start> starts) throws IOException {
        if (root.get("Status") instanceof StringTag status
                && !status.getValue().equals("minecraft:full") && !status.getValue().equals("full")) {
            return;
        }
        if (!(root.get("sections") instanceof ListTag<?> sections)) {
            return;
        }
        Map<Integer, Palette> blocks = new TreeMap<>();
        for (Tag<?> entry : sections) {
            if (entry instanceof CompoundTag section && section.get("block_states") instanceof CompoundTag states
                    && section.get("Y") instanceof NumberTag<?> sectionY) {
                blocks.put(sectionY.asInt(), Palette.read(states));
            }
        }
        int[] top = new int[256];
        int[] water = new int[256];
        List<Integer> descending = new ArrayList<>(blocks.keySet());
        descending.sort((left, right) -> right - left);
        for (int index = 0; index < 256; index++) {
            top[index] = Integer.MIN_VALUE + 1;
            water[index] = NOT_WATER;
            int x = index & 15;
            int z = index >> 4;
            boolean found = false;
            for (int sectionIndex = 0; sectionIndex < descending.size() && !found; sectionIndex++) {
                int sectionY = descending.get(sectionIndex);
                Palette palette = blocks.get(sectionY);
                for (int localY = 15; localY >= 0 && !found; localY--) {
                    Block block = palette.get(x, localY, z);
                    if (block.air()) {
                        continue;
                    }
                    found = true;
                    int y = sectionY * 16 + localY;
                    top[index] = y;
                    if (block.water()) {
                        Block below = blockAt(blocks, x, y - 1, z);
                        water[index] = encodeWater(y, block.source(), below != null && !below.air() && !below.water());
                    }
                }
            }
        }
        tops.put(chunkKey(chunkX, chunkZ), top);
        waters.put(chunkKey(chunkX, chunkZ), water);
        if (root.get("structures") instanceof CompoundTag structures && structures.get("starts") instanceof CompoundTag startTags) {
            for (Map.Entry<String, Tag<?>> entry : startTags) {
                if (entry.getValue() instanceof CompoundTag start) {
                    readStart(entry.getKey(), start, starts);
                }
            }
        }
    }

    private static Block blockAt(Map<Integer, Palette> blocks, int x, int y, int z) throws IOException {
        Palette palette = blocks.get(Math.floorDiv(y, 16));
        return palette == null ? null : palette.get(x, Math.floorMod(y, 16), z);
    }

    private static void readStart(String id, CompoundTag start, List<Start> starts) {
        boolean flatten = false;
        for (String prefix : FLATTEN_STRUCTURES) {
            flatten |= id.startsWith(prefix);
        }
        if (!flatten || !(start.get("Children") instanceof ListTag<?> children)) {
            return;
        }
        List<Bounds> pieces = new ArrayList<>();
        for (Tag<?> child : children) {
            if (child instanceof CompoundTag piece && piece.get("BB") instanceof IntArrayTag box && box.getValue().length == 6) {
                int[] value = box.getValue();
                pieces.add(new Bounds(value[0], value[2], value[3], value[5]));
            }
        }
        if (!pieces.isEmpty()) {
            starts.add(new Start(id, List.copyOf(pieces)));
        }
    }

    record Bounds(int minX, int minZ, int maxX, int maxZ) {
    }

    record Start(String id, List<Bounds> pieces) {
    }

    private record Block(boolean air, boolean water, boolean source) {
    }

    private static final class Palette {
        private final Block[] values;
        private final long[] data;
        private final int bits;
        private final int perLong;

        private Palette(Block[] values, long[] data) {
            this.values = values;
            this.data = data;
            bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(values.length - 1));
            perLong = 64 / bits;
        }

        private static Palette read(CompoundTag states) throws IOException {
            if (!(states.get("palette") instanceof ListTag<?> palette) || palette.size() == 0) {
                throw new IOException("Section has no block palette");
            }
            Block[] values = new Block[palette.size()];
            int index = 0;
            for (Tag<?> entry : palette) {
                values[index++] = block(entry);
            }
            long[] data = states.get("data") instanceof LongArrayTag longs ? longs.getValue() : new long[0];
            return new Palette(values, data);
        }

        private static Block block(Tag<?> entry) throws IOException {
            Tag<?> state = entry;
            if (state instanceof CompoundTag wrapped && wrapped.size() == 1 && wrapped.containsKey("")) {
                state = wrapped.get("");
            }
            if (state instanceof StringTag name) {
                return describe(name.getValue(), "0");
            }
            if (state instanceof CompoundTag compound) {
                String name = compound.get("Name") instanceof StringTag upper ? upper.getValue()
                        : compound.get("id") instanceof StringTag lower ? lower.getValue() : null;
                if (name == null) {
                    throw new IOException("Block palette entry has no name");
                }
                Tag<?> properties = compound.containsKey("Properties") ? compound.get("Properties") : compound.get("properties");
                String level = properties instanceof CompoundTag values && values.get("level") instanceof StringTag text
                        ? text.getValue() : "0";
                return describe(name, level);
            }
            throw new IOException("Block palette entry is not a block state");
        }

        private static Block describe(String name, String level) {
            String key = name.toLowerCase(Locale.ROOT);
            return new Block(NON_SOLID_BED.contains(key), key.equals("minecraft:water"), level.equals("0"));
        }

        private Block get(int x, int y, int z) {
            if (values.length == 1) {
                return values[0];
            }
            int index = (y << 8) | (z << 4) | x;
            int packed = (int) ((data[index / perLong] >>> ((index % perLong) * bits)) & ((1L << bits) - 1L));
            return values[packed];
        }
    }

    record Result(int chunks, long waterColumns, long skippedEdges, long insideSources, long outsideSources,
                  long insideFlowing, long outsideFlowing, int insideChunks, int flattenStarts,
                  Map<String, Long> byStructure, List<String> cells) {
        List<String> lines() {
            List<String> lines = new ArrayList<>();
            lines.add("[census] chunks=" + chunks + " waterTopped=" + waterColumns + " edgeColumnsWithAMissingNeighbour=" + skippedEdges
                    + " flattenStarts=" + flattenStarts);
            lines.add("[census] perched sources inside blends=" + insideSources + " (" + insideChunks + " chunks), outside=" + outsideSources);
            lines.add("[census] perched flowing inside blends=" + insideFlowing + ", outside=" + outsideFlowing);
            byStructure.forEach((String id, Long count) -> lines.add("[census]   " + id + " " + count));
            cells.forEach((String cell) -> lines.add("[census]   cell " + cell));
            return lines;
        }
    }
}
