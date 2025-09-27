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
import com.volmit.iris.engine.framework.EngineMode;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("dimension-mode")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a dimensional mode")
@Data
public class IrisDimensionMode {
    @Desc("The dimension type")
    private IrisDimensionModeType type = IrisDimensionModeType.OVERWORLD;

    @RegistryListResource(IrisScript.class)
    @Desc("The script to create the dimension mode instead of using provided types\nFile extension: .engine.kts")
    private String script;

    public EngineMode create(Engine engine) {
        if (script == null) {
            return type.create(engine);
        }
        Object result = engine.getExecution().evaluate(script);
        if (result instanceof EngineMode) {
            return (EngineMode) result;
        }

        throw new IllegalStateException("The script '" + script + "' did not return an engine mode!");
    }
}
