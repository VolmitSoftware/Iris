/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.hunk.io;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.oldnbt.CompoundTag;
import com.volmit.iris.util.oldnbt.NBTInputStream;
import com.volmit.iris.util.oldnbt.NBTOutputStream;
import com.volmit.iris.util.oldnbt.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

@SuppressWarnings("SynchronizeOnNonFinalField")
public class HunkRegion {
    private final File folder;
    private CompoundTag compound;
    private final int x;
    private final int z;

    public HunkRegion(File folder, int x, int z, CompoundTag compound) {
        this.compound = fix(compound);
        this.folder = folder;
        this.x = x;
        this.z = z;
        folder.mkdirs();
    }

    public HunkRegion(File folder, int x, int z) {
        this(folder, x, z, new CompoundTag(x + "." + z, new KMap<>()));
        File f = getFile();

        if (f.exists()) {
            try {
                NBTInputStream in = new NBTInputStream(new FileInputStream(f));
                compound = fix((CompoundTag) in.readTag());
                in.close();
            } catch (Throwable e) {
                Iris.reportError(e);

            }
        }
    }

    public CompoundTag getCompound() {
        return compound;
    }

    private CompoundTag fix(CompoundTag readTag) {
        Map<String, Tag> v = readTag.getValue();

        if (!(v instanceof KMap)) {
            return new CompoundTag(readTag.getName(), new KMap<>(v));
        }

        return readTag;
    }

    public File getFile() {
        return new File(folder, x + "." + z + ".dat");
    }

    public void save() throws IOException {
        synchronized (compound) {
            File f = getFile();
            FileOutputStream fos = new FileOutputStream(f);
            NBTOutputStream out = new NBTOutputStream(fos);
            out.writeTag(compound);
            out.close();
        }
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

}
