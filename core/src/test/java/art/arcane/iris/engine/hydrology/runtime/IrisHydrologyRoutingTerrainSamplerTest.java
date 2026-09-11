package art.arcane.iris.engine.hydrology.runtime;

import art.arcane.iris.engine.hydrology.HydrologyRoutingTerrainSampler;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.util.common.parallel.MultiBurst;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisHydrologyRoutingTerrainSamplerTest {
    @Test
    public void guardedGridSamplesOneAlignedBasisAtEachCoordinate() {
        int[] spacings = new int[]{64, 128, 256};
        for (int spacing : spacings) {
            int minimumX = -4096;
            int minimumZ = -2048;
            int width = 4096 / spacing + 1;
            AtomicInteger basisCalls = new AtomicInteger();
            AtomicInteger heightCalls = new AtomicInteger();
            Map<Long, Integer> coordinateCalls = new HashMap<>();
            IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                    new IrisHydrologyRoutingTerrainSampler.Sources(
                            (int x, int z, double naturalHeight) -> {
                                basisCalls.incrementAndGet();
                                return basis(x, z, naturalHeight);
                            },
                            (int x, int z) -> {
                                assertEquals(0, Math.floorMod(x - minimumX, spacing));
                                assertEquals(0, Math.floorMod(z - minimumZ, spacing));
                                heightCalls.incrementAndGet();
                                coordinateCalls.merge(pack(x, z), 1, Integer::sum);
                                return height(x, z);
                            },
                            (int x, int z) -> false,
                            128
                    ),
                    IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(65_536)
            );

            HydrologyTerrainSample[] samples = sampler.sampleGrid(new HydrologyRoutingTerrainSampler.GridRequest(
                    minimumX,
                    minimumZ,
                    width,
                    spacing
            ));

            assertEquals(width * width, samples.length);
            assertEquals((width + 1) * (width + 1), basisCalls.get());
            assertEquals(basisCalls.get(), heightCalls.get());
            assertEquals(heightCalls.get(), coordinateCalls.size());
            for (int count : coordinateCalls.values()) {
                assertEquals(1, count);
            }
        }
    }

    @Test
    public void guardedForwardSlopeMatchesRawBitsAcrossNegativeBoundaries() {
        int minimumX = -129;
        int minimumZ = -257;
        int width = 3;
        int spacing = 64;
        IrisHydrologyRoutingTerrainSampler sampler = sampler(64);

        HydrologyTerrainSample[] samples = sampler.sampleGrid(
                new HydrologyRoutingTerrainSampler.GridRequest(minimumX, minimumZ, width, spacing)
        );

        for (int gridZ = 0; gridZ < width; gridZ++) {
            int z = minimumZ + gridZ * spacing;
            for (int gridX = 0; gridX < width; gridX++) {
                int x = minimumX + gridX * spacing;
                double scale = 3D / spacing;
                double deltaX = (height(x + spacing, z) - height(x, z)) * scale;
                double deltaZ = (height(x, z + spacing) - height(x, z)) * scale;
                double expected = StrictMath.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                double actual = samples[gridZ * width + gridX].slope();
                assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
            }
        }
    }

    @Test
    public void pointBasisSamplesCarryTheForwardSlope() {
        IrisHydrologyRoutingTerrainSampler sampler = sampler(64);
        int[][] points = {{0, 0}, {-129, -257}, {37, 1024}};

        for (int[] point : points) {
            int x = point[0];
            int z = point[1];
            double expected = IrisHydrologyRoutingTerrainSampler.localSlope(
                    height(x, z), height(x + 3, z), height(x, z + 3));

            double actual = sampler.sampleBasis(x, z).slope();

            assertTrue(expected > 0D);
            assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
        }
    }

    @Test
    public void slopeFreePointBasisAvoidsNeighbourHeightSamples() {
        AtomicInteger heightCalls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> basis(x, z, naturalHeight),
                        (int x, int z) -> {
                            heightCalls.incrementAndGet();
                            return height(x, z);
                        },
                        (int x, int z) -> false,
                        128
                ),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(64)
        );

        HydrologyTerrainSample slopeFree = sampler.sampleBasisWithoutSlope(4, 8);
        assertEquals(1, heightCalls.get());

        HydrologyTerrainSample sloped = sampler.sampleBasis(4, 8);
        assertEquals(3, heightCalls.get());
        assertTrue(sloped.slope() > 0D);
        assertEquals(sloped, slopeFree.withSlope(sloped.slope()));
    }

    @Test
    public void adjacentGridsShareBasesAndProduceIdenticalOverlap() {
        AtomicInteger calls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> basis(x, z, naturalHeight),
                        (int x, int z) -> {
                            calls.incrementAndGet();
                            return height(x, z);
                        },
                        (int x, int z) -> false,
                        128
                    ),
                    IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(64)
            );

        HydrologyTerrainSample[] first = sampler.sampleGrid(
                new HydrologyRoutingTerrainSampler.GridRequest(-128, -128, 3, 64)
        );
        assertEquals(16, calls.get());
        HydrologyTerrainSample[] second = sampler.sampleGrid(
                new HydrologyRoutingTerrainSampler.GridRequest(0, -128, 3, 64)
        );

        assertEquals(24, calls.get());
        for (int gridZ = 0; gridZ < 3; gridZ++) {
            assertEquals(first[gridZ * 3 + 2], second[gridZ * 3]);
        }
    }

    @Test
    public void serialGridKeepsRowMajorProviderCallOrder() {
        List<String> calls = new ArrayList<>();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> {
                            calls.add("basis:" + x + "," + z);
                            return basis(x, z, naturalHeight);
                        },
                        (int x, int z) -> {
                            calls.add("height:" + x + "," + z);
                            return height(x, z);
                        },
                        (int x, int z) -> false,
                        128
                ),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(64)
        );

        sampler.sampleGrid(new HydrologyRoutingTerrainSampler.GridRequest(-16, 32, 2, 16));

        assertEquals(
                List.of(
                        "height:-16,32", "basis:-16,32",
                        "height:0,32", "basis:0,32",
                        "height:16,32", "basis:16,32",
                        "height:-16,48", "basis:-16,48",
                        "height:0,48", "basis:0,48",
                        "height:16,48", "basis:16,48",
                        "height:-16,64", "basis:-16,64",
                        "height:0,64", "basis:0,64",
                        "height:16,64", "basis:16,64"
                ),
                calls
        );
    }

    @Test
    public void classifierUsesBasisAndCachesExactUnalignedResults() {
        AtomicInteger classifierCalls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> oceanBasis(x, z, naturalHeight),
                        (int x, int z) -> height(x, z),
                        (int x, int z) -> {
                            classifierCalls.incrementAndGet();
                            return x >= 0;
                        },
                        128
                    ),
                    IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(16)
            );
        sampler.sampleGrid(new HydrologyRoutingTerrainSampler.GridRequest(0, 0, 1, 64));

        assertEquals(
                HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN,
                sampler.classifyNatural(0, 0)
        );
        assertEquals(0, classifierCalls.get());
        assertEquals(
                HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN,
                sampler.classifyNatural(1, -1)
        );
        assertEquals(
                HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN,
                sampler.classifyNatural(1, -1)
        );
        assertEquals(1, classifierCalls.get());
    }

    @Test
    public void detailedSlopeKeepsExactPositiveThreeStencil() {
        Map<Long, Integer> calls = new HashMap<>();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> basis(x, z, naturalHeight),
                        (int x, int z) -> {
                            calls.merge(pack(x, z), 1, Integer::sum);
                            return height(x, z);
                        },
                        (int x, int z) -> false,
                        128
                    ),
                    IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(16)
            );
        int x = -65;
        int z = -127;
        double naturalHeight = sampler.basis(x, z).naturalHeight();

        double actual = sampler.localSlope(x, z, naturalHeight);
        double deltaX = height(x + 3, z) - naturalHeight;
        double deltaZ = height(x, z + 3) - naturalHeight;
        double expected = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
        assertEquals(Map.of(pack(x, z), 1, pack(x + 3, z), 1, pack(x, z + 3), 1), calls);
        sampler.basis(x + 3, z);
        sampler.localSlope(x + 3, z, height(x + 3, z));
        assertEquals(1, calls.get(pack(x + 3, z)).intValue());
    }

    @Test
    public void denseDetailedStencilsShareRawNaturalHeights() {
        AtomicInteger basisCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> {
                            basisCalls.incrementAndGet();
                            return basis(x, z, naturalHeight);
                        },
                        (int x, int z) -> {
                            heightCalls.incrementAndGet();
                            return height(x, z);
                        },
                        (int x, int z) -> false,
                        128
                    ),
                    IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(64)
            );

        for (int z = 0; z <= 6; z += 3) {
            for (int x = 0; x <= 6; x += 3) {
                IrisHydrologyRoutingTerrainSampler.TerrainBasis basis = sampler.basis(x, z);
                double actual = sampler.localSlope(x, z, basis.naturalHeight());
                double deltaX = height(x + 3, z) - height(x, z);
                double deltaZ = height(x, z + 3) - height(x, z);
                double expected = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
            }
        }

        assertEquals(9, basisCalls.get());
        assertEquals(15, heightCalls.get());
    }

    @Test
    public void cachesRemainBoundedAndCloseClearsThem() {
        IrisHydrologyRoutingTerrainSampler sampler = sampler(3);

        sampler.sampleGrid(new HydrologyRoutingTerrainSampler.GridRequest(0, 0, 1, 64));
        for (int coordinate = 1; coordinate <= 5; coordinate++) {
            sampler.classifyNatural(coordinate, -coordinate);
        }

        assertEquals(3, sampler.basisCacheSize());
        assertEquals(3, sampler.naturalHeightCacheSize());
        assertEquals(3, sampler.oceanClassificationCacheSize());
        sampler.close();
        assertEquals(0, sampler.basisCacheSize());
        assertEquals(0, sampler.naturalHeightCacheSize());
        assertEquals(0, sampler.oceanClassificationCacheSize());
    }

    @Test
    public void primitiveCachesPreserveAccessOrderedEviction() {
        Map<Long, Integer> basisCalls = new HashMap<>();
        Map<Long, Integer> heightCalls = new HashMap<>();
        Map<Long, Integer> classifierCalls = new HashMap<>();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> {
                            basisCalls.merge(pack(x, z), 1, Integer::sum);
                            return basis(x, z, naturalHeight);
                        },
                        (int x, int z) -> {
                            heightCalls.merge(pack(x, z), 1, Integer::sum);
                            return height(x, z);
                        },
                        (int x, int z) -> {
                            classifierCalls.merge(pack(x, z), 1, Integer::sum);
                            return false;
                        },
                        128
                ),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(2)
        );

        sampler.basis(0, 0);
        sampler.basis(1, 1);
        sampler.basis(0, 0);
        sampler.basis(2, 2);
        sampler.basis(1, 1);

        assertEquals(1, basisCalls.get(pack(0, 0)).intValue());
        assertEquals(2, basisCalls.get(pack(1, 1)).intValue());
        assertEquals(1, basisCalls.get(pack(2, 2)).intValue());
        assertEquals(1, heightCalls.get(pack(0, 0)).intValue());
        assertEquals(1, heightCalls.get(pack(1, 1)).intValue());
        assertEquals(1, heightCalls.get(pack(2, 2)).intValue());

        sampler.classifyNatural(10, 10);
        sampler.classifyNatural(11, 11);
        sampler.classifyNatural(10, 10);
        sampler.classifyNatural(12, 12);
        sampler.classifyNatural(11, 11);

        assertEquals(1, classifierCalls.get(pack(10, 10)).intValue());
        assertEquals(2, classifierCalls.get(pack(11, 11)).intValue());
        assertEquals(1, classifierCalls.get(pack(12, 12)).intValue());
    }

    @Test
    public void primitiveHeightCacheNeverRetainsNanValues() {
        AtomicInteger heightCalls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> basis(x, z, naturalHeight),
                        (int x, int z) -> {
                            heightCalls.incrementAndGet();
                            return Double.NaN;
                        },
                        (int x, int z) -> false,
                        128
                ),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(16)
        );

        assertTrue(Double.isNaN(sampler.localSlope(0, 0, 0D)));
        assertTrue(Double.isNaN(sampler.localSlope(0, 0, 0D)));
        // Two neighbours per slope, sampled again on the second call because a NaN is never memoized.
        assertEquals(4, heightCalls.get());
    }

    @Test
    public void invalidBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> sampler(0));
        IrisHydrologyRoutingTerrainSampler sampler = sampler(16);
        assertThrows(
                IllegalArgumentException.class,
                () -> new HydrologyRoutingTerrainSampler.GridRequest(0, 0, 0, 64)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new HydrologyRoutingTerrainSampler.GridRequest(0, 0, 1, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> IrisHydrologyRoutingTerrainSampler.routingSlope(1D, 2D, 3D, 0)
        );
    }

    @Test
    public void parallelGridMatchesSerialRawBitsAndSamplesEachProviderOnce() throws Exception {
        int minimumX = -2048;
        int minimumZ = 4096;
        int width = 33;
        int spacing = 64;
        int expectedCalls = (width + 1) * (width + 1);
        AtomicInteger serialBasisCalls = new AtomicInteger();
        AtomicInteger serialHeightCalls = new AtomicInteger();
        AtomicInteger parallelBasisCalls = new AtomicInteger();
        AtomicInteger parallelHeightCalls = new AtomicInteger();
        AtomicInteger submissions = new AtomicInteger();
        Map<Long, Integer> serialCoordinateCalls = new HashMap<>();
        Map<Long, Integer> parallelCoordinateCalls = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Executor recordingExecutor = (Runnable command) -> {
            submissions.incrementAndGet();
            executor.execute(command);
        };
        try {
            IrisHydrologyRoutingTerrainSampler serial = countedSampler(
                    65_536,
                    1,
                    Runnable::run,
                    serialBasisCalls,
                    serialHeightCalls,
                    serialCoordinateCalls
            );
            IrisHydrologyRoutingTerrainSampler parallel = countedSampler(
                    65_536,
                    4,
                    recordingExecutor,
                    parallelBasisCalls,
                    parallelHeightCalls,
                    parallelCoordinateCalls
            );
            HydrologyRoutingTerrainSampler.GridRequest request = new HydrologyRoutingTerrainSampler.GridRequest(
                    minimumX,
                    minimumZ,
                    width,
                    spacing
            );

            HydrologyTerrainSample[] serialSamples = serial.sampleGrid(request);
            HydrologyTerrainSample[] parallelSamples = parallel.sampleGrid(request);

            assertEquals(serialSamples.length, parallelSamples.length);
            for (int index = 0; index < serialSamples.length; index++) {
                assertSampleRawBits(serialSamples[index], parallelSamples[index]);
            }
            assertEquals(expectedCalls, serialBasisCalls.get());
            assertEquals(expectedCalls, serialHeightCalls.get());
            assertEquals(expectedCalls, parallelBasisCalls.get());
            assertEquals(expectedCalls, parallelHeightCalls.get());
            assertCoordinateCalls(serialCoordinateCalls, expectedCalls);
            assertCoordinateCalls(parallelCoordinateCalls, expectedCalls);
            assertTrue(submissions.get() > 1);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void burstWorkerForksRowsIntoItsOwnPoolInsteadOfTheExecutor() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        MultiBurst burst = new MultiBurst("Iris Hydrology Test", Thread.NORM_PRIORITY, () -> 2);
        Executor recordingExecutor = (Runnable command) -> {
            submissions.incrementAndGet();
            burst.execute(command);
        };
        try {
            IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                    new IrisHydrologyRoutingTerrainSampler.Sources(
                            (int x, int z, double naturalHeight) -> basis(x, z, naturalHeight),
                            (int x, int z) -> {
                                heightCalls.incrementAndGet();
                                return height(x, z);
                            },
                            (int x, int z) -> false,
                            128
                    ),
                    new IrisHydrologyRoutingTerrainSampler.SamplingOptions(
                            65_536,
                            4,
                            recordingExecutor
                    )
            );
            int width = 21;

            Future<?> completion = burst.submit(
                    () -> sampler.sampleGrid(new HydrologyRoutingTerrainSampler.GridRequest(0, 0, width, 64))
            );
            completion.get(5L, TimeUnit.SECONDS);

            // Handing rows to another pool from a pool worker is how saturated pools deadlock each other.
            assertEquals(0, submissions.get());
            assertEquals((width + 1) * (width + 1), heightCalls.get());
        } finally {
            burst.close();
        }
    }

    @Test
    public void parallelGridPropagatesProviderFailure() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                    new IrisHydrologyRoutingTerrainSampler.Sources(
                            (int x, int z, double naturalHeight) -> basis(x, z, naturalHeight),
                            (int x, int z) -> {
                                if (x == 0 && z == 0) {
                                    throw new IllegalStateException("expected sampling failure");
                                }
                                return height(x, z);
                            },
                            (int x, int z) -> false,
                            128
                    ),
                    new IrisHydrologyRoutingTerrainSampler.SamplingOptions(
                            65_536,
                            2,
                            executor
                    )
            );

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> sampler.sampleGrid(new HydrologyRoutingTerrainSampler.GridRequest(0, 0, 33, 64))
            );

            assertEquals("expected sampling failure", failure.getMessage());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    private IrisHydrologyRoutingTerrainSampler sampler(int maximumEntries) {
        return new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> basis(x, z, naturalHeight),
                        (int x, int z) -> height(x, z),
                        (int x, int z) -> x >= 1,
                        128
                ),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(maximumEntries)
        );
    }

    private IrisHydrologyRoutingTerrainSampler countedSampler(
            int maximumEntries,
            int maximumWorkers,
            Executor executor,
            AtomicInteger basisCalls,
            AtomicInteger heightCalls,
            Map<Long, Integer> coordinateCalls
    ) {
        return new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> {
                            basisCalls.incrementAndGet();
                            return basis(x, z, naturalHeight);
                        },
                        (int x, int z) -> {
                            heightCalls.incrementAndGet();
                            coordinateCalls.merge(pack(x, z), 1, Integer::sum);
                            return height(x, z);
                        },
                        (int x, int z) -> false,
                        128
                ),
                new IrisHydrologyRoutingTerrainSampler.SamplingOptions(
                        maximumEntries,
                        maximumWorkers,
                        executor
                )
        );
    }

    private void assertCoordinateCalls(Map<Long, Integer> coordinateCalls, int expectedCalls) {
        assertEquals(expectedCalls, coordinateCalls.size());
        for (int count : coordinateCalls.values()) {
            assertEquals(1, count);
        }
    }

    private void assertSampleRawBits(HydrologyTerrainSample expected, HydrologyTerrainSample actual) {
        assertEquals(expected, actual);
        assertEquals(Double.doubleToRawLongBits(expected.slope()), Double.doubleToRawLongBits(actual.slope()));
        assertEquals(Double.doubleToRawLongBits(expected.routingCost()), Double.doubleToRawLongBits(actual.routingCost()));
        assertEquals(
                Double.doubleToRawLongBits(expected.surfaceSourceWeight()),
                Double.doubleToRawLongBits(actual.surfaceSourceWeight())
        );
        assertEquals(
                Double.doubleToRawLongBits(expected.undergroundSourceWeight()),
                Double.doubleToRawLongBits(actual.undergroundSourceWeight())
        );
        assertEquals(
                Double.doubleToRawLongBits(expected.widthMultiplier()),
                Double.doubleToRawLongBits(actual.widthMultiplier())
        );
        assertEquals(
                Double.doubleToRawLongBits(expected.depthMultiplier()),
                Double.doubleToRawLongBits(actual.depthMultiplier())
        );
        assertEquals(
                Double.doubleToRawLongBits(expected.incisionMultiplier()),
                Double.doubleToRawLongBits(actual.incisionMultiplier())
        );
        assertEquals(
                Double.doubleToRawLongBits(expected.routingMultiplier()),
                Double.doubleToRawLongBits(actual.routingMultiplier())
        );
    }

    private IrisHydrologyRoutingTerrainSampler.TerrainBasis basis(int x, int z, double height) {
        return new IrisHydrologyRoutingTerrainSampler.TerrainBasis(
                height,
                HydrologyTerrainSample.openLand((int) StrictMath.round(height), 0D, "parent")
        );
    }

    private IrisHydrologyRoutingTerrainSampler.TerrainBasis oceanBasis(int x, int z, double height) {
        return new IrisHydrologyRoutingTerrainSampler.TerrainBasis(
                height,
                HydrologyTerrainSample.ocean((int) StrictMath.round(height), "parent")
        );
    }

    private double height(int x, int z) {
        return x * 0.125D + z * 0.0625D + Math.floorMod(x * 31 + z * 17, 11) * 0.03125D;
    }

    private long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    @Test
    public void nonFiniteNaturalHeightIsNotCachedSoTheNextSampleCanRecover() {
        AtomicInteger originCalls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (int x, int z, double naturalHeight) -> new IrisHydrologyRoutingTerrainSampler.TerrainBasis(
                                naturalHeight,
                                HydrologyTerrainSample.openLand((int) Math.round(naturalHeight), 0D, "parent")
                        ),
                        (int x, int z) -> {
                            if (x == 4 && z == 4) {
                                return originCalls.incrementAndGet() == 1 ? Double.NaN : 90D;
                            }
                            return 90D;
                        },
                        (int x, int z) -> false,
                        128
                ),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(64)
        );

        assertThrows(IllegalArgumentException.class, () -> sampler.sampleBasis(4, 4));

        assertEquals(90, sampler.sampleBasis(4, 4).naturalHeight());
        assertEquals(2, originCalls.get());
    }
}
