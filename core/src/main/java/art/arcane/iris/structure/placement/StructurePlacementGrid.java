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

package art.arcane.iris.structure.placement;

import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.volmlib.util.math.RNG;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class StructurePlacementGrid {
    private static final long DENSITY_SIGNATURE = 0x6A09E667F3BCC909L;

    private StructurePlacementGrid() {
    }

    public static boolean startsInChunk(IrisStructurePlacement placement, int cx, int cz, long seed) {
        return switch (placement.getDistribution()) {
            case RANDOM_SPREAD -> randomSpreadStart(cx, cz, placement.getSpacing(), placement.getSeparation(),
                    placementSalt(placement), seed);
            case DENSITY -> densityStart(placement, cx, cz, seed);
            case CONCENTRIC_RINGS -> concentricRingsStart(cx, cz, placement, seed);
        };
    }

    public static RNG placementRng(IrisStructurePlacement placement, int cx, int cz, long seed) {
        return new RNG(placementSeed(placement, cx, cz, seed));
    }

    public static boolean randomSpreadStart(int cx, int cz, int spacing, int separation, int salt, long seed) {
        int sp = Math.max(1, spacing);
        int cellX = Math.floorDiv(cx, sp);
        int cellZ = Math.floorDiv(cz, sp);
        int[] start = randomSpreadCellChunk(cellX, cellZ, spacing, separation, salt, seed);
        return start[0] == cx && start[1] == cz;
    }

    /**
     * Returns the single {chunkX, chunkZ} that a RANDOM_SPREAD placement occupies within the given
     * grid cell. This is the inverse of {@link #randomSpreadStart} and uses the identical formula, so
     * a locator can jump straight to the one candidate per cell instead of testing every chunk.
     */
    public static int[] randomSpreadCellChunk(int cellX, int cellZ, int spacing, int separation, int salt, long seed) {
        int sp = Math.max(1, spacing);
        int sep = Math.max(0, Math.min(separation, sp - 1));
        RNG r = new RNG(mix(seed, cellX, cellZ, salt));
        int range = Math.max(1, sp - sep);
        int startCx = cellX * sp + r.nextInt(range);
        int startCz = cellZ * sp + r.nextInt(range);
        return new int[]{startCx, startCz};
    }

    public static int[] concentricRingChunk(IrisStructurePlacement placement, int placementIndex, long seed) {
        int count = Math.max(1, placement.getRingCount());
        if (placementIndex < 0 || placementIndex >= count) {
            return null;
        }
        int distance = Math.max(1, placement.getRingDistance());
        int spread = Math.max(1, placement.getRingSpread());
        int ring = Math.floorDiv(placementIndex, spread) + 1;
        int firstIndex = (ring - 1) * spread;
        int slots = Math.min(spread, count - firstIndex);
        int slot = placementIndex - firstIndex;
        double slotAngle = (2.0 * Math.PI) / slots;
        double offset = unitDouble(mix(seed, ring, count, placementSalt(placement))) * slotAngle;
        double angle = offset + (slot * slotAngle);
        long configuredRadius = (long) ring * distance;
        if (configuredRadius > Integer.MAX_VALUE) {
            return null;
        }
        int radius = (int) configuredRadius;
        return new int[]{
                (int) Math.round(Math.cos(angle) * radius),
                (int) Math.round(Math.sin(angle) * radius)
        };
    }

    private static boolean concentricRingsStart(int cx, int cz, IrisStructurePlacement placement, long seed) {
        if (cx == 0 && cz == 0) {
            return false;
        }
        int distance = Math.max(1, placement.getRingDistance());
        int count = Math.max(1, placement.getRingCount());
        int spread = Math.max(1, placement.getRingSpread());
        double dist = Math.sqrt((double) cx * cx + (double) cz * cz);
        int estimatedRing = (int) Math.round(dist / distance);
        int ringCount = Math.ceilDiv(count, spread);
        int firstRing = Math.max(1, estimatedRing - 1);
        int lastRing = Math.min(ringCount, estimatedRing + 1);
        for (int ring = firstRing; ring <= lastRing; ring++) {
            int firstIndex = (ring - 1) * spread;
            int slots = Math.min(spread, count - firstIndex);
            if (slots <= 0) {
                continue;
            }
            double slotAngle = (2.0 * Math.PI) / slots;
            double offset = unitDouble(mix(seed, ring, count, placementSalt(placement))) * slotAngle;
            double slotPosition = (Math.atan2(cz, cx) - offset) / slotAngle;
            int nearestSlot = Math.floorMod((int) Math.round(slotPosition), slots);
            for (int delta = -1; delta <= 1; delta++) {
                int slot = Math.floorMod(nearestSlot + delta, slots);
                int[] candidate = concentricRingChunk(placement, firstIndex + slot, seed);
                if (candidate != null && candidate[0] == cx && candidate[1] == cz) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean densityStart(IrisStructurePlacement placement, int cx, int cz, long seed) {
        double density = placement.getDensity();
        if (density <= 0.0) {
            return false;
        }
        if (density >= 1.0) {
            return true;
        }
        RNG rng = new RNG(placementSeed(placement, cx, cz, seed) ^ DENSITY_SIGNATURE);
        return rng.chance(density);
    }

    public static long placementIdentity(IrisStructurePlacement placement) {
        long signature = 1469598103934665603L;
        signature = appendLong(signature, placement.getDistribution().ordinal());
        signature = appendLong(signature, placement.getSalt());
        String placementId = placement.getPlacementId();
        if (placementId != null && !placementId.isBlank()) {
            signature = appendLong(signature, 1L);
            signature = appendSignature(signature, placementId.trim());
        } else {
            signature = appendLong(signature, 0L);
            signature = appendLong(signature, placement.getSpacing());
            signature = appendLong(signature, placement.getSeparation());
            signature = appendLong(signature, Double.doubleToLongBits(placement.getDensity()));
            signature = appendLong(signature, placement.getRingCount());
            signature = appendLong(signature, placement.getRingDistance());
            signature = appendLong(signature, placement.getRingSpread());
            signature = appendLong(signature, placement.getMinHeight());
            signature = appendLong(signature, placement.getMaxHeight());
            signature = appendLong(signature, placement.isUnderground() ? 1L : 0L);
            signature = appendLong(signature, placement.isUnderwater() ? 1L : 0L);
            signature = appendSources(signature, placement);
            signature = appendAnchor(signature, placement);
        }
        return signature;
    }

    private static long placementSeed(IrisStructurePlacement placement, int cx, int cz, long seed) {
        long signature = placementIdentity(placement);
        int identitySalt = (int) (signature ^ (signature >>> 32));
        return mix(seed ^ signature, cx, cz, placementSalt(placement) ^ identitySalt);
    }

    private static long appendSignature(long signature, String value) {
        long result = appendLong(signature, value.length());
        for (int index = 0; index < value.length(); index++) {
            result = (result ^ value.charAt(index)) * 1099511628211L;
        }
        return result;
    }

    private static long appendLong(long signature, long value) {
        long result = signature;
        long remaining = value;
        for (int index = 0; index < Long.BYTES; index++) {
            result = (result ^ (remaining & 0xffL)) * 1099511628211L;
            remaining >>>= Byte.SIZE;
        }
        return result;
    }

    private static long appendSources(long signature, IrisStructurePlacement placement) {
        long result = signature;
        if (placement.hasIrisStructures()) {
            result = appendLong(result, 1L);
            result = appendLong(result, placement.getStructures().size());
            for (String key : placement.getStructures()) {
                result = appendSignature(result, key == null ? "" : key);
            }
            return result;
        }
        result = appendLong(result, 2L);
        if (placement.getNativeStructures() == null) {
            return appendLong(result, 0L);
        }
        result = appendLong(result, placement.getNativeStructures().size());
        for (IrisNativeStructure source : placement.getNativeStructures()) {
            if (source == null) {
                result = appendSignature(result, "");
                result = appendLong(result, 0L);
                continue;
            }
            result = appendSignature(result,
                    source.getStructure() == null ? "" : source.getStructure());
            result = appendLong(result, source.getWeight());
        }
        return result;
    }

    private static long appendAnchor(long signature, IrisStructurePlacement placement) {
        IrisStructureAnchorMode anchor = placement.getAnchor();
        if (anchor == null || anchor == IrisStructureAnchorMode.LEGACY) {
            return signature;
        }
        long result = appendLong(signature, 0x4A49475341574C41L);
        result = appendLong(result, anchor.ordinal());
        if (!anchor.isCave()) {
            return result;
        }
        result = appendLong(result, placement.getCaveAnchorAttempts());
        result = appendLong(result, placement.getCaveAnchorScanStep());
        result = appendLong(result, placement.getCaveMinimumClearance());
        if (placement.getCaveBiomes() == null) {
            return appendLong(result, 0L);
        }
        Set<String> biomes = new TreeSet<>();
        for (String biome : placement.getCaveBiomes()) {
            if (biome != null && !biome.isBlank()) {
                biomes.add(biome.trim().toLowerCase(Locale.ROOT));
            }
        }
        result = appendLong(result, biomes.size());
        for (String biome : biomes) {
            result = appendSignature(result, biome);
        }
        return result;
    }

    static int placementSalt(IrisStructurePlacement placement) {
        String placementId = placement.getPlacementId();
        long identity;
        if (placementId != null && !placementId.isBlank()) {
            identity = appendSignature(1469598103934665603L, placementId.trim());
        } else {
            identity = 1469598103934665603L;
            identity = appendLong(identity, placement.getDistribution().ordinal());
            identity = appendLong(identity, placement.getSpacing());
            identity = appendLong(identity, placement.getSeparation());
            identity = appendLong(identity, Double.doubleToLongBits(placement.getDensity()));
            identity = appendLong(identity, placement.getRingCount());
            identity = appendLong(identity, placement.getRingDistance());
            identity = appendLong(identity, placement.getRingSpread());
            identity = appendLong(identity, placement.getMinHeight());
            identity = appendLong(identity, placement.getMaxHeight());
            identity = appendLong(identity, placement.isUnderground() ? 1L : 0L);
            identity = appendLong(identity, placement.isUnderwater() ? 1L : 0L);
            identity = appendSources(identity, placement);
            identity = appendAnchor(identity, placement);
        }
        return placement.getSalt() ^ (int) (identity ^ (identity >>> 32));
    }

    private static double unitDouble(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    public static long mix(long seed, int a, int b, int salt) {
        long h = seed;
        h = h * 6364136223846793005L + (a * 341873128712L);
        h = h * 6364136223846793005L + (b * 132897987541L);
        h = h * 6364136223846793005L + salt;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }
}
