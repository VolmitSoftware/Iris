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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;

import java.util.function.Function;

@Desc("Represents a value from the engine")
public enum IrisEngineValueType {
    @Desc("Represents actual height of the engine")
    ENGINE_HEIGHT((f) -> Double.valueOf(f.getHeight())),

    @Desc("Represents virtual bottom of the engine in the compound. If this engine is on top of another engine, it's min height would be at the maxHeight of the previous engine + 1")
    ENGINE_MIN_HEIGHT((f) -> Double.valueOf(f.getMinHeight())),

    @Desc("Represents virtual top of the engine in the compound. If this engine is below another engine, it's max height would be at the minHeight of the next engine - 1")
    ENGINE_MAX_HEIGHT((f) -> Double.valueOf(f.getWorld().maxHeight())),

    @Desc("The fluid height defined in the dimension file")
    FLUID_HEIGHT((f) -> Double.valueOf(f.getComplex().getFluidHeight())),
    ;

    private final Function<Engine, Double> getter;

    IrisEngineValueType(Function<Engine, Double> getter) {
        this.getter = getter;
    }

    public Double get(Engine engine) {
        return getter.apply(engine);
    }
}
