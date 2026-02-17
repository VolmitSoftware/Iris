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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.IrisMantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.*;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.context.ChunkContext;
import art.arcane.iris.util.data.B;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import art.arcane.iris.util.noise.CNG;
import art.arcane.iris.util.noise.NoiseType;
import art.arcane.iris.util.parallel.BurstExecutor;
import art.arcane.iris.util.scheduling.J;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@ComponentFlag(ReservedFlag.OBJECT)
public class MantleObjectComponent extends IrisMantleComponent {

    public MantleObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.OBJECT, 1);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        boolean traceRegen = isRegenTraceThread();
        RNG rng = applyNoise(x, z, Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        if (traceRegen) {
            Iris.info("Regen object layer start: chunk=" + x + "," + z
                    + " biome=" + biome.getLoadKey()
                    + " region=" + region.getLoadKey()
                    + " biomePlacers=" + biome.getSurfaceObjects().size()
                    + " regionPlacers=" + region.getSurfaceObjects().size());
        }
        ObjectPlacementSummary summary = placeObjects(writer, rng, x, z, biome, region, traceRegen);
        if (traceRegen) {
            Iris.info("Regen object layer done: chunk=" + x + "," + z
                    + " biomePlacersChecked=" + summary.biomePlacersChecked()
                    + " biomePlacersTriggered=" + summary.biomePlacersTriggered()
                    + " regionPlacersChecked=" + summary.regionPlacersChecked()
                    + " regionPlacersTriggered=" + summary.regionPlacersTriggered()
                    + " objectAttempts=" + summary.objectAttempts()
                    + " objectPlaced=" + summary.objectPlaced()
                    + " objectRejected=" + summary.objectRejected()
                    + " objectNull=" + summary.objectNull()
                    + " objectErrors=" + summary.objectErrors());
        }
    }

    private RNG applyNoise(int x, int z, long seed) {
        CNG noise = CNG.signatureFast(new RNG(seed), NoiseType.WHITE, NoiseType.GLOB);
        return new RNG((long) (seed * noise.noise(x, z)));
    }

    @ChunkCoordinates
    private ObjectPlacementSummary placeObjects(MantleWriter writer, RNG rng, int x, int z, IrisBiome biome, IrisRegion region, boolean traceRegen) {
        int biomeChecked = 0;
        int biomeTriggered = 0;
        int regionChecked = 0;
        int regionTriggered = 0;
        int attempts = 0;
        int placed = 0;
        int rejected = 0;
        int nullObjects = 0;
        int errors = 0;

        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            biomeChecked++;
            boolean chance = rng.chance(i.getChance() + rng.d(-0.005, 0.005));
            if (traceRegen) {
                Iris.info("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=biome"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                biomeTriggered++;
                try {
                    ObjectPlacementResult result = placeObject(writer, rng, x << 4, z << 4, i, traceRegen, x, z, "biome");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following biome: " + biome.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            regionChecked++;
            boolean chance = rng.chance(i.getChance() + rng.d(-0.005, 0.005));
            if (traceRegen) {
                Iris.info("Regen object placer chance: chunk=" + x + "," + z
                        + " scope=region"
                        + " chanceResult=" + chance
                        + " chanceBase=" + i.getChance()
                        + " densityMid=" + i.getDensity()
                        + " objects=" + i.getPlace().size());
            }
            if (chance) {
                regionTriggered++;
                try {
                    ObjectPlacementResult result = placeObject(writer, rng, x << 4, z << 4, i, traceRegen, x, z, "region");
                    attempts += result.attempts();
                    placed += result.placed();
                    rejected += result.rejected();
                    nullObjects += result.nullObjects();
                    errors += result.errors();
                } catch (Throwable e) {
                    errors++;
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following region: " + region.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        return new ObjectPlacementSummary(
                biomeChecked,
                biomeTriggered,
                regionChecked,
                regionTriggered,
                attempts,
                placed,
                rejected,
                nullObjects,
                errors
        );
    }

    @BlockCoordinates
    private ObjectPlacementResult placeObject(
            MantleWriter writer,
            RNG rng,
            int x,
            int z,
            IrisObjectPlacement objectPlacement,
            boolean traceRegen,
            int chunkX,
            int chunkZ,
            String scope
    ) {
        int attempts = 0;
        int placed = 0;
        int rejected = 0;
        int nullObjects = 0;
        int errors = 0;
        int density = objectPlacement.getDensity(rng, x, z, getData());

        for (int i = 0; i < density; i++) {
            attempts++;
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                nullObjects++;
                if (traceRegen) {
                    Iris.warn("Regen object placement null object: chunk=" + chunkX + "," + chunkZ
                            + " scope=" + scope
                            + " densityIndex=" + i
                            + " density=" + density
                            + " placementKeys=" + objectPlacement.getPlace().toString(","));
                }
                continue;
            }
            int xx = rng.i(x, x + 15);
            int zz = rng.i(z, z + 15);
            int id = rng.i(0, Integer.MAX_VALUE);
            try {
                int result = v.place(xx, -1, zz, writer, objectPlacement, rng, (b, data) -> {
                    writer.setData(b.getX(), b.getY(), b.getZ(), v.getLoadKey() + "@" + id);
                    if (objectPlacement.isDolphinTarget() && objectPlacement.isUnderwater() && B.isStorageChest(data)) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), MatterStructurePOI.BURIED_TREASURE);
                    }
                }, null, getData());

                if (result >= 0) {
                    placed++;
                } else {
                    rejected++;
                }

                if (traceRegen) {
                    Iris.info("Regen object placement result: chunk=" + chunkX + "," + chunkZ
                            + " scope=" + scope
                            + " object=" + v.getLoadKey()
                            + " resultY=" + result
                            + " px=" + xx
                            + " pz=" + zz
                            + " densityIndex=" + i
                            + " density=" + density);
                }
            } catch (Throwable e) {
                errors++;
                Iris.reportError(e);
                Iris.error("Regen object placement exception: chunk=" + chunkX + "," + chunkZ
                        + " scope=" + scope
                        + " object=" + v.getLoadKey()
                        + " densityIndex=" + i
                        + " density=" + density
                        + " error=" + e.getClass().getSimpleName() + ":" + e.getMessage());
            }
        }

        return new ObjectPlacementResult(attempts, placed, rejected, nullObjects, errors);
    }

    private boolean isRegenTraceThread() {
        return Thread.currentThread().getName().startsWith("Iris-Regen-")
                && IrisSettings.get().getGeneral().isDebug();
    }

    private record ObjectPlacementSummary(
            int biomePlacersChecked,
            int biomePlacersTriggered,
            int regionPlacersChecked,
            int regionPlacersTriggered,
            int objectAttempts,
            int objectPlaced,
            int objectRejected,
            int objectNull,
            int objectErrors
    ) {
    }

    private record ObjectPlacementResult(int attempts, int placed, int rejected, int nullObjects, int errors) {
    }

    @BlockCoordinates
    private Set<String> guessPlacedKeys(RNG rng, int x, int z, IrisObjectPlacement objectPlacement) {
        Set<String> f = new KSet<>();
        for (int i = 0; i < objectPlacement.getDensity(rng, x, z, getData()); i++) {
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                continue;
            }

            f.add(v.getLoadKey());
        }

        return f;
    }

    public Set<String> guess(int x, int z) {
        // todo The guess doesnt bring into account that the placer may return -1
        RNG rng = applyNoise(x, z, Cache.key(x, z) + seed());
        IrisBiome biome = getEngineMantle().getEngine().getSurfaceBiome((x << 4) + 8, (z << 4) + 8);
        IrisRegion region = getEngineMantle().getEngine().getRegion((x << 4) + 8, (z << 4) + 8);
        Set<String> v = new KSet<>();
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        return v;
    }

    protected int computeRadius() {
        var dimension = getDimension();

        AtomicInteger xg = new AtomicInteger();
        AtomicInteger zg = new AtomicInteger();

        KSet<String> objects = new KSet<>();
        KMap<IrisObjectScale, KList<String>> scalars = new KMap<>();
        for (var region : dimension.getAllRegions(this::getData)) {
            for (var j : region.getObjects()) {
                if (j.getScale().canScaleBeyond()) {
                    scalars.put(j.getScale(), j.getPlace());
                } else {
                    objects.addAll(j.getPlace());
                }
            }
        }
        for (var biome : dimension.getAllBiomes(this::getData)) {
            for (var j : biome.getObjects()) {
                if (j.getScale().canScaleBeyond()) {
                    scalars.put(j.getScale(), j.getPlace());
                } else {
                    objects.addAll(j.getPlace());
                }
            }
        }

        BurstExecutor e = getEngineMantle().getTarget().getBurster().burst(objects.size());
        boolean maintenanceFolia = false;
        if (J.isFolia()) {
            var world = getEngineMantle().getEngine().getWorld().realWorld();
            maintenanceFolia = world != null && IrisToolbelt.isWorldMaintenanceActive(world);
        }
        if (maintenanceFolia) {
            Iris.info("MantleObjectComponent radius scan using single-threaded mode during maintenance regen.");
            e.setMulticore(false);
        }
        KMap<String, BlockVector> sizeCache = new KMap<>();
        for (String i : objects) {
            e.queue(() -> {
                try {
                    BlockVector bv = sizeCache.computeIfAbsent(i, (k) -> {
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

                    if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
                        Iris.warn("Object " + i + " has a large size (" + bv + ") and may increase memory usage!");
                    }

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
                        BlockVector bv = sizeCache.computeIfAbsent(j, (k) -> {
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

                        if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
                            Iris.warn("Object " + j + " has a large size (" + bv + ") and may increase memory usage! (Object scaled up to " + Form.pc(ms, 2) + ")");
                        }

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
        return Math.max(xg.get(), zg.get());
    }
}
