package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class HydrologyVoxels {
    static final byte UNKNOWN = -1;
    static final byte OPEN = 0;
    static final byte SOLID = 1;
    static final byte CANE = 2;
    static final byte WATER = 16;
    static final byte LAVA = 32;
    static final int NO_WATER = Integer.MIN_VALUE;
    static final int COLUMNS = 256;
    private static final int FLUID_LEVELS = 16;
    private static final int FULL_SNOW = 8;
    private static final int EXAMPLES_PER_KIND = 8;
    private static final int GRID = 18;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final Set<String> WATER_SOURCE_BLOCKS = Set.of("minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp",
            "minecraft:kelp_plant", "minecraft:bubble_column");
    private static final Set<String> OPEN_BLOCKS = Set.of("minecraft:short_grass", "minecraft:grass", "minecraft:tall_grass",
            "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush", "minecraft:bush", "minecraft:firefly_bush",
            "minecraft:short_dry_grass", "minecraft:tall_dry_grass", "minecraft:vine", "minecraft:snow", "minecraft:sweet_berry_bush",
            "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
            "minecraft:wither_rose", "minecraft:torchflower", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
            "minecraft:peony", "minecraft:pitcher_plant", "minecraft:wildflowers", "minecraft:leaf_litter", "minecraft:cactus_flower",
            "minecraft:open_eyeblossom", "minecraft:closed_eyeblossom", "minecraft:nether_sprouts", "minecraft:crimson_roots",
            "minecraft:warped_roots", "minecraft:torch", "minecraft:wall_torch", "minecraft:spore_blossom", "minecraft:pink_petals",
            "minecraft:tripwire", "minecraft:cobweb", "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots",
            "minecraft:melon_stem", "minecraft:pumpkin_stem", "minecraft:attached_melon_stem", "minecraft:attached_pumpkin_stem",
            "minecraft:torchflower_crop", "minecraft:pitcher_crop", "minecraft:nether_wart", "minecraft:fire", "minecraft:soul_fire",
            "minecraft:redstone_wire");
    private static final Map<NativeBlockState, Byte> CLASSES = new ConcurrentHashMap<>();

    private HydrologyVoxels() {
    }

    record ChunkBlocks(int chunkX, int chunkZ, int height, byte[] voxels, int[] heads, byte[] surfaces) {
        ChunkBlocks {
            if (height < 1 || voxels.length != COLUMNS * height || heads.length != COLUMNS || surfaces.length != COLUMNS) {
                throw new IllegalArgumentException("Chunk blocks need 256 x " + height + " voxels and 256 heads and surfaces, got "
                        + voxels.length + ", " + heads.length + " and " + surfaces.length);
            }
        }

        static ChunkBlocks empty(int chunkX, int chunkZ, int height) {
            int[] heads = new int[COLUMNS];
            Arrays.fill(heads, NO_WATER);
            byte[] surfaces = new byte[COLUMNS];
            Arrays.fill(surfaces, WATER);
            return new ChunkBlocks(chunkX, chunkZ, height, new byte[COLUMNS * height], heads, surfaces);
        }

        byte at(int lx, int y, int lz) {
            return y < 0 || y >= height ? UNKNOWN : voxels[(y << 8) | (lz << 4) | lx];
        }

        void set(int lx, int y, int lz, byte voxel) {
            voxels[(y << 8) | (lz << 4) | lx] = voxel;
        }

        int head(int lx, int lz) {
            return heads[(lz << 4) | lx];
        }

        byte surface(int lx, int lz) {
            return surfaces[(lz << 4) | lx];
        }
    }

    @FunctionalInterface
    interface ChunkSource {
        ChunkBlocks generate(int chunkX, int chunkZ);
    }

    static ChunkBlocks generate(Engine engine, int chunkX, int chunkZ) {
        RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(engine, chunkX, chunkZ);
        ChunkBlocks blocks = ChunkBlocks.empty(chunk.chunkX(), chunk.chunkZ(), chunk.height());
        for (int y = 0; y < chunk.height(); y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    blocks.set(lx, y, lz, classify(chunk.blocks().get(lx, y, lz)));
                }
            }
        }
        IrisComplex complex = engine.getComplex();
        System.arraycopy(effectiveHeads(complex, chunkX, chunkZ), 0, blocks.heads(), 0, COLUMNS);
        ChunkWaterRaster own = complex.getHydrologyRuntime().raster(chunkX, chunkZ);
        MantleChunk<Matter> mantle = engine.getMantle().getMantle().useChunk(chunkX, chunkZ);
        try {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int head = blocks.head(lx, lz);
                    if (head == NO_WATER) {
                        continue;
                    }
                    int x = (chunkX << 4) + lx;
                    int z = (chunkZ << 4) + lz;
                    blocks.surfaces()[(lz << 4) | lx] = covered(complex, own, mantle, lx, head, lz, x, z) ? SOLID
                            : classify(complex.resolveWaterFluid(own.fluid(lx, lz), x, z));
                }
            }
        } finally {
            mantle.release();
        }
        return blocks;
    }

    private static boolean covered(IrisComplex complex, ChunkWaterRaster raster, MantleChunk<Matter> mantle, int lx, int head, int lz, int x,
                                   int z) {
        NativeBlockState object = mantle.get(lx, head, lz, NativeBlockState.class);
        if (object != null && object.isSolid()) {
            return true;
        }
        return raster.role(lx, lz) == WaterRole.INLET
                && complex.getTrueBiomeStream().get(x, z).getSeaLayers().isNotEmpty();
    }

    static int[] effectiveHeads(IrisComplex complex, int chunkX, int chunkZ) {
        int[] interior = new int[COLUMNS];
        Arrays.fill(interior, NO_WATER);
        HydrologyRuntime runtime = complex.getHydrologyRuntime();
        if (runtime == null || !runtime.enabled()) {
            return interior;
        }
        ChunkWaterRaster[] around = new ChunkWaterRaster[9];
        for (int slot = 0; slot < around.length; slot++) {
            around[slot] = runtime.raster(chunkX + slot % 3 - 1, chunkZ + slot / 3 - 1);
        }
        int[] heads = new int[GRID * GRID];
        complex.effectiveWaterHeads(chunkX, chunkZ, around, heads);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                interior[(lz << 4) | lx] = heads[(lz + 1) * GRID + lx + 1];
            }
        }
        return interior;
    }

    static final class Volume {
        private final Map<Long, ChunkBlocks> chunks = new HashMap<>();

        void put(ChunkBlocks blocks) {
            chunks.put(chunkKey(blocks.chunkX(), blocks.chunkZ()), blocks);
        }

        byte at(int x, int y, int z) {
            ChunkBlocks chunk = chunks.get(chunkKey(x >> 4, z >> 4));
            return chunk == null ? UNKNOWN : chunk.at(x & 15, y, z & 15);
        }

        int head(int x, int z) {
            ChunkBlocks chunk = chunks.get(chunkKey(x >> 4, z >> 4));
            return chunk == null ? NO_WATER : chunk.head(x & 15, z & 15);
        }

        byte surface(int x, int z) {
            ChunkBlocks chunk = chunks.get(chunkKey(x >> 4, z >> 4));
            return chunk == null ? WATER : chunk.surface(x & 15, z & 15);
        }
    }

    static final class WaterScan {
        long waterCells;
        long containmentLeaks;
        long tickConversionCells;
        long missingSurfaces;
        long censoredCells;
        final List<String> examples = new ArrayList<>();

        void column(Volume volume, int x, int z, int fromY, int toY) {
            int head = volume.head(x, z);
            if (head != NO_WATER && !holdsSurface(volume.surface(x, z), volume.at(x, head, z))) {
                missingSurfaces++;
                example("missing planned surface at " + x + "," + head + "," + z);
            }
            for (int y = Math.max(0, fromY); y <= toY; y++) {
                byte voxel = volume.at(x, y, z);
                if (!fluid(voxel)) {
                    continue;
                }
                waterCells++;
                byte below = volume.at(x, y - 1, z);
                if (source(voxel)) {
                    if (open(below) || opensSideways(volume, x, y, z)) {
                        leak(x, y, z, open(below) ? "water over open ground" : "source beside open ground");
                    }
                    continue;
                }
                if (open(below)) {
                    leak(x, y, z, "flowing water over open ground");
                } else if (y > head && converts(volume, x, y, z, voxel, below)) {
                    tick(x, y, z, "flowing cell converts to a source");
                } else if (!sameFluid(voxel, below) && amount(voxel) > dropOff(voxel) && opensSideways(volume, x, y, z)) {
                    tick(x, y, z, "flowing cell spreads beside lower dry ground");
                }
            }
        }

        private static boolean holdsSurface(byte planned, byte voxel) {
            return planned == SOLID ? voxel == SOLID || fluid(voxel) : sameFluid(planned, voxel);
        }

        private boolean opensSideways(Volume volume, int x, int y, int z) {
            boolean open = false;
            for (int[] offset : CARDINALS) {
                byte neighbour = volume.at(x + offset[0], y, z + offset[1]);
                if (neighbour == UNKNOWN) {
                    censoredCells++;
                } else if (open(neighbour)) {
                    open = true;
                }
            }
            return open;
        }

        private static boolean converts(Volume volume, int x, int y, int z, byte voxel, byte below) {
            if (!water(voxel)) {
                return false;
            }
            boolean supported = below == SOLID || (sameFluid(voxel, below) && source(below));
            if (!supported) {
                return false;
            }
            int sources = 0;
            for (int[] offset : CARDINALS) {
                byte neighbour = volume.at(x + offset[0], y, z + offset[1]);
                if (sameFluid(voxel, neighbour) && source(neighbour)) {
                    sources++;
                }
            }
            return sources >= 2;
        }

        private void leak(int x, int y, int z, String what) {
            containmentLeaks++;
            example("leak " + what + " at " + x + "," + y + "," + z);
        }

        private void tick(int x, int y, int z, String what) {
            tickConversionCells++;
            example("tick " + what + " at " + x + "," + y + "," + z);
        }

        private void example(String value) {
            String kind = value.substring(0, Math.max(0, value.indexOf(' ')));
            int seen = 0;
            for (String example : examples) {
                if (example.startsWith(kind + " ")) {
                    seen++;
                }
            }
            if (seen < EXAMPLES_PER_KIND) {
                examples.add(value);
            }
        }
    }

    static byte classify(NativeBlockState state) {
        if (state == null || state.isAir()) {
            return OPEN;
        }
        Byte known = CLASSES.get(state);
        if (known != null) {
            return known;
        }
        byte resolved = resolve(state);
        CLASSES.putIfAbsent(state, resolved);
        return resolved;
    }

    private static byte resolve(NativeBlockState state) {
        String key = state.key();
        int bracket = key.indexOf('[');
        String block = bracket < 0 ? key : key.substring(0, bracket);
        if (block.equals("minecraft:water")) {
            return (byte) (WATER + level(key));
        }
        if (block.equals("minecraft:lava")) {
            return (byte) (LAVA + level(key));
        }
        if (state.isWaterLogged() || key.contains("waterlogged=true") || WATER_SOURCE_BLOCKS.contains(block)) {
            return WATER;
        }
        if (key.contains("waterlogged=")) {
            return SOLID;
        }
        if (block.equals("minecraft:sugar_cane")) {
            return CANE;
        }
        if (block.equals("minecraft:snow") && number(key, "layers=", 1) >= FULL_SNOW) {
            return SOLID;
        }
        if (OPEN_BLOCKS.contains(block) || block.endsWith("_sapling") || block.endsWith("_tulip") || block.endsWith("_carpet")
                || block.endsWith("_mushroom") || block.endsWith("_fungus") || block.endsWith("_button") || block.endsWith("_pressure_plate")) {
            return OPEN;
        }
        return SOLID;
    }

    private static int level(String key) {
        return Math.min(FLUID_LEVELS - 1, number(key, "level=", 0));
    }

    private static int number(String key, String property, int fallback) {
        int start = key.indexOf(property);
        if (start < 0) {
            return fallback;
        }
        int end = start + property.length();
        while (end < key.length() && Character.isDigit(key.charAt(end))) {
            end++;
        }
        return end == start + property.length() ? fallback : Integer.parseInt(key.substring(start + property.length(), end));
    }

    static boolean open(byte voxel) {
        return voxel == OPEN;
    }

    static boolean fluid(byte voxel) {
        return voxel >= WATER && voxel < LAVA + FLUID_LEVELS;
    }

    static boolean water(byte voxel) {
        return voxel >= WATER && voxel < WATER + FLUID_LEVELS;
    }

    static boolean source(byte voxel) {
        return voxel == WATER || voxel == LAVA;
    }

    static boolean sameFluid(byte voxel, byte other) {
        return fluid(other) && water(voxel) == water(other);
    }

    static int amount(byte voxel) {
        int level = (voxel - WATER) % FLUID_LEVELS;
        return level == 0 || level >= 8 ? 8 : 8 - level;
    }

    private static int dropOff(byte voxel) {
        return water(voxel) ? 1 : 2;
    }

    static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
