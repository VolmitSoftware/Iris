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

import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.service.BlockDrops;
import com.volmit.service.Loot;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:loot}: the loot tables of the pack and the items they and the block drops of the dimension, regions
 * and biomes give. It has no generation stage; Iris asks it through {@link Loot} when a container is filled and through
 * {@link BlockDrops} when a player breaks a block.
 */
public final class LootModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:loot", "Loot", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerService(Loot.class, new LootGiving());
        registry.registerService(BlockDrops.class, new BlockDropping());
    }
}
