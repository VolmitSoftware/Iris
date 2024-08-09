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

package com.volmit.iris.util.collection;


import java.util.List;

/**
 * Adapts a list of objects into a list of other objects
 *
 * @param <FROM> the from object in lists (the item INSIDE the list)
 * @param <TO>   the to object in lists (the item INSIDE the list)
 * @author cyberpwn
 */
public abstract class GListAdapter<FROM, TO> {
    /**
     * Adapts a list of FROM to a list of TO
     *
     * @param from the from list
     * @return the to list
     */
    public List<TO> adapt(List<FROM> from) {
        List<TO> adapted = new KList<>();

        for (FROM i : from) {
            TO t = onAdapt(i);

            if (t != null) {
                adapted.add(onAdapt(i));
            }
        }

        return adapted;
    }

    /**
     * Adapts a list object FROM to TO for use with the adapt method
     *
     * @param from the from object
     * @return the to object
     */
    public abstract TO onAdapt(FROM from);
}
