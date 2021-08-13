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

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.math.M;
import org.checkerframework.checker.units.qual.K;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface DecreeSystem {
    KList<DecreeParameterHandler<?>> handlers = Iris.initialize("com.volmit.iris.util.decree.handlers", null).convert((i) -> (DecreeParameterHandler<?>) i);

    /**
     * Should return the root command<br>
     * Root must extend {@link DecreeCommand}
     *
     * @return The root command class (this#getClass)
     */
    Class<? extends DecreeCommand> getRoot();

    /**
     * Get the handler for the specified type
     * @param type The type to handle
     * @return The corresponding {@link DecreeParameterHandler}, or null
     */
    static DecreeParameterHandler<?> getHandler(Class<?> type)
    {
        for(DecreeParameterHandler<?> i : handlers)
        {
            if(i.supports(type))
            {
                return i;
            }
        }
        return null;
    }

    /**
     * Gets the method command to from the raw command parameters
     * @param command The raw command parameters
     * @return The @{@link Decree} method
     */
    default Method getRootCommandFrom(String[] command){
        return getCommandFrom(new KList<>(command), getRoot());
    }


    default Method getCommandFrom(KList<String> command, Class<? extends DecreeCommand> decree){
        return null;
    }
}
