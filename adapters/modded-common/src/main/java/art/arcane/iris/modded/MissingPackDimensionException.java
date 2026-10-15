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

package art.arcane.iris.modded;

public final class MissingPackDimensionException extends IllegalStateException {
    private final String pack;
    private final String dimensionKey;

    public MissingPackDimensionException(String pack, String dimensionKey) {
        super("Pack '" + pack + "' does not contain dimensions/" + dimensionKey + ".json");
        this.pack = pack;
        this.dimensionKey = dimensionKey;
    }

    public String pack() {
        return pack;
    }

    public String dimensionKey() {
        return dimensionKey;
    }
}
