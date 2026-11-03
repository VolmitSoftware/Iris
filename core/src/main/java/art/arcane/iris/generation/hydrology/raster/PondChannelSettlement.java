package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class PondChannelSettlement {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private PondChannelSettlement() {
    }

    public static ShapedNetwork settle(WindowKey window, ShapedNetwork network, long seed, int seaLevel) {
        int[] initial = new int[network.ponds().size()];
        for (int index = 0; index < initial.length; index++) {
            initial[index] = network.ponds().get(index).level();
        }
        int[] levels = levels(window, network.reaches(), network.ponds(), initial, seed, seaLevel);
        List<Pond> water = new ArrayList<>(initial.length);
        for (int index = 0; index < initial.length; index++) {
            Pond pond = network.ponds().get(index);
            water.add(levels[index] == initial[index] ? pond : new Pond(pond.id(), pond.kind(), levels[index], pond.floor(), pond.spillX(),
                    pond.spillZ(), pond.spillHeight(), pond.mask(), pond.outletReachId(), pond.damRaise(), pond.fluid(), pond.floorX(), pond.floorZ()));
        }
        return new ShapedNetwork(network.reaches(), water);
    }

    static int[] levels(WindowKey window, List<Reach> reaches, List<Pond> ponds, int[] initial, long seed, int seaLevel) {
        int[] levels = initial.clone();
        if (reaches.isEmpty() || ponds.isEmpty()) {
            return levels;
        }
        boolean eligible = false;
        for (int pond = 0; pond < ponds.size() && !eligible; pond++) {
            eligible = ponds.get(pond).kind() != PondKind.LAKE && levels[pond] > seaLevel;
        }
        if (!eligible) {
            return levels;
        }
        ReachIndex index = ReachIndex.build(window, reaches, List.of());
        ReachGeometry.Lookup brinks = new ReachGeometry.Lookup(seed, seaLevel);
        List<Reach> nearby = new ArrayList<>();
        List<Pond> unused = new ArrayList<>();
        for (int pond = 0; pond < ponds.size(); pond++) {
            Pond water = ponds.get(pond);
            if (water.kind() == PondKind.LAKE || levels[pond] <= seaLevel || water.mask().width() == 0 || water.mask().depth() == 0) {
                continue;
            }
            Bounds bounds = Bounds.of(water.mask());
            int chunkX = (bounds.minX() + (bounds.maxX() - bounds.minX()) / 2) >> 4;
            int chunkZ = (bounds.minZ() + (bounds.maxZ() - bounds.minZ()) / 2) >> 4;
            int halo = Math.max(0, Math.max(Math.max((chunkX << 4) - bounds.minX(), bounds.maxX() - (chunkX << 4) - 15),
                    Math.max((chunkZ << 4) - bounds.minZ(), bounds.maxZ() - (chunkZ << 4) - 15))) + 1;
            nearby.clear();
            index.candidates(chunkX, chunkZ, halo, nearby, unused);
            if (!nearby.isEmpty()) {
                levels[pond] = lower(nearby, water.mask(), bounds, levels[pond], brinks, seaLevel);
            }
        }
        return levels;
    }

    private static int lower(List<Reach> reaches, PondMask mask, Bounds bounds, int level, ReachGeometry.Lookup brinks, int seaLevel) {
        ReachSegments segments = new ReachSegments(64);
        for (Reach reach : reaches) {
            segments.addNear(reach, bounds.minX() - 1, bounds.minZ() - 1, bounds.maxX() + 1, bounds.maxZ() + 1, 0f);
        }
        ReachCursor cursor = new ReachCursor(brinks);
        int width = bounds.maxX() - bounds.minX() + 1;
        BitSet visited = new BitSet(width * (bounds.maxZ() - bounds.minZ() + 1));
        for (Reach reach : reaches) {
            for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
                if (WaterHeads.snap(brinks.lowestGrade(reach, segment), seaLevel, reach.tier().stepHeight()) >= WaterHeads.standing(level, seaLevel)) {
                    continue;
                }
                float radius = Math.max(reach.halfWidth()[segment], reach.halfWidth()[segment + 1]) + 1f;
                int minX = Math.max(bounds.minX(), (int) Math.ceil(Math.min(reach.xs()[segment], reach.xs()[segment + 1]) - radius));
                int minZ = Math.max(bounds.minZ(), (int) Math.ceil(Math.min(reach.zs()[segment], reach.zs()[segment + 1]) - radius));
                int maxX = Math.min(bounds.maxX(), (int) Math.floor(Math.max(reach.xs()[segment], reach.xs()[segment + 1]) + radius));
                int maxZ = Math.min(bounds.maxZ(), (int) Math.floor(Math.max(reach.zs()[segment], reach.zs()[segment + 1]) + radius));
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        int cell = (z - bounds.minZ()) * width + x - bounds.minX();
                        if (visited.get(cell) || !mask.contains(x, z)) {
                            continue;
                        }
                        visited.set(cell);
                        level = Math.min(level, touchingHead(segments, cursor, brinks, x, z, seaLevel, level));
                        for (int[] side : SIDES) {
                            int nx = x + side[0];
                            int nz = z + side[1];
                            if (!mask.contains(nx, nz)) {
                                level = Math.min(level, touchingHead(segments, cursor, brinks, nx, nz, seaLevel, level));
                            }
                        }
                    }
                }
            }
        }
        return level;
    }

    private static int touchingHead(ReachSegments segments, ReachCursor cursor, ReachGeometry.Lookup brinks, int x, int z, int seaLevel, int level) {
        segments.nearest(x, z, ReachSegments.Scope.CHANNEL, cursor);
        ReachCursor.Choice channel = cursor.channel;
        if (channel.reach == null) {
            return level;
        }
        int head = brinks.head(channel);
        return head < WaterHeads.standing(level, seaLevel) ? head : level;
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
        private static Bounds of(PondMask mask) {
            int minX = mask.originX() - (mask.step() >> 1);
            int minZ = mask.originZ() - (mask.step() >> 1);
            return new Bounds(minX, minZ, minX + mask.width() * mask.step() - 1, minZ + mask.depth() * mask.step() - 1);
        }
    }
}
