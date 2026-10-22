package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public final class SavedTerrainChunk {
    private final int chunkX;
    private final int chunkZ;
    private final String nativeStatus;
    private final List<TerrainBoundarySignature> columns;

    SavedTerrainChunk(int chunkX, int chunkZ, String nativeStatus,
                              List<TerrainBoundarySignature> columns) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.nativeStatus = Objects.requireNonNull(nativeStatus, "native status");
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
    }

    public static SavedTerrainChunk read(Path dimensionRoot, int chunkX, int chunkZ,
                                         int minimumY, int height) throws IOException {
        return SavedTerrainChunkReader.read(dimensionRoot, chunkX, chunkZ, minimumY, height);
    }

    public static SavedTerrainChunk readNbt(byte[] bytes, int chunkX, int chunkZ,
                                            int minimumY, int height) throws IOException {
        return SavedTerrainChunkReader.readNbt(bytes, chunkX, chunkZ, minimumY, height);
    }

    public static void verifyCheckpoint(Path dimensionRoot, int chunkX, int chunkZ, String status, byte[] receipt, long structureActivation)
            throws IOException {
        SavedTerrainChunkReader.verifyCheckpoint(dimensionRoot, chunkX, chunkZ, status, receipt, structureActivation);
    }

    public static byte[] readReceipt(Path dimensionRoot, int chunkX, int chunkZ) throws IOException {
        return SavedTerrainChunkReader.readReceipt(dimensionRoot, chunkX, chunkZ);
    }

    public static String readStatus(Path dimensionRoot, int chunkX, int chunkZ) throws IOException {
        return SavedTerrainChunkReader.readStatus(dimensionRoot, chunkX, chunkZ);
    }

    public static SavedTerrainChunk capture(int chunkX, int chunkZ, int minimumY, int height,
                                            String nativeStatus, VoxelSource source) throws IOException {
        return capture(chunkX, chunkZ, minimumY, height, nativeStatus, source, false);
    }

    public static SavedTerrainChunk captureBoundary(int chunkX, int chunkZ, int minimumY, int height,
                                                    String nativeStatus, VoxelSource source) throws IOException {
        return capture(chunkX, chunkZ, minimumY, height, nativeStatus, source, true);
    }

    private static SavedTerrainChunk capture(int chunkX, int chunkZ, int minimumY, int height,
                                             String nativeStatus, VoxelSource source, boolean boundaryOnly) throws IOException {
        Objects.requireNonNull(source, "voxel source");
        if (height <= 0 || height > 4096 || minimumY % 16 != 0 || height % 16 != 0) {
            throw new IOException("Invalid saved terrain height: " + minimumY + "/" + height);
        }
        Math.addExact(minimumY, height);
        List<TerrainBoundarySignature> columns = new ArrayList<>(256);
        ColumnScan scan = new ColumnScan(height);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (boundaryOnly && x != 0 && x != 15 && z != 0 && z != 15) {
                    columns.add(null);
                    continue;
                }
                scan.reset();
                source.scanColumn(x, z, minimumY, height, scan);
                scan.requireCovered();
                List<String> biomePalette = new ArrayList<>(4);
                short[] biomeIndices = new short[height / 4];
                String lastBiome = null;
                short lastIndex = 0;
                for (int offset = 0; offset < height; offset += 4) {
                    String biome = Objects.requireNonNull(source.biome(x, minimumY + offset, z), "physical biome");
                    if (biome != lastBiome) {
                        lastIndex = paletteIndex(biomePalette, biome);
                        lastBiome = biome;
                    }
                    biomeIndices[offset / 4] = lastIndex;
                }
                int surface = scan.surface;
                int floor = scan.floor;
                int fluid = scan.fluid;
                BoundaryColumnGeometry geometry = scan.runs.build(minimumY);
                OptionalInt groundSurface = source.groundSurface(x, z);
                if (groundSurface.isPresent()) {
                    floor = geometry.surfaceOffsetNear(groundSurface.getAsInt());
                    if (floor < 0 || floor >= height) {
                        throw new IOException("Natural terrain ground surface is outside the chunk height");
                    }
                    surface = Math.max(floor, fluid);
                }
                columns.add(new TerrainBoundarySignature(
                        new TerrainBoundarySignature.Column(Math.addExact(Math.multiplyExact(chunkX, 16), x),
                                Math.addExact(Math.multiplyExact(chunkZ, 16), z), surface, floor,
                                fluid > floor ? OptionalInt.of(fluid) : OptionalInt.empty(), OptionalInt.empty()),
                        new TerrainBoundarySignature.Samples(
                                new TerrainBoundarySignature.VerticalLayout(minimumY, 4, biomeIndices.length),
                                new TerrainBoundarySignature.BiomeEncoding(biomePalette, biomeIndices)),
                        geometry));
            }
        }
        return new SavedTerrainChunk(chunkX, chunkZ, nativeStatus, columns);
    }

    private static short paletteIndex(List<String> palette, String biome) {
        for (int index = 0; index < palette.size(); index++) {
            String candidate = palette.get(index);
            if (candidate == biome || candidate.equals(biome)) {
                return (short) index;
            }
        }
        palette.add(biome);
        return (short) (palette.size() - 1);
    }

    public SavedTerrainChunk boundaryOnly() {
        if (!hasColumn(1, 1)) {
            return this;
        }
        List<TerrainBoundarySignature> boundary = new ArrayList<>(columns);
        for (int x = 1; x < 15; x++) {
            for (int z = 1; z < 15; z++) {
                boundary.set(x * 16 + z, null);
            }
        }
        return new SavedTerrainChunk(chunkX, chunkZ, nativeStatus, boundary);
    }

    public TerrainBoundarySignature column(int blockX, int blockZ) {
        if (Math.floorDiv(blockX, 16) != chunkX || Math.floorDiv(blockZ, 16) != chunkZ) {
            throw new IllegalArgumentException("Boundary column is outside the captured chunk");
        }
        return Objects.requireNonNull(columns.get(Math.floorMod(blockX, 16) * 16 + Math.floorMod(blockZ, 16)),
                "Interior columns are not retained in a boundary receipt");
    }

    public boolean hasColumn(int localX, int localZ) {
        return columns.get(localX * 16 + localZ) != null;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public String nativeStatus() {
        return nativeStatus;
    }

    public static int statusRank(String status) {
        if (status == null) {
            return -1;
        }
        String key = status.startsWith("minecraft:") ? status.substring(10) : status;
        return switch (key) {
            case "empty" -> 0;
            case "structure_starts" -> 1;
            case "structure_references" -> 2;
            case "biomes" -> 3;
            case "noise" -> 4;
            case "surface" -> 5;
            case "carvers", "terrain" -> 6;
            case "features" -> 7;
            case "initialize_light" -> 8;
            case "light" -> 9;
            case "spawn" -> 10;
            case "full" -> 11;
            default -> -1;
        };
    }

    public static boolean hasTerrain(String status) {
        return statusRank(status) >= 4;
    }

    public static boolean isComplete(String status) {
        return "minecraft:full".equals(status) || "full".equals(status);
    }

    public static void requireComplete(String status, int chunkX, int chunkZ) throws IOException {
        if (!isComplete(status)) {
            throw new IOException("Saved chunk " + chunkX + "," + chunkZ + " has unfinished native generation status "
                    + status + "; finish its generation before changing the generator.");
        }
    }

    public interface VoxelSource {
        default OptionalInt groundSurface(int localX, int localZ) {
            return OptionalInt.empty();
        }

        BoundaryColumnGeometry.Voxel voxel(int localX, int worldY, int localZ) throws IOException;

        String biome(int localX, int worldY, int localZ) throws IOException;

        default void scanColumn(int localX, int localZ, int minimumY, int height, VoxelRuns runs) throws IOException {
            for (int offset = 0; offset < height; offset++) {
                runs.add(offset, offset + 1, voxel(localX, minimumY + offset, localZ));
            }
        }
    }

    @FunctionalInterface
    public interface VoxelRuns {
        void add(int startOffset, int endOffset, BoundaryColumnGeometry.Voxel voxel);
    }

    private static final class ColumnScan implements VoxelRuns {
        private final BoundaryColumnGeometry.Runs runs;
        private final int height;
        private int covered;
        private int surface;
        private int floor;
        private int fluid;

        private ColumnScan(int height) {
            this.runs = new BoundaryColumnGeometry.Runs(height);
            this.height = height;
        }

        private void reset() {
            runs.clear();
            covered = 0;
            surface = 0;
            floor = 0;
            fluid = -1;
        }

        @Override
        public void add(int startOffset, int endOffset, BoundaryColumnGeometry.Voxel voxel) {
            Objects.requireNonNull(voxel, "voxel");
            if (startOffset != covered || endOffset <= startOffset || endOffset > height) {
                throw new IllegalArgumentException("Voxel runs must cover the column in order");
            }
            runs.add(voxel, endOffset - startOffset);
            covered = endOffset;
            int last = endOffset - 1;
            if (voxel.phase() != BoundaryColumnGeometry.Phase.AIR) {
                surface = last;
            }
            if (voxel.phase() == BoundaryColumnGeometry.Phase.SOLID) {
                floor = last;
            }
            if (!voxel.fluidStateKey().isEmpty()) {
                fluid = last;
            }
        }

        private void requireCovered() {
            if (covered != height) {
                throw new IllegalStateException("Voxel runs must cover the whole column");
            }
        }
    }
}
