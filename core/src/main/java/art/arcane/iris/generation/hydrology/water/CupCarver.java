package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

final class CupCarver {
    private static final long SALT_SITE = 0x43555053495445L;
    private static final long SALT_SITE_X = 0x43555053495858L;
    private static final long SALT_SITE_Z = 0x4355505349545aL;
    private static final long SALT_RADIUS = 0x43555052414449L;
    private static final long SALT_DEPTH = 0x43555044455054L;
    private static final long SALT_CHAIN = 0x43555043484149L;

    private final HydrologySettings settings;

    CupCarver(HydrologySettings settings) {
        this.settings = settings;
    }

    CupSet carve(DemGrid raw, WindowKey key, HydrologySampleBuffer samples, BitSet channelHint) {
        int count = raw.values().length;
        if (samples.height().length < count) {
            throw new IllegalArgumentException("Sample buffer holds " + samples.height().length + " cells, grid has " + count);
        }
        if (channelHint.length() > count) {
            throw new IllegalArgumentException("Channel hint reaches cell " + (channelHint.length() - 1) + ", grid has " + count);
        }
        List<CupSet.Cup> cups = new ArrayList<>();
        HydrologySettings.SeededPonds seeded = settings.ponds().seeded();
        if (seeded.enabled()) {
            for (int wz = key.wz() - 1; wz <= key.wz() + 1; wz++) {
                for (int wx = key.wx() - 1; wx <= key.wx() + 1; wx++) {
                    windowCups(raw, wx, wz, samples, cups);
                }
            }
            if (seeded.alongChannels()) {
                chainedCups(raw, samples, channelHint, cups);
            }
        }
        for (CupSet.Cup cup : cups) {
            cut(raw, cup);
        }
        return new CupSet(cups);
    }

    private void windowCups(DemGrid raw, int wx, int wz, HydrologySampleBuffer samples, List<CupSet.Cup> out) {
        float density = settings.ponds().seeded().density();
        if (!(density > 0f)) {
            return;
        }
        int split = (int) Math.ceil(Math.sqrt(density));
        double size = (double) WindowKey.SIZE / split;
        double chance = density / ((double) split * split);
        long seed = settings.seed();
        for (int sz = 0; sz < split; sz++) {
            for (int sx = 0; sx < split; sx++) {
                int siteX = wx * split + sx;
                int siteZ = wz * split + sz;
                int x = wx * WindowKey.SIZE + (int) Math.floor((sx + CellHash.unit(CellHash.mix(seed, SALT_SITE_X, siteX, siteZ))) * size);
                int z = wz * WindowKey.SIZE + (int) Math.floor((sz + CellHash.unit(CellHash.mix(seed, SALT_SITE_Z, siteX, siteZ))) * size);
                int cell = interiorCell(raw, x, z);
                if (cell < 0) {
                    continue;
                }
                float slope = slope(raw, samples, cell);
                if (suitable(samples, cell, slope)
                        && CellHash.unit(CellHash.mix(seed, SALT_SITE, siteX, siteZ)) < chance * samples.pondDensity()[cell]) {
                    addCup(raw, samples, cell, slope, siteX, siteZ, false, out);
                }
            }
        }
    }

    private void chainedCups(DemGrid raw, HydrologySampleBuffer samples, BitSet channels, List<CupSet.Cup> out) {
        HydrologySettings.IntRange spacing = settings.ponds().seeded().channelSpacing();
        double chance = Math.min(1D, 2D * raw.cell() / Math.max(1, spacing.max() - spacing.min()));
        List<Site> sites = new ArrayList<>();
        for (int cell = channels.nextSetBit(0); cell >= 0; cell = channels.nextSetBit(cell + 1)) {
            if (!interior(raw, cell)) {
                continue;
            }
            float slope = slope(raw, samples, cell);
            double roll = CellHash.unit(CellHash.mix(settings.seed(), SALT_CHAIN, raw.blockX(cell), raw.blockZ(cell)));
            if (suitable(samples, cell, slope) && roll < chance * samples.pondDensity()[cell]) {
                sites.add(new Site(roll, cell, slope));
            }
        }
        sites.sort(Comparator.comparingDouble(Site::roll).thenComparingInt(Site::cell));
        long minimum = (long) spacing.min() * spacing.min();
        List<CupSet.Cup> chained = new ArrayList<>();
        for (Site site : sites) {
            int x = raw.blockX(site.cell());
            int z = raw.blockZ(site.cell());
            if (clearOf(chained, x, z, minimum)) {
                addCup(raw, samples, site.cell(), site.slope(), x, z, true, chained);
            }
        }
        out.addAll(chained);
    }

    private static boolean clearOf(List<CupSet.Cup> cups, int x, int z, long minimumSquared) {
        for (CupSet.Cup cup : cups) {
            long dx = cup.x() - x;
            long dz = cup.z() - z;
            if (dx * dx + dz * dz < minimumSquared) {
                return false;
            }
        }
        return true;
    }

    private void addCup(DemGrid raw, HydrologySampleBuffer samples, int cell, float slope, int keyX, int keyZ, boolean chained,
                        List<CupSet.Cup> out) {
        HydrologySettings.SeededPonds seeded = settings.ponds().seeded();
        int depth = pick(seeded.depth(), CellHash.mix(settings.seed(), SALT_DEPTH, keyX, keyZ));
        int radius = pick(seeded.radius(), CellHash.mix(settings.seed(), SALT_RADIUS, keyX, keyZ));
        if (slope > 0f) {
            radius = (int) Math.min(radius, Math.floor(depth / (2D * slope)));
        }
        if (radius < seeded.radius().min()) {
            return;
        }
        float rim = samples.height()[cell] - slope * radius;
        out.add(new CupSet.Cup(raw.blockX(cell), raw.blockZ(cell), radius, rim, depth, chained));
    }

    private boolean suitable(HydrologySampleBuffer samples, int cell, float slope) {
        return (samples.permission()[cell] & HydrologySampleBuffer.ALLOW_PONDS) != 0
                && !samples.ocean()[cell]
                && samples.height()[cell] > settings.seaLevel()
                && slope <= settings.ponds().seeded().maxSlope();
    }

    private static float slope(DemGrid raw, HydrologySampleBuffer samples, int cell) {
        float[] heights = samples.height();
        int width = raw.width();
        float span = 2f * raw.cell();
        float gradientX = (heights[cell + 1] - heights[cell - 1]) / span;
        float gradientZ = (heights[cell + width] - heights[cell - width]) / span;
        return (float) Math.sqrt(gradientX * gradientX + gradientZ * gradientZ);
    }

    private static void cut(DemGrid raw, CupSet.Cup cup) {
        int cell = raw.cell();
        int i0 = Math.max(0, Math.ceilDiv(cup.x() - cup.radius() - raw.originX(), cell));
        int i1 = Math.min(raw.width() - 1, Math.floorDiv(cup.x() + cup.radius() - raw.originX(), cell));
        int j0 = Math.max(0, Math.ceilDiv(cup.z() - cup.radius() - raw.originZ(), cell));
        int j1 = Math.min(raw.depth() - 1, Math.floorDiv(cup.z() + cup.radius() - raw.originZ(), cell));
        float[] values = raw.values();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int index = raw.index(i, j);
                values[index] = Math.min(values[index], cup.floorAt(raw.originX() + i * cell, raw.originZ() + j * cell));
            }
        }
    }

    private static int interiorCell(DemGrid raw, int x, int z) {
        int i = Math.floorDiv(x - raw.originX() + (raw.cell() >> 1), raw.cell());
        int j = Math.floorDiv(z - raw.originZ() + (raw.cell() >> 1), raw.cell());
        if (i < 1 || j < 1 || i > raw.width() - 2 || j > raw.depth() - 2) {
            return -1;
        }
        return j * raw.width() + i;
    }

    private static boolean interior(DemGrid raw, int cell) {
        int i = cell % raw.width();
        int j = cell / raw.width();
        return i >= 1 && j >= 1 && i <= raw.width() - 2 && j <= raw.depth() - 2;
    }

    static int pick(HydrologySettings.IntRange range, long hash) {
        return range.min() + (int) Long.remainderUnsigned(hash, range.max() - range.min() + 1L);
    }

    private record Site(double roll, int cell, float slope) {
    }
}
