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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.cave.IrisCaveProfileSampler;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FloatingIslandSample {
    public static final int REJECT_NONE = 0;
    public static final int REJECT_NO_ENTRIES = 1;
    public static final int REJECT_NO_SEED = 2;
    public static final int REJECT_NO_PICK = 3;
    public static final int REJECT_ABOVE_HEIGHT = 4;
    public static final int REJECT_NO_THICKNESS = 5;
    public static final int REJECT_NO_SOLID = 6;
    public static final int REJECT_COUNT = 7;
    public static final int REJECT_CLUSTER = REJECT_NO_SEED;

    private static final int PINHOLE_CARDINAL_FILL = 3;
    private static final int PINHOLE_TOTAL_FILL = 7;
    private static final int CARVE_CARDINAL_SUPPORT = 2;
    private static final int CARVE_TOTAL_SUPPORT = 4;
    private static final double CARVE_SHELL_FRACTION = 0.14D;
    private static final double CARVE_MAX_VERTICAL_RUN_FRACTION = 0.18D;
    private static final ThreadLocal<int[]> LAST_REJECT = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<double[]> LAST_DENSITY = ThreadLocal.withInitial(() -> new double[2]);
    private static final ThreadLocal<HashMap<Long, FloatingIslandSample>> CHUNK_MEMO = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<IdentityHashMap<CNG, Long2DoubleOpenHashMap>> FOOTPRINT_MEMO = ThreadLocal.withInitial(IdentityHashMap::new);
    private static final AtomicBoolean NULL_CNG_WARNED = new AtomicBoolean(false);

    public static int getLastReject() {
        return LAST_REJECT.get()[0];
    }

    public static double getLastClusterValue() {
        return LAST_DENSITY.get()[0];
    }

    public static double getLastClusterThreshold() {
        return LAST_DENSITY.get()[1];
    }

    public static void clearThreadCaches() {
    }

    public static void clearChunkMemo() {
        CHUNK_MEMO.get().clear();
        FOOTPRINT_MEMO.get().clear();
    }

    public static FloatingIslandSample sampleMemoized(IrisBiome parent, int wx, int wz, int chunkHeight, long baseSeed, IrisData data, Engine engine, FloatingIslandBoundarySampler boundarySampler) {
        long key = (((long) wx) << 32) ^ (wz & 0xFFFFFFFFL);
        HashMap<Long, FloatingIslandSample> memo = CHUNK_MEMO.get();
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        FloatingIslandSample result = sample(parent, wx, wz, chunkHeight, baseSeed, data, engine, boundarySampler);
        memo.put(key, result);
        return result;
    }

    private static FloatingIslandSample reject(int code) {
        LAST_REJECT.get()[0] = code;
        return null;
    }

    private static void warnNullCng(String styleField, IrisBiome parent) {
        if (NULL_CNG_WARNED.compareAndSet(false, true)) {
            String biomeKey = parent == null ? "<unknown>" : parent.getLoadKey();
            IrisLogging.warn("Floating child biome on " + biomeKey + " has a null CNG for " + styleField
                    + " (style factory returned null or AtomicCache swallowed an exception); skipping floating sampling until pack is fixed");
        }
    }

    public final IrisFloatingChildBiomes entry;
    public final int islandBaseY;
    public final int thickness;
    public final int topIdx;
    public final int solidCount;
    public final boolean[] solidMask;
    private final IrisFloatingChildBiomes[] entryMask;
    private transient int cachedBottomIdx = -2;

    private FloatingIslandSample(IrisFloatingChildBiomes entry, int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask) {
        this(entry, islandBaseY, thickness, topIdx, solidCount, solidMask, null);
    }

    private FloatingIslandSample(IrisFloatingChildBiomes entry, int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask, IrisFloatingChildBiomes[] entryMask) {
        this.entry = entry;
        this.islandBaseY = islandBaseY;
        this.thickness = thickness;
        this.topIdx = topIdx;
        this.solidCount = solidCount;
        this.solidMask = solidMask;
        this.entryMask = entryMask;
    }

    static FloatingIslandSample constructForTest(int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask) {
        return new FloatingIslandSample(null, islandBaseY, thickness, topIdx, solidCount, solidMask);
    }

    static FloatingIslandSample constructForTest(IrisFloatingChildBiomes entry, int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask, IrisFloatingChildBiomes[] entryMask) {
        return new FloatingIslandSample(entry, islandBaseY, thickness, topIdx, solidCount, solidMask, entryMask);
    }

    public int topY() {
        return islandBaseY + topIdx;
    }

    public int bottomY() {
        if (cachedBottomIdx == -2) {
            cachedBottomIdx = -1;
            for (int i = 0; i < solidMask.length; i++) {
                if (solidMask[i]) {
                    cachedBottomIdx = i;
                    break;
                }
            }
        }
        return cachedBottomIdx == -1 ? -1 : islandBaseY + cachedBottomIdx;
    }

    public IrisFloatingChildBiomes entryAt(int maskIndex) {
        if (entryMask != null && maskIndex >= 0 && maskIndex < entryMask.length && entryMask[maskIndex] != null) {
            return entryMask[maskIndex];
        }

        return entry;
    }

    public IrisFloatingChildBiomes bottomEntry() {
        int bottomY = bottomY();
        if (bottomY < 0) {
            return entry;
        }

        return entryAt(bottomY - islandBaseY);
    }

    public boolean hasMergedEntries() {
        return entryMask != null;
    }

    public static long columnSeed(long baseSeed, int wx, int wz) {
        return baseSeed ^ ((long) wx * 341873128712L) ^ ((long) wz * 132897987541L);
    }

    private static int maximumEdgeFieldWidth(KList<IrisFloatingChildBiomes> entries, long baseSeed,
                                             IrisData data, IrisBiome parent) {
        int maximumWidth = FloatingIslandEdgeProfile.MIN_WIDTH;
        for (IrisFloatingChildBiomes entry : entries) {
            FloatingIslandEdgeProfile profile = resolveEdgeProfile(entry, baseSeed, data, parent);
            maximumWidth = Math.max(maximumWidth, profile.fieldWidth());
        }
        return maximumWidth;
    }

    private static FloatingIslandEdgeProfile resolveEdgeProfile(IrisFloatingChildBiomes entry, long baseSeed,
                                                                IrisData data, IrisBiome parent) {
        FloatingIslandEdgeProfile profile = entry.getEdgeTaperProfile(baseSeed, data);
        if (profile != null) {
            return profile;
        }
        warnNullCng("edgeTaperVariationStyle", parent);
        return FloatingIslandEdgeProfile.DEFAULT;
    }

    public static FloatingIslandSample sample(IrisBiome parent, int wx, int wz, int chunkHeight, long baseSeed, IrisData data, Engine engine, FloatingIslandBoundarySampler boundarySampler) {
        KList<IrisFloatingChildBiomes> entries = parent.getFloatingChildBiomes();
        if (entries == null || entries.isEmpty()) {
            return reject(REJECT_NO_ENTRIES);
        }

        int boundaryFieldWidth = maximumEdgeFieldWidth(entries, baseSeed, data, parent);
        int parentBoundaryDistance = boundarySampler.edgeDistance(parent, wx, wz, boundaryFieldWidth);

        if (parent.isMergeFloatingChildBiomes()) {
            return sampleMerged(parent, entries, wx, wz, chunkHeight, baseSeed, data, engine, boundarySampler,
                    parentBoundaryDistance, boundaryFieldWidth);
        }

        IrisFloatingChildBiomes entry;
        int ownershipBoundaryDistance = boundaryFieldWidth + 1;
        if (entries.size() == 1) {
            entry = entries.getFirst();
        } else {
            IrisFloatingChildBiomes reference = entries.getFirst();
            CNG picker = reference.getPickerCng(baseSeed, data);
            if (picker == null) {
                warnNullCng("pickerStyle", parent);
                return reject(REJECT_NO_PICK);
            }
            FloatingIslandBoundarySampler.OwnershipSample ownership = boundarySampler.ownership(
                    entries, picker, wx, wz, boundaryFieldWidth);
            entry = ownership.owner();
            if (entry == null) {
                return reject(REJECT_NO_PICK);
            }
            ownershipBoundaryDistance = ownership.boundaryDistance();
        }

        return sampleEntry(parent, entry, wx, wz, chunkHeight, baseSeed, data, engine, boundarySampler,
                parentBoundaryDistance, ownershipBoundaryDistance);
    }

    private static FloatingIslandSample sampleMerged(IrisBiome parent, KList<IrisFloatingChildBiomes> entries,
                                                     int wx, int wz, int chunkHeight, long baseSeed, IrisData data,
                                                     Engine engine, FloatingIslandBoundarySampler boundarySampler,
                                                     int parentBoundaryDistance, int boundaryFieldWidth) {
        KList<FloatingIslandSample> samples = new KList<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (IrisFloatingChildBiomes entry : entries) {
            FloatingIslandSample sample = sampleEntry(parent, entry, wx, wz, chunkHeight, baseSeed, data, engine,
                    boundarySampler, parentBoundaryDistance, boundaryFieldWidth + 1);
            if (sample == null) {
                continue;
            }

            int bottomY = sample.bottomY();
            if (bottomY < 0) {
                continue;
            }

            samples.add(sample);
            if (bottomY < minY) {
                minY = bottomY;
            }
            int topY = sample.topY();
            if (topY > maxY) {
                maxY = topY;
            }
        }

        if (samples.isEmpty() || minY > maxY) {
            return reject(REJECT_NO_SEED);
        }

        int thickness = maxY - minY + 1;
        boolean[] solidMask = new boolean[thickness];
        IrisFloatingChildBiomes[] entryMask = new IrisFloatingChildBiomes[thickness];
        int[] entryTopY = new int[thickness];
        Arrays.fill(entryTopY, Integer.MIN_VALUE);
        int solidCount = 0;

        for (FloatingIslandSample sample : samples) {
            int sampleTopY = sample.topY();
            for (int i = 0; i < sample.solidMask.length; i++) {
                if (!sample.solidMask[i]) {
                    continue;
                }

                int y = sample.islandBaseY + i;
                int mergedIndex = y - minY;
                if (!solidMask[mergedIndex]) {
                    solidMask[mergedIndex] = true;
                    solidCount++;
                }
                if (sampleTopY >= entryTopY[mergedIndex]) {
                    entryTopY[mergedIndex] = sampleTopY;
                    entryMask[mergedIndex] = sample.entryAt(i);
                }
            }
        }

        int topIdx = highestSolidIndex(solidMask);
        if (solidCount == 0 || topIdx < 0) {
            return reject(REJECT_NO_SOLID);
        }

        IrisFloatingChildBiomes topEntry = entryMask[topIdx] == null ? samples.getFirst().entry : entryMask[topIdx];
        LAST_REJECT.get()[0] = REJECT_NONE;
        return new FloatingIslandSample(topEntry, minY, thickness, topIdx, solidCount, solidMask, entryMask);
    }

    private static FloatingIslandSample sampleEntry(IrisBiome parent, IrisFloatingChildBiomes entry, int wx, int wz,
                                                    int chunkHeight, long baseSeed, IrisData data, Engine engine,
                                                    FloatingIslandBoundarySampler boundarySampler,
                                                    int parentBoundaryDistance, int ownershipBoundaryDistance) {
        FloatingIslandEdgeProfile edgeProfile = resolveEdgeProfile(entry, baseSeed, data, parent);
        CNG footprintCng = entry.getFootprintCng(baseSeed, data);
        if (footprintCng == null) {
            warnNullCng("footprintStyle", parent);
            return reject(REJECT_NO_SEED);
        }
        double threshold = Math.max(0, Math.min(1, entry.getFootprintThreshold()));
        double signedCut = (threshold * 2.0) - 1.0;
        FloatingIslandBoundarySampler.FootprintSample footprint = boundarySampler.footprint(
                footprintCng, wx, wz, signedCut, edgeProfile.fieldWidth());
        double signed = footprint.signed();

        double[] diag = LAST_DENSITY.get();
        diag[0] = signed;
        diag[1] = signedCut;

        NeighborSupport footprintSupport = new NeighborSupport(footprint.cardinalSupport(), footprint.diagonalSupport());
        if (!footprint.accepted()) {
            return reject(REJECT_NO_SEED);
        }
        int boundaryDistance = Math.min(footprint.boundaryDistance(),
                Math.min(parentBoundaryDistance, ownershipBoundaryDistance));
        double edgeFade = edgeProfile.fade(boundaryDistance, wx, wz);
        if (edgeFade <= 0.0D) {
            return reject(REJECT_NO_THICKNESS);
        }

        CNG altitudeCng = entry.getAltitudeCng(baseSeed, data);
        if (altitudeCng == null) {
            warnNullCng("altitudeStyle", parent);
            return reject(REJECT_NO_SEED);
        }
        double altNoise = altitudeCng.noise(wx, wz);
        double altClamped = Math.max(0, Math.min(1, altNoise));
        int worldMin = engine.getWorld().minHeight();
        int minAlt = Math.max(0, entry.getMinHeightAboveSurface() - worldMin);
        int maxAlt = Math.max(minAlt, entry.getMaxHeightAboveSurface() - worldMin);
        int baseY = minAlt + (int) Math.round(altClamped * (maxAlt - minAlt));

        IrisBiome target = entry.getRealBiome(parent, data);
        int fullTopHeight = computeTopHeight(entry, target, engine, baseSeed, wx, wz, data);
        int topH = roundedEdgeHeight(fullTopHeight, edgeFade);

        CNG bottomCng = entry.getBottomCng(baseSeed, data);
        if (bottomCng == null) {
            warnNullCng("bottomStyle", parent);
            return reject(REJECT_NO_SEED);
        }
        double bottomNoise = bottomCng.noise(wx, wz);
        double bottomClamped = Math.max(0, Math.min(1, bottomNoise));
        double bottomShaped = Math.pow(bottomClamped, Math.max(0.1, entry.getBottomExponent()));
        int minDepth = Math.max(0, entry.getBottomDepthMin());
        int maxDepth = Math.max(minDepth, entry.getBottomDepthMax());
        int depth = roundedEdgeDepth(minDepth, maxDepth, bottomShaped, edgeFade);
        if (topH == 0 && depth == 0) {
            double fullDepth = minDepth + (bottomShaped * (maxDepth - minDepth));
            if (fullDepth > 0.0D) {
                depth = 1;
            } else if (fullTopHeight > 0) {
                topH = 1;
            } else {
                return reject(REJECT_NO_THICKNESS);
            }
        }
        int topY = baseY + topH;
        int botY = baseY - depth;

        Integer minAbsoluteY = entry.getMinAbsoluteY();
        if (minAbsoluteY != null && botY < minAbsoluteY - worldMin) {
            botY = minAbsoluteY - worldMin;
        }
        Integer maxAbsoluteY = entry.getMaxAbsoluteY();
        if (maxAbsoluteY != null && topY > maxAbsoluteY - worldMin) {
            topY = maxAbsoluteY - worldMin;
        }

        if (botY < 0) {
            botY = 0;
        }
        if (topY >= chunkHeight) {
            topY = chunkHeight - 1;
        }
        if (topY < botY) {
            return reject(REJECT_ABOVE_HEIGHT);
        }

        int thickness = topY - botY + 1;
        int maxThickness = Math.max(1, entry.getMaxThickness());
        if (thickness > maxThickness) {
            botY = topY - maxThickness + 1;
            if (botY < 0) {
                botY = 0;
            }
            thickness = topY - botY + 1;
        }
        if (thickness <= 0) {
            return reject(REJECT_NO_THICKNESS);
        }

        boolean[] solidMask = new boolean[thickness];
        CNG wallWarp = entry.getWallWarpCng(baseSeed, data);
        double warpAmp = Math.max(0, entry.getWallWarpAmplitude());
        double effectiveWarpAmp = effectiveWallWarpAmplitude(warpAmp, edgeFade);
        IrisCaveProfileSampler carvingProfileSampler = entry.getCarvingProfileSampler(engine, data);
        CNG carve = carvingProfileSampler == null && !entry.hasCarvingReference() ? entry.getCarveCng(baseSeed, data) : null;
        double carveThreshold = entry.getCarveThreshold();
        boolean useWarp = wallWarp != null && effectiveWarpAmp > 0;
        boolean useProfileCarve = carvingProfileSampler != null;
        boolean useCarve = directCarveEnabled(entry, carvingProfileSampler, carve, carveThreshold);
        boolean[] lateralCarveMask = useProfileCarve || useCarve ? new boolean[thickness] : null;
        boolean baseCarveInterior = lateralCarveMask != null && canCarveLaterally(edgeFade, footprintSupport);

        if (useWarp) {
            for (int k = 0; k < thickness; k++) {
                int wy = botY + k;
                boolean layerSolid = layerFootprintSolid(footprintCng, wallWarp, true, effectiveWarpAmp, wx, wy, wz, signedCut);
                NeighborSupport layerSupport = layerNeighborSupport(footprintCng, wallWarp, true, effectiveWarpAmp, wx, wy, wz, signedCut);
                if (layerSolid && !layerSupport.hasSolidSupport()) {
                    continue;
                }
                if (!layerSolid && !layerSupport.canFillPinhole()) {
                    continue;
                }
                solidMask[k] = true;
                if (lateralCarveMask != null) {
                    lateralCarveMask[k] = baseCarveInterior && layerSolid && layerSupport.isFullySurrounded();
                }
            }
        } else {
            Arrays.fill(solidMask, true);
            if (baseCarveInterior) {
                Arrays.fill(lateralCarveMask, true);
            }
        }

        int solidCount = solidifyUncarvedInterior(solidMask);
        if (useProfileCarve) {
            solidCount = carveSolidInterior(solidMask, lateralCarveMask, botY, wx, wz, carvingProfileSampler, carveThreshold);
        } else if (useCarve) {
            solidCount = carveSolidInterior(solidMask, lateralCarveMask, botY, wx, wz, carve, carveThreshold);
        }
        int highestSolidIdx = highestSolidIndex(solidMask);

        if (solidCount == 0 || highestSolidIdx < 0) {
            return reject(REJECT_NO_SOLID);
        }

        int topIdx = highestSolidIdx;

        LAST_REJECT.get()[0] = REJECT_NONE;
        return new FloatingIslandSample(entry, botY, thickness, topIdx, solidCount, solidMask);
    }

    static int roundedEdgeHeight(int topHeight, double edgeFade) {
        return Math.max(0, (int) Math.round(Math.max(0, topHeight) * Math.max(0, Math.min(1, edgeFade))));
    }

    static int roundedEdgeDepth(int minDepth, int maxDepth, double bottomShaped, double edgeFade) {
        int min = Math.max(0, minDepth);
        int max = Math.max(min, maxDepth);
        double shaped = Math.max(0, Math.min(1, bottomShaped));
        double fade = Math.max(0, Math.min(1, edgeFade));
        double fullDepth = min + shaped * (max - min);
        return (int) Math.round(fullDepth * fade);
    }

    static double effectiveWallWarpAmplitude(double wallWarpAmplitude, double edgeFade) {
        double amplitude = Math.max(0, wallWarpAmplitude);
        double fade = Math.max(0, Math.min(1, edgeFade));
        return amplitude * fade;
    }

    static boolean directCarveEnabled(IrisFloatingChildBiomes entry, IrisCaveProfileSampler carvingProfileSampler, CNG carve, double carveThreshold) {
        return carvingProfileSampler == null && (entry == null || !entry.hasCarvingReference()) && carve != null && carveThreshold < 1.0;
    }

    static boolean canCarveLaterally(double edgeFade, NeighborSupport support) {
        return edgeFade >= 1.0 && support.isFullySurrounded();
    }

    static int carveSolidInterior(boolean[] solidMask, boolean[] lateralCarveMask, int botY, int wx, int wz, CNG carve, double carveThreshold) {
        return carveSolidInterior(solidMask, lateralCarveMask, botY, wx, wz, (x, y, z) -> {
            double carveNoise = carve.noise(x, y, z);
            double carveClamped = Math.max(0, Math.min(1, carveNoise));
            return carveClamped > carveThreshold;
        });
    }

    static int carveSolidInterior(boolean[] solidMask, boolean[] lateralCarveMask, int botY, int wx, int wz, IrisCaveProfileSampler carve, double carveThreshold) {
        return carveSolidInterior(solidMask, lateralCarveMask, botY, wx, wz, (x, y, z) -> carve.shouldCarve(x, y, z, carveThreshold));
    }

    private static int carveSolidInterior(boolean[] solidMask, boolean[] lateralCarveMask, int botY, int wx, int wz, CarveSampler carve) {
        int firstSolid = -1;
        int lastSolid = -1;
        for (int i = 0; i < solidMask.length; i++) {
            if (!solidMask[i]) {
                continue;
            }
            if (firstSolid < 0) {
                firstSolid = i;
            }
            lastSolid = i;
        }
        if (firstSolid < 0) {
            return 0;
        }
        int span = lastSolid - firstSolid + 1;
        int shell = carveShellThickness(span);
        int carveStart = firstSolid + shell;
        int carveEnd = lastSolid - shell;
        boolean[] carveMask = new boolean[solidMask.length];
        if (carveStart <= carveEnd) {
            for (int i = carveStart; i <= carveEnd; i++) {
                if (!solidMask[i] || !lateralCarveMask[i]) {
                    continue;
                }

                int y = botY + i;
                if (carve.shouldCarve(wx, y, wz) && hasCarveClusterSupport(carve, wx, y, wz)) {
                    carveMask[i] = true;
                }
            }
            clampVerticalCarveRuns(carveMask, carveStart, carveEnd, maxVerticalCarveRun(span));
            for (int i = carveStart; i <= carveEnd; i++) {
                if (carveMask[i]) {
                    solidMask[i] = false;
                }
            }
        }
        int count = 0;
        for (boolean solid : solidMask) {
            if (solid) {
                count++;
            }
        }
        return count;
    }

    static int carveShellThickness(int solidSpan) {
        if (solidSpan <= 4) {
            return 1;
        }

        int proportional = (int) Math.ceil(solidSpan * CARVE_SHELL_FRACTION);
        return Math.max(2, Math.min(5, proportional));
    }

    static int maxVerticalCarveRun(int solidSpan) {
        int proportional = (int) Math.round(solidSpan * CARVE_MAX_VERTICAL_RUN_FRACTION);
        return Math.max(2, Math.min(6, proportional));
    }

    static void clampVerticalCarveRuns(boolean[] carveMask, int start, int end, int maxRun) {
        int i = start;
        while (i <= end) {
            if (!carveMask[i]) {
                i++;
                continue;
            }

            int runStart = i;
            while (i <= end && carveMask[i]) {
                i++;
            }
            int runEnd = i - 1;
            int runLength = runEnd - runStart + 1;
            if (runLength <= maxRun) {
                continue;
            }

            int keepStart = runStart + ((runLength - maxRun) / 2);
            int keepEnd = keepStart + maxRun - 1;
            for (int j = runStart; j <= runEnd; j++) {
                carveMask[j] = j >= keepStart && j <= keepEnd;
            }
        }
    }

    private static boolean hasCarveClusterSupport(CarveSampler carve, int wx, int wy, int wz) {
        int cardinal = 0;
        int diagonal = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (!carve.shouldCarve(wx + dx, wy, wz + dz)) {
                    continue;
                }
                if (Math.abs(dx) + Math.abs(dz) == 1) {
                    cardinal++;
                } else {
                    diagonal++;
                }
            }
        }
        return cardinal >= CARVE_CARDINAL_SUPPORT && cardinal + diagonal >= CARVE_TOTAL_SUPPORT;
    }

    static boolean hasFootprintNeighborSupport(CNG footprintCng, int wx, int wz, double signedCut) {
        return footprintNeighborSupport(footprintCng, wx, wz, signedCut).hasSolidSupport();
    }

    static boolean isFootprintPinholeRepairable(NeighborSupport support) {
        return support.canFillPinhole();
    }

    static NeighborSupport footprintNeighborSupport(CNG footprintCng, int wx, int wz, double signedCut) {
        int cardinal = 0;
        int diagonal = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double signed = footprintSigned(footprintCng, wx + dx, wz + dz);
                if (signed <= signedCut) {
                    continue;
                }
                if (Math.abs(dx) + Math.abs(dz) == 1) {
                    cardinal++;
                } else {
                    diagonal++;
                }
            }
        }
        return new NeighborSupport(cardinal, diagonal);
    }

    static NeighborSupport layerNeighborSupport(CNG footprintCng, CNG wallWarp, boolean useWarp, double warpAmp, int wx, int wy, int wz, double signedCut) {
        int cardinal = 0;
        int diagonal = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (!layerFootprintSolid(footprintCng, wallWarp, useWarp, warpAmp, wx + dx, wy, wz + dz, signedCut)) {
                    continue;
                }
                if (Math.abs(dx) + Math.abs(dz) == 1) {
                    cardinal++;
                } else {
                    diagonal++;
                }
            }
        }
        return new NeighborSupport(cardinal, diagonal);
    }

    static boolean layerFootprintSolid(CNG footprintCng, CNG wallWarp, boolean useWarp, double warpAmp, int wx, int wy, int wz, double signedCut) {
        double sx = wx;
        double sz = wz;
        if (useWarp) {
            double wnX = wallWarp.noise(wx, wy, wz);
            double signedWarpX = signedFromUnit(wnX);
            sx = wx + signedWarpX * warpAmp;
            double wnZ = wallWarp.noise(wx + 1987.3, wy, wz + 2341.1);
            double signedWarpZ = signedFromUnit(wnZ);
            sz = wz + signedWarpZ * warpAmp;
        }
        double layerFoot = footprintCng.noise(sx, sz);
        return signedFromUnit(layerFoot) > signedCut;
    }

    private static double footprintSigned(CNG footprintCng, int wx, int wz) {
        IdentityHashMap<CNG, Long2DoubleOpenHashMap> memoByCng = FOOTPRINT_MEMO.get();
        Long2DoubleOpenHashMap memo = memoByCng.get(footprintCng);
        if (memo == null) {
            memo = new Long2DoubleOpenHashMap(512);
            memo.defaultReturnValue(Double.NaN);
            memoByCng.put(footprintCng, memo);
        }

        long key = columnKey(wx, wz);
        double cached = memo.get(key);
        if (!Double.isNaN(cached)) {
            return cached;
        }

        double signed = signedFromUnit(footprintCng.noise(wx, wz));
        memo.put(key, signed);
        return signed;
    }

    private static long columnKey(int wx, int wz) {
        return ((long) wx << 32) ^ (wz & 0xFFFFFFFFL);
    }

    private static double signedFromUnit(double value) {
        return (Math.max(0, Math.min(1, value)) * 2.0) - 1.0;
    }

    static int solidifyUncarvedInterior(boolean[] solidMask) {
        int firstSolid = -1;
        int lastSolid = -1;
        for (int i = 0; i < solidMask.length; i++) {
            if (!solidMask[i]) {
                continue;
            }
            if (firstSolid < 0) {
                firstSolid = i;
            }
            lastSolid = i;
        }
        if (firstSolid < 0) {
            return 0;
        }
        for (int i = firstSolid; i <= lastSolid; i++) {
            solidMask[i] = true;
        }
        return lastSolid - firstSolid + 1;
    }

    private static int highestSolidIndex(boolean[] solidMask) {
        for (int i = solidMask.length - 1; i >= 0; i--) {
            if (solidMask[i]) {
                return i;
            }
        }
        return -1;
    }

    @FunctionalInterface
    private interface CarveSampler {
        boolean shouldCarve(int x, int y, int z);
    }

    static final class NeighborSupport {
        private final int cardinal;
        private final int diagonal;

        NeighborSupport(int cardinal, int diagonal) {
            this.cardinal = cardinal;
            this.diagonal = diagonal;
        }

        int cardinal() {
            return cardinal;
        }

        int diagonal() {
            return diagonal;
        }

        int total() {
            return cardinal + diagonal;
        }

        boolean hasSolidSupport() {
            return cardinal > 0 || diagonal >= 2;
        }

        boolean canFillPinhole() {
            return cardinal >= PINHOLE_CARDINAL_FILL && total() >= PINHOLE_TOTAL_FILL;
        }

        boolean isFullySurrounded() {
            return cardinal == 4 && diagonal == 4;
        }
    }

    private static int computeTopHeight(IrisFloatingChildBiomes entry, IrisBiome target, Engine engine, long baseSeed, int wx, int wz, IrisData data) {
        int maxTopHeight = Math.max(0, entry.getMaxTopHeight());
        if (maxTopHeight == 0) {
            return 0;
        }
        return switch (entry.getTopShapeMode()) {
            case FLAT -> maxTopHeight;
            case NOISE -> {
                CNG topCng = entry.getTopShapeCng(baseSeed, data);
                if (topCng == null) {
                    warnNullCng("topShapeStyle", null);
                    yield maxTopHeight / 2;
                }
                double n = topCng.noise(wx, wz);
                double clamped = Math.max(0, Math.min(1, n));
                double amp = Math.max(0, Math.min(1, entry.getTopShapeAmp()));
                yield (int) Math.round(clamped * amp * maxTopHeight);
            }
            case BIOME -> {
                if (target == null) {
                    yield maxTopHeight / 2;
                }
                double h = target.getHeight(engine, wx, wz, baseSeed);
                int rounded = (int) Math.round(h);
                if (rounded < 0) {
                    yield 0;
                }
                yield Math.min(maxTopHeight, rounded);
            }
        };
    }
}
