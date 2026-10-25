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

package art.arcane.iris.studio.view;

/**
 * Where the player who opened a window stands.
 */
public record OpenerPosition(Status status, double x, double y, double z) {
    public static final OpenerPosition OFFLINE = new OpenerPosition(Status.OFFLINE, 0D, 0D, 0D);
    public static final OpenerPosition IN_OTHER_WORLD = new OpenerPosition(Status.IN_OTHER_WORLD, 0D, 0D, 0D);

    public static OpenerPosition inWorld(double x, double y, double z) {
        return new OpenerPosition(Status.IN_WORLD, x, y, z);
    }

    public enum Status {
        OFFLINE,
        IN_OTHER_WORLD,
        IN_WORLD
    }
}
