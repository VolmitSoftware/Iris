package art.arcane.iris.engine.terrain;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Terrain3DFragmentFilterTest {
    @Test
    public void removesAnEntire512BlockIslandAcrossChunkBoundaries() {
        Set<Voxel> blocks = new HashSet<>();
        for (int x = 12; x <= 19; x++) {
            for (int z = -20; z <= -13; z++) {
                for (int y = 24; y <= 31; y++) {
                    blocks.add(new Voxel(x, y, z));
                }
            }
        }
        Fixture fixture = new Fixture(blocks, true);

        for (Voxel block : blocks) {
            Terrain3DColumn column = fixture.filter.column(block.x(), block.z());
            assertFalse(column.isSolid(block.y()));
            assertEquals(8, column.topY());
            assertEquals(1, column.spanCount());
            assertTrue(column.isSolid(8));
        }
    }

    @Test
    public void preservesThinShelvesConnectedSidewaysAroundACorner() {
        Set<Voxel> blocks = new HashSet<>();
        for (int x = 0; x <= 15; x++) {
            blocks.add(new Voxel(x, 24, 0));
        }
        for (int z = 1; z <= 15; z++) {
            blocks.add(new Voxel(15, 24, z));
        }
        for (int y = 9; y < 24; y++) {
            blocks.add(new Voxel(15, y, 15));
        }
        Fixture fixture = new Fixture(blocks, true);

        for (Voxel block : blocks) {
            assertTrue(fixture.filter.column(block.x(), block.z()).isSolid(block.y()));
        }
        assertFalse(fixture.filter.column(0, 0).isSolid(23));
        assertEquals(24, fixture.filter.column(0, 0).surfaceY(24));
    }

    @Test
    public void aDiagonalContactDoesNotKeepAnOtherwiseDetachedBlock() {
        Set<Voxel> blocks = new HashSet<>();
        for (int y = 9; y <= 24; y++) {
            blocks.add(new Voxel(0, y, 0));
        }
        blocks.add(new Voxel(1, 24, 1));
        Fixture fixture = new Fixture(blocks, true);

        assertTrue(fixture.filter.column(0, 0).isSolid(24));
        assertFalse(fixture.filter.column(1, 1).isSolid(24));
    }

    @Test
    public void preservesAll513BlocksAfterAPartialComponentClassification() {
        Set<Voxel> blocks = new HashSet<>();
        for (int x = 0; x < 513; x++) {
            blocks.add(new Voxel(x, 24, 0));
        }
        Fixture fixture = new Fixture(blocks, true);

        assertTrue(fixture.filter.column(0, 0).isSolid(24));
        assertTrue(fixture.lookups.get() <= 1 + 4 * 512);
        for (int x = 512; x >= 0; x--) {
            assertTrue(fixture.filter.column(x, 0).isSolid(24));
        }
    }

    @Test
    public void aLargeVerticalIslandNeedsNoNeighbourSampling() {
        Set<Voxel> blocks = new HashSet<>();
        for (int y = 24; y < 537; y++) {
            blocks.add(new Voxel(0, y, 0));
        }
        Fixture fixture = new Fixture(blocks, true);

        assertEquals(536, fixture.filter.column(0, 0).topY());
        assertEquals(1, fixture.lookups.get());
    }

    @Test
    public void completeCacheEvictionAndConcurrentQueriesPreserveDecisions() throws Exception {
        Set<Voxel> blocks = new HashSet<>();
        for (int x = -18; x <= -15; x++) {
            for (int z = 14; z <= 17; z++) {
                for (int y = 24; y <= 27; y++) {
                    blocks.add(new Voxel(x, y, z));
                }
            }
        }
        for (int x = 14; x <= 526; x++) {
            blocks.add(new Voxel(x, 24, 16));
        }
        Fixture evicted = new Fixture(blocks, false);
        Fixture cached = new Fixture(blocks, true);
        int[] coordinates = {-18, -17, -16, -15, 14, 15, 16, 526};
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<Terrain3DColumn>> results = new ArrayList<>();
            for (int x : coordinates) {
                results.add(executor.submit(() -> evicted.filter.column(x, 16)));
            }
            for (int index = results.size() - 1; index >= 0; index--) {
                assertEquals(cached.filter.column(coordinates[index], 16),
                        results.get(index).get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void sharedConcurrentQueriesPublishWholeRemovedAndKeptComponents() throws Exception {
        Set<Voxel> blocks = new HashSet<>();
        for (int x = 12; x <= 19; x++) {
            for (int z = -20; z <= -13; z++) {
                for (int y = 24; y <= 31; y++) {
                    blocks.add(new Voxel(x, y, z));
                }
            }
        }
        for (int x = 32; x <= 544; x++) {
            blocks.add(new Voxel(x, 24, 16));
        }
        Fixture fixture = new Fixture(blocks, true);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Terrain3DColumn>> removed = new ArrayList<>();
            List<Future<Terrain3DColumn>> kept = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                int removedX = 12 + index % 8;
                int removedZ = -20 + index / 8;
                int keptX = 32 + index * 8;
                removed.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return fixture.filter.column(removedX, removedZ);
                }));
                kept.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return fixture.filter.column(keptX, 16);
                }));
            }
            start.countDown();
            for (int index = 63; index >= 0; index--) {
                assertEquals(8, removed.get(index).get(10, TimeUnit.SECONDS).topY());
                assertTrue(kept.get(index).get(10, TimeUnit.SECONDS).isSolid(24));
            }
        }
    }

    @Test
    public void spanFilteringMatchesAnIndependentVoxelComponentOracle() {
        Random random = new Random(74921);
        for (int sample = 0; sample < 18; sample++) {
            Set<Voxel> blocks = new HashSet<>();
            double probability = 0.12D + sample % 6 * 0.14D;
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    for (int y = 9; y <= 20; y++) {
                        if (random.nextDouble() < probability) {
                            blocks.add(new Voxel(x, y, z));
                        }
                    }
                }
            }
            Set<Voxel> retained = retainedBlocks(blocks);
            Fixture fixture = new Fixture(blocks, true);
            for (int x = 3; x >= -3; x--) {
                for (int z = -3; z <= 3; z++) {
                    Terrain3DColumn column = fixture.filter.column(x, z);
                    for (int y = 9; y <= 20; y++) {
                        Voxel block = new Voxel(x, y, z);
                        assertEquals("Sample " + sample + " at " + block,
                                retained.contains(block), column.isSolid(y));
                    }
                }
            }
        }
    }

    private static Set<Voxel> retainedBlocks(Set<Voxel> blocks) {
        Set<Voxel> remaining = new HashSet<>(blocks);
        Set<Voxel> retained = new HashSet<>();
        while (!remaining.isEmpty()) {
            Voxel start = remaining.iterator().next();
            remaining.remove(start);
            Set<Voxel> component = new HashSet<>();
            ArrayDeque<Voxel> pending = new ArrayDeque<>();
            pending.add(start);
            boolean grounded = false;
            while (!pending.isEmpty()) {
                Voxel block = pending.removeFirst();
                component.add(block);
                grounded |= block.y() == 9;
                for (Voxel neighbour : List.of(new Voxel(block.x() - 1, block.y(), block.z()),
                        new Voxel(block.x() + 1, block.y(), block.z()),
                        new Voxel(block.x(), block.y() - 1, block.z()),
                        new Voxel(block.x(), block.y() + 1, block.z()),
                        new Voxel(block.x(), block.y(), block.z() - 1),
                        new Voxel(block.x(), block.y(), block.z() + 1))) {
                    if (remaining.remove(neighbour)) {
                        pending.addLast(neighbour);
                    }
                }
            }
            if (grounded || component.size() > 512) {
                retained.addAll(component);
            }
        }
        return retained;
    }

    private record Voxel(int x, int y, int z) {
    }

    private static final class Fixture {
        private final Set<Voxel> blocks;
        private final boolean cache;
        private final int maximumY;
        private final Map<Long, Terrain3DFragmentFilter.DensityColumn> columns = new ConcurrentHashMap<>();
        private final AtomicInteger lookups = new AtomicInteger();
        private final Terrain3DFragmentFilter filter = new Terrain3DFragmentFilter(this::column);

        private Fixture(Set<Voxel> blocks, boolean cache) {
            this.blocks = Set.copyOf(blocks);
            this.cache = cache;
            int highest = 9;
            for (Voxel block : blocks) {
                highest = Math.max(highest, block.y());
            }
            maximumY = highest + 1;
        }

        private Terrain3DFragmentFilter.DensityColumn column(int x, int z) {
            lookups.incrementAndGet();
            long key = (long) x << 32 ^ z & 0xffffffffL;
            return cache ? columns.computeIfAbsent(key, ignored -> createColumn(x, z)) : createColumn(x, z);
        }

        private Terrain3DFragmentFilter.DensityColumn createColumn(int x, int z) {
            int[] boundaries = new int[maximumY * 2];
            boundaries[0] = 0;
            int count = 1;
            boolean previousSolid = true;
            for (int y = 9; y <= maximumY; y++) {
                boolean solid = blocks.contains(new Voxel(x, y, z));
                if (solid != previousSolid) {
                    boundaries[count++] = solid ? y : y - 1;
                    previousSolid = solid;
                }
            }
            return new Terrain3DFragmentFilter.DensityColumn(
                    new Terrain3DColumn(8, 9, true, Arrays.copyOf(boundaries, count)));
        }
    }
}
