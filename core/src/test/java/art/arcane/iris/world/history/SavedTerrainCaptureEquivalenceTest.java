package art.arcane.iris.world.history;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SavedTerrainCaptureEquivalenceTest {
    private static final int MINIMUM_Y = -64;
    private static final int HEIGHT = 384;
    private static final Comparator<BoundaryColumnGeometry.Voxel> VOXEL_ORDER = Comparator
            .comparing(BoundaryColumnGeometry.Voxel::stateKey)
            .thenComparing(BoundaryColumnGeometry.Voxel::phase)
            .thenComparing(BoundaryColumnGeometry.Voxel::fluidStateKey)
            .thenComparing(BoundaryColumnGeometry.Voxel::protectedContent);
    private static final List<BoundaryColumnGeometry.Voxel> STATES = List.of(
            voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false),
            voxel("minecraft:deepslate", BoundaryColumnGeometry.Phase.SOLID, "", false),
            voxel("minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false),
            voxel("minecraft:cave_air", BoundaryColumnGeometry.Phase.AIR, "", false),
            voxel("minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water[level=0]", false),
            voxel("minecraft:lava[level=0]", BoundaryColumnGeometry.Phase.FLUID, "minecraft:lava[level=0]", false),
            voxel("minecraft:oak_log[axis=y]", BoundaryColumnGeometry.Phase.SOLID, "", true),
            voxel("minecraft:kelp", BoundaryColumnGeometry.Phase.SOLID, "minecraft:water[level=0]", true),
            voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", true));
    private static final List<String> BIOMES = List.of("minecraft:plains", "minecraft:river",
            "minecraft:lush_caves", "minecraft:deep_dark", "minecraft:dripstone_caves");

    @Test
    public void boundaryAndFullCapturesMatchTheReferenceEncoding() throws Exception {
        Random random = new Random(20260928L);
        for (int sample = 0; sample < 48; sample++) {
            RandomSource source = new RandomSource(random, sample);
            for (boolean boundaryOnly : new boolean[]{true, false}) {
                SavedTerrainChunk expected = referenceCapture(-3, 7, source, boundaryOnly);
                SavedTerrainChunk actual = boundaryOnly
                        ? SavedTerrainChunk.captureBoundary(-3, 7, MINIMUM_Y, HEIGHT, "minecraft:noise", source)
                        : SavedTerrainChunk.capture(-3, 7, MINIMUM_Y, HEIGHT, "minecraft:noise", source);
                assertSameTerrain(expected, actual);
                assertArrayEquals(NativeTerrainReceipt.encode(expected, 1L, "epoch"),
                        NativeTerrainReceipt.encode(actual, 1L, "epoch"));
            }
        }
    }

    @Test
    public void geometryMatchesTheReferenceOnWidePalettes() {
        Random random = new Random(7L);
        for (int sample = 0; sample < 32; sample++) {
            ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(HEIGHT);
            int distinct = 1 + random.nextInt(sample < 16 ? 12 : 200);
            while (voxels.size() < HEIGHT) {
                int state = random.nextInt(distinct);
                BoundaryColumnGeometry.Voxel voxel = voxel("minecraft:block_" + state,
                        BoundaryColumnGeometry.Phase.values()[state % 2], "", state % 3 == 0);
                int end = Math.min(HEIGHT, voxels.size() + 1 + random.nextInt(4));
                while (voxels.size() < end) {
                    voxels.add(voxel);
                }
            }
            assertEquals(referenceGeometry(MINIMUM_Y, voxels), BoundaryColumnGeometry.fromVoxels(MINIMUM_Y, voxels));
        }
    }

    private static void assertSameTerrain(SavedTerrainChunk expected, SavedTerrainChunk actual) {
        assertEquals(expected.chunkX(), actual.chunkX());
        assertEquals(expected.chunkZ(), actual.chunkZ());
        assertEquals(expected.nativeStatus(), actual.nativeStatus());
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(expected.hasColumn(x, z), actual.hasColumn(x, z));
                if (!expected.hasColumn(x, z)) {
                    continue;
                }
                TerrainBoundarySignature left = expected.column(-48 + x, 112 + z);
                TerrainBoundarySignature right = actual.column(-48 + x, 112 + z);
                assertEquals(left.column(), right.column());
                assertEquals(left.samples(), right.samples());
                assertEquals(left.geometry(), right.geometry());
            }
        }
    }

    private static SavedTerrainChunk referenceCapture(int chunkX, int chunkZ, SavedTerrainChunk.VoxelSource source,
                                                      boolean boundaryOnly) throws IOException {
        List<TerrainBoundarySignature> columns = new ArrayList<>(256);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (boundaryOnly && x != 0 && x != 15 && z != 0 && z != 15) {
                    columns.add(null);
                    continue;
                }
                List<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(HEIGHT);
                Map<String, Short> biomePalette = new LinkedHashMap<>();
                short[] biomeIndices = new short[HEIGHT / 4];
                int surface = 0;
                int floor = 0;
                int fluid = -1;
                for (int offset = 0; offset < HEIGHT; offset++) {
                    int y = MINIMUM_Y + offset;
                    BoundaryColumnGeometry.Voxel voxel = Objects.requireNonNull(source.voxel(x, y, z), "voxel");
                    voxels.add(voxel);
                    if (voxel.phase() != BoundaryColumnGeometry.Phase.AIR) {
                        surface = offset;
                    }
                    if (voxel.phase() == BoundaryColumnGeometry.Phase.SOLID) {
                        floor = offset;
                    }
                    if (!voxel.fluidStateKey().isEmpty()) {
                        fluid = offset;
                    }
                    if (offset % 4 == 0) {
                        String biome = Objects.requireNonNull(source.biome(x, y, z), "physical biome");
                        Short index = biomePalette.get(biome);
                        if (index == null) {
                            index = (short) biomePalette.size();
                            biomePalette.put(biome, index);
                        }
                        biomeIndices[offset / 4] = index;
                    }
                }
                BoundaryColumnGeometry geometry = referenceGeometry(MINIMUM_Y, voxels);
                OptionalInt groundSurface = source.groundSurface(x, z);
                if (groundSurface.isPresent()) {
                    floor = geometry.surfaceOffsetNear(groundSurface.getAsInt());
                    surface = Math.max(floor, fluid);
                }
                columns.add(new TerrainBoundarySignature(
                        new TerrainBoundarySignature.Column(chunkX * 16 + x, chunkZ * 16 + z, surface, floor,
                                fluid > floor ? OptionalInt.of(fluid) : OptionalInt.empty(), OptionalInt.empty()),
                        new TerrainBoundarySignature.Samples(
                                new TerrainBoundarySignature.VerticalLayout(MINIMUM_Y, 4, biomeIndices.length),
                                new TerrainBoundarySignature.BiomeEncoding(List.copyOf(biomePalette.keySet()), biomeIndices)),
                        geometry));
            }
        }
        return new SavedTerrainChunk(chunkX, chunkZ, "minecraft:noise", columns);
    }

    private static BoundaryColumnGeometry referenceGeometry(int minimumY, List<BoundaryColumnGeometry.Voxel> voxels) {
        TreeSet<BoundaryColumnGeometry.Voxel> used = new TreeSet<>(VOXEL_ORDER);
        BoundaryColumnGeometry.Voxel previous = null;
        for (BoundaryColumnGeometry.Voxel voxel : voxels) {
            if (previous == null || !previous.equals(voxel)) {
                used.add(voxel);
                previous = voxel;
            }
        }
        List<BoundaryColumnGeometry.Voxel> palette = List.copyOf(used);
        Map<BoundaryColumnGeometry.Voxel, Short> indices = new HashMap<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            indices.put(palette.get(index), (short) index);
        }
        int[] ends = new int[voxels.size()];
        short[] values = new short[voxels.size()];
        int runCount = 0;
        previous = null;
        for (int offset = 0; offset < voxels.size(); offset++) {
            BoundaryColumnGeometry.Voxel voxel = voxels.get(offset);
            if (previous == null || !previous.equals(voxel)) {
                values[runCount++] = indices.get(voxel);
                previous = voxel;
            }
            ends[runCount - 1] = offset + 1;
        }
        return new BoundaryColumnGeometry(minimumY, palette, Arrays.copyOf(ends, runCount), Arrays.copyOf(values, runCount));
    }

    private static BoundaryColumnGeometry.Voxel voxel(String state, BoundaryColumnGeometry.Phase phase, String fluid,
                                                      boolean protectedContent) {
        return new BoundaryColumnGeometry.Voxel(state, phase, fluid, protectedContent);
    }

    private static final class RandomSource implements SavedTerrainChunk.VoxelSource {
        private final BoundaryColumnGeometry.Voxel[][] voxels = new BoundaryColumnGeometry.Voxel[256][HEIGHT];
        private final String[][] biomes = new String[256][HEIGHT];
        private final int[] ground = new int[256];

        private RandomSource(Random random, int sample) {
            for (int column = 0; column < 256; column++) {
                int offset = 0;
                while (offset < HEIGHT) {
                    BoundaryColumnGeometry.Voxel state = STATES.get(random.nextInt(STATES.size()));
                    BoundaryColumnGeometry.Voxel copy = random.nextBoolean() ? state
                            : new BoundaryColumnGeometry.Voxel(new String(state.stateKey()), state.phase(),
                            new String(state.fluidStateKey()), state.protectedContent());
                    int end = Math.min(HEIGHT, offset + 1 + random.nextInt(sample % 3 == 0 ? 3 : 64));
                    while (offset < end) {
                        voxels[column][offset++] = copy;
                    }
                }
                offset = 0;
                while (offset < HEIGHT) {
                    String biome = BIOMES.get(random.nextInt(BIOMES.size()));
                    String copy = random.nextBoolean() ? biome : new String(biome);
                    int end = Math.min(HEIGHT, offset + 4 + random.nextInt(96));
                    while (offset < end) {
                        biomes[column][offset++] = copy;
                    }
                }
                ground[column] = sample % 4 == 0 ? -1 : random.nextInt(HEIGHT);
            }
        }

        @Override
        public OptionalInt groundSurface(int localX, int localZ) {
            int value = ground[localX * 16 + localZ];
            return value < 0 ? OptionalInt.empty() : OptionalInt.of(value);
        }

        @Override
        public BoundaryColumnGeometry.Voxel voxel(int localX, int worldY, int localZ) {
            return voxels[localX * 16 + localZ][worldY - MINIMUM_Y];
        }

        @Override
        public String biome(int localX, int worldY, int localZ) {
            return biomes[localX * 16 + localZ][worldY - MINIMUM_Y];
        }
    }
}
