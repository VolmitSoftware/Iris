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

package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.math.M;

public class BooleanHandler implements DecreeParameterHandler<Boolean> {
    @Override
    public KList<Boolean> getPossibilities() {
        return null;
    }

    @Override
    public String toString(Boolean aByte) {
        return aByte.toString();
    }

    @Override
    public Boolean parse(String in, boolean force) throws DecreeParsingException {
        try {
            if (in.equals("null") || in.equals("other") || in.equals("flip")) {
                return null;
            }
            return Boolean.parseBoolean(in);
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to parse boolean \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Boolean.class) || type.equals(boolean.class);
    }

    @Override
    public String getRandomDefault() {
        return M.r(0.5) + "";
    }
}
