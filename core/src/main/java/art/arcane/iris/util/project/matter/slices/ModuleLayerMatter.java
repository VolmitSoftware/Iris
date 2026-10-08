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

package art.arcane.iris.util.project.matter.slices;

import art.arcane.iris.core.modules.ModuleLayerValues;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Stores the values of every module mantle layer at a block in one slice. */
@Sliced
public class ModuleLayerMatter extends RawMatter<ModuleLayerValues> {
    public ModuleLayerMatter() {
        this(1, 1, 1);
    }

    public ModuleLayerMatter(int width, int height, int depth) {
        super(width, height, depth, ModuleLayerValues.class);
    }

    @Override
    public Palette<ModuleLayerValues> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(ModuleLayerValues values, DataOutputStream output) throws IOException {
        values.write(output);
    }

    @Override
    public ModuleLayerValues readNode(DataInputStream input) throws IOException {
        return ModuleLayerValues.read(input);
    }
}
