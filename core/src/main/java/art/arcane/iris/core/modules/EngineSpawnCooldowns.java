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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.spawn.SpawnCooldowns;
import art.arcane.iris.engine.framework.Engine;

import java.util.function.ToLongFunction;

/** The cooldowns of an engine's spawners, in the data the engine saves with its world. */
final class EngineSpawnCooldowns implements SpawnCooldowns {
    private final Engine engine;

    EngineSpawnCooldowns(Engine engine) {
        this.engine = engine;
    }

    @Override
    public boolean canSpawn(String spawner, long intervalMilliseconds) {
        return engine.getEngineData().getCooldown(spawner).canSpawn(intervalMilliseconds);
    }

    @Override
    public void spawned(String spawner) {
        engine.getEngineData().getCooldown(spawner).spawn();
    }

    @Override
    public boolean canSpawnInChunk(int chunkX, int chunkZ, String spawner, long intervalMilliseconds) {
        return engine.getEngineData().getChunk(chunkX, chunkZ).getCooldown(spawner).canSpawn(intervalMilliseconds);
    }

    @Override
    public void spawnedInChunk(int chunkX, int chunkZ, String spawner) {
        engine.getEngineData().getChunk(chunkX, chunkZ).getCooldown(spawner).spawn();
    }

    @Override
    public void forgetExpired(ToLongFunction<String> intervalMilliseconds) {
        engine.getEngineData().cleanup(intervalMilliseconds);
    }
}
