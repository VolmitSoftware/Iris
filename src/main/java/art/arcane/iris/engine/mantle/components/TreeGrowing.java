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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.IrisTreeSize;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.engine.object.procedural.IrisProceduralObjects;
import art.arcane.iris.engine.object.procedural.tree.IrisProceduralTree;
import art.arcane.iris.engine.object.procedural.tree.IrisTreeModes;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.placement.PlacedBlock;
import com.volmit.service.SaplingGrowth;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * How {@code iris:objects} grows trees: when a sapling grows, the tree comes from the tree placements of the biome and
 * region at the sapling (the region's too when the dimension uses all of them), placed like any object but into a list
 * of blocks the host then places. The same placements tell which objects are trees.
 */
public final class TreeGrowing implements SaplingGrowth {
    private static final IrisTreeSize SAPLING_SIZE = new IrisTreeSize(1, 1);

    @Override
    public Optional<Growth> grow(PackWorld world, Sapling sapling, Surroundings surroundings) {
        Engine engine = IrisPackWorld.of(world).engine();
        if (!engine.getDimension().getTreeSettings().isEnabled()) {
            return Optional.empty();
        }

        IrisObjectPlacement placement = pickPlacement(engine, sapling);
        if (placement == null) {
            return Optional.empty();
        }

        IrisData data = engine.getData();
        IrisObject object = data.getObjectLoader().load(placement.getPlace().getRandom(RNG.r));
        GrowthPlacer placer = new GrowthPlacer(engine, surroundings);
        BlockPosition center = sapling.center();
        object.place(center.x(), center.y() + object.getH() / 2, center.z(), placer, placement, RNG.r, data);
        return Optional.of(new Growth(object.getLoadKey(), placer.placed()));
    }

    @Override
    public TreeObjects treeObjects(PackWorld world) {
        Engine engine = IrisPackWorld.of(world).engine();
        Set<String> objects = new HashSet<>();
        Set<String> procedural = new HashSet<>();

        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region != null) {
                collectPlacements(region.getObjects(), objects);
                collectProceduralTrees(region.getProceduralObjects(), procedural);
            }
        }
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            if (biome != null) {
                collectPlacements(biome.getObjects(), objects);
                collectProceduralTrees(biome.getProceduralObjects(), procedural);
            }
        }
        return new TreeObjects(objects, procedural);
    }

    /** One placement, picked at random, of the tree placements that grow {@code sapling}'s species; null for none. */
    private static IrisObjectPlacement pickPlacement(Engine engine, Sapling sapling) {
        KList<IrisObjectPlacement> placements = new KList<>();
        boolean useAll = engine.getDimension().getTreeSettings().getMode().equals(IrisTreeModes.ALL);

        BlockPosition at = sapling.at();
        placements.addAll(matching(engine.getBiome(at.x(), at.y(), at.z()).getObjects(), sapling.species()));
        if (useAll || placements.isEmpty()) {
            placements.addAll(matching(engine.getRegion(at.x(), at.z()).getObjects(), sapling.species()));
        }
        return placements.isNotEmpty() ? placements.getRandom() : null;
    }

    private static KList<IrisObjectPlacement> matching(KList<IrisObjectPlacement> objects, String species) {
        KList<IrisObjectPlacement> matches = new KList<>();
        for (IrisObjectPlacement placement : objects) {
            if (placement.matches(SAPLING_SIZE, species)) {
                matches.add(placement);
            }
        }
        return matches;
    }

    private static void collectPlacements(Iterable<IrisObjectPlacement> placements, Set<String> objectKeys) {
        if (placements == null) {
            return;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null
                    || placement.getTrees() == null
                    || placement.getTrees().isEmpty()
                    || placement.getPlace() == null) {
                continue;
            }
            for (String objectKey : placement.getPlace()) {
                if (objectKey != null && !objectKey.isBlank()) {
                    objectKeys.add(objectKey);
                }
            }
        }
    }

    private static void collectProceduralTrees(IrisProceduralObjects proceduralObjects, Set<String> objectKeys) {
        if (proceduralObjects == null || proceduralObjects.getTrees() == null) {
            return;
        }
        for (IrisProceduralTree tree : proceduralObjects.getTrees()) {
            if (tree == null || tree.getName() == null || tree.getName().isBlank()) {
                continue;
            }
            int variants = Math.max(1, tree.getVariants());
            for (int index = 0; index < variants; index++) {
                objectKeys.add(tree.getVariantLoadKey(index));
            }
        }
    }

    /** Places an object into a list of blocks, reading the world around the sapling through the host. */
    private static final class GrowthPlacer implements IObjectPlacer {
        private final Engine engine;
        private final Surroundings surroundings;
        private final PlatformBlockStates states = new PlatformBlockStates(key -> IrisPlatforms.get().registries().block(key));
        private final List<PlacedBlock> placed = new ArrayList<>();

        private GrowthPlacer(Engine engine, Surroundings surroundings) {
            this.engine = engine;
            this.surroundings = surroundings;
        }

        private List<PlacedBlock> placed() {
            return placed;
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return surroundings.highest(x, z, false);
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            return surroundings.highest(x, z, ignoreFluid);
        }

        @Override
        public void set(int x, int y, int z, PlatformBlockState state) {
            placed.add(new PlacedBlock(new BlockPosition(x, y, z), states.toApi(state), Optional.empty()));
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
            BlockState block = surroundings.blockAt(x, y, z);
            return states.toPlatform(block);
        }

        @Override
        public boolean isPreventingDecay() {
            return true;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSurfaceSolid(int x, int y, int z) {
            return isSolid(x, y, z);
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return get(x, y, z).isSolid();
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int getFluidHeight() {
            return engine.getDimension().getFluidHeight();
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int xx, int yy, int zz, TileData tile) {
        }

        @Override
        public <T> void setData(int xx, int yy, int zz, T data) {
        }

        @Override
        public <T> T getData(int xx, int yy, int zz, Class<T> t) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }
}
