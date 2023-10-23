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

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.util.math.RollingSequence;
import lombok.Data;

@Data
public class EngineAssignedComponent implements EngineComponent {
    private final Engine engine;
    private final RollingSequence metrics;
    private final String name;

    public EngineAssignedComponent(Engine engine, String name) {
        Iris.debug("Engine: " + engine.getCacheID() + " Starting " + name);
        this.engine = engine;
        this.metrics = new RollingSequence(16);
        this.name = name;
    }
}
