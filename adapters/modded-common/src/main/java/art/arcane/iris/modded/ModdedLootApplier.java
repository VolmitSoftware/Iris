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

package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeContainerLoot;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.structure.placement.PlacedObject;
import art.arcane.iris.structure.object.IObjectLoot;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.structure.object.IrisObjectLoot;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectVanillaLoot;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;

import java.util.ArrayList;
import java.util.List;

public final class ModdedLootApplier {
    private ModdedLootApplier() {
    }

    public static void apply(Engine engine, NativeWorld world, NativeBlockPoint pos, NativeBlockState state, MantleChunk<Matter> mc) {
        NativeContainerLoot access = new NativeContainerLoot(new NativeContainerLoot.Target(world, pos, state));
        if (!access.canonical()) {
            return;
        }
        RNG rng = LootResolver.containerRng(engine.getSeedManager().getLoot(), pos.x(), pos.y(), pos.z());
        List<LootSource> sources = resolveSources(engine, access, rng, pos, state, mc, access.hasNativeTable());
        if (sources.isEmpty()) {
            return;
        }
        NativeContainerLoot.Session session = access.open();
        if (session == null) {
            IrisLogging.debug("Iris loot: no container at " + pos);
            return;
        }
        for (LootSource source : sources) {
            if (source instanceof IrisLootSource irisSource) {
                IrisLootTable table = irisSource.table();
                if (table != null) {
                    session.add(ModdedItemTranslator.loot(table, engine.getSeedManager().getLoot(), InventorySlotType.STORAGE,
                            ModdedItemTranslator.context(world), pos.x(), pos.y(), pos.z()));
                }
            } else if (source instanceof NativeLootSource nativeSource) {
                session.generate(nativeSource.table(), rng.nextLong());
            }
        }
        session.fill(rng, message -> IrisLogging.debug("Iris loot: " + message));
    }

    private static List<LootSource> resolveSources(Engine engine, NativeContainerLoot access, RNG rng, NativeBlockPoint pos, NativeBlockState state, MantleChunk<Matter> mc, boolean nativeLootDefined) {
        int rx = pos.x();
        int rz = pos.z();
        int ry = pos.y() - engine.getWorld().minHeight();
        List<LootSource> sources = new ArrayList<>();
        boolean objectLootDefined = nativeLootDefined;

        PlacedObject po = engine.getObjectPlacement(rx, ry, rz, mc);
        if (po != null && po.getPlacement() != null && state.isStorageChest()) {
            objectLootDefined = objectLootDefined
                    || po.getPlacement().getLoot().isNotEmpty()
                    || po.getPlacement().getVanillaLoot().isNotEmpty();
            Candidate picked = pickPlacementTable(engine, access, po.getPlacement(), rng);
            if (picked != null) {
                sources.add(picked.source());
                if (po.getPlacement().isOverrideGlobalLoot()) {
                    return sources;
                }
            }
        }

        LootResolver.resolveEnvironmentSources(
                sources,
                engine,
                rng,
                rx,
                ry,
                rz,
                objectLootDefined,
                IrisLootSource::new
        );
        return sources;
    }

    private static Candidate pickPlacementTable(Engine engine, NativeContainerLoot access, IrisObjectPlacement placement, RNG rng) {
        List<Candidate> exact = new ArrayList<>();
        List<Candidate> basic = new ArrayList<>();
        List<Candidate> global = new ArrayList<>();

        for (IrisObjectLoot loot : placement.getLoot()) {
            if (loot == null || loot.getWeight() <= 0) {
                continue;
            }
            IrisLootTable table = engine.getData().getLootLoader().load(loot.getName());
            if (table == null) {
                IrisLogging.warn("Couldn't find loot table " + loot.getName());
                continue;
            }
            bucket(engine, loot, new Candidate(new IrisLootSource(table), loot.getWeight()), access, exact, basic, global);
        }

        for (IrisObjectVanillaLoot loot : placement.getVanillaLoot()) {
            if (loot == null || loot.getWeight() <= 0) {
                continue;
            }
            NativeContainerLoot.Table table = access.table(loot.getName());
            if (table == null) {
                IrisLogging.warn("Couldn't find vanilla loot table " + loot.getName());
                continue;
            }
            bucket(engine, loot, new Candidate(new NativeLootSource(table), loot.getWeight()), access, exact, basic, global);
        }

        List<Candidate> pool = !exact.isEmpty() ? exact : !basic.isEmpty() ? basic : global;
        return LootResolver.pickWeighted(pool, Candidate::weight, rng);
    }

    private static void bucket(Engine engine, IObjectLoot loot, Candidate candidate, NativeContainerLoot access, List<Candidate> exact, List<Candidate> basic, List<Candidate> global) {
        if (loot.getFilter().isEmpty()) {
            global.add(candidate);
            return;
        }

        for (NativeBlockState filterState : loot.getFilter(engine.getData())) {
            if (access.matches(filterState, loot.isExact())) {
                (loot.isExact() ? exact : basic).add(candidate);
                return;
            }
        }
    }

    private interface LootSource {
    }

    private record IrisLootSource(IrisLootTable table) implements LootSource {
    }

    private record NativeLootSource(NativeContainerLoot.Table table) implements LootSource {
    }

    private record Candidate(LootSource source, int weight) {
    }
}
