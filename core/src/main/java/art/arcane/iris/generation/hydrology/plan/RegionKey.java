package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.water.LakeCandidate;

import java.util.ArrayList;
import java.util.List;

public record RegionKey(int rx, int rz) {
    public static final int SIZE = 4096;
    public static final int LAKE_REACH = LakeCandidate.MAX_EXTENT + 2 * LakeCandidate.BOUND_CELLS * RegionPlanner.CELL;

    public static RegionKey containing(int blockX, int blockZ) {
        return new RegionKey(Math.floorDiv(blockX, SIZE), Math.floorDiv(blockZ, SIZE));
    }

    public static List<RegionKey> covering(int minX, int minZ, int maxX, int maxZ) {
        List<RegionKey> keys = new ArrayList<>();
        for (int rz = Math.floorDiv(minZ, SIZE); rz <= Math.floorDiv(maxZ, SIZE); rz++) {
            for (int rx = Math.floorDiv(minX, SIZE); rx <= Math.floorDiv(maxX, SIZE); rx++) {
                keys.add(new RegionKey(rx, rz));
            }
        }
        return List.copyOf(keys);
    }

    public static List<RegionKey> lakeOwners(int minX, int minZ, int maxX, int maxZ) {
        return covering(minX - LAKE_REACH, minZ - LAKE_REACH, maxX + LAKE_REACH, maxZ + LAKE_REACH);
    }
}
