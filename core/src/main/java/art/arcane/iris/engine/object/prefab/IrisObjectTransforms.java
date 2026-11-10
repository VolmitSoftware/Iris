/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.object.prefab;

import com.volmit.object.ScaleMethod;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.data.VectorMap;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.IrisVector;
import art.arcane.iris.util.project.interpolation.Interpolation3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geometric transforms for {@link IrisObject}: rotation, scaling and the interpolated upscalers.
 */
final class IrisObjectTransforms {
    private IrisObjectTransforms() {
    }

    static IrisObject rotateCopy(IrisObject self, ObjectTurn turn) {
        IrisObject copy = self.copy();
        rotate(copy, turn);
        return copy;
    }

    static void rotate(IrisObject self, ObjectTurn turn) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> d = new VectorMap<>();

            Set<IrisBlockVector> omitted = new HashSet<>();

            for (var entry : self.blocks) {
                PlatformBlockState rotated = BlockStateRotation.rotate(turn, entry.getValue());
                if (rotated == null) {
                    omitted.add(entry.getKey());
                    continue;
                }
                d.put(turn.rotate(entry.getKey()), rotated);
            }

            VectorMap<TileData> dx = new VectorMap<>();

            for (var entry : self.states) {
                if (!omitted.contains(entry.getKey())) {
                    dx.put(turn.rotate(entry.getKey()), entry.getValue());
                }
            }

            self.blocks = d;
            self.states = dx;
            IrisObjectShaping.shrinkwrap(self);
            self.surfaceSupportOffsets.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    static IrisObject scaled(IrisObject self, double scale, ScaleMethod interpolation) {
        if (interpolation == null) {
            interpolation = ScaleMethod.NONE;
        }
        IrisVector sm1 = new IrisVector(scale - 1, scale - 1, scale - 1);
        scale = Math.max(0.001, Math.min(50, scale));
        if (scale < 1) {
            scale = scale - 0.0001;
        }

        VectorMap<PlatformBlockState> placeBlock = new VectorMap<>();
        IrisVector center;
        IrisObject oo;

        self.readLock.lock();
        try {
            center = new IrisVector(self.getCenter().getX(), self.getCenter().getY(), self.getCenter().getZ());
            if (self.getH() == 2) {
                center = center.setY(center.getBlockY() + 0.5);
            }
            if (self.getW() == 2) {
                center = center.setX(center.getBlockX() + 0.5);
            }
            if (self.getD() == 2) {
                center = center.setZ(center.getBlockZ() + 0.5);
            }

            oo = new IrisObject((int) Math.ceil((self.w * scale) + (scale * 2)), (int) Math.ceil((self.h * scale) + (scale * 2)), (int) Math.ceil((self.d * scale) + (scale * 2)));
            oo.setLoadKey(self.getLoadKey());
            oo.setLoader(self.getLoader());
            oo.setLoadFile(self.getLoadFile());
            for (var entry : self.blocks) {
                PlatformBlockState bd = entry.getValue();
                placeBlock.put(entry.getKey().clone().add(IrisObject.HALF).subtract(center)
                        .multiply(scale).add(sm1).toBlockVector(), bd);
            }
        } finally {
            self.readLock.unlock();
        }

        for (var entry : placeBlock) {
            IrisBlockVector v = entry.getKey();
            if (scale > 1) {
                for (IrisBlockVector vec : IrisObjectShaping.blocksBetweenTwoPoints(v.clone().add(center), v.clone().add(center).add(sm1))) {
                    oo.blocks.put(vec, entry.getValue());
                }
            } else {
                oo.setUnsigned(v.getBlockX(), v.getBlockY(), v.getBlockZ(), entry.getValue());
            }
        }

        if (scale > 1) {
            switch (interpolation) {
                case TRILINEAR -> trilinear(oo, (int) Math.round(scale));
                case TRICUBIC -> tricubic(oo, (int) Math.round(scale));
                case TRIHERMITE -> trihermite(oo, (int) Math.round(scale));
            }
        }

        return oo;
    }

    static void trilinear(IrisObject self, int rad) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> v = self.blocks;
            VectorMap<PlatformBlockState> b = new VectorMap<>();
            IrisPosition min = self.getAABB().min();
            IrisPosition max = self.getAABB().max();
            NearestBlockIndex nearestBlocks = NearestBlockIndex.create(v);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        if (Interpolation3D.getTrilinear(x, y, z, rad, (xx, yy, zz) -> {
                            PlatformBlockState data = v.get(new IrisBlockVector((int) xx, (int) yy, (int) zz));

                            if (B.isAir(data)) {
                                return 0;
                            }

                            return 1;
                        }) >= 0.5) {
                            b.put(new IrisBlockVector(x, y, z), nearestBlockData(v, nearestBlocks, x, y, z));
                        } else {
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.AIR);
                        }
                    }
                }
            }

            self.blocks = b;
            self.surfaceSupportOffsets.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    static void tricubic(IrisObject self, int rad) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> v = self.blocks;
            VectorMap<PlatformBlockState> b = new VectorMap<>();
            IrisPosition min = self.getAABB().min();
            IrisPosition max = self.getAABB().max();
            NearestBlockIndex nearestBlocks = NearestBlockIndex.create(v);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        if (Interpolation3D.getTricubic(x, y, z, rad, (xx, yy, zz) -> {
                            PlatformBlockState data = v.get(new IrisBlockVector((int) xx, (int) yy, (int) zz));

                            if (B.isAir(data)) {
                                return 0;
                            }

                            return 1;
                        }) >= 0.5) {
                            b.put(new IrisBlockVector(x, y, z), nearestBlockData(v, nearestBlocks, x, y, z));
                        } else {
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.AIR);
                        }
                    }
                }
            }

            self.blocks = b;
            self.surfaceSupportOffsets.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    static void trihermite(IrisObject self, int rad) {
        trihermite(self, rad, 0D, 0D);
    }

    static void trihermite(IrisObject self, int rad, double tension, double bias) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> v = self.blocks;
            VectorMap<PlatformBlockState> b = new VectorMap<>();
            IrisPosition min = self.getAABB().min();
            IrisPosition max = self.getAABB().max();
            NearestBlockIndex nearestBlocks = NearestBlockIndex.create(v);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        if (Interpolation3D.getTrihermite(x, y, z, rad, (xx, yy, zz) -> {
                            PlatformBlockState data = v.get(new IrisBlockVector((int) xx, (int) yy, (int) zz));

                            if (B.isAir(data)) {
                                return 0;
                            }

                            return 1;
                        }, tension, bias) >= 0.5) {
                            b.put(new IrisBlockVector(x, y, z), nearestBlockData(v, nearestBlocks, x, y, z));
                        } else {
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.AIR);
                        }
                    }
                }
            }

            self.blocks = b;
            self.surfaceSupportOffsets.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    private static PlatformBlockState nearestBlockData(VectorMap<PlatformBlockState> blocks,
                                                       NearestBlockIndex nearestBlocks,
                                                       int x, int y, int z) {
        IrisBlockVector vv = new IrisBlockVector(x, y, z);
        PlatformBlockState direct = blocks.get(vv);
        if (!B.isAir(direct)) {
            return direct;
        }
        return nearestBlocks.nearest(x, y, z, direct);
    }

    static final class NearestBlockIndex {
        private static final Comparator<NearestBlock> X_ORDER = Comparator
                .comparingInt(NearestBlock::x)
                .thenComparingInt(NearestBlock::rank);
        private static final Comparator<NearestBlock> Y_ORDER = Comparator
                .comparingInt(NearestBlock::y)
                .thenComparingInt(NearestBlock::rank);
        private static final Comparator<NearestBlock> Z_ORDER = Comparator
                .comparingInt(NearestBlock::z)
                .thenComparingInt(NearestBlock::rank);

        private final NearestNode root;
        private PlatformBlockState bestState;
        private double bestDistance;
        private int bestRank;

        private NearestBlockIndex(NearestNode root) {
            this.root = root;
        }

        static NearestBlockIndex create(VectorMap<PlatformBlockState> blocks) {
            List<NearestBlock> points = new ArrayList<>(blocks.size());
            VectorMap<PlatformBlockState>.Cursor cursor = blocks.cursor();
            int rank = 0;
            while (cursor.next()) {
                PlatformBlockState state = cursor.value();
                if (!B.isAir(state)) {
                    IrisBlockVector position = cursor.key();
                    points.add(new NearestBlock(position.getBlockX(), position.getBlockY(), position.getBlockZ(),
                            rank, state));
                }
                rank++;
            }

            NearestBlock[] pointArray = points.toArray(new NearestBlock[0]);
            return new NearestBlockIndex(build(pointArray, 0, pointArray.length, 0));
        }

        PlatformBlockState nearest(int x, int y, int z, PlatformBlockState fallback) {
            if (root == null) {
                return fallback;
            }

            bestState = fallback;
            bestDistance = Double.MAX_VALUE;
            bestRank = Integer.MAX_VALUE;
            search(root, x, y, z);
            return bestState;
        }

        private static NearestNode build(NearestBlock[] points, int from, int to, int depth) {
            if (from >= to) {
                return null;
            }

            int axis = depth % 3;
            Arrays.sort(points, from, to, comparator(axis));
            int middle = (from + to) >>> 1;
            return new NearestNode(
                    points[middle],
                    axis,
                    build(points, from, middle, depth + 1),
                    build(points, middle + 1, to, depth + 1)
            );
        }

        private static Comparator<NearestBlock> comparator(int axis) {
            return switch (axis) {
                case 0 -> X_ORDER;
                case 1 -> Y_ORDER;
                default -> Z_ORDER;
            };
        }

        private void search(NearestNode node, int x, int y, int z) {
            if (node == null) {
                return;
            }

            NearestBlock point = node.point();
            double xDistance = point.x() - x;
            double yDistance = point.y() - y;
            double zDistance = point.z() - z;
            double distance = (xDistance * xDistance) + (yDistance * yDistance) + (zDistance * zDistance);
            if (distance < bestDistance || (distance == bestDistance && point.rank() < bestRank)) {
                bestState = point.state();
                bestDistance = distance;
                bestRank = point.rank();
            }

            double axisDistance = switch (node.axis()) {
                case 0 -> x - point.x();
                case 1 -> y - point.y();
                default -> z - point.z();
            };
            NearestNode near = axisDistance <= 0D ? node.lower() : node.upper();
            NearestNode far = axisDistance <= 0D ? node.upper() : node.lower();
            search(near, x, y, z);
            if ((axisDistance * axisDistance) <= bestDistance) {
                search(far, x, y, z);
            }
        }
    }

    private record NearestNode(NearestBlock point, int axis, NearestNode lower, NearestNode upper) {
    }

    private record NearestBlock(int x, int y, int z, int rank, PlatformBlockState state) {
    }
}
