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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.modules.spawn.EffectSettings;
import art.arcane.iris.modules.spawn.SpawnerSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.service.AmbientEffects;
import com.volmit.service.EntitySpawns;

/**
 * Module {@code iris:spawn}: the spawners, entities and markers of the pack ({@link SpawnResources}), the
 * {@link SpawnerSettings} that dimensions, regions and biomes list, the entities that appear in the world over time
 * because of them, and the ambient effects ({@link EffectSettings}) that play around players. It has no generation
 * stage; Iris asks it through {@link EntitySpawns} when it looks for something to spawn in a chunk or at a marker, and
 * through {@link AmbientEffects} for the effects due around a player.
 */
public final class SpawnModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:spawn", "Spawn", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        registry.registerSettings(SpawnerSettings.class);
        registry.registerSettings(EffectSettings.class);
        SpawnResources.kinds().forEach(registry::registerResourceKind);
        registry.registerService(EntitySpawns.class, new EntitySpawning());
        registry.registerService(AmbientEffects.class, new AmbientEffecting());
    }
}
