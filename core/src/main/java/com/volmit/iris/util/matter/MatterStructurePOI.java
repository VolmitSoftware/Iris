/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.matter;

import lombok.Data;

import java.util.Map;

@Data
public class MatterStructurePOI {

    public static final MatterStructurePOI BURIED_TREASURE = new MatterStructurePOI("buried_treasure");

    private static final MatterStructurePOI UNKNOWN = new MatterStructurePOI("unknown");
    private static final Map<String, MatterStructurePOI> VALUES = Map.of(
            "buried_treasure", BURIED_TREASURE
    );

    private final String type;

    public static MatterStructurePOI get(String id) {
        MatterStructurePOI poi = VALUES.get(id);
        return poi != null ? poi : new MatterStructurePOI(id);
    }
}
