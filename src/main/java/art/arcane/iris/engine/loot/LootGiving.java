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

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.IrisSeeds;
import art.arcane.iris.builtin.LiveWorld;
import art.arcane.iris.builtin.LiveWorld.ObjectLoots;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.item.ItemDrop;
import com.volmit.resource.ResourceId;
import com.volmit.service.Loot;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How {@code iris:loot} fills containers: the tables of the object placed around a container, then those of the
 * dimension, region and biomes at its column, and the items each table gives. Draws are deterministic per container, so
 * a container always holds the same loot.
 */
public final class LootGiving implements Loot {
    @Override
    public List<Source> tables(PackWorld world, Site site) {
        LiveWorld live = world.extension(LiveWorld.class).orElseThrow();
        int x = site.position().x();
        int z = site.position().z();
        int relativeY = site.position().y();
        int worldY = relativeY + live.bottomY();
        RNG rng = LootResolver.containerRng(lootSeed(world), x, worldY, z);

        List<Source> sources = new ArrayList<>();
        boolean objectLootDefined = site.nativeLootDefined();
        Optional<ObjectLoots> objectLoots = site.container().isEmpty() ? Optional.empty() : live.objectLoots(x, relativeY, z);
        if (objectLoots.isPresent()) {
            objectLootDefined = objectLootDefined || objectLoots.get().defined();
            Source picked = placementLoot(world, live).pick(objectLoots.get().platformTables(), objectLoots.get().packTables(),
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
        int worldY = site.position().y() + world.extension(LiveWorld.class).orElseThrow().bottomY();
        return new LootItems(world.resources().items(), site.debug(), world.log())
                .draw(tableId.path(), table, lootSeed(world), site.position().x(), worldY, site.position().z());
    }

    private static PlacementLoot placementLoot(PackWorld world, LiveWorld live) {
        return new PlacementLoot(world.resources(), live::platformLootTables, lootSeed(world), world.log());
    }

    private static long lootSeed(PackWorld world) {
        return world.extension(IrisSeeds.class).orElseThrow().seed(IrisSeed.LOOT);
    }
}
