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

package art.arcane.iris.pack.ingest;

import art.arcane.iris.pack.ingest.catalog.CatalogBiome;

import java.util.List;

record IngestClimate(double temperature, double downfall) {
    static IngestClimate of(CatalogBiome biome) {
        return new IngestClimate(biome.temperature(), biome.downfall());
    }

    static IngestClimate average(List<IngestClimate> climates) {
        if (climates.isEmpty()) {
            return null;
        }
        double temperature = 0D;
        double downfall = 0D;
        for (IngestClimate climate : climates) {
            temperature += climate.temperature();
            downfall += climate.downfall();
        }
        return new IngestClimate(temperature / climates.size(), downfall / climates.size());
    }

    double distance(IngestClimate other) {
        if (other == null) {
            return Double.POSITIVE_INFINITY;
        }
        double temperatureDelta = temperature - other.temperature();
        double downfallDelta = downfall - other.downfall();
        return temperatureDelta * temperatureDelta + downfallDelta * downfallDelta;
    }
}
