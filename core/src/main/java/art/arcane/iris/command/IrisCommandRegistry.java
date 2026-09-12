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

package art.arcane.iris.command;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.DependsOn;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.platform.bukkit.BukkitWorld;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Snippet("command-registry")
@Accessors(chain = true)
@NoArgsConstructor
@Description("Represents a casting location for a command")
@Data
public class IrisCommandRegistry {
    @Required
    @ArrayType(min = 1, type = IrisCommand.class)
    @Description("Run commands, at the exact location of the player")
    private KList<IrisCommand> rawCommands = new KList<>();
    @DependsOn({"rawCommands"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Description("The alt x, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetX = 0;
    @DependsOn({"rawCommands"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Description("The alt y, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetY = 0;
    @DependsOn({"rawCommands"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Description("The alt z, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double commandOffsetZ = 0;
    @DependsOn({"rawCommands"})
    @Description("Randomize the altX from -altX to altX")
    private boolean commandRandomAltX = true;
    @DependsOn({"rawCommands"})
    @Description("Randomize the altY from -altY to altY")
    private boolean commandRandomAltY = false;
    @DependsOn({"rawCommands"})
    @Description("Randomize the altZ from -altZ to altZ")
    private boolean commandRandomAltZ = true;
    @DependsOn({"rawCommands"})
    @Description("Randomize location for all separate commands (true), or run all on the same location (false)")
    private boolean commandAllRandomLocations = true;

    public void run(Player p) {
        if (rawCommands.isNotEmpty()) {
            PlatformWorld world = new BukkitWorld(p.getWorld());
            Location part = p.getLocation().clone().add(
                    commandRandomAltX ? RNG.r.d(-commandOffsetX, commandOffsetX) : commandOffsetX,
                    commandRandomAltY ? RNG.r.d(-commandOffsetY, commandOffsetY) : commandOffsetY,
                    commandRandomAltZ ? RNG.r.d(-commandOffsetZ, commandOffsetZ) : commandOffsetZ);
            for (IrisCommand rawCommand : rawCommands) {
                rawCommand.run(world, part.getBlockX(), part.getBlockY(), part.getBlockZ());
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
