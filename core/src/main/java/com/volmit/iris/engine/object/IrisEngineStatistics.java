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

import com.volmit.iris.Iris;
import lombok.Data;
import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class IrisEngineStatistics {
    private int totalHotloads = 0;
    private int chunksGenerated = 0;
    private int IrisCreationVersion = 0;
    private int MinecraftVersion = 0;

    public void generatedChunk() {
        chunksGenerated++;
    }

    public void setVersion(int i) {
        IrisCreationVersion = i;
    }

    public void setMCVersion(int i) {
        MinecraftVersion = i;
    }
    public int getMCVersion() {
        return MinecraftVersion;
    }
    public int getVersion() {
        return MinecraftVersion;
    }




    public void hotloaded() {
        totalHotloads++;
    }
}
