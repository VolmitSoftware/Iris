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

package com.volmit.iris.util.decree;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;

import java.util.Locale;

public interface DecreeParameterHandler<T> {
    KList<T> getPossibilities();

    String toString(T t);

    T parse(String in) throws DecreeParsingException, DecreeWhichException;

    boolean supports(Class<?> type);

    default KList<T> getPossibilities(String input)
    {
        KList<T> p = getPossibilities();
        KList<T> m = new KList<>();

        if(p != null)
        {
            if(input.trim().isEmpty())
            {
                return getPossibilities();
            }

            KList<String> f = p.convert(this::toString);

            for(int i = 0; i < f.size(); i++)
            {
                String g = f.get(i);
                if(g.equalsIgnoreCase(input))
                {
                    m.add(p.get(i));
                }
            }

            for(int i = 0; i < f.size(); i++)
            {
                String g = f.get(i);
                if(g.toLowerCase().contains(input.toLowerCase()) || input.toLowerCase().contains(g.toLowerCase()))
                {
                    m.addIfMissing(p.get(i));
                }
            }
        }

        return m;
    }
}
