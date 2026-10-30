package art.arcane.iris.probe.fidelity;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.probe.RealPackProbeSupport;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class IrisEngineFidelitySource implements FidelitySource {
    private static final int CHUNK_WIDTH = 16;

    private final Engine engine;
    private final int minY;
    private final int maxY;

    public IrisEngineFidelitySource(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        IrisWorld world = engine.getTarget().getWorld();
        this.minY = world.minHeight();
        this.maxY = world.maxHeight();
    }

    @Override
    public String label() {
        return "iris:" + engine.getDimension().getLoadKey();
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
        return new EngineChunk(RealPackProbeSupport.generateChunk(engine, chunkX, chunkZ));
    }

    @Override
    public void close() {
    }

    private final class EngineChunk implements FidelityChunk {
        private final RealPackProbeSupport.GeneratedChunk chunk;
        private final Map<NativeBlockState, String> ids = new IdentityHashMap<>();
        private final int[] surfaces = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        private final TerrainMass[] masses = new TerrainMass[CHUNK_WIDTH * CHUNK_WIDTH];

        private EngineChunk(RealPackProbeSupport.GeneratedChunk chunk) {
            this.chunk = chunk;
            Arrays.fill(surfaces, Integer.MIN_VALUE);
        }

        @Override
        public int surfaceY(int localX, int localZ) {
            int index = localZ * CHUNK_WIDTH + localX;
            if (surfaces[index] == Integer.MIN_VALUE) {
                surfaces[index] = scanSurface(localX, localZ);
            }
            return surfaces[index];
        }

        @Override
        public String surfaceBiome(int localX, int localZ) {
            IrisBiome biome = engine.getComplex().getTrueBiomeStream().get((chunk.chunkX() << 4) + localX, (chunk.chunkZ() << 4) + localZ);
            return biome == null ? null : biome.getLoadKey();
        }

        @Override
        public boolean open(int localX, int y, int localZ) {
            return FidelityBlocks.open(block(localX, y, localZ));
        }

        @Override
        public boolean fluid(int localX, int y, int localZ) {
            return FidelityBlocks.fluid(block(localX, y, localZ));
        }

        @Override
        public String block(int localX, int y, int localZ) {
            NativeBlockState state = chunk.blocks().get(localX, y - minY, localZ);
            if (state == null) {
                return FidelityBlocks.AIR;
            }
            return ids.computeIfAbsent(state, (NativeBlockState key) -> FidelityBlocks.id(key.key()));
        }

        @Override
        public boolean terrainMass(int localX, int y, int localZ) {
            int index = localZ * CHUNK_WIDTH + localX;
            TerrainMass mass = masses[index];
            if (mass == null) {
                mass = TerrainMass.of(engine.getComplex(), (chunk.chunkX() << 4) + localX, (chunk.chunkZ() << 4) + localZ);
                masses[index] = mass;
            }
            return mass.solid(y - minY);
        }

        @Override
        public boolean wallsKnown() {
            return true;
        }

        @Override
        public boolean wall(int localX, int y, int localZ) {
            return CaveWalls.besideCave(this, localX, y, localZ);
        }

        private int scanSurface(int localX, int localZ) {
            for (int y = maxY - 1; y >= minY; y--) {
                String id = block(localX, y, localZ);
                if (!FidelityBlocks.open(id) && !FidelityBlocks.fluid(id)) {
                    return y;
                }
            }
            return minY - 1;
        }
    }

    private record TerrainMass(int top, Terrain3DColumn column) {
        private static TerrainMass of(IrisComplex complex, int x, int z) {
            return new TerrainMass(complex.getRoundedHeighteightStream().get(x, z), complex.terrainColumn(x, z));
        }

        private boolean solid(int localY) {
            return localY <= top && (column == null || column.isSolid(localY));
        }
    }
}
