/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.tooling;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A line sent to the dispatcher without its {@code pack=<name>} option, which may stand anywhere in the line. */
record CommandLine(Optional<String> packName, List<String> words) {
    static final String PACK_OPTION = "pack=";

    static CommandLine of(List<String> tokens) throws CommandFailure {
        List<String> words = new ArrayList<>();
        Optional<String> packName = Optional.empty();
        for (String token : tokens) {
            if (!token.startsWith(PACK_OPTION)) {
                words.add(token);
                continue;
            }
            if (packName.isPresent()) {
                throw new CommandFailure("Name one pack only");
            }
            packName = Optional.of(token.substring(PACK_OPTION.length()));
        }
        return new CommandLine(packName, words);
    }
}
