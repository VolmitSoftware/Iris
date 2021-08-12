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

import java.lang.reflect.Parameter;

public class DecreeParameter {
    private final Parameter parameter;

    public DecreeParameter(Parameter parameter)
    {
        this.parameter = parameter;
    }

    public DecreeParameterHandler<?> getHandler()
    {
        return DecreeSystem.handle(getType());
    }

    public Class<?> getType()
    {
        return parameter.getType();
    }

    public String getName()
    {
        Param p = parameter.getDeclaredAnnotation(Param.class);
        return p == null ? parameter.getName() : p.name().isEmpty() ? parameter.getName() : p.name();
    }

    public String getDescription()
    {
        Param p = parameter.getDeclaredAnnotation(Param.class);
        return p.name().isEmpty() ? parameter.getName() : p.name();
    }

    public KList<String> getAliases()
    {
        Param p = parameter.getDeclaredAnnotation(Param.class);
        KList<String> d=  new KList<>();

        if(p != null)
        {
            for(String i : p.aliases())
            {
                if(i.isEmpty())
                {
                    continue;
                }

                d.add(i);
            }
        }

        return d;
    }
}
