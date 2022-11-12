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

package com.volmit.iris.util.board;

import com.volmit.iris.util.format.C;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

/**
 * @author Missionary (missionarymc@gmail.com)
 * @since 3/29/2018
 */
@SuppressWarnings("ClassCanBeRecord")
public class BoardEntry {

    @Getter
    private final String prefix, suffix;

    private BoardEntry(final String prefix, final String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public static BoardEntry translateToEntry(String input) {
        if (input.isEmpty()) {
            return new BoardEntry("", "");
        }
        if (input.length() <= 16) {
            return new BoardEntry(input, "");
        } else {
            String prefix = input.substring(0, 16);
            String suffix = "";

            if (prefix.endsWith("\u00a7")) {
                prefix = prefix.substring(0, prefix.length() - 1);
                suffix = "\u00a7" + suffix;
            }

            suffix = StringUtils.left(C.getLastColors(prefix) + suffix + input.substring(16), 16);
            return new BoardEntry(prefix, suffix);
        }
    }
}