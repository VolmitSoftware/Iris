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

package art.arcane.iris.util.nbt.common.mca;

import art.arcane.volmlib.util.nbt.mca.MCAChunkSupport;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.iris.Iris;
import art.arcane.iris.core.nms.INMS;

import static org.bukkit.Bukkit.getServer;

public class Chunk extends MCAChunkSupport<Section> {
    public static final int DEFAULT_DATA_VERSION = 2730;

    private static final Config<Section> CONFIG = new Config<>(
            DEFAULT_DATA_VERSION,
            () -> getServer().getWorlds().get(0).getMinHeight(),
            () -> getServer().getWorlds().get(0).getMaxHeight(),
            (min, max) -> INMS.get().newBiomeContainer(min, max),
            (min, max, data) -> INMS.get().newBiomeContainer(min, max, data),
            (sectionRoot, dataVersion, loadFlags) -> new Section(sectionRoot, dataVersion, loadFlags),
            Section::newSection,
            () -> "Iris Headless " + Iris.instance.getDescription().getVersion(),
            () -> "Iris " + Iris.instance.getDescription().getVersion()
    );

    Chunk(int lastMCAUpdate) {
        super(lastMCAUpdate, CONFIG);
    }

    public Chunk(CompoundTag data) {
        super(data, CONFIG);
    }

    public static Chunk newChunk() {
        Chunk c = new Chunk(0);
        c.initializeNewChunk();
        return c;
    }

    public static void injectIrisData(Chunk c) {
        c.injectNativeData("Iris");
    }
}
