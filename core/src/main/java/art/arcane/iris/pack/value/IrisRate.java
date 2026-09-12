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

package art.arcane.iris.pack.value;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Snippet("rate")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Description("Represents a count of something per time duration")
public class IrisRate {
    @Description("The amount of things. Leave 0 for infinite (meaning always spawn whenever)")
    private int amount = 0;

    @Description("The time interval. Leave blank for infinite 0 (meaning always spawn all the time)")
    private IrisDuration per = new IrisDuration();

    public String toString() {
        return Form.f(amount) + "/" + per;
    }

    public long getInterval() {
        long t = per.toMilliseconds() / (amount == 0 ? 1 : amount);
        return Math.abs(t <= 0 ? 1 : t);
    }

    public ChronoLatch toChronoLatch() {
        return new ChronoLatch(getInterval());
    }

    public boolean isInfinite() {
        return per.toMilliseconds() == 0;
    }
}
