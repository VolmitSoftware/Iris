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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedDimensionReplacement;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

record ModdedReplaceArguments(String target, String type, OptionalLong seed, boolean off) {
    static final String DEFAULT_TYPE = "default";
    static final String OFF = "off";
    static final String PRESERVE_SEED = "preserve";
    private static final String TARGET = "target";
    private static final String TYPE = "type";
    private static final String SEED = "seed";
    private static final List<String> POSITIONAL = List.of(TARGET, TYPE, SEED);
    private static final Map<String, String> KEYS = Map.of(
            TARGET, TARGET,
            "world-name", TARGET,
            TYPE, TYPE,
            "dimension", TYPE,
            "pack", TYPE,
            SEED, SEED,
            "s", SEED
    );

    static ModdedReplaceArguments parse(String raw) {
        String input = raw == null ? "" : raw.trim();
        String[] values = new String[POSITIONAL.size()];
        String[] tokens = input.isEmpty() ? new String[0] : input.split("\\s+");
        for (String token : tokens) {
            int equals = token.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = KEYS.get(token.substring(0, equals).toLowerCase(Locale.ROOT));
            if (key == null) {
                throw new IllegalArgumentException("Unknown argument '" + token.substring(0, equals)
                        + "'. Use target=, type=, or seed=.");
            }
            String value = token.substring(equals + 1);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Argument " + key + "= needs a value.");
            }
            int index = POSITIONAL.indexOf(key);
            if (values[index] != null) {
                throw new IllegalArgumentException("Argument " + key + " was given more than once.");
            }
            values[index] = value;
        }
        for (String token : tokens) {
            if (token.indexOf('=') >= 0) {
                continue;
            }
            int index = nextUnset(values);
            if (index < 0) {
                throw new IllegalArgumentException("Unexpected argument '" + token
                        + "'. Usage: /iris replace <target> [type=<pack|pack:dimensionKey|off>] [seed=<seed>]");
            }
            values[index] = token;
        }
        if (values[0] == null) {
            throw new IllegalArgumentException("Missing target. Usage: /iris replace <target> [type=<pack|pack:dimensionKey|off>] [seed=<seed>]");
        }
        String type = values[1] == null ? DEFAULT_TYPE : values[1];
        boolean off = OFF.equalsIgnoreCase(type);
        if (off && values[2] != null) {
            throw new IllegalArgumentException("Turning a replacement off takes no seed.");
        }
        return new ModdedReplaceArguments(values[0], type, parseSeed(values[2]), off);
    }

    private static int nextUnset(String[] values) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == null) {
                return index;
            }
        }
        return -1;
    }

    private static OptionalLong parseSeed(String raw) {
        if (raw == null || PRESERVE_SEED.equalsIgnoreCase(raw)) {
            return OptionalLong.empty();
        }
        long seed;
        try {
            seed = Long.parseLong(raw);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Seed must be a signed 64-bit integer");
        }
        return OptionalLong.of(ModdedDimensionReplacement.requireUsableSeed(seed));
    }
}
