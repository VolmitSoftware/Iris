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

import java.lang.reflect.*;
import java.util.Arrays;

public class DecreeCategory {
    private final Class<?> clazz;
    private final Decree decree;

    public DecreeCategory(Class<?> clazz) throws DecreeInstanceException {
        this.clazz = clazz;
        this.decree = clazz.getDeclaredAnnotation(Decree.class);
        if (decree == null){
            throw new DecreeInstanceException("Cannot instantiate DecreeCategory on class not annotated by @Decree");
        }
    }

    /**
     * Get the subcommands of this decree category
     * @return The list of subcommands if ALL are only classes implementing DecreeCommand, else null
     */
    public KList<DecreeCommand> getCommands() {
        KList<DecreeCommand> c = new KList<>();

        for(Field i : clazz.getFields())
        {
            try {
                i.setAccessible(true);
                if (DecreeCommand.class.isAssignableFrom(i.getType())) {
                    c.add((DecreeCommand) i.getType().getConstructor().newInstance());
                }
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return c;
    }

    public String getName() {
        return decree.name().equals(Decree.METHOD_NAME) ? clazz.getName() : decree.name();
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
