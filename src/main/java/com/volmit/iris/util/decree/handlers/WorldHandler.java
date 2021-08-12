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

package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.DecreeParsingException;
import com.volmit.iris.util.decree.DecreeWhichException;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;

public class WorldHandler implements DecreeParameterHandler<World> {
    @Override
    public KList<World> getPossibilities() {
        return new KList<>(new ArrayList<>(Bukkit.getWorlds()));
    }

    @Override
    public String toString(World player) {
        return player.getName();
    }

    @Override
    public World parse(String in) throws DecreeParsingException, DecreeWhichException {
        try
        {
            KList<World> options = getPossibilities(in);

            if(options.isEmpty())
            {
                throw new DecreeParsingException("Unable to find World \"" + in + "\"");
            }

            else if(options.size() > 1)
            {
                throw new DecreeWhichException();
            }

            return options.get(0);
        }

        catch(Throwable e)
        {
            throw new DecreeParsingException("Unable to find World \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(World.class);
    }
}
