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

enum BiomeRole {
    LAND("landBiomes", "land", 5),
    SEA("seaBiomes", "sea", 3),
    SHORE("shoreBiomes", "shore", 3),
    CAVE("caveBiomes", "cave", 3);

    private final String poolField;
    private final String label;
    private final int templateCap;

    BiomeRole(String poolField, String label, int templateCap) {
        this.poolField = poolField;
        this.label = label;
        this.templateCap = templateCap;
    }

    String poolField() {
        return poolField;
    }

    String label() {
        return label;
    }

    int templateCap() {
        return templateCap;
    }
}
