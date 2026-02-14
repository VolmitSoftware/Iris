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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.util.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.mantle.Mantle;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.iris.util.parallel.BurstExecutor;
import org.jetbrains.annotations.NotNull;

public interface MantleComponent extends Comparable<MantleComponent> {
    int getPriority();

    int getRadius();

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

    default Mantle getMantle() {
        return getEngineMantle().getMantle();
    }

    MantleFlag getFlag();

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
