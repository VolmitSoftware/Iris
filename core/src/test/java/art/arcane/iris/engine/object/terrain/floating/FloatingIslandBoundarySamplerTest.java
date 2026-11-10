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

package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.modules.floating.FloatingChildBiome;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.floating.TopShapeMode;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.host.ModuleLogs;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FloatingIslandBoundarySamplerTest {
    @Test
    public void edgeFade_remainsFullInsideOneParentBiome() {
        ResourceId parent = ResourceId.parse("ns:parent");
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> parent);

        assertEquals(1.0D, sampler.edgeFade(parent, 0, 0), 0.0D);
    }

    @Test
    public void edgeFade_roundsTowardParentBiomeBoundary() {
        ResourceId left = ResourceId.parse("ns:left");
        ResourceId right = ResourceId.parse("ns:right");
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> x < 0 ? left : right);

        assertEquals(FloatingIslandBoundarySampler.edgeFadeForDistance(1), sampler.edgeFade(right, 0, 0), 0.0D);
        assertEquals(FloatingIslandBoundarySampler.edgeFadeForDistance(2), sampler.edgeFade(right, 1, 0), 0.0D);
        assertEquals(1.0D, sampler.edgeFade(right, FloatingIslandBoundarySampler.EDGE_FADE_RADIUS - 1, 0), 0.0D);
    }

    @Test
    public void edgeFade_acceptsReloadedBiomeInstanceWithSameKey() {
        ResourceId expected = ResourceId.parse("ns:magnetics/mycelium");
        ResourceId reloaded = ResourceId.parse("ns:magnetics/mycelium");
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> reloaded);

        assertEquals(1.0D, sampler.edgeFade(expected, 0, 0), 0.0D);
    }

    @Test
    public void edgeFade_detectsOffAxisBoundaryWithinRadius() {
        ResourceId parent = ResourceId.parse("ns:parent");
        ResourceId neighbor = ResourceId.parse("ns:neighbor");
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> x == 2 && z == 1 ? neighbor : parent);

        assertEquals(FloatingIslandBoundarySampler.edgeFadeForDistance(2), sampler.edgeFade(parent, 0, 0), 0.0D);
    }

    @Test
    public void edgeFade_isSymmetricAcrossChunkBoundary() {
        ResourceId left = ResourceId.parse("ns:left");
        ResourceId right = ResourceId.parse("ns:right");
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> x < 16 ? left : right);

        assertEquals(sampler.edgeFade(left, 15, 0), sampler.edgeFade(right, 16, 0), 0.0D);
        assertEquals(sampler.edgeFade(left, 14, 0), sampler.edgeFade(right, 17, 0), 0.0D);
        assertEquals(sampler.edgeFade(left, 13, 0), sampler.edgeFade(right, 18, 0), 0.0D);
    }

    @Test
    public void parent_readsOnlyTheQueriedColumnWithoutBuildingTheBoundaryField() {
        ResourceId parent = ResourceId.parse("ns:parent");
        ResourceId other = ResourceId.parse("ns:other");
        AtomicInteger calls = new AtomicInteger();
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> {
            calls.incrementAndGet();
            return x == 12 && z == -9 ? parent : other;
        });

        assertSame(parent, sampler.parent(12, -9));
        assertSame(other, sampler.parent(13, -9));
        assertEquals(2, calls.get());
    }

    @Test
    public void edgeFade_boundsBiomeStreamReadsToChunkHalo() {
        ResourceId parent = ResourceId.parse("ns:parent");
        AtomicInteger calls = new AtomicInteger();
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> {
            calls.incrementAndGet();
            return parent;
        });

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                sampler.edgeFade(parent, x, z);
            }
        }

        int rawWidth = 16 + (FloatingIslandBoundarySampler.EDGE_FADE_RADIUS * 2) + 2;
        assertEquals(rawWidth * rawWidth, calls.get());
    }

    @Test
    public void footprintFade_usesDistanceForSaturatedHalfPlane() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return x >= 0 ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        int[] depths = new int[FloatingIslandBoundarySampler.EDGE_TAPER_WIDTH];

        assertFalse(sampler.footprint(footprint, 0, 0, 0.0D).accepted());
        for (int distance = 2; distance <= FloatingIslandBoundarySampler.EDGE_FADE_RADIUS; distance++) {
            FloatingIslandBoundarySampler.FootprintSample sample = sampler.footprint(footprint, distance - 1, 0, 0.0D);
            assertTrue(sample.accepted());
            assertEquals(FloatingIslandBoundarySampler.edgeFadeForDistance(distance), sample.edgeFade(), 0.0D);
            depths[distance - 2] = FloatingIslandSample.roundedEdgeDepth(20, 20, 0.0D, sample.edgeFade());
            FloatingIslandSample.NeighborSupport support = new FloatingIslandSample.NeighborSupport(sample.cardinalSupport(), sample.diagonalSupport());
            assertEquals(distance == FloatingIslandBoundarySampler.EDGE_FADE_RADIUS,
                    FloatingIslandSample.canCarveLaterally(sample.edgeFade(), support));
        }

        assertArrayEquals(new int[]{1, 2, 4, 7, 10, 13, 16, 18, 19, 20}, depths);
        for (int i = 1; i < depths.length; i++) {
            assertTrue(depths[i] >= depths[i - 1]);
            assertTrue(depths[i] - depths[i - 1] <= 3);
        }
    }

    @Test
    public void footprint_deepSingleCellPinholeRemainsSolid() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return x == 0.0D && z == 0.0D ? 0.0D : 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        FloatingIslandBoundarySampler.FootprintSample sample = sampler.footprint(footprint, 0, 0, 0.0D);

        assertTrue(sample.accepted());
        assertEquals(1.0D, sample.edgeFade(), 0.0D);
    }

    @Test
    public void footprint_repairsOneByTwoAndDiagonalStaticGaps() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler adjacent = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return z == 0.0D && (x == 0.0D || x == 1.0D) ? 0.0D : 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        NoiseSampler diagonal = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                boolean gap = x == 0.0D && z == 0.0D || x == 1.0D && z == 1.0D;
                return gap ? 0.0D : 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        assertTrue(sampler.footprint(adjacent, 0, 0, 0.0D).accepted());
        assertTrue(sampler.footprint(adjacent, 1, 0, 0.0D).accepted());
        assertTrue(sampler.footprint(diagonal, 0, 0, 0.0D).accepted());
        assertTrue(sampler.footprint(diagonal, 1, 1, 0.0D).accepted());
    }

    @Test
    public void footprint_broadOpeningRemainsOpen() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return Math.abs(x) <= 1.0D && Math.abs(z) <= 1.0D ? 0.0D : 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        assertFalse(sampler.footprint(footprint, 0, 0, 0.0D).accepted());
    }

    @Test
    public void footprint_buildsOneBoundedChunkHalo() {
        AtomicInteger calls = new AtomicInteger();
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                calls.incrementAndGet();
                return 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                sampler.footprint(footprint, x, z, 0.0D);
            }
        }

        int rawWidth = 16 + (FloatingIslandBoundarySampler.EDGE_FADE_RADIUS * 2) + 2;
        assertEquals(rawWidth * rawWidth, calls.get());
    }

    @Test
    public void footprintFade_isStableAcrossChunkAlignedFields() {
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return z >= 0.0D ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        FloatingIslandBoundarySampler forwardSampler = new FloatingIslandBoundarySampler((x, z) -> null);
        FloatingIslandBoundarySampler reverseSampler = new FloatingIslandBoundarySampler((x, z) -> null);

        for (int distance = 2; distance <= FloatingIslandBoundarySampler.EDGE_FADE_RADIUS; distance++) {
            double expected = FloatingIslandBoundarySampler.edgeFadeForDistance(distance);
            double forwardLeft = forwardSampler.footprint(footprint, 15, distance - 1, 0.0D).edgeFade();
            double forwardRight = forwardSampler.footprint(footprint, 16, distance - 1, 0.0D).edgeFade();
            double forwardNegative = forwardSampler.footprint(footprint, -1, distance - 1, 0.0D).edgeFade();
            double forwardOrigin = forwardSampler.footprint(footprint, 0, distance - 1, 0.0D).edgeFade();
            double reverseRight = reverseSampler.footprint(footprint, 16, distance - 1, 0.0D).edgeFade();
            double reverseLeft = reverseSampler.footprint(footprint, 15, distance - 1, 0.0D).edgeFade();
            assertEquals(expected, forwardLeft, 0.0D);
            assertEquals(expected, forwardRight, 0.0D);
            assertEquals(expected, forwardNegative, 0.0D);
            assertEquals(expected, forwardOrigin, 0.0D);
            assertEquals(forwardLeft, reverseLeft, 0.0D);
            assertEquals(forwardRight, reverseRight, 0.0D);
        }
    }

    @Test
    public void footprint_rejectsTwoColumnStaticFragment() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return z == 0.0D && (x == 0.0D || x == 1.0D) ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        assertFalse(sampler.footprint(footprint, 0, 0, 0.0D).accepted());
        assertFalse(sampler.footprint(footprint, 1, 0, 0.0D).accepted());
    }

    @Test
    public void footprint_rejectsComponentWithoutThreeCellDeepCore() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler threeByThree = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return Math.abs(x) <= 1.0D && Math.abs(z) <= 1.0D ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        NoiseSampler fourWideTendril = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return x >= 0.0D && x <= 3.0D ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        assertFalse(sampler.footprint(threeByThree, 0, 0, 0.0D).accepted());
        assertFalse(sampler.footprint(fourWideTendril, 1, 0, 0.0D).accepted());
        assertFalse(sampler.footprint(fourWideTendril, 2, 0, 0.0D).accepted());
    }

    @Test
    public void footprint_acceptsComponentWithThreeCellDeepCore() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return Math.abs(x) <= 2.0D && Math.abs(z) <= 2.0D ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        FloatingIslandBoundarySampler.FootprintSample sample = sampler.footprint(footprint, 0, 0, 0.0D);

        assertTrue(sample.accepted());
        assertEquals(FloatingIslandBoundarySampler.edgeFadeForDistance(3), sample.edgeFade(), 0.0D);
    }

    @Test
    public void footprint_trimsAttachedThinArmWithoutChunkSeam() {
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                boolean arm = Math.abs(z) <= 1.0D;
                boolean core = x >= -10.0D && x <= -6.0D && Math.abs(z) <= 2.0D;
                return arm || core ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };

        assertTrue(sampler.footprint(footprint, -8, 0, 0.0D).accepted());
        assertFalse(sampler.footprint(footprint, 15, 0, 0.0D).accepted());
        assertFalse(sampler.footprint(footprint, 16, 0, 0.0D).accepted());
    }

    @Test
    public void ownershipFade_tapersAcrossPickerBoundary() {
        ResolvedFloatingChild left = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        ResolvedFloatingChild right = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        KList<ResolvedFloatingChild> entries = new KList<>();
        entries.add(left);
        entries.add(right);
        NoiseSampler picker = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return x < 0.0D ? 0.0D : 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);

        assertSame(right, sampler.ownership(entries, picker, 0, 0).owner());
        assertEquals(0.0D, sampler.ownership(entries, picker, 0, 0).edgeFade(), 0.0D);
        for (int distance = 2; distance <= FloatingIslandBoundarySampler.EDGE_FADE_RADIUS; distance++) {
            FloatingIslandBoundarySampler.OwnershipSample sample = sampler.ownership(entries, picker, distance - 1, 0);
            assertSame(right, sample.owner());
            assertEquals(FloatingIslandBoundarySampler.edgeFadeForDistance(distance), sample.edgeFade(), 0.0D);
        }
    }

    @Test
    public void ownershipFade_rejectsThinPickerRegion() {
        ResolvedFloatingChild left = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        ResolvedFloatingChild right = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        KList<ResolvedFloatingChild> entries = new KList<>();
        entries.add(left);
        entries.add(right);
        NoiseSampler picker = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return Math.abs(x) <= 1.0D ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> null);

        FloatingIslandBoundarySampler.OwnershipSample sample = sampler.ownership(entries, picker, 0, 0);

        assertSame(right, sample.owner());
        assertEquals(0.0D, sample.edgeFade(), 0.0D);
    }

    @Test
    public void labelFieldsTrimAttachedThinArmWithoutChunkSeam() {
        ResourceId leftParent = ResourceId.parse("ns:left");
        ResourceId rightParent = ResourceId.parse("ns:right");
        ResolvedFloatingChild leftChild = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        ResolvedFloatingChild rightChild = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        KList<ResolvedFloatingChild> entries = new KList<>();
        entries.add(leftChild);
        entries.add(rightChild);
        NoiseSampler picker = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return isAttachedArm(x, z) ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler(
                (x, z) -> isAttachedArm(x, z) ? rightParent : leftParent);

        assertTrue(sampler.edgeFade(rightParent, -8, 0) > 0.0D);
        assertEquals(0.0D, sampler.edgeFade(rightParent, 15, 0), 0.0D);
        assertEquals(0.0D, sampler.edgeFade(rightParent, 16, 0), 0.0D);
        assertSame(rightChild, sampler.ownership(entries, picker, 15, 0).owner());
        assertSame(rightChild, sampler.ownership(entries, picker, 16, 0).owner());
        assertEquals(0.0D, sampler.ownership(entries, picker, 15, 0).edgeFade(), 0.0D);
        assertEquals(0.0D, sampler.ownership(entries, picker, 16, 0).edgeFade(), 0.0D);
    }

    @Test
    public void sampler_fieldsPublishSafelyAcrossConcurrentReaders() throws Exception {
        ResourceId parent = ResourceId.parse("ns:parent");
        ResolvedFloatingChild left = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        ResolvedFloatingChild right = new ResolvedFloatingChild(new FloatingChildBiome(), null);
        KList<ResolvedFloatingChild> entries = new KList<>();
        entries.add(left);
        entries.add(right);
        NoiseSampler constant = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return 1.0D;
            }
        };
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> parent);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        KList<Future<Boolean>> futures = new KList<>();
        try {
            for (int task = 0; task < 64; task++) {
                int x = (task % 4) * 16;
                futures.add(executor.submit(() -> sampler.parent(x, 0) == parent
                        && sampler.edgeFade(parent, x, 0) == 1.0D
                        && sampler.footprint(constant, x, 0, 0.0D).accepted()
                        && sampler.footprint(constant, x, 0, 0.0D).edgeFade() == 1.0D
                        && sampler.ownership(entries, constant, x, 0).owner() == right
                        && sampler.ownership(entries, constant, x, 0).edgeFade() == 1.0D));
            }
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void sample_appliesSpatialProfileToFinalWorldCoordinates() {
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return x >= 0.0D ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        NoiseSampler constant = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return 1.0D;
            }
        };
        ResolvedFloatingChild entry = mock(ResolvedFloatingChild.class);
        ResourceId parent = ResourceId.parse("ns:parent");
        KList<ResolvedFloatingChild> entries = new KList<>();
        entries.add(entry);
        FloatingWorld world = floatingWorld(parent, false, 31L);
        when(entry.footprintNoise()).thenReturn(footprint);
        when(entry.altitudeNoise()).thenReturn(constant);
        when(entry.realBiome(parent)).thenReturn(parent);
        when(entry.topShapeNoise()).thenReturn(constant);
        when(entry.bottomNoise()).thenReturn(constant);
        when(entry.wallWarpNoise()).thenReturn(null);
        when(entry.carvingProfileSampler()).thenReturn(null);
        when(entry.hasCarvingReference()).thenReturn(false);
        when(entry.carveNoise()).thenReturn(constant);
        when(entry.settings()).thenReturn(TestSettings.with(new FloatingChildBiome(), "footprintThreshold", 0.5D, "minHeightAboveSurface", 160, "maxHeightAboveSurface", 160, "topShapeMode", TopShapeMode.NOISE, "topShapeAmp", 1.0D, "maxTopHeight", 18, "bottomExponent", 1.0D, "bottomDepthMin", 20, "bottomDepthMax", 20, "maxThickness", 64, "wallWarpAmplitude", 0.0D, "carveThreshold", 0.5D));
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> parent);
        int[] expectedBottom = {159, 158, 156, 153, 150, 147, 144, 142, 141, 140};
        int[] expectedTop = {161, 162, 164, 166, 169, 172, 174, 176, 177, 178};

        assertNull(FloatingIslandSample.sample(parent, entries, 0, 0, 320, world, sampler, ModuleLogs.discarding()));
        for (int i = 0; i < expectedBottom.length; i++) {
            FloatingIslandSample sample = FloatingIslandSample.sample(parent, entries, i + 1, 0, 320, world, sampler, ModuleLogs.discarding());
            assertNotNull("distance " + (i + 2) + " rejected with code " + FloatingIslandSample.getLastReject(), sample);
            assertEquals(expectedBottom[i], sample.bottomY());
            assertEquals(expectedTop[i], sample.topY());
            if (i < expectedBottom.length - 1) {
                assertEquals(sample.thickness, sample.solidCount);
            } else {
                assertTrue(sample.solidCount < sample.thickness);
            }
            if (i > 0) {
                assertTrue(Math.abs(expectedBottom[i] - expectedBottom[i - 1]) <= 3);
                assertTrue(Math.abs(expectedTop[i] - expectedTop[i - 1]) <= 3);
            }
        }
    }

    @Test
    public void sample_mergesNoiseTopEntriesAcrossSpatialRim() {
        NoiseSampler footprint = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return x >= 0.0D ? 1.0D : 0.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return sample(x, z);
            }
        };
        NoiseSampler constant = new NoiseSampler() {
            @Override
            public double sample(double x, double z) {
                return 1.0D;
            }

            @Override
            public double sample(double x, double y, double z) {
                return 1.0D;
            }
        };
        ResolvedFloatingChild lower = mock(ResolvedFloatingChild.class);
        ResolvedFloatingChild upper = mock(ResolvedFloatingChild.class);
        ResourceId parent = ResourceId.parse("ns:parent");
        KList<ResolvedFloatingChild> entries = new KList<>();
        entries.add(lower);
        entries.add(upper);
        FloatingWorld world = floatingWorld(parent, true, 35L);
        for (ResolvedFloatingChild entry : entries) {
            when(entry.footprintNoise()).thenReturn(footprint);
            when(entry.altitudeNoise()).thenReturn(constant);
            when(entry.realBiome(parent)).thenReturn(parent);
            when(entry.topShapeNoise()).thenReturn(constant);
            when(entry.bottomNoise()).thenReturn(constant);
            when(entry.wallWarpNoise()).thenReturn(null);
            when(entry.carvingProfileSampler()).thenReturn(null);
            when(entry.hasCarvingReference()).thenReturn(false);
            when(entry.carveNoise()).thenReturn(null);
            when(entry.settings()).thenReturn(TestSettings.with(new FloatingChildBiome(), "footprintThreshold", 0.5D, "topShapeMode", TopShapeMode.NOISE, "topShapeAmp", 1.0D, "maxTopHeight", 18, "bottomExponent", 1.0D, "bottomDepthMin", 20, "bottomDepthMax", 20, "maxThickness", 64, "wallWarpAmplitude", 0.0D, "carveThreshold", 1.0D,
                    "minHeightAboveSurface", entry == lower ? 160 : 180, "maxHeightAboveSurface", entry == lower ? 160 : 180));
        }
        FloatingIslandBoundarySampler sampler = new FloatingIslandBoundarySampler((x, z) -> parent);

        assertNull(FloatingIslandSample.sample(parent, entries, 0, 0, 320, world, sampler, ModuleLogs.discarding()));
        FloatingIslandSample sample = FloatingIslandSample.sample(parent, entries, 1, 0, 320, world, sampler, ModuleLogs.discarding());

        assertNotNull(sample);
        assertTrue(sample.hasMergedEntries());
        assertEquals(159, sample.bottomY());
        assertEquals(181, sample.topY());
        assertEquals(23, sample.thickness);
        assertEquals(6, sample.solidCount);
        assertSame(upper, sample.entryAt(sample.topIdx));
    }

    private static FloatingWorld floatingWorld(ResourceId parent, boolean merges, long baseSeed) {
        FloatingWorld world = mock(FloatingWorld.class);
        when(world.merges(parent)).thenReturn(merges);
        when(world.baseSeed()).thenReturn(baseSeed);
        when(world.minWorldY()).thenReturn(0);
        return world;
    }

    private static boolean isAttachedArm(double x, double z) {
        return Math.abs(z) <= 1.0D || x >= -10.0D && x <= -6.0D && Math.abs(z) <= 2.0D;
    }
}
