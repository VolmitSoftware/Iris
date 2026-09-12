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

package art.arcane.iris.world.storage.matter;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RegistryMatter<T extends IrisRegistrant> extends RawMatter<T> {
    public RegistryMatter(int width, int height, int depth, Class<T> c, T e) {
        super(width, height, depth, c);
    }

    @Override
    public Palette<T> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(T b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getLoadKey());
    }

    @Override
    public T readNode(DataInputStream din) throws IOException {
        String key = din.readUTF();
        IrisData data = IrisMatterContext.require();
        ResourceLoader<? extends IrisRegistrant> loader = data.getLoaders().get(getType());
        if (loader == null) {
            throw new IOException("No Iris registry loader is available for matter type " + getType().getName() + " while reading key " + key + ".");
        }
        return (T) loader.load(key);
    }
}
