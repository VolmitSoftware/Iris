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

package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.modules.floating.FloatingChildBiome;
import art.arcane.iris.engine.object.terrain.carving.CaveProfileSampler;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.ModuleLog;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.ResourceId;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
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
    private static final ThreadLocal<HashMap<Column, FloatingIslandSample>> CHUNK_MEMO = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<IdentityHashMap<NoiseSampler, HashMap<Column, Double>>> FOOTPRINT_MEMO = ThreadLocal.withInitial(IdentityHashMap::new);
    private static final int FOOTPRINT_MEMO_CAPACITY = 512;
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

    public static FloatingIslandSample sampleMemoized(ResourceId parent, int wx, int wz, int chunkHeight, FloatingWorld world, FloatingIslandBoundarySampler boundarySampler, ModuleLog log) {
        Column key = new Column(wx, wz);
        HashMap<Column, FloatingIslandSample> memo = CHUNK_MEMO.get();
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        FloatingIslandSample result = sample(parent, wx, wz, chunkHeight, world, boundarySampler, log);
        memo.put(key, result);
        return result;
    }

    private static FloatingIslandSample reject(int code) {
        LAST_REJECT.get()[0] = code;
        return null;
    }

    private static void warnNullCng(String styleField, ResourceId parent, ModuleLog log) {
        if (NULL_CNG_WARNED.compareAndSet(false, true)) {
            String biomeKey = parent == null ? "<unknown>" : parent.toString();
            log.warn("Floating child biome on " + biomeKey + " has no noise for " + styleField
                    + " (the noise factory failed or AtomicCache swallowed an exception); skipping floating sampling until pack is fixed");
        }
    }

    public final ResolvedFloatingChild entry;
    public final int islandBaseY;
    public final int thickness;
    public final int topIdx;
    public final int solidCount;
    public final boolean[] solidMask;
    private final ResolvedFloatingChild[] entryMask;
    private transient int cachedBottomIdx = -2;

    private FloatingIslandSample(ResolvedFloatingChild entry, int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask) {
        this(entry, islandBaseY, thickness, topIdx, solidCount, solidMask, null);
    }

    private FloatingIslandSample(ResolvedFloatingChild entry, int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask, ResolvedFloatingChild[] entryMask) {
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

    static FloatingIslandSample constructForTest(ResolvedFloatingChild entry, int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask, ResolvedFloatingChild[] entryMask) {
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

    public ResolvedFloatingChild entryAt(int maskIndex) {
        if (entryMask != null && maskIndex >= 0 && maskIndex < entryMask.length && entryMask[maskIndex] != null) {
            return entryMask[maskIndex];
        }

        return entry;
    }

    public ResolvedFloatingChild bottomEntry() {
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

    private static int maximumEdgeFieldWidth(KList<ResolvedFloatingChild> entries, ResourceId parent, ModuleLog log) {
        int maximumWidth = FloatingIslandEdgeProfile.MIN_WIDTH;
        for (ResolvedFloatingChild entry : entries) {
            FloatingIslandEdgeProfile profile = resolveEdgeProfile(entry, parent, log);
            maximumWidth = Math.max(maximumWidth, profile.fieldWidth());
        }
        return maximumWidth;
    }

    private static FloatingIslandEdgeProfile resolveEdgeProfile(ResolvedFloatingChild entry, ResourceId parent, ModuleLog log) {
        FloatingIslandEdgeProfile profile = entry.edgeTaperProfile();
        if (profile != null) {
            return profile;
        }
        warnNullCng("edgeTaperVariationStyle", parent, log);
        return FloatingIslandEdgeProfile.DEFAULT;
    }

    public static FloatingIslandSample sample(ResourceId parent, int wx, int wz, int chunkHeight, FloatingWorld world, FloatingIslandBoundarySampler boundarySampler, ModuleLog log) {
        List<FloatingChildBiome> settings = world.declaredBy(parent);
        if (settings.isEmpty()) {
            return reject(REJECT_NO_ENTRIES);
        }
        KList<ResolvedFloatingChild> entries = new KList<>(world.resolve(settings));
        return sample(parent, entries, wx, wz, chunkHeight, world, boundarySampler, log);
    }

    static FloatingIslandSample sample(ResourceId parent, KList<ResolvedFloatingChild> entries, int wx, int wz, int chunkHeight, FloatingWorld world, FloatingIslandBoundarySampler boundarySampler, ModuleLog log) {
        int boundaryFieldWidth = maximumEdgeFieldWidth(entries, parent, log);
        int parentBoundaryDistance = boundarySampler.edgeDistance(parent, wx, wz, boundaryFieldWidth);

        if (world.merges(parent)) {
            return sampleMerged(parent, entries, wx, wz, chunkHeight, world, boundarySampler,
                    parentBoundaryDistance, boundaryFieldWidth, log);
        }

        ResolvedFloatingChild entry;
        int ownershipBoundaryDistance = boundaryFieldWidth + 1;
        if (entries.size() == 1) {
            entry = entries.getFirst();
        } else {
            ResolvedFloatingChild reference = entries.getFirst();
            NoiseSampler picker = reference.pickerNoise();
            if (picker == null) {
                warnNullCng("pickerStyle", parent, log);
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

        return sampleEntry(parent, entry, wx, wz, chunkHeight, world, boundarySampler,
                parentBoundaryDistance, ownershipBoundaryDistance, log);
    }

    private static FloatingIslandSample sampleMerged(ResourceId parent, KList<ResolvedFloatingChild> entries,
                                                     int wx, int wz, int chunkHeight, FloatingWorld world,
                                                     FloatingIslandBoundarySampler boundarySampler,
                                                     int parentBoundaryDistance, int boundaryFieldWidth, ModuleLog log) {
        KList<FloatingIslandSample> samples = new KList<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (ResolvedFloatingChild entry : entries) {
            FloatingIslandSample sample = sampleEntry(parent, entry, wx, wz, chunkHeight, world,
                    boundarySampler, parentBoundaryDistance, boundaryFieldWidth + 1, log);
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
        ResolvedFloatingChild[] entryMask = new ResolvedFloatingChild[thickness];
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

        ResolvedFloatingChild topEntry = entryMask[topIdx] == null ? samples.getFirst().entry : entryMask[topIdx];
        LAST_REJECT.get()[0] = REJECT_NONE;
        return new FloatingIslandSample(topEntry, minY, thickness, topIdx, solidCount, solidMask, entryMask);
    }

    private static FloatingIslandSample sampleEntry(ResourceId parent, ResolvedFloatingChild entry, int wx, int wz,
                                                    int chunkHeight, FloatingWorld world,
                                                    FloatingIslandBoundarySampler boundarySampler,
                                                    int parentBoundaryDistance, int ownershipBoundaryDistance, ModuleLog log) {
        FloatingIslandEdgeProfile edgeProfile = resolveEdgeProfile(entry, parent, log);
        NoiseSampler footprintCng = entry.footprintNoise();
        if (footprintCng == null) {
            warnNullCng("footprintStyle", parent, log);
            return reject(REJECT_NO_SEED);
        }
        double threshold = Math.max(0, Math.min(1, entry.settings().footprintThreshold()));
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

        NoiseSampler altitudeCng = entry.altitudeNoise();
        if (altitudeCng == null) {
            warnNullCng("altitudeStyle", parent, log);
            return reject(REJECT_NO_SEED);
        }
        double altNoise = altitudeCng.sample(wx, wz);
        double altClamped = Math.max(0, Math.min(1, altNoise));
        int worldMin = world.minWorldY();
        int minAlt = Math.max(0, entry.settings().minHeightAboveSurface() - worldMin);
        int maxAlt = Math.max(minAlt, entry.settings().maxHeightAboveSurface() - worldMin);
        int baseY = minAlt + (int) Math.round(altClamped * (maxAlt - minAlt));

        ResourceId target = entry.realBiome(parent);
        int fullTopHeight = computeTopHeight(entry, target, world, wx, wz, log);
        int topH = roundedEdgeHeight(fullTopHeight, edgeFade);

        NoiseSampler bottomCng = entry.bottomNoise();
        if (bottomCng == null) {
            warnNullCng("bottomStyle", parent, log);
            return reject(REJECT_NO_SEED);
        }
        double bottomNoise = bottomCng.sample(wx, wz);
        double bottomClamped = Math.max(0, Math.min(1, bottomNoise));
        double bottomShaped = Math.pow(bottomClamped, Math.max(0.1, entry.settings().bottomExponent()));
        int minDepth = Math.max(0, entry.settings().bottomDepthMin());
        int maxDepth = Math.max(minDepth, entry.settings().bottomDepthMax());
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

        OptionalInt minAbsoluteY = entry.settings().minAbsoluteY();
        if (minAbsoluteY.isPresent() && botY < minAbsoluteY.getAsInt() - worldMin) {
            botY = minAbsoluteY.getAsInt() - worldMin;
        }
        OptionalInt maxAbsoluteY = entry.settings().maxAbsoluteY();
        if (maxAbsoluteY.isPresent() && topY > maxAbsoluteY.getAsInt() - worldMin) {
            topY = maxAbsoluteY.getAsInt() - worldMin;
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
        int maxThickness = Math.max(1, entry.settings().maxThickness());
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
        NoiseSampler wallWarp = entry.wallWarpNoise();
        double warpAmp = Math.max(0, entry.settings().wallWarpAmplitude());
        double effectiveWarpAmp = effectiveWallWarpAmplitude(warpAmp, edgeFade);
        CaveProfileSampler carvingProfileSampler = entry.carvingProfileSampler();
        NoiseSampler carve = carvingProfileSampler == null && !entry.hasCarvingReference() ? entry.carveNoise() : null;
        double carveThreshold = entry.settings().carveThreshold();
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

    static boolean directCarveEnabled(ResolvedFloatingChild entry, CaveProfileSampler carvingProfileSampler, NoiseSampler carve, double carveThreshold) {
        return carvingProfileSampler == null && (entry == null || !entry.hasCarvingReference()) && carve != null && carveThreshold < 1.0;
    }

    static boolean canCarveLaterally(double edgeFade, NeighborSupport support) {
        return edgeFade >= 1.0 && support.isFullySurrounded();
    }

    static int carveSolidInterior(boolean[] solidMask, boolean[] lateralCarveMask, int botY, int wx, int wz, NoiseSampler carve, double carveThreshold) {
        return carveSolidInterior(solidMask, lateralCarveMask, botY, wx, wz, (x, y, z) -> {
            double carveNoise = carve.sample(x, y, z);
            double carveClamped = Math.max(0, Math.min(1, carveNoise));
            return carveClamped > carveThreshold;
        });
    }

    static int carveSolidInterior(boolean[] solidMask, boolean[] lateralCarveMask, int botY, int wx, int wz, CaveProfileSampler carve, double carveThreshold) {
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

    static boolean hasFootprintNeighborSupport(NoiseSampler footprintCng, int wx, int wz, double signedCut) {
        return footprintNeighborSupport(footprintCng, wx, wz, signedCut).hasSolidSupport();
    }

    static boolean isFootprintPinholeRepairable(NeighborSupport support) {
        return support.canFillPinhole();
    }

    static NeighborSupport footprintNeighborSupport(NoiseSampler footprintCng, int wx, int wz, double signedCut) {
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

    static NeighborSupport layerNeighborSupport(NoiseSampler footprintCng, NoiseSampler wallWarp, boolean useWarp, double warpAmp, int wx, int wy, int wz, double signedCut) {
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

    static boolean layerFootprintSolid(NoiseSampler footprintCng, NoiseSampler wallWarp, boolean useWarp, double warpAmp, int wx, int wy, int wz, double signedCut) {
        double sx = wx;
        double sz = wz;
        if (useWarp) {
            double wnX = wallWarp.sample(wx, wy, wz);
            double signedWarpX = signedFromUnit(wnX);
            sx = wx + signedWarpX * warpAmp;
            double wnZ = wallWarp.sample(wx + 1987.3, wy, wz + 2341.1);
            double signedWarpZ = signedFromUnit(wnZ);
            sz = wz + signedWarpZ * warpAmp;
        }
        double layerFoot = footprintCng.sample(sx, sz);
        return signedFromUnit(layerFoot) > signedCut;
    }

    private static double footprintSigned(NoiseSampler footprintCng, int wx, int wz) {
        HashMap<Column, Double> memo = FOOTPRINT_MEMO.get().computeIfAbsent(footprintCng, ignored -> new HashMap<>(FOOTPRINT_MEMO_CAPACITY));
        Column key = new Column(wx, wz);
        Double cached = memo.get(key);
        if (cached != null) {
            return cached;
        }

        double signed = signedFromUnit(footprintCng.sample(wx, wz));
        memo.put(key, signed);
        return signed;
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

    private static int computeTopHeight(ResolvedFloatingChild entry, ResourceId target, FloatingWorld world, int wx, int wz, ModuleLog log) {
        int maxTopHeight = Math.max(0, entry.settings().maxTopHeight());
        if (maxTopHeight == 0) {
            return 0;
        }
        return switch (entry.settings().topShapeMode()) {
            case FLAT -> maxTopHeight;
            case NOISE -> {
                NoiseSampler topCng = entry.topShapeNoise();
                if (topCng == null) {
                    warnNullCng("topShapeStyle", null, log);
                    yield maxTopHeight / 2;
                }
                double n = topCng.sample(wx, wz);
                double clamped = Math.max(0, Math.min(1, n));
                double amp = Math.max(0, Math.min(1, entry.settings().topShapeAmp()));
                yield (int) Math.round(clamped * amp * maxTopHeight);
            }
            case BIOME -> {
                if (target == null) {
                    yield maxTopHeight / 2;
                }
                double h = world.biomeHeight(target, wx, wz);
                int rounded = (int) Math.round(h);
                if (rounded < 0) {
                    yield 0;
                }
                yield Math.min(maxTopHeight, rounded);
            }
        };
    }
}
