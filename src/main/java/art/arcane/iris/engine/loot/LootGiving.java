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

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.engine.loot.PlacedObjectLoot.ObjectLoots;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.item.ItemDrop;
import com.volmit.resource.ResourceId;
import com.volmit.service.Loot;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * How {@code iris:loot} fills containers: the tables of the object placed around a container, then those of the
 * dimension, region and biomes at its column, and the items each table gives. Draws are deterministic per container, so
 * a container always holds the same loot.
 */
public final class LootGiving implements Loot {
    @Override
    public List<Source> tables(PackWorld world, Site site) {
        int x = site.position().x();
        int z = site.position().z();
        int relativeY = site.position().y();
        int worldY = relativeY + world.minWorldY();
        RNG rng = LootResolver.containerRng(lootSeed(world), x, worldY, z);

        List<Source> sources = new ArrayList<>();
        boolean objectLootDefined = site.nativeLootDefined();
        Optional<ObjectLoots> objectLoots = site.container().isEmpty() ? Optional.empty()
                : PlacedObjectLoot.at(world, x, relativeY, z);
        if (objectLoots.isPresent()) {
            objectLootDefined = objectLootDefined || objectLoots.get().defined();
            Source picked = placementLoot(world).pick(objectLoots.get().platformTables(), objectLoots.get().packTables(),
                    site.container().get(), rng, x, worldY, z);
            if (picked != null) {
                sources.add(picked);
                if (objectLoots.get().overridesGlobalLoot()) {
                    return sources;
                }
            }
        }

        LootTables.resolveEnvironmentSources(sources, world, rng, x, relativeY, z, objectLootDefined,
                key -> new Table(world.resources().idOf(key)));
        return sources;
    }

    @Override
    public List<ItemDrop> items(PackWorld world, Site site, ResourceId tableId) {
        LootTable table = LootResources.table(world.resources(), tableId);
        if (table == null) {
            return List.of();
        }
        int worldY = site.position().y() + world.minWorldY();
        return new LootItems(world.resources().items(), site.debug(), world.log())
                .draw(tableId, table, lootSeed(world), site.position().x(), worldY, site.position().z());
    }

    @Override
    public OptionalLong shuffleSeed(PackWorld world, Site site) {
        int worldY = site.position().y() + world.minWorldY();
        return OptionalLong.of(LootResolver.containerSeed(lootSeed(world), site.position().x(), worldY, site.position().z()));
    }

    private static PlacementLoot placementLoot(PackWorld world) {
        return new PlacementLoot(world.resources(), () -> world.running().orElseThrow().platformLootTables(), lootSeed(world), world.log());
    }

    private static long lootSeed(PackWorld world) {
        return world.seed(IrisSeedPurposes.LOOT);
    }
}
