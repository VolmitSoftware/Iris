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
import art.arcane.iris.engine.object.loot.IrisBlockDrops;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.item.ItemDrop;
import com.volmit.service.BlockDrops;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * How {@code iris:loot} drops items for broken blocks: the block drops of the biome, the region and the dimension, the
 * biome's first and the biome able to keep the others from dropping.
 */
public final class BlockDropping implements BlockDrops {
    @Override
    public Result drops(PackWorld world, Broken broken) {
        Engine engine = IrisPackWorld.of(world).engine();
        int x = broken.position().x();
        int y = broken.position().y();
        int z = broken.position().z();
        PlatformBlockState block = IrisPlatforms.get().registries().block(broken.block().key());

        IrisBiome biome = engine.getBiome(x, y, z);
        List<IrisBlockDrops> providers = dropsFor(biome.getBlockDrops(), block, engine);
        if (providers.stream().noneMatch(IrisBlockDrops::isSkipParents)) {
            IrisRegion region = engine.getRegion(x, z);
            providers.addAll(dropsFor(region.getBlockDrops(), block, engine));
            providers.addAll(dropsFor(engine.getDimension().getBlockDrops(), block, engine));
        }

        LootItems items = new LootItems(world.resources().items(), false);
        List<ItemDrop> drops = new ArrayList<>();
        providers.forEach(provider -> provider.fillDrops(items, drops));
        return new Result(drops, providers.stream().anyMatch(IrisBlockDrops::isReplaceVanillaDrops));
    }

    private static List<IrisBlockDrops> dropsFor(KList<IrisBlockDrops> candidates, PlatformBlockState block, Engine engine) {
        return new KList<>(candidates.stream().filter(drops -> drops.shouldDropFor(block, engine.getData())).toList());
    }
}
