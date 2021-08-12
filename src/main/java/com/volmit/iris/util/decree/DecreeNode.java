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
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.decree.exceptions.DecreeInstanceException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

public class DecreeNode {
    private final Method method;

    public DecreeNode(Method method)
    {
        this.method = method;
    }

    /**
     * Get the parameters of this decree node
     * @return The list of parameters if ALL are annotated by @{@link Param}, else null
     */
    public KList<DecreeParameter> getParameters()
    {
        KList<DecreeParameter> p = new KList<>();

        for(Parameter i : method.getParameters())
        {
            try {
                p.add(new DecreeParameter(i));
            } catch (DecreeInstanceException ignored) {
                return null;
            }
        }

        return p;
    }

    public String getName()
    {
        Decree p = method.getDeclaredAnnotation(Decree.class);
        return p == null || p.name().equals(Decree.METHOD_NAME) ? method.getName() : p.name();
    }

    public DecreeOrigin getOrigin()
    {
        Decree p = method.getDeclaredAnnotation(Decree.class);
        return p == null ? DecreeOrigin.BOTH : p.origin();
    }

    public String getDescription()
    {
        Decree p = method.getDeclaredAnnotation(Decree.class);
        return p != null ? p.description() : Decree.DEFAULT_DESCRIPTION;
    }

    public KList<String> getAliases()
    {
        Decree p = method.getDeclaredAnnotation(Decree.class);
        KList<String> d = new KList<>();

        if (p == null || Arrays.equals(p.aliases(), new String[]{Decree.NO_ALIASES})){
            return d;
        }

        for(String i : p.aliases())
        {
            if(i.isEmpty())
            {
                continue;
            }

            d.add(i);
        }

        return d;
    }
}
