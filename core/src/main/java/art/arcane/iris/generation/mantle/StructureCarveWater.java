package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.volmlib.util.stream.ProceduralStream;

final class StructureCarveWater {
    static final int CONTAINMENT = 2;
    static final int FLOOR = 2;
    private static final StructureCarveWater NONE = new StructureCarveWater(0, 0, 0, 0, null, null);

    private final int minX;
    private final int minZ;
    private final int width;
    private final int depth;
    private final int[] floors;
    private final int[] tops;

    private StructureCarveWater(int minX, int minZ, int width, int depth, int[] floors, int[] tops) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.depth = depth;
        this.floors = floors;
        this.tops = tops;
    }

    static StructureCarveWater of(IrisComplex complex, int minX, int minZ, int maxX, int maxZ) {
        if (complex == null || maxX < minX || maxZ < minZ) {
            return NONE;
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int sampleWidth = width + CONTAINMENT * 2;
        int sampleDepth = depth + CONTAINMENT * 2;
        boolean[] wet = new boolean[sampleWidth * sampleDepth];
        int[] beds = new int[wet.length];
        int[] heads = new int[wet.length];
        ProceduralStream<WaterRole> roles = complex.getWaterRoleStream();
        long revision = ProvisionalSampling.revision();
        boolean any = false;
        for (int sz = 0; sz < sampleDepth; sz++) {
            int z = minZ - CONTAINMENT + sz;
            for (int sx = 0; sx < sampleWidth; sx++) {
                int x = minX - CONTAINMENT + sx;
                if (!roles.get(x, z).wet()) {
                    continue;
                }
                int index = sz * sampleWidth + sx;
                wet[index] = true;
                beds[index] = complex.getRoundedHeighteightStream().get(x, z);
                heads[index] = (int) Math.round(complex.getWaterHeadStream().getDouble(x, z));
                any = true;
            }
        }
        if (ProvisionalSampling.revision() != revision) {
            throw new ProvisionalAnswerException("Structure carve over " + minX + "," + minZ + " to " + maxX + "," + maxZ
                    + " read a provisional water answer; structures only carve around final water.");
        }
        if (!any) {
            return NONE;
        }
        int[] floors = new int[width * depth];
        int[] tops = new int[width * depth];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int floor = Integer.MAX_VALUE;
                int top = Integer.MIN_VALUE;
                for (int dz = -CONTAINMENT; dz <= CONTAINMENT; dz++) {
                    for (int dx = -CONTAINMENT; dx <= CONTAINMENT; dx++) {
                        int sample = (z + CONTAINMENT + dz) * sampleWidth + x + CONTAINMENT + dx;
                        if (!wet[sample]) {
                            continue;
                        }
                        floor = Math.min(floor, beds[sample] - FLOOR);
                        top = dx == 0 && dz == 0 ? Integer.MAX_VALUE : Math.max(top, heads[sample]);
                    }
                }
                floors[z * width + x] = floor;
                tops[z * width + x] = top;
            }
        }
        return new StructureCarveWater(minX, minZ, width, depth, floors, tops);
    }

    boolean keeps(int x, int y, int z) {
        if (floors == null) {
            return false;
        }
        int localX = x - minX;
        int localZ = z - minZ;
        if (localX < 0 || localZ < 0 || localX >= width || localZ >= depth) {
            return false;
        }
        int index = localZ * width + localX;
        return y >= floors[index] && y <= tops[index];
    }
}
