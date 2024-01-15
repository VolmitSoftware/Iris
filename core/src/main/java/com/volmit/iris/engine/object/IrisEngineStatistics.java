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

import lombok.Data;

@Data
public class IrisEngineStatistics {
    private int totalHotloads = 0;
    private int chunksGenerated = 0;
    private int IrisToUpgradedVersion = 0;
    private int IrisCreationVersion = 0;
    private int MinecraftVersion = 0;

    public void generatedChunk() {
        chunksGenerated++;
    }

    public void setUpgradedVersion(int i) {
        IrisToUpgradedVersion = i;
    }
    public int getUpgradedVersion() {
        return IrisToUpgradedVersion;
    }
    public void setVersion(int i) {
        IrisCreationVersion = i;
    }

    public int getVersion() {
        return IrisCreationVersion;
    }

    public void setMCVersion(int i) {
        MinecraftVersion = i;
    }

    public int getMCVersion() {
        return MinecraftVersion;
    }

    public void hotloaded() {
        totalHotloads++;
    }
}
