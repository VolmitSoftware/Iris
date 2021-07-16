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

package com.volmit.iris.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.IrisObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SKConversion {
    public static void convertSchematic(File in, File out) {
        ClipboardFormat format = ClipboardFormats.findByFile(in);
        try (ClipboardReader reader = format.getReader(new FileInputStream(in))) {
            Clipboard clipboard = reader.read();
            BlockVector3 size = clipboard.getMaximumPoint().subtract(clipboard.getMinimumPoint());
            IrisObject o = new IrisObject(size.getBlockX() + 1, size.getBlockY() + 1, size.getBlockZ() + 1);

            for (int i = clipboard.getMinimumPoint().getBlockX(); i <= clipboard.getMaximumPoint().getBlockX(); i++) {
                for (int j = clipboard.getMinimumPoint().getBlockY(); j <= clipboard.getMaximumPoint().getBlockY(); j++) {
                    for (int k = clipboard.getMinimumPoint().getBlockZ(); k <= clipboard.getMaximumPoint().getBlockZ(); k++) {
                        o.setUnsigned(i - clipboard.getMinimumPoint().getBlockX(), j - clipboard.getMinimumPoint().getBlockY(), k - clipboard.getMinimumPoint().getBlockZ(), BukkitAdapter.adapt(clipboard.getFullBlock(BlockVector3.at(i, j, k))));
                    }
                }
            }

            o.write(out);
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }
}
