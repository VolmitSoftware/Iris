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

package art.arcane.iris.engine.object.marker;

import art.arcane.iris.engine.object.world.IrisTimeBlock;
import art.arcane.iris.engine.object.world.IrisWeather;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.util.common.scheduling.ConsoleCommands;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("command")
@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents a set of Iris commands")
@Data
public class IrisCommand {

    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("List of commands. Iris replaces {x} {y} and {z} with the location of the entity spawn")
    private KList<String> commands = new KList<>();

    @Desc("The delay for running the command. Instant by default")
    private long delay = 0;

    @Desc("If this should be repeated (indefinitely, cannot be cancelled). This does not persist with server-restarts, so it only repeats when the chunk is generated.")
    private boolean repeat = false;

    @Desc("The delay between repeats, in server ticks (by default 100, so 5 seconds)")
    private long repeatDelay = 100;

    @Desc("The block of 24 hour time in which the command should execute.")
    private IrisTimeBlock timeBlock = new IrisTimeBlock();

    @Desc("The weather that is required for the command to execute.")
    private IrisWeather weather = IrisWeather.ANY;

    public boolean isValid(PlatformWorld world) {
        return timeBlock.isWithin(world) && weather.is(world);
    }

    public void run(PlatformWorld world, int x, int y, int z) {
        if (!isValid(world)) {
            return;
        }

        ConsoleCommands.run(commands, delay, repeat, repeatDelay, x, y, z);
    }
}
