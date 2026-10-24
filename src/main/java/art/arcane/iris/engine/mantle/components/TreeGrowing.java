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

import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectHost.Declaration;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ObjectSink;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.TreeGrowthSettings;
import art.arcane.iris.builtin.objects.TreeMode;
import art.arcane.iris.builtin.objects.tree.Tree;
import art.arcane.iris.engine.loot.BlockBiomes;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.config.Level;
import com.volmit.placement.PlacedBlock;
import com.volmit.resource.ResourceId;
import com.volmit.service.SaplingGrowth;
import com.volmit.geometry.BlockPosition;
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

    @Override
    public Optional<Growth> grow(PackWorld world, Sapling sapling, Surroundings surroundings) {
        ObjectHost host = world.extension(ObjectHost.class).orElseThrow();
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
        Optional<PlaceableObject> tree = host.object(keys.get(RNG.r.i(keys.size())));
        if (tree.isEmpty()) {
            return Optional.empty();
        }
        PlaceableObject object = tree.get();
        GrowthSink sink = new GrowthSink(surroundings, running.layout().fluidHeight());
        BlockPosition center = sapling.center();
        host.placeInto(object, center.x(), center.y() + object.height() / 2, center.z(), placement.get(), sink, RNG.r);
        return Optional.of(new Growth(world.resources().idOf(object.key()), sink.placed()));
    }

    @Override
    public TreeObjects treeObjects(PackWorld world) {
        ObjectHost host = world.extension(ObjectHost.class).orElseThrow();
        Set<String> objects = new HashSet<>();
        Set<String> procedural = new HashSet<>();
        for (Declaration<ObjectSettings> declaration : host.declarations(ObjectSettings.class)) {
            collectPlacements(declaration.settings().objects(), objects);
        }
        for (Declaration<ProceduralObjectSettings> declaration : host.declarations(ProceduralObjectSettings.class)) {
            collectProceduralTrees(declaration.settings().trees(), procedural);
        }
        return new TreeObjects(idsOf(world, objects), idsOf(world, procedural));
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

    private static void collectPlacements(List<ObjectPlacement> placements, Set<String> objectKeys) {
        for (ObjectPlacement placement : placements) {
            if (placement == null || placement.trees().isEmpty()) {
                continue;
            }
            for (String objectKey : placement.place()) {
                if (objectKey != null && !objectKey.isBlank()) {
                    objectKeys.add(objectKey);
                }
            }
        }
    }

    private static void collectProceduralTrees(List<Tree> trees, Set<String> objectKeys) {
        for (Tree tree : trees) {
            if (tree == null || tree.name() == null || tree.name().isBlank()) {
                continue;
            }
            int variants = Math.max(1, tree.variants());
            for (int index = 0; index < variants; index++) {
                objectKeys.add(Tree.variantLoadKey(tree.name(), index));
            }
        }
    }

    /** Places an object into a list of blocks, reading the world around the sapling through the host. */
    private static final class GrowthSink implements ObjectSink {
        private final Surroundings surroundings;
        private final int fluidHeight;
        private final List<PlacedBlock> placed = new ArrayList<>();

        private GrowthSink(Surroundings surroundings, int fluidHeight) {
            this.surroundings = surroundings;
            this.fluidHeight = fluidHeight;
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
            return surroundings.blockAt(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            placed.add(new PlacedBlock(new BlockPosition(x, y, z), state, Optional.empty()));
        }

        @Override
        public int fluidHeight() {
            return fluidHeight;
        }
    }
}
