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
import com.volmit.config.Snippet;

@Snippet("rate")
@Desc("Represents a count of something per time duration")
public final class SpawnRate {
    @Desc("The amount of things. Leave 0 for infinite (meaning always spawn whenever)")
    private int amount = 0;

    @Desc("The time interval. Leave blank for infinite 0 (meaning always spawn all the time)")
    private SpawnDuration per = new SpawnDuration();

    public SpawnRate() {
    }

    /** The milliseconds between two things: the interval divided by the amount, at least 1. */
    public long interval() {
        long t = duration().toMilliseconds() / (amount == 0 ? 1 : amount);
        return Math.abs(t <= 0 ? 1 : t);
    }

    /** Whether there is no limit: no interval was given. */
    public boolean isInfinite() {
        return duration().toMilliseconds() == 0;
    }

    private SpawnDuration duration() {
        return per == null ? new SpawnDuration() : per;
    }
}
