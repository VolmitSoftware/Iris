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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.data.palette.GlobalPalette;
import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.MatterUpdate;
import com.volmit.iris.util.matter.Sliced;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class UpdateMatter extends RawMatter<MatterUpdate> {
    public static final MatterUpdate ON = new MatterUpdate(true);
    public static final MatterUpdate OFF = new MatterUpdate(false);
    private static final Palette<MatterUpdate> GLOBAL = new GlobalPalette<>(OFF, ON);

    public UpdateMatter() {
        this(1, 1, 1);
    }

    public UpdateMatter(int width, int height, int depth) {
        super(width, height, depth, MatterUpdate.class);
    }

    @Override
    public Palette<MatterUpdate> getGlobalPalette() {
        return GLOBAL;
    }

    @Override
    public void writeNode(MatterUpdate b, DataOutputStream dos) throws IOException {
        dos.writeBoolean(b.isUpdate());
    }

    @Override
    public MatterUpdate readNode(DataInputStream din) throws IOException {
        return din.readBoolean() ? ON : OFF;
    }
}
