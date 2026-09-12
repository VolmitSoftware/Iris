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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.hunk.Hunk;
public abstract class EngineAssignedModifier<T> extends EngineAssignedComponent implements EngineModifier<T> {
    public EngineAssignedModifier(Engine engine, String name) {
        super(engine, name);
    }

    @BlockCoordinates
    public abstract void onModify(int x, int z, Hunk<T> output, boolean multicore, ChunkContext context);

    @BlockCoordinates
    @Override
    public void modify(int x, int z, Hunk<T> output, boolean multicore, ChunkContext context) {
        try {
            onModify(x, z, output, multicore, context);
        } catch (Throwable e) {
            IrisLogging.error("Modifier Failure: " + getName());
            IrisLogging.reportError(e);

            // A failed modifier leaves a half-written chunk. Never continue as if it succeeded,
            // let the chunk generation failure path handle it.
            if (e instanceof Error error) {
                throw error;
            }

            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }

            throw new IllegalStateException("Modifier Failure: " + getName(), e);
        }
    }
}
