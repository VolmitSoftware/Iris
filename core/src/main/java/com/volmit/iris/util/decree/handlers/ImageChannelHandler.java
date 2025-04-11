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

package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.engine.object.IrisImageChannel;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;

import java.util.Arrays;

public class ImageChannelHandler implements DecreeParameterHandler<IrisImageChannel> {
    @Override
    public KList<IrisImageChannel> getPossibilities() {
        return new KList<>(Arrays.stream(IrisImageChannel.values()).toList());
    }

    @Override
    public String toString(IrisImageChannel channel) {
        return channel.name();
    }

    @Override
    public IrisImageChannel parse(String in, boolean force) throws DecreeParsingException {
        return IrisImageChannel.valueOf(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(IrisImageChannel.class);
    }

    @Override
    public String getRandomDefault() {
        return IrisImageChannel.BRIGHTNESS.toString();
    }
}
