/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.platform.studio;

import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class EnginedStudioGenerator implements StudioGenerator {
    private final Engine engine;

    @Override
    public abstract void generateChunk(Engine engine, TerrainChunk tc, int x, int z) throws WrongEngineBroException;
}
