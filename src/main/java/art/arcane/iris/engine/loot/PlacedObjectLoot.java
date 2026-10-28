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
import art.arcane.iris.builtin.structure.StructureHost;
import art.arcane.iris.builtin.structure.StructurePlacementMarker;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.resource.PackResources;
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
        Optional<StructurePlacementMarker.Decoded> marker = running.placementAt(x, y, z).map(StructurePlacementMarker::decode);
        if (marker.isEmpty() || marker.get().objectKey().startsWith(PROCEDURAL_PREFIX)) {
            return Optional.empty();
        }
        String objectKey = marker.get().objectKey();
        Optional<ObjectPlacement> placement = marker.get().structureAware()
                ? structurePlacement(world, marker.get().structureKey(), objectKey)
                : columnPlacement(running.settings().at(ObjectSettings.class, x, z), objectKey);
        return placement.map(found -> new ObjectLoots(found.loot(), found.vanillaLoot(), found.overrideGlobalLoot()));
    }

    /** A structure piece's object gives every table its structure names, each once. */
    private static Optional<ObjectPlacement> structurePlacement(PackWorld world, String structureKey, String objectKey) {
        Optional<List<String>> tables = world.extension(StructureHost.class).orElseThrow().lootTables(structureKey);
        PackResources resources = world.resources();
        if (tables.isEmpty() || resources.objects().object(resources.idOf(objectKey)).isEmpty()) {
            return Optional.empty();
        }
        List<ObjectLoot> loot = new ArrayList<>();
        for (String table : tables.get()) {
            loot.add(new ObjectLoot(table, STRUCTURE_TABLE_WEIGHT));
        }
        return Optional.of(ObjectPlacement.builder().place(List.of(objectKey)).loot(loot).build());
    }

    private static Optional<ObjectPlacement> columnPlacement(SettingsStack<ObjectSettings> declared, String objectKey) {
        Optional<ObjectPlacement> fromRegion = declared.level(Level.REGION).flatMap(settings -> placing(settings, objectKey));
        return fromRegion.isPresent() ? fromRegion : declared.level(Level.BIOME).flatMap(settings -> placing(settings, objectKey));
    }

    private static Optional<ObjectPlacement> placing(ObjectSettings settings, String objectKey) {
        return settings.objects().stream()
                .filter(placement -> placement.place().contains(objectKey))
                .findFirst();
    }
}
