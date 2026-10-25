package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleBinaryOperator;

final class SyntheticTerrain implements HydrologyTerrainSource {
    static final int SEA_LEVEL = 40;
    static final int MIN_Y = -64;
    static final int MAX_Y = 320;
    private static final byte ALLOW_ALL = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;
    private static final double OCEAN_FLOOR = SEA_LEVEL - 30D;
    private static final double BASE_OFFSET = 3D;
    private static final double BASE_WAVELENGTH = 1000D;
    private static final long SALT_BASE = 0x42415345L;
    private static final long SALT_NOISE = 0x4e4f495345L;

    private final DoubleBinaryOperator natural;
    private final DoubleBinaryOperator rainfall;
    private final Area streamsBlocked;
    private final AtomicLong baseSamples;
    private final AtomicLong naturalSamples;

    private SyntheticTerrain(DoubleBinaryOperator natural) {
        this(natural, (x, z) -> 1D, (x, z) -> false);
    }

    private SyntheticTerrain(DoubleBinaryOperator natural, DoubleBinaryOperator rainfall, Area streamsBlocked) {
        this.natural = natural;
        this.rainfall = rainfall;
        this.streamsBlocked = streamsBlocked;
        this.baseSamples = new AtomicLong();
        this.naturalSamples = new AtomicLong();
    }

    SyntheticTerrain withRainfall(DoubleBinaryOperator rainfall) {
        return new SyntheticTerrain(natural, rainfall, streamsBlocked);
    }

    SyntheticTerrain withStreamsBlocked(Area area) {
        return new SyntheticTerrain(natural, rainfall, area);
    }

    SyntheticTerrain withPit(double centreX, double centreZ, double radius, double depth) {
        DoubleBinaryOperator inner = natural;
        return new SyntheticTerrain((x, z) -> {
            double ratio = Math.hypot(x - centreX, z - centreZ) / radius;
            double height = inner.applyAsDouble(x, z);
            return ratio < 1D ? height - depth * (1D - ratio * ratio) : height;
        }, rainfall, streamsBlocked);
    }

    static SyntheticTerrain valleyLake() {
        return valleyLake(5600D);
    }

    static SyntheticTerrain valleyLake(double east) {
        double axis = 2048D;
        double west = 3850D;
        double floor = 105D;
        double lip = 125D;
        return new SyntheticTerrain((x, z) -> {
            double offAxis = Math.abs(z - axis);
            double height = floor + 0.15D * Math.max(0D, offAxis - 250D) + 0.3D * Math.max(0D, west - x) + 0.3D * Math.max(0D, x - east);
            if (x > east && offAxis < 60D) {
                height = Math.min(height, lip - 0.05D * Math.max(0D, x - east - 100D));
            }
            return Math.max(OCEAN_FLOOR, height);
        }).withPit(3950D, axis, 150D, 30D);
    }

    static SyntheticTerrain flat(double height) {
        return new SyntheticTerrain((x, z) -> height);
    }

    static SyntheticTerrain southSlope(double northHeight, double gradient) {
        return new SyntheticTerrain((x, z) -> northHeight - gradient * z);
    }

    static SyntheticTerrain slopedPit(double centreX, double centreZ, double radius, double depth) {
        return new SyntheticTerrain((x, z) -> {
            double ratio = Math.hypot(x - centreX, z - centreZ) / radius;
            double plane = 150D - 0.05D * z;
            return ratio < 1D ? plane - depth * (1D - ratio * ratio) : plane;
        });
    }

    static SyntheticTerrain trunkValley() {
        double west = 384D;
        double east = 7808D;
        double north = 384D;
        double south = 3712D;
        double axis = 2304D;
        return new SyntheticTerrain((x, z) -> {
            double inside = Math.min(Math.min(x - west, east - x), Math.min(z - north, south - z));
            double coast = SEA_LEVEL - 12D + 0.3D * inside;
            double offAxis = Math.abs(z - axis);
            double valley = 56D + 0.02D * (x - west) + 0.07D * offAxis;
            double ridges = 30D * (0.5D - 0.5D * Math.cos(2D * Math.PI * (x - 256D) / 1024D)) * Math.min(1D, offAxis / 128D);
            return Math.max(OCEAN_FLOOR, Math.min(valley + ridges, coast));
        });
    }

    static SyntheticTerrain boxedFractal(long seed, double boxMinX, double boxMinZ, double size) {
        double ridgeTop = 300D;
        double ridgeSlope = 2D;
        double seaX = boxMinX + 0.3D * size;
        double seaZ = boxMinZ + 0.75D * size;
        double seaRadius = 0.35D * size;
        return new SyntheticTerrain((x, z) -> {
            double sea = Math.max(0D, 1D - Math.hypot(x - seaX, z - seaZ) / seaRadius);
            double inside = Math.min(Math.min(x - boxMinX, boxMinX + size - x), Math.min(z - boxMinZ, boxMinZ + size - z));
            double ridge = ridgeTop - ridgeSlope * Math.abs(inside);
            return Math.max(fractalHeight(seed, x, z) - 90D * sea * sea, ridge);
        });
    }

    static SyntheticTerrain coneIsland(double centreX, double centreZ, double coastRadius, double peak, int valleys) {
        double valleyDepth = 40D;
        return new SyntheticTerrain((x, z) -> {
            double dx = x - centreX;
            double dz = z - centreZ;
            double radius = Math.hypot(dx, dz);
            double cone = peak - (peak - SEA_LEVEL) * radius / coastRadius;
            double ramp = Math.min(1D, radius / (0.4D * coastRadius));
            double trough = 0.5D * (1D + Math.cos(valleys * Math.atan2(dz, dx)));
            double valley = valleyDepth * ramp * trough * trough * trough;
            return Math.max(OCEAN_FLOOR, cone - valley);
        });
    }

    static SyntheticTerrain crater(double centreX, double centreZ, int spill, double rimRadius) {
        double coastRadius = rimRadius + 1100D;
        double rim = spill + 20D;
        double floor = spill - 40D;
        double notchHalfWidth = 96D;
        return new SyntheticTerrain((x, z) -> {
            double dx = x - centreX;
            double dz = z - centreZ;
            double radius = Math.hypot(dx, dz);
            double height;
            if (radius < rimRadius) {
                double ratio = radius / rimRadius;
                height = floor + (rim - floor) * ratio * ratio;
            } else {
                height = rim - (rim - SEA_LEVEL) * (radius - rimRadius) / (coastRadius - rimRadius);
            }
            if (dx > 0D && Math.abs(dz) < notchHalfWidth) {
                height = Math.min(height, spill);
            }
            return Math.max(OCEAN_FLOOR, height);
        });
    }

    static SyntheticTerrain steepOutlet(double centreX, double centreZ, double cliffRun, double drop) {
        double radius = 594D;
        double floor = 112D;
        double lip = 118D;
        double outletHalfWidth = 200D;
        return new SyntheticTerrain((x, z) -> {
            double dx = x - centreX;
            double dz = z - centreZ;
            double distance = Math.hypot(dx, dz);
            double tilt = -0.002D * dx + 0.0003D * dz;
            if (distance < radius) {
                double ratio = distance / radius;
                return floor + (lip - floor) * ratio * ratio + tilt * ratio;
            }
            if (dx > 0D && Math.abs(dz) < outletHalfWidth) {
                double beyond = distance - radius;
                double edge = lip + tilt * radius / distance;
                return Math.max(OCEAN_FLOOR, edge - drop * Math.min(1D, beyond / cliffRun) - 0.05D * Math.max(0D, beyond - cliffRun));
            }
            return lip + 12D;
        });
    }

    static SyntheticTerrain notchedBowl(double centreX, double centreZ, double notchDepth) {
        double radius = 600D;
        double crest = 125D;
        return new SyntheticTerrain((x, z) -> {
            double dx = x - centreX;
            double dz = z - centreZ;
            double distance = Math.hypot(dx, dz);
            double ratio = distance / radius;
            double height = distance < radius ? 100D + 30D * ratio * ratio : 130D;
            if (dx > 0D && Math.abs(dz) < 200D) {
                double lip = crest + 0.01D * Math.abs(dz - 40D);
                height = Math.min(height, distance <= radius + 40D ? lip : lip - 0.5D * (distance - radius - 40D));
                if (notchDepth > 0D && dz >= 36D && dz <= 42D && distance > 400D) {
                    height = Math.min(height, crest - notchDepth);
                }
            }
            return Math.max(OCEAN_FLOOR, height);
        });
    }

    static SyntheticTerrain seamLakes() {
        double westX = 3520D;
        double eastX = 4380D;
        double axis = 2048D;
        double plateau = 130D;
        return new SyntheticTerrain((x, z) -> {
            double west = Math.hypot(x - westX, z - axis);
            double east = Math.hypot(x - eastX, z - axis);
            double height = plateau - 0.03D * Math.max(0D, Math.min(west - 550D, east - 258D));
            if (west < 500D) {
                height = 90D + 10D * west / 500D;
            } else if (west < 550D) {
                height = 100D + 30D * (west - 500D) / 50D;
            }
            if (east < 200D) {
                height = Math.min(height, 112D);
            } else if (east < 258D) {
                height = Math.min(height, 112D + 18D * (east - 200D) / 58D);
            }
            if (z >= 2072D && z <= 2100D && x >= 4000D && x <= 4200D) {
                height = Math.min(height, 118D);
            }
            if (x < westX && Math.abs(z - axis) < 96D) {
                height = Math.min(height, 124D - 0.05D * Math.max(0D, westX - 550D - x));
            }
            return Math.max(OCEAN_FLOOR, height);
        });
    }

    static SyntheticTerrain ridgeValley() {
        double west = 384D;
        double east = 7808D;
        double north = 384D;
        double south = 3712D;
        double axis = 2048D;
        double[] lakes = {2304D, 5888D};
        double lakeRadius = 512D;
        return new SyntheticTerrain((x, z) -> {
            double inside = Math.min(Math.min(x - west, east - x), Math.min(z - north, south - z));
            double coast = SEA_LEVEL - 12D + 0.3D * inside;
            double offAxis = Math.abs(z - axis);
            double valley = 56D + 0.02D * (x - west) + 0.07D * offAxis;
            double ridges = 30D * (0.5D - 0.5D * Math.cos(2D * Math.PI * x / 1024D)) * Math.min(1D, offAxis / 512D);
            for (double lakeX : lakes) {
                double ratio = Math.hypot(x - lakeX, z - axis) / lakeRadius;
                if (ratio < 1D) {
                    valley -= 36D * (1D - ratio * ratio);
                }
            }
            return Math.max(OCEAN_FLOOR, Math.min(valley + ridges, coast));
        });
    }

    static SyntheticTerrain cliffCoast(double coastX) {
        return new SyntheticTerrain((x, z) -> x < coastX ? OCEAN_FLOOR : SEA_LEVEL + 30D + 0.05D * (x - coastX) + 3D * Math.sin(z / 40D));
    }

    static SyntheticTerrain fractal(long seed) {
        return new SyntheticTerrain((x, z) -> fractalHeight(seed, x, z));
    }

    private static double fractalHeight(long seed, double x, double z) {
        double height = 70D;
        double amplitude = 60D;
        double wavelength = 4096D;
        for (int octave = 0; octave < 6; octave++) {
            height += amplitude * valueNoise(seed + octave, x / wavelength, z / wavelength);
            amplitude *= 0.5D;
            wavelength *= 0.5D;
        }
        return height;
    }

    double natural(double x, double z) {
        return natural.applyAsDouble(x, z);
    }

    double base(double x, double z) {
        return natural.applyAsDouble(x, z) + BASE_OFFSET * valueNoise(SALT_BASE, x / BASE_WAVELENGTH, z / BASE_WAVELENGTH);
    }

    long baseSamples() {
        return baseSamples.get();
    }

    long naturalSamples() {
        return naturalSamples.get();
    }

    @Override
    public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
        (baseOnly ? baseSamples : naturalSamples).addAndGet(xs.length);
        for (int index = 0; index < xs.length; index++) {
            float value = (float) (baseOnly ? base(xs[index], zs[index]) : natural(xs[index], zs[index]));
            out.height()[index] = value;
            out.ocean()[index] = value <= SEA_LEVEL;
            out.rainfall()[index] = (float) rainfall.applyAsDouble(xs[index], zs[index]);
            out.pondDensity()[index] = 1f;
            out.permission()[index] = streamsBlocked.contains(xs[index], zs[index]) ? HydrologySampleBuffer.ALLOW_PONDS : ALLOW_ALL;
            out.fluidProfile()[index] = null;
        }
    }

    @Override
    public int seaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int minY() {
        return MIN_Y;
    }

    @Override
    public int maxY() {
        return MAX_Y;
    }

    interface Area {
        boolean contains(double x, double z);
    }

    private static double valueNoise(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double top = lerp(corner(seed, x0, z0), corner(seed, x0 + 1, z0), fx);
        double bottom = lerp(corner(seed, x0, z0 + 1), corner(seed, x0 + 1, z0 + 1), fx);
        return lerp(top, bottom, fz);
    }

    private static double corner(long seed, int x, int z) {
        return 2D * CellHash.unit(CellHash.mix(seed, SALT_NOISE, x, z)) - 1D;
    }

    private static double smooth(double t) {
        return t * t * (3D - 2D * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
