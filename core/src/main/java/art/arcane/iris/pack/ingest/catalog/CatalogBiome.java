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


package art.arcane.iris.pack.ingest.catalog;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Objects;

public record CatalogBiome(
        String key,
        float temperature,
        float downfall,
        boolean hasPrecipitation,
        List<String> tags,
        List<List<String>> featureSteps,
        List<CatalogSpawn> spawns,
        JsonElement definition) {
    public CatalogBiome {
        Objects.requireNonNull(key, "key");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        featureSteps = Objects.requireNonNull(featureSteps, "featureSteps").stream().map(List::copyOf).toList();
        spawns = List.copyOf(Objects.requireNonNull(spawns, "spawns"));
    }
}
