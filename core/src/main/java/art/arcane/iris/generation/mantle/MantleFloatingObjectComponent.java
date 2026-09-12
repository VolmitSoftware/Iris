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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.generation.biome.FloatingIslandSample;
import art.arcane.iris.structure.object.FloatingObjectFootprint;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@ComponentFlag(ReservedFlag.FLOATING_OBJECT)
public class MantleFloatingObjectComponent extends IrisMantleComponent {
    private static final int INVERTED_PICK_ATTEMPTS = 8;
    private static final IrisObjectRotation ROTATION_NONE = IrisObjectRotation.of(0, 0, 0);

    public MantleFloatingObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.FLOATING_OBJECT, 3);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        if (!complex.allowsNewGenerationChunk(x, z)) {
            return;
        }
        IrisData data = getData();
        int minX = x << 4;
        int minZ = z << 4;
        RNG chunkRng = new RNG(Cache.key(x, z) + seed() + 0x0FA710BEL);
        FloatingIslandSampleResolver sampleResolver = new FloatingIslandSampleResolver(getEngineMantle(), context.getFloatingIslandBoundarySampler());

        FloatingIslandSample.clearChunkMemo();

        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        for (int xf = 0; xf < 16; xf++) {
            for (int zf = 0; zf < 16; zf++) {
                int wx = minX + xf;
                int wz = minZ + zf;
                FloatingIslandSample sample = sampleResolver.sample(wx, wz);
                if (sample != null) {
                    samples[(zf << 4) | xf] = sample;
                }
            }
        }

        // The IdentityHashMaps are lookup indices only. Iteration MUST follow the encounter
        // order of the deterministic 0..255 column scan: identity-hash order changes per JVM
        // run, and the shared chunkRng makes placement output depend on iteration order.
        IdentityHashMap<IrisFloatingChildBiomes, KList<Integer>> entryColumns = new IdentityHashMap<>();
        IdentityHashMap<IrisFloatingChildBiomes, KList<Integer>> bottomEntryColumns = new IdentityHashMap<>();
        KList<IrisFloatingChildBiomes> entryOrder = new KList<>();
        KList<IrisFloatingChildBiomes> bottomEntryOrder = new KList<>();
        for (int i = 0; i < 256; i++) {
            FloatingIslandSample s = samples[i];
            if (s == null || s.entry == null) {
                continue;
            }
            entryColumns.computeIfAbsent(s.entry, e -> {
                entryOrder.add(e);
                return new KList<>();
            }).add(i);
            IrisFloatingChildBiomes bottomEntry = s.bottomEntry();
            if (bottomEntry != null) {
                bottomEntryColumns.computeIfAbsent(bottomEntry, e -> {
                    bottomEntryOrder.add(e);
                    return new KList<>();
                }).add(i);
            }
        }

        for (IrisFloatingChildBiomes entry : entryOrder) {
            KList<Integer> columns = entryColumns.get(entry);
            if (columns.isEmpty()) {
                continue;
            }

            int firstKey = columns.get(0);
            IrisBiome parent = sampleResolver.parent(minX + (firstKey & 15), minZ + (firstKey >> 4));
            IrisBiome target = entry.getRealBiome(parent, data);

            KList<IrisObjectPlacement> floating = entry.getFloatingObjects();
            if (floating != null && !floating.isEmpty()) {
                for (IrisObjectPlacement placement : floating) {
                    tryPlaceFloatingChunk(writer, complex, chunkRng, data, placement, columns, minX, minZ, entry);
                }
            }

            KList<IrisObjectPlacement> surface = target != null ? entry.resolveTopObjects(target) : null;
            KList<IrisObjectPlacement> extras = entry.getExtraObjects();
            boolean hasSurface = surface != null && !surface.isEmpty();
            boolean hasExtras = extras != null && !extras.isEmpty();
            KList<Integer> interior = null;
            if (hasSurface || hasExtras) {
                interior = interiorColumns(sampleResolver, columns, minX, minZ, entry, IslandObjectPlacer.AnchorFace.TOP);
                if (hasSurface) {
                    for (IrisObjectPlacement placement : surface) {
                        tryPlaceAnchoredChunk(writer, complex, chunkRng, data, placement, samples, sampleResolver, columns, interior, minX, minZ, entry);
                    }
                }
                if (hasExtras) {
                    for (IrisObjectPlacement placement : extras) {
                        tryPlaceAnchoredChunk(writer, complex, chunkRng, data, placement, samples, sampleResolver, columns, interior, minX, minZ, entry);
                    }
                }
            }
        }

        for (IrisFloatingChildBiomes entry : bottomEntryOrder) {
            KList<Integer> columns = bottomEntryColumns.get(entry);
            if (columns.isEmpty()) {
                continue;
            }

            int firstKey = columns.get(0);
            IrisBiome parent = sampleResolver.parent(minX + (firstKey & 15), minZ + (firstKey >> 4));
            IrisBiome target = entry.getRealBiome(parent, data);
            KList<IrisObjectPlacement> bottom = target != null ? entry.resolveBottomObjects(target) : null;
            if (bottom != null && !bottom.isEmpty()) {
                KList<Integer> interior = interiorColumns(sampleResolver, columns, minX, minZ, entry, IslandObjectPlacer.AnchorFace.BOTTOM);
                for (IrisObjectPlacement placement : bottom) {
                    tryPlaceInvertedChunk(writer, complex, chunkRng, data, placement, samples, sampleResolver, columns, interior, minX, minZ, entry);
                }
            }
        }
    }

    @ChunkCoordinates
    private void tryPlaceFloatingChunk(MantleWriter writer, IrisComplex complex, RNG rng, IrisData data, IrisObjectPlacement placement, KList<Integer> columns, int minX, int minZ, IrisFloatingChildBiomes entry) {
        if (placement == null || columns == null || columns.isEmpty()) {
            return;
        }

        if (placement.isCompatExcluded(data)) {
            return;
        }
        int density = placement.getDensity(rng, minX, minZ, data);
        double perAttempt = placement.getChance();
        for (int i = 0; i < density; i++) {
            if (!rng.chance(perAttempt + rng.d(-0.005, 0.005))) {
                continue;
            }
            IrisObject raw = placement.getObject(complex, rng);
            if (raw == null) {
                continue;
            }
            IrisObject obj0 = placement.scaleObject(rng, raw, getDimension());
            if (obj0 == null) {
                continue;
            }
            if (entry != null && entry.hasObjectShrink()) {
                obj0 = entry.getShrinkScale().get(rng, obj0);
                if (obj0 == null) {
                    continue;
                }
            }
            final IrisObject obj = obj0;

            int key = columns.get(rng.i(columns.size()));
            int xx = minX + (key & 15);
            int zz = minZ + (key >> 4);
            IrisObjectPlacement floatingPlacement = placement.toPlacement(obj.getLoadKey());
            if (!MantleObjectComponent.allowsObjectPlacement(
                    complex,
                    obj,
                    floatingPlacement,
                    xx,
                    zz)) {
                continue;
            }
            int id = rng.i(0, Integer.MAX_VALUE);

            try {
                obj.place(xx, -1, zz, writer, floatingPlacement, rng, (b, bd) -> {
                    String marker = placementMarker(obj, id);
                    if (marker != null && shouldWritePlacementMarker(writer, bd, b.getX(), b.getY(), b.getZ())) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), marker);
                    }
                }, null, data);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }
    }

    @ChunkCoordinates
    private void tryPlaceAnchoredChunk(MantleWriter writer, IrisComplex complex, RNG rng, IrisData data, IrisObjectPlacement placement, FloatingIslandSample[] samples, IslandObjectPlacer.SampleProvider sampleProvider, KList<Integer> columns, KList<Integer> interior, int minX, int minZ, IrisFloatingChildBiomes entry) {
        if (placement == null || columns.isEmpty()) {
            return;
        }

        if (placement.isCompatExcluded(data)) {
            return;
        }
        int density = placement.getDensity(rng, minX, minZ, data);
        double perAttempt = placement.getChance();

        for (int i = 0; i < density; i++) {
            if (!rng.chance(perAttempt + rng.d(-0.005, 0.005))) {
                continue;
            }

            IrisObject raw = placement.getObject(complex, rng);
            if (raw == null) {
                continue;
            }
            IrisObject obj0 = placement.scaleObject(rng, raw, getDimension());
            if (obj0 == null) {
                continue;
            }
            if (entry != null && entry.hasObjectShrink()) {
                obj0 = entry.getShrinkScale().get(rng, obj0);
                if (obj0 == null) {
                    continue;
                }
            }
            final IrisObject obj = obj0;

            FloatingObjectFootprint fp = FloatingObjectFootprint.compute(obj);

            KList<Integer> pool = interior.isEmpty() ? columns : interior;

            int pickedKey = pool.get(rng.i(pool.size()));
            int pickedXf = pickedKey & 15;
            int pickedZf = pickedKey >> 4;
            FloatingIslandSample pickedSample = samples[(pickedZf << 4) | pickedXf];
            if (pickedSample == null) {
                continue;
            }
            int pickTopY = pickedSample.topY();

            int pickedX = minX + pickedXf;
            int pickedZ = minZ + pickedZf;
            if (!isFootprintFlat(fp, pickedX, pickedZ, pickTopY, sampleProvider, entry, 2)) {
                if (!isFootprintFlat(fp, pickedX, pickedZ, pickTopY, sampleProvider, entry, 4)) {
                    continue;
                }
            }

            int wx = minX + pickedXf - fp.getTallestKx();
            int wz = minZ + pickedZf - fp.getTallestKz();

            IrisObjectPlacement anchored = placement.toPlacement(obj.getLoadKey());
            anchored.setMode(translateStiltModeForFloating(anchored.getMode()));
            anchored.setTranslate(new IrisObjectTranslate());
            anchored.setRotation(ROTATION_NONE);
            anchored.setForcePlace(true);
            anchored.setBottom(false);
            if (!MantleObjectComponent.allowsObjectPlacement(complex, obj, anchored, wx, wz)) {
                continue;
            }

            int yv = pickTopY + 1 - fp.getLowestSolidKeyY();

            IslandObjectPlacer islandPlacer = IslandObjectPlacer.top(writer, sampleProvider, entry, pickTopY);
            FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(islandPlacer);
            int id = rng.i(0, Integer.MAX_VALUE);

            try {
                int resultY = obj.place(wx, yv, wz, transaction, anchored, rng, (b, bd) -> {
                    String marker = placementMarker(obj, id);
                    if (marker != null && shouldWritePlacementMarker(transaction, bd, b.getX(), b.getY(), b.getZ())) {
                        transaction.setData(b.getX(), b.getY(), b.getZ(), marker);
                    }
                }, null, data);
                if (resultY < 0) {
                    transaction.discard();
                } else {
                    transaction.commit();
                }
            } catch (Throwable e) {
                transaction.discard();
                IrisLogging.reportError(e);
            }
        }
    }

    @ChunkCoordinates
    private void tryPlaceInvertedChunk(MantleWriter writer, IrisComplex complex, RNG rng, IrisData data, IrisObjectPlacement placement, FloatingIslandSample[] samples, IslandObjectPlacer.SampleProvider sampleProvider, KList<Integer> columns, KList<Integer> interior, int minX, int minZ, IrisFloatingChildBiomes entry) {
        if (placement == null || columns.isEmpty()) {
            return;
        }

        if (placement.isCompatExcluded(data)) {
            return;
        }
        int density = placement.getDensity(rng, minX, minZ, data);
        double perAttempt = placement.getChance();

        for (int i = 0; i < density; i++) {
            if (!rng.chance(perAttempt + rng.d(-0.005, 0.005))) {
                continue;
            }

            IrisObject raw = placement.getObject(complex, rng);
            if (raw == null) {
                continue;
            }
            IrisObject obj0 = placement.scaleObject(rng, raw, getDimension());
            if (obj0 == null) {
                continue;
            }
            if (entry != null && entry.hasObjectShrink()) {
                obj0 = entry.getShrinkScale().get(rng, obj0);
                if (obj0 == null) {
                    continue;
                }
            }
            final IrisObject obj = obj0;

            FloatingObjectFootprint fp = FloatingObjectFootprint.compute(obj);
            int invertedYRotation = rng.i(4) * 90;
            IrisObjectRotation invertedRotation = IrisObjectRotation.xFlip180WithY(invertedYRotation);

            KList<Integer> pool = interior.isEmpty() ? columns : interior;

            int pickedXf = -1;
            int pickedZf = -1;
            int pickBottomY = -1;
            boolean foundBottomAnchor = false;
            for (int attempt = 0; attempt < INVERTED_PICK_ATTEMPTS; attempt++) {
                int pickedKey = pool.get(rng.i(pool.size()));
                int candidateXf = pickedKey & 15;
                int candidateZf = pickedKey >> 4;
                FloatingIslandSample candidateSample = samples[(candidateZf << 4) | candidateXf];
                if (candidateSample == null) {
                    continue;
                }
                int candidateBottomY = candidateSample.bottomY();
                if (candidateBottomY < 0) {
                    continue;
                }
                int candidateX = minX + candidateXf;
                int candidateZ = minZ + candidateZf;
                if (!isFootprintFlatBottom(fp, invertedRotation, candidateX, candidateZ, candidateBottomY, sampleProvider, entry, 2)
                        && !isFootprintFlatBottom(fp, invertedRotation, candidateX, candidateZ, candidateBottomY, sampleProvider, entry, 4)) {
                    continue;
                }
                pickedXf = candidateXf;
                pickedZf = candidateZf;
                pickBottomY = candidateBottomY;
                foundBottomAnchor = true;
                break;
            }
            if (!foundBottomAnchor) {
                continue;
            }

            int wx = invertedBaseX(minX, pickedXf, fp, invertedRotation);
            int wz = invertedBaseZ(minZ, pickedZf, fp, invertedRotation);

            IrisObjectPlacement inverted = placement.toPlacement(obj.getLoadKey());
            inverted.setMode(translateStiltModeForFloating(inverted.getMode()));
            inverted.setTranslate(new IrisObjectTranslate());
            inverted.setRotation(invertedRotation);
            inverted.setForcePlace(true);
            inverted.setBottom(false);
            if (!MantleObjectComponent.allowsObjectPlacement(complex, obj, inverted, wx, wz)) {
                continue;
            }

            int yv = invertedBaseY(pickBottomY, fp, invertedRotation);

            IslandObjectPlacer islandPlacer = IslandObjectPlacer.bottom(writer, sampleProvider, entry, pickBottomY);
            FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(islandPlacer);
            int id = rng.i(0, Integer.MAX_VALUE);

            try {
                int resultY = obj.place(wx, yv, wz, transaction, inverted, rng, (b, bd) -> {
                    String marker = placementMarker(obj, id);
                    if (marker != null && shouldWritePlacementMarker(transaction, bd, b.getX(), b.getY(), b.getZ())) {
                        transaction.setData(b.getX(), b.getY(), b.getZ(), marker);
                    }
                }, null, data);
                if (resultY < 0) {
                    transaction.discard();
                } else {
                    transaction.commit();
                }
            } catch (Throwable e) {
                transaction.discard();
                IrisLogging.reportError(e);
            }
        }
    }

    static boolean isFootprintFlatBottom(FloatingObjectFootprint fp, IrisObjectRotation rotation,
                                         int pickedX, int pickedZ, int pickBottomY,
                                         IslandObjectPlacer.SampleProvider samples,
                                         IrisFloatingChildBiomes entry, int tolerance) {
        IrisBlockVector anchor = invertedFootprintAnchor(fp, rotation);
        long[] cells = fp.footprintXZ();
        if (cells.length == 0) {
            return false;
        }
        for (int i = 0, n = cells.length; i < n; i++) {
            long encoded = cells[i];
            int kx = (int) (encoded >> 32);
            int kz = (int) (encoded & 0xFFFFFFFFL);
            IrisBlockVector cell = rotation.rotate(new IrisBlockVector(kx, 0, kz), 0, 0, 0);
            int columnX = pickedX + cell.getBlockX() - anchor.getBlockX();
            int columnZ = pickedZ + cell.getBlockZ() - anchor.getBlockZ();
            FloatingIslandSample sample = samples.sample(columnX, columnZ);
            if (!IslandObjectPlacer.matchesAnchor(sample, entry, IslandObjectPlacer.AnchorFace.BOTTOM)) {
                return false;
            }
            int bottomY = sample.bottomY();
            if (bottomY < 0 || Math.abs(bottomY - pickBottomY) > tolerance) {
                return false;
            }
        }
        return true;
    }

    static int invertedBaseX(int minX, int pickedXf, FloatingObjectFootprint fp) {
        return invertedBaseX(minX, pickedXf, fp, IrisObjectRotation.xFlip180());
    }

    static int invertedBaseY(int pickBottomY, FloatingObjectFootprint fp) {
        return invertedBaseY(pickBottomY, fp, IrisObjectRotation.xFlip180());
    }

    static int invertedBaseZ(int minZ, int pickedZf, FloatingObjectFootprint fp) {
        return invertedBaseZ(minZ, pickedZf, fp, IrisObjectRotation.xFlip180());
    }

    static int invertedBaseX(int minX, int pickedXf, FloatingObjectFootprint fp, IrisObjectRotation rotation) {
        return minX + pickedXf - invertedFootprintAnchor(fp, rotation).getBlockX();
    }

    static int invertedBaseY(int pickBottomY, FloatingObjectFootprint fp, IrisObjectRotation rotation) {
        return pickBottomY - 1 - invertedSolidAnchor(fp, rotation).getBlockY();
    }

    static int invertedBaseZ(int minZ, int pickedZf, FloatingObjectFootprint fp, IrisObjectRotation rotation) {
        return minZ + pickedZf - invertedFootprintAnchor(fp, rotation).getBlockZ();
    }

    private static IrisBlockVector invertedFootprintAnchor(FloatingObjectFootprint fp, IrisObjectRotation rotation) {
        return rotation.rotate(new IrisBlockVector(fp.getTallestKx(), 0, fp.getTallestKz()), 0, 0, 0);
    }

    private static IrisBlockVector invertedSolidAnchor(FloatingObjectFootprint fp, IrisObjectRotation rotation) {
        return rotation.rotate(new IrisBlockVector(fp.getTallestKx(), fp.getLowestSolidKeyY(), fp.getTallestKz()), 0, 0, 0);
    }

    private static boolean shouldWritePlacementMarker(IObjectPlacer placer, PlatformBlockState state, int x, int y, int z) {
        if (state == null) {
            return false;
        }
        PlatformBlockState existingState = placer.get(x, y, z);
        boolean wouldReplace = B.isSolid(existingState) && B.isVineBlock(state);
        String material = IrisProceduralBlocks.materialKey(state);
        boolean placesBlock = !material.equals("minecraft:air") && !material.equals("minecraft:cave_air") && !wouldReplace;
        return state.isCustom() || placesBlock;
    }

    static boolean isFootprintFlat(FloatingObjectFootprint fp, int pickedX, int pickedZ, int pickTopY,
                                   IslandObjectPlacer.SampleProvider samples,
                                   IrisFloatingChildBiomes entry, int tolerance) {
        int tallestKx = fp.getTallestKx();
        int tallestKz = fp.getTallestKz();
        long[] cells = fp.footprintXZ();
        if (cells.length == 0) {
            return false;
        }
        for (int i = 0, n = cells.length; i < n; i++) {
            long encoded = cells[i];
            int kx = (int) (encoded >> 32);
            int kz = (int) (encoded & 0xFFFFFFFFL);
            int columnX = pickedX + (kx - tallestKx);
            int columnZ = pickedZ + (kz - tallestKz);
            FloatingIslandSample sample = samples.sample(columnX, columnZ);
            if (!IslandObjectPlacer.matchesAnchor(sample, entry, IslandObjectPlacer.AnchorFace.TOP)
                    || Math.abs(sample.topY() - pickTopY) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static KList<Integer> interiorColumns(IslandObjectPlacer.SampleProvider samples,
                                                  KList<Integer> columns, int minX, int minZ,
                                                  IrisFloatingChildBiomes entry,
                                                  IslandObjectPlacer.AnchorFace face) {
        KList<Integer> interior = new KList<>();
        for (int key : columns) {
            int x = minX + (key & 15);
            int z = minZ + (key >> 4);
            if (!IslandObjectPlacer.matchesAnchor(samples.sample(x + 1, z), entry, face)) {
                continue;
            }
            if (!IslandObjectPlacer.matchesAnchor(samples.sample(x - 1, z), entry, face)) {
                continue;
            }
            if (!IslandObjectPlacer.matchesAnchor(samples.sample(x, z + 1), entry, face)) {
                continue;
            }
            if (!IslandObjectPlacer.matchesAnchor(samples.sample(x, z - 1), entry, face)) {
                continue;
            }
            interior.add(key);
        }
        return interior;
    }

    private static String placementMarker(IrisObject object, int id) {
        if (object == null) {
            return null;
        }
        String key = object.getLoadKey();
        if (key == null || key.isEmpty() || key.equals("null")) {
            return null;
        }
        return key + "@" + id;
    }

    private static ObjectPlaceMode translateStiltModeForFloating(ObjectPlaceMode m) {
        return switch (m) {
            case STILT -> ObjectPlaceMode.MAX_HEIGHT;
            case FAST_STILT -> ObjectPlaceMode.FAST_MAX_HEIGHT;
            case MIN_STILT -> ObjectPlaceMode.MIN_HEIGHT;
            case FAST_MIN_STILT -> ObjectPlaceMode.FAST_MIN_HEIGHT;
            case CENTER_STILT -> ObjectPlaceMode.CENTER_HEIGHT;
            case ERODE_STILT -> ObjectPlaceMode.MAX_HEIGHT;
            case STRUCTURE_PIECE -> ObjectPlaceMode.CENTER_HEIGHT;
            default -> m;
        };
    }

    @Override
    protected int computeRadius() {
        int maxObjectExtent = 16;
        Map<String, IrisBlockVector> sizeCache = new HashMap<>();
        Set<String> warnedLargeObjects = new HashSet<>();
        try {
            IrisData data = getData();
            for (IrisBiome biome : getDimension().getReachableBiomes(this::getData)) {
                KList<IrisFloatingChildBiomes> entries = biome.getFloatingChildBiomes();
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                for (IrisFloatingChildBiomes entry : entries) {
                    maxObjectExtent = Math.max(maxObjectExtent, computePlacementRadius(entry.getFloatingObjects(), data, sizeCache, warnedLargeObjects));
                    maxObjectExtent = Math.max(maxObjectExtent, computePlacementRadius(entry.getExtraObjects(), data, sizeCache, warnedLargeObjects));
                    maxObjectExtent = Math.max(maxObjectExtent, computePlacementRadius(entry.getTopObjectOverrides(), data, sizeCache, warnedLargeObjects));
                    maxObjectExtent = Math.max(maxObjectExtent, computePlacementRadius(entry.getBottomObjectOverrides(), data, sizeCache, warnedLargeObjects));
                    try {
                        IrisBiome target = entry.getRealBiome(biome, data);
                        if (target != null) {
                            maxObjectExtent = Math.max(maxObjectExtent, computePlacementRadius(entry.resolveTopObjects(target), data, sizeCache, warnedLargeObjects));
                            maxObjectExtent = Math.max(maxObjectExtent, computePlacementRadius(entry.resolveBottomObjects(target), data, sizeCache, warnedLargeObjects));
                        }
                    } catch (Throwable e) {
                        IrisLogging.reportError(e);
                    }
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
        return maxObjectExtent;
    }

    private int computePlacementRadius(KList<IrisObjectPlacement> placements, IrisData data,
                                       Map<String, IrisBlockVector> sizeCache,
                                       Set<String> warnedLargeObjects) {
        int radius = 0;
        if (placements == null) {
            return radius;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null || placement.getPlace() == null) {
                continue;
            }
            for (String objectKey : placement.getPlace()) {
                try {
                    IrisBlockVector size = sizeCache.get(objectKey);
                    if (size == null) {
                        File file = data.getObjectLoader().findFile(objectKey);
                        if (file == null) {
                            continue;
                        }
                        size = IrisObject.sampleSize(file);
                        sizeCache.put(objectKey, size);
                    }
                    int reach = MantleObjectComponent.calculatePlacementReach(size, placement, placement.getMaximumScale(getDimension()));
                    if (reach > 128 && warnedLargeObjects.add(objectKey)) {
                        IrisLogging.warn("Floating object " + objectKey + " has a large placement reach (" + reach + " blocks) and may increase memory usage!");
                    }
                    radius = Math.max(radius, reach);
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }
        }
        return radius;
    }
}
