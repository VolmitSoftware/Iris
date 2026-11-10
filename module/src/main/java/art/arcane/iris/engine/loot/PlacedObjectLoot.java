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

package art.arcane.iris.engine.loot;

import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.loot.ObjectVanillaLoot;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import art.arcane.iris.engine.framework.structure.assembly.StructureStamp;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.placement.PlacementRecord;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The loot of the object placed at a block, found from the placement the world recorded there: a structure piece gives
 * its structure's tables; any other object the tables of the first placement of the column's region, then of its
 * surface biome, that places it. Procedural objects give none.
 */
final class PlacedObjectLoot {
    private static final String PROCEDURAL_PREFIX = "procedural/";
    private static final int STRUCTURE_TABLE_WEIGHT = 1;

    private PlacedObjectLoot() {
    }

    /**
     * The loot an object placement gives its containers.
     *
     * @param packTables          tables of the pack
     * @param platformTables      tables of the platform
     * @param overridesGlobalLoot whether the placement's tables replace those of the dimension, region and biomes
     */
    record ObjectLoots(List<ObjectLoot> packTables, List<ObjectVanillaLoot> platformTables, boolean overridesGlobalLoot) {
        ObjectLoots {
            packTables = List.copyOf(packTables);
            platformTables = List.copyOf(platformTables);
        }

        /** Whether the placement names any table. */
        boolean defined() {
            return !packTables.isEmpty() || !platformTables.isEmpty();
        }
    }

    /** The loot of the object placed at the block; empty when no object placement covers the block. Y is above the dimension's bottom. */
    static Optional<ObjectLoots> at(PackWorld world, int x, int y, int z) {
        RunningWorld running = world.running().orElseThrow();
        Optional<PlacementRecord> recorded = running.placementAt(x, y, z);
        if (recorded.isEmpty() || recorded.get().object().path().startsWith(PROCEDURAL_PREFIX)) {
            return Optional.empty();
        }
        ResourceId object = recorded.get().object();
        Optional<ObjectPlacement> placement = recorded.get().structure().isPresent()
                ? structurePlacement(world, recorded.get().structure().get(), object)
                : columnPlacement(world.resources(), running.settings().at(ObjectSettings.class, x, z), object);
        return placement.map(found -> new ObjectLoots(found.loot(), found.vanillaLoot(), found.overrideGlobalLoot()));
    }

    /** A structure piece's object gives every table its structure names, each once. */
    private static Optional<ObjectPlacement> structurePlacement(PackWorld world, ResourceId structure, ResourceId object) {
        Optional<StructureStamp> stamp = stampOf(world, structure);
        if (stamp.isEmpty() || world.resources().objects().object(object).isEmpty()) {
            return Optional.empty();
        }
        List<ObjectLoot> loot = new ArrayList<>();
        for (String table : stamp.get().loot()) {
            loot.add(new ObjectLoot(table, STRUCTURE_TABLE_WEIGHT));
        }
        return Optional.of(ObjectPlacement.builder().place(List.of(object.path())).loot(loot).build());
    }

    private static Optional<StructureStamp> stampOf(PackWorld world, ResourceId structure) {
        for (StructureAssembly provider : world.services(StructureAssembly.class)) {
            Optional<StructureStamp> stamp = provider.stamp(world.resources(), structure);
            if (stamp.isPresent()) {
                return stamp;
            }
        }
        return Optional.empty();
    }

    private static Optional<ObjectPlacement> columnPlacement(PackResources resources, SettingsStack<ObjectSettings> declared,
                                                             ResourceId object) {
        Optional<ObjectPlacement> fromRegion = declared.level(Level.REGION).flatMap(settings -> placing(resources, settings, object));
        return fromRegion.isPresent() ? fromRegion : declared.level(Level.BIOME).flatMap(settings -> placing(resources, settings, object));
    }

    private static Optional<ObjectPlacement> placing(PackResources resources, ObjectSettings settings, ResourceId object) {
        return settings.objects().stream()
                .filter(placement -> placement.place().stream().anyMatch(key -> resources.idOf(key).equals(object)))
                .findFirst();
    }
}
