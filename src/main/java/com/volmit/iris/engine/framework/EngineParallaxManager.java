/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.jigsaw.PlannedStructure;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.biome.IrisBiomeMutation;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.object.deposits.IrisDepositGenerator;
import com.volmit.iris.engine.object.feature.IrisFeature;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.engine.object.feature.IrisFeaturePotential;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawStructure;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawStructurePlacement;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.engine.object.objects.IrisObjectPlacement;
import com.volmit.iris.engine.object.objects.IrisObjectScale;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.engine.parallax.ParallaxAccess;
import com.volmit.iris.engine.parallax.ParallaxChunkMeta;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.DataProvider;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public interface EngineParallaxManager extends DataProvider, IObjectPlacer {
    BlockData AIR = B.get("AIR");

    Engine getEngine();

    int getParallaxSize();

    default ParallaxAccess getParallaxAccess() {
        return getEngine().getParallax();
    }

    default IrisData getData() {
        return getEngine().getData();
    }

    default IrisComplex getComplex() {
        return getEngine().getComplex();
    }

    default KList<IrisRegion> getAllRegions() {
        KList<IrisRegion> r = new KList<>();

        for (String i : getEngine().getDimension().getRegions()) {
            r.add(getEngine().getData().getRegionLoader().load(i));
        }

        return r;
    }

    default KList<IrisFeaturePotential> getAllFeatures() {
        KList<IrisFeaturePotential> r = new KList<>();
        r.addAll(getEngine().getDimension().getFeatures());
        getAllRegions().forEach((i) -> r.addAll(i.getFeatures()));
        getAllBiomes().forEach((i) -> r.addAll(i.getFeatures()));
        return r;
    }

    default KList<IrisBiome> getAllBiomes() {
        KList<IrisBiome> r = new KList<>();

        for (IrisRegion i : getAllRegions()) {
            r.addAll(i.getAllBiomes(this));
        }

        return r;
    }

    /**
     * Verifies the chunk correctly has all the parallax objects it should.
     * This method should not have to be written but why not.
     * Thread Safe, Designed to run async
     *
     * @param c the bukkit chunk
     */
    default int repairChunk(Chunk c) {
        ParallaxChunkMeta m = getParallaxAccess().getMetaR(c.getX(), c.getZ());
        Hunk<String> o = getParallaxAccess().getObjectsR(c.getX(), c.getZ());
        Hunk<BlockData> b = getParallaxAccess().getBlocksR(c.getX(), c.getZ());
        ChunkSnapshot snapshot = c.getChunkSnapshot(false, false, false);
        KList<Runnable> queue = new KList<>();

        o.iterateSync((x, y, z, s) -> {
            if (s != null) {

                BlockData bd = b.get(x, y, z);
                if (bd != null) {
                    BlockData bdx = snapshot.getBlockData(x, y, z);

                    if (!bdx.getMaterial().equals(bd.getMaterial())) {
                        queue.add(() -> c.getBlock(x, y, z).setBlockData(bd, false));
                    }
                }
            }
        });

        AtomicBoolean bx = new AtomicBoolean(false);
        J.s(() -> {
            queue.forEach(Runnable::run);
            bx.set(true);
        });

        while (!bx.get()) {
            J.sleep(50);
        }

        return queue.size();
    }

    default void insertTileEntities(int x, int z, Consumer4<Integer, Integer, Integer, TileData<? extends TileState>> consumer) {
        ParallaxChunkMeta meta = getParallaxAccess().getMetaRW(x >> 4, z >> 4);

        if (meta.isTilesGenerated()) {
            return;
        }

        meta.setTilesGenerated(true);

        getParallaxAccess().getTilesRW(x >> 4, z >> 4).iterateSync((a, b, c, d) -> {
            if (d != null) {
                consumer.accept(a, b, c, d);
            }
        });
    }

    @ChunkCoordinates
    default void insertParallax(int x, int z, Hunk<BlockData> data) {
        if (!getEngine().getDimension().isPlaceObjects()) {
            return;
        }

        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            ParallaxChunkMeta meta = getParallaxAccess().getMetaR(x, z);

            if (!meta.isParallaxGenerated()) {
                generateParallaxLayer(x, z, true);
                meta = getParallaxAccess().getMetaR(x, z);
            }

            if (!meta.isObjects()) {
                getEngine().getMetrics().getParallaxInsert().put(p.getMilliseconds());
                return;
            }

            getParallaxAccess().getBlocksR(x, z).iterateSync((a, b, c, d) -> {
                if (d != null) {
                    data.set(a, b, c, d);
                }
            });

            getEngine().getMetrics().getParallaxInsert().put(p.getMilliseconds());
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Failed to insert parallax at chunk " + x + " " + z);
            e.printStackTrace();
        }
    }

    @ChunkCoordinates
    default KList<IrisFeaturePositional> getFeaturesInChunk(int x, int z) {
        KList<IrisFeaturePositional> pos = new KList<>();

        for (IrisFeaturePositional i : getEngine().getDimension().getSpecificFeatures()) {
            if (i.shouldFilter((x << 4) + 8, (z << 4) + 8, getEngine().getComplex().getRng(), getData())) {
                pos.add(i);
            }
        }

        for (IrisFeaturePositional i : getParallaxAccess().getMetaR(x, z).getFeatures()) {
            if (i.shouldFilter((x << 4) + 8, (z << 4) + 8, getEngine().getComplex().getRng(), getData())) {
                pos.add(i);
            }
        }

        pos.add(forEachFeature(x << 4, z << 4));
        pos.add(forEachFeature(((x + 1) << 4) - 1, z << 4));
        pos.add(forEachFeature(x << 4, ((z + 1) << 4) - 1));
        pos.add(forEachFeature(((x + 1) << 4) - 1, ((z + 1) << 4) - 1));
        pos.removeDuplicates();
        return pos;
    }

    @BlockCoordinates
    default KList<IrisFeaturePositional> forEachFeature(double x, double z) {
        KList<IrisFeaturePositional> pos = new KList<>();

        if (!getEngine().getDimension().hasFeatures(getEngine())) {
            return pos;
        }

        for (IrisFeaturePositional i : getEngine().getDimension().getSpecificFeatures()) {
            if (i.shouldFilter(x, z, getEngine().getComplex().getRng(), getData())) {
                pos.add(i);
            }
        }

        int s = (int) Math.ceil(getParallaxSize() / 2D);

        int i, j;
        int cx = (int) x >> 4;
        int cz = (int) z >> 4;

        for (i = -s; i <= s; i++) {
            for (j = -s; j <= s; j++) {
                ParallaxChunkMeta m = getParallaxAccess().getMetaR(i + cx, j + cz);

                try {
                    for (IrisFeaturePositional k : m.getFeatures()) {
                        if (k.shouldFilter(x, z, getEngine().getComplex().getRng(), getData())) {
                            pos.add(k);
                        }
                    }
                } catch (Throwable e) {
                    Iris.error("FILTER ERROR" + " AT " + (cx + i) + " " + (j + cz));
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }

        return pos;
    }

    @ChunkCoordinates
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    default void generateParallaxArea(int x, int z) {
        if (!getEngine().getDimension().isPlaceObjects()) {
            return;
        }

        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();

            int s = (int) Math.ceil(getParallaxSize() / 2D);
            int i, j;
            KList<Runnable> after = new KList<>();
            int bs = (int) Math.pow((s * 2) + 1, 2);
            BurstExecutor burst = getEngine().getTarget().getBurster().burst(bs);
            for (i = -s; i <= s; i++) {
                for (j = -s; j <= s; j++) {
                    int xx = i + x;
                    int zz = j + z;
                    int xxx = xx << 4;
                    int zzz = zz << 4;
                    if (!getParallaxAccess().isFeatureGenerated(xx, zz)) {
                        getParallaxAccess().setFeatureGenerated(xx, zz);
                        burst.queue(() -> {
                            RNG rng = new RNG(Cache.key(xx, zz) + getEngine().getTarget().getWorld().seed());
                            IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
                            IrisBiome biome = getComplex().getTrueBiomeStreamNoFeatures().get(xxx, zzz);
                            generateParallaxFeatures(rng, xx, zz, region, biome);
                        });
                    }
                }
            }

            burst.complete();

            if (getEngine().getDimension().isPlaceObjects()) {
                burst = getEngine().getTarget().getBurster().burst(bs);

                for (i = -s; i <= s; i++) {
                    int ii = i;
                    for (j = -s; j <= s; j++) {
                        int jj = j;
                        burst.queue(() -> {
                            KList<Runnable> a = generateParallaxVacuumLayer(ii + x, jj + z);
                            synchronized (a) {
                                after.addAll(a);
                            }
                        });
                    }
                }

                burst.complete();
                burst = getEngine().getTarget().getBurster().burst(bs);

                for (i = -s; i <= s; i++) {
                    int ii = i;
                    for (j = -s; j <= s; j++) {
                        int jj = j;
                        burst.queue(() -> generateParallaxLayer(ii + x, jj + z));
                    }
                }

                burst.complete();
            }

            getEngine().getTarget().getBurster().burst(after);
            getParallaxAccess().setChunkGenerated(x, z);
            p.end();
            getEngine().getMetrics().getParallax().put(p.getMilliseconds());
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Failed to generate parallax in " + x + " " + z);
            e.printStackTrace();
        }
    }

    @ChunkCoordinates
    default KList<Runnable> generateParallaxVacuumLayer(int x, int z) {
        KList<Runnable> after = new KList<>();
        if (getParallaxAccess().isParallaxGenerated(x, z)) {
            return after;
        }

        if (getEngine().getDimension().isPlaceObjects()) {
            int xx = x << 4;
            int zz = z << 4;
            RNG rng = new RNG(Cache.key(x, z)).nextParallelRNG(getEngine().getTarget().getWorld().seed());
            IrisRegion region = getComplex().getRegionStream().get(xx + 8, zz + 8);
            IrisBiome biome = getComplex().getTrueBiomeStreamNoFeatures().get(xx + 8, zz + 8);
            after.addAll(generateParallaxJigsaw(rng, x, z, biome, region));
            generateParallaxSurface(rng, x, z, biome, region, true);
            generateParallaxMutations(rng, x, z, true);
        }

        return after;
    }

    default void generateParallaxLayer(int x, int z, boolean force) {
        if (!force && getParallaxAccess().isParallaxGenerated(x, z)) {
            return;
        }

        int xx = x << 4;
        int zz = z << 4;
        getParallaxAccess().setParallaxGenerated(x, z);
        RNG rng = new RNG(Cache.key(x, z)).nextParallelRNG(getEngine().getTarget().getWorld().seed());
        IrisBiome biome = getComplex().getTrueBiomeStreamNoFeatures().get(xx + 8, zz + 8);
        IrisRegion region = getComplex().getRegionStream().get(xx + 8, zz + 8);
        generateParallaxSurface(rng, x, z, biome, region, false);
        generateParallaxMutations(rng, x, z, false);
    }

    @ChunkCoordinates
    default void generateParallaxFeatures(RNG rng, int cx, int cz, IrisRegion region, IrisBiome biome) {
        for (IrisFeaturePotential i : getEngine().getDimension().getFeatures()) {
            placeZone(rng, cx, cz, i);
        }

        for (IrisFeaturePotential i : region.getFeatures()) {
            placeZone(rng, cx, cz, i);
        }

        for (IrisFeaturePotential i : biome.getFeatures()) {
            placeZone(rng, cx, cz, i);
        }
    }

    default void placeZone(RNG rng, int cx, int cz, IrisFeaturePotential i) {
        if (i.hasZone(rng, cx, cz)) {
            getParallaxAccess().getMetaRW(cx, cz).getFeatures()
                    .add(new IrisFeaturePositional(
                            (cx << 4) + rng.nextInt(16),
                            (cz << 4) + rng.nextInt(16),
                            i.getZone()));
        }
    }

    default void generateParallaxLayer(int x, int z) {
        generateParallaxLayer(x, z, false);
    }

    default KList<Runnable> placeStructure(IrisPosition position, IrisJigsawStructure structure, RNG rng) {
        KList<Runnable> placeAfter = new KList<>();

        if (structure == null) {
            return null;
        }

        if (structure.getFeature() != null) {
            if (structure.getFeature().getBlockRadius() == 32) {
                structure.getFeature().setBlockRadius((double) structure.getMaxDimension() / 3);
            }

            getParallaxAccess().getMetaRW(position.getX() >> 4, position.getZ() >> 4).getFeatures()
                    .add(new IrisFeaturePositional(position.getX(), position.getZ(), structure.getFeature()));
        }

        placeAfter.addAll(new PlannedStructure(structure, position, rng).place(this, this));
        return placeAfter;
    }

    default KList<Runnable> generateParallaxJigsaw(RNG rng, int x, int z, IrisBiome biome, IrisRegion region) {
        KList<Runnable> placeAfter = new KList<>();

        if (getEngine().getDimension().isPlaceObjects()) {
            boolean placed = false;

            if (getEngine().getDimension().getStronghold() != null) {
                List<Position2> poss = getEngine().getDimension().getStrongholds(getEngine().getWorld().seed());

                if (poss != null) {
                    for (Position2 pos : poss) {
                        if (x == pos.getX() >> 4 && z == pos.getZ() >> 4) {
                            IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(getEngine().getDimension().getStronghold());
                            placeAfter.addAll(placeStructure(pos.toIris(), structure, rng));
                            placed = true;
                        }
                    }
                }
            }

            if (!placed) {
                for (IrisJigsawStructurePlacement i : biome.getJigsawStructures()) {
                    if (rng.nextInt(i.getRarity()) == 0) {
                        IrisPosition position = new IrisPosition((x << 4) + rng.nextInt(15), 0, (z << 4) + rng.nextInt(15));
                        IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(i.getStructure());
                        placeAfter.addAll(placeStructure(position, structure, rng));
                        placed = true;
                    }
                }
            }

            if (!placed) {
                for (IrisJigsawStructurePlacement i : region.getJigsawStructures()) {
                    if (rng.nextInt(i.getRarity()) == 0) {
                        IrisPosition position = new IrisPosition((x << 4) + rng.nextInt(15), 0, (z << 4) + rng.nextInt(15));
                        IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(i.getStructure());
                        placeAfter.addAll(placeStructure(position, structure, rng));
                        placed = true;
                    }
                }
            }

            if (!placed) {
                for (IrisJigsawStructurePlacement i : getEngine().getDimension().getJigsawStructures()) {
                    if (rng.nextInt(i.getRarity()) == 0) {
                        IrisPosition position = new IrisPosition((x << 4) + rng.nextInt(15), 0, (z << 4) + rng.nextInt(15));
                        IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(i.getStructure());
                        placeAfter.addAll(placeStructure(position, structure, rng));
                        placed = true;
                    }
                }
            }
        }

        return placeAfter;
    }

    default void generateParallaxSurface(RNG rng, int x, int z, IrisBiome biome, IrisRegion region, boolean useFeatures) {

        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (i.usesFeatures() != useFeatures) {
                continue;
            }

            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && rng.chance(getComplex().getObjectChanceStream().get(x << 4, z << 4))) {
                try {
                    place(rng, x << 4, z << 4, i);
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following biome: " + biome.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            if (i.usesFeatures() != useFeatures) {
                continue;
            }

            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && rng.chance(getComplex().getObjectChanceStream().get(x << 4, z << 4))) {
                try {
                    place(rng, x << 4, z << 4, i);
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following region: " + region.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }
    }

    default void generateParallaxMutations(RNG rng, int x, int z, boolean useFeatures) {
        if (getEngine().getDimension().getMutations().isEmpty()) {
            return;
        }

        searching:
        for (IrisBiomeMutation k : getEngine().getDimension().getMutations()) {
            for (int l = 0; l < k.getChecks(); l++) {
                IrisBiome sa = getComplex().getTrueBiomeStreamNoFeatures().get(((x * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()), ((z * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()));
                IrisBiome sb = getComplex().getTrueBiomeStreamNoFeatures().get(((x * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()), ((z * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()));

                if (sa.getLoadKey().equals(sb.getLoadKey())) {
                    continue;
                }

                if (k.getRealSideA(this).contains(sa.getLoadKey()) && k.getRealSideB(this).contains(sb.getLoadKey())) {
                    for (IrisObjectPlacement m : k.getObjects()) {
                        if (m.usesFeatures() != useFeatures) {
                            continue;
                        }

                        place(rng.nextParallelRNG((34 * ((x * 30) + (z * 30)) * x * z) + x - z + 1569962), x, z, m);
                    }

                    continue searching;
                }
            }
        }
    }

    default void place(RNG rng, int x, int z, IrisObjectPlacement objectPlacement) {
        place(rng, x, -1, z, objectPlacement);
    }

    default void placePiece(RNG rng, int xx, int forceY, int zz, IrisObject v, IrisObjectPlacement p) {
        int id = rng.i(0, Integer.MAX_VALUE);
        int maxf = 10000;
        AtomicBoolean pl = new AtomicBoolean(false);
        AtomicInteger max = new AtomicInteger(-1);
        AtomicInteger min = new AtomicInteger(maxf);
        int h = v.place(xx, forceY, zz, this, p, rng, (b) -> {
            int xf = b.getX();
            int yf = b.getY();
            int zf = b.getZ();
            getParallaxAccess().setObject(xf, yf, zf, v.getLoadKey() + "@" + id);
            ParallaxChunkMeta meta = getParallaxAccess().getMetaRW(xf >> 4, zf >> 4);
            meta.setObjects(true);
            meta.setMinObject(Math.min(Math.max(meta.getMinObject(), 0), yf));
            meta.setMaxObject(Math.max(Math.max(meta.getMaxObject(), 0), yf));

        }, null, getData());

        if (p.usesFeatures()) {
            if (p.isVacuum()) {
                ParallaxChunkMeta rw = getParallaxAccess().getMetaRW(xx >> 4, zz >> 4);
                double a = Math.max(v.getW(), v.getD());
                IrisFeature f = new IrisFeature();
                f.setConvergeToHeight(h - (v.getH() >> 1));
                f.setBlockRadius(a);
                f.setInterpolationRadius(p.getVacuumInterpolationRadius());
                f.setInterpolator(p.getVacuumInterpolationMethod());
                f.setStrength(1D);

                rw.getFeatures().add(new IrisFeaturePositional(xx, zz, f));
            }

            for (IrisFeaturePotential j : p.getAddFeatures()) {
                if (j.hasZone(rng, xx >> 4, zz >> 4)) {
                    ParallaxChunkMeta rw = getParallaxAccess().getMetaRW(xx >> 4, zz >> 4);
                    rw.getFeatures().add(new IrisFeaturePositional(xx + 1, zz - 1, j.getZone()));
                }
            }
        }


    }

    default void place(RNG rng, int x, int forceY, int z, IrisObjectPlacement objectPlacement) {
        placing:
        for (int i = 0; i < objectPlacement.getDensity(); i++) {
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                return;
            }
            int xx = rng.i(x, x + 16);
            int zz = rng.i(z, z + 16);
            int id = rng.i(0, Integer.MAX_VALUE);

            int h = v.place(xx, forceY, zz, this, objectPlacement, rng, (b) -> {
                int xf = b.getX();
                int yf = b.getY();
                int zf = b.getZ();
                getParallaxAccess().setObject(xf, yf, zf, v.getLoadKey() + "@" + id);
                ParallaxChunkMeta meta = getParallaxAccess().getMetaRW(xf >> 4, zf >> 4);
                meta.setObjects(true);
                meta.setMinObject(Math.min(Math.max(meta.getMinObject(), 0), yf));
                meta.setMaxObject(Math.max(Math.max(meta.getMaxObject(), 0), yf));

            }, null, getData());

            if (objectPlacement.usesFeatures()) {
                if (objectPlacement.isVacuum()) {
                    ParallaxChunkMeta rw = getParallaxAccess().getMetaRW(xx >> 4, zz >> 4);
                    double a = Math.max(v.getW(), v.getD());
                    IrisFeature f = new IrisFeature();
                    f.setConvergeToHeight(h - (v.getH() >> 1));
                    f.setBlockRadius(a);
                    f.setInterpolationRadius(objectPlacement.getVacuumInterpolationRadius());
                    f.setInterpolator(objectPlacement.getVacuumInterpolationMethod());
                    f.setStrength(1D);
                    rw.getFeatures().add(new IrisFeaturePositional(xx, zz, f));
                }

                for (IrisFeaturePotential j : objectPlacement.getAddFeatures()) {
                    if (j.hasZone(rng, xx >> 4, zz >> 4)) {
                        ParallaxChunkMeta rw = getParallaxAccess().getMetaRW(xx >> 4, zz >> 4);
                        rw.getFeatures().add(new IrisFeaturePositional(xx + 1, zz - 1, j.getZone()));
                    }
                }
            }
        }
    }

    default void updateParallaxChunkObjectData(int minY, int maxY, int x, int z, IrisObject v) {
        ParallaxChunkMeta meta = getParallaxAccess().getMetaRW(x >> 4, z >> 4);
        meta.setObjects(true);
        meta.setMaxObject(Math.max(maxY, meta.getMaxObject()));
        meta.setMinObject(Math.min(minY, Math.max(meta.getMinObject(), 0)));
    }

    default int computeParallaxSize() {
        Iris.verbose("Calculating the Parallax Size in Parallel");
        AtomicInteger xg = new AtomicInteger(0);
        AtomicInteger zg = new AtomicInteger();
        xg.set(0);
        zg.set(0);
        int jig = 0;
        KSet<String> objects = new KSet<>();
        KMap<IrisObjectScale, KList<String>> scalars = new KMap<>();
        int x = xg.get();
        int z = zg.get();

        if (getEngine().getDimension().isPlaceObjects()) {
            KList<IrisRegion> r = getAllRegions();
            KList<IrisBiome> b = getAllBiomes();

            for (IrisBiome i : b) {
                for (IrisObjectPlacement j : i.getObjects()) {
                    if (j.getScale().canScaleBeyond()) {
                        scalars.put(j.getScale(), j.getPlace());
                    } else {
                        objects.addAll(j.getPlace());
                    }
                }

                for (IrisJigsawStructurePlacement j : i.getJigsawStructures()) {
                    jig = Math.max(jig, getData().getJigsawStructureLoader().load(j.getStructure()).getMaxDimension());
                }
            }

            for (IrisRegion i : r) {
                for (IrisObjectPlacement j : i.getObjects()) {
                    if (j.getScale().canScaleBeyond()) {
                        scalars.put(j.getScale(), j.getPlace());
                    } else {
                        objects.addAll(j.getPlace());
                    }
                }

                for (IrisJigsawStructurePlacement j : i.getJigsawStructures()) {
                    jig = Math.max(jig, getData().getJigsawStructureLoader().load(j.getStructure()).getMaxDimension());
                }
            }

            for (IrisJigsawStructurePlacement j : getEngine().getDimension().getJigsawStructures()) {
                jig = Math.max(jig, getData().getJigsawStructureLoader().load(j.getStructure()).getMaxDimension());
            }

            if (getEngine().getDimension().getStronghold() != null) {
                try {
                    jig = Math.max(jig, getData().getJigsawStructureLoader().load(getEngine().getDimension().getStronghold()).getMaxDimension());
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.error("THIS IS THE ONE");
                    e.printStackTrace();
                }
            }

            Iris.verbose("Checking sizes for " + Form.f(objects.size()) + " referenced objects.");
            BurstExecutor e = getEngine().getTarget().getBurster().burst(objects.size());
            KMap<String, BlockVector> sizeCache = new KMap<>();
            for (String i : objects) {
                e.queue(() -> {
                    try {
                        BlockVector bv = sizeCache.compute(i, (k, v) -> {
                            if (v != null) {
                                return v;
                            }

                            try {
                                return IrisObject.sampleSize(getData().getObjectLoader().findFile(i));
                            } catch (IOException ex) {
                                Iris.reportError(ex);
                                ex.printStackTrace();
                            }

                            return null;
                        });

                        if (bv == null) {
                            throw new RuntimeException();
                        }

                        warn(i, bv);

                        synchronized (xg) {
                            xg.getAndSet(Math.max(bv.getBlockX(), xg.get()));
                        }

                        synchronized (zg) {
                            zg.getAndSet(Math.max(bv.getBlockZ(), zg.get()));
                        }
                    } catch (Throwable ed) {
                        Iris.reportError(ed);

                    }
                });
            }

            for (Map.Entry<IrisObjectScale, KList<String>> entry : scalars.entrySet()) {
                double ms = entry.getKey().getMaximumScale();
                for (String j : entry.getValue()) {
                    e.queue(() -> {
                        try {
                            BlockVector bv = sizeCache.compute(j, (k, v) -> {
                                if (v != null) {
                                    return v;
                                }

                                try {
                                    return IrisObject.sampleSize(getData().getObjectLoader().findFile(j));
                                } catch (IOException ioException) {
                                    Iris.reportError(ioException);
                                    ioException.printStackTrace();
                                }

                                return null;
                            });

                            if (bv == null) {
                                throw new RuntimeException();
                            }

                            warnScaled(j, bv, ms);

                            synchronized (xg) {
                                xg.getAndSet((int) Math.max(Math.ceil(bv.getBlockX() * ms), xg.get()));
                            }

                            synchronized (zg) {
                                zg.getAndSet((int) Math.max(Math.ceil(bv.getBlockZ() * ms), zg.get()));
                            }
                        } catch (Throwable ee) {
                            Iris.reportError(ee);

                        }
                    });
                }
            }

            e.complete();

            x = xg.get();
            z = zg.get();

            for (IrisDepositGenerator i : getEngine().getDimension().getDeposits()) {
                int max = i.getMaxDimension();
                x = Math.max(max, x);
                z = Math.max(max, z);
            }

            for (IrisRegion v : r) {
                for (IrisDepositGenerator i : v.getDeposits()) {
                    int max = i.getMaxDimension();
                    x = Math.max(max, x);
                    z = Math.max(max, z);
                }
            }

            for (IrisBiome v : b) {
                for (IrisDepositGenerator i : v.getDeposits()) {
                    int max = i.getMaxDimension();
                    x = Math.max(max, x);
                    z = Math.max(max, z);
                }
            }
        }

        x = Math.max(z, x);
        int u = x;
        int v = computeFeatureRange();
        x = Math.max(jig, x);
        x = Math.max(x, v);
        x = (Math.max(x, 16) + 16) >> 4;
        x = x % 2 == 0 ? x + 1 : x;
        Iris.info("Parallax Size: " + x + " Chunks");
        Iris.info("  Object Parallax Size: " + u + " (" + ((Math.max(u, 16) + 16) >> 4) + ")");
        Iris.info("  Jigsaw Parallax Size: " + jig + " (" + ((Math.max(jig, 16) + 16) >> 4) + ")");
        Iris.info("  Feature Parallax Size: " + v + " (" + ((Math.max(v, 16) + 16) >> 4) + ")");

        return x;
    }

    default int computeFeatureRange() {
        int m = 0;

        for (IrisFeaturePotential i : getAllFeatures()) {
            m = Math.max(m, i.getZone().getRealSize());
        }

        return m;
    }

    default void warn(String ob, BlockVector bv) {
        if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
            Iris.warn("Object " + ob + " has a large size (" + bv + ") and may increase memory usage!");
        }
    }

    default void warnScaled(String ob, BlockVector bv, double ms) {
        if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
            Iris.warn("Object " + ob + " has a large size (" + bv + ") and may increase memory usage! (Object scaled up to " + Form.pc(ms, 2) + ")");
        }
    }

    default int getHighest(int x, int z) {
        return getHighest(x, z, getData());
    }

    default int getHighest(int x, int z, boolean ignoreFluid) {
        return getHighest(x, z, getData(), ignoreFluid);
    }

    @Override
    default int getHighest(int x, int z, IrisData data) {
        return getHighest(x, z, data, false);
    }

    @Override
    default int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return ignoreFluid ? trueHeight(x, z) : Math.max(trueHeight(x, z), getEngine().getDimension().getFluidHeight());
    }

    default int trueHeight(int x, int z) {
        return getComplex().getTrueHeightStream().get(x, z);
    }

    @Override
    default void set(int x, int y, int z, BlockData d) {
        getParallaxAccess().setBlock(x, y, z, d);
    }

    @Override
    default void setTile(int x, int y, int z, TileData<? extends TileState> d) {
        getParallaxAccess().setTile(x, y, z, d);
    }

    @Override
    default BlockData get(int x, int y, int z) {
        BlockData block = getParallaxAccess().getBlock(x, y, z);

        if (block == null) {
            return AIR;
        }

        return block;
    }

    @Override
    default boolean isPreventingDecay() {
        return getEngine().getDimension().isPreventLeafDecay();
    }

    @Override
    default boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x, y, z));
    }

    @Override
    default boolean isUnderwater(int x, int z) {
        return getHighest(x, z, true) <= getFluidHeight();
    }

    @Override
    default int getFluidHeight() {
        return getEngine().getDimension().getFluidHeight();
    }

    @Override
    default boolean isDebugSmartBore() {
        return getEngine().getDimension().isDebugSmartBore();
    }

    default void close() {

    }

    @ChunkCoordinates
    default KList<IrisFeaturePositional> getFeaturesInChunk(Chunk c) {
        return getFeaturesInChunk(c.getX(), c.getZ());
    }
}
