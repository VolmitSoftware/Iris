package art.arcane.iris.engine.object.placer;

import art.arcane.iris.engine.object.*;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;

public class PlacementBehaviorStilt implements PlacementBehavior {

    private boolean fast;
    private Mode mode;
    public enum Mode {
        DEFAULT,
        CENTER,
        MIN,
        ERODE,
        ORGANIC
    }

    public PlacementBehaviorStilt(Mode mode, boolean fast) {
        this.mode = mode;
        this.fast = fast;
    }

    @Override
    public int getHeight(PlacementContext context) {
        return 0;
    }


    private void stilt(PlacementContext context) {
        context.self.readLock.lock();
        try {
            VectorMap<PlatformBlockState> blocks = context.self.blocks;
            IrisStiltSettings settings = context.config.getStiltSettings();

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
                    d = context.config.getStiltSettings().getPalette().get(rng, context.x, context.y, context.z, rdata);
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
                d = config.getRotation().rotate(d, spinx, spiny, spinz);

                int targetLayer = ceilingHang ? topLayer : lowest;
                if (i.getBlockY() != targetLayer)
                    continue;

                if (hasEdits) {
                    for (IrisObjectReplace j : context.config.getEdit()) {
                        if (rng.chance(j.getChance())) {
                            for (PlatformBlockState k : j.getFind(rdata)) {
                                if (d == null) {
                                    continue;
                                }
                                if (j.isExact() ? k.matches(d) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(d))) {
                                    PlatformBlockState newData = j.getReplace(rng, i.getX() + context.x, i.getY() + y, i.getZ() + context.z, rdata);

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

                xx = context.x + (int) Math.round(i.getX());
                zz = context.z + (int) Math.round(i.getZ());

                if (warped) {
                    xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getX() + context.x, i.getY() + context.y, i.getZ() + context.z);
                    zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getZ() + context.z, i.getY() + context.y, i.getX() + context.x);
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
                            context.placer.set(xx, j, zz, d);
                        }
                    } else {
                        int scan = 0;
                        int solidY = startY - 1;
                        while (scan < maxScan && !context.placer.isSolid(xx, solidY, zz)) {
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
                            context.placer.set(xx, j, zz, d);
                        }
                    }
                    continue;
                }

                int highest = context.placer.getHighest(xx, zz, self.getLoader(), true);

                if (IrisProceduralBlocks.hasProperty(d, "waterlogged") && shouldAutoWaterlogBlock(placer, config, yv, xx, highest, zz)) {
                    d = d.withProperty("waterlogged", "true");
                }

                int lowerBound = highest - 1;
                if (settings != null) {
                    lowerBound -= context.config.getStiltSettings().getOverStilt() - rng.i(0, context.config.getStiltSettings().getYRand());
                    if (settings.getYMax() != 0)
                        lowerBound -= Math.min(context.config.getStiltSettings().getYMax() - (lowest + y - highest), 0);
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
                    PlatformBlockState fluidState = context.placer.get(xx, j, zz);
                    if (B.isFluid(fluidState)) {
                        break;
                    }
                    if (eroding) {
                        int depth = (lowest + context.y) - j;
                        int totalDepth = (lowest + context.y) - lowerBound;
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
                        d = attachVineFaces(placer, d, xx, j, zz);
                    }
                    context.placer.set(xx, j, zz, d);
                }

            }
        } finally {
            context.self.readLock.unlock();
        }

    }


}
