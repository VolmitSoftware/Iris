/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.biome;

import art.arcane.volmlib.util.math.Rarity;

import art.arcane.iris.generation.cache.Cache;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class FloatingIslandBoundarySampler {
    static final int EDGE_TAPER_WIDTH = FloatingIslandEdgeProfile.DEFAULT_WIDTH;
    static final int EDGE_FADE_RADIUS = EDGE_TAPER_WIDTH + 1;
    private static final int CHUNK_SIZE = 16;
    private static final int MIN_CORE_DISTANCE = 3;

    private final BiomeSampler source;
    private final ConcurrentHashMap<ParentFieldKey, ParentBoundaryField> parentFields;
    private final ConcurrentHashMap<FootprintFieldKey, FootprintField> footprintFields;
    private final ConcurrentHashMap<OwnershipFieldKey, OwnershipField> ownershipFields;

    public FloatingIslandBoundarySampler(BiomeSampler source) {
        this.source = Objects.requireNonNull(source);
        this.parentFields = new ConcurrentHashMap<>();
        this.footprintFields = new ConcurrentHashMap<>();
        this.ownershipFields = new ConcurrentHashMap<>();
    }

    public @Nullable IrisBiome parent(int x, int z) {
        return parentField(x, z, EDGE_TAPER_WIDTH).parent(x, z);
    }

    public double edgeFade(IrisBiome parent, int x, int z) {
        return edgeFade(parent, x, z, EDGE_TAPER_WIDTH);
    }

    public double edgeFade(IrisBiome parent, int x, int z, int taperWidth) {
        int boundaryDistance = edgeDistance(parent, x, z, taperWidth);
        return edgeFadeForDistance(boundaryDistance, taperWidth);
    }

    public int edgeDistance(IrisBiome parent, int x, int z, int taperWidth) {
        if (parent == null) {
            return 0;
        }

        return parentField(x, z, taperWidth).edgeDistance(parent, x, z);
    }

    public FootprintSample footprint(CNG footprint, int x, int z, double signedCut) {
        return footprint(footprint, x, z, signedCut, EDGE_TAPER_WIDTH);
    }

    public FootprintSample footprint(CNG footprint, int x, int z, double signedCut, int taperWidth) {
        int width = FloatingIslandEdgeProfile.clampWidth(taperWidth);
        int chunkX = Math.floorDiv(x, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
        FootprintFieldKey key = new FootprintFieldKey(
                footprint, chunkX, chunkZ, Double.doubleToLongBits(signedCut), width);
        FootprintField field = footprintFields.computeIfAbsent(key,
                ignored -> new FootprintField(footprint, chunkX, chunkZ, signedCut, width));
        return field.sample(x, z);
    }

    public OwnershipSample ownership(KList<IrisFloatingChildBiomes> entries, CNG picker, int x, int z) {
        return ownership(entries, picker, x, z, EDGE_TAPER_WIDTH);
    }

    public OwnershipSample ownership(KList<IrisFloatingChildBiomes> entries, CNG picker, int x, int z,
                                     int taperWidth) {
        int width = FloatingIslandEdgeProfile.clampWidth(taperWidth);
        int chunkX = Math.floorDiv(x, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
        OwnershipFieldKey key = new OwnershipFieldKey(entries, picker, chunkX, chunkZ, width);
        OwnershipField field = ownershipFields.computeIfAbsent(key,
                ignored -> new OwnershipField(entries, picker, chunkX, chunkZ, width));
        return field.sample(x, z);
    }

    static double edgeFadeForDistance(int boundaryDistance) {
        return edgeFadeForDistance(boundaryDistance, EDGE_TAPER_WIDTH);
    }

    static double edgeFadeForDistance(int boundaryDistance, int taperWidth) {
        int width = FloatingIslandEdgeProfile.clampWidth(taperWidth);
        double normalized = Math.max(0.0D, Math.min(1.0D, (boundaryDistance - 1.0D) / width));
        return normalized * normalized * (3.0D - (2.0D * normalized));
    }

    private ParentBoundaryField parentField(int x, int z, int taperWidth) {
        int width = FloatingIslandEdgeProfile.clampWidth(taperWidth);
        int chunkX = Math.floorDiv(x, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
        ParentFieldKey key = new ParentFieldKey(Cache.key(chunkX, chunkZ), width);
        return parentFields.computeIfAbsent(key,
                ignored -> new ParentBoundaryField(source, chunkX, chunkZ, width));
    }

    private static boolean sameBiome(@Nullable IrisBiome expected, @Nullable IrisBiome actual) {
        if (actual == expected) {
            return true;
        }
        if (expected == null || actual == null || expected.getLoadKey() == null) {
            return false;
        }
        return expected.getLoadKey().equals(actual.getLoadKey());
    }

    public record FootprintSample(double signed, int cardinalSupport, int diagonalSupport, boolean accepted,
                                  int boundaryDistance, double edgeFade) {
    }

    public record OwnershipSample(IrisFloatingChildBiomes owner, int boundaryDistance, double edgeFade) {
    }

    private record ParentFieldKey(long chunkKey, int taperWidth) {
    }

    private static final class FootprintFieldKey {
        private final CNG footprint;
        private final int chunkX;
        private final int chunkZ;
        private final long signedCutBits;
        private final int taperWidth;

        private FootprintFieldKey(CNG footprint, int chunkX, int chunkZ, long signedCutBits, int taperWidth) {
            this.footprint = footprint;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.signedCutBits = signedCutBits;
            this.taperWidth = taperWidth;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FootprintFieldKey key)) {
                return false;
            }
            return footprint == key.footprint && chunkX == key.chunkX && chunkZ == key.chunkZ
                    && signedCutBits == key.signedCutBits && taperWidth == key.taperWidth;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(footprint);
            result = (31 * result) + chunkX;
            result = (31 * result) + chunkZ;
            result = (31 * result) + Long.hashCode(signedCutBits);
            return (31 * result) + taperWidth;
        }
    }

    private static final class OwnershipFieldKey {
        private final KList<IrisFloatingChildBiomes> entries;
        private final CNG picker;
        private final int chunkX;
        private final int chunkZ;
        private final int taperWidth;

        private OwnershipFieldKey(KList<IrisFloatingChildBiomes> entries, CNG picker, int chunkX, int chunkZ,
                                  int taperWidth) {
            this.entries = entries;
            this.picker = picker;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.taperWidth = taperWidth;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof OwnershipFieldKey key)) {
                return false;
            }
            return entries == key.entries && picker == key.picker && chunkX == key.chunkX && chunkZ == key.chunkZ
                    && taperWidth == key.taperWidth;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(entries);
            result = (31 * result) + System.identityHashCode(picker);
            result = (31 * result) + chunkX;
            result = (31 * result) + chunkZ;
            return (31 * result) + taperWidth;
        }
    }

    private static final class ParentBoundaryField {
        private final FieldGeometry geometry;
        private final int minX;
        private final int minZ;
        private final IrisBiome[] parents;
        private final byte[] distance;
        private final boolean[] viable;

        private ParentBoundaryField(BiomeSampler source, int chunkX, int chunkZ, int taperWidth) {
            this.geometry = FieldGeometry.forWidth(taperWidth);
            this.minX = (chunkX * CHUNK_SIZE) - geometry.fadeRadius();
            this.minZ = (chunkZ * CHUNK_SIZE) - geometry.fadeRadius();
            this.parents = new IrisBiome[geometry.area()];
            this.distance = new byte[geometry.area()];
            this.viable = new boolean[geometry.area()];
            build(source);
        }

        private @Nullable IrisBiome parent(int x, int z) {
            return parents[geometry.index(x - minX, z - minZ)];
        }

        private int edgeDistance(IrisBiome expected, int x, int z) {
            int index = geometry.index(x - minX, z - minZ);
            if (!sameBiome(expected, parents[index]) || !viable[index]) {
                return 0;
            }
            return Math.min(geometry.maxRenderableDistance(), Byte.toUnsignedInt(distance[index]));
        }

        private void build(BiomeSampler source) {
            int rawSize = geometry.fieldSize() + 2;
            IrisBiome[] raw = new IrisBiome[rawSize * rawSize];
            int rawMinX = minX - 1;
            int rawMinZ = minZ - 1;
            for (int z = 0; z < rawSize; z++) {
                for (int x = 0; x < rawSize; x++) {
                    raw[rawIndex(x, z, rawSize)] = source.sample(rawMinX + x, rawMinZ + z);
                }
            }
            for (int z = 0; z < geometry.fieldSize(); z++) {
                for (int x = 0; x < geometry.fieldSize(); x++) {
                    int index = geometry.index(x, z);
                    IrisBiome parent = raw[rawIndex(x + 1, z + 1, rawSize)];
                    parents[index] = parent;
                    distance[index] = (byte) (touchesDifferentBiome(raw, parent, x, z, rawSize)
                            ? 1
                            : geometry.maxDistance());
                }
            }
            propagateDistanceField(distance, geometry);
            markLabelCoreSupported(parents, distance, viable, geometry);
        }

        private static boolean touchesDifferentBiome(IrisBiome[] raw, @Nullable IrisBiome parent, int x, int z,
                                                      int rawSize) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (!sameBiome(parent, raw[rawIndex(x + dx + 1, z + dz + 1, rawSize)])) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static int rawIndex(int x, int z, int rawSize) {
            return (z * rawSize) + x;
        }
    }

    private static final class FootprintField {
        private final FieldGeometry geometry;
        private final int occupancyMinX;
        private final int occupancyMinZ;
        private final double[] signed;
        private final byte[] cardinalSupport;
        private final byte[] diagonalSupport;
        private final boolean[] accepted;
        private final byte[] distance;
        private final boolean[] viable;

        private FootprintField(CNG footprint, int chunkX, int chunkZ, double signedCut, int taperWidth) {
            this.geometry = FieldGeometry.forWidth(taperWidth);
            this.occupancyMinX = (chunkX * CHUNK_SIZE) - geometry.fadeRadius();
            this.occupancyMinZ = (chunkZ * CHUNK_SIZE) - geometry.fadeRadius();
            this.signed = new double[geometry.area()];
            this.cardinalSupport = new byte[geometry.area()];
            this.diagonalSupport = new byte[geometry.area()];
            this.accepted = new boolean[geometry.area()];
            this.distance = new byte[geometry.area()];
            this.viable = new boolean[geometry.area()];
            build(footprint, signedCut);
        }

        private FootprintSample sample(int x, int z) {
            int index = geometry.index(x - occupancyMinX, z - occupancyMinZ);
            int inwardDistance = Byte.toUnsignedInt(distance[index]);
            boolean renderable = accepted[index] && viable[index] && inwardDistance > 1;
            int boundaryDistance = renderable
                    ? Math.min(geometry.maxRenderableDistance(), inwardDistance)
                    : 0;
            double fade = edgeFadeForDistance(boundaryDistance, geometry.taperWidth());
            return new FootprintSample(signed[index], Byte.toUnsignedInt(cardinalSupport[index]),
                    Byte.toUnsignedInt(diagonalSupport[index]), renderable, boundaryDistance, fade);
        }

        private void build(CNG footprint, double signedCut) {
            double[] rawSigned = sampleRawFootprint(footprint);
            buildOccupancy(rawSigned, signedCut);
            buildDistanceField();
        }

        private double[] sampleRawFootprint(CNG footprint) {
            int rawSize = geometry.fieldSize() + 2;
            double[] rawSigned = new double[rawSize * rawSize];
            int rawMinX = occupancyMinX - 1;
            int rawMinZ = occupancyMinZ - 1;
            for (int z = 0; z < rawSize; z++) {
                for (int x = 0; x < rawSize; x++) {
                    rawSigned[rawIndex(x, z, rawSize)] = signedFromUnit(footprint.noise(rawMinX + x, rawMinZ + z));
                }
            }
            return rawSigned;
        }

        private void buildOccupancy(double[] rawSigned, double signedCut) {
            int rawSize = geometry.fieldSize() + 2;
            for (int z = 0; z < geometry.fieldSize(); z++) {
                for (int x = 0; x < geometry.fieldSize(); x++) {
                    int index = geometry.index(x, z);
                    double value = rawSigned[rawIndex(x + 1, z + 1, rawSize)];
                    int cardinal = 0;
                    int diagonal = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            if (rawSigned[rawIndex(x + dx + 1, z + dz + 1, rawSize)] <= signedCut) {
                                continue;
                            }
                            if (Math.abs(dx) + Math.abs(dz) == 1) {
                                cardinal++;
                            } else {
                                diagonal++;
                            }
                        }
                    }
                    boolean solid = value > signedCut;
                    FloatingIslandSample.NeighborSupport support = new FloatingIslandSample.NeighborSupport(cardinal, diagonal);
                    boolean supported = support.hasSolidSupport();
                    boolean repairedPinhole = FloatingIslandSample.isFootprintPinholeRepairable(support);
                    signed[index] = value;
                    cardinalSupport[index] = (byte) cardinal;
                    diagonalSupport[index] = (byte) diagonal;
                    accepted[index] = (solid && supported) || (!solid && repairedPinhole);
                }
            }
        }

        private void buildDistanceField() {
            fillDistanceField(accepted, distance, geometry);
            markCoreSupported(accepted, distance, viable, geometry);
        }

        private static int rawIndex(int x, int z, int rawSize) {
            return (z * rawSize) + x;
        }

        private static double signedFromUnit(double value) {
            return (Math.max(0.0D, Math.min(1.0D, value)) * 2.0D) - 1.0D;
        }
    }

    private static final class OwnershipField {
        private final FieldGeometry geometry;
        private final int minX;
        private final int minZ;
        private final IrisFloatingChildBiomes[] owners;
        private final IdentityHashMap<IrisFloatingChildBiomes, ComponentDistanceField> distances;

        private OwnershipField(KList<IrisFloatingChildBiomes> entries, CNG picker, int chunkX, int chunkZ,
                               int taperWidth) {
            this.geometry = FieldGeometry.forWidth(taperWidth);
            this.minX = (chunkX * CHUNK_SIZE) - geometry.fadeRadius();
            this.minZ = (chunkZ * CHUNK_SIZE) - geometry.fadeRadius();
            this.owners = new IrisFloatingChildBiomes[geometry.area()];
            this.distances = new IdentityHashMap<>();
            for (int z = 0; z < geometry.fieldSize(); z++) {
                for (int x = 0; x < geometry.fieldSize(); x++) {
                    double value = Math.max(0.0D, Math.min(1.0D, picker.noise(minX + x, minZ + z)));
                    owners[geometry.index(x, z)] = Rarity.pick(entries, value);
                }
            }
            for (IrisFloatingChildBiomes entry : entries) {
                if (entry != null && !distances.containsKey(entry)) {
                    distances.put(entry, buildDistanceField(entry));
                }
            }
        }

        private OwnershipSample sample(int x, int z) {
            int index = geometry.index(x - minX, z - minZ);
            IrisFloatingChildBiomes owner = owners[index];
            if (owner == null) {
                return new OwnershipSample(null, 0, 0.0D);
            }
            ComponentDistanceField field = distances.get(owner);
            if (field == null) {
                return new OwnershipSample(null, 0, 0.0D);
            }
            int inwardDistance = Byte.toUnsignedInt(field.distance()[index]);
            int boundaryDistance = field.viable()[index]
                    ? Math.min(geometry.maxRenderableDistance(), inwardDistance)
                    : 0;
            double fade = edgeFadeForDistance(boundaryDistance, geometry.taperWidth());
            return new OwnershipSample(owner, boundaryDistance, fade);
        }

        private ComponentDistanceField buildDistanceField(IrisFloatingChildBiomes owner) {
            boolean[] owned = new boolean[owners.length];
            byte[] distance = new byte[owners.length];
            boolean[] viable = new boolean[owners.length];
            for (int i = 0; i < owners.length; i++) {
                owned[i] = owners[i] == owner;
            }
            fillDistanceField(owned, distance, geometry);
            markCoreSupported(owned, distance, viable, geometry);
            return new ComponentDistanceField(distance, viable);
        }
    }

    private record ComponentDistanceField(byte[] distance, boolean[] viable) {
    }

    private record FieldGeometry(int taperWidth, int fadeRadius, int fieldSize, int maxDistance) {
        private static FieldGeometry forWidth(int taperWidth) {
            int width = FloatingIslandEdgeProfile.clampWidth(taperWidth);
            int radius = width + 1;
            return new FieldGeometry(width, radius, CHUNK_SIZE + (radius * 2), radius + 1);
        }

        private int area() {
            return fieldSize * fieldSize;
        }

        private int maxRenderableDistance() {
            return fadeRadius;
        }

        private int index(int x, int z) {
            return (z * fieldSize) + x;
        }
    }

    private static void fillDistanceField(boolean[] inside, byte[] distance, FieldGeometry geometry) {
        for (int i = 0; i < inside.length; i++) {
            distance[i] = (byte) (inside[i] ? geometry.maxDistance() : 0);
        }
        for (int z = 0; z < geometry.fieldSize(); z++) {
            for (int x = 0; x < geometry.fieldSize(); x++) {
                if (!inside[geometry.index(x, z)]) {
                    continue;
                }
                setMinimumDistance(distance, x, z, x - 1, z, geometry);
                setMinimumDistance(distance, x, z, x, z - 1, geometry);
                setMinimumDistance(distance, x, z, x - 1, z - 1, geometry);
                setMinimumDistance(distance, x, z, x + 1, z - 1, geometry);
            }
        }
        for (int z = geometry.fieldSize() - 1; z >= 0; z--) {
            for (int x = geometry.fieldSize() - 1; x >= 0; x--) {
                if (!inside[geometry.index(x, z)]) {
                    continue;
                }
                setMinimumDistance(distance, x, z, x + 1, z, geometry);
                setMinimumDistance(distance, x, z, x, z + 1, geometry);
                setMinimumDistance(distance, x, z, x + 1, z + 1, geometry);
                setMinimumDistance(distance, x, z, x - 1, z + 1, geometry);
            }
        }
    }

    private static void propagateDistanceField(byte[] distance, FieldGeometry geometry) {
        for (int z = 0; z < geometry.fieldSize(); z++) {
            for (int x = 0; x < geometry.fieldSize(); x++) {
                setMinimumDistance(distance, x, z, x - 1, z, geometry);
                setMinimumDistance(distance, x, z, x, z - 1, geometry);
                setMinimumDistance(distance, x, z, x - 1, z - 1, geometry);
                setMinimumDistance(distance, x, z, x + 1, z - 1, geometry);
            }
        }
        for (int z = geometry.fieldSize() - 1; z >= 0; z--) {
            for (int x = geometry.fieldSize() - 1; x >= 0; x--) {
                setMinimumDistance(distance, x, z, x + 1, z, geometry);
                setMinimumDistance(distance, x, z, x, z + 1, geometry);
                setMinimumDistance(distance, x, z, x + 1, z + 1, geometry);
                setMinimumDistance(distance, x, z, x - 1, z + 1, geometry);
            }
        }
    }

    private static void setMinimumDistance(byte[] distance, int x, int z, int neighborX, int neighborZ,
                                           FieldGeometry geometry) {
        if (neighborX < 0 || neighborX >= geometry.fieldSize()
                || neighborZ < 0 || neighborZ >= geometry.fieldSize()) {
            return;
        }
        int index = geometry.index(x, z);
        int neighborDistance = Byte.toUnsignedInt(distance[geometry.index(neighborX, neighborZ)]);
        int currentDistance = Byte.toUnsignedInt(distance[index]);
        distance[index] = (byte) Math.min(currentDistance, neighborDistance + 1);
    }

    private static void markCoreSupported(boolean[] inside, byte[] distance, boolean[] viable,
                                          FieldGeometry geometry) {
        for (int z = 0; z < geometry.fieldSize(); z++) {
            for (int x = 0; x < geometry.fieldSize(); x++) {
                int index = geometry.index(x, z);
                if (!inside[index]) {
                    continue;
                }
                int inwardDistance = Byte.toUnsignedInt(distance[index]);
                viable[index] = inwardDistance >= MIN_CORE_DISTANCE
                        || inwardDistance == MIN_CORE_DISTANCE - 1
                        && hasCoreNeighbor(inside, distance, null, x, z, geometry);
            }
        }
    }

    private static void markLabelCoreSupported(IrisBiome[] labels, byte[] distance, boolean[] viable,
                                               FieldGeometry geometry) {
        for (int z = 0; z < geometry.fieldSize(); z++) {
            for (int x = 0; x < geometry.fieldSize(); x++) {
                int index = geometry.index(x, z);
                int inwardDistance = Byte.toUnsignedInt(distance[index]);
                viable[index] = inwardDistance >= MIN_CORE_DISTANCE
                        || inwardDistance == MIN_CORE_DISTANCE - 1
                        && hasCoreNeighbor(null, distance, labels, x, z, geometry);
            }
        }
    }

    private static boolean hasCoreNeighbor(@Nullable boolean[] inside, byte[] distance,
                                           @Nullable IrisBiome[] labels, int x, int z, FieldGeometry geometry) {
        int index = geometry.index(x, z);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int neighborX = x + dx;
                int neighborZ = z + dz;
                if (neighborX < 0 || neighborX >= geometry.fieldSize()
                        || neighborZ < 0 || neighborZ >= geometry.fieldSize()) {
                    continue;
                }
                int neighbor = geometry.index(neighborX, neighborZ);
                if (Byte.toUnsignedInt(distance[neighbor]) < MIN_CORE_DISTANCE) {
                    continue;
                }
                if (inside != null && !inside[neighbor]) {
                    continue;
                }
                if (labels != null && !sameBiome(labels[index], labels[neighbor])) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface BiomeSampler {
        @Nullable IrisBiome sample(int x, int z);
    }
}
