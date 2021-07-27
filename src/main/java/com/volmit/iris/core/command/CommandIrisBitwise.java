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

package com.volmit.iris.core.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisBitwise extends MortarCommand {
    public CommandIrisBitwise() {
        super("bitwise", "bits", "bw");
        requiresPermission(Iris.perm.studio);
        setDescription("Run bitwise calculations");
        setCategory("Studio");
    }


    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("/iris bw " + getArgsUsage());
        }

        try {
            if (args[0].contains(",")) {
                KList<Integer> r = new KList<>();

                for (String i : args[0].split("\\Q,\\E")) {
                    int a = Integer.parseInt(i);
                    String op = args[1];
                    int b = Integer.parseInt(args[2]);
                    int v = 0;

                    switch (op) {
                        case "|" -> v = a | b;
                        case "&" -> v = a & b;
                        case "^" -> v = a ^ b;
                        case "%" -> v = a % b;
                        case ">>" -> v = a >> b;
                        case "<<" -> v = a << b;
                        default -> {
                            {
                                sender.sendMessage("Error Invalid operation");
                                return true;
                            }
                        }
                    }

                    r.add(v);
                    sender.sendMessage("Result: " + r.toString(","));
                }
            } else {
                int a = Integer.parseInt(args[0]);
                String op = args[1];
                int b = Integer.parseInt(args[2]);
                int v = 0;

                switch (op) {
                    case "|" -> v = a | b;
                    case "&" -> v = a & b;
                    case "^" -> v = a ^ b;
                    case "%" -> v = a % b;
                    case ">>" -> v = a >> b;
                    case "<<" -> v = a << b;
                    default -> {
                        {
                            sender.sendMessage("Error Invalid operation");
                            return true;
                        }
                    }
                }

                sender.sendMessage("Result: " + v);
            }
        } catch (Throwable ignored) {

        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<number> [|,&,^,>>,<<,%] <other>";
    }
}
