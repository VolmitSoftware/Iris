package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.material.BlockDataMergeSupport;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;

/** Stilt preparation observes all transformed blocks before the fill step runs. */
public final class PlacementModuleStilt implements PlacementModule {
    static final PlacementLifecycleDependency BOUNDS = new PlacementLifecycleDependency("stilt.bounds");
    static final PlacementLifecycleDependency FILL = new PlacementLifecycleDependency("stilt.fill");

    private final boolean fast;
    private final Mode mode;

    public enum Mode {
        DEFAULT, CENTER, MIN, ERODE, ORGANIC, CEILING
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
        if (mode == Mode.CEILING) {
            new ObjectModuleCeilingHang().install(lifecycle);
        }
        PlacementLifecycleDependency[] prerequisites = mode == Mode.CEILING
                ? new PlacementLifecycleDependency[]{PlacementModuleAnchor.HEIGHT, ObjectModuleCeilingHang.FLIP}
                : new PlacementLifecycleDependency[]{PlacementModuleAnchor.HEIGHT};
        lifecycle.add(BOUNDS, PlacementStage.COLLECT_BLOCK, (context, state, block) -> {
            if (IrisObjectShaping.shouldStilt(block.data())) {
                state.stiltLowest = Math.min(state.stiltLowest, block.vector().getBlockY());
                state.stiltHighest = Math.max(state.stiltHighest, block.vector().getBlockY());
            }
        }, prerequisites);
        lifecycle.add(FILL, PlacementStage.AFTER_BLOCKS, this::fill, PlacementModuleAnchor.HEIGHT, BOUNDS, PlacementModuleBlocks.COMPLETE);
    }

    private void fill(PlacementContext context, PlacementState state, Void ignored) {
        if (state.stiltLowest == Integer.MAX_VALUE) {
            return;
        }
        IrisObject self = context.self;
        IrisObjectPlacement config = state.config;
        IObjectPlacer placer = state.placer;
        IrisData rdata = context.rdata;
        RNG rng = context.rng;
        ObjectSpinKernel spin = state.spin;
        IrisBlockVector translateOffset = state.translateOffset;
        boolean translating = translateOffset != null;
        boolean ceilingHang = mode == Mode.CEILING;
        boolean organic = mode == Mode.ORGANIC || ceilingHang;
        boolean eroding = mode == Mode.ERODE;
        boolean hasEdits = !config.getEdit().isEmpty();
        boolean warped = !config.getWarp().isFlat();
        CNG surfaceWarp = warped ? config.getSurfaceWarp(rng, self.getLoader()) : null;
        double warpHalf = warped ? config.getWarp().getMultiplier() / 2D : 0D;
        int x = context.x;
        int z = context.z;
        int y = state.y;
        int lowest = state.stiltLowest;
        int topLayer = state.stiltHighest;
        int xx, zz;
        self.readLock.lock();
        try {
            VectorMap<PlatformBlockState> blocks = self.blocks;
            IrisStiltSettings settings = config.getStiltSettings();

            double erodeCentroidX = 0;
            double erodeCentroidZ = 0;
            double erodeMaxDist = 1;
            if (eroding) {
                int centroidCount = 0;
                VectorMap<PlatformBlockState>.Cursor centroidCursor = blocks.cursor();
                while (centroidCursor.next()) {
                    IrisBlockVector rot = centroidCursor.key().clone();
                    spin.rotate(rot);
                    if (translating) {
                        rot.add(translateOffset);
                    }
                    if (rot.getBlockY() == lowest) {
                        PlatformBlockState bd = centroidCursor.value();
                        if (bd != null && IrisObjectShaping.shouldStilt(bd)) {
                            erodeCentroidX += rot.getX();
                            erodeCentroidZ += rot.getZ();
                            centroidCount++;
                        }
                    }
                }
                if (centroidCount > 0) {
                    erodeCentroidX /= centroidCount;
                    erodeCentroidZ /= centroidCount;
                }
                VectorMap<PlatformBlockState>.Cursor spreadCursor = blocks.cursor();
                while (spreadCursor.next()) {
                    IrisBlockVector rot = spreadCursor.key().clone();
                    spin.rotate(rot);
                    if (translating) {
                        rot.add(translateOffset);
                    }
                    if (rot.getBlockY() == lowest) {
                        PlatformBlockState bd = spreadCursor.value();
                        if (bd != null && IrisObjectShaping.shouldStilt(bd)) {
                            double dx = rot.getX() - erodeCentroidX;
                            double dz = rot.getZ() - erodeCentroidZ;
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (dist > erodeMaxDist) {
                                erodeMaxDist = dist;
                            }
                        }
                    }
                }
            }

            VectorMap<PlatformBlockState>.Cursor stiltCursor = blocks.cursor();
            while (stiltCursor.next()) {
                IrisBlockVector g = stiltCursor.key();
                PlatformBlockState sourceData;
                try {
                    sourceData = stiltCursor.value();
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                    IrisLogging.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + self.getLoadKey() + " (stilt cme)");
                    sourceData = IrisObject.States.AIR;
                }

                if (sourceData == null) {
                    IrisLogging.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + self.getLoadKey() + " (stilt null)");
                    sourceData = IrisObject.States.AIR;
                }

                if (!IrisObjectShaping.shouldStilt(sourceData)) {
                    continue;
                }

                PlatformBlockState d = sourceData;
                if (settings != null && settings.getPalette() != null) {
                    d = config.getStiltSettings().getPalette().get(rng, x, y, z, rdata);
                } else {
                    String mat = IrisObjectShaping.materialKey(d);
                    if (mat.equals("minecraft:grass_block") || mat.equals("minecraft:mycelium") || mat.equals("minecraft:podzol") || mat.equals("minecraft:dirt_path")) {
                        d = B.getState("minecraft:dirt");
                    }
                }

                IrisBlockVector i = g.clone();
                spin.rotate(i);
                if (ceilingHang) {
                    i.setY(-i.getBlockY());
                }
                if (translating) {
                    i.add(translateOffset);
                }
                d = spin.rotate(d);

                int targetLayer = ceilingHang ? topLayer : lowest;
                if (i.getBlockY() != targetLayer)
                    continue;

                if (hasEdits) {
                    for (IrisObjectReplace j : config.getEdit()) {
                        if (rng.chance(j.getChance())) {
                            for (PlatformBlockState k : j.getFind(rdata)) {
                                if (d == null) {
                                    continue;
                                }
                                if (j.isExact() ? k.matches(d) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(d))) {
                                    PlatformBlockState newData = j.getReplace(rng, i.getX() + x, i.getY() + y, i.getZ() + z, rdata);

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

                if (d == null || !d.isOccluding())
                    continue;

                xx = x + (int) Math.round(i.getX());
                zz = z + (int) Math.round(i.getZ());

                if (warped) {
                    xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getX() + x, i.getY() + y, i.getZ() + z);
                    zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getZ() + z, i.getY() + y, i.getX() + x);
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
                            placer.set(xx, j, zz, d);
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
                            placer.set(xx, j, zz, d);
                        }
                    }
                    continue;
                }

                int highest = placer.getHighest(xx, zz, self.getLoader(), true);

                if (IrisProceduralBlocks.hasProperty(d, "waterlogged") && ObjectBlockPass.shouldAutoWaterlogBlock(context, state, xx, highest, zz)) {
                    d = d.withProperty("waterlogged", "true");
                }

                int lowerBound = highest - 1;
                if (settings != null) {
                    lowerBound -= config.getStiltSettings().getOverStilt() - rng.i(0, config.getStiltSettings().getYRand());
                    if (settings.getYMax() != 0)
                        lowerBound -= Math.min(config.getStiltSettings().getYMax() - (lowest + y - highest), 0);
                }

                if (eroding) {
                    double dx = i.getX() - erodeCentroidX;
                    double dz = i.getZ() - erodeCentroidZ;
                    double normalizedDist = Math.sqrt(dx * dx + dz * dz) / erodeMaxDist;
                    normalizedDist = Math.min(normalizedDist, 1.0);
                    int totalDepth = (lowest + y) - lowerBound;
                    int erodeDepth = (int) (totalDepth * Math.pow(1.0 - normalizedDist, 1.5));
                    lowerBound = (lowest + y) - erodeDepth;
                }

                for (int j = lowest + y; j > lowerBound; j--) {
                    PlatformBlockState fluidState = placer.get(xx, j, zz);
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
                    placer.set(xx, j, zz, d);
                }

            }
        } finally {
            self.readLock.unlock();
        }
    }
}
