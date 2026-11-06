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

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectRun;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.modules.objects.TreeGrowthSettings;
import art.arcane.iris.modules.objects.TreeMode;
import art.arcane.iris.engine.loot.BlockBiomes;
import art.arcane.iris.modules.objects.Declaration;
import art.arcane.iris.modules.objects.ObjectPool;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.config.Level;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.PlaceableObject;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.placement.StructureVolumes;
import com.volmit.resource.ResourceId;
import com.volmit.service.SaplingGrowth;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How {@code iris:objects} grows trees: when a sapling grows, the tree comes from the tree placements of the biome and
 * region at the sapling (the region's too when the dimension uses all of them), placed like any object but into a list
 * of blocks the host then places. The same placements tell which objects are trees.
 */
public final class TreeGrowing implements SaplingGrowth {
    private static final int SAPLING_WIDTH = 1;
    private static final int SAPLING_DEPTH = 1;
    private static final String PROCEDURAL_TREE_FOLDER = ProceduralObjectCatalog.KEY_PREFIX + "trees/";

    @Override
    public Optional<Growth> grow(PackWorld world, Sapling sapling, Surroundings surroundings) {
        RunningWorld running = world.running().orElseThrow();
        BlockPosition at = sapling.at();
        TreeGrowthSettings growth = treeGrowth(running, at);
        if (!growth.enabled()) {
            return Optional.empty();
        }
        Optional<ObjectPlacement> placement = pickPlacement(running, growth, sapling);
        if (placement.isEmpty()) {
            return Optional.empty();
        }
        List<String> keys = placement.get().place();
        Optional<PlaceableObject> tree = ObjectPool.of(world).object(keys.get(RNG.r.i(keys.size())), RNG.r);
        if (tree.isEmpty()) {
            return Optional.empty();
        }
        PlaceableObject object = tree.get();
        GrowthTerrain terrain = new GrowthTerrain(surroundings, running.layout().fluidHeight(), world.resources().blocks());
        BlockPosition center = sapling.center();
        ObjectRun run = ObjectRun.of(object, placement.get(), center.x(), center.y() + object.height() / 2, center.z());
        run.placeIn(world, terrain);
        return Optional.of(new Growth(world.resources().idOf(object.name()), terrain.placed()));
    }

    @Override
    public TreeObjects treeObjects(PackWorld world) {
        Set<String> objects = new HashSet<>();
        for (Declaration<ObjectSettings> declaration : Declaration.allOf(world, ObjectSettings.class)) {
            collectPlacements(declaration.settings().objects(), objects);
        }
        return new TreeObjects(idsOf(world, placedNames(world, objects)));
    }

    /** The names objects are placed under: a procedural pool's are its variants' own. */
    private static Set<String> placedNames(PackWorld world, Set<String> objectKeys) {
        ProceduralObjectCatalog procedural = ProceduralObjectCatalog.of(world);
        Set<String> names = new HashSet<>();
        for (String key : objectKeys) {
            if (ProceduralObjectCatalog.isProceduralKey(key)) {
                procedural.placeable(key).forEach(variant -> names.add(variant.name()));
            } else {
                names.add(key);
            }
        }
        return names;
    }

    private static Set<ResourceId> idsOf(PackWorld world, Set<String> keys) {
        return keys.stream().map(world.resources()::idOf).collect(Collectors.toSet());
    }

    private static TreeGrowthSettings treeGrowth(RunningWorld running, BlockPosition at) {
        return running.settings().at(TreeGrowthSettings.class, at.x(), at.z()).dimension().orElseGet(TreeGrowthSettings::new);
    }

    /** One placement, picked at random, of the tree placements that grow {@code sapling}'s species. */
    private static Optional<ObjectPlacement> pickPlacement(RunningWorld running, TreeGrowthSettings growth, Sapling sapling) {
        List<ObjectPlacement> placements = new ArrayList<>();
        BlockPosition at = sapling.at();
        BlockBiomes.declaredAt(running, ObjectSettings.class, at).ifPresent(settings -> placements.addAll(matching(settings, sapling.species())));
        if (growth.mode() == TreeMode.ALL || placements.isEmpty()) {
            running.settings().at(ObjectSettings.class, at.x(), at.z()).level(Level.REGION)
                    .ifPresent(settings -> placements.addAll(matching(settings, sapling.species())));
        }
        if (placements.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(placements.get(RNG.r.i(placements.size())));
    }

    /** The placements whose trees override {@code species}, which packs name by the path of its id, ignoring case. */
    private static List<ObjectPlacement> matching(ObjectSettings settings, ResourceId species) {
        List<ObjectPlacement> matches = new ArrayList<>();
        for (ObjectPlacement placement : settings.objects()) {
            if (placement.trees().stream().anyMatch(tree -> tree.matches(SAPLING_WIDTH, SAPLING_DEPTH, species.path()))) {
                matches.add(placement);
            }
        }
        return matches;
    }

    /** The objects of tree placements, and procedural objects in {@code procedural/trees/} wherever they are placed. */
    private static void collectPlacements(List<ObjectPlacement> placements, Set<String> objectKeys) {
        for (ObjectPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            for (String objectKey : placement.place()) {
                if (objectKey != null && !objectKey.isBlank() && (!placement.trees().isEmpty() || isProceduralTree(objectKey))) {
                    objectKeys.add(objectKey);
                }
            }
        }
    }

    private static boolean isProceduralTree(String objectKey) {
        return objectKey.startsWith(PROCEDURAL_TREE_FOLDER);
    }

    /**
     * The world around a sapling, read through the host, collecting what a tree writes into a list of blocks the host
     * then places. Its leaves never decay; it has no caves, water, structures or placement records, and keeps no block
     * entities or markers.
     */
    private static final class GrowthTerrain implements PlacementTerrain {
        private static final StructureVolumes NO_STRUCTURES = new StructureVolumes() {
            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                return false;
            }

            @Override
            public boolean containsWithin(int x, int y, int z, int margin) {
                return false;
            }
        };

        private final Surroundings surroundings;
        private final int fluidHeight;
        private final BlockRegistry blocks;
        private final List<PlacedBlock> placed = new ArrayList<>();

        private GrowthTerrain(Surroundings surroundings, int fluidHeight, BlockRegistry blocks) {
            this.surroundings = surroundings;
            this.fluidHeight = fluidHeight;
            this.blocks = blocks;
        }

        private List<PlacedBlock> placed() {
            return placed;
        }

        @Override
        public int highest(int x, int z, boolean ignoreFluid) {
            return surroundings.highest(x, z, ignoreFluid);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            return surroundings.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            placed.add(new PlacedBlock(new BlockPosition(x, y, z), state, Optional.empty()));
        }

        @Override
        public void set(int x, int y, int z, BlockState state, BlockEntityData blockEntity) {
            set(x, y, z, state);
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSurfaceSolid(int x, int y, int z) {
            return blocks.hasTrait(get(x, y, z), BlockTrait.SOLID);
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int fluidHeight() {
            return fluidHeight;
        }

        @Override
        public boolean preventsDecay() {
            return true;
        }

        @Override
        public void recordPlacement(int x, int y, int z, PlacementRecord placement) {
        }

        @Override
        public Optional<PlacementRecord> placementAt(int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ) {
            return NO_STRUCTURES;
        }

        @Override
        public void mark(PlacedMarker marker) {
        }
    }
}
