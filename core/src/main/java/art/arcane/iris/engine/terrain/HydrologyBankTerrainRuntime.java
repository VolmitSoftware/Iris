package art.arcane.iris.engine.terrain;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyColumnSnapshot;
import art.arcane.iris.engine.object.IrisRiverBank3DConfig;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.Objects;
import java.util.Optional;

public final class HydrologyBankTerrainRuntime {
    private static final int STEP = 2;
    private static final long DENSITY_SALT = 0x485944524f42414eL;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final Sources sources;
    private final Options options;
    private final double amplitude;
    private final double horizontalFrequency;
    private final double verticalFrequency;
    private final int maximumOverhang;
    private final NoiseSource noise;
    private final Long2ObjectLinkedOpenHashMap<BankColumn> columns = new Long2ObjectLinkedOpenHashMap<>();
    private long columnsEpoch;

    public HydrologyBankTerrainRuntime(Sources sources, Options options) {
        this(sources, new Compiled(options, compileNoise(options)));
    }

    private HydrologyBankTerrainRuntime(Sources sources, Compiled compiled) {
        this.sources = Objects.requireNonNull(sources, "Bank terrain sources");
        options = compiled.options();
        IrisRiverBank3DConfig config = options.config();
        config.validate();
        amplitude = config.isEnabled() ? config.getAmplitude() : 0D;
        horizontalFrequency = 64D / config.getHorizontalScale();
        verticalFrequency = 64D / config.getVerticalScale();
        maximumOverhang = config.getMaximumOverhang();
        noise = Objects.requireNonNull(compiled.noise(), "Bank density noise");
    }

    static HydrologyBankTerrainRuntime withNoise(Sources sources, Options options, NoiseSource noise) {
        return new HydrologyBankTerrainRuntime(sources, new Compiled(options, noise));
    }

    public Terrain3DColumn column(int x, int z) {
        Terrain3DColumn resolved = resolveColumn(x, z);
        return resolved == null ? sources.natural().column(x, z) : resolved;
    }

    public Optional<Terrain3DColumn> columnIfReady(int x, int z) {
        return Optional.ofNullable(resolveColumn(x, z));
    }

    private Terrain3DColumn resolveColumn(int x, int z) {
        BankColumn bank = rawColumn(x, z);
        if (bank == null) {
            return null;
        }
        Terrain3DColumn resolved = bank.resolved;
        if (resolved != null) {
            return resolved;
        }
        Terrain3DColumn raw = bank.raw;
        IntArrayList boundaries = new IntArrayList(raw.spanCount() * 2);
        boundaries.add(raw.ceiling(0));
        boundaries.add(raw.floor(0));
        for (int span = 1; span < raw.spanCount(); span++) {
            Support support = supported(x, z, raw.ceiling(span), raw.floor(span));
            if (support == Support.UNAVAILABLE) {
                return null;
            }
            if (support == Support.SUPPORTED) {
                boundaries.add(raw.ceiling(span));
                boundaries.add(raw.floor(span));
            }
        }
        resolved = boundaries.size() == raw.spanCount() * 2 ? raw
                : new Terrain3DColumn(raw.baseHeight(), raw.minY(), true, boundaries.toIntArray());
        bank.resolved = resolved;
        return resolved;
    }

    public void clear() {
        synchronized (columns) {
            columnsEpoch++;
            columns.clear();
        }
    }

    private static NoiseSource compileNoise(Options options) {
        IrisRiverBank3DConfig config = options.config();
        config.validate();
        if (!config.isEnabled() || config.getAmplitude() == 0D) {
            return (x, y, z) -> 0D;
        }
        CNG generator = config.getDensityStyle().createNoCache(
                new RNG(options.seed() ^ config.getSeed() ^ DENSITY_SALT), options.data());
        return generator::noiseFastSigned3D;
    }

    private BankColumn rawColumn(int x, int z) {
        long key = (long) x << 32 ^ z & 0xffffffffL;
        long loadEpoch;
        synchronized (columns) {
            BankColumn existing = columns.getAndMoveToLast(key);
            if (existing != null) {
                return existing;
            }
            loadEpoch = columnsEpoch;
        }
        BankColumn computed = createColumn(x, z);
        if (computed == null) {
            return null;
        }
        synchronized (columns) {
            if (loadEpoch != columnsEpoch) {
                return null;
            }
            BankColumn existing = columns.getAndMoveToLast(key);
            if (existing != null) {
                return existing;
            }
            columns.putAndMoveToLast(key, computed);
            if (columns.size() > options.maximumColumns()) {
                columns.removeFirst();
            }
        }
        return computed;
    }

    private BankColumn createColumn(int x, int z) {
        HydrologyColumnSnapshot snapshot = sources.hydrology().sample(x, z);
        if (!snapshot.available()) {
            return null;
        }
        Terrain3DColumn natural = Objects.requireNonNull(sources.natural().column(x, z), "Natural bank column");
        HydrologyColumnSample hydrology = snapshot.column();
        HydrologyColumnLayer layer = hydrology == null ? null : hydrology.primarySurfaceLayerOrNull();
        if (layer == null || !layer.terrainOwned()) {
            return new BankColumn(natural, natural);
        }
        int base = Math.clamp(hydrology.terrainHeight(), 0, options.height() - 1);
        if (layer.channel() || !layer.feature().type().isSurface() || amplitude == 0D) {
            Terrain3DColumn flat = Terrain3DColumn.unshaped(base, options.height());
            return new BankColumn(flat, flat);
        }
        if (base >= natural.topY()) {
            return new BankColumn(natural, natural);
        }
        double rise = Math.min(amplitude, natural.topY() - base);
        IntArrayList boundaries = new IntArrayList(natural.spanCount() * 2 + 8);
        boundaries.add(0);
        boolean previous = true;
        int maximum = Math.min(natural.topY(), options.height() - 1);
        int preserved = maximum + 1;
        for (int span = 1; span < natural.spanCount(); span++) {
            if (natural.ceiling(span) > base) {
                preserved = natural.ceiling(span);
                break;
            }
        }
        double[] density = sampleColumn(x, z, base + 1, Math.min(maximum, base + (int) Math.ceil(rise)));
        for (int y = base + 1; y <= maximum; y++) {
            boolean solid = natural.isSolid(y) && (y >= preserved || y - base <= rise
                    && y - base <= rise * (0.5D + 0.5D * density[y - base - 1]));
            if (solid != previous) {
                boundaries.add(solid ? y : y - 1);
                previous = solid;
            }
        }
        if (previous) {
            boundaries.add(maximum);
        }
        Terrain3DColumn raw = new Terrain3DColumn(natural.baseHeight(), base + 1, true, boundaries.toIntArray());
        return new BankColumn(raw, raw.spanCount() == 1 ? raw : null);
    }

    private Support supported(int x, int z, int bottom, int top) {
        for (int y = bottom; y <= top; y++) {
            for (int[] direction : DIRECTIONS) {
                for (int distance = 1; distance <= maximumOverhang; distance++) {
                    long sampleX = (long) x + direction[0] * distance;
                    long sampleZ = (long) z + direction[1] * distance;
                    if (sampleX < Integer.MIN_VALUE || sampleX > Integer.MAX_VALUE
                            || sampleZ < Integer.MIN_VALUE || sampleZ > Integer.MAX_VALUE) {
                        break;
                    }
                    BankColumn sampled = rawColumn((int) sampleX, (int) sampleZ);
                    if (sampled == null) {
                        return Support.UNAVAILABLE;
                    }
                    Terrain3DColumn neighbour = sampled.raw;
                    if (y <= neighbour.floor(0)) {
                        return Support.SUPPORTED;
                    }
                    if (!neighbour.isSolid(y)) {
                        break;
                    }
                }
            }
        }
        return Support.UNSUPPORTED;
    }

    private double[] sampleColumn(int x, int z, int minimum, int maximum) {
        int lowerX = Math.floorDiv(x, STEP) * STEP;
        int lowerZ = Math.floorDiv(z, STEP) * STEP;
        double fractionX = (x - lowerX) / (double) STEP;
        double fractionZ = (z - lowerZ) / (double) STEP;
        int gridY = Math.floorDiv(minimum, STEP) * STEP;
        double lower = interpolatePlane(lowerX, gridY, lowerZ, fractionX, fractionZ);
        double upper = interpolatePlane(lowerX, gridY + STEP, lowerZ, fractionX, fractionZ);
        double[] density = new double[maximum - minimum + 1];
        for (int y = minimum; y <= maximum; y++) {
            if (y >= gridY + STEP) {
                gridY += STEP;
                lower = upper;
                upper = interpolatePlane(lowerX, gridY + STEP, lowerZ, fractionX, fractionZ);
            }
            double fractionY = (y - gridY) / (double) STEP;
            density[y - minimum] = lower + (upper - lower) * fractionY;
        }
        return density;
    }

    private double interpolatePlane(int x, int y, int z, double fractionX, double fractionZ) {
        double northWest = noise(x, y, z);
        double northEast = noise((long) x + STEP, y, z);
        double southWest = noise(x, y, (long) z + STEP);
        double southEast = noise((long) x + STEP, y, (long) z + STEP);
        double north = northWest + (northEast - northWest) * fractionX;
        double south = southWest + (southEast - southWest) * fractionX;
        return north + (south - north) * fractionZ;
    }

    private double noise(long x, int y, long z) {
        double value = noise.sample(x * horizontalFrequency, y * verticalFrequency, z * horizontalFrequency);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Nonfinite riverbank density at " + x + "," + y + "," + z + ".");
        }
        return Math.clamp(value, -1D, 1D);
    }

    public record Sources(NaturalSource natural, HydrologySource hydrology) {
        public Sources {
            Objects.requireNonNull(natural, "Natural terrain source");
            Objects.requireNonNull(hydrology, "Hydrology source");
        }
    }

    public record Options(long seed, int height, int maximumColumns, IrisData data, IrisRiverBank3DConfig config) {
        public Options {
            Objects.requireNonNull(config, "Bank geometry configuration");
            if (height < 2 || maximumColumns < 1) {
                throw new IllegalArgumentException("Bank terrain height and cache size must be positive.");
            }
        }
    }

    @FunctionalInterface
    public interface NaturalSource {
        Terrain3DColumn column(int x, int z);
    }

    @FunctionalInterface
    public interface HydrologySource {
        HydrologyColumnSnapshot sample(int x, int z);
    }

    @FunctionalInterface
    interface NoiseSource {
        double sample(double x, double y, double z);
    }

    private record Compiled(Options options, NoiseSource noise) {
    }

    private enum Support {
        SUPPORTED,
        UNSUPPORTED,
        UNAVAILABLE
    }

    private static final class BankColumn {
        private final Terrain3DColumn raw;
        private volatile Terrain3DColumn resolved;

        private BankColumn(Terrain3DColumn raw, Terrain3DColumn resolved) {
            this.raw = raw;
            this.resolved = resolved;
        }
    }
}
