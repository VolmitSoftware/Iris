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

package art.arcane.iris.generation.terrain;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("slope-clip")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Limits placement to a minimum and maximum terrain slope")
@Data
public class IrisSlopeClip {
    @MinNumber(0)
    @MaxNumber(1024)
    @Description("The minimum slope for placement")
    private double minimumSlope = 0;

    @MinNumber(0)
    @MaxNumber(1024)
    @Description("The maximum slope for placement")
    private double maximumSlope = 10;

    public boolean isDefault() {
        return minimumSlope <= 0 && maximumSlope >= 10;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isValid(double slope) {
        if (isDefault()) {
            return true;
        }

        return !(minimumSlope > slope) && !(maximumSlope < slope);
    }
}
