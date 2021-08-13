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
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.decree.exceptions.DecreeWhichException;
import lombok.Data;

import java.lang.reflect.Parameter;
import java.util.Arrays;

@Data
public class DecreeParameter {
    private final Parameter parameter;
    private final Param param;

    public DecreeParameter(Parameter parameter) {
        this.parameter = parameter;
        this.param = parameter.getDeclaredAnnotation(Param.class);
        if (param == null){
            throw new RuntimeException("Cannot instantiate DecreeParameter on " + parameter.getName() + " in method " + parameter.getDeclaringExecutable().getName() + "(...) in class " + parameter.getDeclaringExecutable().getDeclaringClass().getCanonicalName() + " not annotated by @Param");
        }
    }

    public DecreeParameterHandler<?> getHandler() {
        return DecreeSystem.getHandler(getType());
    }

    public Class<?> getType() {
        return parameter.getType();
    }

    public String getName() {
        return param.name().isEmpty() ? parameter.getName() : param.name();
    }

    public String getDescription() {
        return param.description().isEmpty() ? Param.DEFAULT_DESCRIPTION : param.description();
    }

    public boolean isRequired() {
        return param.required();
    }

    public KList<String> getNames() {
        KList<String> d = new KList<>();

        for(String i : param.aliases())
        {
            if(i.isEmpty())
            {
                continue;
            }

            d.add(i);
        }

        d.add(getName());
        d.removeDuplicates();

        return d;
    }

    public Object getDefaultValue() throws DecreeParsingException, DecreeWhichException {
        return param.defaultValue().isEmpty() ? null : getHandler().parse(param.defaultValue());
    }

    public boolean hasDefault() {
        return !param.defaultValue().isEmpty();
    }

    public String example() {
        KList<?> ff = getHandler().getPossibilities();
        ff = ff != null ? ff : new KList<>();
        KList<String> f = ff.convert((i) -> getHandler().toStringForce(i));
        if(f.isEmpty())
        {
            f = new KList<>();
            f.add(getHandler().getRandomDefault());
        }

        return f.getRandom();
    }
}
