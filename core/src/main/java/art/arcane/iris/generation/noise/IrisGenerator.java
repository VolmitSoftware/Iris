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

package art.arcane.iris.generation.noise;

import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CellGenerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents a composite generator of noise gens")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisGenerator extends IrisRegistrant {
    private static final int SURFACE_GRID = 6;
    private static final int SURFACE_CACHE_STRIPES = 16;
    private static final int SURFACE_CACHE_STRIPE_SIZE = 1024;

    private final transient AtomicCache<CellGenerator> cellGen = new AtomicCache<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient volatile SurfaceCache surfaceSamples;
    @MinNumber(0.001)
    @Description("The zoom or frequency.")
    private double zoom = 1;
    @MinNumber(0)
    @Description("The opacity, essentially a multiplier on the output.")
    private double opacity = 1;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Fine terrain detail retained around bilinear interpolation of the original noise on a 6-block grid. 1 preserves the original noise; 0.5 halves the sub-grid residual. Original grid heights, feature sizes and height ranges stay unchanged.")
    private double surfaceDetail = 1;
    @Description("Multiply the compsites instead of adding them")
    private boolean multiplicitive = false;
    @MinNumber(0.001)
    @Description("The size of the cell fractures")
    private double cellFractureZoom = 1D;
    @MinNumber(0)
    @Description("Cell Fracture Coordinate Shuffling")
    private double cellFractureShuffle = 12D;
    @Description("The height of fracture cells. Set to 0 to disable")
    private double cellFractureHeight = 0D;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("How big are the cells (X,Z) relative to the veins that touch them. Between 0 and 1. 0.1 means thick veins, small cells.")
    private double cellPercentSize = 0.75D;
    @Description("The offset to shift this noise x")
    private double offsetX = 0;
    @Description("The offset to shift this noise z")
    private double offsetZ = 0;
    @Required
    @Description("The seed for this generator")
    private long seed = 1;
    @Required
    @Description("The interpolator to use when smoothing this generator into other regions & generators")
    private IrisInterpolator interpolator = new IrisInterpolator();
    @MinNumber(0)
    @MaxNumber(8192)
    @Description("Cliff Height Max. Disable with 0 for min and max")
    private double cliffHeightMax = 0;
    @MinNumber(0)
    @MaxNumber(8192)
    @Description("Cliff Height Min. Disable with 0 for min and max")
    private double cliffHeightMin = 0;
    @ArrayType(min = 1, type = IrisNoiseGenerator.class)
    @Description("The list of noise gens this gen contains.")
    private KList<IrisNoiseGenerator> composite = new KList<>();
    @Description("The noise gen for cliff height.")
    private IrisNoiseGenerator cliffHeightGenerator = new IrisNoiseGenerator();

    public double getMax() {
        return opacity;
    }

    public boolean hasCliffs() {
        return cliffHeightMax > 0;
    }

    public CellGenerator getCellGenerator(long seed) {
        return cellGen.aquire(() -> new CellGenerator(new RNG(seed + 239466)));
    }

    public <T> T fit(T[] v, long superSeed, double rx, double rz) {
        if (v.length == 0) {
            return null;
        }

        if (v.length == 1) {
            return v[0];
        }

        return v[fit(0, v.length - 1, superSeed, rx, rz)];
    }

    public <T> T fit(List<T> v, long superSeed, double rx, double rz) {
        if (v.size() == 0) {
            return null;
        }

        if (v.size() == 1) {
            return v.get(0);
        }

        return v.get(fit(0, v.size() - 1, superSeed, rx, rz));
    }

    public int fit(int min, int max, long superSeed, double rx, double rz) {
        if (min == max) {
            return min;
        }

        double noise = getHeight(rx, rz, superSeed);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public int fit(double min, double max, long superSeed, double rx, double rz) {
        if (min == max) {
            return (int) Math.round(min);
        }

        double noise = getHeight(rx, rz, superSeed);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public double fitDouble(double min, double max, long superSeed, double rx, double rz) {
        if (min == max) {
            return min;
        }

        double noise = getHeight(rx, rz, superSeed);

        return IrisInterpolation.lerp(min, max, noise);
    }

    public double getHeight(double rx, double rz, long superSeed) {
        return getHeight(rx, 0, rz, superSeed, true);
    }


    public double getHeight(double rx, double ry, double rz, long superSeed) {
        return getHeight(rx, ry, rz, superSeed, false);
    }

    public double getHeight(double rx, double ry, double rz, long superSeed, boolean no3d) {
        if (!Double.isFinite(surfaceDetail) || surfaceDetail < 0D || surfaceDetail > 1D) {
            throw new IllegalArgumentException("Generator surfaceDetail must be finite and between 0 and 1");
        }
        if (surfaceDetail == 1D || composite.isEmpty()) {
            return sampleHeight(rx, rz, superSeed);
        }
        IrisData data = getLoader();
        Engine engine = data == null ? null : data.getEngine();
        SurfaceCacheStripe[] stripes = surfaceCache(data, engine).stripes();
        double lowerX = Math.floor(rx / SURFACE_GRID) * SURFACE_GRID;
        double lowerZ = Math.floor(rz / SURFACE_GRID) * SURFACE_GRID;
        double dx = (rx - lowerX) / SURFACE_GRID;
        double dz = (rz - lowerZ) / SURFACE_GRID;
        double northWest = cachedSurfaceHeight(lowerX, lowerZ, superSeed, stripes);
        if (dx == 0D && dz == 0D) {
            return northWest;
        }
        double north = dx == 0D ? northWest : IrisInterpolation.lerp(northWest,
                cachedSurfaceHeight(lowerX + SURFACE_GRID, lowerZ, superSeed, stripes), dx);
        double smooth = north;
        if (dz != 0D) {
            double southWest = cachedSurfaceHeight(lowerX, lowerZ + SURFACE_GRID, superSeed, stripes);
            double south = dx == 0D ? southWest : IrisInterpolation.lerp(southWest,
                    cachedSurfaceHeight(lowerX + SURFACE_GRID, lowerZ + SURFACE_GRID, superSeed, stripes), dx);
            smooth = IrisInterpolation.lerp(north, south, dz);
        }
        return surfaceDetail == 0D ? smooth
                : smooth + (sampleHeight(rx, rz, superSeed) - smooth) * surfaceDetail;
    }

    private double sampleHeight(double rx, double rz, long superSeed) {
        if (composite.isEmpty()) {
            return 0;
        }

        int hc = (int) ((cliffHeightMin * 10) + 10 + cliffHeightMax * getSeed() + offsetX + offsetZ);
        double h = multiplicitive ? 1 : 0;
        double tp = 0;
        double sampleX = (rx + offsetX) / zoom;
        double sampleZ = (rz + offsetZ) / zoom;

        if (composite.size() == 1) {
            if (multiplicitive) {
                h *= composite.get(0).getNoise(getSeed() + superSeed + hc, sampleX, sampleZ, getLoader());
            } else {
                tp += composite.get(0).getOpacity();
                h += composite.get(0).getNoise(getSeed() + superSeed + hc, sampleX, sampleZ, getLoader());
            }
        } else {
            for (IrisNoiseGenerator i : composite) {
                if (multiplicitive) {
                    h *= i.getNoise(getSeed() + superSeed + hc, sampleX, sampleZ, getLoader());
                } else {
                    tp += i.getOpacity();
                    h += i.getNoise(getSeed() + superSeed + hc, sampleX, sampleZ, getLoader());
                }
            }
        }

        double v = multiplicitive ? h * opacity : (h / tp) * opacity;

        if (Double.isNaN(v)) {
            v = 0;
        }

        v = hasCliffs() ? cliff(rx, rz, v, superSeed + 294596 + hc) : v;
        v = hasCellCracks() ? cell(rx, rz, v, superSeed + 48622 + hc) : v;

        return v;
    }

    private double cachedSurfaceHeight(double x, double z, long superSeed, SurfaceCacheStripe[] stripes) {
        if (x != (int) x || z != (int) z) {
            return sampleHeight(x, z, superSeed);
        }
        long key = ((long) (int) x << 32) | ((int) z & 0xffffffffL);
        long mixed = key ^ (key >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        SurfaceCacheStripe stripe = stripes[(int) mixed & (SURFACE_CACHE_STRIPES - 1)];
        SurfaceSample cached = stripe.get(key, superSeed);
        if (cached != null) {
            return cached.height();
        }
        double height = sampleHeight(x, z, superSeed);
        stripe.put(key, new SurfaceSample(superSeed, height));
        return height;
    }

    private SurfaceCache surfaceCache(IrisData data, Engine engine) {
        SurfaceCache cached = surfaceSamples;
        if (cached != null && cached.data() == data && cached.engine() == engine) {
            return cached;
        }
        synchronized (this) {
            cached = surfaceSamples;
            if (cached == null || cached.data() != data || cached.engine() != engine) {
                cached = new SurfaceCache(data, engine, createSurfaceCache());
                surfaceSamples = cached;
            }
            return cached;
        }
    }

    private static SurfaceCacheStripe[] createSurfaceCache() {
        SurfaceCacheStripe[] stripes = new SurfaceCacheStripe[SURFACE_CACHE_STRIPES];
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new SurfaceCacheStripe();
        }
        return stripes;
    }

    public double cell(double rx, double rz, double v, double superSeed) {
        getCellGenerator(getSeed() + 46222).setShuffle(getCellFractureShuffle());
        double fractureX = rx / getCellFractureZoom();
        double fractureZ = rz / getCellFractureZoom();
        return getCellGenerator(getSeed() + 46222).getDistance(fractureX, fractureZ) > getCellPercentSize() ? (v * getCellFractureHeight()) : v;
    }

    private boolean hasCellCracks() {
        return getCellFractureHeight() != 0;
    }

    public double getCliffHeight(double rx, double rz, double superSeed) {
        int hc = (int) ((cliffHeightMin * 10) + 10 + cliffHeightMax * getSeed() + offsetX + offsetZ);
        double sampleX = (rx + offsetX) / zoom;
        double sampleZ = (rz + offsetZ) / zoom;
        double h = cliffHeightGenerator.getNoise((long) (getSeed() + superSeed + hc), sampleX, sampleZ, getLoader());
        return IrisInterpolation.lerp(cliffHeightMin, cliffHeightMax, h);
    }

    public double cliff(double rx, double rz, double v, double superSeed) {
        double cliffHeight = getCliffHeight(rx, rz, superSeed - 34857);
        return (Math.round((v * 255D) / cliffHeight) * cliffHeight) / 255D;
    }

    public IrisGenerator rescale(double scale) {
        zoom /= scale;
        surfaceSamples = null;
        return this;
    }

    public KList<IrisNoiseGenerator> getAllComposites() {
        KList<IrisNoiseGenerator> g = new KList<>();

        for (IrisNoiseGenerator i : composite) {
            g.addAll(i.getAllComposites());
        }

        return g;
    }

    @Override
    public String getFolderName() {
        return "generators";
    }

    @Override
    public String getTypeName() {
        return "Generator";
    }

    private record SurfaceCache(IrisData data, Engine engine, SurfaceCacheStripe[] stripes) {
    }

    private record SurfaceSample(long seed, double height) {
    }

    private static final class SurfaceCacheStripe {
        private final Long2ObjectLinkedOpenHashMap<SurfaceSample> entries = new Long2ObjectLinkedOpenHashMap<>(16);

        private synchronized SurfaceSample get(long key, long seed) {
            SurfaceSample sample = entries.getAndMoveToLast(key);
            return sample != null && sample.seed() == seed ? sample : null;
        }

        private synchronized void put(long key, SurfaceSample sample) {
            entries.putAndMoveToLast(key, sample);
            if (entries.size() > SURFACE_CACHE_STRIPE_SIZE) {
                entries.removeFirst();
            }
        }
    }
}
