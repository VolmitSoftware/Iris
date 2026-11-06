/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.core.studio.saved;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A world on disk, read from its region files rather than generated, a chunk at a time as the view needs it. A region
 * whose file changed since it was read is read again, so the view follows a world the server is still writing.
 */
public final class SavedWorld {
    static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    // A region read from its file takes about 2 MB; the rest of the heap is the server's.
    private static final long BYTES_PER_REGION = 2_100_000L;
    private static final double HEAP_SHARE_FOR_REGIONS = 0.1;
    private static final int FEWEST_REGIONS = 16;
    private static final OpenRegion NOTHING_SAVED = new OpenRegion(SavedRegion.NOTHING_SAVED, null);

    private final Path regionFolder;
    private final SavedGround ground;
    private final Cache<Long, OpenRegion> regions = Caffeine.newBuilder()
            .maximumSize(heapShareCount())
            .build();

    SavedWorld(Path regionFolder, SavedGround ground) {
        this.regionFolder = regionFolder;
        this.ground = ground;
    }

    static boolean holdsRegionFiles(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.anyMatch(file -> REGION_FILE.matcher(file.getFileName().toString()).matches());
        } catch (IOException unreadable) {
            return false;
        }
    }

    /**
     * Where the view opens: the middle of the region file nearest the world's origin that holds finished terrain, with
     * how low and high its ground goes. Empty when no region file does.
     */
    public Optional<Start> start() {
        for (int[] regionXZ : regionsByDistanceFromOrigin()) {
            SavedRegion region = region(regionXZ[0], regionXZ[1]);
            region.readAll();
            if (!region.isEmpty()) {
                int half = SavedRegion.BLOCKS_ACROSS / 2;
                int seaLevel = region.seaLevel();
                return Optional.of(new Start(regionXZ[0] * SavedRegion.BLOCKS_ACROSS + half, regionXZ[1] * SavedRegion.BLOCKS_ACROSS + half,
                        region.minY(), region.lowestGround(), region.highestGround(), seaLevel == SavedRegion.NO_WATER ? null : seaLevel));
            }
        }
        return Optional.empty();
    }

    /** The column at this block, or null where no finished chunk holds it. */
    SavedRegion.SavedColumn columnAt(int blockX, int blockZ) {
        SavedRegion region = region(Math.floorDiv(blockX, SavedRegion.BLOCKS_ACROSS), Math.floorDiv(blockZ, SavedRegion.BLOCKS_ACROSS));
        return region.columnAt(Math.floorMod(blockX, SavedRegion.BLOCKS_ACROSS), Math.floorMod(blockZ, SavedRegion.BLOCKS_ACROSS));
    }

    private List<int[]> regionsByDistanceFromOrigin() {
        try (Stream<Path> files = Files.list(regionFolder)) {
            return files.map(file -> REGION_FILE.matcher(file.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(match -> new int[]{Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))})
                    .sorted(Comparator.comparingInt(regionXZ -> regionXZ[0] * regionXZ[0] + regionXZ[1] * regionXZ[1]))
                    .toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private SavedRegion region(int regionX, int regionZ) {
        long key = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
        Path file = regionFolder.resolve("r." + regionX + "." + regionZ + ".mca");
        FileTime written = lastWritten(file);
        OpenRegion open = regions.get(key, unused -> openRegion(file, written));
        if (!open.isCurrent(written)) {
            open = openRegion(file, written);
            regions.put(key, open);
        }
        return open.region();
    }

    private OpenRegion openRegion(Path file, FileTime written) {
        if (written == null) {
            return NOTHING_SAVED;
        }
        return new OpenRegion(SavedRegion.of(new RegionFile(file), ground), written);
    }

    private static FileTime lastWritten(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file) : null;
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static long heapShareCount() {
        long fitting = (long) (Runtime.getRuntime().maxMemory() * HEAP_SHARE_FOR_REGIONS) / BYTES_PER_REGION;
        return Math.max(FEWEST_REGIONS, fitting);
    }

    /** A region in memory, with when its file was written as it was read; no file means nothing was saved there. */
    private record OpenRegion(SavedRegion region, FileTime written) {
        boolean isCurrent(FileTime now) {
            return Objects.equals(written, now);
        }
    }

    /**
     * @param minY     the world's lowest block
     * @param seaLevel the top water block most water lies at, or null where the region holds no water
     */
    public record Start(int centerX, int centerZ, int minY, int lowestGround, int highestGround, Integer seaLevel) {
    }
}
