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

import art.arcane.iris.modules.BuiltInModulesVersion;
import art.arcane.iris.modules.loot.BlockDropSettings;
import art.arcane.iris.builtin.loot.LootSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.service.BlockDrops;
import com.volmit.service.Loot;

/**
 * Module {@code iris:loot}: the loot tables of the pack ({@link LootResources}), the {@link LootSettings} and
 * {@link BlockDropSettings} that dimensions, regions and biomes declare, and the items they give. It checks the tables
 * and the references to them ({@link LootValidator}). It has no generation stage; Iris asks it through {@link Loot} when
 * a container is filled and through {@link BlockDrops} when a player breaks a block.
 */
public final class LootModule implements IrisModule {
    private static final String VERSION = BuiltInModulesVersion.VERSION;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:loot", "Loot", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        registry.registerSettings(LootSettings.class);
        registry.registerSettings(BlockDropSettings.class);
        LootResources.kinds().forEach(registry::registerResourceKind);
        registry.registerValidator(new LootValidator());
        registry.registerService(Loot.class, new LootGiving());
        registry.registerService(BlockDrops.class, new BlockDropping());
    }
}
