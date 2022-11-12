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

import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Snippet("command-registry")
@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents a casting location for a command")
@Data
public class IrisCommandRegistry {
    @ArrayType(min = 1, type = IrisCommand.class)
    @Desc("Run commands, at the exact location of the player")
    private KList<IrisCommand> rawCommands = new KList<>();
    @DependsOn({"rawCommands"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt x, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetX = 0;
    @DependsOn({"rawCommands"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt y, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetY = 0;
    @DependsOn({"rawCommands"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt z, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetZ = 0;
    @DependsOn({"rawCommands"})
    @Desc("Randomize the altX from -altX to altX")
    private boolean commandRandomAltX = true;
    @DependsOn({"rawCommands"})
    @Desc("Randomize the altY from -altY to altY")
    private boolean commandRandomAltY = false;
    @DependsOn({"rawCommands"})
    @Desc("Randomize the altZ from -altZ to altZ")
    private boolean commandRandomAltZ = true;
    @DependsOn({"rawCommands"})
    @Desc("Randomize location for all separate commands (true), or run all on the same location (false)")
    private boolean commandAllRandomLocations = true;

    public void run(Player p) {
        if (rawCommands.isNotEmpty()) {
            Location part = p.getLocation().clone().add(
                    commandRandomAltX ? RNG.r.d(-commandOffsetX, commandOffsetX) : commandOffsetX,
                    commandRandomAltY ? RNG.r.d(-commandOffsetY, commandOffsetY) : commandOffsetY,
                    commandRandomAltZ ? RNG.r.d(-commandOffsetZ, commandOffsetZ) : commandOffsetZ);
            for (IrisCommand rawCommand : rawCommands) {
                rawCommand.run(part);
                if (commandAllRandomLocations) {
                    part = p.getLocation().clone().add(
                            commandRandomAltX ? RNG.r.d(-commandOffsetX, commandOffsetX) : commandOffsetX,
                            commandRandomAltY ? RNG.r.d(-commandOffsetY, commandOffsetY) : commandOffsetY,
                            commandRandomAltZ ? RNG.r.d(-commandOffsetZ, commandOffsetZ) : commandOffsetZ);
                }
            }
        }
    }
}
