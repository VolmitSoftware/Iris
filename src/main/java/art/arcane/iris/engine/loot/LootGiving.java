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

import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.object.loot.IrisLootTable;
import art.arcane.iris.engine.object.loot.IrisVanillaLootTable;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.item.ItemDrop;
import com.volmit.service.Loot;
import com.volmit.world.PackWorld;

import java.util.List;

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
        RNG rng = LootResolver.containerRng(lootSeed(engine), x, relativeY + engine.getMinHeight(), z);

        List<IrisLootTable> tables = new KList<>();
        boolean objectLootDefined = site.nativeLootDefined();
        IrisObjectPlacement placement = site.container() == null ? null : placementAt(engine, x, relativeY, z);
        if (placement != null) {
            objectLootDefined = objectLootDefined || placement.getLoot().isNotEmpty() || placement.getVanillaLoot().isNotEmpty();
            IrisLootTable table = placement.getTable(platformState(site), engine.getData(), rng);
            if (table != null) {
                tables.add(table);
                if (placement.isOverrideGlobalLoot()) {
                    return sourcesOf(engine, site, List.of(table));
                }
            }
        }

        LootTables.resolveEnvironmentSources(tables, engine, rng, x, relativeY, z, objectLootDefined, table -> table);
        return sourcesOf(engine, site, tables);
    }

    @Override
    public List<ItemDrop> items(PackWorld world, Site site, String key) {
        Engine engine = engine(world);
        IrisLootTable table = engine.getData().getLootLoader().load(key);
        if (table == null) {
            return List.of();
        }
        int worldY = site.position().y() + engine.getMinHeight();
        return new LootItems(world.resources().items(), site.debug())
                .draw(table, lootSeed(engine), site.position().x(), worldY, site.position().z());
    }

    private static IrisObjectPlacement placementAt(Engine engine, int x, int relativeY, int z) {
        PlacedObject placed = engine.getObjectPlacement(x, relativeY, z);
        return placed == null ? null : placed.getPlacement();
    }

    private static List<Source> sourcesOf(Engine engine, Site site, List<IrisLootTable> tables) {
        int worldY = site.position().y() + engine.getMinHeight();
        List<Source> sources = new KList<>();
        for (IrisLootTable table : tables) {
            if (table instanceof IrisVanillaLootTable vanilla) {
                long seed = LootTables.seed(lootSeed(engine), vanilla, site.position().x(), worldY, site.position().z());
                sources.add(new PlatformTable(vanilla.getLootTableKey(), seed));
                continue;
            }
            if (table != null && table.getLoadKey() != null) {
                sources.add(new Table(table.getLoadKey()));
            }
        }
        return sources;
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
}
