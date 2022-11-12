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

package com.volmit.iris.util.io;

import java.io.File;

public class FileWatcher {
    protected final File file;
    private long lastModified;
    private long size;

    public FileWatcher(File file) {
        this.file = file;
        readProperties();
    }

    protected void readProperties() {
        boolean exists = file.exists();
        lastModified = exists ? file.lastModified() : -1;
        size = exists ? file.isDirectory() ? -2 : file.length() : -1;
    }

    public boolean checkModified() {
        long m = lastModified;
        long g = size;
        boolean mod = false;
        readProperties();

        if (lastModified != m || g != size) {
            mod = true;
        }

        return mod;
    }
}
