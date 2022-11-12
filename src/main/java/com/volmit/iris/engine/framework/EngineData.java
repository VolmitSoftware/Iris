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

package com.volmit.iris.engine.framework;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.util.io.IO;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Data
public class EngineData {
    private String dimension;
    private String lastVersion;
    private List<IrisPosition> strongholdPositions;

    public static EngineData load(File f) {
        try {
            f.getParentFile().mkdirs();
            return new Gson().fromJson(IO.readAll(f), EngineData.class);
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        return new EngineData();
    }

    public void save(File f) {
        try {
            f.getParentFile().mkdirs();
            IO.writeAll(f, new Gson().toJson(this));
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }
}
