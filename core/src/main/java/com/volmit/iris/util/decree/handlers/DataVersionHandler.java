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

package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.core.nms.datapack.DataVersion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;

public class DataVersionHandler implements DecreeParameterHandler<DataVersion> {
    @Override
    public KList<DataVersion> getPossibilities() {
        return new KList<>(DataVersion.values());
    }

    @Override
    public String toString(DataVersion version) {
        return version.getVersion();
    }

    @Override
    public DataVersion parse(String in, boolean force) throws DecreeParsingException {
        if (in.equalsIgnoreCase("latest")) {
            return DataVersion.getLatest();
        }
        for (DataVersion v : DataVersion.values()) {
            if (v.getVersion().equalsIgnoreCase(in)) {
                return v;
            }
        }
        throw new DecreeParsingException("Unable to parse data version \"" + in + "\"");
    }

    @Override
    public boolean supports(Class<?> type) {
        return DataVersion.class.equals(type);
    }
}
