/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.engine.object;

/**
 * A resource of Iris's pack object model read from one file of a pack. Iris sets its key, the file's path below the
 * folder without {@code .json}, when it reads the file; the folder and type name are what studio and load reports show.
 */
public abstract class KeyedResource {
    private transient String loadKey;

    public String getLoadKey() {
        return loadKey;
    }

    public void setLoadKey(String loadKey) {
        this.loadKey = loadKey;
    }

    /** The folder below the pack root that holds resources of this type. */
    public abstract String getFolderName();

    /** The name studio and load reports give a resource of this type, such as {@code Structure}. */
    public abstract String getTypeName();
}
