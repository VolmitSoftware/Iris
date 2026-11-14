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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;
import java.util.Optional;

/**
 * The chance and spacing of surface object placements.
 *
 * <p>A separated placement rolls one candidate spot per chunk. The candidate stands only when no candidate of a lower rank,
 * from a placement with the same objects, lies closer than the separation. Spots and ranks come from the seed, the chunk
 * and the objects alone, so every chunk agrees on them across chunk, biome and region borders, and objects never stand
 * closer than the separation, whatever order chunks generate in.
 */
final class ObjectSeparation {
    private static final int CHUNK_WIDTH_BLOCKS = 16;
    private static final int CHUNK_SHIFT = 4;

    private final long seed;
    private final IrisData data;
    private final SurfacePlacements surfacePlacements;

    ObjectSeparation(long seed, IrisData data, SurfacePlacements surfacePlacements) {
        this.seed = seed;
        this.data = data;
        this.surfacePlacements = surfacePlacements;
    }

    /** The placements a chunk places on the surface, in placing order. A chunk places the biome's before the region's. */
    @FunctionalInterface
    interface SurfacePlacements {
        List<IrisObjectPlacement> at(int chunkX, int chunkZ);
    }

    /** Where a chunk would place, in block coordinates, with the random that picks and places the object there. */
    record Candidate(int x, int z, long rank, RNG rng) {
        boolean isCloserThan(Candidate other, int blocks) {
            long dx = x - other.x;
            long dz = z - other.z;
            return dx * dx + dz * dz < (long) blocks * blocks;
        }
    }

    /** The chance to place at the block column: the placement's chance, multiplied by its chance noise when it has one. */
    double chanceAt(IrisObjectPlacement placement, double x, double z) {
        IrisGeneratorStyle style = placement.getChanceStyle();
        if (style == null) {
            return placement.getChance();
        }
        return placement.getChance() * style.create(seed, data).fitDouble(0D, 1D, x, z);
    }

    /**
     * The candidate the chunk places for the separated placement; empty when it rolled none or a lower ranked one is too
     * close.
     */
    Optional<Candidate> keptCandidate(int chunkX, int chunkZ, IrisObjectPlacement placement) {
        Optional<Candidate> own = candidateOf(chunkX, chunkZ, placement);
        if (own.isEmpty() || isOutranked(own.get(), chunkX, chunkZ, placement)) {
            return Optional.empty();
        }
        return own;
    }

    private boolean isOutranked(Candidate own, int chunkX, int chunkZ, IrisObjectPlacement placement) {
        int reach = placement.getSeparation();
        int minimumChunkX = Math.floorDiv(own.x() - reach, CHUNK_WIDTH_BLOCKS);
        int maximumChunkX = Math.floorDiv(own.x() + reach, CHUNK_WIDTH_BLOCKS);
        int minimumChunkZ = Math.floorDiv(own.z() - reach, CHUNK_WIDTH_BLOCKS);
        int maximumChunkZ = Math.floorDiv(own.z() + reach, CHUNK_WIDTH_BLOCKS);
        for (int x = minimumChunkX; x <= maximumChunkX; x++) {
            for (int z = minimumChunkZ; z <= maximumChunkZ; z++) {
                if ((x != chunkX || z != chunkZ) && isOutrankedBy(x, z, own, placement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOutrankedBy(int rivalChunkX, int rivalChunkZ, Candidate own, IrisObjectPlacement placement) {
        Optional<Candidate> rival = placementOfSameObjectsAt(rivalChunkX, rivalChunkZ, placement)
                .flatMap(other -> candidateOf(rivalChunkX, rivalChunkZ, other));
        return rival.isPresent() && rival.get().rank() < own.rank() && rival.get().isCloserThan(own, placement.getSeparation());
    }

    private Optional<IrisObjectPlacement> placementOfSameObjectsAt(int chunkX, int chunkZ, IrisObjectPlacement placement) {
        return surfacePlacements.at(chunkX, chunkZ).stream()
                .filter(other -> other.isSeparated() && other.getPlace().equals(placement.getPlace()))
                .findFirst();
    }

    private Optional<Candidate> candidateOf(int chunkX, int chunkZ, IrisObjectPlacement placement) {
        RNG rng = new RNG(chunkSeed(chunkX, chunkZ, placement.getPlace().hashCode()));
        int x = (chunkX << CHUNK_SHIFT) + rng.nextInt(CHUNK_WIDTH_BLOCKS);
        int z = (chunkZ << CHUNK_SHIFT) + rng.nextInt(CHUNK_WIDTH_BLOCKS);
        if (!rng.chance(chanceAt(placement, x, z))) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(x, z, rng.nextLong(), rng));
    }

    // Each input is mixed in after the previous one: XORing scaled coordinates gave chunk x,z and -x,-z the same seed
    // whenever both were odd.
    private long chunkSeed(int chunkX, int chunkZ, int objectsHash) {
        return splitMix(splitMix(splitMix(seed + objectsHash) + chunkX) + chunkZ);
    }

    private static long splitMix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
