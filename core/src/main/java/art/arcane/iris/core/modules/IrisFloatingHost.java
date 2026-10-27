/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.builtin.floating.IslandAnchor;
import art.arcane.iris.builtin.floating.IslandFootprint;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.mantle.components.FloatingObjectPlacementTransaction;
import art.arcane.iris.engine.mantle.components.IslandAnchorPlacer;
import art.arcane.iris.engine.object.placement.FloatingObjectFootprint;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.ScaledObjects;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import com.volmit.ModuleLog;
import com.volmit.generation.InvocationContext;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Optional;

/**
 * Iris's answers to {@code iris:floating}: a biome's terrain height by its generators, sky biomes into the engine's
 * mantle, and floating objects placed by Iris's own object placement into the mantle chunk being generated.
 */
final class IrisFloatingHost implements FloatingHost {
    private static final ObjectRotation NO_ROTATION = ObjectRotation.of(0, 0, 0);
    private static final int FLOATING_OBJECT_Y = -1;

    private final Engine engine;
    private final ModuleLog log;

    IrisFloatingHost(Engine engine, ModuleLog log) {
        this.engine = engine;
        this.log = log;
    }

    @Override
    public double biomeHeight(ResourceId biome, int worldX, int worldZ, long seed) {
        return irisBiome(biome).getHeight(engine, worldX, worldZ, seed);
    }

    @Override
    public void paintSkyBiome(ResourceId biome, int worldX, int worldZ, int[] ys, RNG random) {
        MatterBiomeInject matter = skyBiomeMatter(irisBiome(biome), worldX, worldZ, random);
        for (int y : ys) {
            engine.getMantle().getMantle().set(worldX, y, worldZ, matter);
        }
    }

    private MatterBiomeInject skyBiomeMatter(IrisBiome target, int worldX, int worldZ, RNG random) {
        if (target.isCustom()) {
            IrisBiomeCustom custom = target.getCustomBiome(random, engine, worldX, 0, worldZ);
            return BiomeInjectMatter.get(IrisPlatforms.get().biomeWriter().biomeIdFor(engine.getDimension().getLoadKey() + ":" + custom.getId()));
        }
        return BiomeInjectMatter.get(IrisPlatforms.get().biomeWriter().biomeIdFor(target.getSkyBiomeKey(random, engine, worldX, 0, worldZ)));
    }

    private IrisBiome irisBiome(ResourceId biome) {
        return ((IrisPackResources) engine.getData().getPackResources()).irisBiome(biome)
                .orElseThrow(() -> new IllegalArgumentException("The pack has no biome " + biome));
    }

    @Override
    public IslandFootprint footprint(PlaceableObject object) {
        FloatingObjectFootprint footprint = FloatingObjectFootprint.compute((IrisObject) object);
        return new IslandFootprint(footprint.getTallestKx(), footprint.getTallestKz(), footprint.getLowestSolidKeyY(), footprint.footprintXZ());
    }

    @Override
    public Optional<PlaceableObject> shrink(PlaceableObject object, double factor, RNG random) {
        return Optional.ofNullable(ScaledObjects.resized((IrisObject) object, factor, random, engine));
    }

    @Override
    public void placeFloating(InvocationContext context, PlaceableObject object, int worldX, int worldZ, ObjectPlacement placement,
                              RNG random) {
        IrisObject placed = (IrisObject) object;
        IrisData data = engine.getData();
        ObjectPlacement floating = placement.toBuilder().place(List.of(placed.getLoadKey())).build();
        MantleWriter writer = writerOf(context);
        int id = random.i(0, Integer.MAX_VALUE);
        try {
            placed.place(worldX, FLOATING_OBJECT_Y, worldZ, writer, floating, random, (block, state) -> writeMarker(writer, placed, id, block.getX(),
                    block.getY(), block.getZ(), state), null, data);
        } catch (Throwable e) {
            log.error("Failed to place a floating object", e);
        }
    }

    @Override
    public void placeOnTop(InvocationContext context, PlaceableObject object, int worldX, int y, int worldZ, ObjectPlacement placement,
                           IslandAnchor anchor, RNG random) {
        placeAgainstIsland(writerOf(context), (IrisObject) object, worldX, y, worldZ, placement, NO_ROTATION, anchor, random, "anchored");
    }

    @Override
    public void placeUnder(InvocationContext context, PlaceableObject object, int worldX, int y, int worldZ, int yRotation,
                           ObjectPlacement placement, IslandAnchor anchor, RNG random) {
        placeAgainstIsland(writerOf(context), (IrisObject) object, worldX, y, worldZ, placement, ObjectRotation.xFlip180WithY(yRotation),
                anchor, random, "inverted");
    }

    private static MantleWriter writerOf(InvocationContext context) {
        return context.extension(IrisMantleChunk.class).orElseThrow().writer();
    }

    /** Places the object through a transaction that writes nothing unless every block lands where the anchor allows. */
    private void placeAgainstIsland(MantleWriter writer, IrisObject object, int worldX, int y, int worldZ, ObjectPlacement placement,
                                    ObjectRotation rotation, IslandAnchor anchor, RNG random, String kind) {
        IrisData data = engine.getData();
        ObjectPlacement anchored = placement.toBuilder()
                .place(List.of(object.getLoadKey()))
                .mode(placement.mode().onFloatingIsland())
                .translate(new ObjectTranslate())
                .rotation(rotation)
                .forcePlace(true)
                .bottom(false)
                .build();

        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(
                new IslandAnchorPlacer(writer, anchor));
        int id = random.i(0, Integer.MAX_VALUE);
        try {
            int resultY = object.place(worldX, y, worldZ, transaction, anchored, random, (block, state) -> writeMarker(transaction, object, id,
                    block.getX(), block.getY(), block.getZ(), state), null, data);
            if (resultY < 0) {
                transaction.discard();
            } else {
                transaction.commit();
            }
        } catch (Throwable e) {
            transaction.discard();
            log.error("Failed to place an " + kind + " floating object", e);
        }
    }

    private static void writeMarker(IObjectPlacer placer, IrisObject object, int id, int x, int y, int z, PlatformBlockState state) {
        String marker = placementMarker(object, id);
        if (marker != null && shouldWritePlacementMarker(placer, state, x, y, z)) {
            placer.setData(x, y, z, marker);
        }
    }

    private static boolean shouldWritePlacementMarker(IObjectPlacer placer, PlatformBlockState state, int x, int y, int z) {
        if (state == null) {
            return false;
        }
        PlatformBlockState existingState = placer.get(x, y, z);
        boolean wouldReplace = B.isSolid(existingState) && B.isVineBlock(state);
        String material = IrisObjectShaping.materialKey(state);
        boolean placesBlock = !material.equals("minecraft:air") && !material.equals("minecraft:cave_air") && !wouldReplace;
        return state.isCustom() || placesBlock;
    }

    private static String placementMarker(IrisObject object, int id) {
        String key = object.getLoadKey();
        if (key == null || key.isEmpty() || key.equals("null")) {
            return null;
        }
        return key + "@" + id;
    }
}
