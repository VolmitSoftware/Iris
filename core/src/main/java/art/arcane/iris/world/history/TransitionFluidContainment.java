package art.arcane.iris.world.history;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public final class TransitionFluidContainment {
    private static final Set<String> BANK_MATERIALS = Set.of(
            "minecraft:stone", "minecraft:deepslate", "minecraft:granite", "minecraft:diorite",
            "minecraft:andesite", "minecraft:tuff", "minecraft:calcite", "minecraft:bedrock",
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:grass_block", "minecraft:podzol",
            "minecraft:mycelium", "minecraft:clay", "minecraft:terracotta", "minecraft:netherrack",
            "minecraft:basalt", "minecraft:smooth_basalt", "minecraft:blackstone", "minecraft:end_stone",
            "minecraft:obsidian", "minecraft:crying_obsidian");
    private static final BoundaryColumnGeometry.Voxel STONE = new BoundaryColumnGeometry.Voxel(
            "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);

    private TransitionFluidContainment() {
    }

    public static SavedTerrainChunk contain(SavedTerrainChunk raw, TransitionGenerationPlan plan, ColumnSource source)
            throws IOException {
        List<BoundaryColumnGeometry> columns = new ArrayList<>(256);
        boolean changed = false;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = Math.addExact(Math.multiplyExact(raw.chunkX(), 16), localX);
                int z = Math.addExact(Math.multiplyExact(raw.chunkZ(), 16), localZ);
                BoundaryColumnGeometry original = raw.column(x, z).geometry();
                BoundaryColumnGeometry corrected = plan.isHistoricalBlock(x, z) || plan.newEpochWeightAt(x, z) == 1D
                        ? original : containColumn(x, z, original, source);
                columns.add(corrected);
                changed |= corrected != original;
            }
        }
        if (!changed) {
            return raw;
        }
        BoundaryColumnGeometry first = columns.getFirst();
        return SavedTerrainChunk.capture(raw.chunkX(), raw.chunkZ(), first.minimumY(), first.height(),
                raw.nativeStatus(), new ContainedSource(raw, columns));
    }

    private static BoundaryColumnGeometry containColumn(int x, int z, BoundaryColumnGeometry original,
                                                        ColumnSource source) throws IOException {
        List<BoundaryColumnGeometry.Voxel> voxels = original.voxels();
        List<BoundaryColumnGeometry.Voxel> corrected = null;
        BoundaryColumnGeometry.Voxel[] banks = null;
        BoundaryColumnGeometry[] neighbors = null;
        for (int offset = 0; offset < voxels.size(); offset++) {
            BoundaryColumnGeometry.Voxel voxel = voxels.get(offset);
            if (voxel.phase() != BoundaryColumnGeometry.Phase.FLUID || voxel.protectedContent()) {
                continue;
            }
            if (neighbors == null) {
                neighbors = new BoundaryColumnGeometry[]{
                        source.column(Math.subtractExact(x, 1), z), source.column(Math.addExact(x, 1), z),
                        source.column(x, Math.subtractExact(z, 1)), source.column(x, Math.addExact(z, 1))};
            }
            int worldY = Math.addExact(original.minimumY(), offset);
            if (!exposed(original, neighbors, worldY)) {
                continue;
            }
            if (corrected == null) {
                corrected = new ArrayList<>(voxels);
                banks = nearestDrySolids(voxels);
            }
            corrected.set(offset, banks[offset]);
        }
        return corrected == null ? original : BoundaryColumnGeometry.fromVoxels(original.minimumY(), corrected);
    }

    private static boolean exposed(BoundaryColumnGeometry original, BoundaryColumnGeometry[] neighbors, int worldY) {
        if (original.voxelAt(Math.subtractExact(worldY, 1)).phase() == BoundaryColumnGeometry.Phase.AIR) {
            return true;
        }
        for (BoundaryColumnGeometry neighbor : neighbors) {
            if (neighbor.voxelAt(worldY).phase() == BoundaryColumnGeometry.Phase.AIR) {
                return true;
            }
        }
        return false;
    }

    private static BoundaryColumnGeometry.Voxel[] nearestDrySolids(List<BoundaryColumnGeometry.Voxel> voxels) {
        BoundaryColumnGeometry.Voxel[] materials = new BoundaryColumnGeometry.Voxel[voxels.size()];
        int[] distances = new int[voxels.size()];
        BoundaryColumnGeometry.Voxel nearest = STONE;
        int last = -voxels.size() - 1;
        for (int offset = 0; offset < voxels.size(); offset++) {
            if (drySolid(voxels.get(offset))) {
                nearest = voxels.get(offset);
                last = offset;
            }
            materials[offset] = nearest;
            distances[offset] = offset - last;
        }
        last = voxels.size() * 2;
        nearest = null;
        for (int offset = voxels.size() - 1; offset >= 0; offset--) {
            if (drySolid(voxels.get(offset))) {
                nearest = voxels.get(offset);
                last = offset;
            }
            if (nearest != null && last - offset < distances[offset]) {
                materials[offset] = nearest;
            }
        }
        return materials;
    }

    private static boolean drySolid(BoundaryColumnGeometry.Voxel voxel) {
        String state = voxel.stateKey();
        int properties = state.indexOf('[');
        String key = properties < 0 ? state : state.substring(0, properties);
        return voxel.phase() == BoundaryColumnGeometry.Phase.SOLID
                && voxel.fluidStateKey().isEmpty() && !voxel.protectedContent() && BANK_MATERIALS.contains(key);
    }

    @FunctionalInterface
    public interface ColumnSource {
        BoundaryColumnGeometry column(int blockX, int blockZ) throws IOException;
    }

    private record ContainedSource(SavedTerrainChunk raw, List<BoundaryColumnGeometry> columns)
            implements SavedTerrainChunk.VoxelSource {
        @Override
        public OptionalInt groundSurface(int localX, int localZ) {
            return OptionalInt.of(signature(localX, localZ).oceanFloorHeight());
        }

        @Override
        public BoundaryColumnGeometry.Voxel voxel(int localX, int worldY, int localZ) {
            return columns.get(localX * 16 + localZ).voxelAt(worldY);
        }

        @Override
        public String biome(int localX, int worldY, int localZ) {
            TerrainBoundarySignature signature = signature(localX, localZ);
            return signature.biomeAtSample((worldY - signature.samples().layout().minimumY()) / 4);
        }

        private TerrainBoundarySignature signature(int x, int z) {
            return raw.column((raw.chunkX() << 4) + x, (raw.chunkZ() << 4) + z);
        }
    }
}
