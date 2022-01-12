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

package com.volmit.iris.core.pregenerator.syndicate;

import com.volmit.iris.core.pregenerator.syndicate.command.SyndicateCommand;
import com.volmit.iris.util.function.Consumer2;
import lombok.Builder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

@Builder
public class SyndicateClient {
    private final String address;
    private final int port;
    private final SyndicateCommand command;
    private final Consumer<DataOutputStream> output;

    public void go(Consumer2<SyndicateCommand, DataInputStream> handler) throws Throwable {
        Socket socket = new Socket(address, port);
        DataInputStream i = new DataInputStream(socket.getInputStream());
        DataOutputStream o = new DataOutputStream(socket.getOutputStream());
        SyndicateCommandIO.write(command, o);

        if(output != null) {
            output.accept(o);
        }

        o.flush();
        handler.accept(SyndicateCommandIO.read(i), i);
        socket.close();
    }
}
