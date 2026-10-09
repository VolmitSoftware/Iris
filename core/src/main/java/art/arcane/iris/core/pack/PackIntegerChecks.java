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

package art.arcane.iris.core.pack;

import art.arcane.volmlib.util.json.JSONObject;

import java.util.List;

/** Checks of integer fields in the JSON of pack files, which add what is wrong to a list of messages. */
public final class PackIntegerChecks {
    private PackIntegerChecks() {
    }

    /** The integer in {@code field}, {@code defaultValue} when it is absent, or null (with a message) when it is not an integer. */
    public static Integer integerField(JSONObject object, String field, int defaultValue,
                                       String path, List<String> blockingErrors) {
        if (!object.has(field)) {
            return defaultValue;
        }
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.longValue() < Integer.MIN_VALUE
                || number.longValue() > Integer.MAX_VALUE) {
            blockingErrors.add(path + "." + field + " must be an integer.");
            return null;
        }
        return number.intValue();
    }

    public static void requireMinimum(String fieldPath, Integer value, int minimum,
                                      List<String> blockingErrors) {
        if (value != null && value < minimum) {
            blockingErrors.add(fieldPath + " must be at least " + minimum + ".");
        }
    }

    public static void requireMaximum(String fieldPath, Integer value, int maximum,
                                      List<String> blockingErrors) {
        if (value != null && value > maximum) {
            blockingErrors.add(fieldPath + " must be at most " + maximum + ".");
        }
    }

    public static void requireOrdered(String minimumPath, Integer minimum, String maximumPath,
                                      Integer maximum, List<String> blockingErrors) {
        if (minimum != null && maximum != null && minimum > maximum) {
            blockingErrors.add(minimumPath + " must not exceed " + maximumPath + ".");
        }
    }
}
