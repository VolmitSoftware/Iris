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

package art.arcane.iris.world.entity;

import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Description("Represents a marker")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisMarker extends IrisRegistrant {
    @Description("A list of spawners to add to anywhere this marker is.")
    @RegistryListResource(IrisSpawner.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> spawners = new KList<>();

    @Description("Remove this marker when the block it's assigned to is changed.")
    private boolean removeOnChange = true;

    @Description("If true, markers will only be placed here if there is 2 air blocks above it.")
    private boolean emptyAbove = true;

    @Description("If this marker is used, what is the chance it removes itself. For example 25% (0.25) would mean that on average 4 uses will remove a specific marker. Set this below 0 (-1) to never exhaust & set this to 1 or higher to always exhaust on first use.")
    private double exhaustionChance = 0;

    public boolean shouldExhaust() {
        return exhaustionChance > RNG.r.nextDouble();
    }

    @Override
    public String getFolderName() {
        return "markers";
    }

    @Override
    public String getTypeName() {
        return "Marker";
    }
}
