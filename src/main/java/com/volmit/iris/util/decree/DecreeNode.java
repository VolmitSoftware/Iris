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
    private final Decree decree;

    public DecreeNode(Method method) throws DecreeInstanceException {
        this.method = method;
        this.decree = method.getDeclaredAnnotation(Decree.class);
        if (decree == null){
            throw new DecreeInstanceException("Cannot instantiate DecreeNode on method not annotated by @Decree");
        }
    }

    /**
     * Get the parameters of this decree node
     * @return The list of parameters if ALL are annotated by @{@link Param}, else null
     */
    public KList<DecreeParameter> getParameters() {
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

    public String getName() {
        return decree.name().equals(Decree.METHOD_NAME) ? method.getName() : decree.name();
    }

    public DecreeOrigin getOrigin() {
        return decree.origin();
    }

    public String getDescription() {
        return decree.description().isEmpty() ? Decree.DEFAULT_DESCRIPTION : decree.description();
    }

    public KList<String> getAliases() {
        KList<String> d = new KList<>();

        if (Arrays.equals(decree.aliases(), new String[]{Decree.NO_ALIASES})){
            return d;
        }

        for(String i : decree.aliases())
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
