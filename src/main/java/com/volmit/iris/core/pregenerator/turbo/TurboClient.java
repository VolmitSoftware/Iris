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

package com.volmit.iris.core.pregenerator.turbo;

import com.volmit.iris.core.pregenerator.turbo.command.TurboCommand;
import com.volmit.iris.util.collection.GBiset;
import com.volmit.iris.util.function.Consumer2;
import com.volmit.iris.util.scheduling.J;
import lombok.Builder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Builder
public class TurboClient {
    private String address;
    private int port;
    private TurboCommand command;
    private Consumer<DataOutputStream> output;

    public void go(Consumer2<TurboCommand, DataInputStream> handler) throws Throwable {
        Socket socket = new Socket(address, port);
        DataInputStream i = new DataInputStream(socket.getInputStream());
        DataOutputStream o = new DataOutputStream(socket.getOutputStream());
        TurboCommander.write(command, o);

        if(output != null)
        {
            output.accept(o);
        }

        o.flush();
        handler.accept(TurboCommander.read(i), i);
        socket.close();
    }
}
