/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.nms.datapack;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.datapack.v1192.DataFixerV1192;
import com.volmit.iris.core.nms.datapack.v1206.DataFixerV1206;
import com.volmit.iris.util.collection.KMap;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.function.Supplier;

//https://minecraft.wiki/w/Pack_format
@Getter
public enum DataVersion {
    V1192("1.19.2", 10, DataFixerV1192::new),
    V1205("1.20.6", 41, DataFixerV1206::new);
    private static final KMap<DataVersion, IDataFixer> cache = new KMap<>();
    @Getter(AccessLevel.NONE)
    private final Supplier<IDataFixer> constructor;
    private final String version;
    private final int packFormat;

    DataVersion(String version, int packFormat, Supplier<IDataFixer> constructor) {
        this.constructor = constructor;
        this.packFormat = packFormat;
        this.version = version;
    }

    public static IDataFixer getDefault() {
        return INMS.get().getDataVersion().get();
    }

    public static DataVersion getLatest() {
        return values()[values().length - 1];
    }

    public IDataFixer get() {
        return cache.computeIfAbsent(this, k -> constructor.get());
    }
}
