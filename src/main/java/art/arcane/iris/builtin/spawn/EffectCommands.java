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

package art.arcane.iris.builtin.spawn;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

@Snippet("iris/spawn/command-registry")
@Desc("Represents a casting location for a command")
public final class EffectCommands {
    @Required
    @MinItems(1)
    @Desc("Run commands, at the exact location of the player")
    private List<SpawnCommand> rawCommands = List.of();

    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt x, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetX = 0;

    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt y, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetY = 0;

    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt z, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetZ = 0;

    @Desc("Randomize the altX from -altX to altX")
    private boolean commandRandomAltX = true;

    @Desc("Randomize the altY from -altY to altY")
    private boolean commandRandomAltY = false;

    @Desc("Randomize the altZ from -altZ to altZ")
    private boolean commandRandomAltZ = true;

    @Desc("Randomize location for all separate commands (true), or run all on the same location (false)")
    private boolean commandAllRandomLocations = true;

    public EffectCommands() {
    }

    public List<SpawnCommand> rawCommands() {
        return rawCommands == null ? List.of() : Collections.unmodifiableList(rawCommands);
    }

    public double commandOffsetX() {
        return commandOffsetX;
    }

    public double commandOffsetY() {
        return commandOffsetY;
    }

    public double commandOffsetZ() {
        return commandOffsetZ;
    }

    public boolean commandRandomAltX() {
        return commandRandomAltX;
    }

    public boolean commandRandomAltY() {
        return commandRandomAltY;
    }

    public boolean commandRandomAltZ() {
        return commandRandomAltZ;
    }

    public boolean commandAllRandomLocations() {
        return commandAllRandomLocations;
    }
}
