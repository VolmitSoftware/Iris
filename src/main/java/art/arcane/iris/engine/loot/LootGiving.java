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

import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.item.ItemDrop;
import com.volmit.service.Loot;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * How {@code iris:loot} fills containers: the tables of the object placed around a container, then those of the
 * dimension, region and biomes at its column, and the items each table gives. Draws are deterministic per container, so
 * a container always holds the same loot.
 */
public final class LootGiving implements Loot {
    @Override
    public List<Source> tables(PackWorld world, Site site) {
        Engine engine = engine(world);
        int x = site.position().x();
        int z = site.position().z();
        int relativeY = site.position().y();
        int worldY = relativeY + engine.getMinHeight();
        RNG rng = LootResolver.containerRng(lootSeed(engine), x, worldY, z);

        List<Source> sources = new ArrayList<>();
        boolean objectLootDefined = site.nativeLootDefined();
        IrisObjectPlacement placement = site.container() == null ? null : placementAt(engine, x, relativeY, z);
        if (placement != null) {
            objectLootDefined = objectLootDefined || placement.getLoot().isNotEmpty() || placement.getVanillaLoot().isNotEmpty();
            Source picked = placementLoot(world, engine).pick(placement, platformState(site), rng, x, worldY, z);
            if (picked != null) {
                sources.add(picked);
                if (placement.isOverrideGlobalLoot()) {
                    return sources;
                }
            }
        }

        LootTables.resolveEnvironmentSources(sources, engine, rng, x, relativeY, z, objectLootDefined, Table::new);
        return sources;
    }

    @Override
    public List<ItemDrop> items(PackWorld world, Site site, String key) {
        Engine engine = engine(world);
        LootTable table = LootResources.table(engine.getData(), key);
        if (table == null) {
            return List.of();
        }
        int worldY = site.position().y() + engine.getMinHeight();
        return new LootItems(world.resources().items(), site.debug())
                .draw(key, table, lootSeed(engine), site.position().x(), worldY, site.position().z());
    }

    private static PlacementLoot placementLoot(PackWorld world, Engine engine) {
        PlatformLootTables platformTables = world.state(PlatformLootTables.class, ignored -> new PlatformLootTables());
        return new PlacementLoot(engine.getData(), platformTables::keys, lootSeed(engine));
    }

    private static IrisObjectPlacement placementAt(Engine engine, int x, int relativeY, int z) {
        PlacedObject placed = engine.getObjectPlacement(x, relativeY, z);
        return placed == null ? null : placed.getPlacement();
    }

    private static PlatformBlockState platformState(Site site) {
        return IrisPlatforms.get().registries().block(site.container().key());
    }

    private static long lootSeed(Engine engine) {
        return engine.getSeedManager().getLoot();
    }

    private static Engine engine(PackWorld world) {
        return IrisPackWorld.of(world).engine();
    }

    /** The loot table keys of the platform, which do not change while a world runs. */
    private static final class PlatformLootTables {
        private volatile Set<String> keys;

        private Set<String> keys() {
            Set<String> known = keys;
            if (known == null) {
                known = PlacementLoot.platformTableKeys();
                keys = known;
            }
            return known;
        }
    }
}
