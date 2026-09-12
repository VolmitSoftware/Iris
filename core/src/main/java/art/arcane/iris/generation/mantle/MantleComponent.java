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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import org.jetbrains.annotations.NotNull;

public interface MantleComponent extends Comparable<MantleComponent> {
    int getPriority();

    default MatterGenerationPhase getGenerationPhase() {
        return MatterGenerationPhase.CONTENT;
    }

    int getRadius();

    default int getOutputRadius() {
        return getRadius();
    }

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

    default IrisData getData() {
        return getEngineMantle().getData();
    }

    default IrisDimension getDimension() {
        return getEngineMantle().getEngine().getDimension();
    }

    default IrisComplex getComplex() {
        return getEngineMantle().getComplex();
    }

    default long seed() {
        return getEngineMantle().getEngine().getSeedManager().getMantle();
    }

    default BurstExecutor burst() {
        return getEngineMantle().getEngine().burst().burst();
    }

    EngineMantle getEngineMantle();

    default Mantle<Matter> getMantle() {
        return getEngineMantle().getMantle();
    }

    MantleFlag getFlag();

    default MantleFlag[] getPrerequisiteFlags() {
        return EMPTY_PREREQUISITES;
    }

    MantleFlag[] EMPTY_PREREQUISITES = new MantleFlag[0];

    boolean isEnabled();

    void setEnabled(boolean b);

    void hotload();

    @ChunkCoordinates
    void generateLayer(MantleWriter writer, int x, int z, ChunkContext context);

    @Override
    default int compareTo(@NotNull MantleComponent o) {
        return Integer.compare(getPriority(), o.getPriority());
    }
}
