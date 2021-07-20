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

package com.volmit.iris.core.pregenerator.turbo;

import com.google.gson.Gson;
import com.volmit.iris.core.pregenerator.turbo.command.TurboCommand;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TurboCommander {
    private static final Gson gson = new Gson();

    public static TurboCommand read(DataInputStream in) throws IOException, ClassNotFoundException {
        String clazz = in.readUTF();
        return (TurboCommand) gson.fromJson(in.readUTF(), Class.forName(clazz));
    }

    public static void write(TurboCommand c, DataOutputStream out) throws IOException {
        out.writeUTF(c.getClass().getCanonicalName());
        out.writeUTF(gson.toJson(c));
    }
}
