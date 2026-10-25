package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.underground.CarvedTerrain;
import art.arcane.iris.generation.hydrology.underground.CaveVoxel;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletSpace;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MantleCarvedTerrain implements CarvedTerrain {
    private static final int SPAN = 48;
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private final MantleWriter writer;
    private final IrisComplex complex;
    private final Engine engine;
    private final int chunkX;
    private final int chunkZ;
    private final int originX;
    private final int originZ;
    private final int height;
    private final int caveLavaHeight;
    private final int minimumY;
    private final boolean caveFluids;
    private final SubterrainPlanner planner;
    private final List<SubterrainPlan> features;
    private final boolean terrain3D;
    private final SeaInletSpace inlets;
    private final MantleChunk<Matter>[] chunks;
    private final boolean[] existing;
    private final int[] surfaces = new int[SPAN * SPAN];
    private final Terrain3DColumn[] columns;
    private final boolean[] columnsKnown;

    @SuppressWarnings("unchecked")
    MantleCarvedTerrain(MantleWriter writer, IrisComplex complex, Engine engine, int chunkX, int chunkZ) {
        this.writer = writer;
        this.complex = complex;
        this.engine = engine;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.originX = (chunkX - 1) << 4;
        this.originZ = (chunkZ - 1) << 4;
        this.height = writer.getMantle().getWorldHeight();
        this.caveLavaHeight = engine.getDimension().getCaveLavaHeight();
        this.minimumY = engine.getWorld().minHeight();
        HydrologyRuntime runtime = complex.getHydrologyRuntime();
        this.caveFluids = runtime.caveFluidPolicies();
        this.inlets = SeaInletSpace.of(inlets(runtime, chunkX, chunkZ), originX, originZ, SPAN);
        SubterrainPlanner subterrain = complex.getSubterrainPlanner();
        List<SubterrainPlan> plans = subterrain == null || subterrain.isEmpty() ? List.of()
                : subterrain.plansForBounds(originX, originZ, originX + SPAN - 1, originZ + SPAN - 1);
        this.planner = plans.isEmpty() ? null : subterrain;
        this.features = plans;
        this.terrain3D = complex.hasTerrain3D();
        this.chunks = new MantleChunk[9];
        this.existing = new boolean[9];
        this.columns = terrain3D ? new Terrain3DColumn[SPAN * SPAN] : null;
        this.columnsKnown = terrain3D ? new boolean[SPAN * SPAN] : null;
        Arrays.fill(surfaces, UNKNOWN);
    }

    @Override
    public CaveVoxel voxel(int x, int y, int z) {
        int column = column(x, z);
        if (y <= 0 || y >= height || existing(x, z) || y > surface(x, z, column) || inlets.contains(x, y, z)) {
            return CaveVoxel.FOREIGN;
        }
        if (planner != null && planner.sample(features, x, y + minimumY, z).owned()) {
            return CaveVoxel.FOREIGN;
        }
        if (terrain3D) {
            Terrain3DColumn terrain = terrainColumn(x, z, column);
            if (terrain != null && y < terrain.topY() && !terrain.isSolid(y)) {
                return CaveVoxel.FOREIGN;
            }
        }
        MatterCavern cavern = TerrainMatterView.get(chunk(x, z), x, y, z, MatterCavern.class);
        if (cavern == null) {
            return CaveVoxel.SOLID;
        }
        return cavern.isWater() || cavern.isLava() || y <= caveLavaHeight ? CaveVoxel.FOREIGN : CaveVoxel.AIR;
    }

    @Override
    public int surface(int x, int z) {
        return surface(x, z, column(x, z));
    }

    @Override
    public String caveFluid(int x, int y, int z) {
        if (!caveFluids) {
            return null;
        }
        long revision = ProvisionalSampling.revision();
        IrisBiome biome = engine.getCaveBiome(x, y, z);
        requireFinal(revision, x, z);
        IrisHydrologyPolicy policy = biome == null ? null : biome.getHydrologyPolicy();
        return policy == null ? null : policy.getFluid();
    }

    private static Collection<SeaInlet> inlets(HydrologyRuntime runtime, int chunkX, int chunkZ) {
        Map<Long, SeaInlet> inlets = new LinkedHashMap<>();
        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (SeaInlet inlet : runtime.inlets(chunkX + offsetX, chunkZ + offsetZ)) {
                    inlets.putIfAbsent(inlet.id(), inlet);
                }
            }
        }
        return inlets.values();
    }

    private int surface(int x, int z, int column) {
        int known = surfaces[column];
        if (known == UNKNOWN) {
            long revision = ProvisionalSampling.revision();
            known = (int) Math.round(complex.getHeightStream().getDouble(x, z));
            requireFinal(revision, x, z);
            surfaces[column] = known;
        }
        return known;
    }

    private void requireFinal(long revision, int x, int z) {
        if (ProvisionalSampling.revision() != revision) {
            throw new IllegalStateException("Cave pools of chunk " + chunkX + "," + chunkZ + " read a provisional answer at " + x + "," + z
                    + "; pools are only placed from final terrain.");
        }
    }

    private Terrain3DColumn terrainColumn(int x, int z, int column) {
        if (!columnsKnown[column]) {
            columns[column] = complex.terrainColumn(x, z);
            columnsKnown[column] = true;
        }
        return columns[column];
    }

    private boolean existing(int x, int z) {
        int slot = slot(x, z);
        if (chunks[slot] == null && !existing[slot]) {
            load(slot, x >> 4, z >> 4);
        }
        return existing[slot];
    }

    private void load(int slot, int targetX, int targetZ) {
        if (!complex.allowsMantleChunkWrite(targetX, targetZ)) {
            existing[slot] = true;
            return;
        }
        MantleChunk<Matter> chunk = writer.acquireChunk(targetX, targetZ);
        if (chunk == null) {
            throw new IllegalStateException("Cave pool read at chunk " + targetX + "," + targetZ + " is outside the mantle writer of chunk "
                    + chunkX + "," + chunkZ);
        }
        if (chunk.isFlagged(MantleFlag.REAL)) {
            existing[slot] = true;
            return;
        }
        if (!chunk.isFlagged(MantleFlag.CARVED)) {
            throw new IllegalStateException("Cave pools of chunk " + chunkX + "," + chunkZ + " read chunk " + targetX + "," + targetZ
                    + " before it was carved.");
        }
        chunks[slot] = chunk;
    }

    private MantleChunk<Matter> chunk(int x, int z) {
        return chunks[slot(x, z)];
    }

    private int slot(int x, int z) {
        return ((z >> 4) - chunkZ + 1) * 3 + (x >> 4) - chunkX + 1;
    }

    private int column(int x, int z) {
        int localX = x - originX;
        int localZ = z - originZ;
        if (localX < 0 || localX >= SPAN || localZ < 0 || localZ >= SPAN) {
            throw new IllegalStateException("Cave pool read at " + x + "," + z + " leaves the neighbourhood of chunk " + chunkX + "," + chunkZ);
        }
        return localZ * SPAN + localX;
    }
}
