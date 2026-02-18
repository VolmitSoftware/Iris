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

package art.arcane.iris.util.project.matter;

import art.arcane.iris.Iris;
import art.arcane.iris.engine.object.IrisObject;

import art.arcane.iris.util.project.matter.slices.EntityMatter;
import art.arcane.iris.util.project.matter.slices.IdentifierMatter;
import art.arcane.iris.util.project.matter.slices.SpawnerMatter;
import art.arcane.iris.util.project.matter.slices.TileMatter;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.io.File;

public final class IrisMatterSupport {
    private static boolean registered;

    private IrisMatterSupport() {
    }

    public static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }

        IrisMatter.registerSliceType(new EntityMatter());
        IrisMatter.registerSliceType(new IdentifierMatter());
        IrisMatter.registerSliceType(new SpawnerMatter());
        IrisMatter.registerSliceType(new TileMatter());
        registered = true;
    }

    public static Matter from(IrisObject object) {
        ensureRegistered();
        object.clean();
        object.shrinkwrap();
        BlockVector min = new BlockVector();
        Matter matter = new IrisMatter(Math.max(object.getW(), 1) + 1, Math.max(object.getH(), 1) + 1, Math.max(object.getD(), 1) + 1);

        for (BlockVector i : object.getBlocks().keys()) {
            min.setX(Math.min(min.getX(), i.getX()));
            min.setY(Math.min(min.getY(), i.getY()));
            min.setZ(Math.min(min.getZ(), i.getZ()));
        }

        for (BlockVector i : object.getBlocks().keys()) {
            matter.slice(BlockData.class).set(i.getBlockX() - min.getBlockX(), i.getBlockY() - min.getBlockY(), i.getBlockZ() - min.getBlockZ(), object.getBlocks().get(i));
        }

        return matter;
    }

    public static long convert(File folder) {
        if (folder.isDirectory()) {
            long total = 0;
            File[] files = folder.listFiles();
            if (files == null) {
                return total;
            }

            for (File file : files) {
                total += convert(file);
            }

            return total;
        }

        IrisObject object = new IrisObject(1, 1, 1);
        try {
            long oldSize = folder.length();
            object.read(folder);
            from(object).write(folder);
            Iris.info("Converted " + folder.getPath() + " Saved " + (oldSize - folder.length()));
        } catch (Throwable e) {
            Iris.error("Failed to convert " + folder.getPath());
            e.printStackTrace();
        }

        return 0;
    }
}
