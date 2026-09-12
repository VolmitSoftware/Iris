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

package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;

import java.util.List;
import java.util.Set;

final class PackJsonFieldChecks {
    private PackJsonFieldChecks() {
    }

    static void validateOptionalBoolean(String path, JSONObject object, String field,
                                        List<String> blockingErrors) {
        if (!object.has(field) || object.opt(field) == JSONObject.NULL) {
            return;
        }
        if (!(object.opt(field) instanceof Boolean)) {
            blockingErrors.add(path + "." + field + " must be a boolean.");
        }
    }

    static void validateOptionalResourceKey(String path, JSONObject object, String field,
                                            boolean allowNone, List<String> blockingErrors) {
        if (!object.has(field)) {
            return;
        }
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof String value)) {
            blockingErrors.add(path + "." + field + " must be a string.");
            return;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || allowNone && "NONE".equalsIgnoreCase(normalized)) {
            return;
        }
        if (!PackValidator.RESOURCE_KEY_PATTERN.matcher(normalized).matches()) {
            blockingErrors.add(path + "." + field + " must be a namespaced registry key.");
        }
    }

    static void validateOptionalIntegerRange(String path, JSONObject object, String field,
                                             int minimum, int maximum,
                                             List<String> blockingErrors) {
        if (!object.has(field) || object.opt(field) == JSONObject.NULL) {
            return;
        }
        Integer value = PackLootValidator.lootInteger(object, field, minimum, path, blockingErrors);
        PackLootValidator.requireMinimum(path + "." + field, value, minimum, blockingErrors);
        PackLootValidator.requireMaximum(path + "." + field, value, maximum, blockingErrors);
    }

    static void validateOptionalDoubleRange(String path, JSONObject object, String field,
                                            double minimum, double maximum,
                                            List<String> blockingErrors) {
        if (!object.has(field) || object.opt(field) == JSONObject.NULL) {
            return;
        }
        String fieldPath = path + "." + field;
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            blockingErrors.add(fieldPath + " must be a number.");
            return;
        }
        double value = number.doubleValue();
        if (value < minimum) {
            blockingErrors.add(fieldPath + " must be at least " + minimum + ".");
        }
        if (value > maximum) {
            blockingErrors.add(fieldPath + " must be at most " + maximum + ".");
        }
    }

    static void validateOptionalEnum(String path, JSONObject object, String field,
                                     Set<String> values, List<String> blockingErrors) {
        if (!object.has(field)) {
            return;
        }
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof String value) || !values.contains(value)) {
            blockingErrors.add(path + "." + field + " must be one of " + values + ".");
        }
    }
}
