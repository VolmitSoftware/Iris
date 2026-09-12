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

import art.arcane.iris.world.IrisTimeBlock;
import art.arcane.iris.world.IrisWeather;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("command")
@Accessors(chain = true)
@NoArgsConstructor
@Description("Represents a set of Iris commands")
@Data
public class IrisCommand {

    @Required
    @ArrayType(min = 1, type = String.class)
    @Description("List of commands. Iris replaces {x} {y} and {z} with the location of the entity spawn")
    private KList<String> commands = new KList<>();

    @Description("The delay for running the command. Instant by default")
    private long delay = 0;

    @Description("If this should be repeated (indefinitely, cannot be cancelled). This does not persist with server-restarts, so it only repeats when the chunk is generated.")
    private boolean repeat = false;

    @Description("The delay between repeats, in server ticks (by default 100, so 5 seconds)")
    private long repeatDelay = 100;

    @Description("The block of 24 hour time in which the command should execute.")
    private IrisTimeBlock timeBlock = new IrisTimeBlock();

    @Description("The weather that is required for the command to execute.")
    private IrisWeather weather = IrisWeather.ANY;

    public boolean isValid(PlatformWorld world) {
        return timeBlock.isWithin(world) && weather.is(world);
    }

    public void run(PlatformWorld world, int x, int y, int z) {
        if (!isValid(world)) {
            return;
        }

        for (String command : commands) {
            command = (command.startsWith("/") ? command.replaceFirst("/", "") : command)
                    .replaceAll("\\Q{x}\\E", String.valueOf(x))
                    .replaceAll("\\Q{y}\\E", String.valueOf(y))
                    .replaceAll("\\Q{z}\\E", String.valueOf(z));
            final String finalCommand = command;
            int safeDelay = (int) Math.max(0, Math.min(Integer.MAX_VALUE, delay));
            if (repeat) {
                int safeRepeatDelay = (int) Math.max(1, Math.min(Integer.MAX_VALUE, repeatDelay));
                J.s(() -> J.sr(() -> IrisPlatforms.get().dispatchConsoleCommand(finalCommand), safeRepeatDelay), safeDelay);
            } else {
                J.s(() -> IrisPlatforms.get().dispatchConsoleCommand(finalCommand), safeDelay);
            }
        }
    }
}
