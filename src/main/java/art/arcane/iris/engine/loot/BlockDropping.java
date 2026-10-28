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

import art.arcane.iris.modules.loot.BlockDrop;
import art.arcane.iris.modules.loot.BlockDropSettings;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.item.ItemDrop;
import com.volmit.service.BlockDrops;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * How {@code iris:loot} drops items for broken blocks: the block drops of the biome, the region and the dimension, the
 * biome's first and the biome able to keep the others from dropping.
 */
public final class BlockDropping implements BlockDrops {
    @Override
    public Result drops(PackWorld world, Broken broken) {
        RunningWorld running = world.running().orElseThrow();
        String brokenKey = world.resources().blocks().find(broken.block().key()).orElse(broken.block()).key();
        ResolvedDrops resolved = world.state(ResolvedDrops.class, ignored -> new ResolvedDrops());
        SettingsStack<BlockDropSettings> column = running.settings().at(BlockDropSettings.class, broken.position().x(), broken.position().z());

        List<ResolvedBlockDrop> providers = dropsFor(BlockBiomes.declaredAt(running, BlockDropSettings.class, broken.position()), brokenKey, resolved, world);
        if (providers.stream().noneMatch(provider -> provider.drop().skipParents())) {
            providers.addAll(dropsFor(column.level(Level.REGION), brokenKey, resolved, world));
            providers.addAll(dropsFor(column.dimension(), brokenKey, resolved, world));
        }

        LootItems items = new LootItems(world.resources().items(), false, world.log());
        List<ItemDrop> drops = new ArrayList<>();
        providers.forEach(provider -> fillDrops(provider.drop(), items, drops));
        return new Result(drops, providers.stream().anyMatch(provider -> provider.drop().replaceVanillaDrops()));
    }

    /** Adds the items of the drops that pass their rarity to {@code into}. */
    private static void fillDrops(BlockDrop drop, LootItems items, List<ItemDrop> into) {
        for (LootEntry entry : drop.drops()) {
            if (!LootResolver.oneIn(RNG.r, entry.rarity())) {
                continue;
            }
            ItemDrop given = items.give(entry, RNG.r);
            if (given != null) {
                into.add(given);
            }
        }
    }

    private static List<ResolvedBlockDrop> dropsFor(Optional<BlockDropSettings> declared, String brokenKey, ResolvedDrops resolved,
                                                   PackWorld world) {
        List<ResolvedBlockDrop> matching = new ArrayList<>();
        for (BlockDrop drop : declared.map(BlockDropSettings::drops).orElse(List.of())) {
            ResolvedBlockDrop candidate = resolved.of(drop, world);
            if (candidate.dropsFor(brokenKey)) {
                matching.add(candidate);
            }
        }
        return matching;
    }

    /** The drops of the world with their blocks resolved, once per drop. */
    private static final class ResolvedDrops {
        private final ConcurrentMap<BlockDrop, ResolvedBlockDrop> byDrop = new ConcurrentHashMap<>();

        private ResolvedBlockDrop of(BlockDrop drop, PackWorld world) {
            return byDrop.computeIfAbsent(drop, key -> ResolvedBlockDrop.of(key, world.resources().blocks()));
        }
    }
}
