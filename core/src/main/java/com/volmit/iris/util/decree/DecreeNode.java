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

package com.volmit.iris.util.decree;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import lombok.Data;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Data
public class DecreeNode {
    private final Method method;
    private final Object instance;
    private final Decree decree;

    public DecreeNode(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
        this.decree = method.getDeclaredAnnotation(Decree.class);
        if (decree == null) {
            throw new RuntimeException("Cannot instantiate DecreeNode on method " + method.getName() + " in " + method.getDeclaringClass().getCanonicalName() + " not annotated by @Decree");
        }
    }

    /**
     * Get the parameters of this decree node
     *
     * @return The list of parameters if ALL are annotated by @{@link Param}, else null
     */
    public KList<DecreeParameter> getParameters() {
        KList<DecreeParameter> required = new KList<>();
        KList<DecreeParameter> optional = new KList<>();

        for (Parameter i : method.getParameters()) {
            DecreeParameter p = new DecreeParameter(i);
            if (p.isRequired()) {
                required.add(p);
            } else {
                optional.add(p);
            }
        }

        required.addAll(optional);

        return required;
    }

    public String getName() {
        return decree.name().isEmpty() ? method.getName() : decree.name();
    }

    public DecreeOrigin getOrigin() {
        return decree.origin();
    }

    public String getDescription() {
        return decree.description().isEmpty() ? Decree.DEFAULT_DESCRIPTION : decree.description();
    }

    public KList<String> getNames() {
        KList<String> d = new KList<>();
        d.add(getName());

        for (String i : decree.aliases()) {
            if (i.isEmpty()) {
                continue;
            }

            d.add(i);
        }


        d.removeDuplicates();
        return d;
    }

    public boolean isSync() {
        return decree.sync();
    }
}
