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

package art.arcane.iris.generation.runtime;

import lombok.Data;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

@Data
public class IrisEngineStatistics {
    private static final AtomicIntegerFieldUpdater<IrisEngineStatistics> TOTAL_HOTLOADS =
            AtomicIntegerFieldUpdater.newUpdater(IrisEngineStatistics.class, "totalHotloads");
    private static final AtomicIntegerFieldUpdater<IrisEngineStatistics> CHUNKS_GENERATED =
            AtomicIntegerFieldUpdater.newUpdater(IrisEngineStatistics.class, "chunksGenerated");

    private volatile int totalHotloads = 0;
    private volatile int chunksGenerated = 0;
    private volatile int IrisToUpgradedVersion = 0;
    private volatile int IrisCreationVersion = 0;
    private volatile int MinecraftVersion = 0;

    public void generatedChunk() {
        CHUNKS_GENERATED.incrementAndGet(this);
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
        TOTAL_HOTLOADS.incrementAndGet(this);
    }
}
