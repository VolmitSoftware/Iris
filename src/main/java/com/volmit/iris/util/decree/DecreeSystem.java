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
import com.volmit.iris.util.decree.virtual.VirtualDecreeCommand;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DecreeSystem extends CommandExecutor, TabCompleter {
    KList<DecreeParameterHandler<?>> handlers = Iris.initialize("com.volmit.iris.util.decree.handlers", null).convert((i) -> (DecreeParameterHandler<?>) i);

    /**
     * The root class to start command searching from
     * @return
     */
    VirtualDecreeCommand getRoot();

    default boolean call(VolmitSender sender, String[] args)
    {
        DecreeContext.touch(sender);
        return getRoot().invoke(sender, enhanceArgs(args));
    }

    @Nullable
    @Override
    default List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return new KList<>();
    }

    @Override
    default boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        J.aBukkit(() -> {
            if(!call(new VolmitSender(sender), args))
            {
                sender.sendMessage(C.RED + "Unknown Iris Command");
            }
        });
        return true;
    }

    static KList<String> enhanceArgs(String[] args)
    {
        KList<String> a = new KList<>();

        if(args.length == 0)
        {
            return a;
        }

        StringBuilder flat = new StringBuilder();
        for(String i : args)
        {
            if(i.trim().isEmpty())
            {
                continue;
            }

            flat.append(" ").append(i.trim());
        }

        flat = new StringBuilder(flat.substring(1).trim());
        StringBuilder arg = new StringBuilder();
        boolean quoting = false;

        for(int x = 0; x < flat.length(); x++)
        {
            char i = flat.charAt(x);
            char j = x < flat.length() - 1 ? flat.charAt(x + 1) : i;
            boolean hasNext = x < flat.length();

            if(i == ' ' && !quoting)
            {
                if(!arg.toString().trim().isEmpty())
                {
                    a.add(arg.toString().trim());
                    arg = new StringBuilder();
                }
            }

            else if(i == '"')
            {
                if(!quoting && (arg.length() == 0))
                {
                    quoting = true;
                }

                else if(quoting)
                {
                    quoting = false;

                    if(hasNext && j == ' ')
                    {
                        if(!arg.toString().trim().isEmpty())
                        {
                            a.add(arg.toString().trim());
                            arg = new StringBuilder();
                        }
                    }

                    else if(!hasNext)
                    {
                        if(!arg.toString().trim().isEmpty())
                        {
                            a.add(arg.toString().trim());
                            arg = new StringBuilder();
                        }
                    }
                }
            }

            else
            {
                arg.append(i);
            }
        }

        if(!arg.toString().trim().isEmpty())
        {
            a.add(arg.toString().trim());
        }

        return a;
    }

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
}
