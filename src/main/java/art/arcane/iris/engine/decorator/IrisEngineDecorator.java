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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedComponent;
import art.arcane.iris.engine.framework.EngineDecorator;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import lombok.Getter;

public abstract class IrisEngineDecorator extends EngineAssignedComponent implements EngineDecorator {
    @Getter
    private final DecorationPart part;
    protected final BiomeDecorators decorators;

    public IrisEngineDecorator(Engine engine, String name, DecorationPart part) {
        super(engine, name + " Decorator");
        this.part = part;
        this.decorators = BiomeDecorators.of(engine);
    }

    @BlockCoordinates
    protected RNG getRNG(int x, int z) {
        long seed = DecoratorCore.partSeed(getSeed(), part);
        long modX = 29356788L ^ (part.ordinal() + 6);
        long modZ = 10439677L ^ (part.ordinal() + 1);
        return new RNG(x * modX + z * modZ + seed);
    }
}
