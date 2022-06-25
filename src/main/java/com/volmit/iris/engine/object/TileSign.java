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

package com.volmit.iris.engine.object;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import lombok.Data;
import org.bukkit.DyeColor;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data
public class TileSign implements TileData<Sign> {
    public static final int id = 0;
    private String line1;
    private String line2;
    private String line3;
    private String line4;
    private DyeColor dyeColor;

    @Override
    public String getTileId() {
        return "minecraft:sign";
    }

    @Override
    public boolean isApplicable(BlockData data) {
        return data instanceof org.bukkit.block.data.type.Sign || data instanceof WallSign;
    }

    @Override
    public void toBukkit(Sign t) {
        t.setLine(0, line1);
        t.setLine(1, line2);
        t.setLine(2, line3);
        t.setLine(3, line4);
        t.setColor(dyeColor);
    }

    @Override
    public void fromBukkit(Sign sign) {
        line1 = sign.getLine(0);
        line2 = sign.getLine(1);
        line3 = sign.getLine(2);
        line4 = sign.getLine(3);
        dyeColor = sign.getColor();
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public TileSign clone() {
        TileSign ts = new TileSign();
        ts.setDyeColor(getDyeColor());
        ts.setLine1(getLine1());
        ts.setLine2(getLine2());
        ts.setLine3(getLine3());
        ts.setLine4(getLine4());
        return ts;
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        out.writeShort(id);
        out.writeUTF(line1);
        out.writeUTF(line2);
        out.writeUTF(line3);
        out.writeUTF(line4);
        out.writeByte(dyeColor.ordinal());
    }

    @Override
    public void fromBinary(DataInputStream in) throws IOException {
        line1 = in.readUTF();
        line2 = in.readUTF();
        line3 = in.readUTF();
        line4 = in.readUTF();
        dyeColor = DyeColor.values()[in.readByte()];
    }

    @Override
    public CompoundTag toNBT(CompoundTag tag) {
        tag.putString("Text1", line1);
        tag.putString("Text2", line2);
        tag.putString("Text3", line3);
        tag.putString("Text4", line4);
        tag.putString("Color", dyeColor.name().toLowerCase());
        return tag;
    }
}
