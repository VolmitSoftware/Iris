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

package com.volmit.iris.util.nbt.tag;

public final class EndTag extends Tag<Void> {

    public static final byte ID = 0;
    public static final EndTag INSTANCE = new EndTag();

    private EndTag() {
        super(null);
    }

    @Override
    public byte getID() {
        return ID;
    }

    @Override
    protected Void checkValue(Void value) {
        return value;
    }

    @Override
    public String valueToString(int maxDepth) {
        return "\"end\"";
    }

    @Override
    public EndTag clone() {
        return INSTANCE;
    }
}
