package art.arcane.iris.generation.terrain;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class Terrain3DRuntime {
    private static final int STEP = 4;
    private static final int CACHE_STRIPES = 16;
    private static final long DENSITY_SALT = 0x536E4A11C924B3D7L;
    private static final long CRACK_SALT = 0x7839A16DC4052EFBL;
    private static final NodeSample EMPTY_SAMPLE = new NodeSample(0D, 0D, 0D);
    private static final CompiledProfile DISABLED = new CompiledProfile(null, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, null, null);

    private final Sources sources;
    private final Options options;
    private final NoiseFactory noiseFactory;
    private final BoundedCache<Terrain3DFragmentFilter.DensityColumn> columns;
    private final BoundedCache<Anchor> anchors;
    private final Terrain3DFragmentFilter fragments;
    private final Map<IrisTerrain3D, CompiledProfile> profiles = new IdentityHashMap<>();
    private final ThreadLocal<ColumnMemo> localColumn = ThreadLocal.withInitial(ColumnMemo::new);
    private volatile Object cacheGeneration = new Object();

    public Terrain3DRuntime(Sources sources, Options options) {
        this(sources, options, (style, seed) -> {
            CNG noise = style.createNoCache(new RNG(seed), options.data());
            return noise::noiseFastSigned3D;
        });
    }

    Terrain3DRuntime(Sources sources, Options options, NoiseFactory noiseFactory) {
        this.sources = Objects.requireNonNull(sources, "Terrain sources");
        this.options = Objects.requireNonNull(options, "Terrain options");
        this.noiseFactory = Objects.requireNonNull(noiseFactory, "Terrain noise factory");
        columns = new BoundedCache<>(options.maximumColumns());
        anchors = new BoundedCache<>(Math.max(CACHE_STRIPES, options.maximumColumns() / 4));
        fragments = new Terrain3DFragmentFilter(this::rawColumn);
    }

    public boolean active() {
        return options.enabled();
    }

    public Terrain3DColumn column(int x, int z) {
        if (!active()) {
            return Terrain3DColumn.unshaped(baseHeight(x, z), options.height());
        }
        long key = pack(x, z);
        Object generation = cacheGeneration;
        ColumnMemo memo = localColumn.get();
        if (memo.generation == generation && memo.key == key) {
            return memo.column;
        }
        Terrain3DColumn column = fragments.column(x, z);
        memo.key = key;
        memo.column = column;
        memo.generation = generation;
        return column;
    }

    public void clear() {
        columns.clear();
        anchors.clear();
        fragments.clear();
        synchronized (profiles) {
            profiles.clear();
        }
        cacheGeneration = new Object();
        localColumn.remove();
    }

    private Terrain3DFragmentFilter.DensityColumn rawColumn(int x, int z) {
        long key = pack(x, z);
        Terrain3DFragmentFilter.DensityColumn cached = columns.get(key);
        return cached == null ? columns.putIfAbsent(key,
                new Terrain3DFragmentFilter.DensityColumn(createColumn(x, z))) : cached;
    }

    private Terrain3DColumn createColumn(int x, int z) {
        double baseHeight = baseHeight(x, z);
        int gridX = Math.floorDiv(x, STEP) * STEP;
        int gridZ = Math.floorDiv(z, STEP) * STEP;
        Anchor northWest = anchor(gridX, gridZ);
        Anchor northEast = anchor(gridX + STEP, gridZ);
        Anchor southWest = anchor(gridX, gridZ + STEP);
        Anchor southEast = anchor(gridX + STEP, gridZ + STEP);
        double dx = (x - gridX) / (double) STEP;
        double dz = (z - gridZ) / (double) STEP;
        double amplitude = interpolate(northWest.amplitude(), northEast.amplitude(),
                southWest.amplitude(), southEast.amplitude(), dx, dz);
        double crackDepth = interpolate(northWest.crackDepth(), northEast.crackDepth(),
                southWest.crackDepth(), southEast.crackDepth(), dx, dz);
        if (amplitude + crackDepth <= 0D) {
            return Terrain3DColumn.unshaped(baseHeight, options.height());
        }
        double densityHeight = baseHeight + interpolate(northWest.heightOffset(baseHeight),
                northEast.heightOffset(baseHeight), southWest.heightOffset(baseHeight),
                southEast.heightOffset(baseHeight), dx, dz);
        double crackWidth = interpolate(northWest.profile.crackWidth, northEast.profile.crackWidth,
                southWest.profile.crackWidth, southEast.profile.crackWidth, dx, dz);
        int minimum = Math.max(Math.max(1, (int) Math.floor(options.fluidHeight()) + 1),
                (int) Math.floor(densityHeight - amplitude - crackDepth));
        int maximum = Math.min(options.height() - 1, (int) Math.ceil(densityHeight + amplitude));
        if (minimum > maximum) {
            return Terrain3DColumn.unshaped(baseHeight, options.height());
        }
        int[] boundaries = new int[16];
        int count = 1;
        boundaries[0] = 0;
        boolean previousSolid = true;
        for (int gridY = Math.floorDiv(minimum, STEP) * STEP; gridY <= maximum; gridY += STEP) {
            NodeSample lowerNW = northWest.sample(gridY);
            NodeSample lowerNE = northEast.sample(gridY);
            NodeSample lowerSW = southWest.sample(gridY);
            NodeSample lowerSE = southEast.sample(gridY);
            NodeSample upperNW = northWest.sample(gridY + STEP);
            NodeSample upperNE = northEast.sample(gridY + STEP);
            NodeSample upperSW = southWest.sample(gridY + STEP);
            NodeSample upperSE = southEast.sample(gridY + STEP);
            double lowerDisplacement = interpolate(lowerNW.displacement, lowerNE.displacement,
                    lowerSW.displacement, lowerSE.displacement, dx, dz);
            double upperDisplacement = interpolate(upperNW.displacement, upperNE.displacement,
                    upperSW.displacement, upperSE.displacement, dx, dz);
            double lowerCrack = interpolate(lowerNW.crackDistance, lowerNE.crackDistance,
                    lowerSW.crackDistance, lowerSE.crackDistance, dx, dz);
            double upperCrack = interpolate(upperNW.crackDistance, upperNE.crackDistance,
                    upperSW.crackDistance, upperSE.crackDistance, dx, dz);
            double lowerDepth = interpolate(lowerNW.crackDepth, lowerNE.crackDepth,
                    lowerSW.crackDepth, lowerSE.crackDepth, dx, dz);
            double upperDepth = interpolate(upperNW.crackDepth, upperNE.crackDepth,
                    upperSW.crackDepth, upperSE.crackDepth, dx, dz);
            int lastY = Math.min(maximum, gridY + STEP - 1);
            for (int y = Math.max(minimum, gridY); y <= lastY; y++) {
                double dy = (y - gridY) / (double) STEP;
                double displacement = lerp(lowerDisplacement, upperDisplacement, dy);
                double fissure = 0D;
                if (crackDepth > 0D && crackWidth > 0D) {
                    double distance = Math.abs(lerp(lowerCrack, upperCrack, dy));
                    double ridge = Math.max(0D, 1D - distance / crackWidth);
                    fissure = lerp(lowerDepth, upperDepth, dy) * ridge * ridge;
                }
                boolean solid = densityHeight + 0.5D - y + displacement - fissure >= 0D;
                if (solid != previousSolid) {
                    if (count == boundaries.length) {
                        boundaries = Arrays.copyOf(boundaries, count * 2);
                    }
                    boundaries[count++] = solid ? y : y - 1;
                    previousSolid = solid;
                }
            }
        }
        if (previousSolid) {
            if (count == boundaries.length) {
                boundaries = Arrays.copyOf(boundaries, count + 1);
            }
            boundaries[count++] = maximum;
        }
        return new Terrain3DColumn(baseHeight, minimum, true, Arrays.copyOf(boundaries, count));
    }

    private Anchor anchor(int x, int z) {
        long key = pack(x, z);
        Anchor cached = anchors.get(key);
        return cached == null ? anchors.putIfAbsent(key, createAnchor(x, z)) : cached;
    }

    private Anchor createAnchor(int x, int z) {
        IrisBiome biome = sources.biomes().sample(x, z);
        CompiledProfile profile = profile(biome == null ? null : biome.getTerrain3D());
        if (profile == DISABLED) {
            return new Anchor(x, z, profile, 0D, 0D);
        }
        double height = baseHeight(x, z);
        double elevationStrength = smooth((height - options.fluidHeight() - profile.fluidClearance)
                / profile.fluidFade);
        if (elevationStrength <= 0D) {
            return new Anchor(x, z, profile, height, 0D);
        }
        double slopeStrength = 1D;
        if (profile.minimumSlope > 0D) {
            double east = (baseHeight(x + STEP, z) - height) / STEP;
            double south = (baseHeight(x, z + STEP) - height) / STEP;
            double slope = StrictMath.sqrt(east * east + south * south);
            slopeStrength = smooth((slope - profile.minimumSlope) / profile.slopeFade);
        }
        return new Anchor(x, z, profile, height, elevationStrength * slopeStrength);
    }

    private CompiledProfile profile(IrisTerrain3D config) {
        if (config == null || !config.isEnabled()) {
            return DISABLED;
        }
        synchronized (profiles) {
            CompiledProfile cached = profiles.get(config);
            if (cached != null) {
                return cached;
            }
        }
        CompiledProfile compiled = compile(config);
        synchronized (profiles) {
            CompiledProfile existing = profiles.get(config);
            if (existing != null) {
                return existing;
            }
            profiles.put(config, compiled);
            return compiled;
        }
    }

    private CompiledProfile compile(IrisTerrain3D config) {
        config.validate();
        double amplitude = config.getAmplitude();
        double crackDepth = config.getCrackDepth();
        double horizontalScale = config.getHorizontalScale();
        double verticalScale = config.getVerticalScale();
        double crackScale = config.getCrackScale();
        double crackWidth = config.getCrackWidth();
        double minimumSlope = config.getMinimumSlope();
        double slopeFade = config.getSlopeFade();
        double fluidClearance = config.getFluidClearance();
        double fluidFade = config.getFluidFade();
        if (amplitude + crackDepth == 0D) {
            return DISABLED;
        }
        long seed = options.seed() ^ config.getSeed();
        NoiseSource density = amplitude == 0D ? null : noiseFactory.create(
                Objects.requireNonNull(config.getDensityStyle(), "terrain3D.densityStyle"), seed ^ DENSITY_SALT);
        NoiseSource cracks = crackDepth == 0D ? null : noiseFactory.create(
                Objects.requireNonNull(config.getCrackStyle(), "terrain3D.crackStyle"), seed ^ CRACK_SALT);
        return new CompiledProfile(config, amplitude, crackDepth, crackWidth, crackScale,
                64D / horizontalScale, 64D / verticalScale, 64D / crackScale,
                minimumSlope, slopeFade, fluidClearance, fluidFade, density, cracks);
    }

    private double baseHeight(int x, int z) {
        double height = sources.heights().sample(x, z);
        if (!Double.isFinite(height)) {
            throw new IllegalStateException("Nonfinite terrain height at " + x + "," + z);
        }
        return height;
    }

    private static double signed(NoiseSource source, double x, double y, double z) {
        double sampled = source.sample(x, y, z);
        if (!Double.isFinite(sampled)) {
            throw new IllegalStateException("Nonfinite terrain3D density at " + x + "," + y + "," + z);
        }
        return Math.clamp(sampled, -1D, 1D);
    }

    private static double interpolate(double northWest, double northEast, double southWest,
                                      double southEast, double x, double z) {
        return lerp(lerp(northWest, northEast, x), lerp(southWest, southEast, x), z);
    }

    private static double lerp(double lower, double upper, double fraction) {
        return lower + (upper - lower) * fraction;
    }

    private static double smooth(double value) {
        double bounded = Math.clamp(value, 0D, 1D);
        return bounded * bounded * (3D - 2D * bounded);
    }

    private static long pack(int x, int z) {
        return (long) x << 32 ^ z & 0xffffffffL;
    }

    public record Sources(BaseHeightSource heights, BiomeSource biomes) {
        public Sources {
            Objects.requireNonNull(heights, "Base terrain height source");
            Objects.requireNonNull(biomes, "Base terrain biome source");
        }
    }

    public record Options(long seed, int height, double fluidHeight, IrisData data,
                          boolean enabled, int maximumColumns) {
        public Options {
            if (height < 2 || height > 4096 || !Double.isFinite(fluidHeight)
                    || maximumColumns < CACHE_STRIPES) {
                throw new IllegalArgumentException("Invalid volumetric terrain runtime options");
            }
        }
    }

    @FunctionalInterface
    public interface BaseHeightSource {
        double sample(int x, int z);
    }

    @FunctionalInterface
    public interface BiomeSource {
        IrisBiome sample(int x, int z);
    }

    @FunctionalInterface
    interface NoiseFactory {
        NoiseSource create(IrisGeneratorStyle style, long seed);
    }

    @FunctionalInterface
    interface NoiseSource {
        double sample(double x, double y, double z);
    }

    private static final class ColumnMemo {
        private Object generation;
        private long key;
        private Terrain3DColumn column;
    }

    private final class Anchor {
        private final int x;
        private final int z;
        private final CompiledProfile profile;
        private final double height;
        private final double strength;
        private final AtomicReferenceArray<NodeSample> samples;

        private Anchor(int x, int z, CompiledProfile profile, double height, double strength) {
            this.x = x;
            this.z = z;
            this.profile = profile;
            this.height = height;
            this.strength = strength;
            samples = strength == 0D ? null
                    : new AtomicReferenceArray<>(Math.ceilDiv(options.height(), STEP) + 1);
        }

        private double amplitude() {
            return profile.amplitude * strength;
        }

        private double crackDepth() {
            return profile.crackDepth * strength;
        }

        private double heightOffset(double baseHeight) {
            return (height - baseHeight) * strength;
        }

        private NodeSample sample(int y) {
            if (samples == null) {
                return EMPTY_SAMPLE;
            }
            int index = y / STEP;
            NodeSample cached = samples.get(index);
            if (cached != null) {
                return cached;
            }
            double fade = strength * smooth((y - options.fluidHeight() - profile.fluidClearance)
                    / profile.fluidFade);
            double displacement = profile.density == null || fade == 0D ? 0D
                    : profile.amplitude * fade * signed(profile.density,
                    x * profile.horizontalFrequency, y * profile.verticalFrequency, z * profile.horizontalFrequency);
            double crackDistance = profile.cracks == null || fade == 0D ? 0D
                    : profile.crackScale * 0.5D * signed(profile.cracks,
                    x * profile.crackFrequency, y * profile.crackFrequency * 0.25D, z * profile.crackFrequency);
            NodeSample computed = new NodeSample(displacement, crackDistance, profile.crackDepth * fade);
            return samples.compareAndSet(index, null, computed) ? computed : samples.get(index);
        }
    }

    private record NodeSample(double displacement, double crackDistance, double crackDepth) {
    }

    private record CompiledProfile(IrisTerrain3D config, double amplitude, double crackDepth,
                                   double crackWidth, double crackScale, double horizontalFrequency,
                                   double verticalFrequency, double crackFrequency,
                                   double minimumSlope, double slopeFade, double fluidClearance,
                                   double fluidFade, NoiseSource density, NoiseSource cracks) {
    }

    private static final class BoundedCache<T> {
        private final CacheStripe<T>[] stripes;

        @SuppressWarnings("unchecked")
        private BoundedCache(int maximumSize) {
            stripes = (CacheStripe<T>[]) new CacheStripe<?>[CACHE_STRIPES];
            for (int index = 0; index < stripes.length; index++) {
                stripes[index] = new CacheStripe<>(Math.ceilDiv(maximumSize, CACHE_STRIPES));
            }
        }

        private T get(long key) {
            return stripe(key).get(key);
        }

        private T putIfAbsent(long key, T value) {
            return stripe(key).putIfAbsent(key, value);
        }

        private void clear() {
            for (CacheStripe<T> stripe : stripes) {
                stripe.clear();
            }
        }

        private CacheStripe<T> stripe(long key) {
            long mixed = key ^ (key >>> 33);
            mixed *= 0xff51afd7ed558ccdL;
            mixed ^= mixed >>> 33;
            return stripes[(int) mixed & (CACHE_STRIPES - 1)];
        }
    }

    private static final class CacheStripe<T> {
        private final int maximumSize;
        private final Long2ObjectLinkedOpenHashMap<T> entries = new Long2ObjectLinkedOpenHashMap<>(16);

        private CacheStripe(int maximumSize) {
            this.maximumSize = maximumSize;
        }

        private synchronized T get(long key) {
            return entries.getAndMoveToLast(key);
        }

        private synchronized T putIfAbsent(long key, T value) {
            T existing = entries.getAndMoveToLast(key);
            if (existing != null) {
                return existing;
            }
            entries.putAndMoveToLast(key, value);
            if (entries.size() > maximumSize) {
                entries.removeFirst();
            }
            return value;
        }

        private synchronized void clear() {
            entries.clear();
        }
    }
}
