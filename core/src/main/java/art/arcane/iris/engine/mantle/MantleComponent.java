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

package art.arcane.iris.engine.mantle;

import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import org.jetbrains.annotations.NotNull;

/**
 * One pass of the mantle: {@link MatterGenerator} runs every component of a priority for each chunk within its
 * radius plus every later pass's radius around a generating chunk, before the next priority starts. Iris's
 * components are the pack's mantle stages, one each ({@code ModuleMantleComponent}).
 */
public interface MantleComponent extends Comparable<MantleComponent> {
    int getPriority();

    int getRadius();

    default int getInputRadius() {
        return 0;
    }

    default int getInputRadius(
            int targetChunkX,
            int targetChunkZ,
            int invocationChunkRadius,
            ChunkContext context
    ) {
        return getInputRadius();
    }

    default boolean isInputGenerationLazy() {
        return false;
    }

    MantleFlag getFlag();

    default MantleFlag[] getPrerequisiteFlags() {
        return EMPTY_PREREQUISITES;
    }

    MantleFlag[] EMPTY_PREREQUISITES = new MantleFlag[0];

    boolean isEnabled();

    @ChunkCoordinates
    void generateLayer(MantleWriter writer, int x, int z, ChunkContext context);

    @Override
    default int compareTo(@NotNull MantleComponent o) {
        return Integer.compare(getPriority(), o.getPriority());
    }
}
