package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.BlockDataMergeSupport;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;

import java.util.LinkedHashMap;
import java.util.Map;

/** Repeats the object's stilt layer down to the ground or up into the ceiling; cave placements seat on the carve and fill with rock. */
public final class PlacementModuleStilt implements PlacementModule {
    static final PlacementLifecycleDependency FILL = new PlacementLifecycleDependency("stilt.fill");
    static final PlacementLifecycleDependency SEAT = new PlacementLifecycleDependency("stilt.cave-seat");

    private static final int CAVE_SEAT_REACH = 16;
    private static final int CAVE_FILL_REACH = 8;
    private static final int NO_GROUND = -1;
    private static final long CAVE_JITTER_SALT = 0x5ca1ab1e0c5a7e01L;
    private static final long CAVE_SCRATCH_SALT = 0x5ca1ab1e0c5a7e02L;

    private final boolean fast;
    private final Mode mode;

    public enum Mode {
        DEFAULT, CENTER, MIN, ERODE, ORGANIC, CEILING
    }

    private record StiltLayers(int lowest, int highest) {
        boolean isEmpty() {
            return lowest == Integer.MAX_VALUE;
        }
    }

    public PlacementModuleStilt(Mode mode, boolean fast) {
        this.mode = mode;
        this.fast = fast;
    }

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        PlacementModule height = switch (mode) {
            case CENTER, ORGANIC, CEILING -> new PlacementModuleCenterHeight();
            case MIN -> new PlacementModuleHeight(PlacementModuleHeight.Mode.MIN, fast);
            case DEFAULT, ERODE -> new PlacementModuleHeight(PlacementModuleHeight.Mode.MAX, fast);
        };
        height.install(lifecycle);
        if (mode == Mode.ORGANIC || mode == Mode.CEILING) {
            lifecycle.add(SEAT, PlacementStage.BEFORE_BLOCKS, this::seat, PlacementModuleAnchor.FINAL_HEIGHT);
        }
        lifecycle.add(FILL, PlacementStage.AFTER_BLOCKS, this::fill, PlacementModuleAnchor.HEIGHT,
                PlacementModuleBlocks.COMPLETE, PlacementModuleTreeGrounding.ROOTS);
    }

    private static StiltLayers stiltLayers(ObjectPlacementBake bake, PlacementPalette palette) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int index = 0; index < bake.count(); index++) {
            if (palette.stiltLayer(bake.paletteIndex(index))) {
                int layer = bake.layerY(index);
                lowest = Math.min(lowest, layer);
                highest = Math.max(highest, layer);
            }
        }
        return new StiltLayers(lowest, highest);
    }

    private void seat(PlacementContext context, PlacementState state, Void ignored) {
        if (state.grounding != null || state.placer.caveCell(context.x, state.y, context.z) == null) {
            return;
        }
        PlacementModuleBlocks.bake(context, state);
        ObjectPlacementBake bake = state.bake;
        PlacementPalette palette = state.palette;
        StiltLayers layers = stiltLayers(bake, palette);
        if (layers.isEmpty()) {
            return;
        }
        boolean up = mode == Mode.CEILING;
        int targetLayer = up ? layers.highest() : layers.lowest();
        IrisStiltSettings settings = state.config.getStiltSettings();
        int reach = Math.max(CAVE_SEAT_REACH, settings != null ? settings.getOrganicMaxScan() : 0);
        boolean underwater = state.config.isUnderwater();
        boolean rock = dominantRock(bake, palette, targetLayer) != null;
        boolean trunk = false;
        for (int index = 0; !rock && !trunk && index < bake.count(); index++) {
            int entry = bake.paletteIndex(index);
            trunk = bake.layerY(index) == targetLayer && palette.shouldStilt(entry) && IrisObjectShaping.isTrunk(palette.stiltDefault(entry));
        }
        int closest = NO_GROUND;
        boolean candidates = false;
        boolean buried = false;
        boolean exposed = false;
        for (int index = 0; index < bake.count() && closest != 0; index++) {
            if (bake.layerY(index) != targetLayer || !palette.shouldStilt(bake.paletteIndex(index))) {
                continue;
            }
            candidates = true;
            if (trunk && !IrisObjectShaping.isTrunk(palette.stiltDefault(bake.paletteIndex(index)))) {
                continue;
            }
            int cellX = context.x + bake.offsetX(index);
            int cellZ = context.z + bake.offsetZ(index);
            int startY = targetLayer + state.y;
            if (!rock && state.placer.caveCell(cellX, startY, cellZ) == IObjectPlacer.CaveCell.GROUND) {
                buried = true;
                continue;
            }
            exposed = true;
            int gap = caveGap(state.placer, cellX, startY, cellZ, up, reach, underwater);
            if (gap != NO_GROUND && (closest == NO_GROUND || gap < closest)) {
                closest = gap;
            }
        }
        if (buried && !exposed) {
            return;
        }
        if (!candidates) {
            return;
        }
        if (closest == NO_GROUND) {
            state.caveUnseated = true;
            return;
        }
        state.y += up ? closest : -closest;
    }

    private static int caveGap(IObjectPlacer placer, int x, int startY, int z, boolean up, int reach, boolean underwater) {
        if (placer.caveCell(x, startY, z) == IObjectPlacer.CaveCell.GROUND) {
            return 0;
        }
        int direction = up ? 1 : -1;
        for (int step = 1; step <= reach; step++) {
            IObjectPlacer.CaveCell cell = placer.caveCell(x, startY + direction * step, z);
            if (cell == IObjectPlacer.CaveCell.GROUND) {
                return step - 1;
            }
            if (cell == IObjectPlacer.CaveCell.FLUID && !underwater) {
                return NO_GROUND;
            }
        }
        return NO_GROUND;
    }

    private static NativeBlockState dominantRock(ObjectPlacementBake bake, PlacementPalette palette, int targetLayer) {
        Map<NativeBlockState, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < bake.count(); index++) {
            int entry = bake.paletteIndex(index);
            if (bake.layerY(index) == targetLayer && palette.shouldStilt(entry)) {
                NativeBlockState state = palette.stiltDefault(entry);
                if (IrisObjectShaping.isStiltFiller(state)) {
                    counts.merge(state, 1, Integer::sum);
                }
            }
        }
        NativeBlockState best = null;
        int bestCount = 0;
        for (Map.Entry<NativeBlockState, Integer> count : counts.entrySet()) {
            if (count.getValue() > bestCount) {
                best = count.getKey();
                bestCount = count.getValue();
            }
        }
        return best;
    }

    private void fillCaveColumn(PlacementState state, NativeBlockState own, NativeBlockState rock, int x, int startY, int z, long seed, boolean up, boolean waterlogCandidate) {
        NativeBlockState filler = IrisObjectShaping.isStiltFiller(own) ? own : rock;
        if (filler == null) {
            return;
        }
        IObjectPlacer placer = state.placer;
        IrisStiltSettings settings = state.config.getStiltSettings();
        int maxScan = settings != null ? Math.max(1, settings.getOrganicMaxScan()) : 48;
        if (placer.caveCell(x, startY, z) == IObjectPlacer.CaveCell.GROUND) {
            return;
        }
        boolean underwater = state.config.isUnderwater();
        int limit = Math.min(maxScan, CAVE_FILL_REACH);
        int direction = up ? 1 : -1;
        int open = 0;
        boolean grounded = false;
        for (int step = 1; step <= limit; step++) {
            IObjectPlacer.CaveCell cell = placer.caveCell(x, startY + direction * step, z);
            if (cell == IObjectPlacer.CaveCell.GROUND) {
                grounded = true;
                break;
            }
            if (cell == IObjectPlacer.CaveCell.FLUID && !underwater) {
                break;
            }
            open = step;
        }
        int depth = open;
        double scratch = 0;
        if (!grounded) {
            int jitterMax = settings != null ? Math.max(0, settings.getOrganicJitter()) : 3;
            scratch = settings != null ? Math.max(0, Math.min(1, settings.getOrganicScratch())) : 0.55;
            depth -= jitterMax > 0 ? Math.floorMod(CellHash.mix(seed, CAVE_JITTER_SALT, x, z), jitterMax + 1) : 0;
        }
        long scratchHash = CellHash.mix(seed, CAVE_SCRATCH_SALT, x, z);
        for (int step = 1; step <= depth; step++) {
            double ratio = (double) step / depth;
            if (scratch > 0 && ratio > 1.0 - scratch) {
                double skipChance = (ratio - (1.0 - scratch)) / scratch;
                if (CellHash.unit(CellHash.mix(scratchHash, step, x, z)) < skipChance * 0.7) {
                    break;
                }
            }
            int y = startY + direction * step;
            placer.set(x, y, z, ObjectBlockPass.normalizePlacedWaterlogging(filler, placer, x, y, z, waterlogCandidate));
        }
    }

    private void fill(PlacementContext context, PlacementState state, Void ignored) {
        if (state.caveUnseated) {
            state.reject("cave-no-ground");
            return;
        }
        if (state.grounding != null) {
            return;
        }
        ObjectPlacementBake bake = state.bake;
        PlacementPalette palette = state.palette;
        StiltLayers layers = stiltLayers(bake, palette);
        if (layers.isEmpty()) {
            return;
        }
        IrisObject self = context.self;
        IrisObjectPlacement config = state.config;
        IObjectPlacer placer = state.placer;
        IrisData rdata = context.rdata;
        RNG rng = context.rng;
        boolean ceilingHang = mode == Mode.CEILING;
        boolean organic = mode == Mode.ORGANIC || ceilingHang;
        boolean eroding = mode == Mode.ERODE;
        boolean hasEdits = !config.getEdit().isEmpty();
        boolean warped = !config.getWarp().isFlat();
        boolean waterlogCandidate = config.isWaterloggable() || config.isUnderwater();
        CNG surfaceWarp = warped ? config.getSurfaceWarp(rng, self.getLoader()) : null;
        double warpHalf = warped ? config.getWarp().getMultiplier() / 2D : 0D;
        double[] exact = warped || hasEdits || eroding ? bake.exact(self, state.spin) : null;
        int count = bake.count();
        int x = context.x;
        int z = context.z;
        int y = state.y;
        int lowest = layers.lowest();
        int topLayer = layers.highest();
        int xx, zz;
        IrisStiltSettings settings = config.getStiltSettings();

        double erodeCentroidX = 0;
        double erodeCentroidZ = 0;
        double erodeMaxDist = 1;
        if (eroding) {
            int centroidCount = 0;
            for (int index = 0; index < count; index++) {
                if (bake.layerY(index) == lowest && palette.shouldStilt(bake.paletteIndex(index))) {
                    erodeCentroidX += exact[index * 3];
                    erodeCentroidZ += exact[index * 3 + 2];
                    centroidCount++;
                }
            }
            if (centroidCount > 0) {
                erodeCentroidX /= centroidCount;
                erodeCentroidZ /= centroidCount;
            }
            for (int index = 0; index < count; index++) {
                if (bake.layerY(index) == lowest && palette.shouldStilt(bake.paletteIndex(index))) {
                    double dx = exact[index * 3] - erodeCentroidX;
                    double dz = exact[index * 3 + 2] - erodeCentroidZ;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > erodeMaxDist) {
                        erodeMaxDist = dist;
                    }
                }
            }
        }

        int targetLayer = ceilingHang ? topLayer : lowest;
        boolean cave = organic && placer.caveCell(x, y, z) != null;
        NativeBlockState caveRock = cave ? dominantRock(bake, palette, targetLayer) : null;
        Engine engine = placer.getEngine();
        long caveSeed = cave && engine != null && engine.getSeedManager() != null ? engine.getSeedManager().getComplex() : 0L;
        for (int index = 0; index < count; index++) {
            int entry = bake.paletteIndex(index);
            if (!palette.shouldStilt(entry)) {
                continue;
            }

            NativeBlockState d;
            if (settings != null && settings.getPalette() != null) {
                d = settings.getPalette().get(rng, x, y, z, rdata);
            } else {
                d = palette.stiltDefault(entry);
            }

            if (bake.layerY(index) != targetLayer)
                continue;
            d = palette.rotate(d);

            if (hasEdits) {
                double exactX = exact[index * 3];
                double exactY = exact[index * 3 + 1];
                double exactZ = exact[index * 3 + 2];
                for (IrisObjectReplace j : config.getEdit()) {
                    if (rng.chance(j.getChance())) {
                        for (NativeBlockState k : j.getFind(rdata)) {
                            if (d == null) {
                                continue;
                            }
                            if (j.isExact() ? k.matches(d) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(d))) {
                                NativeBlockState newData = j.getReplace(rng, exactX + x, exactY + y, exactZ + z, rdata);

                                if (IrisObjectShaping.materialKey(newData).equals(IrisObjectShaping.materialKey(d))) {
                                    d = BlockDataMergeSupport.merge(d, newData);
                                } else {
                                    d = newData;
                                }
                            }
                        }
                    }
                }
            }

            if (d == null || !palette.shouldStiltPlaced(d))
                continue;

            xx = x + bake.offsetX(index);
            zz = z + bake.offsetZ(index);

            if (warped) {
                double exactX = exact[index * 3];
                double exactY = exact[index * 3 + 1];
                double exactZ = exact[index * 3 + 2];
                xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, exactX + x, exactY + y, exactZ + z);
                zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, exactZ + z, exactY + y, exactX + x);
            }

            if (cave) {
                fillCaveColumn(state, d, caveRock, xx, targetLayer + y, zz, caveSeed, ceilingHang, waterlogCandidate);
                continue;
            }

            if (organic) {
                int startY = targetLayer + y;
                int maxScan = settings != null ? Math.max(1, settings.getOrganicMaxScan()) : 48;
                int jitterMax = settings != null ? Math.max(0, settings.getOrganicJitter()) : 3;
                double scratch = settings != null ? Math.max(0, Math.min(1, settings.getOrganicScratch())) : 0.55;
                long colHash = ((long) xx * 341873128712L) ^ ((long) zz * 132897987541L);
                int jitter = jitterMax > 0 ? (int) (Math.abs(colHash) % (jitterMax + 1)) : 0;

                if (ceilingHang) {
                    int scan = 0;
                    int solidY = startY + 1;
                    while (scan < maxScan && !placer.isSolid(xx, solidY, zz)) {
                        solidY++;
                        scan++;
                    }
                    int topBound = (scan < maxScan ? solidY - 1 : startY + Math.min(maxScan, 8)) - jitter;
                    int total = topBound - startY;
                    for (int j = startY; j <= topBound; j++) {
                        if (scratch > 0 && total > 0) {
                            double ratio = (double) (j - startY) / total;
                            if (ratio > (1.0 - scratch)) {
                                long sh = ((long) xx * 341873128712L) ^ ((long) j * 132897987541L) ^ ((long) zz * 735791245321L);
                                double skipChance = (ratio - (1.0 - scratch)) / scratch;
                                if ((Math.abs(sh) % 1000) / 1000.0 < skipChance * 0.7) {
                                    continue;
                                }
                            }
                        }
                        placer.set(xx, j, zz, ObjectBlockPass.normalizePlacedWaterlogging(d, placer, xx, j, zz, waterlogCandidate));
                    }
                } else {
                    int scan = 0;
                    int solidY = startY - 1;
                    while (scan < maxScan && !placer.isSolid(xx, solidY, zz)) {
                        solidY--;
                        scan++;
                    }
                    int bottomBound = (scan < maxScan ? solidY + 1 : startY - Math.min(maxScan, 8)) + jitter;
                    int total = startY - bottomBound;
                    for (int j = startY; j >= bottomBound; j--) {
                        if (scratch > 0 && total > 0) {
                            double ratio = (double) (startY - j) / total;
                            if (ratio > (1.0 - scratch)) {
                                long sh = ((long) xx * 341873128712L) ^ ((long) j * 132897987541L) ^ ((long) zz * 735791245321L);
                                double skipChance = (ratio - (1.0 - scratch)) / scratch;
                                if ((Math.abs(sh) % 1000) / 1000.0 < skipChance * 0.7) {
                                    continue;
                                }
                            }
                        }
                        placer.set(xx, j, zz, ObjectBlockPass.normalizePlacedWaterlogging(d, placer, xx, j, zz, waterlogCandidate));
                    }
                }
                continue;
            }

            int highest = placer.getHighest(xx, zz, self.getLoader(), true);

            int lowerBound = highest - 1;
            if (settings != null) {
                lowerBound -= settings.getOverStilt() - rng.i(0, settings.getYRand());
                if (settings.getYMax() != 0)
                    lowerBound -= Math.min(settings.getYMax() - (lowest + y - highest), 0);
            }

            if (eroding) {
                double dx = exact[index * 3] - erodeCentroidX;
                double dz = exact[index * 3 + 2] - erodeCentroidZ;
                double normalizedDist = Math.sqrt(dx * dx + dz * dz) / erodeMaxDist;
                normalizedDist = Math.min(normalizedDist, 1.0);
                int totalDepth = (lowest + y) - lowerBound;
                int erodeDepth = (int) (totalDepth * Math.pow(1.0 - normalizedDist, 1.5));
                lowerBound = (lowest + y) - erodeDepth;
            }

            for (int j = lowest + y; j > lowerBound; j--) {
                NativeBlockState fluidState = placer.get(xx, j, zz);
                if (B.isFluid(fluidState)) {
                    break;
                }
                if (eroding) {
                    int depth = (lowest + y) - j;
                    int totalDepth = (lowest + y) - lowerBound;
                    double depthRatio = totalDepth > 0 ? (double) depth / totalDepth : 0;
                    if (depthRatio > 0.4) {
                        long hash = ((long) (xx * 341873128712L) ^ ((long) j * 132897987541L) ^ ((long) zz * 735791245321L));
                        double skipChance = (depthRatio - 0.4) / 0.6;
                        if ((Math.abs(hash) % 1000) / 1000.0 < skipChance * 0.7) {
                            continue;
                        }
                    }
                }

                if (B.isVineBlock(d)) {
                    d = ObjectBlockPass.attachVineFaces(placer, d, xx, j, zz);
                }
                placer.set(xx, j, zz, ObjectBlockPass.normalizePlacedWaterlogging(d, placer, xx, j, zz, waterlogCandidate));
            }
        }
    }
}
