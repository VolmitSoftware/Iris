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
import com.volmit.config.MinItems;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

@Snippet("iris/spawn/command")
@Desc("Represents a set of Iris commands")
public final class SpawnCommand {
    @Required
    @MinItems(1)
    @Desc("List of commands. Iris replaces {x} {y} and {z} with the location of the entity spawn")
    private List<String> commands = List.of();

    @Desc("The delay for running the command. Instant by default")
    private long delay = 0;

    @Desc("If this should be repeated (indefinitely, cannot be cancelled). This does not persist with server-restarts, so it only repeats when the chunk is generated.")
    private boolean repeat = false;

    @Desc("The delay between repeats, in server ticks (by default 100, so 5 seconds)")
    private long repeatDelay = 100;

    @Desc("The block of 24 hour time in which the command should execute.")
    private SpawnTimeBlock timeBlock = new SpawnTimeBlock();

    @Desc("The weather that is required for the command to execute.")
    private SpawnWeather weather = SpawnWeather.ANY;

    public SpawnCommand() {
    }

    public List<String> commands() {
        return commands == null ? List.of() : Collections.unmodifiableList(commands);
    }

    public long delay() {
        return delay;
    }

    public boolean repeat() {
        return repeat;
    }

    public long repeatDelay() {
        return repeatDelay;
    }

    public SpawnTimeBlock timeBlock() {
        return timeBlock == null ? new SpawnTimeBlock() : timeBlock;
    }

    public SpawnWeather weather() {
        return weather == null ? SpawnWeather.ANY : weather;
    }
}
