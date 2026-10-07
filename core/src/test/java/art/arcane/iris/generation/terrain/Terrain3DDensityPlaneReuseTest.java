package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Terrain3DDensityPlaneReuseTest {
    @Test
    public void densityPlanesPreservePointwiseOccupancyAcrossPartialBandsAndNegativeCoordinates() throws Exception {
        for (int height : new int[]{2, 3, 5, 63, 64, 256, 257, 4096}) {
            double baseHeight = Math.min(96.375D, height - 2.625D);
            for (double fluid : new double[]{0D, 48.25D, baseHeight + 2D}) {
                IrisTerrain3D profile = profile();
                IrisBiome biome = new IrisBiome().setTerrain3D(profile);
                Terrain3DRuntime runtime = new Terrain3DRuntime(
                        new Terrain3DRuntime.Sources((x, z) -> baseHeight, (x, z) -> biome),
                        new Terrain3DRuntime.Options(328473L, height, fluid, null, true, 4096),
                        (style, seed) -> style == profile.getDensityStyle()
                                ? Terrain3DDensityPlaneReuseTest::density : Terrain3DDensityPlaneReuseTest::cracks);
                Method createColumn = Terrain3DRuntime.class.getDeclaredMethod("createColumn", int.class, int.class);
                createColumn.setAccessible(true);
                for (int x : new int[]{-9, -8, -7, -1, 0, 3}) {
                    for (int z : new int[]{-7, -4, -1, 2}) {
                        Terrain3DColumn actual = (Terrain3DColumn) createColumn.invoke(runtime, x, z);
                        double strength = smooth((baseHeight - fluid) / 24D);
                        if (strength == 0D) {
                            assertEquals(Terrain3DColumn.unshaped(baseHeight, height), actual);
                            continue;
                        }
                        int minimum = Math.max(Math.max(1, (int) Math.floor(fluid) + 1),
                                (int) Math.floor(baseHeight - (33.5D * strength + 24.5D * strength)));
                        int maximum = Math.min(height - 1, (int) Math.ceil(baseHeight + 33.5D * strength));
                        if (minimum > maximum) {
                            assertEquals(Terrain3DColumn.unshaped(baseHeight, height), actual);
                            continue;
                        }
                        assertEquals(minimum, actual.minY());
                        assertEquals(Double.doubleToRawLongBits(baseHeight), Double.doubleToRawLongBits(actual.baseHeight()));
                        for (int y = 0; y < height; y++) {
                            boolean expected = y < minimum || y <= maximum
                                    && referenceSolid(x, y, z, baseHeight, fluid, strength);
                            assertEquals("at " + x + "," + y + "," + z + " height=" + height + " fluid=" + fluid,
                                    expected, actual.isSolid(y));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void reusedPlanesPreserveOrderedColdNoiseEvaluationAndWarmAnchorValues() throws Exception {
        IrisTerrain3D profile = profile();
        IrisBiome biome = new IrisBiome().setTerrain3D(profile);
        List<Sample> samples = new ArrayList<>();
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 96.375D, (x, z) -> biome),
                new Terrain3DRuntime.Options(328473L, 256, 0D, null, true, 4096),
                (style, seed) -> {
                    boolean fissure = style == profile.getCrackStyle();
                    return (x, y, z) -> {
                        samples.add(new Sample(fissure, x, y, z));
                        return fissure ? cracks(x, y, z) : density(x, y, z);
                    };
                });
        Method createColumn = Terrain3DRuntime.class.getDeclaredMethod("createColumn", int.class, int.class);
        createColumn.setAccessible(true);
        Terrain3DColumn first = (Terrain3DColumn) createColumn.invoke(runtime, -7, -1);
        List<Sample> expected = new ArrayList<>();
        for (int y = 36; y <= 132; y += 4) {
            for (int z : new int[]{-4, 0}) {
                for (int x : new int[]{-8, -4}) {
                    expected.add(new Sample(false, x, y, z));
                    expected.add(new Sample(true, x, y * 0.25D, z));
                }
            }
        }
        assertEquals(expected, samples);
        assertEquals(first, createColumn.invoke(runtime, -7, -1));
        assertEquals(expected, samples);
        runtime.clear();
        samples.clear();
        assertEquals(first, createColumn.invoke(runtime, -7, -1));
        assertEquals(expected, samples);
    }

    private static IrisTerrain3D profile() {
        return new IrisTerrain3D().setAmplitude(33.5D).setCrackDepth(24.5D)
                .setCrackWidth(4.75D).setCrackScale(64D).setHorizontalScale(64D).setVerticalScale(64D)
                .setMinimumSlope(0D).setFluidClearance(0D).setFluidFade(24D);
    }

    private static boolean referenceSolid(int x, int y, int z, double baseHeight, double fluid, double strength) {
        int gridY = Math.floorDiv(y, 4) * 4;
        double dy = (y - gridY) / 4D;
        double displacement = lerp(plane(x, gridY, z, fluid, strength, 0),
                plane(x, gridY + 4, z, fluid, strength, 0), dy);
        double distance = Math.abs(lerp(plane(x, gridY, z, fluid, strength, 1),
                plane(x, gridY + 4, z, fluid, strength, 1), dy));
        double ridge = Math.max(0D, 1D - distance / 4.75D);
        double depth = lerp(plane(x, gridY, z, fluid, strength, 2),
                plane(x, gridY + 4, z, fluid, strength, 2), dy);
        return baseHeight + 0.5D - y + displacement - depth * ridge * ridge >= 0D;
    }

    private static double plane(int x, int y, int z, double fluid, double strength, int component) {
        int gx = Math.floorDiv(x, 4) * 4;
        int gz = Math.floorDiv(z, 4) * 4;
        double dx = (x - gx) / 4D;
        double dz = (z - gz) / 4D;
        double north = lerp(node(gx, y, gz, fluid, strength, component),
                node(gx + 4, y, gz, fluid, strength, component), dx);
        double south = lerp(node(gx, y, gz + 4, fluid, strength, component),
                node(gx + 4, y, gz + 4, fluid, strength, component), dx);
        return lerp(north, south, dz);
    }

    private static double node(int x, int y, int z, double fluid, double strength, int component) {
        double fade = strength * smooth((y - fluid) / 24D);
        return switch (component) {
            case 0 -> fade == 0D ? 0D : 33.5D * fade * density(x, y, z);
            case 1 -> fade == 0D ? 0D : 32D * cracks(x, y * 0.25D, z);
            case 2 -> 24.5D * fade;
            default -> throw new AssertionError(component);
        };
    }

    private static double density(double x, double y, double z) {
        return StrictMath.sin(y * 0.31D) * StrictMath.cos(x * 0.08D) * StrictMath.cos(z * 0.06D);
    }

    private static double cracks(double x, double y, double z) {
        return StrictMath.sin(x * 0.37D + y * 0.21D - z * 0.29D);
    }

    private static double smooth(double value) {
        double bounded = Math.clamp(value, 0D, 1D);
        return bounded * bounded * (3D - 2D * bounded);
    }

    private static double lerp(double lower, double upper, double fraction) {
        return lower + (upper - lower) * fraction;
    }

    private record Sample(boolean fissure, double x, double y, double z) {
    }
}
