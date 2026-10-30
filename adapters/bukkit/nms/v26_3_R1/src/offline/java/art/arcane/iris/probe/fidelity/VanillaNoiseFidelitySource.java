package art.arcane.iris.probe.fidelity;

import art.arcane.iris.probe.WorldgenExportSampler;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class VanillaNoiseFidelitySource implements FidelitySource {
    private static final int CHUNK_WIDTH = 16;
    private static final byte SOLID = 0;
    private static final byte OPEN = 1;
    private static final byte FLUID = 2;
    private static final byte LAVA = 3;

    private final WorldgenExportSampler sampler;
    private final Map<String, String> loadKeyByBiomeId;
    private final String wallShell;
    private final DensityFunction massDensity;
    private final boolean walls;
    private final int minY;
    private final int maxY;

    public VanillaNoiseFidelitySource(WorldgenExportSampler sampler, Map<String, String> loadKeyByBiomeId, String wallShell) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.loadKeyByBiomeId = Map.copyOf(loadKeyByBiomeId);
        this.wallShell = Objects.requireNonNull(wallShell, "wallShell");
        this.massDensity = terrainMass(sampler.finalDensity());
        this.walls = sampler.densityFunctionIds().contains(wallShell);
        this.minY = sampler.minY();
        this.maxY = sampler.minY() + sampler.height();
    }

    @Override
    public String label() {
        return "vanilla:26.3";
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public FidelityChunk sample(int chunkX, int chunkZ) {
        return new TerrainChunk(sampler.terrainChunk(chunkX, chunkZ), chunkX * CHUNK_WIDTH, chunkZ * CHUNK_WIDTH);
    }

    @Override
    public void close() throws IOException {
        sampler.close();
    }

    private String loadKey(int x, int y, int z) {
        String biomeId = sampler.biome(x, y, z);
        String loadKey = loadKeyByBiomeId.get(biomeId);
        if (loadKey == null) {
            throw new IllegalStateException("Exported biome " + biomeId + " at " + x + "," + y + "," + z
                    + " has no Iris load key in the export result");
        }
        return loadKey;
    }

    static DensityFunction terrainMass(DensityFunction finalDensity) {
        DensityFunction root = finalDensity;
        while (root instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> holder)) {
            root = holder.value();
        }
        return root instanceof BinaryFunction(BinaryFunction.Type type, DensityFunction terrain, DensityFunction ignored) && type == BinaryFunction.Type.MIN
                ? terrain : root;
    }

    private static byte classify(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (FidelityBlocks.open(id)) {
            return OPEN;
        }
        if (FidelityBlocks.lava(id)) {
            return LAVA;
        }
        return FidelityBlocks.fluid(id) ? FLUID : SOLID;
    }

    private record Column(int surfaceY, String biome, byte[] kinds) {
    }

    private final class TerrainChunk implements FidelityChunk {
        private final ProtoChunk terrain;
        private final int blockX;
        private final int blockZ;
        private final Column[] columns = new Column[CHUNK_WIDTH * CHUNK_WIDTH];
        private final Map<Block, Byte> kinds = new IdentityHashMap<>();
        private float[] masses;

        private TerrainChunk(ProtoChunk terrain, int blockX, int blockZ) {
            this.terrain = terrain;
            this.blockX = blockX;
            this.blockZ = blockZ;
        }

        @Override
        public int surfaceY(int localX, int localZ) {
            return column(localX, localZ).surfaceY();
        }

        @Override
        public String surfaceBiome(int localX, int localZ) {
            return column(localX, localZ).biome();
        }

        @Override
        public boolean open(int localX, int y, int localZ) {
            return kind(localX, y, localZ) == OPEN;
        }

        @Override
        public boolean fluid(int localX, int y, int localZ) {
            byte kind = kind(localX, y, localZ);
            return kind == FLUID || kind == LAVA;
        }

        @Override
        public boolean lava(int localX, int y, int localZ) {
            return kind(localX, y, localZ) == LAVA;
        }

        @Override
        public String block(int localX, int y, int localZ) {
            return null;
        }

        @Override
        public boolean terrainMass(int localX, int y, int localZ) {
            if (y < minY || y >= maxY) {
                throw new IllegalArgumentException("Y " + y + " is outside [" + minY + ", " + maxY + ")");
            }
            if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
                throw new IllegalArgumentException("Column " + localX + "," + localZ + " is outside the chunk");
            }
            if (masses == null) {
                masses = sampler.densityChunk(massDensity, Math.floorDiv(blockX, CHUNK_WIDTH), Math.floorDiv(blockZ, CHUNK_WIDTH));
            }
            return masses[(localZ * CHUNK_WIDTH + localX) * (maxY - minY) + y - minY] > 0.0F;
        }

        @Override
        public boolean wallsKnown() {
            return walls;
        }

        @Override
        public boolean wall(int localX, int y, int localZ) {
            return walls && sampler.density(wallShell, blockX + localX, y, blockZ + localZ) > 0.0F;
        }

        private byte kind(int localX, int y, int localZ) {
            if (y < minY || y >= maxY) {
                throw new IllegalArgumentException("Y " + y + " is outside [" + minY + ", " + maxY + ")");
            }
            return column(localX, localZ).kinds()[y - minY];
        }

        private Column column(int localX, int localZ) {
            if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
                throw new IllegalArgumentException("Column " + localX + "," + localZ + " is outside the chunk");
            }
            int index = localZ * CHUNK_WIDTH + localX;
            Column column = columns[index];
            if (column == null) {
                column = sampleColumn(localX, localZ);
                columns[index] = column;
            }
            return column;
        }

        private Column sampleColumn(int localX, int localZ) {
            byte[] column = new byte[maxY - minY];
            int surfaceY = minY - 1;
            for (int y = maxY - 1; y >= minY; y--) {
                LevelChunkSection section = terrain.getSection(terrain.getSectionIndex(y));
                byte kind = kinds.computeIfAbsent(section.getBlockState(localX, y & (CHUNK_WIDTH - 1), localZ).getBlock(), VanillaNoiseFidelitySource::classify);
                column[y - minY] = kind;
                if (kind == SOLID && surfaceY < minY) {
                    surfaceY = y;
                }
            }
            return new Column(surfaceY, loadKey(blockX + localX, Math.max(minY, surfaceY), blockZ + localZ), column);
        }
    }
}
